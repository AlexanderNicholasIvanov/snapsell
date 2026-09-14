# Contracts

JSON Schema (draft 2020-12) for every payload that crosses the network between
the Android app and the backend. Both sides validate against these files in
tests, so a change here must be made once and consumed twice.

| File | Direction | Used by |
|---|---|---|
| `item.schema.json` | shared type | embedded in every other schema |
| `identify.request.schema.json` | app -> backend `POST /identify` | one cutout image in |
| `identify.response.schema.json` | backend -> app | identified `Item` + listing text |
| `price.request.schema.json` | app -> backend `POST /price` | confirmed `Item` + local sale factor |
| `price.response.schema.json` | backend -> app | `PriceQuote` with comps and sold estimate |
| `bundle.request.schema.json` | app -> backend `POST /bundle` | items to bundle |
| `bundle.response.schema.json` | backend -> app | bundle title and description |
| `listing.schema.json` | on-device | Room `Listing` shape, kept here so sync can reuse it |
| `examples/` | fixtures | valid instances used by tests on both sides |

Conventions: snake_case keys, ISO 8601 UTC timestamps, money as decimal numbers
in USD, enums as lowercase strings.
