#!/usr/bin/env python3
"""Exact Sede Judicial private-area public-catalog/profile binding contract."""

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


class JusticiaSedeJudicialPublicCatalogContractTest(unittest.TestCase):
    def test_private_area_binds_only_the_reviewed_justice_identity_gateway(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "justicia-sede-judicial"
        )

        self.assertEqual("ES-PUB-0009", entry["inventoryId"])
        self.assertEqual(
            "https://sedejudicial.justicia.es/group/guest/area-privada",
            entry["entryUrl"],
        )
        self.assertEqual("justicia-sede-judicial-private-area", entry["profileId"])
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual(
            ["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"],
            entry["observedMechanisms"],
        )
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("NO_VERIFICADO", entry["protocolFamily"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("am.justicia.es", entry["limitations"])
        self.assertIn("sin aceptación e2e", entry["limitations"].lower())

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(
            item for item in profiles
            if item["profileId"] == "justicia-sede-judicial-private-area"
        )
        self.assertEqual("VERIFIED_CONTRACT", profile["compatibilityStatus"])
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual(["https://sedejudicial.justicia.es"], profile["initiatorOrigins"])
        self.assertEqual(["https://am.justicia.es"], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertEqual([], profile["capabilities"])
        self.assertIsNone(profile["clientAuthPolicy"])
        self.assertNotIn("https://pasarela.clave.gob.es", profile["redirectOrigins"])
        self.assertNotIn("https://pasarela.clave.gob.es", profile["trustedBrowseOrigins"])


if __name__ == "__main__":
    unittest.main()
