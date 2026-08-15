#!/usr/bin/env python3
"""Exact Junta de Andalucía Sede (VEA) public-catalog binding contract."""

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


class JuntaSedePublicCatalogContractTest(unittest.TestCase):
    def test_junta_sede_entry_is_bound_without_e2e_or_endpoint_claim(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["portalId"] == "junta-andalucia-sede")

        self.assertEqual("https://veaja.cloud.juntadeandalucia.es/inicio/", entry["entryUrl"])
        self.assertEqual("junta-andalucia-sede", entry["profileId"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual(
            ["AUTOSCRIPT", "CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"],
            entry["observedMechanisms"],
        )
        self.assertEqual(["CADES"], entry["observedSignatureFormats"])
        self.assertEqual("AUTOSCRIPT", entry["protocolFamily"])
        self.assertEqual("2026-08-15", entry["reviewedOn"])
        self.assertIn("e2e", entry["limitations"].lower())


if __name__ == "__main__":
    unittest.main()
