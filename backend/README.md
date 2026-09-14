# SnapSell backend

FastAPI service that does the two paid things the phone should not: call Claude to
identify an item from a photo, and call eBay for comparable listings. Everything
else (camera, segmentation, inventory, the Marketplace hand-off) lives in the app.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness |
| GET | `/me` | Bearer | confirms the token and allowlist without spending anything |
| POST | `/identify` | Bearer, metered | one image in, `Item` + listing text out |
| POST | `/price` | Bearer, metered | confirmed `Item` in, `PriceQuote` out |
| POST | `/bundle` | Bearer, metered | items + bundle price in, bundle title/description out |

Request and response shapes are defined once in [`../contracts`](../contracts) and
enforced by `tests/test_contracts.py`.

## Run locally

```bash
uv sync
cp .env.example .env   # fill in keys
uv run --env-file .env uvicorn snapsell.main:app --reload --port 8000
```

For a phone or emulator on the same machine, the Android app defaults to
`http://10.0.2.2:8000/` (emulator loopback). For a physical phone, put your Mac's
LAN address in the app's `snapsell.backendUrl` and bind uvicorn with `--host 0.0.0.0`.

To develop the app before Firebase is configured, set `SNAPSELL_AUTH_DISABLED=1`.
Every request is then treated as the `dev` user. Never set this on a deployed instance.

## Tests

```bash
uv run pytest
uv run ruff check .
```

No test touches the network. eBay responses are recorded fixtures under
`tests/fixtures/` in the exact Browse API shape, and the LLM is a fake that returns
recorded structured outputs. The same fixtures double as the pricing benchmark:
change the formula, run the tests, see what moves.

## How a price is computed

1. Search eBay Browse (`EBAY_US`, USD, US-located) with the item's `search_query`
   filtered to the item's condition bucket (see `snapsell/ebay/conditions.py`).
2. If fewer than `SNAPSELL_MIN_FILTERED_COMPS` (default 3) come back, search again
   with no condition filter and set `condition_filtered=false` so the UI can say so.
3. Each listing's total is price plus the first USD shipping option.
4. Drop totals outside 0.3x to 3x the raw median (accessories, lots).
5. `asking_median` is the median of what is left. `suggested_price` is
   `asking_median * local_sale_factor` (default 0.85) rounded to a price point
   (nearest $1 under $20, $5 under $100, $10 above).
6. `estimated_sold` comes from `SoldPriceSource`. v1 is an LLM estimate and is
   labelled `llm_estimate`; the UI must present it as an estimate. When eBay grants
   Marketplace Insights access, implement a second `SoldPriceSource` and switch it
   in `main.create_app`.

## Configuration

See `.env.example`. All settings are `SNAPSELL_*` environment variables read by
`snapsell/config.py`, except `ANTHROPIC_API_KEY` and `GOOGLE_APPLICATION_CREDENTIALS`,
which their SDKs read directly.

## Deploy (Cloud Run)

```bash
gcloud run deploy snapsell-backend --source . --region us-central1 \
  --set-env-vars SNAPSELL_ALLOWED_EMAILS=you@example.com \
  --set-secrets ANTHROPIC_API_KEY=anthropic-key:latest,SNAPSELL_EBAY_CLIENT_ID=ebay-id:latest,SNAPSELL_EBAY_CLIENT_SECRET=ebay-secret:latest
```

Grant the Cloud Run service account the Firebase Admin role so
`firebase_admin.initialize_app()` can verify ID tokens with application default
credentials. Scale-to-zero is fine; the eBay app token is re-minted on cold start.
