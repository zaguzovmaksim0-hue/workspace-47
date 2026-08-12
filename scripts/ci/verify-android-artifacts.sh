#!/usr/bin/env bash
set -euo pipefail

find_build_tool() {
  local name=$1
  if command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
    return
  fi
  local sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  [[ -n "$sdk_root" ]] || { echo "Missing Android SDK and $name" >&2; exit 1; }
  local candidate
  candidate=$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$name" -perm -u+x | sort -V | tail -1)
  [[ -n "$candidate" ]] || { echo "Missing Android build tool: $name" >&2; exit 1; }
  printf '%s\n' "$candidate"
}

APKSIGNER=$(find_build_tool apksigner)
ZIPALIGN=$(find_build_tool zipalign)
AAPT2=$(find_build_tool aapt2)

DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
QA_APK=app/build/outputs/apk/qa/app-qa.apk
TEST_APK=app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
for apk in "$DEBUG_APK" "$QA_APK" "$TEST_APK"; do
  [[ -s "$apk" ]] || { echo "Missing APK: $apk" >&2; exit 1; }
  "$ZIPALIGN" -c -p -v 4 "$apk" >/dev/null
  signature_report=$(mktemp)
  "$APKSIGNER" verify --verbose --print-certs "$apk" >"$signature_report"
  grep -Eq '^Verified using v2 scheme .*: true$' "$signature_report"
  grep -Fxq 'Number of signers: 1' "$signature_report"
  rm -f "$signature_report"
done

manifest_report=$(mktemp)
"$AAPT2" dump xmltree --file AndroidManifest.xml "$QA_APK" >"$manifest_report"
grep -Eq 'android:allowBackup.*=false' "$manifest_report"
grep -Eq 'android:usesCleartextTraffic.*=false' "$manifest_report"
if grep -Eq 'android:testOnly.*=true' "$manifest_report"; then
  echo "QA APK must not be testOnly" >&2
  exit 1
fi
rm -f "$manifest_report"

forbidden_canaries=(
  'secret-canary'
  'certificate-signature-secret-canary'
  'qa-secret-token-canary'
  'PASSWORD_CANARY'
  'PKCS12_BYTES_CANARY'
  'raw-document-must-never-be-recorded'
  'ws024-double-tls-canary'
  'cookie-value-must-not-escape'
)
for apk in "$DEBUG_APK" "$QA_APK"; do
  strings_file=$(mktemp)
  unzip -p "$apk" | strings >"$strings_file"
  for canary in "${forbidden_canaries[@]}"; do
    if grep -Fq "$canary" "$strings_file"; then
      echo "forbidden canary found in $apk" >&2
      exit 1
    fi
  done
  rm -f "$strings_file"
done

echo "Android artifact verification passed"
