#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit

PORTAL_RE = re.compile(r"[a-z0-9][a-z0-9-]{0,95}")
TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
HOST_RE = re.compile(
    r"(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+"
    r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
)
CLASSIFICATIONS = {
    "PENDING",
    "PASS_BROWSE",
    "PASS_MECHANISM_BOUNDARY",
    "PASS_CLIENT_TLS",
    "PASS_CRYPTO_CALLBACK",
    "PASS_PORTAL_AUTH",
    "RECIPE_REQUIRED",
    "FAIL_SECURITY_OR_NETWORK",
    "FAIL_UNEXPECTED_CLIENT_AUTH_HOST",
    "FAIL_CLIENT_AUTH_LOOP",
    "INFRASTRUCTURE_ERROR",
}
BOOLEAN_FIELDS = {
    "certificateUnlocked",
    "openRequested",
    "webViewActive",
    "pageStarted",
    "pageFinished",
    "clientAuthObserved",
    "clientAuthConfirmed",
    "clientCertReceived",
    "clientCertProceeded",
    "clientCertRejected",
    "certificateSelectionObserved",
    "publicCertificateShared",
    "signingConfirmationObserved",
    "signingConfirmed",
    "signingCancelledAtBoundary",
    "signatureCompleted",
    "portalAuthSuccess",
    "networkError",
    "sslError",
    "navigationBlocked",
    "unexpectedClientAuthHost",
}
RESULT_FIELDS = {
    "schemaVersion",
    "portalId",
    "profileId",
    "classification",
    "level",
    "capabilities",
    "expectedStartHost",
    "currentHost",
    *BOOLEAN_FIELDS,
    "signingFailureCode",
    "infrastructureError",
}
ALLOWED_LOG_EVENTS = {
    "EXTERNAL_NAVIGATION",
    "PLAY_STORE_FALLBACK_INTERCEPTED",
    "NAVIGATION_BLOCKED",
    "WEB_MESSAGE_REJECTED",
    "WEB_MESSAGE_FEATURE_UNAVAILABLE",
    "DOCUMENT_START_SCRIPT_UNAVAILABLE",
    "SSL_ERROR_CANCELLED",
    "SAFE_BROWSING_BLOCKED",
    "NETWORK_ERROR",
    "NAVIGATION_ALLOWED",
    "NETWORK_REQUEST",
    "PAGE_STARTED",
    "PAGE_FINISHED",
    "PORTAL_CALLBACK",
}
EVENT_RE = re.compile(r"(?:^| )event=([A-Z_]+)(?: |$)")

PROGRESS_TIMESTAMP_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")
PROGRESS_INDEX_RE = re.compile(r"\d{1,3}/\d{1,3}")
PROGRESS_STAGES = {
    "SELECT_START",
    "SELECT_DONE",
    "INSTALL_QA_START",
    "INSTALL_QA_DONE",
    "INSTALL_TEST_START",
    "INSTALL_TEST_DONE",
    "STAGE_FIXTURE_START",
    "STAGE_MKDIR_START",
    "STAGE_MKDIR_DONE",
    "STAGE_CERT_WRITE_START",
    "STAGE_CERT_WRITE_DONE",
    "STAGE_PASSWORD_WRITE_START",
    "STAGE_PASSWORD_WRITE_DONE",
    "STAGE_CHMOD_START",
    "STAGE_CHMOD_DONE",
    "STAGE_STAT_START",
    "STAGE_STAT_DONE",
    "STAGE_FIXTURE_DONE",
    "PORTAL_START",
    "INSTRUMENT_START",
    "RESULT_READ_START",
    "RESULT_READ_DONE",
    "NAV_READ_START",
    "NAV_READ_DONE",
    "PORTAL_DONE",
    "SUMMARY_START",
    "SUMMARY_DONE",
}
INSTRUMENT_DONE_RE = re.compile(r"INSTRUMENT_DONE_[0-9]{1,3}")


def read_catalog(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data.get("entries")
    if not isinstance(entries, list) or len(entries) < 150:
        raise ValueError("public portal catalog is unexpectedly small")
    ids = [entry.get("portalId") for entry in entries]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate portal id")
    if any(not isinstance(value, str) or not PORTAL_RE.fullmatch(value) for value in ids):
        raise ValueError("unsafe portal id")
    return data


def selected_ids(
    catalog: dict,
    portal: str = "",
    shard_index: int | None = None,
    shard_total: int | None = None,
) -> list[str]:
    ids = [entry["portalId"] for entry in catalog["entries"]]
    if portal and (not PORTAL_RE.fullmatch(portal) or portal not in ids):
        raise ValueError("portal filter is not exactly one bundled portal")
    if (shard_index is None) != (shard_total is None):
        raise ValueError("shard index and total must be supplied together")
    if shard_total is not None:
        if shard_total < 1 or shard_total > 32 or shard_index is None or not 0 <= shard_index < shard_total:
            raise ValueError("invalid shard selection")
        base, extra = divmod(len(ids), shard_total)
        start = shard_index * base + min(shard_index, extra)
        size = base + (1 if shard_index < extra else 0)
        ids = ids[start : start + size]
    if portal:
        ids = [portal] if portal in ids else []
    return ids


def select(args: argparse.Namespace) -> None:
    catalog = read_catalog(args.catalog)
    ids = selected_ids(catalog, args.portal, args.shard_index, args.shard_total)
    if ids:
        print("\n".join(ids))


def validate_result_data(data: dict, expected_portal: str) -> None:
    if set(data) != RESULT_FIELDS:
        raise ValueError("REAL_E2E result schema mismatch")
    if data["schemaVersion"] != 1 or data["portalId"] != expected_portal:
        raise ValueError("REAL_E2E result identity mismatch")
    if not PORTAL_RE.fullmatch(data["portalId"]) or not PORTAL_RE.fullmatch(data["profileId"]):
        raise ValueError("unsafe result identity")
    if data["classification"] not in CLASSIFICATIONS:
        raise ValueError("unknown result classification")
    if not isinstance(data["level"], int) or data["level"] not in range(0, 6):
        raise ValueError("invalid result level")
    if not isinstance(data["capabilities"], list) or any(
        not isinstance(value, str) or not TOKEN_RE.fullmatch(value) for value in data["capabilities"]
    ):
        raise ValueError("unsafe capability token")
    for field in ("expectedStartHost", "currentHost"):
        value = data[field]
        if value is not None and (not isinstance(value, str) or not HOST_RE.fullmatch(value)):
            raise ValueError(f"unsafe host field: {field}")
    for field in BOOLEAN_FIELDS:
        if not isinstance(data[field], bool):
            raise ValueError(f"{field} must be boolean")
    for field in ("signingFailureCode", "infrastructureError"):
        value = data[field]
        if value is not None and (not isinstance(value, str) or not TOKEN_RE.fullmatch(value)):
            raise ValueError(f"unsafe token field: {field}")


def validate_result(args: argparse.Namespace) -> None:
    data = json.loads(args.result.read_text(encoding="ascii"))
    validate_result_data(data, args.portal)


def describe_result(args: argparse.Namespace) -> None:
    data = json.loads(args.result.read_text(encoding="ascii"))
    validate_result_data(data, args.portal)
    infrastructure = data["infrastructureError"] or "NONE"
    signing = data["signingFailureCode"] or "NONE"
    print(
        "RESULT_DIAGNOSTIC "
        f"portal={data['portalId']} "
        f"classification={data['classification']} "
        f"level={data['level']} "
        f"infrastructure={infrastructure} "
        f"signing={signing}"
    )


def validate_log(args: argparse.Namespace) -> None:
    raw = args.log.read_bytes()
    if len(raw) > 65_536:
        raise ValueError("sanitized navigation journal exceeds 64 KiB")
    text = raw.decode("ascii")
    for number, line in enumerate(text.splitlines(), 1):
        if not line:
            continue
        if len(line.encode("ascii")) > 4_096:
            raise ValueError(f"record {number} is oversized")
        if not (line.startswith("timestamp=") or line.startswith("event=")):
            raise ValueError(f"record {number} has an invalid prefix")
        match = EVENT_RE.search(line)
        if match is None or match.group(1) not in ALLOWED_LOG_EVENTS:
            raise ValueError(f"record {number} has a disallowed event")


def validate_progress(args: argparse.Namespace) -> None:
    raw = args.progress.read_bytes()
    if len(raw) > 262_144:
        raise ValueError("REAL_E2E progress journal exceeds 256 KiB")
    text = raw.decode("ascii")
    for number, line in enumerate(text.splitlines(), 1):
        fields = line.split("\t")
        if len(fields) != 6:
            raise ValueError(f"progress record {number} has wrong field count")
        timestamp, shard, index, portal, stage, elapsed = fields
        if not PROGRESS_TIMESTAMP_RE.fullmatch(timestamp):
            raise ValueError(f"progress record {number} has unsafe timestamp")
        if not shard.isdigit() or not 0 <= int(shard) < 32:
            raise ValueError(f"progress record {number} has invalid shard")
        if not PROGRESS_INDEX_RE.fullmatch(index):
            raise ValueError(f"progress record {number} has invalid index")
        current, total = (int(value) for value in index.split("/"))
        if current > 183 or total > 183 or current > total:
            raise ValueError(f"progress record {number} has impossible index")
        if portal != "-" and not PORTAL_RE.fullmatch(portal):
            raise ValueError(f"progress record {number} has unsafe portal id")
        if stage not in PROGRESS_STAGES and not INSTRUMENT_DONE_RE.fullmatch(stage):
            raise ValueError(f"progress record {number} has unsafe stage")
        if not elapsed.isdigit() or int(elapsed) > 86_400:
            raise ValueError(f"progress record {number} has invalid elapsed time")


def validate_partial_results(args: argparse.Namespace) -> None:
    catalog = read_catalog(args.catalog)
    allowed = {entry["portalId"] for entry in catalog["entries"]}
    if not args.results.exists():
        return
    paths = sorted(args.results.iterdir())
    if len(paths) > len(allowed):
        raise ValueError("too many partial REAL_E2E results")
    for path in paths:
        if not path.is_file() or path.suffix != ".json":
            raise ValueError("unexpected file in partial REAL_E2E results")
        portal = path.stem
        if portal not in allowed or not PORTAL_RE.fullmatch(portal):
            raise ValueError("unknown partial REAL_E2E portal")
        data = json.loads(path.read_text(encoding="ascii"))
        validate_result_data(data, portal)


def synthetic(args: argparse.Namespace) -> None:
    catalog = read_catalog(args.catalog)
    entry = next(item for item in catalog["entries"] if item["portalId"] == args.portal)
    host = urlsplit(entry["entryUrl"]).hostname
    data = {
        "schemaVersion": 1,
        "portalId": args.portal,
        "profileId": entry["profileId"],
        "classification": "INFRASTRUCTURE_ERROR",
        "level": 0,
        "capabilities": [],
        "expectedStartHost": host,
        "currentHost": None,
        **{field: False for field in BOOLEAN_FIELDS},
        "signingFailureCode": None,
        "infrastructureError": args.reason,
    }
    validate_result_data(data, args.portal)
    args.output.write_text(json.dumps(data, separators=(",", ":"), sort_keys=True), encoding="ascii")


def summary(args: argparse.Namespace) -> None:
    catalog = read_catalog(args.catalog)
    expected = selected_ids(catalog, args.portal, args.shard_index, args.shard_total)
    results = []
    for portal in expected:
        path = args.results / f"{portal}.json"
        if not path.is_file():
            raise ValueError(f"missing result for {portal}")
        data = json.loads(path.read_text(encoding="ascii"))
        validate_result_data(data, portal)
        results.append(data)
    if len(results) != len(expected) or len({result["portalId"] for result in results}) != len(expected):
        raise ValueError("REAL_E2E result coverage mismatch")
    classes = Counter(result["classification"] for result in results)
    levels = Counter(result["level"] for result in results)
    output = {
        "schemaVersion": 1,
        "catalogVersion": catalog["catalogVersion"],
        "sourceRevision": catalog["sourceRevision"],
        "total": len(results),
        "classifications": dict(sorted(classes.items())),
        "levels": {str(key): value for key, value in sorted(levels.items())},
        "results": results,
    }
    args.json_output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="ascii")
    lines = [
        "# REAL E2E catalog report",
        "",
        f"Total catalog cards: **{len(results)}**",
        "",
        "## Classification counts",
        "",
        "| Classification | Count |",
        "|---|---:|",
    ]
    lines.extend(f"| `{key}` | {value} |" for key, value in sorted(classes.items()))
    lines.extend(
        [
            "",
            "## Portal results",
            "",
            "| Portal | Profile | Level | Classification |",
            "|---|---|---:|---|",
        ]
    )
    lines.extend(
        f"| `{result['portalId']}` | `{result['profileId']}` | {result['level']} | "
        f"`{result['classification']}` |"
        for result in results
    )
    args.markdown_output.write_text("\n".join(lines) + "\n", encoding="ascii")
    print(f"total={len(results)}")
    for key, value in sorted(classes.items()):
        print(f"{key}={value}")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    sub = root.add_subparsers(dest="command", required=True)

    p = sub.add_parser("select")
    p.add_argument("--catalog", type=Path, required=True)
    p.add_argument("--portal", default="")
    p.add_argument("--shard-index", type=int)
    p.add_argument("--shard-total", type=int)
    p.set_defaults(func=select)

    p = sub.add_parser("validate-result")
    p.add_argument("--result", type=Path, required=True)
    p.add_argument("--portal", required=True)
    p.set_defaults(func=validate_result)

    p = sub.add_parser("describe-result")
    p.add_argument("--result", type=Path, required=True)
    p.add_argument("--portal", required=True)
    p.set_defaults(func=describe_result)

    p = sub.add_parser("validate-log")
    p.add_argument("--log", type=Path, required=True)
    p.set_defaults(func=validate_log)

    p = sub.add_parser("validate-progress")
    p.add_argument("--progress", required=True, type=Path)
    p.set_defaults(func=validate_progress)

    p = sub.add_parser("validate-partial-results")
    p.add_argument("--catalog", required=True, type=Path)
    p.add_argument("--results", required=True, type=Path)
    p.set_defaults(func=validate_partial_results)

    p = sub.add_parser("synthetic")
    p.add_argument("--catalog", type=Path, required=True)
    p.add_argument("--portal", required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--reason", required=True, choices=("TIMEOUT", "RESULT_MISSING"))
    p.set_defaults(func=synthetic)

    p = sub.add_parser("summary")
    p.add_argument("--catalog", type=Path, required=True)
    p.add_argument("--results", type=Path, required=True)
    p.add_argument("--portal", default="")
    p.add_argument("--shard-index", type=int)
    p.add_argument("--shard-total", type=int)
    p.add_argument("--json-output", type=Path, required=True)
    p.add_argument("--markdown-output", type=Path, required=True)
    p.set_defaults(func=summary)
    return root


def main() -> None:
    args = parser().parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
