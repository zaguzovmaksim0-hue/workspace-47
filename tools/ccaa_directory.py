#!/usr/bin/env python3
"""Enumerate the closed CCAA list from the official PAG directory.

This tool fetches only the HTTPS directory page.  It never requests a listed
territory target.  Legacy HTTP references are persisted as validated URL
components, not as executable URLs, until an HTTPS location is independently
resolved and reviewed.
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
from html.parser import HTMLParser
import os
from pathlib import Path
import stat
import sys
import unicodedata
from typing import Iterable, Mapping, Sequence
from urllib.parse import parse_qsl, urlsplit

import public_portal_inventory as inventory


SOURCE_URL = (
    "https://administracion.gob.es/pag_Home/atencionCiudadana/"
    "SedesElectronicas-y-Webs-Publicas/websPublicas/WP_CCAA.html"
)
OUTPUT_SCHEMA_VERSION = 1
SOURCE_ID = "D03"
MAX_SOURCE_BYTES = 2 * 1024 * 1024
EXPECTED_HTTPS_COUNT = 3
EXPECTED_HTTP_COUNT = 16
BALEARIC_TERRITORY = "Illes Balears"
BALEARIC_QUERY = (("lang", "es"),)
EXPECTED_HTTPS_TERRITORIES = frozenset(
    {"Extremadura", "Galicia", "Comunidad de Madrid"}
)

SOURCE_LABEL_ORDER = (
    "Andalucía",
    "Aragón",
    "Asturias, Principado de",
    "Balears, Illes",
    "Canarias",
    "Cantabria",
    "Castilla y León",
    "Castilla-La Mancha",
    "Cataluña",
    "Ciudad de Ceuta",
    "Ciudad de Melilla",
    "Comunitat Valenciana",
    "Extremadura",
    "Galicia",
    "Madrid, Comunidad de",
    "Murcia, Región de",
    "Navarra, Comunidad Foral de",
    "País Vasco",
    "Rioja, La",
)
SOURCE_LABEL_TO_TERRITORY = {
    "Andalucía": "Andalucía",
    "Aragón": "Aragón",
    "Asturias, Principado de": "Principado de Asturias",
    "Balears, Illes": "Illes Balears",
    "Canarias": "Canarias",
    "Cantabria": "Cantabria",
    "Castilla y León": "Castilla y León",
    "Castilla-La Mancha": "Castilla-La Mancha",
    "Cataluña": "Cataluña",
    "Ciudad de Ceuta": "Ciudad Autónoma de Ceuta",
    "Ciudad de Melilla": "Ciudad Autónoma de Melilla",
    "Comunitat Valenciana": "Comunitat Valenciana",
    "Extremadura": "Extremadura",
    "Galicia": "Galicia",
    "Madrid, Comunidad de": "Comunidad de Madrid",
    "Murcia, Región de": "Región de Murcia",
    "Navarra, Comunidad Foral de": "Comunidad Foral de Navarra",
    "País Vasco": "País Vasco",
    "Rioja, La": "La Rioja",
}
EXPECTED_SOURCE_LABELS = frozenset(SOURCE_LABEL_ORDER)


class CcaaDirectoryError(inventory.InventoryError):
    """The official directory no longer matches the reviewed closed contract."""


@dataclasses.dataclass(frozen=True)
class DirectoryAnchor:
    source_label: str
    href: str

    @property
    def territory(self) -> str:
        return SOURCE_LABEL_TO_TERRITORY[self.source_label]


def _normalize_text(value: str) -> str:
    normalized = " ".join(unicodedata.normalize("NFKC", value).split())
    if not normalized:
        raise CcaaDirectoryError("territory label is blank")
    if len(normalized) > 128:
        raise CcaaDirectoryError("territory label is unexpectedly long")
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in normalized):
        raise CcaaDirectoryError("territory label contains a control character")
    return normalized


def _one_attribute(
    attributes: Sequence[tuple[str, str | None]],
    name: str,
    *,
    required: bool = False,
) -> str | None:
    values = [value for key, value in attributes if key.casefold() == name.casefold()]
    if len(values) > 1:
        raise CcaaDirectoryError(f"duplicate {name!r} attribute")
    if not values:
        if required:
            raise CcaaDirectoryError(f"missing {name!r} attribute")
        return None
    value = values[0]
    if value is None or not value.strip():
        if required:
            raise CcaaDirectoryError(f"blank {name!r} attribute")
        return None
    return value.strip()


class _CcaaAnchorParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._target_div_nesting = 0
        self._current_href: str | None = None
        self._current_text: list[str] = []
        self.target_div_count = 0
        self.anchors: list[DirectoryAnchor] = []

    @property
    def _inside_target(self) -> bool:
        return self._target_div_nesting > 0

    def handle_starttag(
        self,
        tag: str,
        attrs: list[tuple[str, str | None]],
    ) -> None:
        lowered = tag.casefold()
        if lowered == "div":
            element_id = _one_attribute(attrs, "id")
            if element_id == "pie_comunidad":
                self.target_div_count += 1
                if self._inside_target:
                    raise CcaaDirectoryError("nested div#pie_comunidad is ambiguous")
                self._target_div_nesting = 1
            elif self._inside_target:
                self._target_div_nesting += 1
            return
        if lowered == "a" and self._inside_target:
            if self._current_href is not None:
                raise CcaaDirectoryError("nested anchors inside div#pie_comunidad")
            self._current_href = _one_attribute(attrs, "href", required=True)
            self._current_text = []

    def handle_startendtag(
        self,
        tag: str,
        attrs: list[tuple[str, str | None]],
    ) -> None:
        if tag.casefold() == "div":
            element_id = _one_attribute(attrs, "id")
            if element_id == "pie_comunidad":
                self.target_div_count += 1
                raise CcaaDirectoryError("empty div#pie_comunidad is invalid")
        elif tag.casefold() == "a" and self._inside_target:
            raise CcaaDirectoryError("self-closing territory anchor is invalid")

    def handle_endtag(self, tag: str) -> None:
        lowered = tag.casefold()
        if lowered == "a" and self._inside_target and self._current_href is not None:
            source_label = _normalize_text("".join(self._current_text))
            if not source_label:
                raise CcaaDirectoryError("territory anchor text is blank")
            self.anchors.append(DirectoryAnchor(source_label, self._current_href))
            self._current_href = None
            self._current_text = []
            return
        if lowered == "div":
            if self._inside_target:
                self._target_div_nesting -= 1
            if self._target_div_nesting == 0:
                if self._current_href is not None:
                    raise CcaaDirectoryError("unclosed territory anchor")

    def handle_data(self, data: str) -> None:
        if self._inside_target and self._current_href is not None:
            self._current_text.append(data)

    def finish(self) -> tuple[DirectoryAnchor, ...]:
        self.close()
        if self.target_div_count != 1:
            raise CcaaDirectoryError(
                f"expected exactly one div#pie_comunidad, found {self.target_div_count}"
            )
        if self._target_div_nesting or self._current_href is not None:
            raise CcaaDirectoryError("unterminated div#pie_comunidad")
        return tuple(self.anchors)


def parse_directory_html(html: str) -> tuple[DirectoryAnchor, ...]:
    parser = _CcaaAnchorParser()
    try:
        parser.feed(html)
        anchors = parser.finish()
    except CcaaDirectoryError:
        raise
    except Exception as exc:
        raise CcaaDirectoryError("directory HTML could not be parsed safely") from exc

    source_labels = [anchor.source_label for anchor in anchors]
    duplicates = sorted(
        {label for label in source_labels if source_labels.count(label) > 1}
    )
    unknown = sorted(set(source_labels) - EXPECTED_SOURCE_LABELS)
    missing = sorted(EXPECTED_SOURCE_LABELS - set(source_labels))
    if duplicates or unknown or missing or len(anchors) != len(SOURCE_LABEL_ORDER):
        raise CcaaDirectoryError(
            "closed territory allowlist drift; "
            f"missing={missing}, unknown_count={len(unknown)}, "
            f"duplicate_count={len(duplicates)}, "
            f"anchor_count={len(anchors)}"
        )
    by_source_label = {anchor.source_label: anchor for anchor in anchors}
    return tuple(by_source_label[label] for label in SOURCE_LABEL_ORDER)


def _validate_target_parts(
    territory: str,
    href: str,
) -> tuple[str, str, int | None, str, tuple[tuple[str, str], ...]]:
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in href):
        raise CcaaDirectoryError(f"{territory}: target contains a control character")
    try:
        parts = urlsplit(href)
        scheme = parts.scheme.casefold()
        username = parts.username
        password = parts.password
        raw_host = parts.hostname or ""
        port = parts.port
    except (UnicodeError, ValueError) as exc:
        raise CcaaDirectoryError(f"{territory}: target authority is invalid") from exc
    if scheme not in {"http", "https"}:
        raise CcaaDirectoryError(f"{territory}: target scheme is not HTTP(S)")
    if username is not None or password is not None:
        raise CcaaDirectoryError(f"{territory}: target credentials are forbidden")
    if parts.fragment:
        raise CcaaDirectoryError(f"{territory}: target fragment is forbidden")
    try:
        host = inventory._canonical_host(raw_host)
    except (inventory.InventoryError, ValueError) as exc:
        raise CcaaDirectoryError(f"{territory}: target authority is invalid") from exc
    if port is not None and not 1 <= port <= 65535:
        raise CcaaDirectoryError(f"{territory}: target port is invalid")
    if scheme == "https" and port == 443:
        port = None
    if scheme == "http" and port == 80:
        port = None

    path = parts.path or "/"
    if "\\" in path or "//" in path or ";" in path:
        raise CcaaDirectoryError(f"{territory}: target path is ambiguous")
    try:
        decoded_path = inventory._validate_decoded_component(path, "target path")
        inventory._validate_decoded_component(parts.query, "target query")
    except inventory.InventoryError as exc:
        raise CcaaDirectoryError(f"{territory}: target encoding is invalid") from exc
    if "%2f" in path.casefold() or "%5c" in path.casefold():
        raise CcaaDirectoryError(f"{territory}: target path contains encoded separators")
    if "\\" in decoded_path or "//" in decoded_path or ";" in decoded_path:
        raise CcaaDirectoryError(f"{territory}: decoded target path is ambiguous")
    if any(segment in {".", ".."} for segment in decoded_path.split("/")):
        raise CcaaDirectoryError(f"{territory}: target path contains a dot segment")

    try:
        query_pairs = tuple(parse_qsl(parts.query, keep_blank_values=True, strict_parsing=True))
    except ValueError as exc:
        raise CcaaDirectoryError(f"{territory}: target query is malformed") from exc
    expected_query = BALEARIC_QUERY if territory == BALEARIC_TERRITORY else ()
    if query_pairs != expected_query:
        raise CcaaDirectoryError(f"{territory}: public query does not match reviewed selector")
    return scheme, host, port, path, query_pairs


def _target_record(anchor: DirectoryAnchor) -> dict[str, object]:
    scheme, host, port, path, query_pairs = _validate_target_parts(
        anchor.territory,
        anchor.href,
    )
    components: dict[str, object] = {
        "scheme": scheme,
        "host": host,
        "port": port,
        "path": path,
        "public_query_pairs": [list(pair) for pair in query_pairs],
    }
    if scheme == "http":
        return {
            "target": {
                "kind": "HTTP_REFERENCE_COMPONENTS",
                **components,
            },
            "target_status": "HTTPS_RESOLUTION_REQUIRED",
            "candidate_seed_eligible": False,
            "target_fetch_performed": False,
        }

    allowed_query_keys: Iterable[str] = ("lang",) if query_pairs else ()
    try:
        safe_url = inventory.sanitize_url(
            anchor.href,
            allowed_query_keys,
            reject_unlisted_query=True,
        )
        origin = inventory.exact_origin(safe_url)
    except inventory.InventoryError as exc:
        raise CcaaDirectoryError(f"{anchor.territory}: HTTPS target is unsafe") from exc
    return {
        "target": {
            "kind": "HTTPS_URL",
            **components,
            "url": safe_url,
            "origin": origin,
        },
        "target_status": "HTTPS_REFERENCE_VALIDATED",
        "candidate_seed_eligible": True,
        "target_fetch_performed": False,
    }


def _validate_snapshot_date(value: str) -> str:
    try:
        parsed = dt.date.fromisoformat(value)
    except ValueError as exc:
        raise CcaaDirectoryError("snapshot date must be canonical YYYY-MM-DD") from exc
    if parsed.isoformat() != value:
        raise CcaaDirectoryError("snapshot date must be canonical YYYY-MM-DD")
    return value


def enumerate_directory(
    fetcher: inventory.Fetcher,
    snapshot_date: str,
) -> list[dict[str, object]]:
    snapshot_date = _validate_snapshot_date(snapshot_date)
    result = fetcher.fetch(
        SOURCE_URL,
        allowed_query_keys=(),
        allowed_redirect_origins=(),
    )
    if result.requested_url != SOURCE_URL or result.final_url != SOURCE_URL:
        raise CcaaDirectoryError("source URL changed or redirected")
    if result.redirect_chain or result.request_count != 1:
        raise CcaaDirectoryError("source fetch must be exactly one non-redirected GET")
    if result.status != 200:
        raise CcaaDirectoryError(f"source returned HTTP status {result.status}")
    if result.content_type not in {"text/html", "application/xhtml+xml"}:
        raise CcaaDirectoryError("source is not HTML")
    if len(result.body) > MAX_SOURCE_BYTES:
        raise CcaaDirectoryError("source body exceeded the fixed limit")
    try:
        html = result.body.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise CcaaDirectoryError("source body is not valid UTF-8") from exc

    anchors = parse_directory_html(html)
    target_records = [_target_record(anchor) for anchor in anchors]
    https_count = sum(
        record["target"]["scheme"] == "https"  # type: ignore[index]
        for record in target_records
    )
    http_count = len(target_records) - https_count
    actual_https_territories = {
        anchor.territory
        for anchor, record in zip(anchors, target_records, strict=True)
        if record["target"]["scheme"] == "https"  # type: ignore[index]
    }
    if (
        (https_count, http_count) != (EXPECTED_HTTPS_COUNT, EXPECTED_HTTP_COUNT)
        or actual_https_territories != EXPECTED_HTTPS_TERRITORIES
    ):
        raise CcaaDirectoryError(
            "reviewed scheme baseline drift; "
            f"expected=({EXPECTED_HTTPS_COUNT} HTTPS, {EXPECTED_HTTP_COUNT} HTTP), "
            f"actual=({https_count} HTTPS, {http_count} HTTP), "
            f"unexpected_https={sorted(actual_https_territories - EXPECTED_HTTPS_TERRITORIES)}, "
            f"missing_https={sorted(EXPECTED_HTTPS_TERRITORIES - actual_https_territories)}"
        )

    source_hash = hashlib.sha256(result.body).hexdigest()
    snapshot_id = f"ccaa-directory-{snapshot_date}"
    records: list[dict[str, object]] = []
    for anchor, target in zip(anchors, target_records, strict=True):
        records.append(
            {
                "output_schema_version": OUTPUT_SCHEMA_VERSION,
                "snapshot_id": snapshot_id,
                "snapshot_date": snapshot_date,
                "administrative_level": "AUTONOMICO",
                "source_label": anchor.source_label,
                "territory": anchor.territory,
                "source_url": SOURCE_URL,
                "source_final_url": result.final_url,
                "source_id": SOURCE_ID,
                "source_http_status": result.status,
                "source_content_type": result.content_type,
                "source_etag": result.etag,
                "source_last_modified": result.last_modified,
                "source_byte_count": len(result.body),
                "source_sha256": source_hash,
                "source_request_count": 1,
                **target,
                "discovery_state": "CANDIDATE",
                "compatibility_status": "BROWSE_ONLY",
                "protocol_family": "NO_VERIFICADO",
                "contract_claims": [],
                "promotion_blocker": "PUBLIC_TARGET_CONTRACT_NOT_REVIEWED",
            }
        )
    return records


def read_fixture_secure(path: Path, max_bytes: int = MAX_SOURCE_BYTES) -> bytes:
    if not 1 <= max_bytes <= MAX_SOURCE_BYTES:
        raise CcaaDirectoryError("fixture size limit is invalid")
    flags = os.O_RDONLY
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise CcaaDirectoryError("fixture could not be opened safely") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise CcaaDirectoryError("fixture must be one regular, singly-linked file")
        if metadata.st_size > max_bytes:
            raise CcaaDirectoryError("fixture exceeded the configured size limit")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(64 * 1024, max_bytes + 1 - total))
            if not chunk:
                break
            chunks.append(chunk)
            total += len(chunk)
            if total > max_bytes:
                raise CcaaDirectoryError("fixture exceeded the configured size limit")
        return b"".join(chunks)
    finally:
        os.close(descriptor)


class _FixtureFetcher:
    def __init__(self, body: bytes) -> None:
        self._body = body
        self.call_count = 0

    def fetch(
        self,
        url: str,
        *,
        same_origin: str | None = None,
        allowed_query_keys: Iterable[str] = (),
        allowed_redirect_origins: Iterable[str] = (),
    ) -> inventory.FetchResult:
        self.call_count += 1
        if (
            url != SOURCE_URL
            or same_origin is not None
            or tuple(allowed_query_keys)
            or tuple(allowed_redirect_origins)
        ):
            raise CcaaDirectoryError("offline fixture permits only the exact source request")
        if self.call_count != 1:
            raise CcaaDirectoryError("offline fixture source was requested more than once")
        return inventory.FetchResult(
            requested_url=SOURCE_URL,
            final_url=SOURCE_URL,
            redirect_chain=(),
            status=200,
            content_type="text/html",
            body=self._body,
            request_count=1,
        )


def write_jsonl_atomic(path: Path, records: Sequence[Mapping[str, object]]) -> None:
    inventory.write_jsonl_atomic(path, records)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--snapshot-date", required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--fixture-html", type=Path)
    source.add_argument("--live", action="store_true")
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=inventory.DEFAULT_TIMEOUT_SECONDS,
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.fixture_html is not None:
            fetcher: inventory.Fetcher = _FixtureFetcher(read_fixture_secure(args.fixture_html))
        else:
            limits = inventory.ScanLimits(
                max_body_bytes=MAX_SOURCE_BYTES,
                max_redirects=0,
                max_script_depth=0,
                max_assets=0,
                timeout_seconds=args.timeout_seconds,
                min_host_interval_seconds=0.0,
            )
            fetcher = inventory.LiveFetcher(limits)
        records = enumerate_directory(fetcher, args.snapshot_date)
        write_jsonl_atomic(args.output, records)
    except (CcaaDirectoryError, inventory.InventoryError, OSError) as exc:
        print(f"CCAA directory enumeration failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
