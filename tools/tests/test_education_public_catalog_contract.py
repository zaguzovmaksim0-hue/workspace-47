import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
spec = importlib.util.spec_from_file_location("generate_public_portal_catalog", GENERATOR_PATH)
GENERATOR = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(GENERATOR)
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
SITE_PROFILES = ROOT / "config" / "site_profiles_v1.json"


class EducationPublicCatalogContractTest(unittest.TestCase):
    def test_convocatoria_46_exposes_verified_client_tls_auth_only(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0182")
        self.assertEqual("educacion-convocatoria", target["profileId"])
        self.assertEqual("E2E_VERIFIED", target["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", target["inventoryStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", target["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", target["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", target["observedMechanisms"])
        self.assertNotIn("ELECTRONIC_SIGNATURE", target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertEqual("2026-08-27", target["reviewedOn"])
        self.assertIn("e2e", target["limitations"].lower())
        self.assertIn("firma", target["limitations"].lower())


if __name__ == "__main__":
    unittest.main()
