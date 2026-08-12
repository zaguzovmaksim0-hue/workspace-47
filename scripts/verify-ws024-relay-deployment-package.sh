#!/usr/bin/env bash
set -euo pipefail

readonly root_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
readonly unit_file="${WS024_RELAY_UNIT_FILE:-$root_dir/ws024-relay/deploy/ws024-relay.service}"
readonly tls_verifier="$root_dir/ws024-relay/deploy/verify-outer-tls.sh"
readonly tls_test="$root_dir/ws024-relay/deploy/verify-outer-tls_test.sh"
readonly unit_test="$root_dir/scripts/verify-ws024-relay-deployment-package_test.sh"

fail() {
    printf '%s\n' "deployment-package verification failed: $1" >&2
    exit 1
}

trim_systemd_whitespace() {
    local value="$1"
    value="${value#"${value%%[!$' \t\r\n']*}"}"
    value="${value%"${value##*[!$' \t\r\n']}"}"
    printf '%s' "$value"
}

verify_service_directives() {
    local file="$1" line section="" key value
    local -A expected=(
        [Type]='simple'
        [User]='ws024-relay'
        [Group]='ws024-relay'
        [ExecStart]='/opt/ws024-relay/ws024-relay -listen [::]:443 -tls-cert /etc/ws024-relay/tls-cert.pem -tls-key /etc/ws024-relay/tls-key.pem -qa-credentials /etc/ws024-relay/qa-credentials.json'
        [Restart]='on-failure'
        [RestartSec]='5s'
        [TimeoutStopSec]='30s'
        [UMask]='0077'
        [StandardOutput]='journal'
        [StandardError]='journal'
        [NoNewPrivileges]='true'
        [CapabilityBoundingSet]='CAP_NET_BIND_SERVICE'
        [AmbientCapabilities]='CAP_NET_BIND_SERVICE'
        [PrivateTmp]='true'
        [PrivateDevices]='true'
        [ProtectSystem]='strict'
        [ProtectHome]='true'
        [ProtectControlGroups]='true'
        [ProtectKernelModules]='true'
        [ProtectKernelTunables]='true'
        [ProtectKernelLogs]='true'
        [ProtectClock]='true'
        [ProtectHostname]='true'
        [RestrictAddressFamilies]='AF_INET AF_INET6 AF_UNIX'
        [RestrictNamespaces]='true'
        [RestrictRealtime]='true'
        [RestrictSUIDSGID]='true'
        [LockPersonality]='true'
        [MemoryDenyWriteExecute]='true'
        [SystemCallArchitectures]='native'
    )
    local -A seen=()

    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" =~ ^[[:space:]]*\[([A-Za-z]+)\][[:space:]]*$ ]]; then
            section="${BASH_REMATCH[1]}"
            continue
        fi
        [[ "$section" == 'Service' ]] || continue
        [[ "$line" =~ ^[[:space:]]*($|[#\;]) ]] && continue
        [[ "$line" =~ ^[[:space:]]*([A-Za-z][A-Za-z0-9]*)[[:space:]]*=[[:space:]]*(.*)$ ]] || \
            fail 'malformed service directive'
        key="${BASH_REMATCH[1]}"
        value="$(trim_systemd_whitespace "${BASH_REMATCH[2]}")"
        case "$key" in
            Environment|EnvironmentFile|PassEnvironment|UnsetEnvironment|ExecStartPre|ExecStartPost|ExecReload)
                fail 'unit contains prohibited service directive'
                ;;
        esac
        [[ -n "${expected[$key]+present}" ]] || fail 'unexpected service directive'
        [[ -z "${seen[$key]+present}" ]] || fail 'duplicate service directive'
        [[ "$value" == "${expected[$key]}" ]] || fail 'service directive differs from exact safe value'
        seen["$key"]=1
    done <"$file"

    for key in "${!expected[@]}"; do
        [[ -n "${seen[$key]+present}" ]] || fail 'required service directive missing'
    done
}

[[ -f "$unit_file" ]] || fail "required unit is missing"
for file in "$tls_verifier" "$tls_test" "$unit_test" "$0"; do
    [[ -f "$file" && -x "$file" ]] || fail "required executable is missing"
    bash -n "$file" || fail "shell syntax"
done

verify_service_directives "$unit_file"
if grep -Eqi '(nginx|apache|caddy|haproxy|traefik|reverse.?proxy|proxy_pass|cloudflare|cdn|health|admin|upstream[[:space:]]*=)' "$unit_file"; then
    fail 'unit contains prohibited proxy, health, admin, or arbitrary upstream content'
fi
if grep -Eqi '(keylog|trace|debug|msg|dump)' "$tls_verifier"; then
    fail 'TLS verifier contains prohibited diagnostic or protocol behavior'
fi
grep -Fq -- '-verify_return_error' "$tls_verifier" || fail 'normal chain verification is absent'
grep -Fq -- '-verify_hostname' "$tls_verifier" || fail 'hostname verification is absent'
grep -Fq -- '-alpn http/1.1' "$tls_verifier" || fail 'ALPN verification is absent'

"$tls_test"
printf '%s\n' 'PASS ws024 relay deployment package'
