#!/data/data/com.termux/files/usr/bin/python3
from __future__ import annotations

import base64
import json
import os
import re
import secrets
import selectors
import shutil
import signal
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Final, NoReturn

ROOT: Final = Path(__file__).resolve().parents[1]
RELAY_MODULE: Final = ROOT / "ws024-relay"
GRADLE_TEST: Final = "dev.junta.firmamobile.network.SecureTunnelExternalHarnessTest"
ENV_RELAY_PORT: Final = "JFM_TUNNEL_TEST_RELAY_PORT"
ENV_OUTER_CA: Final = "JFM_TUNNEL_TEST_OUTER_CA_PEM"
ENV_INNER_CA: Final = "JFM_TUNNEL_TEST_INNER_CA_PEM"
ENV_RESULT_FILE: Final = "JFM_TUNNEL_TEST_RESULT_FILE"
ENV_NAMES: Final = (ENV_RELAY_PORT, ENV_OUTER_CA, ENV_INNER_CA, ENV_RESULT_FILE)
RESULT_KEYS: Final = ("direct", "tunnel", "innerTls", "httpPosts", "relayPayloadVisible")
AUDIT_KEYS: Final = (
    "protocol_version",
    "result",
    "duration_bucket",
    "downstream_to_upstream_bucket",
    "upstream_to_downstream_bucket",
)
READY_PATTERN: Final = re.compile(rb"READY ([1-9][0-9]{0,4})\n")
TARGET_REQUEST_BYTES: Final = 131_071
SYNTHETIC_RESPONSE: Final = b"synthetic-triphase-ok"


class HarnessFailure(RuntimeError):
    pass


@dataclass(frozen=True)
class PKI:
    outer_ca: Path
    outer_relay_chain: Path
    outer_relay_key: Path
    inner_ca: Path
    inner_valid_chain: Path
    inner_valid_key: Path
    inner_wrong_chain: Path
    inner_wrong_key: Path
    certificate_material: tuple[bytes, ...]


@dataclass(frozen=True)
class ScenarioExpectation:
    direct: str
    tunnel: str
    inner_tls: str
    http_posts: int
    relay_connections: int


SCENARIOS: Final = {
    "success": ScenarioExpectation(
        direct="TCP_BEFORE_HTTP_BYTES",
        tunnel="ESTABLISHED",
        inner_tls="VERIFIED_WS024",
        http_posts=1,
        relay_connections=1,
    ),
    "after-write": ScenarioExpectation(
        direct="HTTP_WRITE_STARTED",
        tunnel="NOT_ATTEMPTED",
        inner_tls="NOT_ATTEMPTED",
        http_posts=0,
        relay_connections=0,
    ),
    "wrong-inner": ScenarioExpectation(
        direct="TCP_BEFORE_HTTP_BYTES",
        tunnel="FAILED",
        inner_tls="REJECTED_WRONG_LEAF",
        http_posts=0,
        relay_connections=1,
    ),
}


def fail(message: str) -> NoReturn:
    raise HarnessFailure(message)


def run_checked(
    command: list[str],
    *,
    cwd: Path = ROOT,
    env: dict[str, str] | None = None,
    timeout: float = 180.0,
) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        fail(f"command failed: {Path(command[0]).name}")
    return result


def write_private(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        view = memoryview(content)
        written = 0
        while written < len(view):
            written += os.write(descriptor, view[written:])
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    if path.stat().st_mode & 0o077:
        fail("private file permissions are unsafe")


def openssl(arguments: list[str], directory: Path) -> None:
    run_checked(["openssl", *arguments], cwd=directory, timeout=120.0)


def generate_ca(directory: Path, prefix: str, common_name: str) -> tuple[Path, Path]:
    key = directory / f"{prefix}-ca.key"
    certificate = directory / f"{prefix}-ca.pem"
    openssl(
        [
            "genpkey",
            "-algorithm",
            "RSA",
            "-pkeyopt",
            "rsa_keygen_bits:2048",
            "-out",
            str(key),
        ],
        directory,
    )
    key.chmod(0o600)
    openssl(
        [
            "req",
            "-x509",
            "-new",
            "-sha256",
            "-days",
            "2",
            "-key",
            str(key),
            "-subj",
            f"/CN={common_name}",
            "-out",
            str(certificate),
        ],
        directory,
    )
    certificate.chmod(0o644)
    return key, certificate


def generate_leaf(
    directory: Path,
    *,
    prefix: str,
    common_name: str,
    ca_key: Path,
    ca_certificate: Path,
) -> tuple[Path, Path, Path]:
    key = directory / f"{prefix}.key"
    request = directory / f"{prefix}.csr"
    certificate = directory / f"{prefix}.pem"
    chain = directory / f"{prefix}-chain.pem"
    extension = directory / f"{prefix}.ext"
    openssl(
        [
            "genpkey",
            "-algorithm",
            "RSA",
            "-pkeyopt",
            "rsa_keygen_bits:2048",
            "-out",
            str(key),
        ],
        directory,
    )
    key.chmod(0o600)
    openssl(
        [
            "req",
            "-new",
            "-sha256",
            "-key",
            str(key),
            "-subj",
            f"/CN={common_name}",
            "-out",
            str(request),
        ],
        directory,
    )
    extension.write_text(
        "basicConstraints=critical,CA:FALSE\n"
        "keyUsage=critical,digitalSignature,keyEncipherment\n"
        "extendedKeyUsage=serverAuth\n"
        f"subjectAltName=DNS:{common_name}\n"
        "authorityKeyIdentifier=keyid,issuer\n"
        "subjectKeyIdentifier=hash\n",
        encoding="ascii",
    )
    openssl(
        [
            "x509",
            "-req",
            "-sha256",
            "-days",
            "2",
            "-in",
            str(request),
            "-CA",
            str(ca_certificate),
            "-CAkey",
            str(ca_key),
            "-CAcreateserial",
            "-extfile",
            str(extension),
            "-out",
            str(certificate),
        ],
        directory,
    )
    chain.write_bytes(certificate.read_bytes() + ca_certificate.read_bytes())
    certificate.chmod(0o644)
    chain.chmod(0o644)
    request.unlink(missing_ok=True)
    extension.unlink(missing_ok=True)
    return key, certificate, chain


def pem_to_der(path: Path) -> bytes:
    text = path.read_text(encoding="ascii")
    match = re.search(
        r"-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----",
        text,
    )
    if match is None:
        fail("certificate PEM is malformed")
    return base64.b64decode("".join(match.group(1).split()), validate=True)


def generate_pki(directory: Path) -> PKI:
    outer_ca_key, outer_ca = generate_ca(directory, "outer", "WS024 Synthetic Outer CA")
    inner_ca_key, inner_ca = generate_ca(directory, "inner", "WS024 Synthetic Inner CA")
    outer_relay_key, outer_relay, outer_relay_chain = generate_leaf(
        directory,
        prefix="outer-relay",
        common_name="relay.test",
        ca_key=outer_ca_key,
        ca_certificate=outer_ca,
    )
    inner_valid_key, inner_valid, inner_valid_chain = generate_leaf(
        directory,
        prefix="inner-valid",
        common_name="ws024.juntadeandalucia.es",
        ca_key=inner_ca_key,
        ca_certificate=inner_ca,
    )
    inner_wrong_key, inner_wrong, inner_wrong_chain = generate_leaf(
        directory,
        prefix="inner-wrong",
        common_name="evil.example",
        ca_key=inner_ca_key,
        ca_certificate=inner_ca,
    )
    for private_key in (
        outer_ca_key,
        inner_ca_key,
        outer_relay_key,
        inner_valid_key,
        inner_wrong_key,
    ):
        if private_key.stat().st_mode & 0o077:
            fail("generated private key permissions are unsafe")
    material: list[bytes] = []
    for certificate in (outer_ca, outer_relay, inner_ca, inner_valid, inner_wrong):
        material.append(certificate.read_bytes())
        material.append(pem_to_der(certificate))
    return PKI(
        outer_ca=outer_ca,
        outer_relay_chain=outer_relay_chain,
        outer_relay_key=outer_relay_key,
        inner_ca=inner_ca,
        inner_valid_chain=inner_valid_chain,
        inner_valid_key=inner_valid_key,
        inner_wrong_chain=inner_wrong_chain,
        inner_wrong_key=inner_wrong_key,
        certificate_material=tuple(material),
    )


class InnerTLSServer:
    def __init__(
        self,
        certificate_chain: Path,
        private_key: Path,
        expected_request: bytes,
        expected_canary: bytes,
    ) -> None:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.set_alpn_protocols(["http/1.1"])
        context.load_cert_chain(str(certificate_chain), str(private_key))
        self._context = context
        self._expected_request = expected_request
        self._expected_canary = expected_canary
        self._listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._listener.bind(("127.0.0.1", 0))
        self._listener.listen(2)
        self._listener.settimeout(0.2)
        self.port = int(self._listener.getsockname()[1])
        self.connection_count = 0
        self.post_count = 0
        self.method: str | None = None
        self.content_type: str | None = None
        self.failure_stage: str | None = None
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="ws024-inner-tls-harness", daemon=True)
        self._thread.start()

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                raw, _ = self._listener.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            self.connection_count += 1
            try:
                raw.settimeout(8.0)
                self.failure_stage = "tls_handshake"
                with self._context.wrap_socket(raw, server_side=True) as connection:
                    self.failure_stage = "alpn"
                    if connection.selected_alpn_protocol() not in (None, "http/1.1"):
                        return
                    self.failure_stage = "http_header"
                    header = read_http_header(connection)
                    lines = header.decode("iso-8859-1").split("\r\n")
                    request_line = lines[0].split(" ")
                    if len(request_line) != 3 or request_line[0] != "POST" or request_line[2] != "HTTP/1.1":
                        return
                    headers: dict[str, str] = {}
                    for line in lines[1:]:
                        if not line:
                            continue
                        name, separator, value = line.partition(":")
                        if not separator:
                            return
                        headers[name.strip().lower()] = value.strip()
                    length_text = headers.get("content-length")
                    content_type = headers.get("content-type")
                    if length_text is None or content_type is None:
                        return
                    length = int(length_text)
                    self.failure_stage = "http_body"
                    body = read_exactly(connection, length)
                    try:
                        if bytes(body) != self._expected_request or self._expected_canary not in body:
                            return
                    finally:
                        body[:] = b"\x00" * len(body)
                    self.method = "POST"
                    self.content_type = content_type
                    self.post_count += 1
                    self.failure_stage = None
                    response = (
                        b"HTTP/1.1 200 OK\r\n"
                        b"Content-Type: text/plain\r\n"
                        + f"Content-Length: {len(SYNTHETIC_RESPONSE)}\r\n".encode("ascii")
                        + b"Connection: close\r\n\r\n"
                        + SYNTHETIC_RESPONSE
                    )
                    connection.sendall(response)
                    return
            except (EOFError, OSError, ssl.SSLError, ValueError):
                return
            finally:
                try:
                    raw.close()
                except OSError:
                    pass

    def close(self) -> None:
        self._stop.set()
        try:
            self._listener.close()
        except OSError:
            pass
        self._thread.join(timeout=10.0)
        if self._thread.is_alive():
            fail("inner TLS server did not terminate")


def read_http_header(connection: ssl.SSLSocket) -> bytes:
    output = bytearray()
    while len(output) <= 64 * 1024:
        chunk = connection.recv(1)
        if not chunk:
            raise EOFError("incomplete HTTP header")
        output.extend(chunk)
        if output.endswith(b"\r\n\r\n"):
            return bytes(output)
    raise ValueError("HTTP header is too large")


def read_exactly(connection: ssl.SSLSocket, length: int) -> bytearray:
    if length < 0 or length > 4 * 1024 * 1024:
        raise ValueError("invalid HTTP body length")
    output = bytearray(length)
    offset = 0
    while offset < length:
        chunk = connection.recv(length - offset)
        if not chunk:
            raise EOFError("incomplete HTTP body")
        output[offset : offset + len(chunk)] = chunk
        offset += len(chunk)
    return output


def build_integration_relay(binary: Path) -> None:
    run_checked(
        [
            "go",
            "build",
            "-tags=integration",
            "-o",
            str(binary),
            "./cmd/ws024-relay-integration",
        ],
        cwd=RELAY_MODULE,
        timeout=180.0,
    )
    binary.chmod(0o700)


def read_ready(process: subprocess.Popen[bytes]) -> tuple[int, bytes]:
    if process.stdout is None:
        fail("relay stdout is unavailable")
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    try:
        events = selector.select(timeout=10.0)
        if not events:
            fail("relay did not become ready")
        line = process.stdout.readline()
    finally:
        selector.close()
    match = READY_PATTERN.fullmatch(line)
    if match is None:
        fail("relay readiness record is invalid")
    port = int(match.group(1))
    if not 1 <= port <= 65535:
        fail("relay readiness port is invalid")
    return port, line


def start_relay(
    binary: Path,
    pki: PKI,
    inner_port: int,
    audit_file: Path,
    stage_file: Path,
) -> tuple[subprocess.Popen[bytes], int, bytes]:
    process = subprocess.Popen(
        [
            str(binary),
            "--listen",
            "127.0.0.1:0",
            "--tls-cert",
            str(pki.outer_relay_chain),
            "--tls-key",
            str(pki.outer_relay_key),
            "--upstream",
            f"127.0.0.1:{inner_port}",
            "--audit-file",
            str(audit_file),
            "--stage-file",
            str(stage_file),
        ],
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    try:
        port, ready = read_ready(process)
        return process, port, ready
    except BaseException:
        terminate_process(process)
        raise


def terminate_process(process: subprocess.Popen[bytes]) -> tuple[bytes, bytes]:
    if process.poll() is None:
        process.send_signal(signal.SIGTERM)
    try:
        stdout, stderr = process.communicate(timeout=15.0)
    except subprocess.TimeoutExpired:
        process.kill()
        stdout, stderr = process.communicate(timeout=5.0)
        fail("relay process did not terminate after SIGTERM")
    if process.returncode != 0:
        fail("relay process exited unsuccessfully")
    return stdout, stderr


def make_request(canary: bytes) -> bytes:
    prefix = b"op=pre&cop=sign&canary=" + canary + b"&padding="
    if len(prefix) >= TARGET_REQUEST_BYTES:
        fail("request canary is unexpectedly large")
    return prefix + (b"A" * (TARGET_REQUEST_BYTES - len(prefix)))


def run_gradle_scenario(
    relay_port: int,
    pki: PKI,
    result_file: Path,
) -> None:
    for output in (
        ROOT / "app/build/test-results/testDebugUnitTest",
        ROOT / "app/build/reports/tests/testDebugUnitTest",
    ):
        shutil.rmtree(output, ignore_errors=True)
    environment = os.environ.copy()
    for name in ENV_NAMES:
        environment.pop(name, None)
    environment.update(
        {
            ENV_RELAY_PORT: str(relay_port),
            ENV_OUTER_CA: str(pki.outer_ca),
            ENV_INNER_CA: str(pki.inner_ca),
            ENV_RESULT_FILE: str(result_file),
        }
    )
    run_checked(
        [
            "./gradlew",
            "testDebugUnitTest",
            "--tests",
            GRADLE_TEST,
            "--no-daemon",
            "--console=plain",
        ],
        cwd=ROOT,
        env=environment,
        timeout=600.0,
    )


def load_exact_json(path: Path, expected_keys: tuple[str, ...]) -> dict[str, object]:
    if not path.is_file() or path.is_symlink():
        fail("result file is not a regular file")
    raw = path.read_bytes()
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise HarnessFailure("result JSON is invalid") from error
    if not isinstance(value, dict) or tuple(value.keys()) != expected_keys:
        fail("result JSON fields are invalid")
    return value


def load_audit(path: Path) -> tuple[list[dict[str, object]], bytes]:
    if not path.is_file() or path.is_symlink():
        fail("audit file is not a regular file")
    if path.stat().st_mode & 0o077:
        fail("audit file permissions are unsafe")
    raw = path.read_bytes()
    records: list[dict[str, object]] = []
    for line in raw.splitlines():
        value = json.loads(line)
        if not isinstance(value, dict) or tuple(value.keys()) != AUDIT_KEYS:
            fail("audit fields are invalid")
        if value["protocol_version"] != "1":
            fail("audit protocol version is invalid")
        records.append(value)
    return records, raw


def load_stages(path: Path) -> tuple[list[str], bytes]:
    if not path.is_file() or path.is_symlink():
        fail("stage file is not a regular file")
    if path.stat().st_mode & 0o077:
        fail("stage file permissions are unsafe")
    raw = path.read_bytes()
    allowed = {
        "success",
        "outer_tls",
        "alpn",
        "connect",
        "credential",
        "admission",
        "upstream",
        "response",
        "pump",
        "internal",
    }
    try:
        stages = raw.decode("ascii").splitlines()
    except UnicodeDecodeError as error:
        raise HarnessFailure("stage file is not ASCII") from error
    if any(stage not in allowed for stage in stages):
        fail("integration stage record is invalid")
    return stages, raw


def validate_result(value: dict[str, object], expectation: ScenarioExpectation) -> None:
    if value["direct"] != expectation.direct:
        fail("direct result is invalid")
    if value["tunnel"] != expectation.tunnel:
        fail("tunnel result is invalid")
    if value["innerTls"] != expectation.inner_tls:
        fail("inner TLS result is invalid")
    if type(value["httpPosts"]) is not int or value["httpPosts"] != expectation.http_posts:
        fail("HTTP POST count is invalid")
    if type(value["relayPayloadVisible"]) is not bool or value["relayPayloadVisible"] is not False:
        fail("relay opacity result is invalid")


def prove_opacity(
    relay_output: bytes,
    audit_output: bytes,
    *,
    request: bytes,
    canary: bytes,
    credential: bytes,
    certificate_material: tuple[bytes, ...],
) -> None:
    combined = relay_output + b"\n" + audit_output
    forbidden = [
        request,
        canary,
        credential,
        str(len(request)).encode("ascii"),
        b"Authorization: Bearer",
        b"POST ",
        b"Content-Type:",
        *certificate_material,
    ]
    for value in forbidden:
        if value and value in combined:
            fail("relay output exposed forbidden material")


def run_scenario(
    name: str,
    directory: Path,
    binary: Path,
    pki: PKI,
    request: bytes,
    canary: bytes,
    credential: bytes,
) -> dict[str, object]:
    expectation = SCENARIOS[name]
    scenario_directory = directory / name
    scenario_directory.mkdir(mode=0o700)
    result_file = scenario_directory / f"{name}-result.json"
    request_file = Path(str(result_file) + ".request")
    credential_file = Path(str(result_file) + ".credential")
    audit_file = scenario_directory / "relay-audit.jsonl"
    stage_file = scenario_directory / "relay-stage.txt"
    write_private(request_file, request)
    write_private(credential_file, credential)
    wrong_leaf = name == "wrong-inner"
    inner = InnerTLSServer(
        pki.inner_wrong_chain if wrong_leaf else pki.inner_valid_chain,
        pki.inner_wrong_key if wrong_leaf else pki.inner_valid_key,
        request,
        canary,
    )
    process: subprocess.Popen[bytes] | None = None
    ready = b""
    trailing_stdout = b""
    stderr = b""
    scenario_error: HarnessFailure | None = None
    try:
        process, relay_port, ready = start_relay(
            binary,
            pki,
            inner.port,
            audit_file,
            stage_file,
        )
        run_gradle_scenario(relay_port, pki, result_file)
    except HarnessFailure as error:
        scenario_error = error
    finally:
        if process is not None:
            trailing_stdout, stderr = terminate_process(process)
        inner.close()
    if process is None or process.poll() is None:
        fail("relay process remains active")
    if scenario_error is not None:
        audit_results: list[object] = []
        if audit_file.is_file():
            records, _ = load_audit(audit_file)
            audit_results = [record["result"] for record in records]
        stages, _ = load_stages(stage_file) if stage_file.is_file() else ([], b"")
        fail(
            f"scenario {name} failed before result: audit={audit_results}, stages={stages}, "
            f"inner_connections={inner.connection_count}, inner_stage={inner.failure_stage}, "
            f"http_posts={inner.post_count}"
        )
    if ready + trailing_stdout != ready or stderr:
        fail("relay emitted unexpected output")
    result = load_exact_json(result_file, RESULT_KEYS)
    validate_result(result, expectation)
    if inner.connection_count != expectation.relay_connections:
        fail("inner TLS connection count is invalid")
    if inner.post_count != expectation.http_posts:
        fail("inner TLS HTTP POST count is invalid")
    if expectation.http_posts == 1:
        if inner.method != "POST" or inner.content_type is None or not inner.content_type.lower().startswith(
            "application/x-www-form-urlencoded"
        ):
            fail("inner HTTP metadata is invalid")
    audit_records, audit_raw = load_audit(audit_file)
    if len(audit_records) != expectation.relay_connections:
        fail("relay connection/audit count is invalid")
    stages, stage_raw = load_stages(stage_file)
    if len(stages) != expectation.relay_connections:
        fail("relay connection/stage count is invalid")
    prove_opacity(
        ready + trailing_stdout + stderr + stage_raw,
        audit_raw,
        request=request,
        canary=canary,
        credential=credential,
        certificate_material=pki.certificate_material,
    )
    return result


def verify_prerequisites() -> None:
    for command in ("openssl", "go", "python"):
        if shutil.which(command) is None:
            fail(f"required command is unavailable: {command}")
    if not (ROOT / "gradlew").is_file():
        fail("Gradle wrapper is unavailable")


def main() -> int:
    verify_prerequisites()
    temporary_path: Path | None = None
    success_result: dict[str, object] | None = None
    with tempfile.TemporaryDirectory(prefix="ws024-double-tls-") as temporary:
        temporary_path = Path(temporary)
        temporary_path.chmod(0o700)
        pki_directory = temporary_path / "pki"
        pki_directory.mkdir(mode=0o700)
        pki = generate_pki(pki_directory)
        binary = temporary_path / "ws024-relay-integration"
        build_integration_relay(binary)
        canary = secrets.token_urlsafe(32).encode("ascii")
        credential = secrets.token_urlsafe(36).encode("ascii")
        request = make_request(canary)
        for name in ("success", "after-write", "wrong-inner"):
            result = run_scenario(
                name,
                temporary_path,
                binary,
                pki,
                request,
                canary,
                credential,
            )
            if name == "success":
                success_result = result
    if temporary_path is None or temporary_path.exists():
        fail("temporary PKI cleanup failed")
    if success_result is None:
        fail("success result is unavailable")
    expected = {
        "direct": "TCP_BEFORE_HTTP_BYTES",
        "tunnel": "ESTABLISHED",
        "innerTls": "VERIFIED_WS024",
        "httpPosts": 1,
        "relayPayloadVisible": False,
    }
    if success_result != expected:
        fail("final result is invalid")
    print(json.dumps(expected, separators=(",", ":"), ensure_ascii=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (HarnessFailure, OSError, subprocess.SubprocessError, ValueError, json.JSONDecodeError) as error:
        print(f"ws024 tunnel harness: {error}", file=sys.stderr)
        raise SystemExit(1)
