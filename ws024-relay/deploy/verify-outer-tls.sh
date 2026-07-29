#!/usr/bin/env bash
# Verify only the public TLS listener. This command never sends CONNECT data.
set -euo pipefail

readonly exit_invalid_input=64
readonly exit_missing_dependency=69
readonly exit_tls_verification=70
readonly exit_pin_mismatch=71

die() {
    local status="$1"
    local category="$2"
    printf '%s\n' "verify-outer-tls: $category" >&2
    exit "$status"
}

host=""
port=""
declare -a pins=()

while (($#)); do
    case "$1" in
        --host)
            (($# >= 2)) || die "$exit_invalid_input" 'invalid input'
            [[ -z "$host" ]] || die "$exit_invalid_input" 'invalid input'
            host="$2"
            shift 2
            ;;
        --port)
            (($# >= 2)) || die "$exit_invalid_input" 'invalid input'
            [[ -z "$port" ]] || die "$exit_invalid_input" 'invalid input'
            port="$2"
            shift 2
            ;;
        --pin)
            (($# >= 2)) || die "$exit_invalid_input" 'invalid input'
            pins+=("$2")
            shift 2
            ;;
        *)
            die "$exit_invalid_input" 'invalid input'
            ;;
    esac
done

valid_host() {
    local value="$1"
    local label
    local -a labels
    [[ -n "$value" && ${#value} -le 253 && "$value" == "${value,,}" ]] || return 1
    [[ "$value" != *.* ]] && [[ "$value" == 'localhost' ]] && return 0
    [[ "$value" != .* && "$value" != *. && "$value" != *..* ]] || return 1
    IFS='.' read -r -a labels <<<"$value"
    ((${#labels[@]} >= 2)) || return 1
    for label in "${labels[@]}"; do
        [[ "$label" =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] || return 1
    done
}

valid_port() {
    [[ "$1" =~ ^[1-9][0-9]{0,4}$ ]] && ((10#$1 <= 65535))
}

valid_pin() {
    local value="$1"
    local encoded decoded reencoded
    [[ "$value" == sha256/* ]] || return 1
    encoded="${value#sha256/}"
    [[ ${#encoded} -eq 44 && "$encoded" =~ ^[A-Za-z0-9+/]{43}=$ ]] || return 1
    decoded="$temp_dir/pin-check.bin"
    printf '%s' "$encoded" | openssl base64 -d -A >"$decoded" 2>/dev/null || return 1
    [[ "$(wc -c <"$decoded")" -eq 32 ]] || return 1
    reencoded="$(openssl base64 -A <"$decoded" 2>/dev/null)"
    [[ "$reencoded" == "$encoded" ]]
}

valid_host "$host" && valid_port "$port" && ((${#pins[@]} >= 2)) || die "$exit_invalid_input" 'invalid input'

for dependency in awk chmod grep mktemp openssl rm timeout wc; do
    command -v "$dependency" >/dev/null 2>&1 || die "$exit_missing_dependency" 'missing dependency'
done

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/ws024-outer-tls.XXXXXX" 2>/dev/null)" || die "$exit_tls_verification" 'TLS verification failed'
chmod 700 "$temp_dir" 2>/dev/null || {
    rm -rf -- "$temp_dir" 2>/dev/null || true
    die "$exit_tls_verification" 'TLS verification failed'
}
cleanup() {
    rm -rf -- "$temp_dir" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM

declare -A seen_pins=()
for pin in "${pins[@]}"; do
    valid_pin "$pin" || die "$exit_invalid_input" 'invalid input'
    [[ -z "${seen_pins[$pin]+present}" ]] || die "$exit_invalid_input" 'invalid input'
    seen_pins["$pin"]=1
done

session="$temp_dir/session.txt"
diagnostic="$temp_dir/openssl.err"
if ! timeout 15s openssl s_client \
    -connect "$host:$port" \
    -servername "$host" \
    -alpn http/1.1 \
    -verify_hostname "$host" \
    -verify_return_error \
    -showcerts </dev/null >"$session" 2>"$diagnostic"; then
    die "$exit_tls_verification" 'TLS verification failed'
fi

grep -Eq '^[[:space:]]*ALPN protocol: http/1\.1[[:space:]]*$' "$session" || \
    die "$exit_tls_verification" 'TLS verification failed'

if ! awk -v output_dir="$temp_dir" '
    /-----BEGIN CERTIFICATE-----/ {
        if (inside) exit 1
        inside = 1
        count++
        file = output_dir "/presented-" count ".pem"
    }
    inside { print > file }
    /-----END CERTIFICATE-----/ {
        if (!inside) exit 1
        close(file)
        inside = 0
    }
    END { if (inside || count == 0) exit 1 }
' "$session" 2>/dev/null; then
    die "$exit_tls_verification" 'TLS verification failed'
fi

shopt -s nullglob
certificates=("$temp_dir"/presented-*.pem)
shopt -u nullglob
((${#certificates[@]} > 0)) || die "$exit_tls_verification" 'TLS verification failed'

matching_pins=0
for certificate in "${certificates[@]}"; do
    encoded_pin="$(openssl x509 -in "$certificate" -pubkey -noout 2>/dev/null |
        openssl pkey -pubin -outform DER 2>/dev/null |
        openssl dgst -sha256 -binary 2>/dev/null |
        openssl base64 -A 2>/dev/null)" || die "$exit_tls_verification" 'TLS verification failed'
    [[ ${#encoded_pin} -eq 44 ]] || die "$exit_tls_verification" 'TLS verification failed'
    public_pin="sha256/$encoded_pin"
    if [[ -n "${seen_pins[$public_pin]+present}" ]]; then
        ((matching_pins += 1))
    fi
done

((matching_pins > 0)) || die "$exit_pin_mismatch" 'pin mismatch'
printf '{"status":"ok","presented_certificates":%d,"matching_pins":%d}\n' \
    "${#certificates[@]}" "$matching_pins"
