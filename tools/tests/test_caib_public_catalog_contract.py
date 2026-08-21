from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
PROFILES = ROOT / "config" / "site_profiles_v1.json"
SPEC = importlib.util.spec_from_file_location("caib_public_catalog_generator", GENERATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class CaibPublicCatalogContractTest(unittest.TestCase):
    def test_caib_entry_binds_only_controlled_auth_observed_pades_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0097")
        self.assertEqual("caib-portafib", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual(["PADES"], entry["observedSignatureFormats"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertEqual("2026-08-18", entry["reviewedOn"])
        self.assertIn("no se ejecutaron", entry["limitations"])

        profile = next(p for p in json.loads(PROFILES.read_text())["profiles"] if p["profileId"] == "caib-portafib")
        self.assertEqual("QA_ONLY", profile["activation"])
        self.assertEqual(["https://www.caib.es", "https://intranet.caib.es"], profile["initiatorOrigins"])
        self.assertEqual([], profile["redirectOrigins"])
        self.assertEqual([], profile["trustedBrowseOrigins"])
        self.assertIsNone(profile["clientAuthPolicy"])
        op = profile["operationPolicies"][0]
        self.assertEqual(["SHA256_WITH_RSA"], op["algorithms"])
        self.assertEqual("PADES", op["format"])
        self.assertEqual({}, op["fixedExtraProperties"])
        self.assertEqual([], op["allowedExtraProperties"])

    def test_caib_registre_alias_reuses_exact_generic_instance_profile(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0098")
        self.assertEqual("caib-portafib", entry["profileId"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual(
            "https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/",
            entry["entryUrl"],
        )
        self.assertEqual(
            "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros=",
            entry["launchUrl"],
        )
        self.assertEqual("DELEGACION_CAIB_INSTANCIA_GENERICA", entry["protocolFamily"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("Alias QA-only", entry["limitations"])

        profile = next(p for p in json.loads(PROFILES.read_text())["profiles"] if p["profileId"] == "caib-portafib")
        self.assertNotIn("https://apps.caib.es", profile["initiatorOrigins"])
        self.assertEqual(["https://www.caib.es", "https://intranet.caib.es"], profile["initiatorOrigins"])


if __name__ == "__main__":
    unittest.main()
