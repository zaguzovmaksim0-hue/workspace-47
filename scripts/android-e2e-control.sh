#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

readonly PACKAGE_NAME="${PACKAGE_NAME:-dev.junta.firmamobile}"
readonly MAIN_COMPONENT="$PACKAGE_NAME/dev.junta.firmamobile.MainActivity"
readonly ACTION="dev.junta.firmamobile.action.E2E_CONTROL"
readonly RISH_BIN="${RISH_BIN:-/data/data/com.termux/files/usr/bin/rish}"
readonly STATE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/workspace-47/e2e-control/$PACKAGE_NAME"
readonly CERT_STAGE_DIR="/storage/emulated/0/Download/.w47-e2e-control"
readonly CERT_STAGE_RECORD="$STATE_DIR/certificate-stage"
readonly SECRET_RELATIVE_DIR="cache/e2e-control/secrets"
readonly INTENT_FLAGS=0x41 # FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_PERSISTABLE_URI_PERMISSION
readonly TIMEOUT_SECONDS="${E2E_CONTROL_TIMEOUT_SECONDS:-30}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/android-e2e-control.sh launch
  scripts/android-e2e-control.sh state RUN_ID
  scripts/android-e2e-control.sh cert-select RUN_ID /local/path/to/certificate.p12
  scripts/android-e2e-control.sh cert-unlock RUN_ID --password-file /path/to/mode-600-file
  scripts/android-e2e-control.sh cert-unlock RUN_ID --password-stdin
  scripts/android-e2e-control.sh cert-lock RUN_ID
  scripts/android-e2e-control.sh cert-forget RUN_ID
  scripts/android-e2e-control.sh portal-open RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-inspect RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-close RUN_ID portal|profile ID
  scripts/android-e2e-control.sh client-auth-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh client-auth-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-cert-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-cert-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-dismiss RUN_ID portal|profile ID

QA/debug only. Commands use a lifecycle-bound android.permission.DUMP receiver.
The password is never accepted as a command-line argument or Intent extra. CERT_UNLOCK sends only an
opaque one-shot handle after staging the secret through run-as into app-private cache. Raw certificate
bytes are never placed in Intent extras; CERT_SELECT uses a granted content:// data URI.

SIGN_CONFIRM performs the application's real confirmation path. Use it only when current execution policy
permits it and explicit authorization exists for the exact portal/action. It never authorizes final filing/payment.
USAGE
}

fail() { echo "$*" >&2; exit 64; }
valid_run_id() { [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; }
valid_target_id() { [[ "$1" =~ ^[a-z0-9][a-z0-9-]{0,95}$ ]]; }
valid_target_kind() { [[ "$1" == portal || "$1" == profile ]]; }

run_rish() {
  timeout "$TIMEOUT_SECONDS" "$RISH_BIN" -c "$1" 2>&1
}

ensure_tools() {
  [[ -x "$RISH_BIN" ]] || { echo "rish is unavailable" >&2; exit 69; }
  command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }
  command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 69; }
  [[ "$PACKAGE_NAME" =~ ^[a-zA-Z0-9._]+$ ]] || { echo "Invalid package name" >&2; exit 64; }
  [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || { echo "Invalid timeout" >&2; exit 64; }
  ((TIMEOUT_SECONDS >= 3 && TIMEOUT_SECONDS <= 180)) || { echo "Timeout must be 3..180" >&2; exit 64; }
  local identity
  identity="$(run_rish 'id')" || { echo "Shizuku/rish shell is unavailable" >&2; exit 69; }
  grep -q 'uid=2000(shell)' <<<"$identity" || { echo "Unexpected Shizuku identity" >&2; exit 69; }
  run_rish "pm path $PACKAGE_NAME" | grep -q '^package:' || { echo "Package is not installed" >&2; exit 69; }
  run_rish "run-as $PACKAGE_NAME id" | grep -q 'u0_a' || {
    echo "Installed package is not a debuggable QA build" >&2
    exit 69
  }
}

ensure_foreground() {
  local resumed output
  resumed="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
  if [[ "$resumed" != *"$MAIN_COMPONENT"* ]]; then
    output="$(run_rish "am start -W --user 0 -f 0x24000000 -n $MAIN_COMPONENT")" || true
    grep -q '^Status: ok' <<<"$output" || { echo "MainActivity did not start successfully" >&2; exit 70; }
  fi
  resumed="$(run_rish 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity|ResumedActivity"')" || true
  [[ "$resumed" == *"$MAIN_COMPONENT"* ]] || { echo "MainActivity is not foreground" >&2; exit 70; }
}

extract_json() {
  local raw="$1" line
  line="$(sed -n 's/^Broadcast completed: result=[^,]*, data="\(.*\)"$/\1/p' <<<"$raw" | tail -n 1)"
  [[ -n "$line" ]] || return 1
  line="${line//\\\"/\"}"
  jq -e 'select(type == "object" and .schemaVersion == 3)' <<<"$line"
}

broadcast() {
  local run_id="$1" command="$2" target_kind="${3:-}" target_id="${4:-}" data_uri="${5:-}" secret_handle="${6:-}"
  local cmd output result
  valid_run_id "$run_id" || fail "Invalid run ID"
  [[ "$command" =~ ^[A-Z_]+$ ]] || fail "Invalid command"
  cmd="am broadcast --user 0 -a $ACTION -p $PACKAGE_NAME --es runId $run_id --es command $command"
  if [[ -n "$target_kind" ]]; then
    valid_target_kind "$target_kind" || fail "Target kind must be portal or profile"
    valid_target_id "$target_id" || fail "Invalid portal/profile ID"
    cmd+=" --es ${target_kind}Id $target_id"
  fi
  if [[ -n "$secret_handle" ]]; then
    [[ "$secret_handle" =~ ^[a-f0-9]{32}$ ]] || fail "Invalid secret handle"
    cmd+=" --es secretHandle $secret_handle"
  fi
  if [[ -n "$data_uri" ]]; then
    [[ "$data_uri" =~ ^content://com\.android\.externalstorage\.documents/document/[A-Za-z0-9%._-]+$ ]] || fail "Invalid certificate data URI"
    cmd+=" -f $INTENT_FLAGS -d $data_uri"
  fi
  output="$(run_rish "$cmd")" || { echo "E2E control broadcast failed" >&2; return 70; }
  result="$(extract_json "$output")" || { echo "E2E control returned no schema-v3 JSON" >&2; return 70; }
  printf '%s\n' "$result"
  jq -e '.success == true' >/dev/null <<<"$result"
}

random_handle() { openssl rand -hex 16; }

certificate_stage_uri() {
  local handle="$1"
  printf 'content://com.android.externalstorage.documents/document/primary%%3ADownload%%2F.w47-e2e-control%%2F%s.p12' "$handle"
}

stage_certificate() {
  local source_file="$1" handle="$2" stage_file uri old_stage=""
  [[ -f "$source_file" && -r "$source_file" ]] || { echo "Certificate file is not readable" >&2; return 66; }
  local size
  size="$(stat -c '%s' "$source_file" 2>/dev/null)" || { echo "Cannot inspect certificate file" >&2; return 66; }
  [[ "$size" =~ ^[0-9]+$ ]] && ((size > 0 && size <= 10 * 1024 * 1024)) || {
    echo "Certificate file size is outside the supported bound" >&2
    return 66
  }
  mkdir -p "$STATE_DIR" "$CERT_STAGE_DIR"
  chmod 700 "$STATE_DIR" 2>/dev/null || true
  stage_file="$CERT_STAGE_DIR/$handle.p12"
  cp "$source_file" "$stage_file" 2>/dev/null || { echo "Certificate staging failed" >&2; return 74; }
  [[ "$(stat -c '%s' "$stage_file" 2>/dev/null)" == "$size" ]] || {
    rm -f "$stage_file"
    echo "Certificate staging integrity check failed" >&2
    return 74
  }
  uri="$(certificate_stage_uri "$handle")"
  if [[ -f "$CERT_STAGE_RECORD" ]]; then old_stage="$(cat "$CERT_STAGE_RECORD" 2>/dev/null || true)"; fi
  if result="$(broadcast "$RUN_ID" CERT_SELECT "" "" "$uri")"; then
    printf '%s\n' "$stage_file" >"$CERT_STAGE_RECORD"
    chmod 600 "$CERT_STAGE_RECORD"
    if [[ -n "$old_stage" && "$old_stage" != "$stage_file" && "$old_stage" == "$CERT_STAGE_DIR/"*.p12 ]]; then
      rm -f "$old_stage"
    fi
    printf '%s\n' "$result"
  else
    rm -f "$stage_file"
    return 70
  fi
}

stage_password_and_unlock() {
  local mode="$1" input_file="${2:-}" handle command_output result
  handle="$(random_handle)"
  [[ "$handle" =~ ^[a-f0-9]{32}$ ]] || { echo "Secret handle generation failed" >&2; return 70; }
  if [[ "$mode" == file ]]; then
    [[ -f "$input_file" && -r "$input_file" ]] || { echo "Password file is not readable" >&2; return 66; }
    local size mode_bits
    size="$(stat -c '%s' "$input_file" 2>/dev/null)" || return 66
    ((size > 0 && size <= 8192)) || { echo "Password file size is outside the supported bound" >&2; return 66; }
    mode_bits="$(stat -c '%a' "$input_file" 2>/dev/null)" || return 66
    (( (8#$mode_bits & 077) == 0 )) || { echo "Password file must not be group/world accessible" >&2; return 66; }
    timeout "$TIMEOUT_SECONDS" "$RISH_BIN" -c \
      "run-as $PACKAGE_NAME sh -c 'umask 077; mkdir -p $SECRET_RELATIVE_DIR; cat > $SECRET_RELATIVE_DIR/$handle; chmod 600 $SECRET_RELATIVE_DIR/$handle'" \
      <"$input_file" >/dev/null 2>&1 || { echo "Password staging failed" >&2; return 70; }
  else
    [[ ! -t 0 ]] || { echo "--password-stdin requires redirected/non-terminal stdin" >&2; return 64; }
    timeout "$TIMEOUT_SECONDS" "$RISH_BIN" -c \
      "run-as $PACKAGE_NAME sh -c 'umask 077; mkdir -p $SECRET_RELATIVE_DIR; cat > $SECRET_RELATIVE_DIR/$handle; chmod 600 $SECRET_RELATIVE_DIR/$handle'" \
      >/dev/null 2>&1 || { echo "Password staging failed" >&2; return 70; }
  fi
  if result="$(broadcast "$RUN_ID" CERT_UNLOCK "" "" "" "$handle")"; then
    printf '%s\n' "$result"
  else
    run_rish "run-as $PACKAGE_NAME rm -f $SECRET_RELATIVE_DIR/$handle" >/dev/null 2>&1 || true
    return 70
  fi
}

cleanup_certificate_stage() {
  local stage=""
  if [[ -f "$CERT_STAGE_RECORD" ]]; then stage="$(cat "$CERT_STAGE_RECORD" 2>/dev/null || true)"; fi
  if [[ -n "$stage" && "$stage" == "$CERT_STAGE_DIR/"*.p12 ]]; then rm -f "$stage"; fi
  rm -f "$CERT_STAGE_RECORD"
}

ensure_tools
(($# >= 1)) || { usage; exit 64; }
verb="$1"; shift

case "$verb" in
  launch)
    (($# == 0)) || fail "launch takes no arguments"
    ensure_foreground
    printf '{"schemaVersion":3,"result":"APP_FOREGROUND","success":true}\n'
    ;;
  state|cert-lock|cert-forget)
    (($# == 1)) || fail "$verb requires RUN_ID"
    RUN_ID="$1"; ensure_foreground
    case "$verb" in
      state) broadcast "$RUN_ID" STATE ;;
      cert-lock) broadcast "$RUN_ID" CERT_LOCK ;;
      cert-forget)
        result="$(broadcast "$RUN_ID" CERT_FORGET)" || exit $?
        cleanup_certificate_stage
        printf '%s\n' "$result"
        ;;
    esac
    ;;
  cert-select)
    (($# == 2)) || fail "cert-select requires RUN_ID and certificate path"
    RUN_ID="$1"; cert_file="$2"; valid_run_id "$RUN_ID" || fail "Invalid run ID"
    ensure_foreground
    stage_certificate "$cert_file" "$(random_handle)"
    ;;
  cert-unlock)
    (($# >= 2)) || fail "cert-unlock requires RUN_ID and password source"
    RUN_ID="$1"; shift; valid_run_id "$RUN_ID" || fail "Invalid run ID"
    ensure_foreground
    case "$1" in
      --password-file)
        (($# == 2)) || fail "--password-file requires exactly one file"
        stage_password_and_unlock file "$2"
        ;;
      --password-stdin)
        (($# == 1)) || fail "--password-stdin takes no path"
        stage_password_and_unlock stdin
        ;;
      *) fail "Use --password-file or --password-stdin; raw password arguments are forbidden" ;;
    esac
    ;;
  portal-open|portal-inspect|portal-close|client-auth-confirm|client-auth-cancel|portal-cert-confirm|portal-cert-cancel|sign-confirm|sign-cancel|sign-dismiss)
    (($# == 3)) || fail "$verb requires RUN_ID portal|profile ID"
    RUN_ID="$1"; kind="$2"; id="$3"; ensure_foreground
    case "$verb" in
      portal-open) command=PORTAL_OPEN ;;
      portal-inspect) command=PORTAL_INSPECT ;;
      portal-close) command=PORTAL_CLOSE ;;
      client-auth-confirm) command=CLIENT_AUTH_CONFIRM ;;
      client-auth-cancel) command=CLIENT_AUTH_CANCEL ;;
      portal-cert-confirm) command=PORTAL_CERT_CONFIRM ;;
      portal-cert-cancel) command=PORTAL_CERT_CANCEL ;;
      sign-confirm) command=SIGN_CONFIRM ;;
      sign-cancel) command=SIGN_CANCEL ;;
      sign-dismiss) command=SIGN_DISMISS ;;
    esac
    broadcast "$RUN_ID" "$command" "$kind" "$id"
    ;;
  -h|--help|help) usage ;;
  *) usage; fail "Unknown command" ;;
esac
