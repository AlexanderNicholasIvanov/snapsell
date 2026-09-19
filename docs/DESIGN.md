# SnapSell design decisions

Recorded from the planning interview on 2026-09-14. Each row is a decision that
later work should not silently reopen.

| # | Topic | Decision |
|---|---|---|
| 1 | Facebook integration | No API, no scraping. Staged hand-off: the user taps Publish. |
| 2 | Hand-off mechanics | Photos to a "Resale" gallery album, title/price/description on the clipboard, `fb://marketplace` deep link with web fallback. Return prompt: Listed, Skipped, Try Again. Sold is a manual status. |
| 3 | Audience | Friends-and-family sideloaded APK now, Play Store later. |
| 4 | Pricing sources | Vision LLM identifies. eBay Browse API supplies asking-price comps. |
| 5 | Sold prices | eBay Marketplace Insights is restricted. v1 uses an LLM estimate labelled as such, behind a `SoldPriceSource` interface. Application to eBay submitted in parallel. |
| 6 | Photo scenarios | One or many items per photo. Every item identified. User chooses separate listings or a bundle. |
| 7 | Item isolation | ML Kit Subject Segmentation on-device. Outlines for selection, cutouts as listing image, retake per item, tap-to-add for misses. Each cutout identified individually. |
| 8 | Bundle pricing | Sum of item prices times a discount, default 0.8, slider or typed. LLM writes bundle title and description only. |
| 9 | Android stack | Kotlin, Jetpack Compose, CameraX, ML Kit, Room, min API 24. |
| 10 | LLM and backend | Claude Opus 5 behind a FastAPI backend. Firebase Auth with an email allowlist. Only the backend URL ships in the apps. Hosting: originally Cloud Run; since 2026-09-16 Docker + Caddy on the owner's VPS (`deploy/`), deployed on demand, revisit only if public load needs it. |
| 11 | Repo | Monorepo: `android/`, `backend/`, `ios/`, `contracts/`. All sides test against the same JSON Schemas and examples. |
| 12 | Inventory storage | Room on the phone. UUID keys, `updatedAt`, nullable `remoteId`. Backend stateless. |
| 13 | Confirmation flow | Always confirm identity before pricing. Confirm All for multi-item scans. Condition chips on every card. Any edit re-prices. |
| 14 | Price formula | `EBAY_US`. Condition-filtered median with unfiltered fallback. Price plus shipping per comp. Outliers outside 0.3x–3x median dropped. Local-sale factor 0.85, user adjustable. Five comps shown. Suggested and final price logged per item. |
| 15 | Milestones | M1 single item, M2 multi-item, M3 bundles, M4 public polish. Built back to back. |
| 16 | Testing | Backend: unit tests with recorded eBay and Claude fixtures, mock-transport tests for both clients, contract tests. Android: unit tests for bundle maths and contract round-trips. CI compiles the APK. |
| 17 | iOS | Native SwiftUI port (2026-09-15), same nine screens and hand-off as Android. Apple Vision instance masks instead of ML Kit. Bundle id `com.victorivanov.snapsell`, owned and shipped by Victor; Android stays `com.alexivanov.snapsell`. Sign in with Apple plus Google on iOS. |
| 18 | Playbooks | Operational procedures are written once in `playbooks/` and followed by any agent or person; credential and irreversible steps are `[HUMAN]` gates. Backend deploy is a manual `workflow_dispatch`, never push-to-deploy. LLM prompts move to versioned playbook files in `backend/playbooks/`; the pricing formula stays code until 50 real items say otherwise. |
| 19 | Next milestone | M1 real: one item through live Claude and live eBay to a Marketplace listing from a phone, ten times, before any multi-item or bundle work. Direct-to-device builds until a second tester needs TestFlight. |

## Accuracy target

Suggested price within 20 percent of the final listed price on the first 50 real
items, using the suggested/final pairs the app logs. The backend fixtures are the
regression benchmark for the formula.

## Open items

- Verify the Marketplace deep link on a real device. Facebook does not document it.
- Apply for eBay Marketplace Insights. Expect a slow or negative answer.
- Test ML Kit on a crowded shelf early in M2; the tap-to-add path is the safety net.
- Backend spend: `SNAPSELL_DAILY_CAP` is in-memory and per instance. Set a hard
  monthly cap on the Anthropic key in the console as the real backstop.
