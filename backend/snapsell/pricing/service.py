"""Orchestrates eBay comps + the sold-price source into one PriceQuote."""

from __future__ import annotations

from snapsell.config import Settings
from snapsell.ebay.client import EbayListing, SearchSource
from snapsell.models import Comp, Item, PriceQuote
from snapsell.pricing.formula import compute_quote
from snapsell.pricing.sold import SoldPriceSource


class PricingService:
    def __init__(self, ebay: SearchSource, sold: SoldPriceSource, settings: Settings) -> None:
        self._ebay = ebay
        self._sold = sold
        self._settings = settings

    async def quote(self, item: Item, local_sale_factor: float, request_id: str) -> PriceQuote:
        limit = self._settings.ebay_search_limit
        listings = await self._ebay.search(item.search_query, item.condition, limit)
        condition_filtered = True
        if len(listings) < self._settings.min_filtered_comps:
            # Not enough exact-condition comps. Fall back to every condition and say so.
            listings = await self._ebay.search(item.search_query, None, limit)
            condition_filtered = False

        numbers = compute_quote(listings, local_sale_factor)
        sold = await self._sold.estimate(item, numbers.asking_median)

        return PriceQuote(
            suggested_price=numbers.suggested,
            asking_median=numbers.asking_median,
            asking_low=numbers.asking_low,
            asking_high=numbers.asking_high,
            comp_count=numbers.count,
            condition_filtered=condition_filtered,
            local_sale_factor=local_sale_factor,
            comps=[_to_comp(x) for x in listings[: self._settings.comps_shown]],
            estimated_sold=sold,
            search_query=item.search_query,
            request_id=request_id,
        )


def _to_comp(listing: EbayListing) -> Comp:
    return Comp(
        item_id=listing.item_id,
        title=listing.title,
        price=listing.price,
        shipping=listing.shipping,
        total=listing.total,
        condition=listing.condition,
        url=listing.url,
        image_url=listing.image_url,
    )
