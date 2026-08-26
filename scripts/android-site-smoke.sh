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

timeout_seconds=60
settle_seconds=3
selection="explicit"
declare -a requested=()

usage() {
  cat <<'USAGE'
Usage: scripts/android-site-smoke.sh [--all|--implemented] [--timeout SECONDS] [--settle SECONDS] [PORTAL_OR_PROFILE_ID ...]

Runs the QA-only, DUMP-protected E2E observation bridge through Shizuku/rish.
The command surface accepts only catalog portal/profile IDs and a closed operation enum.
It never accepts a URL, JavaScript, selector, certificate, password, cookie or signing payload.

The runner does not confirm certificate sharing, client-auth or signing dialogs. Those remain visible
manual boundaries. If a user confirms one, subsequent real runtime events are captured in the same run.
USAGE
}

while (($#)); do
  case "$1" in
    --all) selection="all"; shift ;;
    --implemented) selection="implemented"; shift ;;
    --timeout)
      (($# >= 2)) || { echo "--timeout requires a value" >&2; exit 64; }
      timeout_seconds="$2"; shift 2 ;;
    --settle)
      (($# >= 2)) || { echo "--settle requires a value" >&2; exit 64; }
      settle_seconds="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    --*) echo "Unknown option: $1" >&2; exit 64 ;;
    *) requested+=("$1"); shift ;;
  esac
done

[[ "$timeout_seconds" =~ ^[0-9]+$ ]] || { echo "Timeout must be an integer" >&2; exit 64; }
((timeout_seconds >= 3 && timeout_seconds <= 180)) || { echo "Timeout must be between 3 and 180 seconds" >&2; exit 64; }
[[ "$settle_seconds" =~ ^[0-9]+$ ]] || { echo "Settle must be an integer" >&2; exit 64; }
((settle_seconds >= 1 && settle_seconds <= 30)) || { echo "Settle must be between 1 and 30 seconds" >&2; exit 64; }
[[ -r "$CATALOG_FILE" ]] || { echo "Catalog resource not found" >&2; exit 66; }
[[ -x "$RISH_BIN" ]] || { echo "rish is unavailable: $RISH_BIN" >&2; exit 69; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }

# target format is "portal:<id>" or "profile:<id>". Bulk runs intentionally use portal IDs so
# duplicate public entries never make a profile identifier ambiguous.
declare -a targets=()
case "$selection" in
  all)
    mapfile -t targets < <(jq -er '.entries[].portalId | "portal:" + .' "$CATALOG_FILE")
    ;;
  implemented)
    mapfile -t targets < <(jq -er '.entries[] | select(.profileId != null) | "portal:" + .portalId' "$CATALOG_FILE")
    ;;
  explicit)
    ((${#requested[@]} > 0)) || { echo "Provide one or more IDs, --implemented, or --all" >&2; exit 64; }
    for identifier in "${requested[@]}"; do
      [[ "$identifier" =~ ^[a-z0-9][a-z0-9-]{0,95}$ ]] || { echo "Invalid portal/profile ID" >&2; exit 64; }
      portal_count="$(jq -r --arg id "$identifier" '[.entries[] | select(.portalId == $id)] | length' "$CATALOG_FILE")"
      profile_count="$(jq -r --arg id "$identifier" '[.entries[] | select(.profileId == $id)] | length' "$CATALOG_FILE")"
      if ((portal_count == 1)); then
        targets+=("portal:$identifier")
      elif ((profile_count > 0)); then
        targets+=("profile:$identifier")
      else
        echo "Unknown catalog portal/profile ID: $identifier" >&2
        exit 65
      fi
    done
    ;;
esac

((${#targets[@]} > 0)) || { echo "No catalog targets selected" >&2; exit 65; }
mapfile -t targets < <(printf '%s\n' "${targets[@]}" | awk '!seen[$0]++')

run_rish() {
  timeout "$timeout_seconds" "$RISH_BIN" -c "$1" 2>&1
}

bridge_identity="$(run_rish 'id')" || { echo "Shizuku/rish shell is unavailable" >&2; exit 69; }
grep -q 'uid=2000(shell)' <<<"$bridge_identity" || { echo "Shizuku bridge did not return Android shell identity" >&2; exit 69; }
package_path="$(run_rish "pm path $PACKAGE_NAME")" || true
grep -q '^package:' <<<"$package_path" || { echo "Package is not installed: $PACKAGE_NAME" >&2; exit 69; }
run_as_identity="$(run_rish "run-as $PACKAGE_NAME id")" || {
  echo "Installed package is not a debuggable QA build (run-as unavailable)" >&2
  exit 69
}
grep -q "u0_a" <<<"$run_as_identity" || { echo "Unexpected run-as identity" >&2; exit 69; }

resumed_activity="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
if [[ "$resumed_activity" != *"$PACKAGE_NAME/.MainActivity"* ]]; then
  launch_output="$(run_rish "am start -W --user 0 -f 0x24000000 -n $MAIN_COMPONENT")" || true
  grep -q '^Status: ok' <<<"$launch_output" || { echo "MainActivity did not start successfully" >&2; exit 70; }
fi
resumed_activity="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
[[ "$resumed_activity" == *"$PACKAGE_NAME/.MainActivity"* ]] || { echo "MainActivity is not the resumed foreground activity" >&2; exit 70; }

batch_pid="$(run_rish "pidof $PACKAGE_NAME")" || true
batch_pid="${batch_pid%% *}"
[[ "$batch_pid" =~ ^[0-9]+$ ]] || { echo "Application process is not alive" >&2; exit 70; }

mkdir -p "$RAW_DIR"
records_file="$(mktemp "$REPORT_DIR/.records.XXXXXX")"
cleanup() { rm -f "$records_file" "$REPORT_DIR/.latest.json.tmp" "$REPORT_DIR/.latest.md.tmp"; }
trap cleanup EXIT

extract_result_json() {
  local raw="$1" line
  line="$(sed -n 's/^Broadcast completed: result=[^,]*, data="\(.*\)"$/\1/p' <<<"$raw" | tail -n 1)"
  [[ -n "$line" ]] || return 1
  line="${line//\\\"/\"}"
  jq -e 'select(type == "object" and .schemaVersion == 2)' <<<"$line"
}

run_command() {
  local target_kind="$1" target_id="$2" run_id="$3" operation="$4" output_file="$5"
  local extra_name output code result attempt
  [[ "$target_kind" == "portal" ]] && extra_name="portalId" || extra_name="profileId"
  : >"$output_file"
  for attempt in 1 2 3; do
    set +e
    output="$(run_rish "am broadcast --user 0 -a $ACTION -p $PACKAGE_NAME --es runId $run_id --es $extra_name $target_id --es operation $operation")"
    code=$?
    set -e
    printf 'attempt=%s\n%s\n' "$attempt" "$output" >>"$output_file"
    if ((code == 0)) && grep -q '^Broadcast completed:' <<<"$output"; then
      if result="$(extract_result_json "$output")"; then printf '%s\n' "$result"; return 0; fi
    fi
    if ((attempt < 3)) && { ((code == 124)) || grep -qiE 'request timeout|connection.*shizuku|binder.*(dead|failed)|broken pipe' <<<"$output"; }; then
      sleep "$attempt"; continue
    fi
    ((code != 0)) && return "$code"
    return 70
  done
  return 70
}

synthetic_result() {
  local run_id="$1" target_kind="$2" target_id="$3" result="$4"
  jq -cn --arg runId "$run_id" --arg kind "$target_kind" --arg id "$target_id" --arg result "$result" '
    {schemaVersion:2,runId:$runId,portalId:(if $kind=="portal" then $id else null end),profileId:(if $kind=="profile" then $id else null end),adapterId:null,entryUrl:null,supportStatus:null,result:$result,runtime:null}'
}

runtime_terminal() {
  jq -e '(.runtime // {}) as $r | ($r.failureCode != null) or ($r.renderProcessGone == true) or ($r.signingCompletedObserved == true) or ($r.signingFailedObserved == true) or ($r.clientAuthConfirmationRequired == true) or ($r.certificateSelectionRequired == true) or ($r.signingConfirmationRequired == true)' >/dev/null <<<"$1"
}

observed_stage() {
  jq -r '
    (.runtime // {}) as $r |
    if $r.signingCompletedObserved == true then "SIGNING_COMPLETED"
    elif $r.signingFailedObserved == true then "SIGNING_FAILED"
    elif $r.portalCallbackObserved == true then "PORTAL_CALLBACK"
    elif $r.clientCertAcceptedObserved == true then "CLIENT_CERT_ACCEPTED"
    elif $r.signingConfirmationRequired == true then "SIGNING_CONFIRMATION_REQUIRED"
    elif $r.clientAuthConfirmationRequired == true then "CLIENT_AUTH_CONFIRMATION_REQUIRED"
    elif $r.certificateSelectionRequired == true then "CERTIFICATE_SELECTION_REQUIRED"
    elif $r.afirmaRequestObserved == true then "AFIRMA_REQUEST"
    elif $r.autofirmaIntentObserved == true then "AUTOFIRMA_INTENT"
    elif $r.clientCertRequestObserved == true then "CLIENT_CERT_REQUEST"
    elif $r.webViewActive == true then "WEBVIEW_ACTIVE"
    else .result end' <<<"$1"
}

run_started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
run_started_epoch="$(date +%s)"
index=0
for target in "${targets[@]}"; do
  index=$((index + 1))
  target_kind="${target%%:*}"
  target_id="${target#*:}"
  run_id="e2e-$(date -u +%Y%m%dT%H%M%S)-$index"
  started_ms="$(date +%s%3N)"
  started_epoch="$(date +%s)"
  open_file="$RAW_DIR/$run_id-open.txt"
  inspect_file="$RAW_DIR/$run_id-inspect.txt"
  timed_out=false
  opened_webview=false
  # The file contains only QaDiagnosticFileSink allowlisted ASCII records. Clearing it per target
  # makes the foreground-loss fallback exact to this single sequential run.
  run_rish "run-as $PACKAGE_NAME sh -c ': > files/qa-navigation.log'" >/dev/null || {
    echo "Could not reset QA sanitized diagnostic log" >&2
    exit 70
  }

  set +e
  open_json="$(run_command "$target_kind" "$target_id" "$run_id" OPEN "$open_file")"
  open_code=$?
  set -e
  if ((open_code == 124)); then timed_out=true; fi
  if ((open_code != 0)) || ! jq -e . >/dev/null 2>&1 <<<"$open_json"; then
    open_json="$(synthetic_result "$run_id" "$target_kind" "$target_id" BRIDGE_ERROR)"
  fi

  inspect_json="$(synthetic_result "$run_id" "$target_kind" "$target_id" INSPECT_NOT_REQUIRED)"
  if [[ "$(jq -r '.result' <<<"$open_json")" == "OPEN_REQUESTED" ]]; then
    deadline=$((SECONDS + timeout_seconds))
    stable_since=-1
    last_sequence=-1
    while ((SECONDS < deadline)); do
      sleep 0.5
      set +e
      inspect_json="$(run_command "$target_kind" "$target_id" "$run_id" INSPECT "$inspect_file")"
      inspect_code=$?
      set -e
      if ((inspect_code == 124)); then timed_out=true; inspect_json="$(synthetic_result "$run_id" "$target_kind" "$target_id" BRIDGE_ERROR)"; break; fi
      if ((inspect_code != 0)) || ! jq -e . >/dev/null 2>&1 <<<"$inspect_json"; then
        inspect_result="$(jq -r '.result // empty' <<<"$inspect_json" 2>/dev/null || true)"
        if [[ "$inspect_result" == "WEBVIEW_NOT_ACTIVE" || "$inspect_result" == "RUN_NOT_ACTIVE" ]]; then continue; fi
        inspect_json="$(synthetic_result "$run_id" "$target_kind" "$target_id" BRIDGE_ERROR)"; break
      fi
      inspect_result="$(jq -r '.result' <<<"$inspect_json")"
      if [[ "$inspect_result" == "WEBVIEW_ACTIVE" ]]; then
        opened_webview=true
        runtime_terminal "$inspect_json" && break
        sequence="$(jq -r '(.runtime.events[-1].sequence // 0)' <<<"$inspect_json")"
        if [[ "$sequence" != "$last_sequence" ]]; then last_sequence="$sequence"; stable_since=$SECONDS; fi
        if ((stable_since >= 0 && SECONDS - stable_since >= settle_seconds)); then break; fi
      elif [[ "$inspect_result" != "WEBVIEW_NOT_ACTIVE" && "$inspect_result" != "RUN_NOT_ACTIVE" ]]; then
        break
      fi
    done
    if [[ "$opened_webview" != true && "$(jq -r '.result' <<<"$inspect_json")" =~ ^(WEBVIEW_NOT_ACTIVE|RUN_NOT_ACTIVE)$ ]]; then timed_out=true; fi
  fi

  process_alive=true
  activity_started=true
  crash_detected=false
  anr_detected=false
  pid_output="$(run_rish "pidof $PACKAGE_NAME")" || true
  pid_after="${pid_output%% *}"
  [[ "$pid_after" == "$batch_pid" ]] || process_alive=false
  focus_output="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
  [[ "$focus_output" == *"$PACKAGE_NAME/.MainActivity"* ]] || activity_started=false
  crash_signals="$(run_rish "logcat -d --pid $batch_pid -v brief -T $started_epoch.000 AndroidRuntime:E '*:S'")" || true
  anr_signals="$(run_rish "logcat -d -v brief -T $started_epoch.000 ActivityManager:E '*:S'")" || true
  grep -q "FATAL EXCEPTION" <<<"$crash_signals" && crash_detected=true
  grep -q "ANR in $PACKAGE_NAME" <<<"$anr_signals" && anr_detected=true

  qa_diagnostics="$(run_rish "run-as $PACKAGE_NAME cat files/qa-navigation.log")" || true
  autofirma_handoff=false
  portal_callback_fallback=false
  grep -qE '(^| )event=EXTERNAL_NAVIGATION( |$).*host=autofirma( |$)' <<<"$qa_diagnostics" && autofirma_handoff=true
  grep -qE '(^| )event=PORTAL_CALLBACK( |$)' <<<"$qa_diagnostics" && portal_callback_fallback=true

  finished_ms="$(date +%s%3N)"
  duration_ms=$((finished_ms - started_ms))
  open_result="$(jq -r '.result' <<<"$open_json")"
  final_json="$open_json"
  [[ "$open_result" == "OPEN_REQUESTED" ]] && final_json="$inspect_json"
  final_result="$(jq -r '.result' <<<"$final_json")"
  stage="$(observed_stage "$final_json")"
  if [[ "$autofirma_handoff" == true ]]; then
    stage="AUTOFIRMA_INTENT"
    opened_webview=true
  elif [[ "$portal_callback_fallback" == true && "$stage" =~ ^(BRIDGE_ERROR|WEBVIEW_ACTIVE)$ ]]; then
    stage="PORTAL_CALLBACK"
  fi

  manual_action=false
  blocked=false
  reason=null
  if jq -e '(.runtime // {}) | .signingConfirmationRequired == true' >/dev/null <<<"$final_json"; then manual_action=true; blocked=true; reason='"MANUAL_SIGNING_CONFIRMATION_REQUIRED"';
  elif jq -e '(.runtime // {}) | .clientAuthConfirmationRequired == true' >/dev/null <<<"$final_json"; then manual_action=true; blocked=true; reason='"MANUAL_CLIENT_AUTH_CONFIRMATION_REQUIRED"';
  elif jq -e '(.runtime // {}) | .certificateSelectionRequired == true' >/dev/null <<<"$final_json"; then manual_action=true; blocked=true; reason='"MANUAL_CERTIFICATE_SHARING_CONFIRMATION_REQUIRED"';
  elif [[ "$open_result" == "PROFILE_RESOLVED" ]]; then blocked=true; reason='"CERTIFICATE_SESSION_LOCKED"';
  else
    runtime_reason="$(jq -r '(.runtime.failureCode // empty)' <<<"$final_json")"
    [[ -n "$runtime_reason" ]] && reason="$(jq -cn --arg v "$runtime_reason" '$v')"
  fi

  hard_failure=false
  [[ "$open_result" =~ ^(BRIDGE_ERROR|INVALID_REQUEST|UNKNOWN_PORTAL|UNKNOWN_PROFILE|AMBIGUOUS_PROFILE|PROFILE_DISABLED)$ ]] && hard_failure=true
  if [[ "$final_result" == "BRIDGE_ERROR" && "$autofirma_handoff" != true ]]; then hard_failure=true; fi
  jq -e '(.runtime // {}) | (.signingFailedObserved == true or .renderProcessGone == true or .failureCode != null)' >/dev/null <<<"$final_json" && hard_failure=true
  [[ "$process_alive" != true || "$timed_out" == true || "$crash_detected" == true || "$anr_detected" == true ]] && hard_failure=true
  if [[ "$activity_started" != true && "$autofirma_handoff" != true ]]; then hard_failure=true; fi

  jq -cn \
    --argjson open "$open_json" --argjson final "$final_json" \
    --arg targetKind "$target_kind" --arg targetId "$target_id" --arg stage "$stage" \
    --argjson activityStarted "$activity_started" --argjson processAlive "$process_alive" \
    --argjson webViewOpened "$opened_webview" --argjson timeoutDetected "$timed_out" \
    --argjson crashDetected "$crash_detected" --argjson anrDetected "$anr_detected" \
    --argjson blocked "$blocked" --argjson manualActionRequired "$manual_action" \
    --argjson failed "$hard_failure" --argjson reason "$reason" --argjson durationMs "$duration_ms" \
    --argjson autofirmaFallback "$autofirma_handoff" --argjson portalCallbackFallback "$portal_callback_fallback" '
    {
      runId: $open.runId,
      targetKind: $targetKind,
      targetId: $targetId,
      portalId: ($final.portalId // $open.portalId),
      profileId: ($final.profileId // $open.profileId),
      adapterId: ($final.adapterId // $open.adapterId),
      entryUrl: ($final.entryUrl // $open.entryUrl),
      activityStarted: $activityStarted,
      processAlive: $processAlive,
      profileResolved: ($open.result == "PROFILE_RESOLVED" or $open.result == "OPEN_REQUESTED"),
      webViewOpened: $webViewOpened,
      observedStage: $stage,
      autofirmaIntentObserved: (((($final.runtime // {}).autofirmaIntentObserved) == true) or $autofirmaFallback),
      portalCallbackObserved: (((($final.runtime // {}).portalCallbackObserved) == true) or $portalCallbackFallback),
      blocked: $blocked,
      manualActionRequired: $manualActionRequired,
      timeout: $timeoutDetected,
      crashDetected: $crashDetected,
      anrDetected: $anrDetected,
      failed: $failed,
      reason: $reason,
      durationMs: $durationMs,
      result: (if $open.result == "OPEN_REQUESTED" then $final.result else $open.result end),
      runtime: $final.runtime,
      evidence: (["DUMP_PROTECTED_ORDERED_BROADCAST", $open.result, $final.result]
        + (if $autofirmaFallback then ["SANITIZED_LOG_AUTOFIRMA_HANDOFF"] else [] end)
        + (if $portalCallbackFallback then ["SANITIZED_LOG_PORTAL_CALLBACK"] else [] end)
        + (($final.runtime.events // []) | map(.code)) | unique)
    }' >>"$records_file"
done

final_pid="$(run_rish "pidof $PACKAGE_NAME")" || true
final_pid="${final_pid%% *}"
batch_process_alive=false; [[ "$final_pid" == "$batch_pid" ]] && batch_process_alive=true
final_focus="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
batch_activity_started=false; [[ "$final_focus" == *"$PACKAGE_NAME/.MainActivity"* ]] && batch_activity_started=true
batch_crash_signals="$(run_rish "logcat -d --pid $batch_pid -v brief -T $run_started_epoch.000 AndroidRuntime:E '*:S'")" || true
batch_anr_signals="$(run_rish "logcat -d -v brief -T $run_started_epoch.000 ActivityManager:E '*:S'")" || true
batch_crash_detected=false; batch_anr_detected=false
grep -q "FATAL EXCEPTION" <<<"$batch_crash_signals" && batch_crash_detected=true
grep -q "ANR in $PACKAGE_NAME" <<<"$batch_anr_signals" && batch_anr_detected=true

generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
jq -s \
  --arg generatedAt "$generated_at" --arg startedAt "$run_started_at" \
  --arg packageName "$PACKAGE_NAME" --arg packagePath "${package_path#package:}" \
  --argjson batchProcessAlive "$batch_process_alive" --argjson batchActivityStarted "$batch_activity_started" \
  --argjson batchCrashDetected "$batch_crash_detected" --argjson batchAnrDetected "$batch_anr_detected" '
  map(
    .processAlive = (.processAlive and $batchProcessAlive)
    | .activityStarted = (.activityStarted and $batchActivityStarted)
    | .crashDetected = (.crashDetected or $batchCrashDetected)
    | .anrDetected = (.anrDetected or $batchAnrDetected)
    | .failed = (.failed or ($batchProcessAlive == false) or ($batchActivityStarted == false) or $batchCrashDetected or $batchAnrDetected)
  ) as $results
  | {
      schemaVersion: 2,
      generatedAt: $generatedAt,
      startedAt: $startedAt,
      packageName: $packageName,
      packagePath: $packagePath,
      results: $results,
      summary: {
        total: ($results | length),
        webViewActive: ($results | map(select(.webViewOpened)) | length),
        clientCertAccepted: ($results | map(select(.runtime.clientCertAcceptedObserved == true)) | length),
        signingConfirmationRequired: ($results | map(select(.runtime.signingConfirmationRequired == true)) | length),
        signingCompleted: ($results | map(select(.runtime.signingCompletedObserved == true)) | length),
        portalCallbackObserved: ($results | map(select(.runtime.portalCallbackObserved == true)) | length),
        blocked: ($results | map(select(.blocked)) | length),
        timeout: ($results | map(select(.timeout)) | length),
        crashDetected: ($results | map(select(.crashDetected)) | length),
        anrDetected: ($results | map(select(.anrDetected)) | length),
        failed: ($results | map(select(.failed)) | length)
      }
    }' "$records_file" >"$REPORT_DIR/.latest.json.tmp"
mv "$REPORT_DIR/.latest.json.tmp" "$REPORT_DIR/latest.json"

{
  echo "# Android site E2E observation"
  echo
  echo "Generated: $generated_at"
  echo
  echo '| Portal/Profile | Stage | Result | Blocked | Failure | Duration |'
  echo '|---|---|---|---:|---:|---:|'
  jq -r '.results[] | "| \(.portalId // .profileId // .targetId) | \(.observedStage) | \(.result) | \(.blocked) | \(.failed) | \(.durationMs) ms |"' "$REPORT_DIR/latest.json"
} >"$REPORT_DIR/.latest.md.tmp"
mv "$REPORT_DIR/.latest.md.tmp" "$REPORT_DIR/latest.md"

jq '.summary' "$REPORT_DIR/latest.json"
if ! jq -e '.summary.failed == 0' "$REPORT_DIR/latest.json" >/dev/null; then exit 1; fi
