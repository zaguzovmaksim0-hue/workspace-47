from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
SPEC = importlib.util.spec_from_file_location("murcia_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class MurciaPublicCatalogContractTest(unittest.TestCase):
    def test_murcia_carm_binds_only_the_bounded_pase_navigation_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0113")

        self.assertEqual("murcia-carm-pase", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual(
            "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("CARM_PASE_CONCLAVE_BROWSE_AUTH_LAUNCH", entry["protocolFamily"])
        self.assertEqual(["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"], entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("capabilities=[]", entry["limitations"])

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(item for item in profiles if item["profileId"] == "murcia-carm-pase")
        self.assertEqual("VERIFIED_CONTRACT", profile["compatibilityStatus"])
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual([], profile["capabilities"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertIsNone(profile["clientAuthPolicy"])
        self.assertEqual(["https://sede.carm.es"], profile["initiatorOrigins"])
        self.assertEqual(
            ["https://validate.perfdrive.com", "https://pase.carm.es", "https://conclave.carm.es"],
            profile["redirectOrigins"],
        )
        self.assertEqual([], profile["trustedBrowseOrigins"])


if __name__ == "__main__":
    unittest.main()
