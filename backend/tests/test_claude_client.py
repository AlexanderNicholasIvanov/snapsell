"""Exercises ClaudeClient against a mock transport: request shape, parsing, refusals.

No network. This is the closest we get to the live API in CI; the request body it
checks is the one the claude-api docs prescribe for structured outputs + fallbacks.
"""

from __future__ import annotations

import json

import anthropic
import httpx2
import pytest
from anthropic import DefaultAsyncHttpxClient

from snapsell.llm.client import FALLBACK_BETA, ClaudeClient, LlmError, LlmRefused
from snapsell.models import BundleItem, Item
from tests.conftest import load_example, load_fixture

pytestmark = pytest.mark.anyio


def _message(text: str | None, stop_reason: str = "end_turn", **extra) -> dict:
    content = [{"type": "text", "text": text}] if text is not None else []
    return {
        "id": "msg_test",
        "type": "message",
        "role": "assistant",
        "model": "claude-opus-5",
        "content": content,
        "stop_reason": stop_reason,
        "stop_sequence": None,
        "usage": {"input_tokens": 10, "output_tokens": 10},
        **extra,
    }


def _client(responder):
    captured: dict = {}

    def handler(request: httpx2.Request) -> httpx2.Response:
        captured["body"] = json.loads(request.content)
        captured["headers"] = dict(request.headers)
        return httpx2.Response(200, json=responder(captured["body"]))

    sdk = anthropic.AsyncAnthropic(
        api_key="test",
        max_retries=0,
        http_client=DefaultAsyncHttpxClient(transport=httpx2.MockTransport(handler)),
    )
    return ClaudeClient("claude-opus-5", "medium", client=sdk), captured


async def test_identify_builds_documented_request_and_parses_output() -> None:
    fixture = load_fixture("claude_identify_ipad.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))

    result = await client.identify(b"\x89PNG", "image/png", hint="a tablet")

    body = captured["body"]
    assert body["model"] == "claude-opus-5"
    assert body["thinking"] == {"type": "adaptive"}
    assert body["fallbacks"] == "default"
    assert captured["headers"]["anthropic-beta"] == FALLBACK_BETA
    assert body["output_config"]["effort"] == "medium"
    fmt = body["output_config"]["format"]
    assert fmt["type"] == "json_schema" and fmt["schema"]["additionalProperties"] is False
    image_block, text_block = body["messages"][0]["content"]
    assert image_block["type"] == "image" and image_block["source"]["media_type"] == "image/png"
    assert "a tablet" in text_block["text"]

    assert result.item.name == fixture["name"]
    assert result.item.condition == "good"
    assert result.listing_text.title == fixture["title"]


async def test_refusal_raises_llm_refused() -> None:
    client, _ = _client(
        lambda body: _message(
            None, stop_reason="refusal", stop_details={"type": "refusal", "category": "other"}
        )
    )
    with pytest.raises(LlmRefused):
        await client.identify(b"x", "image/jpeg", None)


async def test_truncation_raises_llm_error() -> None:
    client, _ = _client(lambda body: _message('{"low": 1', stop_reason="max_tokens"))
    with pytest.raises(LlmError):
        await client.sold_estimate(Item(**load_example("item.json")), 50.0)


async def test_invalid_json_raises_llm_error() -> None:
    client, _ = _client(lambda body: _message("not json"))
    with pytest.raises(LlmError):
        await client.sold_estimate(Item(**load_example("item.json")), 50.0)


async def test_sold_estimate_zero_means_no_estimate() -> None:
    client, _ = _client(lambda body: _message('{"low": 0, "high": 0, "rationale": ""}'))
    assert await client.sold_estimate(Item(**load_example("item.json")), None) is None


async def test_sold_estimate_orders_bounds_and_labels_source() -> None:
    client, _ = _client(lambda body: _message('{"low": 90, "high": 60, "rationale": "r"}'))
    est = await client.sold_estimate(Item(**load_example("item.json")), 80.0)
    assert est is not None and (est.low, est.high) == (60.0, 90.0)
    assert est.source == "llm_estimate"


async def test_bundle_text_lists_every_item_in_prompt() -> None:
    fixture = load_fixture("claude_bundle_kitchen.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))
    req = load_example("bundle.request.json")
    items = [BundleItem(**x) for x in req["items"]]

    text = await client.bundle_text(items, req["bundle_price"])

    prompt = captured["body"]["messages"][0]["content"][0]["text"]
    assert "KitchenAid" in prompt and "Cuisinart" in prompt and "$160" in prompt
    assert text.title == fixture["title"]


async def test_api_status_error_becomes_llm_error() -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(500, json={"type": "error", "error": {"message": "boom"}})

    sdk = anthropic.AsyncAnthropic(
        api_key="test",
        max_retries=0,
        http_client=DefaultAsyncHttpxClient(transport=httpx2.MockTransport(handler)),
    )
    client = ClaudeClient("claude-opus-5", client=sdk)
    with pytest.raises(LlmError):
        await client.sold_estimate(Item(**load_example("item.json")), 1.0)


async def test_identify_system_prompt_from_playbooks() -> None:
    """Verify the system prompt matches the loaded playbook."""
    from snapsell.llm import prompts

    fixture = load_fixture("claude_identify_ipad.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))

    await client.identify(b"\x89PNG", "image/png", None)

    # The system prompt in the request should match the playbook
    request_system = captured["body"]["system"]
    assert request_system == prompts.IDENTIFY_SYSTEM


async def test_identify_user_prompt_from_playbooks() -> None:
    """Verify the user prompt matches the loaded playbook."""
    from snapsell.llm import prompts

    fixture = load_fixture("claude_identify_ipad.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))

    await client.identify(b"\x89PNG", "image/png", None)

    # The user text should match the base IDENTIFY_USER prompt
    prompt_text = captured["body"]["messages"][0]["content"][1]["text"]
    assert prompt_text == prompts.IDENTIFY_USER


async def test_identify_user_prompt_with_hint_from_playbooks() -> None:
    """Verify the user prompt with hint matches the loaded playbook."""
    from snapsell.llm import prompts

    fixture = load_fixture("claude_identify_ipad.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))

    hint = "a tablet device"
    await client.identify(b"\x89PNG", "image/png", hint)

    # The user text should match the WITH_HINT prompt formatted with the hint
    prompt_text = captured["body"]["messages"][0]["content"][1]["text"]
    expected = prompts.IDENTIFY_USER_WITH_HINT.format(hint=hint)
    assert prompt_text == expected


async def test_sold_system_prompt_from_playbooks() -> None:
    """Verify the sold estimate system prompt matches the playbook."""
    from snapsell.llm import prompts

    client, captured = _client(lambda body: _message('{"low": 50, "high": 100, "rationale": "r"}'))

    item = Item(**load_example("item.json"))
    await client.sold_estimate(item, 80.0)

    # The system prompt should match the playbook
    request_system = captured["body"]["system"]
    assert request_system == prompts.SOLD_SYSTEM


async def test_bundle_system_prompt_from_playbooks() -> None:
    """Verify the bundle system prompt matches the playbook."""
    from snapsell.llm import prompts

    fixture = load_fixture("claude_bundle_kitchen.json")
    client, captured = _client(lambda body: _message(json.dumps(fixture)))
    req = load_example("bundle.request.json")
    items = [BundleItem(**x) for x in req["items"]]

    await client.bundle_text(items, req["bundle_price"])

    # The system prompt should match the playbook
    request_system = captured["body"]["system"]
    assert request_system == prompts.BUNDLE_SYSTEM
