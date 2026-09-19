# Add an endpoint (or change a payload)

## Facts

| Key | Value |
|---|---|
| Contracts | `contracts/*.schema.json` (JSON Schema 2020-12) + `contracts/examples/*.json`; the source of truth for all three sides |
| Backend | route in `backend/snapsell/main.py`, pydantic models in `backend/snapsell/models.py`, contract tests `backend/tests/test_contracts.py` (the only side that validates against the schema itself) |
| iOS | DTOs `ios/SnapSell/Data/Remote/Dtos.swift`, client `SnapsellApi.swift`, round-trip test `ios/SnapSellTests/ContractsTests.swift` |
| Android | DTOs `android/app/src/main/java/com/alexivanov/snapsell/data/remote/dto/`, client `SnapsellApi.kt`, round-trip test `android/app/src/test/java/com/alexivanov/snapsell/contracts/ContractsRoundTripTest.kt` |
| Auth | every non-health route depends on `metered_user` (Bearer token + daily cap) |
| Known gap | `contracts/examples/identify.request.json` does not exist, so `IdentifyRequest` is round-tripped on neither client; the iOS test silently skips missing files |

## Goal

The new or changed payload has one schema, one example, and passes contract tests on backend, iOS, and Android, with the route reachable through the deployed backend.

## Preconditions

- The shape is agreed (fields, types, enums, optionality) before any code.
- Backend, iOS, and Android toolchains build on this machine (`uv run pytest`, `xcodebuild test`, `./gradlew testDebugUnitTest`).

## Steps

1. Contract first: add or edit `contracts/<name>.schema.json` (set `$id`, reuse `item.schema.json` by `$ref`) and `contracts/examples/<name>.json`. Add the row to `contracts/README.md`.
2. Backend:
   - Model in `models.py` (snake_case, `extra="forbid"` like its neighbours).
   - Route in `main.py` with `Depends(metered_user)`; map domain errors to the existing handlers (`LlmRefused` 422, `LlmError`/`EbayError` 502).
   - Add the example to all three parametrize lists in `tests/test_contracts.py`; add a route test in `tests/test_api.py` using the fakes in `tests/conftest.py`.
   - `cd backend && uv run ruff check && uv run ruff format --check && uv run pytest`.
3. iOS:
   - DTO in `Dtos.swift` (Codable, snake_case via `SnapsellJSON`), method in `SnapsellApi.swift`, repository call in `Data/Repository/RemoteRepositories.swift`.
   - Add a `roundTrip(...)` line in `ContractsTests.swift`; before relying on it, confirm the example file is found (the test skips silently if not).
   - `cd ios && xcodegen generate && xcodebuild test -project SnapSell.xcodeproj -scheme SnapSell -destination 'platform=iOS Simulator,id=48668AB7-04A7-4385-955E-72C5F3CA69A2' CODE_SIGNING_ALLOWED=NO`.
4. Android:
   - DTO with `@SerialName` per field, Retrofit method in `SnapsellApi.kt`, repository call.
   - Add a `roundTrip(...)` case in `ContractsRoundTripTest.kt` (it fails loudly on a missing example).
   - `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew --no-daemon testDebugUnitTest`.
5. If the change needs a new env var or key: `deploy/.env.example`, `backend/.env.example`, `backend/snapsell/config.py`, then the `deploy-backend` playbook step for editing `.env` on the host.
6. One PR, squash-merged after `backend`, `ios`, and `android` CI are green. Then run `deploy-backend`.

## Verification

- All three test suites green locally and in CI.
- Against the deployed backend, an unauthenticated call returns 401: `curl -s -o /dev/null -w '%{http_code}\n' -X POST https://srv1343782.hstgr.cloud/<route>`.
- From a device build, the feature that uses the route works end to end.

## Rollback

Revert the squash commit (`git revert <sha>`), merge, run `deploy-backend`. Clients on the old build keep working because unknown response fields are tolerated on both clients and unknown request fields are rejected by the backend with 422, not 500.
