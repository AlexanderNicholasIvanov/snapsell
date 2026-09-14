"""Pydantic models mirroring ../contracts/*.schema.json.

These are the wire types. `extra="forbid"` everywhere so a field drifting from the
contract fails loudly in tests instead of being silently dropped.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Condition = Literal["new", "like_new", "good", "fair", "for_parts"]
SoldSource = Literal["llm_estimate", "ebay_marketplace_insights", "third_party"]


class Contract(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Item(Contract):
    name: str = Field(min_length=1, max_length=120)
    brand: str | None = Field(default=None, max_length=60)
    model: str | None = Field(default=None, max_length=80)
    category: str = Field(min_length=1, max_length=80)
    condition: Condition
    attributes: list[str] = Field(default_factory=list, max_length=12)
    search_query: str = Field(min_length=1, max_length=200)
    confidence: float = Field(ge=0, le=1)
    notes: str | None = Field(default=None, max_length=500)


class IdentifyRequest(Contract):
    image_base64: str = Field(min_length=1)
    media_type: Literal["image/jpeg", "image/png", "image/webp"]
    hint: str | None = Field(default=None, max_length=200)


class ListingText(Contract):
    title: str = Field(min_length=1, max_length=99)
    description: str = Field(min_length=1, max_length=2000)


class IdentifyResponse(Contract):
    item: Item
    listing_text: ListingText
    request_id: str


class PriceRequest(Contract):
    item: Item
    local_sale_factor: float = Field(default=0.85, gt=0, le=1.5)


class Comp(Contract):
    item_id: str
    title: str
    price: float = Field(ge=0)
    shipping: float = Field(ge=0)
    total: float = Field(ge=0)
    condition: str | None
    url: str
    image_url: str | None = None


class SoldEstimate(Contract):
    low: float = Field(ge=0)
    high: float = Field(ge=0)
    currency: Literal["USD"] = "USD"
    source: SoldSource
    rationale: str | None = Field(default=None, max_length=300)


class PriceQuote(Contract):
    suggested_price: float | None = Field(ge=0)
    currency: Literal["USD"] = "USD"
    asking_median: float | None = Field(ge=0)
    asking_low: float | None = Field(ge=0)
    asking_high: float | None = Field(ge=0)
    comp_count: int = Field(ge=0)
    condition_filtered: bool
    local_sale_factor: float = Field(gt=0, le=1.5)
    comps: list[Comp] = Field(max_length=5)
    estimated_sold: SoldEstimate | None
    search_query: str
    request_id: str


class BundleItem(Contract):
    item: Item
    price: float = Field(ge=0)


class BundleRequest(Contract):
    items: list[BundleItem] = Field(min_length=2, max_length=30)
    bundle_price: float = Field(ge=0)


class BundleResponse(Contract):
    title: str = Field(min_length=1, max_length=99)
    description: str = Field(min_length=1, max_length=2000)
    request_id: str
