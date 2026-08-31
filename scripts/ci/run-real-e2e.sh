#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly PACKAGE_NAME="dev.junta.firmamobile"
readonly TEST_PACKAGE_NAME="dev.junta.firmamobile.test"
readonly RUNNER="androidx.test.runner.AndroidJUnitRunner"
readonly TEST_CLASS="dev.junta.firmamobile.RealE2eInstrumentedTest"
readonly FIXTURE_DIR="files/real-e2e"
readonly CERTIFICATE_PATH="$FIXTURE_DIR/identity.p12"
readonly PASSWORD_PATH="$FIXTURE_DIR/password"
readonly REPORT_DIR="$ROOT_DIR/build/reports/real-e2e"
readonly RESULTS_DIR="$REPORT_DIR/results"
readonly LOGS_DIR="$REPORT_DIR/navigation"
readonly RESULT_PATH="$FIXTURE_DIR/result.json"
readonly CATALOG_FILE="$ROOT_DIR/app/src/main/res/raw/public_portal_catalog_v1.json"
readonly PORTAL_TIMEOUT_SECONDS=165
readonly REPORT_HELPER="$ROOT_DIR/scripts/ci/real_e2e_report.py"
readonly QA_APK="$ROOT_DIR/app/build/outputs/apk/qa/app-qa.apk"
readonly TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk"

cleanup() {
  unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD || true
  adb shell run-as "$PACKAGE_NAME" rm -rf "$FIXTURE_DIR" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

require_secret() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    printf 'Required REAL_E2E secret is unavailable: %s\n' "$name" >&2
    exit 2
  fi
}

stage_fixture() {
  adb shell run-as "$PACKAGE_NAME" mkdir -p "$FIXTURE_DIR"
  printf '%s' "$REAL_E2E_CERT_P12_B64" \
    | base64 --decode \
    | adb exec-out run-as "$PACKAGE_NAME" tee "$CERTIFICATE_PATH" >/dev/null
  printf '%s' "$REAL_E2E_CERT_PASSWORD" \
    | adb exec-out run-as "$PACKAGE_NAME" tee "$PASSWORD_PATH" >/dev/null
  adb shell run-as "$PACKAGE_NAME" chmod 600 "$CERTIFICATE_PATH" "$PASSWORD_PATH"

  local cert_size password_size
  cert_size="$(adb shell run-as "$PACKAGE_NAME" stat -c %s "$CERTIFICATE_PATH" | tr -d '\r')"
  password_size="$(adb shell run-as "$PACKAGE_NAME" stat -c %s "$PASSWORD_PATH" | tr -d '\r')"
  [[ "$cert_size" =~ ^[0-9]+$ && "$cert_size" -ge 1 && "$cert_size" -le 1048576 ]]
  [[ "$password_size" =~ ^[0-9]+$ && "$password_size" -ge 1 && "$password_size" -le 8192 ]]

  # Keep the credential exposure window as short as possible.
  unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD
}

main() {
  [[ -f "$QA_APK" && -f "$TEST_APK" && -f "$CATALOG_FILE" && -x "$REPORT_HELPER" ]]
  command -v adb >/dev/null
  command -v timeout >/dev/null

  local shard_index="${REAL_E2E_SHARD_INDEX:-}"
  local shard_total="${REAL_E2E_SHARD_TOTAL:-}"
  [[ "$shard_index" =~ ^[0-9]+$ && "$shard_total" =~ ^[1-9][0-9]*$ ]] || {
    echo 'REAL_E2E shard coordinates are required' >&2
    exit 64
  }

  rm -rf "$REPORT_DIR"
  mkdir -p "$RESULTS_DIR" "$LOGS_DIR"
  mapfile -t portal_ids < <(
    "$REPORT_HELPER" select \
      --catalog "$CATALOG_FILE" \
      --portal "${PORTAL_ID_FILTER:-}" \
      --shard-index "$shard_index" \
      --shard-total "$shard_total"
  )
  printf 'REAL_E2E shard %s/%s selected %d catalog portal(s).\n' \
    "$((shard_index + 1))" "$shard_total" "${#portal_ids[@]}"

  if ((${#portal_ids[@]} == 0)); then
    "$REPORT_HELPER" summary \
      --catalog "$CATALOG_FILE" \
      --results "$RESULTS_DIR" \
      --portal "${PORTAL_ID_FILTER:-}" \
      --shard-index "$shard_index" \
      --shard-total "$shard_total" \
      --json-output "$REPORT_DIR/summary.json" \
      --markdown-output "$REPORT_DIR/summary.md"
    return 0
  fi

  require_secret REAL_E2E_CERT_P12_B64
  require_secret REAL_E2E_CERT_PASSWORD
  adb install -r "$QA_APK" >/dev/null
  adb install -r "$TEST_APK" >/dev/null
  stage_fixture

  local deep_arg="${REAL_E2E_DEEP_AUTH_SIGNING:-true}"
  [[ "$deep_arg" == true || "$deep_arg" == false ]] || {
    echo 'REAL_E2E_DEEP_AUTH_SIGNING must be true or false' >&2
    exit 64
  }

  local index=0 portal_id status result_file navigation_file reason
  for portal_id in "${portal_ids[@]}"; do
    index=$((index + 1))
    printf '[%d/%d] %s\n' "$index" "${#portal_ids[@]}" "$portal_id"
    adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
    adb shell run-as "$PACKAGE_NAME" rm -f "$RESULT_PATH" files/qa-navigation.log >/dev/null 2>&1 || true

    set +e
    timeout --signal=TERM --kill-after=10s "${PORTAL_TIMEOUT_SECONDS}s" \
      adb shell am instrument -w -r \
        -e realE2e true \
        -e realE2eDeep "$deep_arg" \
        -e portalId "$portal_id" \
        -e class "$TEST_CLASS" \
        "$TEST_PACKAGE_NAME/$RUNNER" \
        >/dev/null 2>&1
    status=$?
    set -e

    result_file="$RESULTS_DIR/$portal_id.json"
    if ! adb exec-out run-as "$PACKAGE_NAME" cat "$RESULT_PATH" >"$result_file" 2>/dev/null || \
       ! "$REPORT_HELPER" validate-result --result "$result_file" --portal "$portal_id"; then
      if [[ $status -eq 124 ]]; then reason=TIMEOUT; else reason=RESULT_MISSING; fi
      "$REPORT_HELPER" synthetic \
        --catalog "$CATALOG_FILE" \
        --portal "$portal_id" \
        --output "$result_file" \
        --reason "$reason"
      "$REPORT_HELPER" validate-result --result "$result_file" --portal "$portal_id"
    fi

    navigation_file="$LOGS_DIR/$portal_id.log"
    if adb exec-out run-as "$PACKAGE_NAME" cat files/qa-navigation.log >"$navigation_file" 2>/dev/null; then
      "$REPORT_HELPER" validate-log --log "$navigation_file"
    else
      : >"$navigation_file"
    fi
  done

  "$REPORT_HELPER" summary \
    --catalog "$CATALOG_FILE" \
    --results "$RESULTS_DIR" \
    --portal "${PORTAL_ID_FILTER:-}" \
    --shard-index "$shard_index" \
    --shard-total "$shard_total" \
    --json-output "$REPORT_DIR/summary.json" \
    --markdown-output "$REPORT_DIR/summary.md"
}

main "$@"
