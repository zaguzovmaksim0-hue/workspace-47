import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("generate_public_portal_catalog", ROOT / "tools" / "generate_public_portal_catalog.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(GENERATOR)
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"

class MadridCuentaDigitalPublicCatalogContractTest(unittest.TestCase):
    def test_53f1_binds_exact_qa_browse_profile_without_sensitive_capability_claim(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0179")
        self.assertEqual("comunidad-madrid-cuenta-digital-53f1", entry["profileId"])
        self.assertEqual("https://digital.comunidad.madrid/ext/53F1", entry["entryUrl"])
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("CUENTA_DIGITAL_AUTH_CLIENT_TLS_BOUNDARY", entry["protocolFamily"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", entry["observedMechanisms"])
        self.assertIn("qa_only", entry["limitations"].lower())
        self.assertIn("post", entry["limitations"].lower())
        self.assertIn("gestiona2", entry["limitations"].lower())

if __name__ == "__main__":
    unittest.main()
