#!/usr/bin/env python3
"""Exact Ayuntamiento de Madrid public-catalog/profile navigation contract."""

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


class MadridPublicCatalogContractTest(unittest.TestCase):
    def test_madrid_entry_binds_only_observed_municipal_oidc_navigation(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["portalId"] == "madrid-sede")

        start = (
            "https://sede.madrid.es/portal/site/tramites/"
            "menuitem.62876cb64654a55e2dbd7003a8a409a0/"
            "?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&"
            "vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD"
        )
        self.assertEqual("ES-PUB-0017", entry["inventoryId"])
        self.assertEqual(start, entry["entryUrl"])
        self.assertEqual("madrid-sede-tarjeta-azul", entry["profileId"])
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("OIDC_PKCE_CLAVE_CERTIFICATE_ROUTE", entry["protocolFamily"])
        self.assertEqual(["CERTIFICATE_ACCESS"], entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("tls client auth", entry["limitations"].lower())
        self.assertIn("firma", entry["limitations"].lower())

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(item for item in profiles if item["profileId"] == "madrid-sede-tarjeta-azul")
        self.assertEqual(start, profile["startUrl"])
        self.assertEqual(["https://sede.madrid.es"], profile["initiatorOrigins"])
        self.assertEqual(
            ["https://servcla.madrid.es", "https://cas.madrid.es"],
            profile["redirectOrigins"],
        )
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertEqual([], profile["capabilities"])
        self.assertIsNone(profile["clientAuthPolicy"])
        self.assertNotIn("https://pasarela.clave.gob.es", profile["redirectOrigins"])


if __name__ == "__main__":
    unittest.main()
