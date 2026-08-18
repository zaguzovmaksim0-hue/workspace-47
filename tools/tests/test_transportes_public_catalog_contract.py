#!/usr/bin/env python3
"""Exact Transportes ES-PUB-0075 public-catalog binding contract."""
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

class TransportesPublicCatalogContractTest(unittest.TestCase):
    def test_transportes_qys_is_bounded_to_current_first_party_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(x for x in catalog["entries"] if x["inventoryId"] == "ES-PUB-0075")
        self.assertEqual("transportes-qys-cert-login", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual(
            "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual(["XADES"], entry["observedSignatureFormats"])

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(x for x in profiles if x["profileId"] == "transportes-qys-cert-login")
        self.assertEqual(["https://sede.transportes.gob.es"], profile["initiatorOrigins"])
        self.assertEqual([], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertEqual([], profile["endpoints"])
        self.assertNotIn("fire.transportes.gob.es", json.dumps(profile))
        op = profile["operationPolicies"][0]
        self.assertEqual(["SHA1_WITH_RSA"], op["algorithms"])
        self.assertEqual("XADES", op["format"])
        self.assertEqual("ATTACHED", op["packaging"])
        self.assertEqual("XAdES Enveloped", op["fixedExtraProperties"]["format"])
        self.assertEqual("tag1", op["fixedExtraProperties"]["nodeToSign"])

if __name__ == "__main__":
    unittest.main()
