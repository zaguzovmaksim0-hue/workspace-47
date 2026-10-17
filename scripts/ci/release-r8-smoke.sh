#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME=dev.junta.firmamobile
readonly OUTPUT_DIR=app/build/outputs/release-smoke
readonly CATALOG_STARTUP_FAILURE='No se encontró una aplicación para abrir este sitio.'

APK_PATH=${1:-}
DIAGNOSTICS_SAVED=0
UI_DUMP_AVAILABLE=0

mkdir -p "$OUTPUT_DIR"

fail() {
  printf 'Release R8 smoke failed: %s\n' "$*" >&2
  exit 1
}

collect_diagnostics() {
  local window_dump

  adb logcat -d >"$OUTPUT_DIR/logcat.txt" 2>&1 || true
  adb logcat -d -b crash >"$OUTPUT_DIR/crash-buffer.txt" 2>&1 || true
  window_dump=$(adb shell dumpsys window 2>&1 || true)
  if ! printf '%s\n' "$window_dump" | grep -E 'mCurrentFocus|mFocusedApp' >"$OUTPUT_DIR/dumpsys-window.txt"; then
    printf '%s\n' "$window_dump" >"$OUTPUT_DIR/dumpsys-window.txt"
  fi

  if adb shell uiautomator dump /sdcard/release-r8-ui.xml >"$OUTPUT_DIR/uiautomator-dump.log" 2>&1 &&
    adb pull /sdcard/release-r8-ui.xml "$OUTPUT_DIR/ui.xml" >>"$OUTPUT_DIR/uiautomator-dump.log" 2>&1; then
    UI_DUMP_AVAILABLE=1
  else
    printf '%s\n' 'UI hierarchy dump unavailable; see uiautomator-dump.log.' >"$OUTPUT_DIR/ui.xml"
    UI_DUMP_AVAILABLE=0
  fi
  DIAGNOSTICS_SAVED=1
}

on_exit() {
  local status=$?
  trap - EXIT
  if ((DIAGNOSTICS_SAVED == 0)); then
    collect_diagnostics || true
  fi
  exit "$status"
}
trap on_exit EXIT

[[ $# -eq 1 && -n "$APK_PATH" ]] || fail "usage: $0 <release-apk>"
[[ -s "$APK_PATH" ]] || fail "release APK is missing or empty: $APK_PATH"

adb install -r "$APK_PATH" || fail "could not install release APK: $APK_PATH"
adb logcat -c || fail "could not clear logcat before launch"
adb shell am start -W -n "$PACKAGE_NAME/.MainActivity" || fail "could not start MainActivity"

focus_deadline=$((SECONDS + 60))
focused_window=
while ((SECONDS < focus_deadline)); do
  focused_window=$(adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' || true)
  if [[ "$focused_window" == *"$PACKAGE_NAME"* ]]; then
    break
  fi
  sleep 2
done
[[ "$focused_window" == *"$PACKAGE_NAME"* ]] || fail "MainActivity did not receive window focus within 60 seconds"

sleep 20
collect_diagnostics

pid=$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' || true)
[[ -n "$pid" ]] || fail "app process is not running after startup"

if grep -Fq "$PACKAGE_NAME" "$OUTPUT_DIR/crash-buffer.txt"; then
  fail "crash buffer contains an entry for $PACKAGE_NAME"
fi

if grep -F -A 8 'FATAL EXCEPTION' "$OUTPUT_DIR/logcat.txt" | grep -Fq "Process: $PACKAGE_NAME,"; then
  fail "logcat contains a FATAL EXCEPTION for $PACKAGE_NAME"
fi

if ((UI_DUMP_AVAILABLE == 1)) && grep -Fq "$CATALOG_STARTUP_FAILURE" "$OUTPUT_DIR/ui.xml"; then
  fail "catalog startup failure is visible: $CATALOG_STARTUP_FAILURE"
fi

printf 'Release R8 startup smoke passed for %s\n' "$PACKAGE_NAME"
