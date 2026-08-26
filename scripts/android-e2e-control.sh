#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

readonly PACKAGE_NAME="${PACKAGE_NAME:-dev.junta.firmamobile}"
readonly MAIN_COMPONENT="$PACKAGE_NAME/dev.junta.firmamobile.MainActivity"
readonly ACTION="dev.junta.firmamobile.action.E2E_CONTROL"
readonly RISH_BIN="${RISH_BIN:-/data/data/com.termux/files/usr/bin/rish}"
readonly CERT_RELATIVE_DIR="no_backup/e2e-control/certificates"
readonly SECRET_RELATIVE_DIR="cache/e2e-control/secrets"
readonly TIMEOUT_SECONDS="${E2E_CONTROL_TIMEOUT_SECONDS:-30}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/android-e2e-control.sh launch
  scripts/android-e2e-control.sh app-stop
  scripts/android-e2e-control.sh state RUN_ID
  scripts/android-e2e-control.sh cert-select RUN_ID /local/path/to/certificate.p12
  scripts/android-e2e-control.sh cert-unlock RUN_ID --password-file /path/to/mode-600-file
  scripts/android-e2e-control.sh cert-unlock RUN_ID --password-stdin
  scripts/android-e2e-control.sh cert-unlock RUN_ID --password-clipboard
  scripts/android-e2e-control.sh cert-lock RUN_ID
  scripts/android-e2e-control.sh cert-forget RUN_ID
  scripts/android-e2e-control.sh portal-open RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-inspect RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-close RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-login RUN_ID portal|profile ID
  scripts/android-e2e-control.sh client-auth-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh client-auth-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-cert-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh portal-cert-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-confirm RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-cancel RUN_ID portal|profile ID
  scripts/android-e2e-control.sh sign-dismiss RUN_ID portal|profile ID

QA/debug only. Commands use a lifecycle-bound android.permission.DUMP receiver.
The password is never accepted as a command-line argument or Intent extra. CERT_UNLOCK sends only an
opaque one-shot handle after staging the secret through run-as into app-private cache. CERT_SELECT also
stages through run-as into app-private no-backup storage and sends only an opaque certificate handle.

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

stop_app() {
  local attempt pids
  for attempt in 1 2; do
    run_rish "am force-stop --user 0 $PACKAGE_NAME" >/dev/null 2>&1 || true
    sleep 1
    pids="$(run_rish "pidof $PACKAGE_NAME" 2>/dev/null || true)"
    [[ -z "$pids" ]] && {
      printf '{"schemaVersion":3,"result":"APP_STOPPED","success":true}\n'
      return 0
    }
  done
  echo "Application process is still running after force-stop" >&2
  return 70
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
  local run_id="$1" command="$2" target_kind="${3:-}" target_id="${4:-}" certificate_handle="${5:-}" secret_handle="${6:-}"
  local cmd output result
  valid_run_id "$run_id" || fail "Invalid run ID"
  [[ "$command" =~ ^[A-Z_]+$ ]] || fail "Invalid command"
  cmd="am broadcast --user 0 -a $ACTION -p $PACKAGE_NAME --es runId $run_id --es command $command"
  if [[ -n "$target_kind" ]]; then
    valid_target_kind "$target_kind" || fail "Target kind must be portal or profile"
    valid_target_id "$target_id" || fail "Invalid portal/profile ID"
    cmd+=" --es ${target_kind}Id $target_id"
  fi
  if [[ -n "$certificate_handle" ]]; then
    [[ "$certificate_handle" =~ ^[a-f0-9]{32}$ ]] || fail "Invalid certificate handle"
    cmd+=" --es certificateHandle $certificate_handle"
  fi
  if [[ -n "$secret_handle" ]]; then
    [[ "$secret_handle" =~ ^[a-f0-9]{32}$ ]] || fail "Invalid secret handle"
    cmd+=" --es secretHandle $secret_handle"
  fi
  local transport_rc=0
  if output="$(run_rish "$cmd")"; then
    transport_rc=0
  else
    transport_rc=$?
  fi
  if result="$(extract_json "$output")"; then
    printf '%s\n' "$result"
    jq -e '.success == true' >/dev/null <<<"$result"
    return $?
  fi
  if ((transport_rc != 0)); then
    echo "E2E control broadcast failed" >&2
  else
    echo "E2E control returned no schema-v3 JSON" >&2
  fi
  return 70
}

random_handle() { openssl rand -hex 16; }

stage_certificate() {
  local source_file="$1" handle="$2" size staged_size result
  [[ -f "$source_file" && -r "$source_file" ]] || { echo "Certificate file is not readable" >&2; return 66; }
  size="$(stat -c '%s' "$source_file" 2>/dev/null)" || { echo "Cannot inspect certificate file" >&2; return 66; }
  [[ "$size" =~ ^[0-9]+$ ]] && ((size > 0 && size <= 10 * 1024 * 1024)) || {
    echo "Certificate file size is outside the supported bound" >&2
    return 66
  }
  timeout "$TIMEOUT_SECONDS" "$RISH_BIN" -c \
    "run-as $PACKAGE_NAME sh -c 'umask 077; mkdir -p $CERT_RELATIVE_DIR; cat > $CERT_RELATIVE_DIR/$handle.p12; chmod 600 $CERT_RELATIVE_DIR/$handle.p12'" \
    <"$source_file" >/dev/null 2>&1 || { echo "Certificate staging failed" >&2; return 70; }
  staged_size="$(run_rish "run-as $PACKAGE_NAME stat -c %s $CERT_RELATIVE_DIR/$handle.p12" 2>/dev/null || true)"
  [[ "$staged_size" == "$size" ]] || {
    run_rish "run-as $PACKAGE_NAME rm -f $CERT_RELATIVE_DIR/$handle.p12" >/dev/null 2>&1 || true
    echo "Certificate staging integrity check failed" >&2
    return 70
  }
  if result="$(broadcast "$RUN_ID" CERT_SELECT "" "" "$handle")"; then
    printf '%s\n' "$result"
  else
    run_rish "run-as $PACKAGE_NAME rm -f $CERT_RELATIVE_DIR/$handle.p12" >/dev/null 2>&1 || true
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
  elif [[ "$mode" == clipboard ]]; then
    command -v termux-clipboard-get >/dev/null || { echo "termux-clipboard-get is unavailable" >&2; return 69; }
    termux-clipboard-get | timeout "$TIMEOUT_SECONDS" "$RISH_BIN" -c \
      "run-as $PACKAGE_NAME sh -c 'umask 077; mkdir -p $SECRET_RELATIVE_DIR; cat > $SECRET_RELATIVE_DIR/$handle; chmod 600 $SECRET_RELATIVE_DIR/$handle'" \
      >/dev/null 2>&1 || { echo "Password clipboard staging failed" >&2; return 70; }
    if command -v termux-clipboard-set >/dev/null; then
      printf '' | termux-clipboard-set >/dev/null 2>&1 || true
    fi
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

ensure_tools
(($# >= 1)) || { usage; exit 64; }
verb="$1"; shift

case "$verb" in
  launch)
    (($# == 0)) || fail "launch takes no arguments"
    ensure_foreground
    printf '{"schemaVersion":3,"result":"APP_FOREGROUND","success":true}\n'
    ;;
  app-stop)
    (($# == 0)) || fail "app-stop takes no arguments"
    stop_app
    ;;
  state|cert-lock|cert-forget)
    (($# == 1)) || fail "$verb requires RUN_ID"
    RUN_ID="$1"; ensure_foreground
    case "$verb" in
      state) broadcast "$RUN_ID" STATE ;;
      cert-lock) broadcast "$RUN_ID" CERT_LOCK ;;
      cert-forget) broadcast "$RUN_ID" CERT_FORGET ;;
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
      --password-clipboard)
        (($# == 1)) || fail "--password-clipboard takes no path"
        stage_password_and_unlock clipboard
        ;;
      *) fail "Use --password-file, --password-stdin, or --password-clipboard; raw password arguments are forbidden" ;;
    esac
    ;;
  portal-open|portal-inspect|portal-close|portal-login|client-auth-confirm|client-auth-cancel|portal-cert-confirm|portal-cert-cancel|sign-confirm|sign-cancel|sign-dismiss)
    (($# == 3)) || fail "$verb requires RUN_ID portal|profile ID"
    RUN_ID="$1"; kind="$2"; id="$3"; ensure_foreground
    case "$verb" in
      portal-open) command=PORTAL_OPEN ;;
      portal-inspect) command=PORTAL_INSPECT ;;
      portal-close) command=PORTAL_CLOSE ;;
      portal-login) command=PORTAL_LOGIN ;;
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
