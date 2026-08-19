from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
SPEC = importlib.util.spec_from_file_location("catalunya_seu_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class CatalunyaSeuPublicCatalogContractTest(unittest.TestCase):
    def test_seu_entry_binds_only_the_observed_valid_client_tls_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0104")
        self.assertEqual("catalunya-seu-electronica", entry["portalId"])
        self.assertEqual("catalunya-seu-registre-client-auth", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", entry["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", entry["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("QA-only", entry["limitations"])
        self.assertIn("firma", entry["limitations"])

        profile = next(
            p for p in json.loads(PROFILES.read_text())["profiles"]
            if p["profileId"] == "catalunya-seu-registre-client-auth"
        )
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual(
            "https://web.gencat.cat/ca/seu-electronica/serveis-de-la-seu/registre-electronic/",
            profile["startUrl"],
        )
        self.assertEqual(["CLIENT_TLS_AUTH"], profile["capabilities"])
        self.assertEqual(
            ["https://cert.valid.aoc.cat"],
            profile["clientAuthPolicy"]["requestOrigins"],
        )
        self.assertEqual("/o/oauth2/cert", profile["clientAuthPolicy"]["requestPath"])
        self.assertEqual([], profile["clientAuthPolicy"]["requiredEphemeralQueryParameters"])


if __name__ == "__main__":
    unittest.main()
