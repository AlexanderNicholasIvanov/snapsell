"""Claude client for identification, sold-price estimation, and bundle copy.

All three calls use structured outputs (`output_config.format`) so the response is a
JSON document that already matches a schema, and server-side refusal fallbacks
(`fallbacks: "default"`) so a safety-classifier decline is re-run on Anthropic's
recommended substitute inside the same request.

Verified against the Python SDK docs shipped with the claude-api skill (SDK 1.x).
Not exercised against the live API in this repository's tests: tests use FakeLlm.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Protocol

import anthropic
from pydantic import BaseModel, ConfigDict, Field

from snapsell.llm import prompts
from snapsell.models import BundleItem, Condition, Item, ListingText, SoldEstimate

FALLBACK_BETA = "server-side-fallback-2026-07-01"


class LlmError(Exception):
    """Any failure talking to the model. Mapped to 502 by the API layer."""


class LlmRefused(LlmError):
    """The model (and its fallback) declined the request. Mapped to 422."""


@dataclass(frozen=True)
class IdentifyResult:
    item: Item
    listing_text: ListingText


class LlmClient(Protocol):
    async def identify(self, image: bytes, media_type: str, hint: str | None) -> IdentifyResult: ...

    async def sold_estimate(
        self, item: Item, asking_median: float | None
    ) -> SoldEstimate | None: ...

    async def bundle_text(self, items: list[BundleItem], bundle_price: float) -> ListingText: ...


# --- Output schemas -------------------------------------------------------------
# Every field is required and nullable where optional, because structured outputs
# want every property listed in `required` with additionalProperties false.


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class IdentifyOutput(_Strict):
    name: str
    brand: str | None
    model: str | None
    category: str
    condition: Condition
    attributes: list[str]
    search_query: str
    confidence: float = Field(ge=0, le=1)
    notes: str | None
    title: str
    description: str


class SoldOutput(_Strict):
    low: float
    high: float
    rationale: str


class BundleOutput(_Strict):
    title: str
    description: str


def strict_schema(model: type[BaseModel]) -> dict[str, Any]:
    """JSON schema with every property required and no extras, recursively."""
    schema = model.model_json_schema()

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            if node.get("type") == "object" and "properties" in node:
                node["additionalProperties"] = False
                node["required"] = list(node["properties"].keys())
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(schema)
    return schema


# --- Client ---------------------------------------------------------------------


class ClaudeClient:
    def __init__(
        self,
        model: str,
        effort: str = "medium",
        client: anthropic.AsyncAnthropic | None = None,
    ) -> None:
        self._client = client or anthropic.AsyncAnthropic()
        self._model = model
        self._effort = effort

    async def _structured(
        self,
        system: str,
        content: list[dict[str, Any]],
        output: type[BaseModel],
        max_tokens: int = 2048,
    ) -> dict[str, Any]:
        try:
            response = await self._client.beta.messages.create(
                model=self._model,
                max_tokens=max_tokens,
                system=system,
                messages=[{"role": "user", "content": content}],
                thinking={"type": "adaptive"},
                output_config={
                    "effort": self._effort,
                    "format": {"type": "json_schema", "schema": strict_schema(output)},
                },
                betas=[FALLBACK_BETA],
                fallbacks="default",
            )
        except anthropic.RateLimitError as e:
            raise LlmError("rate limited by Anthropic") from e
        except anthropic.APIStatusError as e:
            raise LlmError(f"Anthropic API error {e.status_code}: {e.message}") from e
        except anthropic.APIConnectionError as e:
            raise LlmError("could not reach Anthropic") from e
        except TypeError as e:
            # The SDK raises TypeError when no credential can be resolved
            # (ANTHROPIC_API_KEY unset). Surface it as a 502 with a clear log line
            # rather than a bare 500.
            raise LlmError(f"Anthropic client misconfigured: {e}") from e

        if response.stop_reason == "refusal":
            details = getattr(response, "stop_details", None)
            category = getattr(details, "category", None) if details else None
            raise LlmRefused(f"model declined the request (category={category})")
        if response.stop_reason == "max_tokens":
            raise LlmError("model output was truncated")

        text = next((b.text for b in response.content if b.type == "text"), None)
        if text is None:
            raise LlmError("model returned no text block")
        try:
            return json.loads(text)
        except json.JSONDecodeError as e:
            raise LlmError("model returned invalid JSON") from e

    async def identify(self, image: bytes, media_type: str, hint: str | None) -> IdentifyResult:
        import base64

        user_text = (
            prompts.IDENTIFY_USER_WITH_HINT.format(hint=hint) if hint else prompts.IDENTIFY_USER
        )
        content = [
            {
                "type": "image",
                "source": {
                    "type": "base64",
                    "media_type": media_type,
                    "data": base64.standard_b64encode(image).decode("ascii"),
                },
            },
            {"type": "text", "text": user_text},
        ]
        data = await self._structured(prompts.IDENTIFY_SYSTEM, content, IdentifyOutput)
        out = IdentifyOutput.model_validate(data)
        item = Item(
            name=out.name[:120],
            brand=out.brand,
            model=out.model,
            category=out.category[:80],
            condition=out.condition,
            attributes=out.attributes[:12],
            search_query=out.search_query[:200],
            confidence=out.confidence,
            notes=out.notes,
        )
        text = ListingText(title=out.title[:99], description=out.description[:2000])
        return IdentifyResult(item=item, listing_text=text)

    async def sold_estimate(self, item: Item, asking_median: float | None) -> SoldEstimate | None:
        user = prompts.SOLD_USER.format(
            name=item.name,
            brand=item.brand or "unknown",
            model=item.model or "unknown",
            category=item.category,
            condition=item.condition,
            attributes=", ".join(item.attributes) or "none",
            asking_median=f"${asking_median:.2f}" if asking_median else "no comps found",
        )
        data = await self._structured(
            prompts.SOLD_SYSTEM, [{"type": "text", "text": user}], SoldOutput, max_tokens=512
        )
        out = SoldOutput.model_validate(data)
        if out.low <= 0 and out.high <= 0:
            return None
        low, high = sorted((max(out.low, 0), max(out.high, 0)))
        return SoldEstimate(
            low=low, high=high, source="llm_estimate", rationale=out.rationale[:300] or None
        )

    async def bundle_text(self, items: list[BundleItem], bundle_price: float) -> ListingText:
        lines = []
        for entry in items:
            i = entry.item
            lines.append(
                f"- {i.name} ({i.condition.replace('_', ' ')}), "
                f"individually ${entry.price:.0f}"
                + (f"; {', '.join(i.attributes)}" if i.attributes else "")
            )
        user = prompts.BUNDLE_USER.format(bundle_price=bundle_price, items="\n".join(lines))
        data = await self._structured(
            prompts.BUNDLE_SYSTEM, [{"type": "text", "text": user}], BundleOutput, max_tokens=1024
        )
        out = BundleOutput.model_validate(data)
        return ListingText(title=out.title[:99], description=out.description[:2000])
