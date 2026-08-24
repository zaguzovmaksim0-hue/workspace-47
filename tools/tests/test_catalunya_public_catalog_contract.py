import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
SPEC = importlib.util.spec_from_file_location("catalunya_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class CatalunyaPublicCatalogContractTest(unittest.TestCase):
    def test_peticio_generica_binds_only_observed_client_tls_auth_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0105")

        self.assertEqual("catalunya-tramits-peticio-generica", entry["portalId"])
        self.assertEqual("catalunya-peticio-generica-client-auth", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", entry["protocolFamily"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", entry["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", entry["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertIn("GSIT", entry["limitations"])
        self.assertIn("firma", entry["limitations"].lower())
        self.assertIn("e2e", entry["limitations"].lower())


if __name__ == "__main__":
    unittest.main()
