"""Shared fakes. No network, no spend."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from snapsell.config import Settings
from snapsell.ebay.client import EbayListing, parse_item_summaries
from snapsell.llm.client import IdentifyResult, LlmRefused
from snapsell.main import create_app
from snapsell.models import BundleItem, Condition, Item, ListingText, SoldEstimate

FIXTURES = Path(__file__).parent / "fixtures"
CONTRACTS = Path(__file__).resolve().parents[2] / "contracts"


def load_fixture(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text())


def load_example(name: str) -> dict:
    return json.loads((CONTRACTS / "examples" / name).read_text())


class FakeEbay:
    """Recorded Browse API responses: `filtered` for condition searches, else `unfiltered`."""

    def __init__(self, filtered: str, unfiltered: str) -> None:
        self._filtered = parse_item_summaries(load_fixture(filtered))
        self._unfiltered = parse_item_summaries(load_fixture(unfiltered))
        self.calls: list[tuple[str, Condition | None, int]] = []

    async def search(
        self, query: str, condition: Condition | None, limit: int
    ) -> list[EbayListing]:
        self.calls.append((query, condition, limit))
        return list(self._filtered if condition is not None else self._unfiltered)


class FakeLlm:
    def __init__(self, refuse: bool = False) -> None:
        self.refuse = refuse
        self.identify_calls = 0

    async def identify(self, image: bytes, media_type: str, hint: str | None) -> IdentifyResult:
        self.identify_calls += 1
        if self.refuse:
            raise LlmRefused("declined")
        data = load_fixture("claude_identify_ipad.json")
        text = ListingText(title=data.pop("title"), description=data.pop("description"))
        return IdentifyResult(item=Item(**data), listing_text=text)

    async def sold_estimate(self, item: Item, asking_median: float | None) -> SoldEstimate | None:
        data = load_fixture("claude_sold_ipad.json")
        return SoldEstimate(
            low=data["low"], high=data["high"], source="llm_estimate", rationale=data["rationale"]
        )

    async def bundle_text(self, items: list[BundleItem], bundle_price: float) -> ListingText:
        return ListingText(**load_fixture("claude_bundle_kitchen.json"))


@pytest.fixture
def settings() -> Settings:
    return Settings(auth_disabled=True, ebay_client_id="x", ebay_client_secret="y", daily_cap=5)


@pytest.fixture
def fake_ebay() -> FakeEbay:
    return FakeEbay("ebay_search_ipad_good.json", "ebay_search_ipad_all.json")


@pytest.fixture
def fake_llm() -> FakeLlm:
    return FakeLlm()


@pytest.fixture
def client(settings: Settings, fake_llm: FakeLlm, fake_ebay: FakeEbay):
    from snapsell.pricing.sold import LlmSoldEstimate

    app = create_app(
        settings=settings, llm=fake_llm, ebay=fake_ebay, sold=LlmSoldEstimate(fake_llm)
    )
    with TestClient(app) as c:
        yield c


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"
