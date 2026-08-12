#!/usr/bin/env python3
"""Enumerate the official AGE electronic-office directory without following its links.

The output is candidate inventory evidence only.  This command performs one bounded
public GET of the official directory, never opens an institution link, and never
promotes a discovered surface above BROWSE_ONLY.
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
from html.parser import HTMLParser
import os
from pathlib import Path
import re
import stat
import sys
import unicodedata
from typing import Mapping, Sequence
from urllib.parse import parse_qsl, urljoin, urlsplit, urlunsplit

from public_portal_inventory import (
    DEFAULT_MAX_BODY_BYTES,
    DEFAULT_TIMEOUT_SECONDS,
    FetchResult,
    InventoryError,
    LiveFetcher,
    ScanLimits,
    canonical_origin,
    exact_origin,
    sanitize_redirect_url,
    sanitize_url,
    write_jsonl_atomic,
)


OUTPUT_SCHEMA_VERSION = 1
SOURCE_ID = "D11"
SOURCE_URL = "https://sede.administracion.gob.es/sedes-electronicas"
SOURCE_ORIGIN = "https://sede.administracion.gob.es"
MAX_FIXTURE_BYTES = 4 * 1024 * 1024
EXPECTED_MINISTRY_COUNT = 22
EXPECTED_CARD_COUNT = 81
EXPECTED_HREF_OCCURRENCE_COUNT = 84
EXPECTED_UNIQUE_ENTRY_COUNT = 79


def _normalize_text(value: str, context: str) -> str:
    normalized = " ".join(unicodedata.normalize("NFKC", value).split())
    if not normalized:
        raise InventoryError(f"{context} must not be blank")
    if len(normalized) > 512:
        raise InventoryError(f"{context} is unexpectedly long")
    if any(ord(char) < 0x20 or ord(char) == 0x7F for char in normalized):
        raise InventoryError(f"{context} contains a control character")
    return normalized


def _text_key(value: str) -> str:
    return _normalize_text(value, "text key").casefold()


CIEMAT_NAME_KEY = _text_key(
    "Centro de Investigaciones Energéticas, Medioambientales y Tecnológicas (CIEMAT)"
)
SAFE_ORIGIN_FALLBACKS: Mapping[str, str] = {
    CIEMAT_NAME_KEY: "https://sede.ciemat.gob.es",
}


@dataclasses.dataclass(frozen=True)
class Card:
    ministry: str
    institution_name: str
    hrefs: tuple[str, ...]


@dataclasses.dataclass(frozen=True)
class DirectoryEntry:
    institution_name: str
    origin: str
    entry_url: str
    ministries: tuple[str, ...]
    source_occurrence_count: int
    href_occurrence_count: int
    url_sanitization: str


@dataclasses.dataclass(frozen=True)
class ParsedDirectory:
    entries: tuple[DirectoryEntry, ...]
    ministry_count: int
    card_count: int
    href_occurrence_count: int


class _AgeDirectoryParser(HTMLParser):
    """Read only institution cards nested in the directory accordion."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._div_depth = 0
        self._accordion_depth: int | None = None
        self._item_depth: int | None = None
        self._item_card_count = 0
        self._card_depth: int | None = None
        self._button_open = False
        self._button_text: list[str] = []
        self._card_text: list[str] = []
        self._card_hrefs: list[str] = []
        self._ministry: str | None = None
        self.cards: list[Card] = []
        self.accordion_count = 0
        self.item_count = 0

    @staticmethod
    def _classes(attrs: Mapping[str, str | None]) -> frozenset[str]:
        return frozenset((attrs.get("class") or "").split())

    def handle_starttag(self, tag: str, attrs_list: list[tuple[str, str | None]]) -> None:
        attrs = dict(attrs_list)
        classes = self._classes(attrs)
        if tag == "div":
            self._div_depth += 1
            if {"cmp-accordion", "list-container"}.issubset(classes):
                if self._accordion_depth is not None or self.accordion_count != 0:
                    raise InventoryError("multiple or nested AGE directory accordions")
                self._accordion_depth = self._div_depth
                self.accordion_count += 1
            elif (
                self._accordion_depth is not None
                and {"cmp-accordion__item", "pagination-sgad"}.issubset(classes)
            ):
                if self._item_depth is not None:
                    raise InventoryError("nested AGE directory items are not supported")
                self._item_depth = self._div_depth
                self._ministry = None
                self._item_card_count = 0
                self.item_count += 1
            elif self._item_depth is not None and "cmp-text" in classes:
                if self._card_depth is not None:
                    raise InventoryError("nested AGE directory cards are not supported")
                self._card_depth = self._div_depth
                self._card_text = []
                self._card_hrefs = []
        elif (
            tag == "button"
            and self._item_depth is not None
            and "cmp-accordion__button" in classes
        ):
            if self._button_open:
                raise InventoryError("nested AGE directory buttons are not supported")
            self._button_open = True
            self._button_text = []
        elif tag == "a" and self._card_depth is not None:
            href = attrs.get("href")
            if href is not None:
                self._card_hrefs.append(href)

    def handle_data(self, data: str) -> None:
        if self._button_open:
            self._button_text.append(data)
        if self._card_depth is not None:
            self._card_text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag == "button" and self._button_open:
            ministry = _normalize_text("".join(self._button_text), "ministry name")
            if self._ministry is not None and self._ministry != ministry:
                raise InventoryError("one AGE directory item declared multiple ministries")
            self._ministry = ministry
            self._button_open = False
            self._button_text = []
            return
        if tag != "div":
            return
        if self._card_depth == self._div_depth:
            if self._ministry is None:
                raise InventoryError("AGE directory card appeared before its ministry label")
            institution = _normalize_text("".join(self._card_text), "institution name")
            if not self._card_hrefs:
                raise InventoryError("AGE directory institution card has no link")
            self.cards.append(Card(self._ministry, institution, tuple(self._card_hrefs)))
            self._item_card_count += 1
            self._card_depth = None
            self._card_text = []
            self._card_hrefs = []
        if self._item_depth == self._div_depth:
            if self._ministry is None:
                raise InventoryError("AGE directory item has no ministry label")
            if self._item_card_count == 0:
                raise InventoryError("AGE directory item has no institution cards")
            self._item_depth = None
            self._ministry = None
            self._item_card_count = 0
        if self._accordion_depth == self._div_depth:
            self._accordion_depth = None
        self._div_depth -= 1
        if self._div_depth < 0:
            raise InventoryError("malformed AGE directory div nesting")

    def close(self) -> None:
        super().close()
        if (
            self._accordion_depth is not None
            or self._item_depth is not None
            or self._card_depth is not None
            or self._button_open
        ):
            raise InventoryError("truncated AGE directory HTML")


def _fallback_origin(raw_url: str, institution_name: str) -> str:
    expected = SAFE_ORIGIN_FALLBACKS.get(_text_key(institution_name))
    if expected is None:
        raise InventoryError("unexpected unsafe AGE directory URL")
    parts = urlsplit(raw_url)
    candidate = urlunsplit((parts.scheme, parts.netloc, "", "", ""))
    if canonical_origin(candidate) != expected:
        raise InventoryError("AGE directory origin fallback did not match its allowlist")
    if parts.fragment or "%" in parts.path:
        raise InventoryError("AGE directory origin fallback URL is malformed")
    if not re.fullmatch(
        r"/[A-Za-z0-9._~/-]*;jsessionid=[A-Za-z0-9._~-]{1,256}",
        parts.path,
        flags=re.IGNORECASE,
    ):
        raise InventoryError("AGE directory origin fallback URL is not expected")
    try:
        query = parse_qsl(parts.query, keep_blank_values=False, strict_parsing=True)
    except ValueError as exc:
        raise InventoryError("AGE directory origin fallback query is malformed") from exc
    if not query or len(query) != len({key for key, _ in query}):
        raise InventoryError("AGE directory origin fallback query is not expected")
    if {key for key, _ in query} != {"IDM", "NM"} or any(
        not re.fullmatch(r"[A-Za-z0-9._~-]{1,128}", value) for _, value in query
    ):
        raise InventoryError("AGE directory origin fallback query is not expected")
    return f"{expected}/"


def _sanitize_card_href(href: str, institution_name: str) -> tuple[str, str]:
    resolved = urljoin(SOURCE_URL, href)
    parts = urlsplit(resolved)
    if parts.query or parts.fragment or ";" in parts.path:
        return _fallback_origin(resolved, institution_name), "ORIGIN_FALLBACK"
    return sanitize_url(resolved, ()), "EXACT"


def parse_directory_html(body: bytes) -> ParsedDirectory:
    try:
        html = body.decode("utf-8", errors="strict")
    except UnicodeError as exc:
        raise InventoryError("AGE directory is not valid UTF-8") from exc
    parser = _AgeDirectoryParser()
    try:
        parser.feed(html)
        parser.close()
    except InventoryError:
        raise
    except Exception as exc:
        raise InventoryError("AGE directory HTML could not be parsed") from exc
    if parser.accordion_count != 1 or parser.item_count == 0 or not parser.cards:
        raise InventoryError("AGE directory accordion was not found")

    grouped: dict[tuple[str, str], dict[str, object]] = {}
    href_occurrence_count = 0
    for card in parser.cards:
        sanitized = [_sanitize_card_href(href, card.institution_name) for href in card.hrefs]
        href_occurrence_count += len(sanitized)
        origins = {exact_origin(url) for url, _ in sanitized}
        if len(origins) != 1:
            raise InventoryError("one AGE directory card linked multiple origins")
        origin = next(iter(origins))
        urls = sorted({url for url, _ in sanitized}, key=lambda url: (len(url), url))
        if len(urls) != 1:
            raise InventoryError("one AGE directory card published multiple entry URLs")
        modes = {mode for _, mode in sanitized}
        mode = "ORIGIN_FALLBACK" if "ORIGIN_FALLBACK" in modes else "EXACT"
        key = (_text_key(card.institution_name), origin)
        current = grouped.get(key)
        if current is None:
            grouped[key] = {
                "institution_name": card.institution_name,
                "origin": origin,
                "entry_url": urls[0],
                "ministries": {card.ministry},
                "source_occurrence_count": 1,
                "href_occurrence_count": len(sanitized),
                "url_sanitization": mode,
            }
        else:
            if current["entry_url"] != urls[0]:
                raise InventoryError(
                    "one AGE institution and origin published multiple entry URLs"
                )
            current["ministries"].add(card.ministry)  # type: ignore[union-attr]
            current["source_occurrence_count"] = int(current["source_occurrence_count"]) + 1
            current["href_occurrence_count"] = int(current["href_occurrence_count"]) + len(
                sanitized
            )
            if mode == "ORIGIN_FALLBACK":
                current["url_sanitization"] = mode

    entries: list[DirectoryEntry] = []
    for value in grouped.values():
        entries.append(
            DirectoryEntry(
                institution_name=str(value["institution_name"]),
                origin=str(value["origin"]),
                entry_url=str(value["entry_url"]),
                ministries=tuple(sorted(value["ministries"])),
                source_occurrence_count=int(value["source_occurrence_count"]),
                href_occurrence_count=int(value["href_occurrence_count"]),
                url_sanitization=str(value["url_sanitization"]),
            )
        )
    entries.sort(key=lambda entry: (_text_key(entry.institution_name), entry.origin))
    ministry_count = len({card.ministry for card in parser.cards})
    return ParsedDirectory(
        entries=tuple(entries),
        ministry_count=ministry_count,
        card_count=len(parser.cards),
        href_occurrence_count=href_occurrence_count,
    )


def build_records(
    parsed: ParsedDirectory,
    fetched: FetchResult,
    snapshot_date: str,
) -> list[dict[str, object]]:
    source_final_url = sanitize_redirect_url(fetched.final_url)
    source_sha256 = hashlib.sha256(fetched.body).hexdigest()
    snapshot_id = f"age-sede-directory-{snapshot_date}"
    records: list[dict[str, object]] = []
    for entry in parsed.entries:
        records.append(
            {
                "output_schema_version": OUTPUT_SCHEMA_VERSION,
                "snapshot_id": snapshot_id,
                "snapshot_date": snapshot_date,
                "source_id": SOURCE_ID,
                "source_url": SOURCE_URL,
                "source_final_url": source_final_url,
                "source_http_status": fetched.status,
                "source_content_type": fetched.content_type,
                "source_byte_count": len(fetched.body),
                "source_sha256": source_sha256,
                "source_etag": fetched.etag,
                "source_last_modified": fetched.last_modified,
                "institution_name": entry.institution_name,
                "origin": entry.origin,
                "entry_url": entry.entry_url,
                "ministries": list(entry.ministries),
                "source_occurrence_count": entry.source_occurrence_count,
                "href_occurrence_count": entry.href_occurrence_count,
                "url_sanitization": entry.url_sanitization,
                "discovery_state": "DISCOVERED",
                "compatibility_status": "BROWSE_ONLY",
                "promotion_blocker": "DIRECTORY_LISTING_HAS_NO_TECHNICAL_CONTRACT",
            }
        )
    return records


def validate_reviewed_live_baseline(parsed: ParsedDirectory) -> None:
    observed = (
        parsed.ministry_count,
        parsed.card_count,
        parsed.href_occurrence_count,
        len(parsed.entries),
    )
    expected = (
        EXPECTED_MINISTRY_COUNT,
        EXPECTED_CARD_COUNT,
        EXPECTED_HREF_OCCURRENCE_COUNT,
        EXPECTED_UNIQUE_ENTRY_COUNT,
    )
    if observed != expected:
        raise InventoryError("AGE directory structure changed; reviewed baseline required")


def fetch_live_directory(max_body_bytes: int, timeout_seconds: float) -> FetchResult:
    limits = ScanLimits(
        max_body_bytes=max_body_bytes,
        max_redirects=0,
        max_script_depth=0,
        max_assets=0,
        timeout_seconds=timeout_seconds,
        min_host_interval_seconds=0.5,
    )
    limits.validate()
    return LiveFetcher(limits).fetch(SOURCE_URL, same_origin=SOURCE_ORIGIN)


def _load_fixture(path: Path) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise InventoryError("fixture must be a regular single-link file") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise InventoryError("fixture must be a regular single-link file")
        if metadata.st_size > MAX_FIXTURE_BYTES:
            raise InventoryError("fixture exceeded the configured size limit")
        chunks: list[bytes] = []
        total = 0
        while total <= MAX_FIXTURE_BYTES:
            chunk = os.read(descriptor, min(64 * 1024, MAX_FIXTURE_BYTES + 1 - total))
            if not chunk:
                break
            chunks.append(chunk)
            total += len(chunk)
        if total > MAX_FIXTURE_BYTES:
            raise InventoryError("fixture exceeded the configured size limit")
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _snapshot_date(value: str) -> str:
    try:
        parsed = dt.date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("snapshot date must be YYYY-MM-DD") from exc
    if parsed.isoformat() != value:
        raise argparse.ArgumentTypeError("snapshot date must be canonical YYYY-MM-DD")
    return value


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path, help="deterministic JSONL output")
    parser.add_argument("--snapshot-date", required=True, type=_snapshot_date)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--live", action="store_true", help="perform one bounded public HTTPS GET")
    source.add_argument("--html-fixture", type=Path, help="parse a local HTML fixture")
    parser.add_argument("--max-body-bytes", type=int, default=DEFAULT_MAX_BODY_BYTES)
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.html_fixture is not None:
            body = _load_fixture(args.html_fixture)
            fetched = FetchResult(
                requested_url=SOURCE_URL,
                final_url=SOURCE_URL,
                redirect_chain=(),
                status=200,
                content_type="text/html; charset=utf-8",
                body=body,
            )
        else:
            fetched = fetch_live_directory(args.max_body_bytes, args.timeout_seconds)
        if fetched.status != 200:
            raise InventoryError("AGE directory did not return HTTP 200")
        if not fetched.content_type.casefold().startswith("text/html"):
            raise InventoryError("AGE directory did not return HTML")
        parsed = parse_directory_html(fetched.body)
        if args.live:
            validate_reviewed_live_baseline(parsed)
        records = build_records(parsed, fetched, args.snapshot_date)
        write_jsonl_atomic(args.output, records)
        print(
            "AGE directory snapshot: "
            f"ministries={parsed.ministry_count} cards={parsed.card_count} "
            f"hrefs={parsed.href_occurrence_count} unique_entries={len(parsed.entries)}"
        )
    except (InventoryError, OSError) as exc:
        print(f"AGE directory enumeration failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
