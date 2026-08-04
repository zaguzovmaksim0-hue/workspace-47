#!/usr/bin/env python3
"""Bounded, read-only discovery of public Spanish government portal contracts.

The scanner deliberately produces candidate evidence only. It never authenticates,
submits forms, invokes custom schemes, calls discovered endpoints, or promotes a
portal above BROWSE_ONLY.
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import http.client
import ipaddress
import json
import os
from pathlib import Path
import queue
import re
import socket
import ssl
import sys
import tempfile
import threading
import time
import unicodedata
from html.parser import HTMLParser
from typing import Callable, Iterable, Mapping, Protocol, Sequence, TypeVar
from urllib.parse import parse_qsl, unquote, urlencode, urljoin, urlsplit, urlunsplit


SCHEMA_VERSION = 1
OUTPUT_SCHEMA_VERSION = 1
MAX_INPUT_BYTES = 4 * 1024 * 1024
MAX_FIXTURE_BYTES = 8 * 1024 * 1024
MAX_FIXTURE_TOTAL_BODY_BYTES = 32 * 1024 * 1024
DEFAULT_MAX_BODY_BYTES = 2 * 1024 * 1024
DEFAULT_MAX_REDIRECTS = 4
DEFAULT_MAX_SCRIPT_DEPTH = 2
DEFAULT_MAX_ASSETS = 32
DEFAULT_TIMEOUT_SECONDS = 15.0
DEFAULT_MIN_HOST_INTERVAL_SECONDS = 0.5

SEED_KEYS = frozenset(
    {
        "seed_id",
        "institution_name",
        "administrative_level",
        "autonomous_community",
        "province_or_municipality",
        "source_url",
        "entry_urls",
        "public_query_keys",
        "allowed_redirect_origins",
    }
)
ROOT_KEYS = frozenset({"schema_version", "snapshot_id", "snapshot_date", "seeds"})
ADMINISTRATIVE_LEVELS = frozenset(
    {
        "ESTATAL",
        "AUTONOMICO",
        "PROVINCIAL",
        "INSULAR",
        "MUNICIPAL",
        "UNIVERSIDAD_PUBLICA",
        "OTRA_INSTITUCION_PUBLICA",
    }
)
SENSITIVE_QUERY_KEYS = frozenset(
    {
        "access_token",
        "auth",
        "authorization",
        "cert",
        "certificate",
        "code",
        "cookie",
        "credential",
        "firma",
        "jsessionid",
        "password",
        "pkcs12",
        "p12",
        "session",
        "sessionid",
        "sig",
        "signature",
        "state",
        "ticket",
        "token",
        "accesstoken",
        "sessionkey",
        "samlrequest",
        "relaystate",
        "idsesion",
        "clave",
        "secret",
        "nonce",
        "challenge",
    }
)
SENSITIVE_QUERY_FRAGMENTS = (
    "authorization",
    "auth",
    "code",
    "state",
    "certificate",
    "cert",
    "signature",
    "firma",
    "ticket",
    "nonce",
    "challenge",
    "token",
    "session",
    "sesion",
    "secret",
    "password",
    "passwd",
    "credential",
    "cookie",
    "saml",
)
SENSITIVE_QUERY_AFFIXES = ("sig", "pkcs12", "p12")
SEED_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,95}\Z")
QUERY_KEY_RE = re.compile(r"[A-Za-z][A-Za-z0-9_.-]{0,63}\Z")
SAFE_QUERY_VALUE_RE = re.compile(r"[A-Za-z0-9._~-]{0,128}\Z")
JS_REFERENCE_RE = re.compile(
    r"(?:\bimport\s*(?:[^;]*?\sfrom\s*)?|\bimport\s*\()"
    r"[\"']([^\"']+\.js(?:\?[^\"']*)?)[\"']",
    re.IGNORECASE,
)
ENDPOINT_TOKEN_RE = re.compile(
    r"[\"']([^\"'\s<>]*(?:TriPhaseSignatureService|SignatureService|StorageService|"
    r"RetrieveService|stservlet|rtservlet)[^\"'\s<>]*)[\"']",
    re.IGNORECASE,
)
SET_SERVLETS_RE = re.compile(
    r"(?:setServlets|setServletsLocation)\s*\(\s*[\"']([^\"']+)[\"']\s*,\s*"
    r"[\"']([^\"']+)[\"']",
    re.IGNORECASE,
)


class InventoryError(ValueError):
    """A fail-closed validation or scanning error."""


T = TypeVar("T")


@dataclasses.dataclass(frozen=True)
class Seed:
    seed_id: str
    institution_name: str
    administrative_level: str
    autonomous_community: str
    province_or_municipality: str
    source_url: str
    entry_urls: tuple[str, ...]
    public_query_keys: frozenset[str]
    allowed_redirect_origins: frozenset[str]


@dataclasses.dataclass(frozen=True)
class InventoryInput:
    snapshot_id: str
    snapshot_date: str
    seeds: tuple[Seed, ...]


@dataclasses.dataclass(frozen=True)
class FetchResult:
    requested_url: str
    final_url: str
    redirect_chain: tuple[str, ...]
    status: int
    content_type: str
    body: bytes
    etag: str | None = None
    last_modified: str | None = None
    request_count: int = 1


class Fetcher(Protocol):
    def fetch(
        self,
        url: str,
        *,
        same_origin: str | None = None,
        allowed_query_keys: Iterable[str] = (),
        allowed_redirect_origins: Iterable[str] = (),
    ) -> FetchResult:
        ...


@dataclasses.dataclass(frozen=True)
class ScanLimits:
    max_body_bytes: int = DEFAULT_MAX_BODY_BYTES
    max_redirects: int = DEFAULT_MAX_REDIRECTS
    max_script_depth: int = DEFAULT_MAX_SCRIPT_DEPTH
    max_assets: int = DEFAULT_MAX_ASSETS
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    min_host_interval_seconds: float = DEFAULT_MIN_HOST_INTERVAL_SECONDS

    def validate(self) -> None:
        if not 1 <= self.max_body_bytes <= 5 * 1024 * 1024:
            raise InventoryError("max_body_bytes must be between 1 and 5242880")
        if not 0 <= self.max_redirects <= 8:
            raise InventoryError("max_redirects must be between 0 and 8")
        if not 0 <= self.max_script_depth <= 3:
            raise InventoryError("max_script_depth must be between 0 and 3")
        if not 0 <= self.max_assets <= 64:
            raise InventoryError("max_assets must be between 0 and 64")
        if not 1.0 <= self.timeout_seconds <= 30.0:
            raise InventoryError("timeout_seconds must be between 1 and 30")
        if not 0.0 <= self.min_host_interval_seconds <= 5.0:
            raise InventoryError("min_host_interval_seconds must be between 0 and 5")


def _require_exact_keys(value: Mapping[str, object], expected: frozenset[str], context: str) -> None:
    actual = frozenset(value.keys())
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        raise InventoryError(f"{context} keys mismatch; missing={missing}, unknown={unknown}")


def _require_string(value: object, context: str, *, allow_empty: bool = False) -> str:
    if not isinstance(value, str):
        raise InventoryError(f"{context} must be a string")
    if not allow_empty and not value.strip():
        raise InventoryError(f"{context} must not be blank")
    if any(ord(char) < 0x20 or ord(char) == 0x7F for char in value):
        raise InventoryError(f"{context} contains a control character")
    return value


def _canonical_host(host: str) -> str:
    if not host or host.endswith(".") or "*" in host or "\\" in host:
        raise InventoryError("host is missing or not exact")
    try:
        ascii_host = host.encode("idna").decode("ascii").lower()
    except UnicodeError as exc:
        raise InventoryError("host is not valid IDNA") from exc
    if ascii_host == "localhost" or ascii_host.endswith(".localhost"):
        raise InventoryError("localhost is forbidden")
    try:
        ipaddress.ip_address(ascii_host.strip("[]"))
    except ValueError:
        pass
    else:
        raise InventoryError("IP literal hosts are forbidden")
    labels = ascii_host.split(".")
    if len(labels) < 2 or any(not label or len(label) > 63 for label in labels):
        raise InventoryError("host must be a fully qualified DNS name")
    if any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label) for label in labels):
        raise InventoryError("host contains an invalid label")
    return ascii_host


def exact_origin(url: str) -> str:
    parts = _validated_https_parts(url)
    host = _canonical_host(parts.hostname or "")
    try:
        port = parts.port
    except ValueError as exc:
        raise InventoryError("URL port is invalid") from exc
    authority = host if port in (None, 443) else f"{host}:{port}"
    return f"https://{authority}"


def canonical_origin(value: str) -> str:
    parts = _validated_https_parts(value)
    if parts.path not in ("", "/") or parts.query:
        raise InventoryError("redirect origin must not contain path or query")
    return exact_origin(value)


def _is_sensitive_query_key(key: str) -> bool:
    normalized = re.sub(r"[^a-z0-9]", "", key.casefold())
    normalized_exact = {
        re.sub(r"[^a-z0-9]", "", candidate.casefold()) for candidate in SENSITIVE_QUERY_KEYS
    }
    return normalized in normalized_exact or any(
        fragment in normalized for fragment in SENSITIVE_QUERY_FRAGMENTS
    ) or any(
        normalized.startswith(affix) or normalized.endswith(affix)
        for affix in SENSITIVE_QUERY_AFFIXES
    )


def _validate_decoded_component(raw: str, context: str) -> str:
    if re.search(r"%(?![0-9A-Fa-f]{2})", raw):
        raise InventoryError(f"{context} contains malformed percent encoding")
    try:
        decoded_once = unquote(raw, errors="strict")
        decoded_twice = unquote(decoded_once, errors="strict")
    except UnicodeError as exc:
        raise InventoryError(f"{context} is not valid UTF-8 percent encoding") from exc
    if decoded_once != decoded_twice:
        raise InventoryError(f"{context} contains double percent encoding")
    if unicodedata.normalize("NFC", decoded_once) != decoded_once:
        raise InventoryError(f"{context} is not Unicode NFC")
    if any(ord(char) < 0x20 or ord(char) == 0x7F for char in decoded_once):
        raise InventoryError(f"{context} contains an encoded control character")
    return decoded_once


def _validated_https_parts(url: str):
    if not isinstance(url, str) or not url:
        raise InventoryError("URL must be a non-empty string")
    if any(ord(char) < 0x20 or ord(char) == 0x7F for char in url):
        raise InventoryError("URL contains a control character")
    parts = urlsplit(url)
    if parts.scheme.lower() != "https":
        raise InventoryError("only HTTPS URLs are allowed")
    if parts.username is not None or parts.password is not None:
        raise InventoryError("URL credentials are forbidden")
    if parts.fragment:
        raise InventoryError("URL fragments are forbidden")
    _canonical_host(parts.hostname or "")
    try:
        port = parts.port
    except ValueError as exc:
        raise InventoryError("URL port is invalid") from exc
    if port is not None and not 1 <= port <= 65535:
        raise InventoryError("URL port is outside the valid range")
    if "\\" in parts.path or "//" in parts.path or ";" in parts.path:
        raise InventoryError("URL path is not canonical")
    decoded_path = _validate_decoded_component(parts.path, "URL path")
    _validate_decoded_component(parts.query, "URL query")
    lowered_path = parts.path.casefold()
    if "%2f" in lowered_path or "%5c" in lowered_path:
        raise InventoryError("URL path contains an encoded separator")
    if "\\" in decoded_path or "//" in decoded_path or ";" in decoded_path:
        raise InventoryError("URL path contains an encoded ambiguous separator")
    path_segments = decoded_path.split("/")
    if any(segment in {".", ".."} for segment in path_segments):
        raise InventoryError("URL path contains a dot segment")
    return parts


def sanitize_url(
    url: str,
    allowed_query_keys: Iterable[str] = (),
    *,
    reject_unlisted_query: bool = False,
) -> str:
    """Return the only URL representation that may be persisted or requested as a seed."""
    parts = _validated_https_parts(url)
    host = _canonical_host(parts.hostname or "")
    port = parts.port
    authority = host if port in (None, 443) else f"{host}:{port}"
    allowed = frozenset(allowed_query_keys)
    for key in allowed:
        if not QUERY_KEY_RE.fullmatch(key) or _is_sensitive_query_key(key):
            raise InventoryError(f"unsafe public query key: {key!r}")
    retained: list[tuple[str, str]] = []
    seen_query_keys: set[str] = set()
    for key, value in parse_qsl(parts.query, keep_blank_values=True, strict_parsing=False):
        if key not in allowed:
            if reject_unlisted_query:
                raise InventoryError(f"redirect query key is not explicitly allowed: {key!r}")
            continue
        if key in seen_query_keys:
            raise InventoryError(f"duplicate public query key: {key!r}")
        seen_query_keys.add(key)
        if _is_sensitive_query_key(key) or not SAFE_QUERY_VALUE_RE.fullmatch(value):
            raise InventoryError(f"unsafe public query value for key {key!r}")
        retained.append((key, value))
    retained.sort()
    path = parts.path or "/"
    return urlunsplit(("https", authority, path, urlencode(retained), ""))


def sanitize_redirect_url(url: str) -> str:
    """Redact all redirect query values while retaining exact public origin/path."""
    parts = _validated_https_parts(url)
    host = _canonical_host(parts.hostname or "")
    port = parts.port
    authority = host if port in (None, 443) else f"{host}:{port}"
    return urlunsplit(("https", authority, parts.path or "/", "", ""))


def _resolve_public_addresses(
    host: str,
    port: int,
    deadline: float,
) -> tuple[tuple[int, int, int, str, tuple], ...]:
    results: queue.Queue[tuple[str, object]] = queue.Queue(maxsize=1)

    def resolve() -> None:
        try:
            answers = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
        except OSError as exc:
            results.put(("error", exc))
        else:
            results.put(("ok", answers))

    worker = threading.Thread(target=resolve, daemon=True, name="public-inventory-dns")
    worker.start()
    worker.join(_remaining_seconds(deadline))
    if worker.is_alive():
        raise InventoryError("public DNS resolution timed out")
    try:
        status, payload = results.get_nowait()
    except queue.Empty as exc:
        raise InventoryError("public DNS resolution produced no result") from exc
    if status != "ok":
        if isinstance(payload, BaseException):
            raise InventoryError("public DNS resolution failed") from payload
        raise InventoryError("public DNS resolution failed")
    answers = payload
    if not isinstance(answers, list) or not answers:
        raise InventoryError("public DNS resolution returned no addresses")
    validated: list[tuple[int, int, int, str, tuple]] = []
    seen: set[tuple[int, str, int]] = set()
    for answer in answers:
        family, socktype, protocol, canonical_name, sockaddr = answer
        address = str(sockaddr[0]).split("%", 1)[0]
        try:
            parsed = ipaddress.ip_address(address)
        except ValueError as exc:
            raise InventoryError("DNS returned a malformed address") from exc
        if not parsed.is_global:
            raise InventoryError("DNS returned a non-public address")
        key = (family, address, int(sockaddr[1]))
        if key not in seen:
            seen.add(key)
            validated.append((family, socktype, protocol, canonical_name, sockaddr))
    return tuple(validated)


def _remaining_seconds(deadline: float) -> float:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise InventoryError("HTTPS request deadline exceeded")
    return remaining


def _run_timeout_cleanup(on_timeout: Callable[[], None] | None) -> None:
    if on_timeout is None:
        return
    try:
        on_timeout()
    except Exception:
        pass


def _run_with_deadline(
    operation: Callable[[], T],
    deadline: float,
    on_timeout: Callable[[], None] | None = None,
) -> T:
    results: queue.Queue[tuple[str, object]] = queue.Queue(maxsize=1)

    def run() -> None:
        try:
            value = operation()
        except BaseException as exc:  # Propagated to the calling scanner thread.
            results.put(("error", exc))
        else:
            results.put(("ok", value))

    worker = threading.Thread(target=run, daemon=True, name="public-inventory-io")
    worker.start()
    try:
        join_timeout = _remaining_seconds(deadline)
    except InventoryError:
        _run_timeout_cleanup(on_timeout)
        raise
    worker.join(join_timeout)
    if worker.is_alive():
        _run_timeout_cleanup(on_timeout)
        raise InventoryError("HTTPS request deadline exceeded")
    try:
        status, payload = results.get_nowait()
    except queue.Empty as exc:
        raise InventoryError("HTTPS operation produced no result") from exc
    if status == "error":
        if isinstance(payload, BaseException):
            raise payload
        raise InventoryError("HTTPS operation failed")
    return payload  # type: ignore[return-value]


def _safe_response_header(value: str | None) -> str | None:
    if value is None:
        return None
    if len(value) > 256 or any(ord(char) < 0x20 or ord(char) == 0x7F for char in value):
        return None
    return value


class _PinnedHttpsConnection(http.client.HTTPSConnection):
    def __init__(
        self,
        host: str,
        port: int,
        addresses: Sequence[tuple[int, int, int, str, tuple]],
        *,
        deadline: float,
        context: ssl.SSLContext,
    ) -> None:
        super().__init__(
            host=host,
            port=port,
            timeout=_remaining_seconds(deadline),
            context=context,
        )
        self._addresses = tuple(addresses)
        self._deadline = deadline

    def connect(self) -> None:
        last_error: OSError | None = None
        for family, socktype, protocol, _, sockaddr in self._addresses:
            remaining = _remaining_seconds(self._deadline)
            raw_socket = socket.socket(family, socktype, protocol)
            try:
                self.sock = raw_socket
                raw_socket.settimeout(remaining)
                raw_socket.connect(sockaddr)
                raw_socket.settimeout(_remaining_seconds(self._deadline))
                wrapped_socket = self._context.wrap_socket(
                    raw_socket,
                    server_hostname=self.host,
                    do_handshake_on_connect=False,
                )
                self.sock = wrapped_socket
                wrapped_socket.settimeout(_remaining_seconds(self._deadline))
                wrapped_socket.do_handshake()
                return
            except InventoryError:
                if self.sock is not None:
                    self.sock.close()
                self.sock = None
                raise
            except OSError as exc:
                last_error = exc
                if self.sock is not None:
                    self.sock.close()
                self.sock = None
        if last_error is None:
            raise OSError("no approved address available")
        raise last_error


def _read_bounded_body(
    response: http.client.HTTPResponse,
    connection: _PinnedHttpsConnection,
    max_body_bytes: int,
    deadline: float,
) -> bytes:
    chunks: list[bytes] = []
    total = 0
    while True:
        remaining = _remaining_seconds(deadline)
        if connection.sock is not None:
            connection.sock.settimeout(remaining)
        chunk = _run_with_deadline(
            lambda: response.read1(min(64 * 1024, max_body_bytes + 1 - total)),
            deadline,
            connection.close,
        )
        if not chunk:
            break
        chunks.append(chunk)
        total += len(chunk)
        if total > max_body_bytes:
            raise InventoryError("response body exceeded the configured limit")
    return b"".join(chunks)


class LiveFetcher:
    def __init__(self, limits: ScanLimits):
        limits.validate()
        self._limits = limits
        self._ssl_context = ssl.create_default_context()
        try:
            self._ssl_context.set_alpn_protocols(["http/1.1"])
        except NotImplementedError:
            pass
        self._last_request_by_authority: dict[tuple[str, int], float] = {}

    def _before_request(self, host: str, port: int, deadline: float) -> None:
        authority = (host, port)
        previous = self._last_request_by_authority.get(authority)
        now = time.monotonic()
        if previous is not None:
            remaining = self._limits.min_host_interval_seconds - (now - previous)
            if remaining > 0:
                if remaining >= _remaining_seconds(deadline):
                    raise InventoryError("HTTPS request deadline exceeded during rate limiting")
                time.sleep(remaining)
        _remaining_seconds(deadline)
        self._last_request_by_authority[authority] = time.monotonic()

    def fetch(
        self,
        url: str,
        *,
        same_origin: str | None = None,
        allowed_query_keys: Iterable[str] = (),
        allowed_redirect_origins: Iterable[str] = (),
    ) -> FetchResult:
        allowed_query_keys = frozenset(allowed_query_keys)
        deadline = time.monotonic() + self._limits.timeout_seconds
        request_url = sanitize_url(url, allowed_query_keys)
        initial_origin = exact_origin(request_url)
        redirects_allowed = {canonical_origin(value) for value in allowed_redirect_origins}
        approved_origins = {initial_origin, *redirects_allowed}
        if same_origin is not None:
            same_origin = canonical_origin(same_origin)
            if initial_origin != same_origin or redirects_allowed:
                raise InventoryError("asset request left the approved origin")
            approved_origins = {same_origin}

        current_url = request_url
        redirect_chain: list[str] = []
        request_count = 0
        while True:
            current_origin = exact_origin(current_url)
            if current_origin not in approved_origins:
                raise InventoryError("redirect left the explicitly approved origins")
            parts = urlsplit(current_url)
            host = _canonical_host(parts.hostname or "")
            port = parts.port or 443
            addresses = _resolve_public_addresses(
                host,
                port,
                deadline,
            )
            self._before_request(host, port, deadline)
            connection = _PinnedHttpsConnection(
                host,
                port,
                addresses,
                deadline=deadline,
                context=self._ssl_context,
            )
            path = urlunsplit(("", "", parts.path or "/", parts.query, ""))
            try:
                _run_with_deadline(
                    lambda: connection.request(
                        "GET",
                        path,
                        headers={
                            "Accept": "text/html,application/xhtml+xml,application/javascript,text/javascript;q=0.9,*/*;q=0.1",
                            "Accept-Encoding": "identity",
                            "Connection": "close",
                            "User-Agent": "JuntaFirmaMobile-PublicInventory/1.0",
                        },
                    ),
                    deadline,
                    connection.close,
                )
                if connection.sock is not None:
                    connection.sock.settimeout(_remaining_seconds(deadline))
                response = _run_with_deadline(
                    connection.getresponse,
                    deadline,
                    connection.close,
                )
                request_count += 1
                if response.status in (301, 302, 303, 307, 308):
                    location = response.getheader("Location")
                    if location is None:
                        raise InventoryError("redirect response omitted Location")
                    if len(redirect_chain) >= self._limits.max_redirects:
                        raise InventoryError("redirect limit exceeded")
                    absolute = urljoin(current_url, location)
                    next_url = sanitize_url(
                        absolute,
                        allowed_query_keys,
                        reject_unlisted_query=True,
                    )
                    if exact_origin(next_url) not in approved_origins:
                        raise InventoryError("redirect left the explicitly approved origins")
                    redirect_chain.append(sanitize_redirect_url(next_url))
                    current_url = next_url
                    continue
                if not 200 <= response.status <= 299:
                    raise InventoryError(f"HTTP status {response.status}")
                content_encoding = (response.getheader("Content-Encoding") or "identity").lower()
                if content_encoding != "identity":
                    raise InventoryError("response used an unsupported content encoding")
                content_length = response.getheader("Content-Length")
                if content_length is not None:
                    try:
                        declared_length = int(content_length)
                    except ValueError as exc:
                        raise InventoryError("response Content-Length is malformed") from exc
                    if declared_length < 0 or declared_length > self._limits.max_body_bytes:
                        raise InventoryError("response body exceeded the configured limit")
                body = _read_bounded_body(
                    response,
                    connection,
                    self._limits.max_body_bytes,
                    deadline,
                )
                content_type = (response.getheader("Content-Type") or "application/octet-stream")
                content_type = content_type.split(";", 1)[0].strip().lower()
                if len(content_type) > 128 or not re.fullmatch(
                    r"[a-z0-9.+-]+/[a-z0-9.+-]+", content_type
                ):
                    content_type = "application/octet-stream"
                etag = _safe_response_header(response.getheader("ETag"))
                last_modified = _safe_response_header(response.getheader("Last-Modified"))
                return FetchResult(
                    requested_url=request_url,
                    final_url=sanitize_redirect_url(current_url),
                    redirect_chain=tuple(redirect_chain),
                    status=int(response.status),
                    content_type=content_type,
                    body=body,
                    etag=etag,
                    last_modified=last_modified,
                    request_count=request_count,
                )
            except (OSError, ssl.SSLError, http.client.HTTPException) as exc:
                raise InventoryError("HTTPS request failed") from exc
            finally:
                connection.close()


class OfflineFixtureFetcher:
    MANIFEST_KEYS = frozenset({"schema_version", "responses"})
    RESPONSE_KEYS = frozenset(
        {
            "url",
            "public_query_keys",
            "status",
            "content_type",
            "body_file",
            "final_url",
            "redirect_chain",
            "etag",
            "last_modified",
        }
    )

    def __init__(self, manifest_path: Path, limits: ScanLimits):
        limits.validate()
        data = _load_json(manifest_path, MAX_FIXTURE_BYTES)
        manifest_path = manifest_path.resolve(strict=True)
        if not isinstance(data, dict):
            raise InventoryError("fixture manifest root must be an object")
        _require_exact_keys(data, self.MANIFEST_KEYS, "fixture manifest")
        if data["schema_version"] != SCHEMA_VERSION:
            raise InventoryError("unsupported fixture schema_version")
        responses = data["responses"]
        if not isinstance(responses, list):
            raise InventoryError("fixture responses must be a list")
        self._limits = limits
        self._responses: dict[str, tuple[frozenset[str], FetchResult]] = {}
        total_body_bytes = 0
        base = manifest_path.parent
        for index, raw in enumerate(responses):
            if not isinstance(raw, dict):
                raise InventoryError(f"fixture response {index} must be an object")
            _require_exact_keys(raw, self.RESPONSE_KEYS, f"fixture response {index}")
            query_keys_raw = raw["public_query_keys"]
            if not isinstance(query_keys_raw, list) or not all(
                isinstance(item, str) for item in query_keys_raw
            ):
                raise InventoryError("fixture public_query_keys must be a string list")
            if len(set(query_keys_raw)) != len(query_keys_raw):
                raise InventoryError("fixture public_query_keys contains duplicates")
            query_keys = frozenset(query_keys_raw)
            raw_url = _require_string(raw["url"], "fixture url")
            raw_url_keys = {key for key, _ in parse_qsl(urlsplit(raw_url).query)}
            if not raw_url_keys.issubset(query_keys):
                raise InventoryError("fixture URL contains an undeclared query key")
            url = sanitize_url(raw_url, query_keys)
            final_url = sanitize_redirect_url(
                sanitize_url(
                    _require_string(raw["final_url"], "fixture final_url"),
                    query_keys,
                    reject_unlisted_query=True,
                )
            )
            status = raw["status"]
            if not isinstance(status, int) or isinstance(status, bool) or not 200 <= status <= 299:
                raise InventoryError("fixture status must be a successful HTTP status integer")
            content_type = _require_string(raw["content_type"], "fixture content_type").lower()
            if not re.fullmatch(r"[a-z0-9.+-]+/[a-z0-9.+-]+", content_type):
                raise InventoryError("fixture content_type is malformed")
            body_file = Path(_require_string(raw["body_file"], "fixture body_file"))
            if body_file.is_absolute() or ".." in body_file.parts:
                raise InventoryError("fixture body_file must remain inside the fixture directory")
            unresolved_body_path = base / body_file
            if unresolved_body_path.is_symlink():
                raise InventoryError("fixture body_file must not be a symlink")
            body_path = unresolved_body_path.resolve(strict=True)
            try:
                body_path.relative_to(base)
            except ValueError as exc:
                raise InventoryError("fixture body_file escaped the fixture directory") from exc
            if not body_path.is_file():
                raise InventoryError("fixture body_file must be a regular non-symlink file")
            body_stat = body_path.stat()
            if body_stat.st_nlink != 1:
                raise InventoryError("fixture body_file must not be hard-linked")
            if body_stat.st_size > limits.max_body_bytes:
                raise InventoryError("fixture body exceeded the configured limit")
            total_body_bytes += body_stat.st_size
            if total_body_bytes > MAX_FIXTURE_TOTAL_BODY_BYTES:
                raise InventoryError("fixture bodies exceeded the total size limit")
            body = body_path.read_bytes()
            if len(body) > limits.max_body_bytes:
                raise InventoryError("fixture body exceeded the configured limit")
            chain_raw = raw["redirect_chain"]
            if not isinstance(chain_raw, list) or not all(isinstance(item, str) for item in chain_raw):
                raise InventoryError("fixture redirect_chain must be a string list")
            chain = tuple(
                sanitize_redirect_url(
                    sanitize_url(item, query_keys, reject_unlisted_query=True)
                )
                for item in chain_raw
            )
            if len(chain) > limits.max_redirects:
                raise InventoryError("fixture redirect limit exceeded")
            if chain:
                if chain[-1] != final_url:
                    raise InventoryError("fixture final_url does not match redirect_chain")
            elif sanitize_redirect_url(url) != final_url:
                raise InventoryError("fixture changed final_url without a redirect")
            etag_raw = raw["etag"]
            last_modified_raw = raw["last_modified"]
            if etag_raw is not None and not isinstance(etag_raw, str):
                raise InventoryError("fixture etag must be a string or null")
            if last_modified_raw is not None and not isinstance(last_modified_raw, str):
                raise InventoryError("fixture last_modified must be a string or null")
            etag = _safe_response_header(etag_raw)
            last_modified = _safe_response_header(last_modified_raw)
            if etag_raw is not None and etag is None:
                raise InventoryError("fixture etag is unsafe")
            if last_modified_raw is not None and last_modified is None:
                raise InventoryError("fixture last_modified is unsafe")
            if url in self._responses:
                raise InventoryError("duplicate fixture URL")
            self._responses[url] = (
                query_keys,
                FetchResult(
                    requested_url=url,
                    final_url=final_url,
                    redirect_chain=chain,
                    status=status,
                    content_type=content_type,
                    body=body,
                    etag=etag,
                    last_modified=last_modified,
                    request_count=1 + len(chain),
                ),
            )

    def fetch(
        self,
        url: str,
        *,
        same_origin: str | None = None,
        allowed_query_keys: Iterable[str] = (),
        allowed_redirect_origins: Iterable[str] = (),
    ) -> FetchResult:
        allowed = frozenset(allowed_query_keys)
        key = sanitize_url(url, allowed)
        try:
            fixture_query_keys, result = self._responses[key]
        except KeyError as exc:
            raise InventoryError("offline fixture is missing for requested URL") from exc
        if fixture_query_keys != allowed:
            raise InventoryError("offline fixture query policy does not match the seed")
        initial_origin = exact_origin(key)
        redirects_allowed = {canonical_origin(value) for value in allowed_redirect_origins}
        approved_origins = {initial_origin, *redirects_allowed}
        if same_origin is not None:
            same_origin = canonical_origin(same_origin)
            if initial_origin != same_origin or redirects_allowed:
                raise InventoryError("offline asset fixture left the approved origin")
            approved_origins = {same_origin}
        if any(exact_origin(item) not in approved_origins for item in result.redirect_chain):
            raise InventoryError("offline fixture redirect left the approved origins")
        if exact_origin(result.final_url) not in approved_origins:
            raise InventoryError("offline fixture final URL left the approved origins")
        return result


class _ScriptParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.script_sources: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "script":
            return
        for key, value in attrs:
            if key.lower() == "src" and value:
                self.script_sources.append(value)


FINGERPRINT_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("CLIENT_AUTOSCRIPT", re.compile(r"\bAutoScript\b|autoscript\.js", re.IGNORECASE)),
    ("CLIENT_MINIAPPLET", re.compile(r"\bMiniApplet\b|miniapplet\.js", re.IGNORECASE)),
    ("CLIENT_AFIRMA", re.compile(r"Cliente\s*@?firma|ClienteFirma|clienteafirma", re.IGNORECASE)),
    ("AUTOFIRMA_REFERENCE", re.compile(r"\bAutoFirma\b|es\.gob\.afirma", re.IGNORECASE)),
    ("AFIRMA_URI", re.compile(r"afirma://", re.IGNORECASE)),
    (
        "AUTOFIRMA_INTENT_URI",
        re.compile(r"intent://[^\s\"']*(?:afirma|es\.gob\.afirma)", re.IGNORECASE),
    ),
    (
        "OP_SIGN",
        re.compile(
            r"\b(?:MiniApplet|AutoScript|ClienteFirma|clienteafirma)\s*\.\s*sign\s*\(",
            re.IGNORECASE,
        ),
    ),
    (
        "OP_COSIGN",
        re.compile(
            r"\b(?:MiniApplet|AutoScript|ClienteFirma|clienteafirma)\s*\.\s*coSign\s*\(",
            re.IGNORECASE,
        ),
    ),
    (
        "OP_COUNTERSIGN",
        re.compile(
            r"\b(?:MiniApplet|AutoScript|ClienteFirma|clienteafirma)\s*\.\s*counterSign\s*\(",
            re.IGNORECASE,
        ),
    ),
    (
        "OP_SELECT_CERTIFICATE",
        re.compile(
            r"\b(?:MiniApplet|AutoScript|ClienteFirma|clienteafirma)\s*\.\s*selectCertificate\s*\(",
            re.IGNORECASE,
        ),
    ),
    (
        "TRIPHASE_PRE_POST",
        re.compile(r"TriPhaseSignatureService|\bpreSign\b|\bpostSign\b|\btriphase\b", re.IGNORECASE),
    ),
    ("STORAGE_SERVICE", re.compile(r"StorageService|\bstservlet\b", re.IGNORECASE)),
    ("RETRIEVE_SERVICE", re.compile(r"RetrieveService|\brtservlet\b", re.IGNORECASE)),
    ("FORMAT_CADES", re.compile(r"\bCAdES\b", re.IGNORECASE)),
    ("FORMAT_PADES", re.compile(r"\bPAdES\b", re.IGNORECASE)),
    ("FORMAT_XADES", re.compile(r"\bXAdES\b", re.IGNORECASE)),
    ("FORMAT_FACTURAE", re.compile(r"\bFacturaE\b", re.IGNORECASE)),
    (
        "RSA_ALGORITHM",
        re.compile(r"\b(?:SHA(?:1|256|384|512)withRSA|NONEwithRSA)\b", re.IGNORECASE),
    ),
)


def decode_public_text(body: bytes, content_type: str) -> str:
    if content_type not in {
        "text/html",
        "application/xhtml+xml",
        "application/javascript",
        "text/javascript",
        "text/plain",
    }:
        return ""
    return body.decode("utf-8", errors="replace")


def fingerprint_text(text: str) -> tuple[str, ...]:
    return tuple(sorted(label for label, pattern in FINGERPRINT_PATTERNS if pattern.search(text)))


def protocol_families(fingerprints: Iterable[str]) -> tuple[str, ...]:
    values = frozenset(fingerprints)
    families: set[str] = set()
    if values & {"CLIENT_AUTOSCRIPT", "CLIENT_MINIAPPLET", "CLIENT_AFIRMA"}:
        families.add("MINIAPPLET_AUTOSCRIPT_CALLBACK")
    if values & {"AFIRMA_URI", "AUTOFIRMA_INTENT_URI"}:
        families.add("AFIRMA_CUSTOM_URI")
    if "TRIPHASE_PRE_POST" in values:
        families.add("TRIPHASE_PRE_POST")
    if values & {"STORAGE_SERVICE", "RETRIEVE_SERVICE"}:
        families.add("STORAGE_RETRIEVE")
    if values & {"FORMAT_CADES", "FORMAT_PADES", "FORMAT_XADES", "FORMAT_FACTURAE"}:
        families.add("LOCAL_SIGNATURE_FORMAT")
    if "OP_SELECT_CERTIFICATE" in values:
        families.add("CERTIFICATE_SELECTION")
    if values & {"OP_COSIGN", "OP_COUNTERSIGN"}:
        families.add("MULTISIGNATURE")
    return tuple(sorted(families))


def evidence_confidence(fingerprints: Iterable[str]) -> str:
    values = frozenset(fingerprints)
    if not values:
        return "NONE"
    client = bool(values & {"CLIENT_AUTOSCRIPT", "CLIENT_MINIAPPLET", "CLIENT_AFIRMA"})
    operation = bool(values & {"OP_SIGN", "OP_COSIGN", "OP_COUNTERSIGN", "OP_SELECT_CERTIFICATE"})
    contract = bool(
        values
        & {
            "TRIPHASE_PRE_POST",
            "STORAGE_SERVICE",
            "RETRIEVE_SERVICE",
            "FORMAT_CADES",
            "FORMAT_PADES",
            "FORMAT_XADES",
            "FORMAT_FACTURAE",
        }
    )
    return "LIKELY_FAMILY" if (client and operation) or (operation and contract) else "OBSERVED_STATIC"


def evidence_confidence_for_resources(resources: Iterable[Iterable[str]]) -> str:
    rank = {"NONE": 0, "OBSERVED_STATIC": 1, "LIKELY_FAMILY": 2}
    values = [evidence_confidence(fingerprints) for fingerprints in resources]
    return max(values, key=rank.__getitem__, default="NONE")


def endpoint_candidates(text: str, base_url: str) -> tuple[str, ...]:
    candidates: set[str] = set()
    literals = [match.group(1) for match in ENDPOINT_TOKEN_RE.finditer(text)]
    for match in SET_SERVLETS_RE.finditer(text):
        literals.extend((match.group(1), match.group(2)))
    for literal in literals:
        try:
            candidates.add(sanitize_redirect_url(urljoin(base_url, literal)))
        except InventoryError:
            continue
    return tuple(sorted(candidates))


def _same_origin_script_urls(html_text: str, base_url: str, approved_origin: str) -> tuple[str, ...]:
    parser = _ScriptParser()
    parser.feed(html_text)
    urls: set[str] = set()
    for raw in parser.script_sources:
        try:
            absolute = sanitize_url(urljoin(base_url, raw))
            if exact_origin(absolute) == approved_origin:
                urls.add(absolute)
        except InventoryError:
            continue
    return tuple(sorted(urls))


def _same_origin_js_imports(js_text: str, base_url: str, approved_origin: str) -> tuple[str, ...]:
    urls: set[str] = set()
    for match in JS_REFERENCE_RE.finditer(js_text):
        try:
            absolute = sanitize_url(urljoin(base_url, match.group(1)))
            if exact_origin(absolute) == approved_origin:
                urls.add(absolute)
        except InventoryError:
            continue
    return tuple(sorted(urls))


def _asset_record(result: FetchResult, fingerprints: Sequence[str]) -> dict[str, object]:
    return {
        "url": sanitize_redirect_url(result.final_url),
        "http_status": result.status,
        "content_type": result.content_type,
        "byte_count": len(result.body),
        "sha256": hashlib.sha256(result.body).hexdigest(),
        "etag": result.etag,
        "last_modified": result.last_modified,
        "request_count": result.request_count,
        "fingerprints": list(fingerprints),
    }


def scan_entry(seed: Seed, entry_url: str, fetcher: Fetcher, limits: ScanLimits) -> dict[str, object]:
    root = fetcher.fetch(
        entry_url,
        allowed_query_keys=seed.public_query_keys,
        allowed_redirect_origins=seed.allowed_redirect_origins,
    )
    if len(root.redirect_chain) > limits.max_redirects:
        raise InventoryError("fixture redirect limit exceeded")
    root_text = decode_public_text(root.body, root.content_type)
    approved_origin = exact_origin(root.final_url)
    root_fingerprints = fingerprint_text(root_text)
    resource_fingerprints: list[tuple[str, ...]] = [root_fingerprints]
    all_fingerprints = set(root_fingerprints)
    endpoints = set(endpoint_candidates(root_text, root.final_url))
    assets: list[dict[str, object]] = []
    skipped_assets = 0
    asset_attempts = 0
    total_request_count = root.request_count

    pending: list[tuple[str, int]] = []
    if limits.max_script_depth > 0:
        pending = [
            (url, 1)
            for url in _same_origin_script_urls(root_text, root.final_url, approved_origin)
        ]
    seen: set[str] = set()
    while pending:
        asset_url, depth = pending.pop(0)
        if asset_url in seen:
            continue
        seen.add(asset_url)
        if asset_attempts >= limits.max_assets:
            skipped_assets += 1 + len(pending)
            break
        asset_attempts += 1
        try:
            result = fetcher.fetch(asset_url, same_origin=approved_origin)
        except InventoryError:
            skipped_assets += 1
            continue
        if len(result.redirect_chain) > limits.max_redirects:
            skipped_assets += 1
            continue
        text = decode_public_text(result.body, result.content_type)
        fingerprints = fingerprint_text(text)
        resource_fingerprints.append(fingerprints)
        all_fingerprints.update(fingerprints)
        endpoints.update(endpoint_candidates(text, result.final_url))
        total_request_count += result.request_count
        assets.append(_asset_record(result, fingerprints))
        if depth < limits.max_script_depth:
            for imported in _same_origin_js_imports(text, result.final_url, approved_origin):
                if imported not in seen:
                    pending.append((imported, depth + 1))

    fingerprints_sorted = tuple(sorted(all_fingerprints))
    return {
        "output_schema_version": OUTPUT_SCHEMA_VERSION,
        "snapshot_id": "",  # Filled by scan_inventory.
        "snapshot_date": "",  # Filled by scan_inventory.
        "seed_id": seed.seed_id,
        "institution_name": seed.institution_name,
        "administrative_level": seed.administrative_level,
        "autonomous_community": seed.autonomous_community,
        "province_or_municipality": seed.province_or_municipality,
        "source_url": seed.source_url,
        "entry_url": entry_url,
        "final_url": sanitize_redirect_url(root.final_url),
        "origin": approved_origin,
        "redirect_chain": list(root.redirect_chain),
        "http_status": root.status,
        "content_type": root.content_type,
        "byte_count": len(root.body),
        "sha256": hashlib.sha256(root.body).hexdigest(),
        "etag": root.etag,
        "last_modified": root.last_modified,
        "request_count": total_request_count,
        "asset_attempt_count": asset_attempts,
        "fingerprints": list(fingerprints_sorted),
        "protocol_family_candidates": list(protocol_families(fingerprints_sorted)),
        "static_evidence_confidence": evidence_confidence_for_resources(resource_fingerprints),
        "endpoint_candidates": sorted(endpoints),
        "assets": sorted(assets, key=lambda item: str(item["url"])),
        "skipped_asset_count": skipped_assets,
        "compatibility_status": "BROWSE_ONLY",
        "promotion_blocker": "STATIC_DISCOVERY_REQUIRES_CONTRACT_REVIEW",
    }


def scan_inventory(inventory: InventoryInput, fetcher: Fetcher, limits: ScanLimits) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    for seed in inventory.seeds:
        for entry_url in seed.entry_urls:
            try:
                record = scan_entry(seed, entry_url, fetcher, limits)
            except InventoryError as exc:
                record = {
                    "output_schema_version": OUTPUT_SCHEMA_VERSION,
                    "snapshot_id": "",
                    "snapshot_date": "",
                    "seed_id": seed.seed_id,
                    "institution_name": seed.institution_name,
                    "administrative_level": seed.administrative_level,
                    "autonomous_community": seed.autonomous_community,
                    "province_or_municipality": seed.province_or_municipality,
                    "source_url": seed.source_url,
                    "entry_url": entry_url,
                    "final_url": None,
                    "origin": exact_origin(entry_url),
                    "redirect_chain": [],
                    "fetch_result": "INACCESSIBLE_CANDIDATE",
                    "error_class": type(exc).__name__,
                    "etag": None,
                    "last_modified": None,
                    "request_count": 0,
                    "asset_attempt_count": 0,
                    "fingerprints": [],
                    "protocol_family_candidates": [],
                    "static_evidence_confidence": "NONE",
                    "endpoint_candidates": [],
                    "assets": [],
                    "skipped_asset_count": 0,
                    "compatibility_status": "BROWSE_ONLY",
                    "promotion_blocker": "PUBLIC_FETCH_REQUIRES_REVIEW",
                }
            record["snapshot_id"] = inventory.snapshot_id
            record["snapshot_date"] = inventory.snapshot_date
            records.append(record)
    return sorted(records, key=lambda item: (str(item["seed_id"]), str(item["entry_url"])))


def _load_json(path: Path, max_bytes: int) -> object:
    if path.is_symlink():
        raise InventoryError("input must be a regular non-symlink file")
    resolved = path.resolve(strict=True)
    if not resolved.is_file():
        raise InventoryError("input must be a regular non-symlink file")
    if resolved.stat().st_size > max_bytes:
        raise InventoryError("input exceeded the configured size limit")
    try:
        return json.loads(resolved.read_text(encoding="utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise InventoryError("input is not valid UTF-8 JSON") from exc


def load_inventory(path: Path) -> InventoryInput:
    raw = _load_json(path, MAX_INPUT_BYTES)
    if not isinstance(raw, dict):
        raise InventoryError("inventory input root must be an object")
    _require_exact_keys(raw, ROOT_KEYS, "inventory root")
    if raw["schema_version"] != SCHEMA_VERSION:
        raise InventoryError("unsupported inventory schema_version")
    snapshot_id = _require_string(raw["snapshot_id"], "snapshot_id")
    if not SEED_ID_RE.fullmatch(snapshot_id):
        raise InventoryError("snapshot_id is malformed")
    snapshot_date = _require_string(raw["snapshot_date"], "snapshot_date")
    try:
        parsed_date = dt.date.fromisoformat(snapshot_date)
    except ValueError as exc:
        raise InventoryError("snapshot_date must be YYYY-MM-DD") from exc
    if parsed_date.isoformat() != snapshot_date:
        raise InventoryError("snapshot_date must be canonical YYYY-MM-DD")
    raw_seeds = raw["seeds"]
    if not isinstance(raw_seeds, list) or not raw_seeds:
        raise InventoryError("seeds must be a non-empty list")
    seeds: list[Seed] = []
    seen_ids: set[str] = set()
    for index, value in enumerate(raw_seeds):
        if not isinstance(value, dict):
            raise InventoryError(f"seed {index} must be an object")
        _require_exact_keys(value, SEED_KEYS, f"seed {index}")
        seed_id = _require_string(value["seed_id"], f"seed {index}.seed_id")
        if not SEED_ID_RE.fullmatch(seed_id):
            raise InventoryError(f"seed {index}.seed_id is malformed")
        if seed_id in seen_ids:
            raise InventoryError("duplicate seed_id")
        seen_ids.add(seed_id)
        administrative_level = _require_string(
            value["administrative_level"], f"seed {index}.administrative_level"
        )
        if administrative_level not in ADMINISTRATIVE_LEVELS:
            raise InventoryError(f"seed {index}.administrative_level is unknown")
        query_keys_raw = value["public_query_keys"]
        if not isinstance(query_keys_raw, list) or not all(
            isinstance(item, str) for item in query_keys_raw
        ):
            raise InventoryError(f"seed {index}.public_query_keys must be a string list")
        if len(set(query_keys_raw)) != len(query_keys_raw):
            raise InventoryError(f"seed {index}.public_query_keys contains duplicates")
        query_keys = frozenset(query_keys_raw)
        source_url = sanitize_url(
            _require_string(value["source_url"], f"seed {index}.source_url"), query_keys
        )
        entries_raw = value["entry_urls"]
        if not isinstance(entries_raw, list) or not entries_raw or not all(
            isinstance(item, str) for item in entries_raw
        ):
            raise InventoryError(f"seed {index}.entry_urls must be a non-empty string list")
        entry_urls = tuple(sanitize_url(item, query_keys) for item in entries_raw)
        if len(set(entry_urls)) != len(entry_urls):
            raise InventoryError(f"seed {index}.entry_urls contains duplicates")
        redirect_origins_raw = value["allowed_redirect_origins"]
        if not isinstance(redirect_origins_raw, list) or not all(
            isinstance(item, str) for item in redirect_origins_raw
        ):
            raise InventoryError(f"seed {index}.allowed_redirect_origins must be a string list")
        allowed_redirect_origins = frozenset(
            canonical_origin(item) for item in redirect_origins_raw
        )
        if len(allowed_redirect_origins) != len(redirect_origins_raw):
            raise InventoryError(f"seed {index}.allowed_redirect_origins contains duplicates")
        seeds.append(
            Seed(
                seed_id=seed_id,
                institution_name=_require_string(
                    value["institution_name"], f"seed {index}.institution_name"
                ),
                administrative_level=administrative_level,
                autonomous_community=_require_string(
                    value["autonomous_community"],
                    f"seed {index}.autonomous_community",
                    allow_empty=True,
                ),
                province_or_municipality=_require_string(
                    value["province_or_municipality"],
                    f"seed {index}.province_or_municipality",
                    allow_empty=True,
                ),
                source_url=source_url,
                entry_urls=entry_urls,
                public_query_keys=query_keys,
                allowed_redirect_origins=allowed_redirect_origins,
            )
        )
    return InventoryInput(snapshot_id, snapshot_date, tuple(sorted(seeds, key=lambda item: item.seed_id)))


def write_jsonl_atomic(path: Path, records: Sequence[Mapping[str, object]]) -> None:
    if path.exists() and path.is_symlink():
        raise InventoryError("output path must not be a symlink")
    parent = path.parent.resolve(strict=True)
    if not parent.is_dir():
        raise InventoryError("output parent must be a directory")
    if path.exists() and not path.is_file():
        raise InventoryError("output path must be a regular file")
    payload = "".join(
        json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
        for record in records
    ).encode("utf-8")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=parent)
    temporary_path = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_path, path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="strict local seed JSON")
    parser.add_argument("--output", required=True, type=Path, help="deterministic JSONL output")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--offline-fixtures", type=Path, help="strict local fixture manifest")
    source.add_argument("--live", action="store_true", help="perform bounded public HTTPS GETs")
    parser.add_argument("--max-body-bytes", type=int, default=DEFAULT_MAX_BODY_BYTES)
    parser.add_argument("--max-redirects", type=int, default=DEFAULT_MAX_REDIRECTS)
    parser.add_argument("--max-script-depth", type=int, default=DEFAULT_MAX_SCRIPT_DEPTH)
    parser.add_argument("--max-assets", type=int, default=DEFAULT_MAX_ASSETS)
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument(
        "--min-host-interval-seconds",
        type=float,
        default=DEFAULT_MIN_HOST_INTERVAL_SECONDS,
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        limits = ScanLimits(
            max_body_bytes=args.max_body_bytes,
            max_redirects=args.max_redirects,
            max_script_depth=args.max_script_depth,
            max_assets=args.max_assets,
            timeout_seconds=args.timeout_seconds,
            min_host_interval_seconds=args.min_host_interval_seconds,
        )
        limits.validate()
        inventory = load_inventory(args.input)
        fetcher: Fetcher
        if args.offline_fixtures is not None:
            fetcher = OfflineFixtureFetcher(args.offline_fixtures, limits)
        else:
            fetcher = LiveFetcher(limits)
        records = scan_inventory(inventory, fetcher, limits)
        write_jsonl_atomic(args.output, records)
    except (InventoryError, OSError) as exc:
        print(f"inventory scan failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
