#!/usr/bin/env python3
"""Reproducibility and single-source checks for the bundled public portal catalog."""

from __future__ import annotations

import importlib.util
import hashlib
import json
import subprocess
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = ROOT / "tools" / "generate_public_portal_catalog.py"
SOURCE = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
SITE_PROFILES = ROOT / "config" / "site_profiles_v1.json"
OLD_RAW_SITE_PROFILES = (
    ROOT / "app" / "src" / "main" / "res" / "raw" / "site_profiles_v1.json"
)
REGISTRY_SOURCE = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "dev"
    / "junta"
    / "firmamobile"
    / "profile"
    / "SiteProfileRegistry.kt"
)
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "raw" / "public_portal_catalog_v1.json"

SPEC = importlib.util.spec_from_file_location("public_catalog_generator", GENERATOR_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("catalog generator could not be loaded")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class PublicPortalCatalogGeneratorTest(unittest.TestCase):
    def test_committed_resource_is_byte_for_byte_reproducible(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        generated = json.dumps(
            catalog,
            ensure_ascii=False,
            indent=2,
            sort_keys=False,
        ) + "\n"

        self.assertEqual(OUTPUT.read_text(encoding="utf-8"), generated)
        inventory_count = sum(entry["inventoryId"] is not None for entry in catalog["entries"])
        self.assertGreaterEqual(inventory_count, GENERATOR.MIN_INVENTORY_RECORDS)
        self.assertEqual(inventory_count, len(catalog["entries"]))
        profile_count = len(json.loads(SITE_PROFILES.read_text(encoding="utf-8"))["profiles"])
        bound_profile_ids = {
            entry["profileId"] for entry in catalog["entries"] if entry["profileId"] is not None
        }
        self.assertEqual(profile_count, len(bound_profile_ids))
        self.assertGreaterEqual(
            sum(entry["profileId"] is not None for entry in catalog["entries"]),
            profile_count,
        )
        pag_reg = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-pag-reg"
        )
        self.assertEqual("reg-age-redsara", pag_reg["profileId"])
        self.assertEqual("https://reg.redsara.es/es/", pag_reg["launchUrl"])
        self.assertEqual("E2E_PENDING", pag_reg["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", pag_reg["inventoryStatus"])
        self.assertIn("reg-age", pag_reg["limitations"].lower())
        self.assertIn("qa", pag_reg["limitations"].lower())
        self.assertIn("e2e", pag_reg["limitations"].lower())

        redsara = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-reg-redsara"
        )
        self.assertEqual("reg-age-redsara", redsara["profileId"])
        self.assertEqual("E2E_PENDING", redsara["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", redsara["inventoryStatus"])
        self.assertEqual("2026-07-30", redsara["reviewedOn"])
        self.assertIn("cl@ve", redsara["limitations"].lower())
        self.assertIn("administrativa", redsara["limitations"].lower())
        self.assertIn("xades", redsara["limitations"].lower())

        aeat = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "aeat-sede"
        )
        self.assertEqual("aeat-mis-datos-censales", aeat["profileId"])
        self.assertEqual("E2E_PENDING", aeat["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", aeat["inventoryStatus"])
        self.assertEqual("2026-07-31", aeat["reviewedOn"])
        self.assertIn("client tls", aeat["limitations"].lower())
        self.assertIn("qa", aeat["limitations"].lower())
        self.assertIn("e2e", aeat["limitations"].lower())
        self.assertNotIn("firma aceptada", aeat["limitations"].lower())

        ugr = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "ugr-sede"
        )
        self.assertEqual("ugr-certificado-login", ugr["profileId"])
        self.assertEqual("E2E_PENDING", ugr["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", ugr["inventoryStatus"])
        self.assertEqual("2026-08-09", ugr["reviewedOn"])
        self.assertIn("autoscript", ugr["protocolFamily"].lower())
        self.assertIn("cades", ugr["protocolFamily"].lower())
        self.assertIn("e2e", ugr["limitations"].lower())
        self.assertIn("storage", ugr["limitations"].lower())

        sevilla = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "sevilla-sede"
        )
        self.assertEqual("sevilla-atse-certificate-login", sevilla["profileId"])
        self.assertEqual(
            "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente",
            sevilla["entryUrl"],
        )
        self.assertNotIn("launchUrl", sevilla)
        self.assertEqual("E2E_PENDING", sevilla["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", sevilla["inventoryStatus"])
        self.assertEqual("2026-08-11", sevilla["reviewedOn"])
        self.assertIn("AUTOSCRIPT", sevilla["observedMechanisms"])
        self.assertIn("XADES", sevilla["observedSignatureFormats"])
        self.assertIn("qa", sevilla["limitations"].lower())
        self.assertIn("e2e", sevilla["limitations"].lower())
        self.assertIn("authenticate", sevilla["limitations"].lower())

        us = next(entry for entry in catalog["entries"] if entry["portalId"] == "us-sede")
        self.assertEqual("reg-age-redsara", us["profileId"])
        self.assertEqual(
            "https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01",
            us["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", us["launchUrl"])
        self.assertEqual("E2E_PENDING", us["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", us["inventoryStatus"])
        self.assertEqual("2026-08-09", us["reviewedOn"])
        self.assertIn("reg-age", us["limitations"].lower())
        self.assertIn("e2e", us["limitations"].lower())

        cantabria = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "cantabria-registro-electronico-comun"
        )
        self.assertEqual("cantabria-rec-cert-login", cantabria["profileId"])
        self.assertEqual("E2E_PENDING", cantabria["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", cantabria["inventoryStatus"])
        self.assertEqual("2026-08-09", cantabria["reviewedOn"])
        self.assertIn("miniapplet", cantabria["protocolFamily"].lower())
        self.assertIn("cades", cantabria["protocolFamily"].lower())
        self.assertIn("e2e", cantabria["limitations"].lower())
        self.assertIn("qa", cantabria["limitations"].lower())

        aragon = next(entry for entry in catalog["entries"] if entry["portalId"] == "aragon-siraw")
        self.assertEqual("aragon-siraw", aragon["profileId"])
        self.assertEqual("E2E_VERIFIED", aragon["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", aragon["inventoryStatus"])
        ofvirtual = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "junta-andalucia-ofvirtual"
        )
        self.assertEqual("junta-ofvirtual", ofvirtual["profileId"])
        self.assertEqual("E2E_VERIFIED", ofvirtual["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", ofvirtual["inventoryStatus"])
        self.assertEqual("2026-07-29", ofvirtual["reviewedOn"])
        self.assertIn("portal real aceptó", ofvirtual["limitations"].lower())
        self.assertIn("login", ofvirtual["limitations"].lower())
        unizar = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "unizar-tramitador"
        )
        self.assertEqual("unizar-tramitador", unizar["profileId"])
        self.assertEqual("E2E_VERIFIED", unizar["catalogStatus"])
        self.assertEqual("VERIFIED_E2E", unizar["inventoryStatus"])
        self.assertEqual("2026-07-30", unizar["reviewedOn"])
        self.assertIn("portal real aceptó", unizar["limitations"].lower())
        self.assertIn("autenticación", unizar["limitations"].lower())
        self.assertEqual(
            hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            catalog["sourceRevision"],
        )

    def test_melilla_batch_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        melilla = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "melilla-sede"
        )

        self.assertEqual("melilla-sede", melilla["profileId"])
        self.assertEqual("ES-PUB-0107", melilla["inventoryId"])
        self.assertEqual(
            "https://sede.melilla.es/sta/CarpetaPublic/doEvent?"
            "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
            melilla["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", melilla["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", melilla["inventoryStatus"])
        self.assertEqual("2026-08-11", melilla["reviewedOn"])
        self.assertIn("AUTOSCRIPT", melilla["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", melilla["observedMechanisms"])
        self.assertIn("CADES", melilla["observedSignatureFormats"])
        self.assertIn("e2e", melilla["limitations"].lower())
        self.assertIn("qa", melilla["limitations"].lower())
        self.assertNotIn("VERIFIED_E2E", melilla["inventoryStatus"])

    def test_extremadura_sta_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        extremadura = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "extremadura-tramites"
        )

        self.assertEqual("extremadura-tramites", extremadura["profileId"])
        self.assertEqual("ES-PUB-0109", extremadura["inventoryId"])
        self.assertEqual("https://tramites.juntaex.es/", extremadura["entryUrl"])
        self.assertEqual("E2E_PENDING", extremadura["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", extremadura["inventoryStatus"])
        self.assertEqual("2026-08-13", extremadura["reviewedOn"])
        self.assertIn("AUTOSCRIPT", extremadura["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", extremadura["observedMechanisms"])
        self.assertIn("CADES", extremadura["observedSignatureFormats"])
        self.assertIn("e2e", extremadura["limitations"].lower())
        self.assertIn("qa", extremadura["limitations"].lower())
        self.assertNotEqual("VERIFIED_E2E", extremadura["inventoryStatus"])

    def test_huesca_sta_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        huesca = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "diputacion-huesca-portal"
        )

        self.assertEqual("diputacion-huesca-portal", huesca["profileId"])
        self.assertEqual("ES-PUB-0159", huesca["inventoryId"])
        self.assertEqual(
            "https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME",
            huesca["entryUrl"],
        )
        self.assertNotIn("launchUrl", huesca)
        self.assertEqual("E2E_PENDING", huesca["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", huesca["inventoryStatus"])
        self.assertEqual("2026-08-16", huesca["reviewedOn"])
        self.assertIn("AUTOSCRIPT", huesca["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", huesca["observedMechanisms"])
        self.assertEqual(
            {"CADES", "PADES", "XADES"},
            set(huesca["observedSignatureFormats"]),
        )
        self.assertIn("e2e", huesca["limitations"].lower())
        self.assertIn("qa", huesca["limitations"].lower())
        self.assertNotEqual("VERIFIED_E2E", huesca["inventoryStatus"])

    def test_jccm_certificate_probe_binds_separate_public_catalog_surface(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        broad = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "castilla-la-mancha-sede"
        )
        probe = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "castilla-la-mancha-certificate-login-probe"
        )

        self.assertIsNone(broad["profileId"])
        self.assertEqual("https://www.jccm.es/", broad["entryUrl"])
        self.assertEqual("CATALOGED", broad["catalogStatus"])
        self.assertEqual("BROWSE_ONLY", broad["inventoryStatus"])

        self.assertEqual("jccm-certificate-login-probe", probe["profileId"])
        self.assertEqual("ES-PUB-0183", probe["inventoryId"])
        self.assertEqual(
            "https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml",
            probe["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", probe["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", probe["inventoryStatus"])
        self.assertEqual("2026-08-09", probe["reviewedOn"])
        self.assertIn("AUTOSCRIPT", probe["observedMechanisms"])
        self.assertIn("MINIAPPLET", probe["observedMechanisms"])
        self.assertIn("CADES", probe["observedSignatureFormats"])

        limitations = probe["limitations"].lower()
        self.assertIn("solo para qa", limitations)
        self.assertIn("e2e pendiente", limitations)

    def test_sanidad_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        sanidad = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-sanidad"
        )

        self.assertEqual("ministerio-sanidad-certificado", sanidad["profileId"])
        self.assertEqual("ES-PUB-0073", sanidad["inventoryId"])
        self.assertEqual("https://sede.mscbs.gob.es/", sanidad["entryUrl"])
        self.assertNotIn("launchUrl", sanidad)
        self.assertEqual("E2E_PENDING", sanidad["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", sanidad["inventoryStatus"])
        self.assertEqual("2026-08-14", sanidad["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", sanidad["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", sanidad["observedMechanisms"])
        self.assertNotIn("ELECTRONIC_SIGNATURE", sanidad["observedMechanisms"])
        self.assertEqual([], sanidad["observedSignatureFormats"])
        self.assertIn("tls 1.2", sanidad["limitations"].lower())
        self.assertIn("e2e", sanidad["limitations"].lower())

    def test_tea_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        tea = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-sede-electronica-de-los-tribunales-economico-administrativos-tea"
        )

        self.assertEqual("tea-alegaciones-certificado", tea["profileId"])
        self.assertEqual("ES-PUB-0090", tea["inventoryId"])
        self.assertEqual("https://sede.tea.hacienda.gob.es/TEA/alegaciones.html", tea["entryUrl"])
        self.assertNotIn("launchUrl", tea)
        self.assertEqual("E2E_PENDING", tea["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", tea["inventoryStatus"])
        self.assertEqual("2026-08-14", tea["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", tea["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", tea["observedMechanisms"])
        self.assertNotIn("ELECTRONIC_SIGNATURE", tea["observedMechanisms"])
        self.assertEqual([], tea["observedSignatureFormats"])
        self.assertIn("tls 1.2", tea["limitations"].lower())
        self.assertIn("e2e", tea["limitations"].lower())

    def test_tenerife_autoscript_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        tenerife = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "tenerife-sede-electronica"
        )

        self.assertEqual("tenerife-sede-electronica", tenerife["profileId"])
        self.assertEqual("ES-PUB-0128", tenerife["inventoryId"])
        self.assertEqual("https://sede.tenerife.es/", tenerife["entryUrl"])
        self.assertNotIn("launchUrl", tenerife)
        self.assertEqual("E2E_PENDING", tenerife["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", tenerife["inventoryStatus"])
        self.assertEqual("2026-08-14", tenerife["reviewedOn"])
        self.assertIn("AUTOFIRMA", tenerife["observedMechanisms"])
        self.assertIn("AUTOSCRIPT", tenerife["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", tenerife["observedMechanisms"])
        self.assertEqual(["CADES"], tenerife["observedSignatureFormats"])
        self.assertIn("e2e", tenerife["limitations"].lower())

    def test_toledo_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        toledo = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "diputacion-toledo-sede"
        )

        self.assertEqual("diputacion-toledo-sede", toledo["profileId"])
        self.assertEqual("ES-PUB-0174", toledo["inventoryId"])
        self.assertEqual(
            "https://diputacion.toledo.gob.es/SIGEM_RegistroTelematicoWeb/realizarSolicitudRegistro.do?tramiteId=TRAM_31",
            toledo["entryUrl"],
        )
        self.assertNotIn("launchUrl", toledo)
        self.assertEqual("E2E_PENDING", toledo["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", toledo["inventoryStatus"])
        self.assertEqual("2026-08-13", toledo["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", toledo["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", toledo["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", toledo["observedMechanisms"])
        self.assertEqual([], toledo["observedSignatureFormats"])
        self.assertIn("client_tls_auth", toledo["limitations"].lower())
        self.assertIn("firma", toledo["limitations"].lower())
        self.assertIn("e2e", toledo["limitations"].lower())


    def test_ceuta_browse_only_profile_binds_exact_catalog_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        ceuta = next(entry for entry in catalog["entries"] if entry["portalId"] == "ceuta-sede")

        self.assertEqual("ceuta-sede", ceuta["profileId"])
        self.assertEqual("ES-PUB-0106", ceuta["inventoryId"])
        self.assertEqual("https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info", ceuta["entryUrl"])
        self.assertNotIn("launchUrl", ceuta)
        self.assertEqual("CATALOGED", ceuta["catalogStatus"])
        self.assertEqual("BROWSE_ONLY", ceuta["inventoryStatus"])
        self.assertEqual("2026-07-16", ceuta["reviewedOn"])
        self.assertEqual([], ceuta["observedSignatureFormats"])

    def test_every_profile_binds_to_exactly_one_inventory_entry_by_start_url(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entries_by_url = {entry["entryUrl"]: entry for entry in catalog["entries"]}
        profiles = json.loads(SITE_PROFILES.read_text(encoding="utf-8"))["profiles"]

        self.assertEqual(len(catalog["entries"]), len(entries_by_url))
        for profile in profiles:
            entry = entries_by_url[profile["startUrl"]]
            self.assertEqual(profile["profileId"], entry["profileId"])

    def test_missing_inventory_match_fails_closed(self) -> None:
        inventory = SOURCE.read_text(encoding="utf-8")
        exact = (
            'entry_url: "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"'
        )
        self.assertIn(exact, inventory)
        mutated = inventory.replace(
            exact,
            'entry_url: "https://ws072.juntadeandalucia.es/ofvirtual/auth/changed"',
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inventory.md"
            path.write_text(mutated, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "no inventory entry"):
                GENERATOR.generate(path, SITE_PROFILES)

    def test_unknown_alias_launch_url_fails_closed(self) -> None:
        inventory = SOURCE.read_text(encoding="utf-8")
        exact = '    launch_url: "https://reg.redsara.es/es/"\n'
        self.assertIn(exact, inventory)
        mutated = inventory.replace(
            exact,
            '    launch_url: "https://example.invalid/not-a-profile"\n',
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inventory.md"
            path.write_text(mutated, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "alias launch_url"):
                GENERATOR.generate(path, SITE_PROFILES)

    def test_duplicate_profile_start_url_fails_closed(self) -> None:
        profile_catalog = json.loads(SITE_PROFILES.read_text(encoding="utf-8"))
        profiles = profile_catalog["profiles"]
        profiles[1]["startUrl"] = profiles[0]["startUrl"]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "site-profiles.json"
            path.write_text(
                json.dumps(profile_catalog, ensure_ascii=False),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "duplicate profile startUrl"):
                GENERATOR.generate(SOURCE, path)

    def test_multiple_inventory_matches_for_one_profile_fail_closed(self) -> None:
        duplicate = """
```yaml
records:
  - inventory_id: "ES-PUB-9999"
    surface_key: "duplicate-junta-ofvirtual"
    entry_url: "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
```
"""
        inventory = SOURCE.read_text(encoding="utf-8").replace(
            "## 8. Relación con el catálogo de producto",
            duplicate + "\n## 8. Relación con el catálogo de producto",
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inventory.md"
            path.write_text(inventory, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "multiple inventory entries"):
                GENERATOR.generate(path, SITE_PROFILES)

    def test_unexpected_site_profile_root_key_fails_closed(self) -> None:
        profile_catalog = json.loads(SITE_PROFILES.read_text(encoding="utf-8"))
        profile_catalog["unexpected"] = True
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "site-profiles.json"
            path.write_text(json.dumps(profile_catalog), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "invalid site profile catalog root"):
                GENERATOR.generate(SOURCE, path)

    def test_site_profile_catalog_has_one_committed_source(self) -> None:
        self.assertTrue(SITE_PROFILES.is_file())
        self.assertFalse(OLD_RAW_SITE_PROFILES.exists())
        tracked = subprocess.check_output(
            ["git", "ls-files"],
            cwd=ROOT,
            text=True,
        ).splitlines()
        self.assertEqual(
            ["config/site_profiles_v1.json"],
            [name for name in tracked if name.endswith("/site_profiles_v1.json")],
        )
        registry = REGISTRY_SOURCE.read_text(encoding="utf-8")
        self.assertIn("BuildConfig.SITE_PROFILE_CATALOG_JSON", registry)
        self.assertNotIn('const val JSON = """', registry)


if __name__ == "__main__":
    unittest.main()
