from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
START_URL = (
    "https://www.euskadi.eus/web01-sedeform/es/x43kToolkitWar/form/fdp?"
    "procedureId=1017701&tipoPresentacion=19&language=es"
)
SOURCE_URL = "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication"
TARGET_ORIGIN = "https://eidas2.izenpe.com"
TARGET_PATH = "/cert-authn-external-validation/authenticate"

SPEC = importlib.util.spec_from_file_location("euskadi_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class EuskadiPublicCatalogContractTest(unittest.TestCase):
    def test_entry_binds_only_reviewed_izenpe_client_tls_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0115")
        self.assertEqual("euskadi-sede-electronica", entry["profileId"])
        self.assertEqual(START_URL, entry["entryUrl"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", entry["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("firma documental", entry["limitations"].lower())

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(item for item in profiles if item["profileId"] == "euskadi-sede-electronica")
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual("VERIFIED_CONTRACT", profile["compatibilityStatus"])
        self.assertEqual(["CLIENT_TLS_AUTH"], profile["capabilities"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual(["https://www.euskadi.eus"], profile["initiatorOrigins"])
        self.assertEqual(["https://eidas.izenpe.com"], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        policy = profile["clientAuthPolicy"]
        self.assertEqual("DIRECT_FROM_SOURCE", policy["transitionMode"])
        self.assertEqual([TARGET_ORIGIN], policy["requestOrigins"])
        self.assertEqual([SOURCE_URL], policy["sourceUrls"])
        self.assertEqual(TARGET_PATH, policy["requestPath"])
        self.assertFalse(policy["allowEmptyIssuerList"])
        self.assertEqual(443, policy["requestPort"])
        self.assertEqual(
            {"allowedKeyAlgorithms": ["RSA", "EC"], "requireDigitalSignatureKeyUsage": True},
            profile["certificateRules"],
        )


if __name__ == "__main__":
    unittest.main()
