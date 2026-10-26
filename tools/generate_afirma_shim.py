#!/usr/bin/env python3
"""Compose the packaged shim from ordered source sections, without rewriting JS."""
import argparse
import json
import os
from pathlib import Path
import re
import stat
import tempfile

MAX_SCRIPT_CHARS = 96 * 1024
MAX_SOURCE_BYTES = MAX_SCRIPT_CHARS * 4
MAX_PARTS = 64
NAME = re.compile(r"[0-9]{2}-[a-z0-9-]+\.js\Z")
RAW = Path("app/src/main/res/raw")
# Fixed source ranges; Kotlin selects the optional portal resources.
RESOURCE_RANGES = (
    ("afirma_shim_bootstrap.js", 0, 3),
    ("afirma_shim_cantabria.js", 3, 4),
    ("afirma_shim_jccm.js", 4, 5),
    ("afirma_shim_jccm_registro.js", 5, 6),
    ("afirma_shim_airef.js", 6, 7),
    ("afirma_shim_cdti.js", 7, 8),
    ("afirma_shim_policia.js", 8, 9),
    ("afirma_shim_handlers.js", 9, 15),
    ("afirma_shim_installation.js", 15, 18),
)


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate manifest key")
        result[key] = value
    return result


def _regular(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError("expected a regular non-symlink source file")


def _read_parts(root):
    directory = Path(root) / "config/afirma_shim"
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError("missing or linked source directory")
    manifest = directory / "manifest.json"
    _regular(manifest)
    if manifest.stat().st_size > 8192:
        raise ValueError("manifest is too large")
    specification = json.loads(manifest.read_text(encoding="utf-8"), object_pairs_hook=_object)
    if not isinstance(specification, dict) or set(specification) != {"format", "parts"}:
        raise ValueError("invalid manifest shape")
    if type(specification["format"]) is not int or specification["format"] != 1:
        raise ValueError("unsupported manifest format")
    names = specification["parts"]
    if not isinstance(names, list) or not 1 <= len(names) <= MAX_PARTS:
        raise ValueError("invalid source count")
    if any(not isinstance(name, str) or NAME.fullmatch(name) is None for name in names):
        raise ValueError("invalid source name")
    if len(set(names)) != len(names):
        raise ValueError("duplicate source name")
    if {p.name for p in directory.glob("*.js")} != set(names):
        raise ValueError("missing or unlisted JavaScript source")
    parts = []
    total_bytes = 0
    for name in names:
        path = directory / name
        _regular(path)
        size = path.stat().st_size
        if size == 0 or size > MAX_SOURCE_BYTES - total_bytes:
            raise ValueError("source size outside bounds")
        data = path.read_bytes()
        if len(data) != size:
            raise ValueError("source changed while reading")
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError as error:
            raise ValueError("source must be UTF-8") from error
        if "\r" in text or not text.endswith("\n"):
            raise ValueError("source must use LF with a final newline")
        total_bytes += len(data)
        parts.append(data)
    combined = b"".join(parts)
    if len(combined.decode("utf-8").encode("utf-16-le")) // 2 > MAX_SCRIPT_CHARS:
        raise ValueError("script exceeds runtime character bound")
    return parts


def compose(root):
    return b"".join(_read_parts(root))


def synchronize(root, *, write=False):
    parts = _read_parts(root)
    if len(parts) != RESOURCE_RANGES[-1][2]:
        raise ValueError("unexpected packaged source count")
    raw = Path(root) / RAW
    expected = {name for name, _, _ in RESOURCE_RANGES}
    if {p.name for p in raw.glob("afirma_shim*.js")} - expected:
        raise ValueError("unlisted packaged shim fragment")
    changed = False
    for name, start, end in RESOURCE_RANGES:
        changed = _synchronize_output(raw / name, b"".join(parts[start:end]), write=write) or changed
    return changed


def _synchronize_output(output, combined, *, write):
    if output.is_symlink():
        raise ValueError("output must not be a symlink")
    if output.exists():
        _regular(output)
        if output.read_bytes() == combined:
            return False
    if not write:
        raise ValueError("packaged shim is stale; run tools/generate_afirma_shim.py --write")
    mode = stat.S_IMODE(output.stat().st_mode) if output.exists() else 0o644
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".afirma-shim-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(combined)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.chmod(mode)
        if output.is_symlink():
            raise ValueError("output became a symlink")
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    flags = parser.add_mutually_exclusive_group()
    flags.add_argument("--check", action="store_true", help="check only (default)")
    flags.add_argument("--write", action="store_true", help="regenerate the tracked resource")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        changed = synchronize(root, write=args.write)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Shim composition failed: {error}\n")
    print("Shim resource updated" if changed else "Shim resource is up to date")


if __name__ == "__main__":
    main()
