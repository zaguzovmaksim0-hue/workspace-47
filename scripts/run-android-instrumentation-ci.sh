#!/usr/bin/env bash
set -euo pipefail

# This fixture preparation must never target a user's physical Android device.
[[ "${GITHUB_ACTIONS:-}" == "true" && "${CI:-}" == "true" ]] || {
  echo "This harness is restricted to the disposable GitHub Actions emulator." >&2
  exit 64
}
[[ "$(adb -e shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]] || {
  echo "Refusing to modify a non-emulator device." >&2
  exit 65
}

state_dir="app/build/outputs/instrumentation-device-state"
mkdir -p "$state_dir"
printf 'verify_ref=%s\n' "${VERIFY_REF:-unknown}" > "$state_dir/source.txt"
capture_state() {
  local stage="$1" service
  for service in power window display activity; do
    timeout 8s adb -e shell dumpsys "$service" > "$state_dir/$stage-$service.txt" 2>&1 || true
  done
  timeout 8s adb -e exec-out screencap -p > "$state_dir/$stage-screen.png" 2> "$state_dir/$stage-screen-error.txt" || true
}
finish() {
  local status=$?
  trap - EXIT
  printf 'test_exit_code=%s\n' "$status" >> "$state_dir/source.txt"
  capture_state after
  exit "$status"
}
trap finish EXIT
capture_state before

# A build can leave the fresh AVD idle for several minutes. The subsequent
# real-window tests require an awake, unlocked test device, not synthetic
# gestures injected behind its lock screen. App lifecycle/consent tests remain
# unchanged; no production permission or security check is disabled.
adb -e shell svc power stayon true
adb -e shell settings put system screen_off_timeout 2147483647
adb -e shell input keyevent KEYCODE_WAKEUP
adb -e shell wm dismiss-keyguard
for attempt in {1..10}; do
  adb -e shell dumpsys power > "$state_dir/ready-power.txt"
  if grep -q 'mWakefulness=Awake' "$state_dir/ready-power.txt"; then break; fi
  sleep 1
done
grep -q 'mWakefulness=Awake' "$state_dir/ready-power.txt" || {
  echo "The emulator did not reach the required awake state." >&2
  exit 66
}
adb -e shell settings get system screen_off_timeout > "$state_dir/screen-off-timeout.txt"
adb -e shell settings get global stay_on_while_plugged_in > "$state_dir/stay-on.txt"
capture_state ready

# Never filter tests or mask the Gradle exit status. Diagnostics run on both
# success and failure so a lost-focus regression can be investigated directly.
./gradlew connectedQaAndroidTest --no-daemon --console=plain
