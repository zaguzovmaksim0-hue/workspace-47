#!/usr/bin/env bash
# Re-sign the tested optimized payload; never distribute the runner debug identity.
set -euo pipefail
: "${JFM_DAILY_STORE_FILE:?Missing daily keystore}"
: "${JFM_DAILY_STORE_PASSWORD:?Missing daily store password}"
: "${JFM_DAILY_KEY_ALIAS:?Missing daily alias}"
: "${JFM_DAILY_KEY_PASSWORD:?Missing daily key password}"
[[ $# -eq 2 ]] || { echo 'Usage: sign-daily-apk.sh INPUT.apk OUTPUT.apk' >&2; exit 64; }
input=$1
output=$2
[[ -s "$input" && -f "$JFM_DAILY_STORE_FILE" && "$input" != "$output" ]] || exit 1
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
APKSIGNER=${APKSIGNER:-$sdk/build-tools/36.0.0/apksigner}
ZIPALIGN=${ZIPALIGN:-$sdk/build-tools/36.0.0/zipalign}
AAPT2=${AAPT2:-$sdk/build-tools/36.0.0/aapt2}
mkdir -p "$(dirname "$output")"
tmp=$(mktemp -d "$(dirname "$output")/.daily-sign.XXXXXX")
trap 'rm -rf "$tmp"' EXIT
"$AAPT2" dump badging "$input" > "$tmp/badging.txt"
grep -Eq "^package: name='dev.junta.firmamobile' " "$tmp/badging.txt"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$input" > "$tmp/manifest.txt"
if grep -Eq 'android:debuggable.*=true|android:testOnly.*=true|ProtocolProbeActivity|E2eControl' "$tmp/manifest.txt"; then
  echo 'Daily APK must not contain debug/test flags or control components' >&2; exit 1
fi
grep -Eq 'android:allowBackup.*=false' "$tmp/manifest.txt"
grep -Eq 'android:usesCleartextTraffic.*=false' "$tmp/manifest.txt"
"$ZIPALIGN" -c -p 4 "$input"
"$APKSIGNER" sign --ks "$JFM_DAILY_STORE_FILE" \
  --ks-pass env:JFM_DAILY_STORE_PASSWORD --key-pass env:JFM_DAILY_KEY_PASSWORD \
  --ks-key-alias "$JFM_DAILY_KEY_ALIAS" --v1-signing-enabled true \
  --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$tmp/daily.apk" "$input"
"$APKSIGNER" verify --verbose --print-certs "$tmp/daily.apk" > "$tmp/certificate.txt"
python3 tools/verify_daily_apk_signature.py --report "$tmp/certificate.txt" \
  --expected config/signing/daily-cert-sha256.txt
"$ZIPALIGN" -c -p 4 "$tmp/daily.apk"
python3 tools/verify_legal_bundle.py --apk "$tmp/daily.apk"
mv "$tmp/daily.apk" "$output"
cp "$tmp/certificate.txt" "$(dirname "$output")/daily-certificate.txt"
(cd "$(dirname "$output")" && sha256sum "$(basename "$output")" > SHA256SUMS)
