"""Map SnapSell's five conditions onto eBay condition IDs.

eBay's Browse API filters with `conditionIds:{1000|1500}`. The numeric codes are
category-specific in places (4000/5000/6000 only exist for media-type categories)
so each bucket lists every code that could plausibly match. Verified against the
Buy API field-filter reference: New = 1000, Good = 5000, Seller Refurbished = 2500.
"""

from __future__ import annotations

from snapsell.models import Condition

CONDITION_IDS: dict[Condition, tuple[int, ...]] = {
    # New, New other (see details)
    "new": (1000, 1500),
    # Like New, Certified/Excellent/Very Good/Good refurbished, Seller refurbished
    "like_new": (2750, 2000, 2010, 2020, 2030, 2500),
    # Used (the generic code most categories use), Very Good, Good
    "good": (3000, 4000, 5000),
    # Used, Acceptable
    "fair": (3000, 6000),
    # For parts or not working
    "for_parts": (7000,),
}


def condition_filter(condition: Condition) -> str:
    """Render the `filter=` fragment for one condition bucket."""
    ids = "|".join(str(i) for i in CONDITION_IDS[condition])
    return f"conditionIds:{{{ids}}}"
