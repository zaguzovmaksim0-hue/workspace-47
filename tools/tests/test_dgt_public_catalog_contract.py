#!/usr/bin/env python3
"""Exact DGT public-catalog binding contract."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"


SPEC = importlib.util.spec_from_file_location("public_catalog_generator", GENERATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("catalog generator could not be loaded")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class DgtPublicCatalogContractTest(unittest.TestCase):
    def test_dgt_entry_is_bound_without_e2e_or_endpoint_claim(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["portalId"] == "dgt-sede")

        self.assertEqual(
            "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/",
            entry["entryUrl"],
        )
        self.assertEqual("dgt-verificacion-equipo", entry["profileId"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual(
            ["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE", "MINIAPPLET"],
            entry["observedMechanisms"],
        )
        self.assertEqual(["CADES"], entry["observedSignatureFormats"])
        self.assertEqual("MINIAPPLET_LOCAL_CADES", entry["protocolFamily"])
        self.assertEqual("2026-08-09", entry["reviewedOn"])
        limitations = entry["limitations"].lower()
        self.assertIn("no se ha observado", limitations)
        self.assertIn("endpoint portal-specific", limitations)


if __name__ == "__main__":
    unittest.main()
