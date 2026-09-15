# SnapSell

Photograph the stuff you want to sell, get each item identified and priced from
real eBay comps, and stage a Facebook Marketplace listing in a few taps.

Android app plus a small backend. Friends-and-family APK first, Play Store later.

```
snapsell/
  android/     Kotlin + Jetpack Compose app (camera, ML Kit segmentation, Room, hand-off)
  backend/     FastAPI service (Claude identification, eBay comps, Firebase Auth)
  contracts/   JSON Schemas shared by both sides, with example fixtures
  docs/        design decisions
  .github/     one path-filtered workflow per folder
```

## How it works

1. **Capture.** One photo of one item, or a pile. ML Kit Subject Segmentation runs
   on the phone and outlines every object. Tap to select, tap to add one it missed,
   retake any cutout.
2. **Identify.** Each cutout goes to `POST /identify`. Claude returns brand, model,
   condition, an eBay search query, and draft listing text. You confirm or correct
   before anything is priced. "Confirm all" when there are several.
3. **Price.** `POST /price` pulls comparable eBay listings filtered to the item's
   condition, takes the median of price plus shipping, applies a local-sale factor
   (default 0.85), and shows the five comps behind the number. An estimated sold
   range is shown too, clearly labelled as an estimate.
4. **Bundle or list.** Sell items separately, or bundle them: sum of prices times a
   discount (default 0.8), Claude writes the bundle title and description.
5. **Hand off.** Photos are saved to a "Resale" album, the title, price, and
   description are copied to the clipboard, and Facebook Marketplace opens. Paste,
   pick the photos, publish. Back in the app, mark it Listed, Skipped, or Try Again.

There is no Facebook API integration and no scraping. The last mile is manual by design.

## Getting started

- Backend: see [backend/README.md](backend/README.md). `uv sync && uv run pytest`.
- Android: see [android/README.md](android/README.md). Open in Android Studio or
  `./gradlew assembleDebug`.
- Design decisions and open questions: [docs/DESIGN.md](docs/DESIGN.md).

## Install on a phone

Every push to `main` that touches the app publishes a signed APK under
[Releases](https://github.com/AlexanderNicholasIvanov/snapsell/releases).

1. On the phone, open the latest release and download the `.apk`.
2. Open the download. Android asks once to allow installs from your browser.
3. Open SnapSell. If Google sign-in is not configured, tap "Continue without sign-in".
4. In Settings, set the Backend URL to wherever the backend is deployed.

Every build is signed with the same key, so a newer release installs over the
older one and keeps your inventory. If you ever see "app not installed" on an
update, the key changed; uninstall and reinstall once.

## Updates

On launch the app looks at the latest GitHub Release. If its version is newer
than the installed one, a banner offers **Download** or **Not now**. Nothing is
downloaded or installed unless you tap Download, and "Not now" silences that
version until the next one ships. Settings has a manual "Check for updates".

The check reads the Releases API anonymously, which works only while the repo
is public. For a private repo, either set the repository variable
`SNAPSELL_UPDATE_REPO` to a separate public releases-only repo, or set the
secret `SNAPSELL_UPDATE_TOKEN` to a fine-grained token with read-only Contents
access on this repo (it is baked into the APK, so keep its scope minimal).

Release signing lives outside the repo. The keystore and its passwords are
stored as GitHub Actions secrets; keep a backup of the keystore, because losing
it means every user must uninstall to take the next update.

## Status

| Milestone | Scope | State |
|---|---|---|
| M1 | Single item end to end: capture, identify, confirm, price, hand-off, inventory, sign-in | scaffolded |
| M2 | Multi-item: segmentation outlines, select, add, retake, confirm all | scaffolded |
| M3 | Bundles: discount slider, bundle copy | scaffolded |
| M4 | Public: sold-price provider swap, accuracy dashboard, Play Store, sync | not started |

Two things must be verified on a real device before M1 is called done: the
`fb://` deep link that lands on the Marketplace sell screen, and ML Kit's
behaviour on small items in a crowded photo.

## iOS

A native SwiftUI port lives in [`ios/`](ios/README.md). It shares the backend and `contracts/`. See that README for building, Firebase setup and TestFlight uploads.
