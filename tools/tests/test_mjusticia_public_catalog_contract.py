from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
SPEC = importlib.util.spec_from_file_location("mjusticia_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class MJusticiaPublicCatalogContractTest(unittest.TestCase):
    def test_mjusticia_binds_only_exact_public_fundaciones_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0010")
        self.assertEqual("mjusticia-sede", entry["portalId"])
        self.assertEqual("mjusticia-fundaciones-idp75", entry["profileId"])
        self.assertEqual(
            "https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("MJUSTICIA_SEDE2_PUBLIC_LAUNCH", entry["protocolFamily"])
        self.assertEqual(["ELECTRONIC_SIGNATURE"], entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("qa-only", entry["limitations"].lower())
        self.assertIn("no se exponen", entry["limitations"].lower())

        profile = next(
            p for p in json.loads(PROFILES.read_text())["profiles"]
            if p["profileId"] == "mjusticia-fundaciones-idp75"
        )
        self.assertEqual("VERIFIED_CONTRACT", profile["compatibilityStatus"])
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual(["https://sede2.mjusticia.gob.es"], profile["initiatorOrigins"])
        self.assertEqual([], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertEqual([], profile["capabilities"])
        self.assertIsNone(profile["clientAuthPolicy"])


if __name__ == "__main__":
    unittest.main()
