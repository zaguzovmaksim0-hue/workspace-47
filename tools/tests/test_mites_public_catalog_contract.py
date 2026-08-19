#!/usr/bin/env python3
"""Exact MITES public-catalog/profile binding contract."""

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


class MitesPublicCatalogContractTest(unittest.TestCase):
    def test_mites_entry_is_bound_to_only_the_observed_certificate_login_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "age-ministerio-de-trabajo-y-economia-social"
        )

        self.assertEqual("ES-PUB-0074", entry["inventoryId"])
        self.assertEqual("https://sede.mites.gob.es/", entry["entryUrl"])
        self.assertEqual("mites-certificate-login", entry["profileId"])
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual(
            ["AUTOSCRIPT", "CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE", "MINIAPPLET"],
            entry["observedMechanisms"],
        )
        self.assertEqual(["CADES"], entry["observedSignatureFormats"])
        self.assertEqual("AUTOSCRIPT_LOCAL_CADES_IMPLICIT", entry["protocolFamily"])
        self.assertEqual("2026-08-17", entry["reviewedOn"])
        self.assertIn("e2e", entry["limitations"].lower())

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(item for item in profiles if item["profileId"] == "mites-certificate-login")
        self.assertEqual(["https://sede.mites.gob.es"], profile["initiatorOrigins"])
        self.assertEqual([], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertEqual([], profile["endpoints"])
        operation = profile["operationPolicies"][0]
        self.assertEqual(["SHA512_WITH_RSA"], operation["algorithms"])
        self.assertEqual("CADES", operation["format"])
        self.assertEqual("IMPLICIT", operation["mode"])
        self.assertEqual(
            {
                "mode": "implicit",
                "filters.1": "signingCert:;keyusage.nonrepudiation:true;nonexpired:",
            },
            operation["fixedExtraProperties"],
        )
        self.assertNotIn("expinterweb.mites.gob.es", profile["initiatorOrigins"])


if __name__ == "__main__":
    unittest.main()
