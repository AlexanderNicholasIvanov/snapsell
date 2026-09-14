"""Where "what did it actually sell for" comes from.

eBay's Marketplace Insights API is restricted, so v1 ships an LLM estimate that the
UI must label as an estimate. Swapping in real data later means adding one class
here that implements SoldPriceSource, and choosing it in main.create_app.
"""

from __future__ import annotations

from typing import Protocol

from snapsell.llm.client import LlmClient
from snapsell.models import Item, SoldEstimate


class SoldPriceSource(Protocol):
    async def estimate(self, item: Item, asking_median: float | None) -> SoldEstimate | None: ...


class NoSoldEstimate:
    async def estimate(self, item: Item, asking_median: float | None) -> SoldEstimate | None:
        return None


class LlmSoldEstimate:
    def __init__(self, llm: LlmClient) -> None:
        self._llm = llm

    async def estimate(self, item: Item, asking_median: float | None) -> SoldEstimate | None:
        est = await self._llm.sold_estimate(item, asking_median)
        if est is None:
            return None
        # Force the label regardless of what the model claims.
        return est.model_copy(update={"source": "llm_estimate"})
