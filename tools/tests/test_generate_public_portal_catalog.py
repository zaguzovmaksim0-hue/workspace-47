#!/usr/bin/env python3
"""Reproducibility checks for the bundled public portal catalog."""

from __future__ import annotations

import importlib.util
import hashlib
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "raw" / "public_portal_catalog_v1.json"

SPEC = importlib.util.spec_from_file_location("public_catalog_generator", GENERATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("catalog generator could not be loaded")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class PublicPortalCatalogGeneratorTest(unittest.TestCase):
    def test_committed_resource_is_byte_for_byte_reproducible(self) -> None:
        catalog = GENERATOR.generate(SOURCE)
        generated = json.dumps(
            catalog,
            ensure_ascii=False,
            indent=2,
            sort_keys=False,
        ) + "\n"

        self.assertEqual(OUTPUT.read_text(encoding="utf-8"), generated)
        inventory_count = sum(entry["inventoryId"] is not None for entry in catalog["entries"])
        self.assertGreaterEqual(inventory_count, GENERATOR.MIN_INVENTORY_RECORDS)
        self.assertEqual(inventory_count + 2, len(catalog["entries"]))
        self.assertEqual(
            7,
            sum(entry["profileId"] is not None for entry in catalog["entries"]),
        )
        aragon = next(entry for entry in catalog["entries"] if entry["portalId"] == "aragon-siraw")
        self.assertEqual("aragon-siraw", aragon["profileId"])
        self.assertEqual("E2E_VERIFIED", aragon["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", aragon["inventoryStatus"])
        ofvirtual = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "junta-andalucia-ofvirtual"
        )
        self.assertEqual("junta-ofvirtual", ofvirtual["profileId"])
        self.assertEqual("E2E_VERIFIED", ofvirtual["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", ofvirtual["inventoryStatus"])
        self.assertEqual("2026-07-29", ofvirtual["reviewedOn"])
        self.assertIn("portal real aceptó", ofvirtual["limitations"].lower())
        self.assertIn("login", ofvirtual["limitations"].lower())
        unizar = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "unizar-tramitador"
        )
        self.assertEqual("unizar-tramitador", unizar["profileId"])
        self.assertEqual("E2E_VERIFIED", unizar["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", unizar["inventoryStatus"])
        self.assertEqual("2026-07-30", unizar["reviewedOn"])
        self.assertIn("portal real aceptó", unizar["limitations"].lower())
        self.assertIn("autenticación", unizar["limitations"].lower())
        self.assertEqual(
            hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            catalog["sourceRevision"],
        )


if __name__ == "__main__":
    unittest.main()
