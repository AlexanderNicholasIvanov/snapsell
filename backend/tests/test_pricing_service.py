from __future__ import annotations

import pytest

from snapsell.config import Settings
from snapsell.models import Item
from snapsell.pricing.service import PricingService
from snapsell.pricing.sold import LlmSoldEstimate, NoSoldEstimate
from tests.conftest import FakeEbay, FakeLlm, load_example

pytestmark = pytest.mark.anyio


def item() -> Item:
    return Item(**load_example("item.json"))


async def test_condition_filtered_path_trims_outliers(settings: Settings) -> None:
    ebay = FakeEbay("ebay_search_ipad_good.json", "ebay_search_ipad_all.json")
    svc = PricingService(ebay, LlmSoldEstimate(FakeLlm()), settings)
    q = await svc.quote(item(), 0.85, "req_test")
    assert ebay.calls == [("Apple iPad Air 2 64GB Wi-Fi", "good", settings.ebay_search_limit)]
    assert q.condition_filtered is True
    # 11 listings, the $17.98 accessory and the $499 lot are trimmed.
    assert q.comp_count == 9
    assert q.asking_low == 64.0 and q.asking_high == 129.95
    assert q.asking_median == 89.99
    assert q.suggested_price == 75.0
    assert len(q.comps) == settings.comps_shown
    assert q.comps[0].total == 89.99
    assert q.estimated_sold is not None and q.estimated_sold.source == "llm_estimate"
    assert q.request_id == "req_test"


async def test_falls_back_to_all_conditions_when_sparse(settings: Settings) -> None:
    ebay = FakeEbay("ebay_search_ipad_sparse.json", "ebay_search_ipad_all.json")
    svc = PricingService(ebay, NoSoldEstimate(), settings)
    q = await svc.quote(item(), 0.85, "req_test")
    assert [c[1] for c in ebay.calls] == ["good", None]
    assert q.condition_filtered is False
    assert q.comp_count > 2
    assert q.estimated_sold is None


async def test_no_comps_gives_null_price(settings: Settings) -> None:
    ebay = FakeEbay("ebay_search_empty.json", "ebay_search_empty.json")
    svc = PricingService(ebay, NoSoldEstimate(), settings)
    q = await svc.quote(item(), 0.85, "req_test")
    assert q.suggested_price is None
    assert q.comp_count == 0
    assert q.comps == []
    assert q.condition_filtered is False


async def test_local_factor_is_applied(settings: Settings) -> None:
    ebay = FakeEbay("ebay_search_ipad_good.json", "ebay_search_ipad_all.json")
    svc = PricingService(ebay, NoSoldEstimate(), settings)
    full = await svc.quote(item(), 1.0, "r")
    cheap = await svc.quote(item(), 0.5, "r")
    assert full.suggested_price == 90.0
    assert cheap.suggested_price == 45.0
