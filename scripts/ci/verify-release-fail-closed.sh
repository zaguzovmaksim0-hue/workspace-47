#!/usr/bin/env bash
set -euo pipefail

unset JFM_RELEASE_STORE_FILE JFM_RELEASE_STORE_PASSWORD JFM_RELEASE_KEY_ALIAS JFM_RELEASE_KEY_PASSWORD
rm -f app/build/outputs/apk/release/app-release.apk
log_file=$(mktemp)
set +e
./gradlew assembleRelease --no-daemon >"$log_file" 2>&1
status=$?
set -e
if [[ $status -eq 0 ]]; then
  echo "assembleRelease unexpectedly succeeded without private signing" >&2
  cat "$log_file" >&2
  exit 1
fi
grep -Fq 'Private release signing is required' "$log_file"
if [[ -e app/build/outputs/apk/release/app-release.apk ]]; then
  echo "app-release.apk exists after fail-closed release gate" >&2
  exit 1
fi
rm -f "$log_file"
echo "Release signing fail-closed verification passed"
