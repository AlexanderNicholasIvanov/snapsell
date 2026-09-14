from __future__ import annotations

import pytest

from snapsell.ebay.client import EbayListing
from snapsell.pricing.formula import compute_quote, price_point, trim_outliers


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        (0, 1.0),
        (-5, 1.0),
        (0.4, 1.0),
        (7.49, 7.0),
        (7.5, 8.0),
        (12.5, 13.0),
        (19.99, 20.0),
        (20.0, 20.0),
        (22.4, 20.0),
        (22.5, 25.0),
        (87.0, 85.0),
        (99.99, 100.0),
        (100.0, 100.0),
        (104.9, 100.0),
        (105.0, 110.0),
        (1234.0, 1230.0),
    ],
)
def test_price_point(value: float, expected: float) -> None:
    assert price_point(value) == expected


def test_trim_outliers_needs_four_values() -> None:
    assert trim_outliers([1, 100, 1000]) == [1, 100, 1000]


def test_trim_outliers_drops_far_values() -> None:
    kept = trim_outliers([13, 80, 85, 90, 95, 100, 500])
    assert kept == [80, 85, 90, 95, 100]


def _listing(price: float, shipping: float = 0.0) -> EbayListing:
    return EbayListing(
        item_id="x",
        title="t",
        price=price,
        shipping=shipping,
        condition=None,
        condition_id=None,
        url="https://www.ebay.com/itm/1",
        image_url=None,
    )


def test_compute_quote_uses_price_plus_shipping_and_local_factor() -> None:
    listings = [_listing(80, 10), _listing(90), _listing(100), _listing(110, 5)]
    q = compute_quote(listings, 0.85)
    # totals: 90, 90, 100, 115 -> median 95 -> * 0.85 = 80.75 -> price point 80
    assert q.asking_median == 95.0
    assert q.asking_low == 90.0
    assert q.asking_high == 115.0
    assert q.count == 4
    assert q.suggested == 80.0


def test_compute_quote_empty() -> None:
    q = compute_quote([], 0.85)
    assert q.suggested is None and q.asking_median is None and q.count == 0
