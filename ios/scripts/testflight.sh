#!/usr/bin/env bash
# Archive the iOS app and upload it to TestFlight from this Mac.
#
# Needs an App Store Connect API key (App Store Connect -> Users and Access ->
# Integrations -> App Store Connect API), saved as ~/.private_keys/AuthKey_<ID>.p8.
#
#   ASC_KEY_ID=48M5JQ79S5 ASC_ISSUER_ID=<uuid> ios/scripts/testflight.sh [build-number]
#
# Optional: SNAPSELL_BACKEND_URL, GOOGLE_REVERSED_CLIENT_ID, MARKETING_VERSION are
# read from ios/Config/Local.xcconfig if present (see Local.xcconfig.example).
set -euo pipefail

cd "$(dirname "$0")/.."

: "${ASC_KEY_ID:?set ASC_KEY_ID to the App Store Connect API key ID}"
: "${ASC_ISSUER_ID:?set ASC_ISSUER_ID to the App Store Connect issuer ID}"
KEY_PATH="${ASC_KEY_PATH:-$HOME/.private_keys/AuthKey_${ASC_KEY_ID}.p8}"
[ -f "$KEY_PATH" ] || { echo "API key not found at $KEY_PATH" >&2; exit 1; }

BUILD_NUMBER="${1:-$(date -u +%Y%m%d%H%M)}"

command -v xcodegen >/dev/null || { echo "xcodegen missing: brew install xcodegen" >&2; exit 1; }
xcodegen generate

rm -rf build
xcodebuild archive \
  -project SnapSell.xcodeproj \
  -scheme SnapSell \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/SnapSell.xcarchive \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER"

xcodebuild -exportArchive \
  -archivePath build/SnapSell.xcarchive \
  -exportOptionsPlist scripts/ExportOptions.plist \
  -exportPath build/export \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID"

echo "Uploaded build $BUILD_NUMBER. It appears in App Store Connect -> TestFlight after processing (10-30 min)."
