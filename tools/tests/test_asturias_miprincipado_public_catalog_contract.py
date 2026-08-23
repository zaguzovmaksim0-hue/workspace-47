#!/usr/bin/env python3
"""Exact Asturias MiPrincipado certificate-auth public-catalog contract."""

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
    "https://miprincipado.asturias.es/-/dboid-6269000102616541907573"
    "?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites"
)
SOURCE_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
TARGET_URL = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"

SPEC = importlib.util.spec_from_file_location("public_catalog_generator", GENERATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("catalog generator could not be loaded")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class AsturiasMiPrincipadoPublicCatalogContractTest(unittest.TestCase):
    def test_asturias_entry_is_bound_only_to_the_observed_clave_client_tls_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0095")

        self.assertEqual("asturias-miprincipado-sede", entry["portalId"])
        self.assertEqual("asturias-miprincipado", entry["profileId"])
        self.assertEqual(START_URL, entry["entryUrl"])
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", entry["protocolFamily"])
        self.assertEqual(
            ["CERTIFICATE_ACCESS", "CLIENT_TLS_AUTH", "ELECTRONIC_SIGNATURE"],
            entry["observedMechanisms"],
        )
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertEqual("2026-08-19", entry["reviewedOn"])
        self.assertIn("firma sigue no_verificado", entry["limitations"].lower())
        self.assertIn("no se ejecutó operación de clave privada", entry["limitations"].lower())

        profiles = json.loads(PROFILES.read_text(encoding="utf-8"))["profiles"]
        profile = next(item for item in profiles if item["profileId"] == "asturias-miprincipado")
        self.assertEqual(START_URL, profile["startUrl"])
        self.assertEqual(["CLIENT_TLS_AUTH"], profile["capabilities"])
        self.assertEqual([], profile["operationPolicies"])
        self.assertEqual([], profile["endpoints"])
        self.assertEqual(["https://miprincipado.asturias.es"], profile["initiatorOrigins"])
        self.assertEqual(
            [
                "https://tramita.asturias.es",
                "https://rhsso.asturias.es",
                "https://pasarela.clave.gob.es",
            ],
            profile["redirectOrigins"],
        )
        self.assertEqual([], profile["trustedBrowseOrigins"])
        policy = profile["clientAuthPolicy"]
        self.assertEqual("DIRECT_FROM_SOURCE", policy["transitionMode"])
        self.assertEqual(["https://pasarela-ident.clave.gob.es"], policy["requestOrigins"])
        self.assertEqual([SOURCE_URL], policy["sourceUrls"])
        self.assertEqual("/IdP2/AuthenticateCitizen", policy["requestPath"])
        self.assertEqual({}, policy["fixedQueryParameters"])
        self.assertEqual([], policy["requiredEphemeralQueryParameters"])
        self.assertTrue(policy["allowEmptyIssuerList"])
        self.assertEqual(15, policy["grantTtlSeconds"])
        self.assertEqual(443, policy["requestPort"])
        self.assertEqual(
            {"allowedKeyAlgorithms": ["RSA"], "requireDigitalSignatureKeyUsage": True},
            profile["certificateRules"],
        )
        evidence_urls = {item["url"] for item in profile["evidence"]}
        self.assertIn(SOURCE_URL, evidence_urls)
        self.assertIn(TARGET_URL, evidence_urls)
        self.assertTrue(all(item["reviewedOn"] == "2026-08-19" for item in profile["evidence"]))


if __name__ == "__main__":
    unittest.main()
