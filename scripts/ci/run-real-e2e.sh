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
readonly PROGRESS_PATH="$REPORT_DIR/progress.tsv"
readonly CATALOG_FILE="$ROOT_DIR/app/src/main/res/raw/public_portal_catalog_v1.json"
# The probe and the reviewed portal recipes match the Spanish UI and Spanish portal pages.
readonly APP_LOCALE="es-ES"
# 75s portal observation + 90s signing + 30s post-sign observation + 15s margin.
readonly PORTAL_TIMEOUT_SECONDS=210
readonly ADB_TIMEOUT_SECONDS=30
readonly ADB_INSTALL_TIMEOUT_SECONDS=120
# android-emulator-runner starts AVD "test" on console port 5554; a lost emulator is restarted the
# same way with the reviewed REAL_E2E_EMULATOR_OPTIONS so one crash does not void the whole shard.
readonly EMULATOR_AVD_NAME="test"
readonly EMULATOR_PORT=5554
readonly EMULATOR_BOOT_TIMEOUT_SECONDS=420
readonly EMULATOR_RESTART_LIMIT=4
readonly REPORT_HELPER="$ROOT_DIR/scripts/ci/real_e2e_report.py"
readonly QA_APK="$ROOT_DIR/app/build/outputs/apk/qa/app-qa.apk"
readonly TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk"

adb_bounded() {
  timeout --signal=TERM --kill-after=5s "${ADB_TIMEOUT_SECONDS}s" adb "$@"
}

adb_install_bounded() {
  timeout --signal=TERM --kill-after=5s "${ADB_INSTALL_TIMEOUT_SECONDS}s" adb install --no-streaming -r "$1"
}

progress() {
  local stage="$1"
  local portal_id="${2:--}"
  local index="${3:-0}"
  local total="${4:-0}"
  local elapsed="${SECONDS:-0}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    "${REAL_E2E_SHARD_INDEX:-?}" "$index/$total" "$portal_id" "$stage" "$elapsed" \
    | tee -a "$PROGRESS_PATH"
  printf '::notice title=REAL_E2E progress::shard=%s index=%s portal=%s stage=%s elapsed=%ss\n' \
    "${REAL_E2E_SHARD_INDEX:-?}" "$index/$total" "$portal_id" "$stage" "$elapsed"
}

cleanup() {
  unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD || true
  adb_bounded shell run-as "$PACKAGE_NAME" rm -rf "$FIXTURE_DIR" >/dev/null 2>&1 || true
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
  local total="$1"
  progress STAGE_MKDIR_START - 0 "$total"
  adb_bounded shell run-as "$PACKAGE_NAME" mkdir -p "$FIXTURE_DIR"
  progress STAGE_MKDIR_DONE - 0 "$total"

  progress STAGE_CERT_WRITE_START - 0 "$total"
  printf '%s' "$REAL_E2E_CERT_P12_B64" \
    | base64 --decode \
    | adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$CERTIFICATE_PATH" bs=4096 2>/dev/null
  progress STAGE_CERT_WRITE_DONE - 0 "$total"

  progress STAGE_PASSWORD_WRITE_START - 0 "$total"
  printf '%s' "$REAL_E2E_CERT_PASSWORD" \
    | adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$PASSWORD_PATH" bs=4096 2>/dev/null
  progress STAGE_PASSWORD_WRITE_DONE - 0 "$total"

  progress STAGE_CHMOD_START - 0 "$total"
  adb_bounded shell run-as "$PACKAGE_NAME" chmod 600 "$CERTIFICATE_PATH" "$PASSWORD_PATH"
  progress STAGE_CHMOD_DONE - 0 "$total"

  progress STAGE_STAT_START - 0 "$total"
  local cert_size password_size
  cert_size="$(adb_bounded shell run-as "$PACKAGE_NAME" stat -c %s "$CERTIFICATE_PATH" | tr -d '\r')"
  password_size="$(adb_bounded shell run-as "$PACKAGE_NAME" stat -c %s "$PASSWORD_PATH" | tr -d '\r')"
  [[ "$cert_size" =~ ^[0-9]+$ && "$cert_size" -ge 1 && "$cert_size" -le 1048576 ]]
  [[ "$password_size" =~ ^[0-9]+$ && "$password_size" -ge 1 && "$password_size" -le 8192 ]]
  progress STAGE_STAT_DONE - 0 "$total"

  # Keep the credential exposure window as short as possible.
  unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD
}

prepare_device() {
  local total="$1"
  progress DEVICE_PREPARE_START - 0 "$total"
  # Long catalog shards must not lose the foreground activity to the screen timeout or keyguard.
  adb_bounded shell svc power stayon true >/dev/null
  adb_bounded shell settings put system screen_off_timeout 2147483647 >/dev/null
  adb_bounded shell input keyevent KEYCODE_WAKEUP >/dev/null
  adb_bounded shell wm dismiss-keyguard >/dev/null 2>&1 || true
  # The emulator image defaults to en-US; pin the per-app language before the first launch.
  adb_bounded shell cmd locale set-app-locales "$PACKAGE_NAME" --user 0 --locales "$APP_LOCALE" >/dev/null
  local locales
  locales="$(adb_bounded shell cmd locale get-app-locales "$PACKAGE_NAME" --user 0 | tr -d '\r')"
  [[ "$locales" == *"$APP_LOCALE"* ]] || {
    printf 'REAL_E2E could not pin the Spanish app locale: %s\n' "$locales" >&2
    return 65
  }
  progress DEVICE_PREPARE_DONE - 0 "$total"
}

device_online() {
  [[ "$(adb_bounded get-state 2>/dev/null | tr -d '\r')" == device ]]
}

# Host-side evidence only (emulator process count, host memory, kernel crash/OOM lines); no guest,
# portal or credential data is printed.
report_device_loss() {
  local portal_id="$1" emulator_processes
  emulator_processes="$(pgrep -c -f qemu-system 2>/dev/null || true)"
  printf 'REAL_E2E_DEVICE_LOST portal=%s emulator_processes=%s\n' "$portal_id" "${emulator_processes:-0}"
  free -m 2>/dev/null \
    | awk 'NR == 2 { printf "REAL_E2E_HOST_MEMORY used_mb=%s available_mb=%s\n", $3, $7 }' || true
  sudo -n dmesg 2>/dev/null \
    | grep -E -i 'segfault|general protection|traps:|out of memory|oom-kill|killed process' \
    | tail -n 6 | cut -c1-220 | sed 's/^/REAL_E2E_HOST_KERNEL /' || true
}

fixture_present() {
  local cert_size password_size
  cert_size="$(adb_bounded shell run-as "$PACKAGE_NAME" stat -c %s "$CERTIFICATE_PATH" 2>/dev/null | tr -d '\r' || true)"
  password_size="$(adb_bounded shell run-as "$PACKAGE_NAME" stat -c %s "$PASSWORD_PATH" 2>/dev/null | tr -d '\r' || true)"
  [[ "$cert_size" =~ ^[0-9]+$ && "$cert_size" -ge 1 && "$password_size" =~ ^[0-9]+$ && "$password_size" -ge 1 ]]
}

restart_emulator() {
  local total="$1"
  local emulator_bin="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/emulator/emulator"
  [[ -x "$emulator_bin" && -n "${REAL_E2E_EMULATOR_OPTIONS:-}" ]] || return 1
  adb_bounded -s "emulator-$EMULATOR_PORT" emu kill >/dev/null 2>&1 || true
  pkill -KILL -f qemu-system >/dev/null 2>&1 || true
  sleep 3
  local -a emulator_options
  read -r -a emulator_options <<<"$REAL_E2E_EMULATOR_OPTIONS"
  nohup "$emulator_bin" -port "$EMULATOR_PORT" -avd "$EMULATOR_AVD_NAME" "${emulator_options[@]}" \
    >"${RUNNER_TEMP:-/tmp}/real-e2e-emulator-restart.log" 2>&1 &
  local deadline=$((SECONDS + EMULATOR_BOOT_TIMEOUT_SECONDS)) booted=""
  while ((SECONDS < deadline)); do
    booted="$(adb_bounded shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    [[ "$booted" == 1 ]] && break
    sleep 5
  done
  [[ "$booted" == 1 ]] || return 1
  adb_bounded shell settings put global window_animation_scale 0.0 >/dev/null || return 1
  adb_bounded shell settings put global transition_animation_scale 0.0 >/dev/null || return 1
  adb_bounded shell settings put global animator_duration_scale 0.0 >/dev/null || return 1
  prepare_device "$total" || return 1
  fixture_present
}

write_synthetic_result() {
  local portal_id="$1" result_file="$2" reason="$3"
  "$REPORT_HELPER" synthetic \
    --catalog "$CATALOG_FILE" \
    --portal "$portal_id" \
    --output "$result_file" \
    --reason "$reason"
  "$REPORT_HELPER" validate-result --result "$result_file" --portal "$portal_id"
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
  : >"$PROGRESS_PATH"
  progress SELECT_START
  mapfile -t portal_ids < <(
    "$REPORT_HELPER" select \
      --catalog "$CATALOG_FILE" \
      --portal "${PORTAL_ID_FILTER:-}" \
      --shard-index "$shard_index" \
      --shard-total "$shard_total"
  )
  printf 'REAL_E2E shard %s/%s selected %d catalog portal(s).\n' \
    "$((shard_index + 1))" "$shard_total" "${#portal_ids[@]}"
  progress SELECT_DONE - 0 "${#portal_ids[@]}"

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
  progress INSTALL_QA_START - 0 "${#portal_ids[@]}"
  adb_install_bounded "$QA_APK" >/dev/null
  progress INSTALL_QA_DONE - 0 "${#portal_ids[@]}"
  progress INSTALL_TEST_START - 0 "${#portal_ids[@]}"
  adb_install_bounded "$TEST_APK" >/dev/null
  progress INSTALL_TEST_DONE - 0 "${#portal_ids[@]}"
  prepare_device "${#portal_ids[@]}"
  progress STAGE_FIXTURE_START - 0 "${#portal_ids[@]}"
  stage_fixture "${#portal_ids[@]}"
  progress STAGE_FIXTURE_DONE - 0 "${#portal_ids[@]}"

  local deep_arg="${REAL_E2E_DEEP_AUTH_SIGNING:-true}"
  [[ "$deep_arg" == true || "$deep_arg" == false ]] || {
    echo 'REAL_E2E_DEEP_AUTH_SIGNING must be true or false' >&2
    exit 64
  }

  local index=0 portal_id status result_file navigation_file reason
  local device_lost=false device_available=true restarts=0
  for portal_id in "${portal_ids[@]}"; do
    index=$((index + 1))
    printf '[%d/%d] %s\n' "$index" "${#portal_ids[@]}" "$portal_id"
    progress PORTAL_START "$portal_id" "$index" "${#portal_ids[@]}"
    result_file="$RESULTS_DIR/$portal_id.json"
    navigation_file="$LOGS_DIR/$portal_id.log"
    if [[ "$device_available" != true ]]; then
      write_synthetic_result "$portal_id" "$result_file" EMULATOR_LOST
      "$REPORT_HELPER" describe-result --result "$result_file" --portal "$portal_id"
      : >"$navigation_file"
      progress PORTAL_DONE "$portal_id" "$index" "${#portal_ids[@]}"
      continue
    fi
    adb_bounded shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
    adb_bounded shell run-as "$PACKAGE_NAME" rm -f "$RESULT_PATH" files/qa-navigation.log >/dev/null 2>&1 || true

    progress INSTRUMENT_START "$portal_id" "$index" "${#portal_ids[@]}"
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
    progress "INSTRUMENT_DONE_${status}" "$portal_id" "$index" "${#portal_ids[@]}"
    device_lost=false
    if ((status != 0)) && ! device_online; then
      device_lost=true
      progress DEVICE_LOST "$portal_id" "$index" "${#portal_ids[@]}"
      report_device_loss "$portal_id"
    fi

    progress RESULT_READ_START "$portal_id" "$index" "${#portal_ids[@]}"
    if [[ "$device_lost" == true ]]; then
      write_synthetic_result "$portal_id" "$result_file" EMULATOR_LOST
    elif ! adb_bounded exec-out run-as "$PACKAGE_NAME" cat "$RESULT_PATH" >"$result_file" 2>/dev/null || \
       ! "$REPORT_HELPER" validate-result --result "$result_file" --portal "$portal_id"; then
      if [[ $status -eq 124 ]]; then reason=TIMEOUT; else reason=RESULT_MISSING; fi
      write_synthetic_result "$portal_id" "$result_file" "$reason"
    fi

    "$REPORT_HELPER" describe-result --result "$result_file" --portal "$portal_id"
    progress RESULT_READ_DONE "$portal_id" "$index" "${#portal_ids[@]}"
    progress NAV_READ_START "$portal_id" "$index" "${#portal_ids[@]}"
    if [[ "$device_lost" != true ]] && \
       adb_bounded exec-out run-as "$PACKAGE_NAME" cat files/qa-navigation.log >"$navigation_file" 2>/dev/null; then
      "$REPORT_HELPER" validate-log --log "$navigation_file"
    else
      : >"$navigation_file"
    fi
    progress NAV_READ_DONE "$portal_id" "$index" "${#portal_ids[@]}"
    progress PORTAL_DONE "$portal_id" "$index" "${#portal_ids[@]}"

    if [[ "$device_lost" == true ]]; then
      if ((restarts >= EMULATOR_RESTART_LIMIT)); then
        device_available=false
        continue
      fi
      restarts=$((restarts + 1))
      progress EMULATOR_RESTART_START "$portal_id" "$index" "${#portal_ids[@]}"
      if restart_emulator "${#portal_ids[@]}"; then
        progress EMULATOR_RESTART_DONE "$portal_id" "$index" "${#portal_ids[@]}"
      else
        device_available=false
        progress EMULATOR_RESTART_FAILED "$portal_id" "$index" "${#portal_ids[@]}"
      fi
    fi
  done

  progress SUMMARY_START - "${#portal_ids[@]}" "${#portal_ids[@]}"
  "$REPORT_HELPER" summary \
    --catalog "$CATALOG_FILE" \
    --results "$RESULTS_DIR" \
    --portal "${PORTAL_ID_FILTER:-}" \
    --shard-index "$shard_index" \
    --shard-total "$shard_total" \
    --json-output "$REPORT_DIR/summary.json" \
    --markdown-output "$REPORT_DIR/summary.md"
  progress SUMMARY_DONE - "${#portal_ids[@]}" "${#portal_ids[@]}"
}

main "$@"
