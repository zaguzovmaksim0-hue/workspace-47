#!/usr/bin/env python3
"""Generate the bundled public portal catalog from the reviewed inventory.

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

import yaml


PROFILE_BINDINGS = {
    "age-reg-redsara": "reg-age-redsara",
    "junta-andalucia-ovorion": "junta-andalucia",
    "unizar-tramitador": "unizar-tramitador",
    "junta-andalucia-carne-joven": "carne-joven-andalucia",
    "aragon-siraw": "aragon-siraw",
}

LEVELS = {
    "ESTATAL": "STATE",
    "AUTONOMICO": "AUTONOMOUS_COMMUNITY",
    "PROVINCIAL": "LOCAL_ADMINISTRATION",
    "INSULAR": "LOCAL_ADMINISTRATION",
    "MUNICIPAL": "LOCAL_ADMINISTRATION",
    "UNIVERSIDAD_PUBLICA": "UNIVERSITY",
    "OTRA_INSTITUCION_PUBLICA": "PUBLIC_INSTITUTION",
}

MIN_INVENTORY_RECORDS = 180


def _records(markdown: str) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    for body in re.findall(r"```yaml\n(.*?)\n```", markdown, flags=re.DOTALL):
        parsed = yaml.safe_load(body)
        if isinstance(parsed, dict) and isinstance(parsed.get("records"), list):
            records.extend(parsed["records"])
    return records


def _mechanisms(record: dict[str, object]) -> list[str]:
    js_client = str(record.get("js_client", "")).strip().lower()
    protocol_family = str(record.get("protocol_family", "")).strip().upper()
    protocol_tokens = set(re.split(r"[^A-Z0-9]+", protocol_family))
    client_tls_auth = str(record.get("client_tls_auth", "")).strip().upper()
    result: set[str] = set()
    if record.get("certificate_required") in {"SI", "CONDICIONAL"}:
        result.add("CERTIFICATE_ACCESS")
    if record.get("signature_required") in {"SI", "CONDICIONAL"}:
        result.add("ELECTRONIC_SIGNATURE")
    if js_client == "autofirma" or "AUTOFIRMA" in protocol_tokens:
        result.add("AUTOFIRMA")
    if js_client == "autoscript" or "AUTOSCRIPT" in protocol_tokens:
        result.add("AUTOSCRIPT")
    if js_client == "miniapplet" or "MINIAPPLET" in protocol_tokens:
        result.add("MINIAPPLET")
    if client_tls_auth in {"SI", "VERIFIED_E2E"} or (
        "CLIENT" in protocol_tokens and "TLS" in protocol_tokens
    ):
        result.add("CLIENT_TLS_AUTH")
    if js_client in {"@firma", "afirma"} or "AFIRMA" in protocol_tokens:
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
    return "España"


def _entry(record: dict[str, object]) -> dict[str, object]:
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
        "profileId": PROFILE_BINDINGS.get(surface_key),
        "displayName": str(record["surface_name"]),
        "organization": str(record["institution_name"]),
        "governmentLevel": LEVELS[str(record["administrative_level"])],
        "territory": _territory(record),
        "purpose": str(record["operation_summary"]),
        "entryUrl": entry_url,
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


def _supplemental_entries() -> list[dict[str, object]]:
    return [
        {
            "portalId": "junta-andalucia-ofvirtual",
            "inventoryId": None,
            "profileId": "junta-ofvirtual",
            "displayName": "Junta de Andalucía — Oficina Virtual",
            "organization": "Junta de Andalucía",
            "governmentLevel": "AUTONOMOUS_COMMUNITY",
            "territory": "Andalucía",
            "purpose": "Acceso con certificado a la Oficina Virtual",
            "entryUrl": "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            "observedMechanisms": ["AFIRMA", "CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE", "MINIAPPLET"],
            "observedSignatureFormats": ["CADES"],
            "protocolFamily": "MINIAPPLET_TRIPHASE",
            "catalogStatus": "E2E_VERIFIED",
            "inventoryStatus": "VERIFIED_E2E",
            "discoveryState": "REVIEWED",
            "evidenceIds": [
                "LIVE-JUNTA-OFVIRTUAL-2026-07-22",
                "E2E-JUNTA-OFVIRTUAL-2026-07-29",
            ],
            "reviewedOn": "2026-07-29",
            "limitations": (
                "El portal real aceptó la firma CAdES de autenticación y abrió la sesión interna; "
                "verificación limitada al login observado."
            ),
        },
        {
            "portalId": "educacion-convocatoria-46",
            "inventoryId": None,
            "profileId": "educacion-convocatoria",
            "displayName": "Ministerio de Educación — Convocatoria 46",
            "organization": "Ministerio de Educación, Formación Profesional y Deportes",
            "governmentLevel": "STATE",
            "territory": "España",
            "purpose": "Consulta de la convocatoria de homologación y convalidación",
            "entryUrl": "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46",
            "observedMechanisms": ["CERTIFICATE_ACCESS"],
            "observedSignatureFormats": [],
            "protocolFamily": "CLAVE_GATEWAY_UNVERIFIED",
            "catalogStatus": "CATALOGED",
            "inventoryStatus": "BROWSE_ONLY",
            "discoveryState": "REVIEWED",
            "evidenceIds": ["LIVE-EDUCACION-ENTRY-2026-07-22"],
            "reviewedOn": "2026-07-22",
            "limitations": "Transporte downstream de certificado y callback no verificados; firma bloqueada.",
        },
    ]


def generate(source: Path) -> dict[str, object]:
    raw = source.read_bytes()
    markdown = raw.decode("utf-8")
    records = _records(markdown)
    if len(records) < MIN_INVENTORY_RECORDS:
        raise ValueError(
            f"expected at least {MIN_INVENTORY_RECORDS} inventory records, found {len(records)}"
        )
    entries = [_entry(record) for record in records] + _supplemental_entries()
    portal_ids = [entry["portalId"] for entry in entries]
    if len(portal_ids) != len(set(portal_ids)):
        raise ValueError("duplicate portalId")
    return {
        "schemaVersion": 1,
        "catalogVersion": 1,
        "sourceRevision": hashlib.sha256(raw).hexdigest(),
        "entries": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    catalog = generate(args.source)
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
