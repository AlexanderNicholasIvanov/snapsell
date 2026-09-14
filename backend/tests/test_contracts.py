"""The contracts folder is the source of truth. These tests keep the backend honest about it."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

from snapsell.models import (
    BundleRequest,
    BundleResponse,
    IdentifyResponse,
    Item,
    PriceQuote,
    PriceRequest,
)
from tests.conftest import CONTRACTS, load_example

SCHEMAS = sorted(p for p in CONTRACTS.glob("*.schema.json"))


def registry() -> Registry:
    reg = Registry()
    for path in SCHEMAS:
        doc = json.loads(path.read_text())
        res = Resource.from_contents(doc)
        reg = reg.with_resource(doc["$id"], res).with_resource(path.name, res)
    return reg


def validator(schema_name: str) -> Draft202012Validator:
    doc = json.loads((CONTRACTS / schema_name).read_text())
    return Draft202012Validator(doc, registry=registry())


def validate(schema_name: str, instance: dict) -> None:
    errors = sorted(validator(schema_name).iter_errors(instance), key=lambda e: e.path)
    assert not errors, "\n".join(f"{list(e.path)}: {e.message}" for e in errors)


@pytest.mark.parametrize("path", SCHEMAS, ids=lambda p: p.name)
def test_schemas_are_valid_draft_2020_12(path: Path) -> None:
    Draft202012Validator.check_schema(json.loads(path.read_text()))


@pytest.mark.parametrize(
    ("example", "schema"),
    [
        ("item.json", "item.schema.json"),
        ("identify.response.json", "identify.response.schema.json"),
        ("price.request.json", "price.request.schema.json"),
        ("price.response.json", "price.response.schema.json"),
        ("bundle.request.json", "bundle.request.schema.json"),
        ("bundle.response.json", "bundle.response.schema.json"),
        ("listing.json", "listing.schema.json"),
    ],
)
def test_examples_match_schemas(example: str, schema: str) -> None:
    validate(schema, load_example(example))


@pytest.mark.parametrize(
    ("example", "model", "schema"),
    [
        ("item.json", Item, "item.schema.json"),
        ("identify.response.json", IdentifyResponse, "identify.response.schema.json"),
        ("price.request.json", PriceRequest, "price.request.schema.json"),
        ("price.response.json", PriceQuote, "price.response.schema.json"),
        ("bundle.request.json", BundleRequest, "bundle.request.schema.json"),
        ("bundle.response.json", BundleResponse, "bundle.response.schema.json"),
    ],
)
def test_pydantic_models_round_trip_examples(example: str, model, schema: str) -> None:
    data = load_example(example)
    parsed = model.model_validate(data)
    dumped = parsed.model_dump(mode="json")
    validate(schema, dumped)
    assert model.model_validate(dumped) == parsed
