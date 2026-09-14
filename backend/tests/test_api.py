from __future__ import annotations

import base64

from fastapi.testclient import TestClient

from tests.conftest import FakeLlm, load_example
from tests.test_contracts import validate

PNG_1PX = base64.b64encode(
    bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
        "0000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082"
    )
).decode()


def test_health(client: TestClient) -> None:
    assert client.get("/health").json() == {"status": "ok"}


def test_identify_returns_contract_shaped_response(client: TestClient) -> None:
    r = client.post("/identify", json={"image_base64": PNG_1PX, "media_type": "image/png"})
    assert r.status_code == 200, r.text
    body = r.json()
    validate("identify.response.schema.json", body)
    assert body["item"]["name"] == "Apple iPad Air 2 64GB Wi-Fi"
    assert body["request_id"].startswith("req_")


def test_identify_rejects_bad_base64(client: TestClient) -> None:
    r = client.post("/identify", json={"image_base64": "not base64!!", "media_type": "image/png"})
    assert r.status_code == 400


def test_identify_rejects_unknown_fields(client: TestClient) -> None:
    r = client.post(
        "/identify", json={"image_base64": PNG_1PX, "media_type": "image/png", "extra": 1}
    )
    assert r.status_code == 422


def test_identify_too_large(client: TestClient) -> None:
    settings = client.app.state.settings
    big = base64.b64encode(b"\0" * (settings.max_image_bytes + 1)).decode()
    r = client.post("/identify", json={"image_base64": big, "media_type": "image/jpeg"})
    assert r.status_code == 413


def test_identify_refusal_is_422(settings, fake_ebay) -> None:
    from snapsell.main import create_app

    app = create_app(settings=settings, llm=FakeLlm(refuse=True), ebay=fake_ebay)
    with TestClient(app) as c:
        r = c.post("/identify", json={"image_base64": PNG_1PX, "media_type": "image/png"})
    assert r.status_code == 422


def test_price_returns_contract_shaped_quote(client: TestClient) -> None:
    r = client.post("/price", json=load_example("price.request.json"))
    assert r.status_code == 200, r.text
    body = r.json()
    validate("price.response.schema.json", body)
    assert body["suggested_price"] == 75.0
    assert body["condition_filtered"] is True
    assert body["estimated_sold"]["source"] == "llm_estimate"


def test_price_default_factor(client: TestClient) -> None:
    payload = load_example("price.request.json")
    del payload["local_sale_factor"]
    r = client.post("/price", json=payload)
    assert r.status_code == 200
    assert r.json()["local_sale_factor"] == 0.85


def test_bundle(client: TestClient) -> None:
    r = client.post("/bundle", json=load_example("bundle.request.json"))
    assert r.status_code == 200, r.text
    validate("bundle.response.schema.json", r.json())


def test_bundle_needs_two_items(client: TestClient) -> None:
    payload = load_example("bundle.request.json")
    payload["items"] = payload["items"][:1]
    assert client.post("/bundle", json=payload).status_code == 422


def test_daily_cap(client: TestClient) -> None:
    # settings fixture sets daily_cap=5 and identify counts against it.
    codes = [
        client.post(
            "/identify", json={"image_base64": PNG_1PX, "media_type": "image/png"}
        ).status_code
        for _ in range(6)
    ]
    assert codes == [200, 200, 200, 200, 200, 429]


def test_me_dev_user(client: TestClient) -> None:
    assert client.get("/me").json() == {"uid": "dev", "email": "dev@localhost"}
