#!/usr/bin/env bash
# Verify only the public TLS listener. This command never sends CONNECT data.
set -euo pipefail

readonly exit_invalid_input=64
readonly exit_missing_dependency=69
readonly exit_tls_verification=70
readonly exit_pin_mismatch=71
# OpenSSL's untrusted stdout and stderr each have a hard 32,769-byte capture
# cap: 32,768 allowed bytes plus one sentinel byte used solely to detect
# overflow. Any sentinel is fail-closed as TLS verification failure.
readonly openssl_output_limit=32768

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

for dependency in awk chmod dd grep mkfifo mktemp openssl rm timeout wc; do
    command -v "$dependency" >/dev/null 2>&1 || die "$exit_missing_dependency" 'missing dependency'
done

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/ws024-outer-tls.XXXXXX" 2>/dev/null)" || die "$exit_tls_verification" 'TLS verification failed'
chmod 700 "$temp_dir" 2>/dev/null || {
    rm -rf -- "$temp_dir" 2>/dev/null || true
    die "$exit_tls_verification" 'TLS verification failed'
}
session_reader=""
diagnostic_reader=""
input_fd=""
cleanup() {
    local reader
    if [[ -n "$input_fd" ]]; then
        exec {input_fd}>&-
    fi
    for reader in "$session_reader" "$diagnostic_reader"; do
        if [[ -n "$reader" ]]; then
            kill "$reader" 2>/dev/null || true
            wait "$reader" 2>/dev/null || true
        fi
    done
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
session_fifo="$temp_dir/session.fifo"
diagnostic_fifo="$temp_dir/openssl.err.fifo"
input_fifo="$temp_dir/input.fifo"
mkfifo "$session_fifo" "$diagnostic_fifo" "$input_fifo" 2>/dev/null || \
    die "$exit_tls_verification" 'TLS verification failed'
exec {input_fd}<>"$input_fifo"

dd if="$session_fifo" of="$session" bs=1 \
    count="$((openssl_output_limit + 1))" status=none 2>/dev/null &
session_reader=$!
dd if="$diagnostic_fifo" of="$diagnostic" bs=1 \
    count="$((openssl_output_limit + 1))" status=none 2>/dev/null &
diagnostic_reader=$!

set +e
# Keep each status separate: a pipeline would hide OpenSSL/timeout failure.
timeout 15s openssl s_client \
    -connect "$host:$port" \
    -servername "$host" \
    -alpn http/1.1 \
    -verify_hostname "$host" \
    -verify_return_error \
    -showcerts <&"$input_fd" >"$session_fifo" 2>"$diagnostic_fifo"
openssl_status=$?
exec {input_fd}>&-
input_fd=""
wait "$session_reader"
session_reader_status=$?
wait "$diagnostic_reader"
diagnostic_reader_status=$?
set -e
session_reader=""
diagnostic_reader=""

if ((session_reader_status != 0 || diagnostic_reader_status != 0)); then
    die "$exit_tls_verification" 'TLS verification failed'
fi
if (( $(wc -c <"$session") > openssl_output_limit || \
      $(wc -c <"$diagnostic") > openssl_output_limit )); then
    die "$exit_tls_verification" 'TLS verification failed'
fi
if ((openssl_status != 0)); then
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
