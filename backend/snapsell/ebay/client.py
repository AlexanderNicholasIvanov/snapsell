"""eBay Browse API client.

Only two calls are used:
  POST https://api.ebay.com/identity/v1/oauth2/token   (client credentials grant)
  GET  https://api.ebay.com/buy/browse/v1/item_summary/search

The search method needs an *application* token minted with the scope
`https://api.ebay.com/oauth/api_scope`, and the marketplace header
`X-EBAY-C-MARKETPLACE-ID`. Both facts were confirmed on the method's reference page.
"""

from __future__ import annotations

import base64
import time
from dataclasses import dataclass
from typing import Protocol

import httpx

from snapsell.models import Condition

TOKEN_PATH = "/identity/v1/oauth2/token"
SEARCH_PATH = "/buy/browse/v1/item_summary/search"
APP_SCOPE = "https://api.ebay.com/oauth/api_scope"


class EbayError(Exception):
    """Raised for any failure talking to eBay. Mapped to HTTP 502 by the API layer."""


@dataclass(frozen=True)
class EbayListing:
    item_id: str
    title: str
    price: float
    shipping: float
    condition: str | None
    condition_id: str | None
    url: str
    image_url: str | None

    @property
    def total(self) -> float:
        return round(self.price + self.shipping, 2)


class SearchSource(Protocol):
    """What the pricing service depends on. Tests provide a fixture-backed fake."""

    async def search(
        self, query: str, condition: Condition | None, limit: int
    ) -> list[EbayListing]: ...


def parse_item_summaries(payload: dict) -> list[EbayListing]:
    """Turn a Browse API search response into listings. Skips entries with no price."""
    out: list[EbayListing] = []
    for raw in payload.get("itemSummaries", []) or []:
        price = raw.get("price") or {}
        if price.get("currency") not in (None, "USD") or "value" not in price:
            continue
        shipping = 0.0
        for opt in raw.get("shippingOptions", []) or []:
            cost = opt.get("shippingCost") or {}
            if cost.get("currency") in (None, "USD") and "value" in cost:
                shipping = float(cost["value"])
                break
        image = raw.get("image") or {}
        out.append(
            EbayListing(
                item_id=str(raw.get("itemId", "")),
                title=str(raw.get("title", "")),
                price=float(price["value"]),
                shipping=shipping,
                condition=raw.get("condition"),
                condition_id=raw.get("conditionId"),
                url=str(raw.get("itemWebUrl", "")),
                image_url=image.get("imageUrl"),
            )
        )
    return out


class EbayClient:
    """Async client with a cached application token."""

    def __init__(
        self,
        client_id: str,
        client_secret: str,
        api_base: str,
        marketplace: str,
        http: httpx.AsyncClient | None = None,
    ) -> None:
        # Missing credentials are reported on first use, not at construction, so a
        # dev server without eBay keys still boots and serves /health and /identify.
        self._configured = bool(client_id and client_secret)
        self._basic = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
        self._api_base = api_base.rstrip("/")
        self._marketplace = marketplace
        self._http = http or httpx.AsyncClient(timeout=15.0)
        self._token: str | None = None
        self._token_expires_at = 0.0

    async def _app_token(self) -> str:
        if not self._configured:
            raise EbayError("eBay client id/secret are not configured")
        # Refresh a minute early so a request never races the expiry.
        if self._token and time.time() < self._token_expires_at - 60:
            return self._token
        resp = await self._http.post(
            self._api_base + TOKEN_PATH,
            headers={
                "Authorization": f"Basic {self._basic}",
                "Content-Type": "application/x-www-form-urlencoded",
            },
            data={"grant_type": "client_credentials", "scope": APP_SCOPE},
        )
        if resp.status_code != 200:
            raise EbayError(f"token request failed: {resp.status_code} {resp.text[:200]}")
        body = resp.json()
        self._token = body["access_token"]
        self._token_expires_at = time.time() + float(body.get("expires_in", 7200))
        return self._token

    async def search(
        self, query: str, condition: Condition | None, limit: int
    ) -> list[EbayListing]:
        from snapsell.ebay.conditions import condition_filter

        filters = ["priceCurrency:USD", "itemLocationCountry:US"]
        if condition is not None:
            filters.append(condition_filter(condition))
        params = {"q": query, "limit": str(limit), "filter": ",".join(filters)}
        token = await self._app_token()
        resp = await self._http.get(
            self._api_base + SEARCH_PATH,
            params=params,
            headers={
                "Authorization": f"Bearer {token}",
                "X-EBAY-C-MARKETPLACE-ID": self._marketplace,
            },
        )
        if resp.status_code == 401:
            # Token may have been revoked; mint a fresh one once.
            self._token = None
            token = await self._app_token()
            resp = await self._http.get(
                self._api_base + SEARCH_PATH,
                params=params,
                headers={
                    "Authorization": f"Bearer {token}",
                    "X-EBAY-C-MARKETPLACE-ID": self._marketplace,
                },
            )
        if resp.status_code != 200:
            raise EbayError(f"search failed: {resp.status_code} {resp.text[:200]}")
        return parse_item_summaries(resp.json())

    async def aclose(self) -> None:
        await self._http.aclose()
