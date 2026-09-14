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

`snapsell.backendUrl` is exposed as `BuildConfig.BACKEND_URL`. Debug builds
allow cleartext HTTP (`src/debug/AndroidManifest.xml`) so a local backend
works; release builds do not.

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

Debug builds show a **"Continue without sign-in (dev)"** button. It sets a
local DataStore flag; while set, `AuthInterceptor` sends **no**
`Authorization` header. Use it with a backend started with auth disabled.
"Sign out" in Settings clears the flag.

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

## Tests

`./gradlew testDebugUnitTest` runs:

- `domain/BundlePricingTest`, `domain/PricePointsTest` — pricing rules that
  must match the backend.
- `contracts/ContractsRoundTripTest` — decodes every file in
  `../../contracts/examples`, re-encodes, and checks the round trip and key
  sets. If the contracts change, this is the test that goes red.
- `handoff/ClipboardStagerTest` — exact clipboard block format.

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
   acceptable. The emulator has no Play services model download, so this
   only works on a device.

Also worth checking on a device: the `Pictures/Resale` album shows up in the
Facebook photo picker (API 29+ uses `RELATIVE_PATH`; API 24–28 requests
`WRITE_EXTERNAL_STORAGE` at hand-off time).
