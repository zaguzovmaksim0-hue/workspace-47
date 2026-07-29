#!/usr/bin/env bash
# Negative fixtures for the deployment package systemd unit gate.
set -euo pipefail

readonly script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
readonly root_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
readonly verifier="$script_dir/verify-ws024-relay-deployment-package.sh"
readonly safe_unit="$root_dir/ws024-relay/deploy/ws024-relay.service"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/ws024-deployment-package-test.XXXXXX")"
cleanup() {
    rm -rf -- "$tmp_dir"
}
trap cleanup EXIT HUP INT TERM
chmod 700 "$tmp_dir"

[[ -x "$verifier" && -f "$safe_unit" ]] || {
    printf '%s\n' 'deployment package verifier or safe unit is missing' >&2
    exit 1
}

"$verifier" >/dev/null

fixture_number=0
expect_rejected() {
    local name="$1"
    shift
    local fixture="$tmp_dir/$fixture_number.service"
    fixture_number=$((fixture_number + 1))
    cp -- "$safe_unit" "$fixture"
    "$@" "$fixture"
    set +e
    WS024_RELAY_UNIT_FILE="$fixture" "$verifier" >/dev/null 2>"$tmp_dir/stderr"
    local status=$?
    set -e
    if [[ "$status" -eq 0 ]]; then
        printf '%s\n' "negative fixture accepted: $name" >&2
        exit 1
    fi
}

append_duplicate_capability() {
    sed -i '/^\[Install\]/i CapabilityBoundingSet = CAP_NET_BIND_SERVICE CAP_SYS_ADMIN' "$1"
}

append_spaced_environment() {
    sed -i '/^\[Install\]/i  Environment = SAFE_LOOKING=value' "$1"
}

append_spaced_exec_start_pre() {
    sed -i '/^\[Install\]/i  ExecStartPre = /bin/true' "$1"
}

append_second_exec_start() {
    sed -i '/^\[Install\]/i ExecStart = /bin/true' "$1"
}

replace_exec_start_with_shell_wrapper() {
    sed -i 's|^ExecStart=.*$|ExecStart = /bin/sh -c "/opt/ws024-relay/ws024-relay"|' "$1"
}

append_prohibited_service_content() {
    sed -i '/^\[Install\]/i X-Relay-Upstream = https://example.invalid' "$1"
    sed -i '/^\[Install\]/i X-Relay-Admin = enabled' "$1"
    sed -i '/^\[Install\]/i X-Relay-Health = enabled' "$1"
}

expect_rejected 'duplicate extra capability' append_duplicate_capability
expect_rejected 'spaced Environment' append_spaced_environment
expect_rejected 'spaced ExecStartPre' append_spaced_exec_start_pre
expect_rejected 'second ExecStart' append_second_exec_start
expect_rejected 'shell wrapper' replace_exec_start_with_shell_wrapper
expect_rejected 'upstream/admin/health content' append_prohibited_service_content
