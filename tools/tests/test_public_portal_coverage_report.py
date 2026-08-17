#!/usr/bin/env python3
"""Regression checks for canonical portal coverage accounting."""

from __future__ import annotations

from collections import Counter
import importlib.util
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
CATALOG = ROOT / "app" / "src" / "main" / "res" / "raw" / "public_portal_catalog_v1.json"
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
REPORT_PATH = ROOT / "tools" / "report_public_portal_coverage.py"


def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


GENERATOR = _load("public_catalog_generator_for_coverage", GENERATOR_PATH)
REPORT = _load("public_portal_coverage_report", REPORT_PATH)


class PublicPortalCoverageReportTest(unittest.TestCase):
    def test_report_uses_generated_catalog_as_source_of_truth(self) -> None:
        summary = REPORT.summarize(CATALOG)
        self.assertEqual(summary["total"], sum(summary["inventory_status"].values()))
        self.assertEqual(
            summary["implemented_total"],
            summary["verified_e2e"] + summary["implemented_not_e2e"],
        )
        self.assertEqual(summary["remaining"], summary["total"] - summary["implemented_total"])

    def test_inventory_summary_tables_match_embedded_records(self) -> None:
        markdown = INVENTORY.read_text(encoding="utf-8")
        records = GENERATOR._records(markdown)
        inventory_counts = Counter(str(record["inventory_status"]) for record in records)
        discovery_counts = Counter(str(record["discovery_state"]) for record in records)

        for status in (
            "VERIFIED_E2E",
            "IMPLEMENTED_NOT_E2E",
            "VERIFIED_CONTRACT",
            "REQUIRES_AUTHENTICATED_RESEARCH",
            "BROWSE_ONLY",
            "UNSUPPORTED_PROTOCOL",
            "INACCESSIBLE",
            "DEPRECATED",
        ):
            self.assertRegex(
                markdown,
                rf"(?m)^\| `{re.escape(status)}` \| {inventory_counts[status]} \|$",
                msg=f"stale inventory summary count for {status}",
            )

        for status in ("REVIEWED", "RECHECK_REQUIRED", "DISCOVERED"):
            self.assertRegex(
                markdown,
                rf"(?m)^\| `{re.escape(status)}` \| {discovery_counts[status]} \|$",
                msg=f"stale discovery summary count for {status}",
            )
        self.assertIn(
            f"| `CANDIDATE`, `RETIRED` | {discovery_counts['CANDIDATE'] + discovery_counts['RETIRED']} |",
            markdown,
        )

        implemented = inventory_counts["VERIFIED_E2E"] + inventory_counts["IMPLEMENTED_NOT_E2E"]
        remaining = len(records) - implemented
        self.assertIn(f"| Entradas `VERIFIED_E2E` | {inventory_counts['VERIFIED_E2E']} |", markdown)
        self.assertIn(
            f"| Entradas `IMPLEMENTED_NOT_E2E` | {inventory_counts['IMPLEMENTED_NOT_E2E']} |",
            markdown,
        )
        self.assertIn(
            f"| Entradas implementadas (`VERIFIED_E2E` + `IMPLEMENTED_NOT_E2E`) | {implemented} |",
            markdown,
        )
        self.assertIn(f"| Entradas restantes fuera de ambos estados | {remaining} |", markdown)


if __name__ == "__main__":
    unittest.main()
