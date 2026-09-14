from __future__ import annotations

from snapsell.llm.client import BundleOutput, IdentifyOutput, SoldOutput, strict_schema


def _assert_strict(node) -> None:
    if isinstance(node, dict):
        if node.get("type") == "object" and "properties" in node:
            assert node["additionalProperties"] is False
            assert set(node["required"]) == set(node["properties"])
        for v in node.values():
            _assert_strict(v)
    elif isinstance(node, list):
        for v in node:
            _assert_strict(v)


def test_output_schemas_are_strict() -> None:
    for model in (IdentifyOutput, SoldOutput, BundleOutput):
        _assert_strict(strict_schema(model))


def test_identify_schema_condition_enum() -> None:
    schema = strict_schema(IdentifyOutput)
    assert set(schema["properties"]["condition"]["enum"]) == {
        "new",
        "like_new",
        "good",
        "fair",
        "for_parts",
    }
