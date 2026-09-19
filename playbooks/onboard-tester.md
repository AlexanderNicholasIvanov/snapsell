# Onboard a tester

## Facts

| Key | Value |
|---|---|
| Auth | Firebase Auth ID tokens verified by the backend; email allowlist `SNAPSELL_ALLOWED_EMAILS` in `/opt/snapsell/deploy/.env` on the VPS (comma-separated; empty = any signed-in user) |
| Providers | iOS: Google + Sign in with Apple. Android: Google. |
| Apple relay | Apple users who hide their email sign in as `<random>@privaterelay.appleid.com`; that address must be allowlisted, not their real one |
| Firebase status | **not yet wired**: no `GoogleService-Info.plist`, no `google-services.json`, empty service-account file on the VPS. Until then only the dev bypass works and the backend rejects it. |
| iOS delivery | `release-ios` playbook (direct-to-device now, TestFlight later) |
| Android delivery | GitHub Releases page (`release-android` playbook) |

## Goal

The tester signs in on their own phone, identifies one item, and the backend log shows a request under their email.

## Preconditions

- Firebase is wired on both clients and the VPS (see the one-time block below). Check on the VPS: `test -s /opt/snapsell/deploy/firebase-service-account.json && echo ok`.
- The tester has told you which email they will sign in with (Google account, or Apple with "share my email"; otherwise ask them for the relay address after their first attempt, which will 403).

## Steps

### One-time: wire Firebase (owner)

1. `[HUMAN]` Firebase console -> project -> Authentication -> Sign-in method: enable Google and Apple.
2. `[HUMAN]` Project settings -> Add app -> iOS, bundle id `com.victorivanov.snapsell`; download `GoogleService-Info.plist` to `ios/SnapSell/Resources/`. Add app -> Android, package `com.alexivanov.snapsell`, with the release key SHA-1; download `google-services.json` to `android/app/`.
3. `[HUMAN]` Project settings -> Service accounts -> Generate new private key; copy the JSON to the VPS at `/opt/snapsell/deploy/firebase-service-account.json` (paste via the web console: `cat > firebase-service-account.json` then the content, then Ctrl-D).
4. Agent: put `REVERSED_CLIENT_ID` from the plist into `ios/Config/Local.xcconfig` as `GOOGLE_REVERSED_CLIENT_ID`, add `com.apple.developer.applesignin` to `ios/SnapSell/SnapSell.entitlements`, set repo secret `GOOGLE_SERVICE_INFO_PLIST_B64` and variable `GOOGLE_REVERSED_CLIENT_ID` when TestFlight is configured.
5. Run `deploy-backend` so the container picks up the service account.

### Per tester

6. `[HUMAN]` on the VPS, append the email to `SNAPSELL_ALLOWED_EMAILS` in `/opt/snapsell/deploy/.env`.
7. Restart the backend so it re-reads `.env`: `cd /opt/snapsell/deploy && docker compose up -d` (env change alone does not need `--build`).
8. Deliver a build: iOS via `release-ios` (TestFlight invite once configured, otherwise a direct install while the phone is with you); Android via the Releases link.
9. Tell the tester: sign in with the agreed account, then in Settings confirm the backend URL is `https://srv1343782.hstgr.cloud/`.

## Verification

```bash
# on the VPS
grep SNAPSELL_ALLOWED_EMAILS /opt/snapsell/deploy/.env
docker compose -f /opt/snapsell/deploy/docker-compose.yml logs --since 10m backend | grep -E "identify|403|401"
```
- The tester's first `/identify` appears with a 200, not a 403 (not allowlisted) or 401 (no token: they used the bypass or Firebase is not configured in their build).

## Rollback

Remove the email from `SNAPSELL_ALLOWED_EMAILS` and `docker compose up -d`; their next request gets 403. Firebase console -> Authentication -> Users -> disable the account if the token itself should stop working.
