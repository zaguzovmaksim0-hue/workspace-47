#!/usr/bin/env python3
"""Enumerate three closed local-government directories published by PAG.

Only the selected official HTTPS index is requested.  Listed targets are
validated and serialized as references; they are never fetched.  Legacy HTTP
and quarantined references are deliberately non-executable components.
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


OUTPUT_SCHEMA_VERSION = 1
MAX_SOURCE_BYTES = 2 * 1024 * 1024
SOURCE_PREFIX = (
    "https://administracion.gob.es/pag_Home/atencionCiudadana/"
    "SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/"
)

DIPUTACIONES_LABELS = (
    "Alacant/Alicante", "Araba/Álava", "Albacete", "Almería", "Ávila",
    "Badajoz", "Barcelona", "Burgos", "Cáceres", "Cádiz",
    "Castelló/Castellón", "Ciudad Real", "Córdoba", "Coruña, A", "Cuenca",
    "Girona/Gerona", "Granada", "Guadalajara", "Gipuzkoa/Guipúzcoa",
    "Huelva", "Huesca", "Jaén", "León", "Lleida/Lerida", "Lugo", "Málaga",
    "Ourense/Orense", "Palencia", "Pontevedra", "Salamanca", "Segovia",
    "Sevilla", "Soria", "Tarragona", "Teruel", "Toledo",
    "València/Valencia", "Valladolid", "Bizkaia/Vizcaya", "Zamora", "Zaragoza",
)
DIPUTACIONES_HTTPS = frozenset(
    {"Araba/Álava", "León", "Lugo", "Ourense/Orense", "Palencia", "Tarragona", "Teruel"}
)

INSULAR_LABELS = (
    "Consell Insular de Menorca", "Consell de Mallorca",
    "Consell Insular de d’Eivissa", "Consell Insular de Formentera",
    "Cabildo Insular de El Hierro", "Cabildo Insular de Tenerife",
    "Cabildo Insular de La Palma", "Cabildo Insular de La Gomera",
    "Cabildo Insular de Fuerteventura", "Cabildo Insular de Lanzarote",
    "Cabildo Insular de Gran Canaria",
)
INSULAR_HTTPS = frozenset(
    {"Cabildo Insular de El Hierro", "Cabildo Insular de Tenerife", "Cabildo Insular de Gran Canaria"}
)
INSULAR_QUERIES = {
    "Consell Insular de Formentera": (("lang", "es"),),
    "Cabildo Insular de Tenerife": (("lang", "es"),),
    "Cabildo Insular de La Palma": (("codResi", "1"),),
}

MUNICIPAL_LABELS = (
    "Alacant/Alicante", "Araba/Álava", "Albacete", "Almería", "Asturias",
    "Ávila", "Badajoz", "Barcelona", "Burgos", "Cáceres", "Cádiz",
    "Cantabria", "Castelló/Castellón", "Ceuta", "Ciudad Real", "Córdoba",
    "Coruña, A", "Cuenca", "Girona/Gerona", "Granada", "Guadalajara",
    "Gipuzkoa/Guipúzcoa", "Huelva", "Huesca", "Illes Balears/Islas Baleares",
    "Jaén", "León", "Lleida/Lerida", "Lugo", "Madrid", "Málaga", "Melilla",
    "Murcia", "Navarra", "Ourense/Orense", "Palencia", "Palmas, Las",
    "Pontevedra", "Rioja, La", "Salamanca", "Santa Cruz de Tenerife",
    "Segovia", "Sevilla", "Soria", "Tarragona", "Teruel", "Toledo",
    "València/Valencia", "Valladolid", "Bizkaia/Vizcaya", "Zamora", "Zaragoza",
)
MUNICIPAL_HTTPS = frozenset(
    {
        "Alacant/Alicante", "Araba/Álava", "Albacete", "Ávila", "Cáceres",
        "Cádiz", "Castelló/Castellón", "Ciudad Real", "Córdoba", "Coruña, A",
        "Cuenca", "Girona/Gerona", "Granada", "Gipuzkoa/Guipúzcoa", "Huelva",
        "Huesca", "Illes Balears/Islas Baleares", "León", "Lugo", "Madrid",
        "Melilla", "Murcia", "Ourense/Orense", "Palencia", "Palmas, Las",
        "Pontevedra", "Santa Cruz de Tenerife", "Segovia", "Tarragona", "Teruel",
        "Toledo", "Zamora",
    }
)
MUNICIPAL_QUERIES = {
    "Asturias": (("page_id", "341"),),
    "Castelló/Castellón": (("sort", "nombre_poblacion"),),
    "Córdoba": (("idcategoria", "Ayuntamiento"), ("idprovincia", "3")),
    "Málaga": (("tpl", "3"),),
    "Murcia": (("METHOD", "SELECCION_COMARCA"), ("sit", "c,372")),
    "Rioja, La": (("idtab", "559068"), ("id_str", "6"), ("id_ele", "854"), ("id_opt", "0")),
    "Salamanca": (("prestacion", "Cipublico"), ("funcion", "MuestraMunicipios"), ("codProvincia", "37")),
}


@dataclasses.dataclass(frozen=True)
class DirectorySpec:
    kind: str
    source_url: str
    source_id: str
    expected_h1: str
    labels: tuple[str, ...]
    https_labels: frozenset[str]
    administrative_level: str
    record_kind: str
    layout: str
    reviewed_queries: Mapping[str, tuple[tuple[str, str], ...]]


DIRECTORIES = {
    "diputaciones": DirectorySpec(
        "diputaciones", SOURCE_PREFIX + "WP_Diputaciones.html", "D06",
        "Portales de internet de las Diputaciones Provinciales",
        DIPUTACIONES_LABELS, DIPUTACIONES_HTTPS, "PROVINCIAL",
        "PROVINCIAL_PORTAL_REFERENCE", "province_map", {},
    ),
    "insular": DirectorySpec(
        "insular", SOURCE_PREFIX + "WP_CabildosConsejos.html", "D12",
        "Portales de internet de los Consells y Cabildos Insulares",
        INSULAR_LABELS, INSULAR_HTTPS, "INSULAR",
        "INSULAR_PORTAL_REFERENCE", "insular_list", INSULAR_QUERIES,
    ),
    "municipal_queues": DirectorySpec(
        "municipal_queues", SOURCE_PREFIX + "WP_Ayuntamientos.html", "D05",
        "Portales de internet de los Ayuntamientos",
        MUNICIPAL_LABELS, MUNICIPAL_HTTPS, "MUNICIPAL_QUEUE",
        "MUNICIPAL_DIRECTORY_QUEUE", "province_map", MUNICIPAL_QUERIES,
    ),
}


class PagLocalDirectoryError(inventory.InventoryError):
    """The official page no longer matches its reviewed closed contract."""


@dataclasses.dataclass(frozen=True)
class DirectoryAnchor:
    source_label: str
    href: str


@dataclasses.dataclass(frozen=True)
class _Element:
    tag: str
    attributes: tuple[tuple[str, str | None], ...]


def _normalize_text(value: str, field: str) -> str:
    normalized = " ".join(unicodedata.normalize("NFKC", value).split())
    if not normalized:
        raise PagLocalDirectoryError(f"{field} is blank")
    if len(normalized) > 160:
        raise PagLocalDirectoryError(f"{field} is unexpectedly long")
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in normalized):
        raise PagLocalDirectoryError(f"{field} contains a control character")
    return normalized


def _attribute(
    attributes: Sequence[tuple[str, str | None]],
    name: str,
    *,
    required: bool = False,
    preserve: bool = False,
) -> str | None:
    values = [value for key, value in attributes if key.casefold() == name.casefold()]
    if len(values) > 1:
        raise PagLocalDirectoryError(f"duplicate {name!r} attribute")
    if not values:
        if required:
            raise PagLocalDirectoryError(f"missing {name!r} attribute")
        return None
    value = values[0]
    if value is None or not value.strip():
        if required:
            raise PagLocalDirectoryError(f"blank {name!r} attribute")
        return None
    return value if preserve else value.strip()


def _classes(element: _Element) -> frozenset[str]:
    value = _attribute(element.attributes, "class") or ""
    return frozenset(value.split())


class _DirectoryParser(HTMLParser):
    _VOID = frozenset({"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"})

    def __init__(self, spec: DirectorySpec) -> None:
        super().__init__(convert_charrefs=True)
        self.spec = spec
        self.stack: list[_Element] = []
        self.h1_values: list[str] = []
        self._h1_text: list[str] | None = None
        self.anchors: list[DirectoryAnchor] = []
        self.map_anchors: list[DirectoryAnchor] = []
        self._anchor_href: str | None = None
        self._anchor_text: list[str] = []
        self.canonical_container_count = 0
        self.map_count = 0

    def _is_province_anchor(self) -> bool:
        if len(self.stack) < 2 or self.stack[-1].tag != "p":
            return False
        parent = self.stack[-2]
        return (
            parent.tag == "div"
            and _attribute(parent.attributes, "id") == "pie_provincia"
            and "piemapa" in _classes(parent)
        )

    def _is_insular_anchor(self, attrs: Sequence[tuple[str, str | None]]) -> bool:
        if len(self.stack) < 3 or self.stack[-1].tag != "li":
            return False
        ul, div = self.stack[-2], self.stack[-3]
        return (
            ul.tag == "ul" and "lista1" in _classes(ul)
            and div.tag == "div" and "title_mb_30" in _classes(div)
            and "enlacenegrita" in frozenset((_attribute(attrs, "class") or "").split())
        )

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        lowered = tag.casefold()
        element = _Element(lowered, tuple(attrs))
        if lowered == "h1":
            if self._h1_text is not None:
                raise PagLocalDirectoryError("nested h1 is ambiguous")
            self._h1_text = []

        if self.spec.layout == "province_map":
            if lowered == "div" and _attribute(attrs, "id") == "pie_provincia" and "piemapa" in _classes(element):
                self.canonical_container_count += 1
            if lowered == "map" and _attribute(attrs, "id") == "MapProvincias":
                if not self.stack:
                    raise PagLocalDirectoryError("map#MapProvincias left its reviewed container")
                parent = self.stack[-1]
                if parent.tag != "div" or "mapas_provincia" not in _classes(parent):
                    raise PagLocalDirectoryError("map#MapProvincias left its reviewed container")
                self.map_count += 1
            if lowered == "area" and self.stack:
                parent = self.stack[-1]
                if parent.tag == "map" and _attribute(parent.attributes, "id") == "MapProvincias":
                    label = _attribute(attrs, "alt", required=True, preserve=True)
                    href = _attribute(attrs, "href", required=True, preserve=True)
                    assert label is not None and href is not None
                    self.map_anchors.append(DirectoryAnchor(_normalize_text(label, "map label"), href))
        elif lowered == "ul" and self.stack:
            parent = self.stack[-1]
            if parent.tag == "div" and "title_mb_30" in _classes(parent) and "lista1" in _classes(element):
                self.canonical_container_count += 1

        if lowered == "a":
            canonical = self._is_province_anchor() if self.spec.layout == "province_map" else self._is_insular_anchor(attrs)
            if canonical:
                if self._anchor_href is not None:
                    raise PagLocalDirectoryError("nested canonical anchors are ambiguous")
                href = _attribute(attrs, "href", required=True, preserve=True)
                assert href is not None
                self._anchor_href = href
                self._anchor_text = []

        if lowered not in self._VOID:
            self.stack.append(element)

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.casefold() == "a":
            raise PagLocalDirectoryError("self-closing anchor is invalid")
        self.handle_starttag(tag, attrs)
        if tag.casefold() not in self._VOID:
            self.handle_endtag(tag)

    def handle_data(self, data: str) -> None:
        if self._h1_text is not None:
            self._h1_text.append(data)
        if self._anchor_href is not None:
            self._anchor_text.append(data)

    def handle_endtag(self, tag: str) -> None:
        lowered = tag.casefold()
        if lowered == "a" and self._anchor_href is not None:
            label = _normalize_text("".join(self._anchor_text), "source label")
            self.anchors.append(DirectoryAnchor(label, self._anchor_href))
            self._anchor_href = None
            self._anchor_text = []
        if lowered == "h1" and self._h1_text is not None:
            self.h1_values.append(_normalize_text("".join(self._h1_text), "h1"))
            self._h1_text = None
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index].tag == lowered:
                del self.stack[index:]
                break

    def finish(self) -> tuple[DirectoryAnchor, ...]:
        self.close()
        if self._anchor_href is not None or self._h1_text is not None:
            raise PagLocalDirectoryError("directory HTML contains an unterminated reviewed element")
        if self.h1_values != [self.spec.expected_h1]:
            raise PagLocalDirectoryError("reviewed h1 baseline drift")
        if self.canonical_container_count != 1:
            raise PagLocalDirectoryError("expected exactly one canonical directory container")
        if self.spec.layout == "province_map":
            if self.map_count != 1:
                raise PagLocalDirectoryError("expected exactly one map#MapProvincias")
            # HTMLParser decodes character references once. Compare those decoded
            # attribute values before trimming, URL normalization, query parsing,
            # or quarantine handling.
            if tuple(anchor.href for anchor in self.anchors) != tuple(anchor.href for anchor in self.map_anchors):
                raise PagLocalDirectoryError("map/footer href baseline conflict")
            if tuple(anchor.source_label for anchor in self.map_anchors) != self.spec.labels:
                raise PagLocalDirectoryError("map closed label allowlist drift")
        return tuple(self.anchors)


def parse_directory_html(html: str, kind: str) -> tuple[DirectoryAnchor, ...]:
    try:
        spec = DIRECTORIES[kind]
    except KeyError as exc:
        raise PagLocalDirectoryError("directory kind is not in the closed selector") from exc
    parser = _DirectoryParser(spec)
    try:
        parser.feed(html)
        anchors = parser.finish()
    except PagLocalDirectoryError:
        raise
    except Exception as exc:
        raise PagLocalDirectoryError("directory HTML could not be parsed safely") from exc
    labels = tuple(anchor.source_label for anchor in anchors)
    if labels != spec.labels or len(set(labels)) != len(labels):
        # Do not reflect unknown source labels in diagnostics.
        raise PagLocalDirectoryError("closed source label/order allowlist drift")
    return anchors


def _validated_target_parts(
    spec: DirectorySpec,
    anchor: DirectoryAnchor,
) -> tuple[dict[str, object], str | None, bool]:
    href = anchor.href
    label = anchor.source_label
    quarantine: str | None = None
    parse_href = href
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in href):
        raise PagLocalDirectoryError(f"{label}: target contains a control character")
    whitespace = [character for character in href if character.isspace()]
    if spec.kind == "municipal_queues" and label == "León":
        if not href.endswith(" ") or href.endswith("  ") or whitespace != [" "]:
            raise PagLocalDirectoryError(f"{label}: reviewed trailing-space baseline drift")
        quarantine = "SOURCE_URL_WHITESPACE"
        parse_href = href[:-1]
    elif whitespace:
        raise PagLocalDirectoryError(f"{label}: target contains unexpected whitespace")
    try:
        parts = urlsplit(parse_href)
        scheme = parts.scheme.casefold()
        username, password = parts.username, parts.password
        raw_host = parts.hostname or ""
        port = parts.port
    except (UnicodeError, ValueError) as exc:
        raise PagLocalDirectoryError(f"{label}: target authority is invalid") from exc
    if scheme not in {"http", "https"}:
        raise PagLocalDirectoryError(f"{label}: target scheme is not HTTP(S)")
    if username is not None or password is not None:
        raise PagLocalDirectoryError(f"{label}: target credentials are forbidden")
    try:
        host = inventory._canonical_host(raw_host)
    except (inventory.InventoryError, ValueError) as exc:
        raise PagLocalDirectoryError(f"{label}: target authority is invalid") from exc
    if port is not None and not 1 <= port <= 65535:
        raise PagLocalDirectoryError(f"{label}: target port is invalid")
    if (scheme, port) in {("https", 443), ("http", 80)}:
        port = None

    has_query_marker = "?" in parse_href.partition("#")[0]
    has_fragment_marker = "#" in parse_href
    expected_query = spec.reviewed_queries.get(label, ())
    try:
        query_pairs = tuple(parse_qsl(parts.query, keep_blank_values=True, strict_parsing=True))
    except ValueError as exc:
        raise PagLocalDirectoryError(f"{label}: target query is malformed") from exc
    if query_pairs != expected_query:
        raise PagLocalDirectoryError(f"{label}: public query does not match reviewed selector")
    if bool(expected_query) != bool(parts.query):
        raise PagLocalDirectoryError(f"{label}: reviewed query marker baseline drift")

    empty_marker_labels = (
        {"Coruña, A", "Girona/Gerona"}
        if spec.kind == "municipal_queues"
        else set()
    )
    if label in empty_marker_labels:
        if not has_query_marker or parts.query:
            raise PagLocalDirectoryError(f"{label}: empty query marker baseline drift")
        quarantine = quarantine or "SOURCE_QUERY_MARKER"
    elif has_query_marker != bool(expected_query):
        raise PagLocalDirectoryError(f"{label}: query marker baseline drift")

    if spec.kind == "municipal_queues":
        # The reviewed Murcia selector contains punctuation outside the scanner's
        # deliberately narrow public-value alphabet.  Preserve its validated
        # components, but never turn it into an executable candidate URL.
        if label == "Murcia":
            quarantine = quarantine or "SOURCE_QUERY_UNSAFE"
        expected_fragment = {"Cádiz": "", "Lleida/Lerida": "A"}.get(label)
        if expected_fragment is not None:
            if not has_fragment_marker or parts.fragment != expected_fragment:
                raise PagLocalDirectoryError(f"{label}: fragment baseline drift")
            quarantine = "SOURCE_FRAGMENT"
        elif has_fragment_marker:
            raise PagLocalDirectoryError(f"{label}: unexpected fragment")
    elif has_fragment_marker:
        raise PagLocalDirectoryError(f"{label}: target fragment is forbidden")

    path = parts.path or "/"
    if "\\" in path or "//" in path or ";" in path:
        raise PagLocalDirectoryError(f"{label}: target path is ambiguous")
    try:
        decoded_path = inventory._validate_decoded_component(path, "target path")
        inventory._validate_decoded_component(parts.query, "target query")
    except inventory.InventoryError as exc:
        raise PagLocalDirectoryError(f"{label}: target encoding is invalid") from exc
    if "%2f" in path.casefold() or "%5c" in path.casefold():
        raise PagLocalDirectoryError(f"{label}: target path contains encoded separators")
    if "\\" in decoded_path or "//" in decoded_path or ";" in decoded_path:
        raise PagLocalDirectoryError(f"{label}: decoded target path is ambiguous")
    if any(segment in {".", ".."} for segment in decoded_path.split("/")):
        raise PagLocalDirectoryError(f"{label}: target path contains a dot segment")

    components: dict[str, object] = {
        "scheme": scheme, "host": host, "port": port, "path": path,
        "public_query_pairs": [list(pair) for pair in query_pairs],
    }
    return components, quarantine, has_query_marker


def _target_record(spec: DirectorySpec, anchor: DirectoryAnchor) -> dict[str, object]:
    components, quarantine, _ = _validated_target_parts(spec, anchor)
    scheme = str(components["scheme"])
    if quarantine is not None:
        return {
            "target": {"kind": "QUARANTINED_REFERENCE_COMPONENTS", **components},
            "target_status": quarantine,
            "candidate_seed_eligible": False,
            "target_fetch_performed": False,
        }
    if scheme == "http":
        return {
            "target": {"kind": "HTTP_REFERENCE_COMPONENTS", **components},
            "target_status": "HTTPS_RESOLUTION_REQUIRED",
            "candidate_seed_eligible": False,
            "target_fetch_performed": False,
        }
    allowed_keys = tuple(key for key, _ in spec.reviewed_queries.get(anchor.source_label, ()))
    try:
        safe_url = inventory.sanitize_url(anchor.href, allowed_keys, reject_unlisted_query=True)
        origin = inventory.exact_origin(safe_url)
    except inventory.InventoryError as exc:
        raise PagLocalDirectoryError(f"{anchor.source_label}: HTTPS target is unsafe") from exc
    return {
        "target": {"kind": "HTTPS_URL", **components, "url": safe_url, "origin": origin},
        "target_status": "HTTPS_REFERENCE_VALIDATED",
        "candidate_seed_eligible": True,
        "target_fetch_performed": False,
    }


def _validate_snapshot_date(value: str) -> str:
    try:
        parsed = dt.date.fromisoformat(value)
    except ValueError as exc:
        raise PagLocalDirectoryError("snapshot date must be canonical YYYY-MM-DD") from exc
    if parsed.isoformat() != value:
        raise PagLocalDirectoryError("snapshot date must be canonical YYYY-MM-DD")
    return value


def enumerate_directory(fetcher: inventory.Fetcher, kind: str, snapshot_date: str) -> list[dict[str, object]]:
    try:
        spec = DIRECTORIES[kind]
    except KeyError as exc:
        raise PagLocalDirectoryError("directory kind is not in the closed selector") from exc
    snapshot_date = _validate_snapshot_date(snapshot_date)
    result = fetcher.fetch(spec.source_url, allowed_query_keys=(), allowed_redirect_origins=())
    if result.requested_url != spec.source_url or result.final_url != spec.source_url:
        raise PagLocalDirectoryError("source URL changed or redirected")
    if result.redirect_chain or result.request_count != 1:
        raise PagLocalDirectoryError("source fetch must be exactly one non-redirected GET")
    if result.status != 200:
        raise PagLocalDirectoryError(f"source returned HTTP status {result.status}")
    if result.content_type not in {"text/html", "application/xhtml+xml"}:
        raise PagLocalDirectoryError("source is not HTML")
    if len(result.body) > MAX_SOURCE_BYTES:
        raise PagLocalDirectoryError("source body exceeded the fixed limit")
    try:
        html = result.body.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise PagLocalDirectoryError("source body is not valid UTF-8") from exc

    anchors = parse_directory_html(html, kind)
    labels_by_href: dict[str, set[str]] = {}
    for anchor in anchors:
        labels_by_href.setdefault(anchor.href, set()).add(anchor.source_label)
    duplicate_href_groups = {
        frozenset(labels)
        for labels in labels_by_href.values()
        if len(labels) > 1
    }
    expected_duplicate_groups = (
        {frozenset({"Coruña, A", "Girona/Gerona"})}
        if spec.kind == "municipal_queues"
        else set()
    )
    if duplicate_href_groups != expected_duplicate_groups:
        raise PagLocalDirectoryError("reviewed target uniqueness baseline drift")
    schemes: dict[str, str] = {}
    for anchor in anchors:
        components, _, _ = _validated_target_parts(spec, anchor)
        schemes[anchor.source_label] = str(components["scheme"])
    actual_https = frozenset(label for label, scheme in schemes.items() if scheme == "https")
    if actual_https != spec.https_labels or len(actual_https) + sum(value == "http" for value in schemes.values()) != len(spec.labels):
        raise PagLocalDirectoryError("reviewed per-label scheme baseline drift")
    if spec.kind == "municipal_queues":
        by_label = {anchor.source_label: anchor.href for anchor in anchors}
        if by_label["Girona/Gerona"] != by_label["Coruña, A"]:
            raise PagLocalDirectoryError("reviewed municipal source conflict baseline drift")

    targets = [_target_record(spec, anchor) for anchor in anchors]
    if spec.kind == "municipal_queues":
        index = spec.labels.index("Girona/Gerona")
        targets[index] = {
            **targets[index],
            "target_status": "SOURCE_CONFLICT",
            "candidate_seed_eligible": False,
        }
    source_hash = hashlib.sha256(result.body).hexdigest()
    snapshot_id = f"pag-{kind}-{snapshot_date}"
    return [
        {
            "output_schema_version": OUTPUT_SCHEMA_VERSION,
            "snapshot_id": snapshot_id,
            "snapshot_date": snapshot_date,
            "record_kind": spec.record_kind,
            "administrative_level": spec.administrative_level,
            "source_label": anchor.source_label,
            "territory": anchor.source_label,
            "source_url": spec.source_url,
            "source_final_url": result.final_url,
            "source_id": spec.source_id,
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
        for anchor, target in zip(anchors, targets, strict=True)
    ]


def read_fixture_secure(path: Path, max_bytes: int = MAX_SOURCE_BYTES) -> bytes:
    if not 1 <= max_bytes <= MAX_SOURCE_BYTES:
        raise PagLocalDirectoryError("fixture size limit is invalid")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise PagLocalDirectoryError("fixture could not be opened safely") from exc
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
            raise PagLocalDirectoryError("fixture must be one regular, singly-linked file")
        if metadata.st_size > max_bytes:
            raise PagLocalDirectoryError("fixture exceeded the configured size limit")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(64 * 1024, max_bytes + 1 - total))
            if not chunk:
                break
            chunks.append(chunk)
            total += len(chunk)
            if total > max_bytes:
                raise PagLocalDirectoryError("fixture exceeded the configured size limit")
        return b"".join(chunks)
    finally:
        os.close(descriptor)


class _FixtureFetcher:
    def __init__(self, spec: DirectorySpec, body: bytes) -> None:
        self.spec = spec
        self.body = body
        self.call_count = 0

    def fetch(self, url: str, *, same_origin: str | None = None, allowed_query_keys: Iterable[str] = (), allowed_redirect_origins: Iterable[str] = ()) -> inventory.FetchResult:
        self.call_count += 1
        if url != self.spec.source_url or same_origin is not None or tuple(allowed_query_keys) or tuple(allowed_redirect_origins) or self.call_count != 1:
            raise PagLocalDirectoryError("offline fixture permits only one exact source request")
        return inventory.FetchResult(
            requested_url=url, final_url=url, redirect_chain=(), status=200,
            content_type="text/html", body=self.body, request_count=1,
        )


def write_jsonl_atomic(path: Path, records: Sequence[Mapping[str, object]]) -> None:
    inventory.write_jsonl_atomic(path, records)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", required=True, choices=tuple(DIRECTORIES))
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--snapshot-date", required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--fixture-html", type=Path)
    source.add_argument("--live", action="store_true")
    parser.add_argument("--timeout-seconds", type=float, default=inventory.DEFAULT_TIMEOUT_SECONDS)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    spec = DIRECTORIES[args.kind]
    try:
        if args.fixture_html is not None:
            fetcher: inventory.Fetcher = _FixtureFetcher(spec, read_fixture_secure(args.fixture_html))
        else:
            limits = inventory.ScanLimits(
                max_body_bytes=MAX_SOURCE_BYTES, max_redirects=0,
                max_script_depth=0, max_assets=0,
                timeout_seconds=args.timeout_seconds, min_host_interval_seconds=0.0,
            )
            fetcher = inventory.LiveFetcher(limits)
        records = enumerate_directory(fetcher, args.kind, args.snapshot_date)
        write_jsonl_atomic(args.output, records)
    except (PagLocalDirectoryError, inventory.InventoryError, OSError) as exc:
        print(f"PAG local directory enumeration failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
