#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly CATALOG_FILE="$ROOT_DIR/app/src/main/res/raw/public_portal_catalog_v1.json"
readonly REPORT_DIR="$ROOT_DIR/build/reports/site-smoke"
readonly RAW_DIR="$REPORT_DIR/raw"
readonly PACKAGE_NAME="dev.junta.firmamobile"
readonly MAIN_COMPONENT="$PACKAGE_NAME/.MainActivity"
readonly ACTION="dev.junta.firmamobile.action.CATALOG_SMOKE"
readonly RISH_BIN="${RISH_BIN:-/data/data/com.termux/files/usr/bin/rish}"

timeout_seconds=20
selection="explicit"
declare -a requested=()

usage() {
  cat <<'EOF'
Usage: scripts/android-site-smoke.sh [--all|--implemented] [--timeout SECONDS] [PORTAL_OR_PROFILE_ID ...]

Runs the QA-only, DUMP-protected catalog smoke bridge through Shizuku/rish.
No URL, certificate, password, cookie or signing payload is accepted by the bridge.
EOF
}

while (($#)); do
  case "$1" in
    --all)
      selection="all"
      shift
      ;;
    --implemented)
      selection="implemented"
      shift
      ;;
    --timeout)
      (($# >= 2)) || { echo "--timeout requires a value" >&2; exit 64; }
      timeout_seconds="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      echo "Unknown option: $1" >&2
      exit 64
      ;;
    *)
      requested+=("$1")
      shift
      ;;
  esac
done

[[ "$timeout_seconds" =~ ^[0-9]+$ ]] || { echo "Timeout must be an integer" >&2; exit 64; }
((timeout_seconds >= 3 && timeout_seconds <= 120)) || {
  echo "Timeout must be between 3 and 120 seconds" >&2
  exit 64
}
[[ -r "$CATALOG_FILE" ]] || { echo "Catalog resource not found" >&2; exit 66; }
[[ -x "$RISH_BIN" ]] || { echo "rish is unavailable: $RISH_BIN" >&2; exit 69; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }

declare -a portal_ids=()
case "$selection" in
  all)
    mapfile -t portal_ids < <(jq -er '.entries[].portalId' "$CATALOG_FILE")
    ;;
  implemented)
    mapfile -t portal_ids < <(jq -er '.entries[] | select(.profileId != null) | .portalId' "$CATALOG_FILE")
    ;;
  explicit)
    ((${#requested[@]} > 0)) || {
      echo "Provide one or more IDs, --implemented, or --all" >&2
      exit 64
    }
    for identifier in "${requested[@]}"; do
      [[ "$identifier" =~ ^[a-z0-9][a-z0-9-]{0,95}$ ]] || {
        echo "Invalid portal/profile ID" >&2
        exit 64
      }
      mapfile -t matches < <(
        jq -er --arg id "$identifier" \
          '.entries[] | select(.portalId == $id or .profileId == $id) | .portalId' \
          "$CATALOG_FILE"
      ) || true
      ((${#matches[@]} == 1)) || {
        echo "ID must resolve to exactly one catalog portal: $identifier" >&2
        exit 65
      }
      portal_ids+=("${matches[0]}")
    done
    ;;
esac

((${#portal_ids[@]} > 0)) || { echo "No catalog portals selected" >&2; exit 65; }
mapfile -t portal_ids < <(printf '%s\n' "${portal_ids[@]}" | awk '!seen[$0]++')
for portal_id in "${portal_ids[@]}"; do
  [[ "$portal_id" =~ ^[a-z0-9][a-z0-9-]{0,95}$ ]] || {
    echo "Catalog contains an unsafe portal ID" >&2
    exit 65
  }
done

run_rish() {
  timeout "$timeout_seconds" "$RISH_BIN" -c "$1" 2>&1
}

bridge_identity="$(run_rish 'id')" || {
  echo "Shizuku/rish shell is unavailable" >&2
  exit 69
}
grep -q 'uid=2000(shell)' <<<"$bridge_identity" || {
  echo "Shizuku bridge did not return Android shell identity" >&2
  exit 69
}

package_path="$(run_rish "pm path $PACKAGE_NAME")" || true
grep -q '^package:' <<<"$package_path" || {
  echo "Package is not installed: $PACKAGE_NAME" >&2
  exit 69
}

resumed_activity="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
if [[ "$resumed_activity" != *"$PACKAGE_NAME/.MainActivity"* ]]; then
  launch_output="$(run_rish "am start -W --user 0 -f 0x24000000 -n $MAIN_COMPONENT")" || true
  grep -q '^Status: ok' <<<"$launch_output" || {
    echo "MainActivity did not start successfully" >&2
    exit 70
  }
fi

resumed_activity="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
[[ "$resumed_activity" == *"$PACKAGE_NAME/.MainActivity"* ]] || {
  echo "MainActivity is not the resumed foreground activity" >&2
  exit 70
}
batch_pid="$(run_rish "pidof $PACKAGE_NAME")" || true
batch_pid="${batch_pid%% *}"
[[ "$batch_pid" =~ ^[0-9]+$ ]] || {
  echo "Application process is not alive" >&2
  exit 70
}

mkdir -p "$RAW_DIR"
records_file="$(mktemp "$REPORT_DIR/.records.XXXXXX")"
cleanup() { rm -f "$records_file" "$REPORT_DIR/.latest.json.tmp" "$REPORT_DIR/.latest.md.tmp"; }
trap cleanup EXIT

extract_result_json() {
  local raw="$1" line
  line="$(sed -n 's/^Broadcast completed: result=[^,]*, data="\(.*\)"$/\1/p' <<<"$raw" | tail -n 1)"
  [[ -n "$line" ]] || return 1
  line="${line//\\\"/\"}"
  jq -e 'select(type == "object" and .schemaVersion == 1)' <<<"$line"
}

run_command() {
  local portal_id="$1" run_id="$2" operation="$3" output_file="$4"
  local output code result attempt
  : >"$output_file"
  for attempt in 1 2 3; do
    set +e
    output="$(run_rish "am broadcast --user 0 -a $ACTION -p $PACKAGE_NAME --es runId $run_id --es portalId $portal_id --es operation $operation")"
    code=$?
    set -e
    printf 'attempt=%s\n%s\n' "$attempt" "$output" >>"$output_file"
    if ((code == 0)) && grep -q '^Broadcast completed:' <<<"$output"; then
      if result="$(extract_result_json "$output")"; then
        printf '%s\n' "$result"
        return 0
      fi
    fi
    if ((attempt < 3)) && {
      ((code == 124)) || grep -qiE 'request timeout|connection.*shizuku|binder.*(dead|failed)|broken pipe' <<<"$output"
    }; then
      sleep "$attempt"
      continue
    fi
    ((code != 0)) && return "$code"
    return 70
  done
  return 70
}

synthetic_result() {
  local run_id="$1" portal_id="$2" result="$3"
  jq -cn --arg runId "$run_id" --arg portalId "$portal_id" --arg result "$result" \
    '{schemaVersion:1,runId:$runId,portalId:$portalId,profileId:null,adapterId:null,entryUrl:null,supportStatus:null,result:$result}'
}

run_started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
run_started_epoch="$(date +%s)"
index=0
for portal_id in "${portal_ids[@]}"; do
  index=$((index + 1))
  run_id="smoke-$(date -u +%Y%m%dT%H%M%S)-$index"
  started_ms="$(date +%s%3N)"
  started_epoch="$(date +%s)"
  open_file="$RAW_DIR/$run_id-open.txt"
  inspect_file="$RAW_DIR/$run_id-inspect.txt"
  timed_out=false
  opened_webview=false

  set +e
  open_json="$(run_command "$portal_id" "$run_id" OPEN "$open_file")"
  open_code=$?
  set -e
  if ((open_code == 124)); then timed_out=true; fi
  if ((open_code != 0)) || ! jq -e . >/dev/null 2>&1 <<<"$open_json"; then
    open_json="$(synthetic_result "$run_id" "$portal_id" BRIDGE_ERROR)"
  fi

  inspect_json="$(synthetic_result "$run_id" "$portal_id" INSPECT_NOT_REQUIRED)"
  if [[ "$(jq -r '.result' <<<"$open_json")" == "OPEN_REQUESTED" ]]; then
    opened_webview=true
    inspect_deadline=$((SECONDS + timeout_seconds))
    while ((SECONDS < inspect_deadline)); do
      sleep 0.5
      set +e
      inspect_json="$(run_command "$portal_id" "$run_id" INSPECT "$inspect_file")"
      inspect_code=$?
      set -e
      if ((inspect_code == 124)); then
        timed_out=true
        inspect_json="$(synthetic_result "$run_id" "$portal_id" BRIDGE_ERROR)"
        break
      fi
      if ((inspect_code != 0)) || ! jq -e . >/dev/null 2>&1 <<<"$inspect_json"; then
        inspect_json="$(synthetic_result "$run_id" "$portal_id" BRIDGE_ERROR)"
        break
      fi
      inspect_result="$(jq -r '.result' <<<"$inspect_json")"
      [[ "$inspect_result" == "WEBVIEW_NOT_ACTIVE" ]] || break
    done
    if [[ "$(jq -r '.result' <<<"$inspect_json")" == "WEBVIEW_NOT_ACTIVE" ]]; then
      timed_out=true
    fi
  fi

  process_alive=true
  activity_started=true
  crash_detected=false
  anr_detected=false
  if [[ "$opened_webview" == true ]]; then
    pid_output="$(run_rish "pidof $PACKAGE_NAME")" || true
    pid_after="${pid_output%% *}"
    [[ "$pid_after" == "$batch_pid" ]] || process_alive=false
    focus_output="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
    [[ "$focus_output" == *"$PACKAGE_NAME/.MainActivity"* ]] || activity_started=false

    crash_signals="$(run_rish "logcat -d --pid $batch_pid -v brief -T $started_epoch.000 AndroidRuntime:E '*:S'")" || true
    anr_signals="$(run_rish "logcat -d -v brief -T $started_epoch.000 ActivityManager:E '*:S'")" || true
    grep -q "FATAL EXCEPTION" <<<"$crash_signals" && crash_detected=true
    grep -q "ANR in $PACKAGE_NAME" <<<"$anr_signals" && anr_detected=true
  fi

  finished_ms="$(date +%s%3N)"
  duration_ms=$((finished_ms - started_ms))
  open_result="$(jq -r '.result' <<<"$open_json")"
  inspect_result="$(jq -r '.result' <<<"$inspect_json")"
  webview_opened=false
  [[ "$inspect_result" == "WEBVIEW_ACTIVE" ]] && webview_opened=true
  navigation_blocked=false
  [[ "$open_result" =~ ^(PROFILE_DISABLED|BRIDGE_ERROR|INVALID_REQUEST|UNKNOWN_PORTAL)$ ]] && navigation_blocked=true
  result="$(if [[ "$open_result" == "OPEN_REQUESTED" ]]; then printf '%s' "$inspect_result"; else printf '%s' "$open_result"; fi)"
  failed=false
  if [[ ! "$result" =~ ^(CATALOG_ONLY|PROFILE_RESOLVED|WEBVIEW_ACTIVE)$ ]] || \
      [[ "$activity_started" != true || "$process_alive" != true || "$timed_out" == true || \
         "$crash_detected" == true || "$anr_detected" == true ]]; then
    failed=true
  fi

  jq -cn \
    --argjson open "$open_json" \
    --argjson inspect "$inspect_json" \
    --argjson activityStarted "$activity_started" \
    --argjson processAlive "$process_alive" \
    --argjson webViewOpened "$webview_opened" \
    --argjson navigationBlocked "$navigation_blocked" \
    --argjson timeoutDetected "$timed_out" \
    --argjson crashDetected "$crash_detected" \
    --argjson anrDetected "$anr_detected" \
    --argjson failed "$failed" \
    --argjson durationMs "$duration_ms" \
    '{
      runId: $open.runId,
      portalId: $open.portalId,
      profileId: $open.profileId,
      origin: ($open.entryUrl // "" | capture("^(?<origin>https://[^/]+)").origin? // null),
      adapterId: $open.adapterId,
      activityStarted: $activityStarted,
      processAlive: $processAlive,
      profileResolved: ($open.result == "PROFILE_RESOLVED" or $open.result == "OPEN_REQUESTED"),
      webViewOpened: $webViewOpened,
      externalBrowserOpened: false,
      autofirmaIntentObserved: false,
      clientCertRequestObserved: false,
      navigationBlocked: $navigationBlocked,
      timeout: $timeoutDetected,
      crashDetected: $crashDetected,
      anrDetected: $anrDetected,
      failed: $failed,
      durationMs: $durationMs,
      result: (if $open.result == "OPEN_REQUESTED" then $inspect.result else $open.result end),
      evidence: (["DUMP_PROTECTED_BROADCAST", $open.result, $inspect.result] | unique)
    }' >>"$records_file"
done

final_pid="$(run_rish "pidof $PACKAGE_NAME")" || true
final_pid="${final_pid%% *}"
batch_process_alive=false
[[ "$final_pid" == "$batch_pid" ]] && batch_process_alive=true
final_focus="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
batch_activity_started=false
[[ "$final_focus" == *"$PACKAGE_NAME/.MainActivity"* ]] && batch_activity_started=true
batch_crash_signals="$(run_rish "logcat -d --pid $batch_pid -v brief -T $run_started_epoch.000 AndroidRuntime:E '*:S'")" || true
batch_anr_signals="$(run_rish "logcat -d -v brief -T $run_started_epoch.000 ActivityManager:E '*:S'")" || true
batch_crash_detected=false
batch_anr_detected=false
grep -q "FATAL EXCEPTION" <<<"$batch_crash_signals" && batch_crash_detected=true
grep -q "ANR in $PACKAGE_NAME" <<<"$batch_anr_signals" && batch_anr_detected=true

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
jq -s \
  --arg generatedAt "$generated_at" \
  --arg startedAt "$run_started_at" \
  --arg packageName "$PACKAGE_NAME" \
  --arg packagePath "${package_path#package:}" \
  --argjson batchProcessAlive "$batch_process_alive" \
  --argjson batchActivityStarted "$batch_activity_started" \
  --argjson batchCrashDetected "$batch_crash_detected" \
  --argjson batchAnrDetected "$batch_anr_detected" \
  'map(
    .processAlive = (.processAlive and $batchProcessAlive)
    | .activityStarted = (.activityStarted and $batchActivityStarted)
    | .crashDetected = (.crashDetected or $batchCrashDetected)
    | .anrDetected = (.anrDetected or $batchAnrDetected)
    | .failed = (
        .failed
        or ($batchProcessAlive == false)
        or ($batchActivityStarted == false)
        or $batchCrashDetected
        or $batchAnrDetected
      )
  ) as $results
  | {
    schemaVersion: 1,
    generatedAt: $generatedAt,
    startedAt: $startedAt,
    packageName: $packageName,
    packagePath: $packagePath,
    results: $results,
    summary: {
      total: ($results | length),
      webViewActive: ($results | map(select(.webViewOpened)) | length),
      profileResolvedOnly: ($results | map(select(.result == "PROFILE_RESOLVED")) | length),
      catalogOnly: ($results | map(select(.result == "CATALOG_ONLY")) | length),
      failures: ($results | map(select(.failed)) | length)
    }
  }' "$records_file" >"$REPORT_DIR/.latest.json.tmp"
mv "$REPORT_DIR/.latest.json.tmp" "$REPORT_DIR/latest.json"

{
  echo "# Android site smoke"
  echo
  echo "Generated: $generated_at"
  echo
  echo '| Portal | Profile | Result | WebView | Crash/ANR | Duration |'
  echo '|---|---|---|---:|---:|---:|'
  jq -r '.results[] | "| \(.portalId) | \(.profileId // "—") | \(.result) | \(.webViewOpened) | \(.crashDetected or .anrDetected) | \(.durationMs) ms |"' \
    "$REPORT_DIR/latest.json"
} >"$REPORT_DIR/.latest.md.tmp"
mv "$REPORT_DIR/.latest.md.tmp" "$REPORT_DIR/latest.md"

jq '.summary' "$REPORT_DIR/latest.json"
if ! jq -e '.summary.failures == 0' "$REPORT_DIR/latest.json" >/dev/null; then
  exit 1
fi
