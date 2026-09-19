# SnapSell for iOS

Native SwiftUI port of the Android app. Same backend, same `contracts/`, same
Modernist skin (`docs/design-handoff.md`). iOS 17+, iPhone only.

## Layout

```
ios/
  project.yml            XcodeGen spec -> SnapSell.xcodeproj (generated, gitignored)
  Config/*.xcconfig      Build settings; Local.xcconfig (gitignored) overrides per machine
  SnapSell/
    App/                 Entry point, dependency container, navigation
    Core/  Domain/       Errors, ids, ISO-8601, JSON, pricing rules, enums
    Data/Remote/         DTOs mirroring contracts/, URLSession API client
    Data/Local/          SwiftData models (items, listings)
    Data/Repository/     Inventory, settings, identify/price/bundle repositories
    Vision/              Camera, photo store, Vision segmentation, cutout rendering
    Handoff/             Clipboard, "Resale" album, Marketplace launcher
    Auth/                Firebase Auth (Google + Sign in with Apple), dev bypass
    UI/                  Theme, shared components, the nine screens
  SnapSellTests/         XCTest: pricing, clipboard format, backend URL, contract round-trips
  scripts/               TestFlight upload + App Store Connect check
```

## Build locally

```bash
brew install xcodegen
cd ios
cp Config/Local.xcconfig.example Config/Local.xcconfig   # optional overrides
xcodegen generate
open SnapSell.xcodeproj
```

Or from the command line:

```bash
cd ios && xcodegen generate && xcodebuild test -project SnapSell.xcodeproj -scheme SnapSell -destination 'platform=iOS Simulator,name=iPhone 16'
```

The simulator has no camera: the Capture screen shows a photo picker instead.
With no `GoogleService-Info.plist` bundled, the sign-in screen offers
"Continue without sign-in", which sends no `Authorization` header; run the
backend with `SNAPSELL_AUTH_DISABLED=1` for that.

## Backend URL

`SNAPSELL_BACKEND_URL` in `Config/Base.xcconfig` (default `http://localhost:8000/`)
is baked into the build; the user can override it in Settings. Note the
`http:/$()/` spelling: `//` starts a comment in xcconfig. On a real device use
the Mac's LAN address or a deployed backend.

## Firebase sign-in (owner action)

1. Firebase console -> project settings -> Add app -> iOS, bundle id `com.victorivanov.snapsell`.
2. Download `GoogleService-Info.plist` into `ios/SnapSell/Resources/` (gitignored).
3. Copy its `REVERSED_CLIENT_ID` into `Config/Local.xcconfig` as `GOOGLE_REVERSED_CLIENT_ID = com.googleusercontent.apps....` (this becomes the URL scheme Google Sign-In returns on).
4. Enable Google and Apple providers in Firebase Auth. Sign in with Apple is
   required by App Store guideline 4.8 when Google sign-in is offered. Add
   `com.apple.developer.applesignin` to `SnapSell.entitlements` (see
   `playbooks/onboard-tester.md`); Xcode registers it on the App ID with
   automatic signing.
5. Apple users may hide their email (`...@privaterelay.appleid.com`). Add those
   relay addresses to `SNAPSELL_ALLOWED_EMAILS` on the backend or they will get 403.

## TestFlight

Signing is automatic with team `D9QD5DUKNC`. Before the first upload, the
owner must create the app record once: App Store Connect -> Apps -> + ->
name "SnapSell", bundle id `com.victorivanov.snapsell`, SKU anything.

An App Store Connect API key is needed for non-interactive uploads
(Users and Access -> Integrations -> App Store Connect API -> Generate,
role App Manager). Note the Key ID and Issuer ID and download the `.p8`.

### From this Mac

```bash
ASC_KEY_ID=48M5JQ79S5 ASC_ISSUER_ID=<issuer-uuid> ios/scripts/testflight.sh
```

The key is expected at `~/.private_keys/AuthKey_<KEY_ID>.p8`. Check access first with
`ios/scripts/asc-check.sh` (needs `pip3 install pyjwt cryptography`).

### From GitHub Actions

`.github/workflows/testflight.yml` runs on tags `ios-v*` or manually. Set:

| Secret | Value |
|---|---|
| `ASC_KEY_ID` | API key ID |
| `ASC_ISSUER_ID` | Issuer ID |
| `ASC_KEY_P8` | `base64 < AuthKey_XXXX.p8` |
| `GOOGLE_SERVICE_INFO_PLIST_B64` | `base64 < GoogleService-Info.plist` (optional) |

| Variable | Value |
|---|---|
| `SNAPSELL_BACKEND_URL` | deployed backend, ending in `/` |
| `GOOGLE_REVERSED_CLIENT_ID` | from the plist (optional) |

The build number is the workflow run number; the marketing version comes from
`Base.xcconfig` or the manual-run input. Processing takes 10-30 minutes, then
add testers under TestFlight -> Internal Testing.

`.github/workflows/ios.yml` builds and runs the unit tests on every PR touching `ios/` or `contracts/`.

## Not ported

- The GitHub-Releases update checker: TestFlight delivers updates on iOS.
- Lucide icons: SF Symbols with the same meaning are used instead.
