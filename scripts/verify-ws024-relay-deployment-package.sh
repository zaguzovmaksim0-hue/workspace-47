#!/usr/bin/env bash
set -euo pipefail

readonly root_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
readonly unit_file="$root_dir/ws024-relay/deploy/ws024-relay.service"
readonly tls_verifier="$root_dir/ws024-relay/deploy/verify-outer-tls.sh"
readonly tls_test="$root_dir/ws024-relay/deploy/verify-outer-tls_test.sh"

fail() {
    printf '%s\n' "deployment-package verification failed: $1" >&2
    exit 1
}

require_line() {
    local line="$1"
    local file="$2"
    grep -Fqx -- "$line" "$file" || fail "required unit setting missing"
}

[[ -f "$unit_file" ]] || fail "required unit is missing"
for file in "$tls_verifier" "$tls_test" "$0"; do
    [[ -f "$file" && -x "$file" ]] || fail "required executable is missing"
    bash -n "$file" || fail "shell syntax"
done

require_line 'User=ws024-relay' "$unit_file"
require_line 'Group=ws024-relay' "$unit_file"
require_line 'ExecStart=/opt/ws024-relay/ws024-relay -listen [::]:443 -tls-cert /etc/ws024-relay/tls-cert.pem -tls-key /etc/ws024-relay/tls-key.pem -qa-credentials /etc/ws024-relay/qa-credentials.json' "$unit_file"
require_line 'UMask=0077' "$unit_file"
require_line 'Restart=on-failure' "$unit_file"
require_line 'TimeoutStopSec=30s' "$unit_file"
require_line 'StandardOutput=journal' "$unit_file"
require_line 'StandardError=journal' "$unit_file"
require_line 'NoNewPrivileges=true' "$unit_file"
require_line 'CapabilityBoundingSet=CAP_NET_BIND_SERVICE' "$unit_file"
require_line 'AmbientCapabilities=CAP_NET_BIND_SERVICE' "$unit_file"
require_line 'RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX' "$unit_file"

if grep -Eqi '^(Environment|EnvironmentFile|ExecStartPre|ExecStartPost)=' "$unit_file"; then
    fail 'unit contains environment or pre/post-start material'
fi
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
