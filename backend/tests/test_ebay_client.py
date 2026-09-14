from __future__ import annotations

import httpx
import pytest

from snapsell.ebay.client import EbayClient, EbayError, parse_item_summaries
from snapsell.ebay.conditions import condition_filter
from tests.conftest import load_fixture

pytestmark = pytest.mark.anyio


def test_condition_filter_syntax() -> None:
    assert condition_filter("new") == "conditionIds:{1000|1500}"
    assert condition_filter("for_parts") == "conditionIds:{7000}"


def test_parse_item_summaries_sums_shipping() -> None:
    listings = parse_item_summaries(load_fixture("ebay_search_ipad_good.json"))
    assert len(listings) == 11
    first = listings[0]
    assert first.price == 79.99 and first.shipping == 10.0 and first.total == 89.99
    assert first.url.startswith("https://www.ebay.com/itm/")


def test_parse_skips_non_usd_and_missing_price() -> None:
    payload = {
        "itemSummaries": [
            {"itemId": "a", "title": "eur", "price": {"value": "10", "currency": "EUR"}},
            {"itemId": "b", "title": "no price"},
            {"itemId": "c", "title": "ok", "price": {"value": "10", "currency": "USD"}},
        ]
    }
    assert [x.item_id for x in parse_item_summaries(payload)] == ["c"]


async def test_client_mints_token_then_searches() -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        if request.url.path.endswith("/oauth2/token"):
            assert request.headers["Authorization"].startswith("Basic ")
            assert b"grant_type=client_credentials" in request.content
            return httpx.Response(200, json={"access_token": "tok", "expires_in": 7200})
        assert request.headers["Authorization"] == "Bearer tok"
        assert request.headers["X-EBAY-C-MARKETPLACE-ID"] == "EBAY_US"
        return httpx.Response(200, json=load_fixture("ebay_search_ipad_sparse.json"))

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    client = EbayClient("id", "secret", "https://api.ebay.com", "EBAY_US", http=http)
    listings = await client.search("ipad", "good", 25)
    assert len(listings) == 2
    assert "conditionIds:{3000|4000|5000}" in seen[-1].url.params["filter"]
    # Second search reuses the cached token: only one token call in total.
    await client.search("ipad", None, 25)
    assert "conditionIds" not in seen[-1].url.params["filter"]
    assert sum(1 for r in seen if r.url.path.endswith("/oauth2/token")) == 1


async def test_client_raises_on_search_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/oauth2/token"):
            return httpx.Response(200, json={"access_token": "tok", "expires_in": 7200})
        return httpx.Response(500, text="boom")

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    client = EbayClient("id", "secret", "https://api.ebay.com", "EBAY_US", http=http)
    with pytest.raises(EbayError):
        await client.search("ipad", None, 25)


async def test_client_without_credentials_fails_on_use_not_construction() -> None:
    client = EbayClient("", "", "https://api.ebay.com", "EBAY_US")
    with pytest.raises(EbayError):
        await client.search("ipad", None, 25)
