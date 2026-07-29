#!/usr/bin/env bash
# Integration checks for verify-outer-tls.sh. All certificates and keys are
# synthetic test fixtures created in a private temporary directory.
set -euo pipefail

readonly script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
readonly verifier="$script_dir/verify-outer-tls.sh"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/ws024-outer-tls-test.XXXXXX")"
server_pid=""
port=""

cleanup() {
    if [[ -n "$server_pid" ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -rf -- "$tmp_dir"
}
trap cleanup EXIT HUP INT TERM
chmod 700 "$tmp_dir"

require() {
    command -v "$1" >/dev/null 2>&1 || {
        printf '%s\n' "missing test dependency: $1" >&2
        exit 69
    }
}

for dependency in openssl timeout; do
    require "$dependency"
done

make_leaf() {
    local name="$1"
    local san="$2"
    openssl req -new -newkey rsa:2048 -nodes \
        -keyout "$tmp_dir/$name-key.pem" \
        -out "$tmp_dir/$name.csr" \
        -subj "/CN=$san" >/dev/null 2>&1
    openssl x509 -req -sha256 -days 1 \
        -in "$tmp_dir/$name.csr" \
        -CA "$tmp_dir/ca-cert.pem" -CAkey "$tmp_dir/ca-key.pem" -CAcreateserial \
        -out "$tmp_dir/$name-cert.pem" \
        -extfile <(printf 'subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\n' "$san") \
        >/dev/null 2>&1
}

spki_pin() {
    openssl x509 -in "$1" -pubkey -noout 2>/dev/null |
        openssl pkey -pubin -outform DER 2>/dev/null |
        openssl dgst -sha256 -binary 2>/dev/null |
        openssl base64 -A 2>/dev/null |
        sed 's/^/sha256\//'
}

stop_server() {
    if [[ -n "$server_pid" ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
        server_pid=""
    fi
}

start_server() {
    local cert="$1"
    local key="$2"
    local alpn="$3"
    local attempt
    for attempt in $(seq 1 20); do
        port=$((20000 + (RANDOM % 30000)))
        openssl s_server -accept "$port" -quiet \
            -cert "$cert" -key "$key" -cert_chain "$tmp_dir/ca-cert.pem" \
            -alpn "$alpn" >"$tmp_dir/server.log" 2>&1 &
        server_pid=$!
        sleep 0.1
        if kill -0 "$server_pid" 2>/dev/null; then
            return 0
        fi
        wait "$server_pid" 2>/dev/null || true
        server_pid=""
    done
    printf '%s\n' 'unable to start synthetic TLS server' >&2
    exit 70
}

expect_exit() {
    local expected="$1"
    shift
    set +e
    "$@" >"$tmp_dir/stdout" 2>"$tmp_dir/stderr"
    local actual=$?
    set -e
    if [[ "$actual" -ne "$expected" ]]; then
        printf '%s\n' "unexpected exit: got $actual, want $expected" >&2
        exit 1
    fi
}

assert_success_output() {
    [[ ! -s "$tmp_dir/stderr" ]] || {
        printf '%s\n' 'successful verifier invocation wrote stderr' >&2
        exit 1
    }
    [[ "$(wc -l <"$tmp_dir/stdout")" -eq 1 ]] || {
        printf '%s\n' 'successful verifier stdout was not one line' >&2
        exit 1
    }
    [[ "$(<"$tmp_dir/stdout")" == '{"status":"ok","presented_certificates":2,"matching_pins":1}' ]] || {
        printf '%s\n' 'successful verifier stdout was not the sanitized expected JSON' >&2
        exit 1
    }
    if grep -Eq 'localhost|sha256/|CERTIFICATE|PRIVATE KEY' "$tmp_dir/stdout"; then
        printf '%s\n' 'successful verifier stdout exposed sensitive or identifying material' >&2
        exit 1
    fi
}

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
    -keyout "$tmp_dir/ca-key.pem" -out "$tmp_dir/ca-cert.pem" \
    -subj '/CN=ws024 synthetic test CA' >/dev/null 2>&1
make_leaf current localhost
make_leaf backup backup.invalid

readonly current_pin="$(spki_pin "$tmp_dir/current-cert.pem")"
readonly backup_pin="$(spki_pin "$tmp_dir/backup-cert.pem")"
readonly zero_pin='sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='

start_server "$tmp_dir/current-cert.pem" "$tmp_dir/current-key.pem" http/1.1
SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$current_pin" --pin "$backup_pin" >"$tmp_dir/stdout" 2>"$tmp_dir/stderr"
assert_success_output

expect_exit 64 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin 'sha256/not-base64' --pin "$backup_pin"
expect_exit 64 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$current_pin"
expect_exit 64 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$current_pin" --pin "$current_pin"
expect_exit 71 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$backup_pin" --pin "$zero_pin"

stop_server
start_server "$tmp_dir/backup-cert.pem" "$tmp_dir/backup-key.pem" http/1.1
expect_exit 70 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$current_pin" --pin "$backup_pin"

stop_server
start_server "$tmp_dir/current-cert.pem" "$tmp_dir/current-key.pem" h2
expect_exit 70 env SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" --host localhost --port "$port" \
    --pin "$current_pin" --pin "$backup_pin"
