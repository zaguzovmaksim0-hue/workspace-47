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

for dependency in openssl python3 timeout; do
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

start_oversize_server() {
    local attempt
    for attempt in $(seq 1 20); do
        port=$((20000 + (RANDOM % 30000)))
        # OpenSSL s_server only writes application data after client input. The
        # standard-library Python 3 endpoint is test-only and required above
        # (the test fails closed with 69 if unavailable); it completes TLS
        # handshake before emitting the synthetic body.
        python3 -c '
import socket
import ssl
import sys

port, cert, key, payload = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(cert, key)
context.set_alpn_protocols(["http/1.1"])
listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(("127.0.0.1", port))
listener.listen(1)
print("READY", flush=True)
try:
    connection, _ = listener.accept()
    with context.wrap_socket(connection, server_side=True) as tls:
        if tls.selected_alpn_protocol() != "http/1.1":
            raise RuntimeError("ALPN was not negotiated")
        print("HANDSHAKE_COMPLETE", flush=True)
        try:
            tls.sendall(open(payload, "rb").read())
        except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
            pass
finally:
    listener.close()
' "$port" "$tmp_dir/current-cert.pem" "$tmp_dir/current-key.pem" \
            "$tmp_dir/oversize-payload" >"$tmp_dir/server.log" 2>&1 &
        server_pid=$!
        sleep 0.1
        if kill -0 "$server_pid" 2>/dev/null && grep -Fqx 'READY' "$tmp_dir/server.log"; then
            return 0
        fi
        wait "$server_pid" 2>/dev/null || true
        server_pid=""
    done
    printf '%s\n' 'unable to start oversized synthetic TLS server' >&2
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

expect_bounded_tls_failure() {
    local started actual elapsed attempt handshake_ready=false
    local verifier_tmp="$tmp_dir/verifier-tmp" verifier_pid
    mkdir -p "$verifier_tmp"
    started=$SECONDS
    set +e
    env TMPDIR="$verifier_tmp" SSL_CERT_FILE="$tmp_dir/ca-cert.pem" "$verifier" \
        --host localhost --port "$port" --pin "$current_pin" --pin "$backup_pin" \
        >"$tmp_dir/stdout" 2>"$tmp_dir/stderr" &
    verifier_pid=$!
    set -e
    for attempt in $(seq 1 100); do
        if grep -Fqx 'HANDSHAKE_COMPLETE' "$tmp_dir/server.log"; then
            handshake_ready=true
            break
        fi
        sleep 0.05
    done
    "$handshake_ready" || {
        kill "$verifier_pid" 2>/dev/null || true
        wait "$verifier_pid" 2>/dev/null || true
        printf '%s\n' 'oversized TLS endpoint did not complete the ALPN handshake' >&2
        exit 1
    }
    set +e
    wait "$verifier_pid"
    actual=$?
    set -e
    elapsed=$((SECONDS - started))
    [[ "$actual" -eq 70 ]] || {
        printf '%s\n' "oversized TLS endpoint exit: got $actual, want 70" >&2
        exit 1
    }
    [[ ! -s "$tmp_dir/stdout" ]] || {
        printf '%s\n' 'oversized TLS endpoint wrote stdout' >&2
        exit 1
    }
    [[ "$(<"$tmp_dir/stderr")" == 'verify-outer-tls: TLS verification failed' ]] || {
        printf '%s\n' 'oversized TLS endpoint stderr was not generic' >&2
        exit 1
    }
    [[ "$(wc -c <"$tmp_dir/stderr")" -le 80 && "$elapsed" -lt 5 ]] || {
        printf '%s\n' 'oversized TLS endpoint did not fail within the bounded path' >&2
        exit 1
    }
    [[ -z "$(find "$verifier_tmp" -mindepth 1 -print -quit)" ]] || {
        printf '%s\n' 'oversized TLS endpoint left temporary output behind' >&2
        exit 1
    }
    if grep -Eq 'CERTIFICATE|PRIVATE KEY|sha256/|localhost' "$tmp_dir/stderr"; then
        printf '%s\n' 'oversized TLS endpoint leaked TLS material' >&2
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

stop_server
dd if=/dev/zero bs=1024 count=96 status=none | tr '\0' 'A' >"$tmp_dir/oversize-payload"
start_oversize_server
oversize_pid="$server_pid"
expect_bounded_tls_failure
stop_server
if kill -0 "$oversize_pid" 2>/dev/null; then
    printf '%s\n' 'oversized TLS server was not cleaned up' >&2
    exit 1
fi
