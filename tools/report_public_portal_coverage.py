#!/usr/bin/env python3
"""Report the canonical public-portal implementation counts for this checkout."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = ROOT / "app" / "src" / "main" / "res" / "raw" / "public_portal_catalog_v1.json"
IMPLEMENTED_STATUSES = {"VERIFIED_E2E", "IMPLEMENTED_NOT_E2E"}


def summarize(catalog_path: Path = DEFAULT_CATALOG) -> dict[str, Any]:
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    entries = catalog.get("entries")
    if not isinstance(entries, list):
        raise ValueError("public portal catalog has no entries list")

    inventory = Counter(str(entry.get("inventoryStatus")) for entry in entries)
    catalog_status = Counter(str(entry.get("catalogStatus")) for entry in entries)
    discovery = Counter(str(entry.get("discoveryState")) for entry in entries)
    implemented = sum(inventory[status] for status in IMPLEMENTED_STATUSES)

    return {
        "total": len(entries),
        "verified_e2e": inventory["VERIFIED_E2E"],
        "implemented_not_e2e": inventory["IMPLEMENTED_NOT_E2E"],
        "implemented_total": implemented,
        "remaining": len(entries) - implemented,
        "inventory_status": dict(sorted(inventory.items())),
        "catalog_status": dict(sorted(catalog_status.items())),
        "discovery_state": dict(sorted(discovery.items())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Report public-portal coverage from the generated catalog in this checkout."
    )
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args()

    summary = summarize(args.catalog)
    if args.as_json:
        print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
        return 0

    print(f"total={summary['total']}")
    print(f"verified_e2e={summary['verified_e2e']}")
    print(f"implemented_not_e2e={summary['implemented_not_e2e']}")
    print(f"implemented_total={summary['implemented_total']}")
    print(f"remaining={summary['remaining']}")
    print("inventory_status=" + json.dumps(summary["inventory_status"], sort_keys=True))
    print("catalog_status=" + json.dumps(summary["catalog_status"], sort_keys=True))
    print("discovery_state=" + json.dumps(summary["discovery_state"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
