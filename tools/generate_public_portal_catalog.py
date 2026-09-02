#!/usr/bin/env python3
"""Generate the bundled public portal catalog from reviewed local sources.

This is a build-time transformation only. Android never reads Markdown at runtime.
The generator does not fetch URLs, authenticate, or mutate external systems.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit

import yaml


LEVELS = {
    "ESTATAL": "STATE",
    "AUTONOMICO": "AUTONOMOUS_COMMUNITY",
    "PROVINCIAL": "LOCAL_ADMINISTRATION",
    "INSULAR": "LOCAL_ADMINISTRATION",
    "MUNICIPAL": "LOCAL_ADMINISTRATION",
    "UNIVERSIDAD_PUBLICA": "UNIVERSITY",
    "OTRA_INSTITUCION_PUBLICA": "PUBLIC_INSTITUTION",
}

MIN_INVENTORY_RECORDS = 182
SITE_PROFILE_ROOT_KEYS = {"schemaVersion", "catalogVersion", "profiles"}

COMMUNITY_REGION_CODES = {
    "Andalucía": "ES-AN",
    "Aragón": "ES-AR",
    "Principado de Asturias": "ES-AS",
    "Cantabria": "ES-CB",
    "Castilla y León": "ES-CL",
    "Castilla-La Mancha": "ES-CM",
    "Canarias": "ES-CN",
    "Cataluña": "ES-CT",
    "Extremadura": "ES-EX",
    "Galicia": "ES-GA",
    "Illes Balears": "ES-IB",
    "Región de Murcia": "ES-MC",
    "Comunidad de Madrid": "ES-MD",
    "Comunidad Foral de Navarra": "ES-NC",
    "País Vasco": "ES-PV",
    "La Rioja": "ES-RI",
    "Comunidad Valenciana": "ES-VC",
    "Comunitat Valenciana": "ES-VC",
    "Ciudad Autónoma de Ceuta": "ES-CE",
    "Ciudad Autónoma de Melilla": "ES-ML",
}

EXPLICIT_REGION_OVERRIDES = {
    "ES-PUB-0015": "ES-CL",
    "ES-PUB-0016": "ES-AN",
    "ES-PUB-0017": "ES-MD",
    "ES-PUB-0018": "ES-AN",
    "ES-PUB-0020": "ES-AR",
    "ES-PUB-0092": "ES",
}

REGION_TERRITORY_NAMES = {
    "ES": "España",
    "ES-AN": "Andalucía",
    "ES-AR": "Aragón",
    "ES-AS": "Principado de Asturias",
    "ES-CB": "Cantabria",
    "ES-CL": "Castilla y León",
    "ES-CM": "Castilla-La Mancha",
    "ES-CN": "Canarias",
    "ES-CT": "Cataluña",
    "ES-EX": "Extremadura",
    "ES-GA": "Galicia",
    "ES-IB": "Illes Balears",
    "ES-MC": "Región de Murcia",
    "ES-MD": "Comunidad de Madrid",
    "ES-NC": "Comunidad Foral de Navarra",
    "ES-PV": "País Vasco",
    "ES-RI": "La Rioja",
    "ES-VC": "Comunitat Valenciana",
    "ES-CE": "Ciudad Autónoma de Ceuta",
    "ES-ML": "Ciudad Autónoma de Melilla",
}


def _records(markdown: str) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    for body in re.findall(r"```yaml\n(.*?)\n```", markdown, flags=re.DOTALL):
        parsed = yaml.safe_load(body)
        if isinstance(parsed, dict) and isinstance(parsed.get("records"), list):
            records.extend(parsed["records"])
    return records


def _site_profiles(source: Path) -> list[tuple[str, str]]:
    try:
        parsed = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid site profile catalog: {source}") from error
    if not isinstance(parsed, dict) or set(parsed) != SITE_PROFILE_ROOT_KEYS:
        raise ValueError("invalid site profile catalog root")
    if parsed.get("schemaVersion") != 1:
        raise ValueError("unsupported site profile schemaVersion")
    if not isinstance(parsed.get("catalogVersion"), int) or parsed["catalogVersion"] < 1:
        raise ValueError("invalid site profile catalogVersion")
    raw_profiles = parsed.get("profiles")
    if not isinstance(raw_profiles, list) or not raw_profiles:
        raise ValueError("site profile catalog must contain profiles")

    profiles: list[tuple[str, str]] = []
    profile_ids: set[str] = set()
    start_urls: set[str] = set()
    for index, raw_profile in enumerate(raw_profiles):
        if not isinstance(raw_profile, dict):
            raise ValueError(f"invalid site profile at index {index}")
        profile_id = raw_profile.get("profileId")
        start_url = raw_profile.get("startUrl")
        if not isinstance(profile_id, str) or not profile_id.strip():
            raise ValueError(f"invalid profileId at index {index}")
        if profile_id != profile_id.strip():
            raise ValueError(f"non-canonical profileId: {profile_id!r}")
        if not isinstance(start_url, str) or not start_url.strip():
            raise ValueError(f"invalid profile startUrl: {profile_id}")
        if start_url != start_url.strip():
            raise ValueError(f"non-canonical profile startUrl: {profile_id}")
        parsed_url = urlsplit(start_url)
        if (
            parsed_url.scheme != "https"
            or not parsed_url.hostname
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.fragment
        ):
            raise ValueError(f"invalid HTTPS profile startUrl: {profile_id}")
        if profile_id in profile_ids:
            raise ValueError(f"duplicate profileId: {profile_id}")
        if start_url in start_urls:
            raise ValueError(f"duplicate profile startUrl: {start_url}")
        profile_ids.add(profile_id)
        start_urls.add(start_url)
        profiles.append((profile_id, start_url))
    return profiles


def _profile_bindings(
    records: list[dict[str, object]],
    profiles: list[tuple[str, str]],
) -> dict[str, str]:
    records_by_entry_url: dict[str, list[dict[str, object]]] = {}
    for record in records:
        entry_url = str(record.get("entry_url", ""))
        records_by_entry_url.setdefault(entry_url, []).append(record)

    bindings: dict[str, str] = {}
    bound_profile_ids: set[str] = set()
    profile_by_start_url = {start_url: profile_id for profile_id, start_url in profiles}
    for profile_id, start_url in profiles:
        matches = records_by_entry_url.get(start_url, [])
        if len(matches) > 1:
            raise ValueError(f"profile {profile_id} has multiple inventory entries for exact startUrl")
        if not matches:
            continue
        surface_key = str(matches[0].get("surface_key", ""))
        if not surface_key:
            raise ValueError(f"profile {profile_id} matched an invalid inventory surface")
        if surface_key in bindings:
            raise ValueError(f"multiple profiles map to inventory surface: {surface_key}")
        bindings[surface_key] = profile_id
        bound_profile_ids.add(profile_id)

    for record in records:
        if "launch_url" not in record:
            continue
        launch_url = record.get("launch_url")
        if not isinstance(launch_url, str) or not launch_url or launch_url != launch_url.strip():
            raise ValueError("invalid alias launch_url")
        profile_id = profile_by_start_url.get(launch_url)
        if profile_id is None:
            raise ValueError(f"alias launch_url does not match a profile startUrl: {launch_url}")
        surface_key = str(record.get("surface_key", ""))
        if not surface_key:
            raise ValueError("alias launch_url belongs to an invalid inventory surface")
        existing = bindings.get(surface_key)
        if existing is not None:
            raise ValueError(f"redundant alias launch_url on profile-owned surface: {surface_key}")
        bindings[surface_key] = profile_id
        bound_profile_ids.add(profile_id)

    unbound = [profile_id for profile_id, _ in profiles if profile_id not in bound_profile_ids]
    if unbound:
        raise ValueError(f"profile {unbound[0]} has no inventory entry or launch_url for exact startUrl")
    return bindings


def _mechanisms(record: dict[str, object]) -> list[str]:
    js_client = str(record.get("js_client", "")).strip().upper().replace("@FIRMA", "AFIRMA")
    js_tokens = set(re.split(r"[^A-Z0-9]+", js_client))
    protocol_family = str(record.get("protocol_family", "")).strip().upper()
    protocol_tokens = set(re.split(r"[^A-Z0-9]+", protocol_family))
    client_tls_auth = str(record.get("client_tls_auth", "")).strip().upper()
    result: set[str] = set()
    if record.get("certificate_required") in {"SI", "CONDICIONAL"}:
        result.add("CERTIFICATE_ACCESS")
    if record.get("signature_required") in {"SI", "CONDICIONAL"}:
        result.add("ELECTRONIC_SIGNATURE")
    if "AUTOFIRMA" in js_tokens or "AUTOFIRMA" in protocol_tokens:
        result.add("AUTOFIRMA")
    if "AUTOSCRIPT" in js_tokens or "AUTOSCRIPT" in protocol_tokens:
        result.add("AUTOSCRIPT")
    if "MINIAPPLET" in js_tokens or "MINIAPPLET" in protocol_tokens:
        result.add("MINIAPPLET")
    if client_tls_auth in {"SI", "VERIFIED_E2E"} or (
        "CLIENT" in protocol_tokens and "TLS" in protocol_tokens
    ):
        result.add("CLIENT_TLS_AUTH")
    if "AFIRMA" in js_tokens or "AFIRMA" in protocol_tokens:
        result.add("AFIRMA")
    return sorted(result)


def _formats(record: dict[str, object]) -> list[str]:
    value = str(record.get("signature_format", "")).lower()
    return [name for name in ("CADES", "PADES", "XADES", "FACTURAE") if name.lower() in value]


def _catalog_status(record: dict[str, object]) -> str:
    inventory = str(record["inventory_status"])
    discovery = str(record["discovery_state"])
    if inventory == "VERIFIED_E2E":
        return "E2E_VERIFIED"
    if inventory == "IMPLEMENTED_NOT_E2E":
        return "E2E_PENDING"
    if inventory == "DEPRECATED":
        return "DEPRECATED"
    if inventory in {"UNSUPPORTED_PROTOCOL", "INACCESSIBLE"}:
        return "BLOCKED"
    return "DISCOVERED" if discovery == "DISCOVERED" else "CATALOGED"


def _territory(record: dict[str, object]) -> str:
    community = str(record.get("autonomous_community", "NO_VERIFICADO"))
    locality = str(record.get("province_or_municipality", "NO_VERIFICADO"))
    if community not in {"NO_APLICA", "NO_VERIFICADO"}:
        return community
    if locality not in {"NO_APLICA", "NO_VERIFICADO"}:
        return locality
    return REGION_TERRITORY_NAMES[_region_code(record)]


def _region_code(record: dict[str, object]) -> str:
    inventory_id = str(record.get("inventory_id", ""))
    explicit = record.get("region_code")
    expected_override = EXPLICIT_REGION_OVERRIDES.get(inventory_id)
    if explicit is not None:
        if expected_override is None or explicit != expected_override:
            raise ValueError(f"invalid explicit region_code for {inventory_id}")
        return str(explicit)
    if expected_override is not None:
        raise ValueError(f"missing explicit region_code for {inventory_id}")

    community = str(record.get("autonomous_community", "NO_VERIFICADO"))
    mapped = COMMUNITY_REGION_CODES.get(community)
    if mapped is not None:
        return mapped
    if community == "NO_APLICA" and record.get("administrative_level") == "ESTATAL":
        return "ES"
    raise ValueError(f"region cannot be resolved for {inventory_id}")


def _entry(record: dict[str, object], profile_bindings: dict[str, str]) -> dict[str, object]:
    surface_key = str(record["surface_key"])
    entry_url = str(record["entry_url"])
    if not entry_url.startswith("https://"):
        raise ValueError(f"non-HTTPS inventory entry: {surface_key}")
    evidence_ids = record.get("evidence_ids")
    if not isinstance(evidence_ids, list) or not evidence_ids:
        raise ValueError(f"missing evidence IDs: {surface_key}")
    return {
        "portalId": surface_key,
        "inventoryId": str(record["inventory_id"]),
        "profileId": profile_bindings.get(surface_key),
        "displayName": str(record["surface_name"]),
        "organization": str(record["institution_name"]),
        "governmentLevel": LEVELS[str(record["administrative_level"])],
        "regionCode": _region_code(record),
        "territory": _territory(record),
        "purpose": str(record["operation_summary"]),
        "entryUrl": entry_url,
        **({"launchUrl": str(record["launch_url"])} if "launch_url" in record else {}),
        "observedMechanisms": _mechanisms(record),
        "observedSignatureFormats": _formats(record),
        "protocolFamily": str(record["protocol_family"]),
        "catalogStatus": _catalog_status(record),
        "inventoryStatus": str(record["inventory_status"]),
        "discoveryState": str(record["discovery_state"]),
        "evidenceIds": [str(value) for value in evidence_ids],
        "reviewedOn": None if record["reviewed_at"] == "NO_VERIFICADO" else str(record["reviewed_at"]),
        "limitations": str(record["reason"]),
    }


def generate(source: Path, profiles_source: Path) -> dict[str, object]:
    raw = source.read_bytes()
    markdown = raw.decode("utf-8")
    records = _records(markdown)
    if len(records) < MIN_INVENTORY_RECORDS:
        raise ValueError(
            f"expected at least {MIN_INVENTORY_RECORDS} inventory records, found {len(records)}"
        )
    profiles = _site_profiles(profiles_source)
    profile_bindings = _profile_bindings(records, profiles)
    entries = [_entry(record, profile_bindings) for record in records]

    for field in ("portalId", "inventoryId", "entryUrl"):
        values = [entry[field] for entry in entries]
        if len(values) != len(set(values)):
            raise ValueError(f"duplicate {field}")
    bound_profile_ids = {entry["profileId"] for entry in entries if entry["profileId"] is not None}
    if bound_profile_ids != {profile_id for profile_id, _ in profiles}:
        raise ValueError("not all profiles were mapped")
    return {
        "schemaVersion": 2,
        "catalogVersion": 2,
        "sourceRevision": hashlib.sha256(raw).hexdigest(),
        "entries": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--profiles", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    catalog = generate(args.source, args.profiles)
    output = (json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=args.output.parent, delete=False) as temporary:
        temporary.write(output)
        temporary.flush()
        temporary_path = Path(temporary.name)
    temporary_path.replace(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
