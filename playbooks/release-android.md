# Release Android

## Facts

| Key | Value |
|---|---|
| Application id | `com.alexivanov.snapsell` (Alex's namespace; unchanged) |
| Release channel | GitHub Releases, tag `v0.1.<run_number>`, one `.apk` asset; the app's update checker polls it |
| Workflow | `.github/workflows/release.yml`: push to `main` touching `android/**` or `contracts/**`, or `workflow_dispatch` |
| Signing | repo secrets `SNAPSELL_KEYSTORE_B64`, `SNAPSELL_KEYSTORE_PASSWORD`, `SNAPSELL_KEY_ALIAS`, `SNAPSELL_KEY_PASSWORD` (set) |
| Backend URL | repo variable `SNAPSELL_BACKEND_URL` (set to the VPS URL) |
| versionCode | the workflow run number (strictly increasing) |
| Google sign-in | needs `android/app/google-services.json` (git-ignored, not yet provided) and the SHA-1 of the release key registered in Firebase |

## Goal

A signed APK for the merged `main` is published as the latest GitHub Release and installs over the previous one on a phone.

## Preconditions

- `android` CI green on the merge commit: `gh run list --workflow android.yml --branch main --limit 1`.
- The four keystore secrets exist: `gh secret list | grep SNAPSELL_KEY`.

## Steps

1. Normal case: merging to `main` already triggered `release.yml`. Find the run: `gh run list --workflow release.yml --limit 1`.
2. Manual case (re-publish without a code change): `gh workflow run release.yml` then `gh run watch`.
3. Install on a phone: `[HUMAN]` open `https://github.com/AlexanderNicholasIvanov/snapsell/releases/latest` on the phone, download the `.apk`, open it. Existing installs can instead accept the in-app update banner.

## Verification

```bash
gh release view --json tagName,assets -q '.tagName, (.assets[].name)'   # newest tag, one .apk
```
- On the phone: Settings shows the version and `Check for updates` reports "up to date".
- `apksigner verify --print-certs <apk>` (from the Android build-tools) shows the release certificate, not the debug one (a debug-signed APK is labelled `DEBUG-SIGNED` in the app name and cannot update a release install).

## Rollback

Previous releases stay downloadable: install the prior tag's `.apk` from the Releases page. Android refuses a lower `versionCode` over a higher one, so a true downgrade needs uninstall + reinstall (inventory is lost). Prefer fixing forward: merge the fix, the workflow publishes a higher versionCode.
