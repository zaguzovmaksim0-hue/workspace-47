#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit

PORTAL_RE = re.compile(r"[a-z0-9][a-z0-9-]{0,95}")
TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
RESULT_SCHEMA_VERSION = 2
HOST_RE = re.compile(
    r"(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+"
    r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
)
CLASSIFICATIONS = {
    "PENDING",
    "PASS_BROWSE",
    "PASS_MECHANISM_BOUNDARY",
    "PASS_CLIENT_TLS",
    "PASS_REAL_CRYPTO_SIGN",
    "PASS_CRYPTO_CALLBACK",
    "PASS_PORTAL_AUTH",
    "BLOCKED_CONSEQUENTIAL_ACTION",
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
    "signingCallbackObserved",
    "postSignNavigationObserved",
    "postSignPageFinished",
    "postSignCallbackObserved",
    "postSignHostChanged",
    "postSignPathChanged",
    "authenticatedReturnObserved",
    "postSignPortalAuthSuccess",
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
    "BLANK_DOCUMENT",
    "MINIAPPLET_BRIDGE",
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
    "DEVICE_PREPARE_START",
    "DEVICE_PREPARE_DONE",
    "DEVICE_LOST",
    "EMULATOR_RESTART_START",
    "EMULATOR_RESTART_DONE",
    "EMULATOR_RESTART_FAILED",
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
    requested: list[str] | None = None
    if portal:
        requested = portal.split(",")
        if (
            len(requested) > 32
            or len(requested) != len(set(requested))
            or any(not value or not PORTAL_RE.fullmatch(value) or value not in ids for value in requested)
        ):
            raise ValueError("portal filter must contain 1-32 unique bundled portal ids separated by commas")
    if (shard_index is None) != (shard_total is None):
        raise ValueError("shard index and total must be supplied together")
    if requested is not None and len(requested) > 1 and shard_total not in (None, 1):
        raise ValueError("portal filter cannot be combined with multi-shard selection")
    if shard_total is not None:
        if shard_total < 1 or shard_total > 32 or shard_index is None or not 0 <= shard_index < shard_total:
            raise ValueError("invalid shard selection")
        base, extra = divmod(len(ids), shard_total)
        start = shard_index * base + min(shard_index, extra)
        size = base + (1 if shard_index < extra else 0)
        ids = ids[start : start + size]
    if requested is not None:
        shard_ids = set(ids)
        ids = [value for value in requested if value in shard_ids]
    return ids


def select(args: argparse.Namespace) -> None:
    catalog = read_catalog(args.catalog)
    ids = selected_ids(catalog, args.portal, args.shard_index, args.shard_total)
    if ids:
        print("\n".join(ids))


def validate_result_data(data: dict, expected_portal: str) -> None:
    if set(data) != RESULT_FIELDS:
        raise ValueError("REAL_E2E result schema mismatch")
    if data["schemaVersion"] != RESULT_SCHEMA_VERSION or data["portalId"] != expected_portal:
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
        "schemaVersion": RESULT_SCHEMA_VERSION,
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


INSTRUMENT_READ_LIMIT = 1_048_576
GUEST_READ_LIMIT = 4_194_304
MINIDUMP_READ_LIMIT = 268_435_456
JAVA_CLASS_RE = re.compile(r"(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Z_$][A-Za-z0-9_$]*")
SAFE_MESSAGE_RE = re.compile(r"[A-Za-z][A-Za-z0-9 ._#$-]{0,79}")
SAFE_NAME_RE = re.compile(r"[A-Za-z0-9_.:@+!\[\]-]{1,160}")
SAFE_SYMBOL_RE = re.compile(r"[A-Za-z0-9_:~.+$]{1,100}")
SAFE_THREAD_RE = re.compile(r"[A-Za-z0-9_.:@+!#\[\] -]{1,40}")
GUEST_LINE_RE = re.compile(r"([VDIWEFA])/([^(]{1,64}?)\s*\(\s*\d+\): ?(.*)")
NATIVE_SIGNAL_RE = re.compile(
    r"Fatal signal (\d{1,2}) \((SIG[A-Z0-9]{2,8})\), code (-?\d{1,3})"
    r"(?: \(([A-Z_]{2,24})\))?.*? in tid \d+ \(([^()]{0,40})\), pid \d+ \(([^()]{0,40})\)"
)
TOMBSTONE_NAME_RE = re.compile(r"pid: \d+, tid: \d+, name: (\S{1,40})\s+>>> (\S{1,80}) <<<")
TOMBSTONE_FRAME_RE = re.compile(r"#(\d{2}) pc [0-9a-f]{4,16}\s+(\S{1,512})(.*)")
FATAL_TOKEN_RE = re.compile(r"\[FATAL:([A-Za-z0-9_./-]{1,80}\(\d{1,6}\))\]")
INTERESTING_PROCESS_MARKERS = (
    "firmamobile",
    "webview",
    "sandboxed_process",
    "chrome",
    "system_server",
    "surfaceflinger",
    "graphics",
)
SIGNAL_NAMES = {
    4: "SIGILL",
    5: "SIGTRAP",
    6: "SIGABRT",
    7: "SIGBUS",
    8: "SIGFPE",
    9: "SIGKILL",
    11: "SIGSEGV",
    13: "SIGPIPE",
    15: "SIGTERM",
}


def read_bounded_text(path: Path, limit: int) -> str:
    try:
        with path.open("rb") as handle:
            raw = handle.read(limit)
    except OSError:
        return ""
    return raw.decode("utf-8", errors="replace")


def safe_value(value: str, pattern: re.Pattern[str] = SAFE_NAME_RE) -> str:
    value = value.strip()
    if pattern.fullmatch(value):
        return value.replace(" ", "_")
    return "REDACTED"


def safe_portal(value: str) -> str:
    return value if PORTAL_RE.fullmatch(value) else "invalid"


def java_class_prefix(text: str) -> str | None:
    match = JAVA_CLASS_RE.match(text.strip())
    if match is None:
        return None
    rest = text.strip()[match.end():]
    if rest and not rest.startswith((":", " ")):
        return None
    return match.group(0)


def interesting_process(name: str) -> bool:
    lowered = name.lower()
    return any(marker in lowered for marker in INTERESTING_PROCESS_MARKERS)


def describe_instrumentation(args: argparse.Namespace) -> None:
    """Summarize `am instrument -r` output without printing messages or stack text."""
    text = read_bounded_text(args.output, INSTRUMENT_READ_LIMIT)
    status_codes: list[str] = []
    exception = caused_by = result = code = tests = failures = adb_error = None
    flags: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.rstrip("\r")
        if match := re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d{1,3})", line):
            if not status_codes or status_codes[-1] != match.group(1):
                status_codes.append(match.group(1))
        elif line.startswith("INSTRUMENTATION_STATUS: stack="):
            exception = exception or java_class_prefix(line.split("=", 1)[1])
        elif match := re.match(r"\s*Caused by: (.*)", line):
            caused_by = caused_by or java_class_prefix(match.group(1))
        elif line.startswith("INSTRUMENTATION_RESULT: shortMsg="):
            result = safe_value(line.split("=", 1)[1], SAFE_MESSAGE_RE)
        elif match := re.fullmatch(r"INSTRUMENTATION_CODE: (-?\d{1,3})", line):
            code = match.group(1)
        elif line.startswith("INSTRUMENTATION_FAILED:"):
            flags.append("instrumentation_failed")
        elif line.startswith("INSTRUMENTATION_ABORTED:"):
            flags.append("aborted")
            result = safe_value(line.split(":", 1)[1], SAFE_MESSAGE_RE)
        elif line.startswith(("adb: ", "error: ", "adb: error: ")):
            lowered = line.lower()
            if "offline" in lowered:
                adb_error = "device_offline"
            elif "no devices" in lowered or "not found" in lowered:
                adb_error = "no_device"
            elif "closed" in lowered:
                adb_error = "closed"
            else:
                adb_error = adb_error or "other"
        if match := re.search(r"\bOK \((\d{1,4}) tests?\)", line):
            tests, failures = match.group(1), "0"
        elif match := re.search(r"Tests run: (\d{1,4}),\s+Failures: (\d{1,4})", line):
            tests, failures = match.group(1), match.group(2)
    print(
        "REAL_E2E_INSTRUMENTATION "
        f"portal={safe_portal(args.portal)} "
        f"status={args.status} "
        f"bytes={len(text.encode('utf-8'))} "
        f"code={code or '-'} "
        f"result={result or '-'} "
        f"tests={tests or '-'} "
        f"failures={failures or '-'} "
        f"status_codes={','.join(status_codes[:8]) or '-'} "
        f"exception={exception or '-'} "
        f"caused_by={caused_by or '-'} "
        f"flags={','.join(flags) or '-'} "
        f"adb_error={adb_error or '-'}"
    )


def tombstone_frame(match: re.Match[str]) -> str:
    module = safe_value(match.group(2).rsplit("/", 1)[-1])
    rest = match.group(3)
    rest = re.sub(r"^\s*\(offset 0x[0-9a-f]+\)", "", rest).lstrip()
    symbol = "-"
    if rest.startswith("(") and not rest.startswith("(BuildId:"):
        depth = 0
        for position, character in enumerate(rest):
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
                if depth == 0:
                    inner = re.sub(r"\([^()]*\)", "", rest[1:position])
                    inner = re.sub(r"[^A-Za-z0-9_:~.+$]", "", inner)[:100]
                    symbol = inner if SAFE_SYMBOL_RE.fullmatch(inner) else "-"
                    break
    return f"frame={match.group(1)} module={module} symbol={symbol}"


def describe_guest_log(args: argparse.Namespace) -> None:
    """Summarize guest crash/kill records as signal, names and class tokens only."""
    portal = safe_portal(args.portal)
    text = read_bounded_text(args.log, GUEST_READ_LIMIT)
    records: list[str] = []
    lmk_kills = 0
    frames_in_tombstone = 0
    tombstones = 0
    java_pending = False
    java_thread = java_process = java_exception = None
    java_causes: list[str] = []

    def flush_java() -> None:
        nonlocal java_pending, java_thread, java_process, java_exception, java_causes
        if java_pending:
            records.append(
                "kind=java "
                f"thread={java_thread or '-'} process={java_process or '-'} "
                f"exception={java_exception or '-'} caused_by={','.join(java_causes[:2]) or '-'}"
            )
        java_pending = False
        java_thread = java_process = java_exception = None
        java_causes = []

    for raw_line in text.splitlines():
        match = GUEST_LINE_RE.fullmatch(raw_line.rstrip("\r"))
        if match is None:
            continue
        tag = match.group(2).strip()
        message = match.group(3)
        if tag == "AndroidRuntime":
            if fatal := re.match(r"\*?\*?\*? ?FATAL EXCEPTION(?: IN SYSTEM PROCESS)?: (\S{1,40})", message):
                flush_java()
                java_pending = True
                java_thread = safe_value(fatal.group(1), SAFE_THREAD_RE)
                if "SYSTEM PROCESS" in message:
                    java_process = "system_server"
            elif java_pending and (process := re.match(r"Process: ([^,\s]{1,80}), PID: \d+", message)):
                java_process = safe_value(process.group(1))
            elif java_pending and (cause := re.match(r"\s*Caused by: (.*)", message)):
                if (name := java_class_prefix(cause.group(1))) is not None:
                    java_causes.append(name)
            elif java_pending and java_exception is None and not message.lstrip().startswith("at "):
                java_exception = java_class_prefix(message)
            continue
        flush_java()
        if signal := NATIVE_SIGNAL_RE.search(message):
            records.append(
                "kind=native "
                f"signal={signal.group(2)}({signal.group(1)}) code={signal.group(4) or signal.group(3)} "
                f"thread={safe_value(signal.group(5), SAFE_THREAD_RE)} process={safe_value(signal.group(6))}"
            )
        elif "*** *** *** *** ***" in message:
            tombstones += 1
            frames_in_tombstone = 0
        elif name := TOMBSTONE_NAME_RE.search(message):
            records.append(
                f"kind=tombstone thread={safe_value(name.group(1), SAFE_THREAD_RE)} process={safe_value(name.group(2))}"
            )
        elif message.startswith("Abort message: "):
            token = FATAL_TOKEN_RE.search(message)
            records.append(f"kind=abort token={('FATAL:' + token.group(1)) if token else 'present'}")
        elif frame := TOMBSTONE_FRAME_RE.search(message):
            if tombstones <= 3 and frames_in_tombstone < 8:
                frames_in_tombstone += 1
                records.append("kind=frame " + tombstone_frame(frame))
        elif tag in {"am_crash", "am_kill", "am_proc_died", "am_anr"}:
            fields = message.strip().strip("[]").split(",")
            if len(fields) < 3 or not interesting_process(fields[2]):
                continue
            process = safe_value(fields[2])
            if tag == "am_crash":
                exception = fields[4] if len(fields) > 4 else ""
                exception = java_class_prefix(exception) or safe_value(exception, SAFE_MESSAGE_RE)
                records.append(f"kind=am_crash process={process} exception={exception}")
            elif tag == "am_kill":
                reason = fields[4] if len(fields) > 4 else ""
                records.append(f"kind=am_kill process={process} reason={safe_value(reason, SAFE_MESSAGE_RE)}")
            elif tag == "am_proc_died":
                records.append(f"kind=proc_died process={process}")
            else:
                records.append(f"kind=anr process={process}")
        elif tag == "am_low_memory":
            records.append("kind=low_memory")
        elif tag in {"lowmemorykiller", "lmkd"}:
            if kill := re.search(r"Kill '([^']{1,80})'", message):
                lmk_kills += 1
                if interesting_process(kill.group(1)):
                    records.append(f"kind=lmk_kill process={safe_value(kill.group(1))}")
        elif tag == "cr_AwBrowserTerminator" and "wasn't hand" in message:
            records.append("kind=webview_renderer_gone_unhandled")
    flush_java()

    counts = Counter(records)
    ordered = list(dict.fromkeys(records))
    for record in ordered[:24]:
        print(f"REAL_E2E_GUEST portal={portal} {record} count={counts[record]}")
    print(
        f"REAL_E2E_GUEST_SUMMARY portal={portal} records={len(records)} "
        f"distinct={len(ordered)} tombstones={tombstones} lmk_kills={lmk_kills}"
    )


def describe_minidump(args: argparse.Namespace) -> None:
    """Summarize a host emulator crash dump as signal and host module basenames only."""
    import struct

    try:
        with args.dump.open("rb") as handle:
            data = handle.read(MINIDUMP_READ_LIMIT)
        signature, _version, stream_count, directory_rva = struct.unpack_from("<4sIII", data, 0)
        if signature != b"MDMP":
            raise ValueError("not a minidump")
        streams: dict[int, tuple[int, int]] = {}
        for index in range(min(stream_count, 256)):
            stream_type, size, rva = struct.unpack_from("<III", data, directory_rva + 12 * index)
            streams.setdefault(stream_type, (size, rva))

        modules: list[tuple[int, int, str]] = []
        if 4 in streams:
            size, rva = streams[4]
            (count,) = struct.unpack_from("<I", data, rva)
            header = 8 if size == 8 + 108 * count else 4
            for index in range(min(count, 4096)):
                base = rva + header + 108 * index
                image_base, image_size = struct.unpack_from("<QI", data, base)
                (name_rva,) = struct.unpack_from("<I", data, base + 20)
                (name_length,) = struct.unpack_from("<I", data, name_rva)
                raw_name = data[name_rva + 4:name_rva + 4 + min(name_length, 2048)]
                name = raw_name.decode("utf-16-le", errors="replace").rsplit("/", 1)[-1]
                modules.append((image_base, image_size, safe_value(name)))

        def module_for(address: int) -> str | None:
            for image_base, image_size, name in modules:
                if image_base <= address < image_base + image_size:
                    return name
            return None

        signal_name = "-"
        si_code = "-"
        pc_module = "-"
        stack_modules: list[str] = []
        if 6 in streams:
            _size, rva = streams[6]
            thread_id, _alignment, exception_code, exception_flags = struct.unpack_from("<IIII", data, rva)
            signal_name = f"{SIGNAL_NAMES.get(exception_code, 'SIG')}({exception_code})"
            si_code = str(exception_flags if exception_flags < 2**31 else exception_flags - 2**32)
            context_size, context_rva = struct.unpack_from("<II", data, rva + 160)
            architecture = None
            if 7 in streams:
                (architecture,) = struct.unpack_from("<H", data, streams[7][1])
            if architecture == 9 and context_size >= 256:
                (pc,) = struct.unpack_from("<Q", data, context_rva + 248)
                pc_module = module_for(pc) or "unknown"
            if 3 in streams:
                size, rva = streams[3]
                (thread_count,) = struct.unpack_from("<I", data, rva)
                header = 8 if size == 8 + 48 * thread_count else 4
                for index in range(min(thread_count, 4096)):
                    base = rva + header + 48 * index
                    (candidate,) = struct.unpack_from("<I", data, base)
                    if candidate != thread_id:
                        continue
                    stack_size, stack_rva = struct.unpack_from("<II", data, base + 32)
                    stack = data[stack_rva:stack_rva + min(stack_size, 65_536)]
                    for offset in range(0, len(stack) - 7, 8):
                        (word,) = struct.unpack_from("<Q", stack, offset)
                        name = module_for(word)
                        if name and name not in stack_modules:
                            stack_modules.append(name)
                            if len(stack_modules) >= 12:
                                break
                    break
        print(
            "REAL_E2E_HOST_MINIDUMP "
            f"bytes={len(data)} modules={len(modules)} signal={signal_name} code={si_code} "
            f"pc_module={pc_module} stack_modules={','.join(stack_modules) or '-'}"
        )
    except Exception as error:  # noqa: BLE001 - diagnostics must never fail the shard
        print(f"REAL_E2E_HOST_MINIDUMP unreadable={type(error).__name__}")


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
    p.add_argument("--reason", required=True, choices=("TIMEOUT", "RESULT_MISSING", "EMULATOR_LOST"))
    p.set_defaults(func=synthetic)

    p = sub.add_parser("describe-instrumentation")
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--portal", required=True)
    p.add_argument("--status", type=int, required=True)
    p.set_defaults(func=describe_instrumentation)

    p = sub.add_parser("describe-guest-log")
    p.add_argument("--log", type=Path, required=True)
    p.add_argument("--portal", required=True)
    p.set_defaults(func=describe_guest_log)

    p = sub.add_parser("describe-minidump")
    p.add_argument("--dump", type=Path, required=True)
    p.set_defaults(func=describe_minidump)

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
    try:
        args.func(args)
    except (OSError, ValueError, KeyError, TypeError) as error:
        # Validation failures are expected for missing or partial device files; report the reviewed
        # message without a traceback and fail the command.
        print(f"real_e2e_report {args.command}: {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
