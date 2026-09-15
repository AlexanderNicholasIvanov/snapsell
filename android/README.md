# SnapSell — Android

Kotlin + Jetpack Compose app. Photograph one or many items, ML Kit outlines
each one on-device, the backend identifies and prices each cutout, and the
app stages a Facebook Marketplace listing (gallery album + clipboard + deep
link). Inventory lives on the device in Room.

## Build

Requirements (what the verified build uses):

- JDK 21 at `/opt/homebrew/opt/openjdk@21` (pinned in `gradle.properties`
  via `org.gradle.java.home`; the `./gradlew` launcher itself still needs a
  `java` on `PATH` or `JAVA_HOME` to start).
- Android SDK with platform 37 and build-tools 36+. Put its path in
  `local.properties` (gitignored):

  ```properties
  sdk.dir=/opt/homebrew/share/android-commandlinetools
  ```

- Gradle 9.5 via the wrapper, AGP 9.3 (built-in Kotlin 2.3.10, so there is
  no `org.jetbrains.kotlin.android` plugin — do not add one).

```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew --no-daemon assembleDebug testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration (`android/local.properties`)

All optional; each falls back to a Gradle project property (`-P` or
`gradle.properties`) and then to the default.

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
# Backend base URL. Default is the emulator's alias for the host machine.
snapsell.backendUrl=http://10.0.2.2:8000/
# Google OAuth *web* client id for Credential Manager sign-in. Only needed
# when google-services.json is absent (the plugin generates the same value
# as the default_web_client_id string resource when it is present).
snapsell.googleWebClientId=1234567890-abc.apps.googleusercontent.com
```

`snapsell.backendUrl` is exposed as `BuildConfig.BACKEND_URL` and can be
overridden at runtime in Settings.

## Firebase / Google sign-in

1. In the Firebase console, add an Android app with package
   `com.alexivanov.snapsell`, enable the Google sign-in provider, and register
   the SHA-1 of your debug keystore (`keytool -list -v -keystore
   ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
2. Download `google-services.json` and put it at
   `android/app/google-services.json` (gitignored).

The `com.google.gms.google-services` plugin is applied **only if that file
exists**, so the project builds without it. Without it `AuthManager.isConfigured`
is false, the "Sign in with Google" button is disabled, and the sign-in
screen says so.

### Debug: continue without sign-in

Whenever Firebase is not configured, every build type shows a **"Continue
without sign-in"** button (debug builds label it "(dev)" and keep it even
when Firebase is configured). It sets a local DataStore flag; while set,
`AuthInterceptor` sends **no** `Authorization` header. Use it with a backend
started with auth disabled. "Sign out" in Settings clears the flag.

## Layout

```
app/src/main/java/com/alexivanov/snapsell/
  core/        AppResult, DispatcherProvider, Clock, Ids, Iso8601, Task.await
  domain/      Condition, ListingStatus/Kind, BundlePricing, PricePoints, Money
  data/remote/ DTOs mirroring /contracts, Retrofit SnapsellApi, AuthInterceptor
  data/local/  Room: ItemEntity, ListingEntity, ListingItemCrossRef, DAOs
  data/repository/ Identify/Pricing/Bundle (interfaces + Remote*), Inventory, Settings
  vision/      CameraScreen (CameraX), Segmenter (ML Kit), CutoutRenderer, PhotoStore
  handoff/     ResaleAlbum (MediaStore), ClipboardStager, MarketplaceLauncher
  auth/        AuthManager (Credential Manager + Firebase Auth)
  ui/          Compose screens + ViewModels, manual DI through AppContainer
app/schemas/   exported Room schema (KSP room.schemaLocation)
```

## Release builds, signing and versioning

`assembleRelease` reads the signing key from Gradle properties first, then
environment variables, named exactly:

```
SNAPSELL_KEYSTORE_PATH  SNAPSELL_KEYSTORE_PASSWORD  SNAPSELL_KEY_ALIAS  SNAPSELL_KEY_PASSWORD
```

```bash
set -a; . ~/.snapsell/signing.env; set +a
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew --no-daemon assembleRelease
/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

When `SNAPSELL_KEYSTORE_PATH` is unset or the file is missing, the release
build type falls back to the **debug** key and Gradle prints one warning line
(`SnapSell: ... DEBUG-SIGNED`). Such an APK installs, but cannot update a
properly signed install. Release stays unminified on purpose so stack traces
from sideloaded builds are readable.

`versionCode` comes from `-Psnapsell.versionCode=<n>` (CI passes the run
number; local builds are 1) and `versionName` is `0.1.<versionCode>`.

Cleartext HTTP is allowed in every build type (`usesCleartextTraffic` in the
manifest) because the app is sideloaded to people who run the backend on a
home network. Revisit before any Play Store submission.

## In-app update check

On the first Inventory display per process, `update/UpdateChecker` GETs
`https://api.github.com/repos/<snapsell.updateRepo>/releases/latest` and,
when the release is newer than the running build and has an `.apk` asset,
Inventory shows an "Update available" banner. Download opens the asset URL
in the browser; nothing is fetched or installed by the app. "Not now"
remembers that version. Settings has a manual "Check for updates".

- Version code of a release: `version_code: N` in the release body, else the
  last number of the tag (`v0.1.42` -> 42).
- `snapsell.updateToken`: optional GitHub token, sent as a Bearer header
  (only needed while the repo is private).
- `snapsell.updateManifestUrl`: replaces the GitHub URL entirely, for testing
  against a local JSON file (`python3 -m http.server` on the host, then
  `-Psnapsell.updateManifestUrl=http://10.0.2.2:8090/latest.json`).

All three are read from `local.properties`, then `-P` properties, then the
defaults, like `snapsell.backendUrl`. The backend URL can also be changed at
runtime in Settings; it is persisted and applied to every request by a URL
rewriting interceptor.

## Design system

The UI follows `docs/design-handoff.md` (Modernist skin on M3 structure).
Tokens live in `ui/theme/`: `SnapColors` (light/dark, exposed through
`LocalSnapColors`), `SnapType` (Archivo 400/600/800 roles) and
`ModernistShapes` (every M3 shape is square; only the capture shutter is a
circle). Shared pieces are in `ui/common/Common.kt`: `SnapTopBar`,
`SnapBottomBar`, `PrimaryButton` / `SecondaryButton` / `GhostButton`
(flush-left labels), `StatusChip`, `ConditionChipRow`, `CutoutTile`,
`SnapTextField`, `SquareCheckbox`, `SnapSlider`, `Skeleton`, `Spinner`.
Lucide icons are `res/drawable/ic_lucide_*.xml`, tinted at the use site.

Changing the local-sale factor in Settings recomputes every stored
suggested price on the device (`SuggestedPrice.recompute`, the same rule the
backend uses) and never touches a final price the user typed.

## Tests

`./gradlew testDebugUnitTest` runs:

- `domain/BundlePricingTest`, `domain/PricePointsTest`, `domain/SuggestedPriceTest`
  — pricing rules that must match the backend.
- `contracts/ContractsRoundTripTest` — decodes every file in
  `../../contracts/examples`, re-encodes, and checks the round trip and key
  sets. If the contracts change, this is the test that goes red.
- `handoff/ClipboardStagerTest` — exact clipboard block format.
- `update/UpdateCheckerTest`, `data/remote/BackendUrlInterceptorTest` — release
  parsing and URL rewriting, against an in-memory OkHttp interceptor.

Room DAO tests are intentionally not included (they need Robolectric or a
device); the repository layer is thin enough to verify on a device.

## Must be verified on a real device

1. **`fb://marketplace` deep link.** Facebook does not document its URL
   scheme and changes it without notice. `MarketplaceLauncher` tries the deep
   link first and falls back to a Custom Tab on
   `https://www.facebook.com/marketplace/create/item`. Check on a phone with
   the current Facebook build that the deep link actually lands in
   Marketplace; if it opens the Facebook home feed, switch the order or drop
   the deep link.
2. **ML Kit subject segmentation on small items.** The model (downloaded by
   Play services; see the `com.google.mlkit.vision.DEPENDENCIES` meta-data)
   is tuned for people and large objects. Test with small items (jewellery,
   cables, phone cases) and cluttered backgrounds; when it finds nothing the
   Review screen falls back to "Add item" manual rectangles, which must feel
   acceptable. ML Kit's GPU pipeline needs OpenGL ES 3.1; on a GLES 3.0
   device (the emulator) `Segmenter` refuses to run rather than crash
   natively, so the emulator always shows the "outlining unavailable"
   notice and only a real device exercises the model.

Also worth checking on a device: the `Pictures/Resale` album shows up in the
Facebook photo picker (API 29+ uses `RELATIVE_PATH`; API 24–28 requests
`WRITE_EXTERNAL_STORAGE` at hand-off time).
