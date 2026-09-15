#!/usr/bin/env bash
# Sanity-check App Store Connect access: lists apps visible to the API key.
#   ASC_KEY_ID=48M5JQ79S5 ASC_ISSUER_ID=<uuid> ios/scripts/asc-check.sh
set -euo pipefail
: "${ASC_KEY_ID:?}" ; : "${ASC_ISSUER_ID:?}"
KEY_PATH="${ASC_KEY_PATH:-$HOME/.private_keys/AuthKey_${ASC_KEY_ID}.p8}"
python3 - "$KEY_PATH" "$ASC_KEY_ID" "$ASC_ISSUER_ID" <<'PY'
import sys, json, time, base64, subprocess, urllib.request
key_path, kid, iss = sys.argv[1:4]
try:
    import jwt  # PyJWT
except ImportError:
    sys.exit("pip3 install pyjwt cryptography")
now = int(time.time())
token = jwt.encode({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}, open(key_path).read(), algorithm="ES256", headers={"kid": kid})
req = urllib.request.Request("https://api.appstoreconnect.apple.com/v1/apps?fields[apps]=name,bundleId", headers={"Authorization": f"Bearer {token}"})
data = json.load(urllib.request.urlopen(req))
for app in data.get("data", []):
    print(app["attributes"]["bundleId"], "-", app["attributes"]["name"])
if not any(a["attributes"]["bundleId"] == "com.alexivanov.snapsell" for a in data.get("data", [])):
    print("\nNo app record for com.alexivanov.snapsell yet: create it in App Store Connect -> Apps -> + before the first upload.")
PY
