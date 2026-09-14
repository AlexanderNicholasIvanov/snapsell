"""The pricing arithmetic. Pure functions, no I/O, fully unit tested.

The Android app has a Kotlin copy of `price_point` for bundle maths. Keep them in sync.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from statistics import median

from snapsell.ebay.client import EbayListing

# Listings whose total is outside [LOW_MULT, HIGH_MULT] x median are dropped before
# the final median. This removes "for parts" and accessory-only hits at the low end
# and bundles or mislabelled items at the high end.
LOW_MULT = 0.3
HIGH_MULT = 3.0


def price_point(value: float) -> float:
    """Round to a price a person would actually type into a listing.

    < $20   -> nearest $1
    < $100  -> nearest $5
    else    -> nearest $10
    Never returns less than $1.
    """
    if value <= 0:
        return 1.0
    if value < 20:
        step = 1
    elif value < 100:
        step = 5
    else:
        step = 10
    # Round half up so $12.50 -> $13, not banker's rounding to $12.
    rounded = math.floor(value / step + 0.5) * step
    return float(max(rounded, 1))


def trim_outliers(totals: list[float]) -> list[float]:
    """Drop totals far from the raw median. Needs at least 4 values to act."""
    if len(totals) < 4:
        return list(totals)
    raw = median(totals)
    if raw <= 0:
        return list(totals)
    return [t for t in totals if LOW_MULT * raw <= t <= HIGH_MULT * raw]


@dataclass(frozen=True)
class QuoteNumbers:
    suggested: float | None
    asking_median: float | None
    asking_low: float | None
    asking_high: float | None
    count: int


def compute_quote(listings: list[EbayListing], local_sale_factor: float) -> QuoteNumbers:
    totals = [listing.total for listing in listings if listing.total > 0]
    kept = trim_outliers(totals)
    if not kept:
        return QuoteNumbers(None, None, None, None, 0)
    asking_median = round(median(kept), 2)
    suggested = price_point(asking_median * local_sale_factor)
    return QuoteNumbers(
        suggested=suggested,
        asking_median=asking_median,
        asking_low=round(min(kept), 2),
        asking_high=round(max(kept), 2),
        count=len(kept),
    )
