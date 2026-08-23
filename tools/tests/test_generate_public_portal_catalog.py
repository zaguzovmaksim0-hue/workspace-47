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
    def test_puertos_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        puertos = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-puertos-del-estado"
        )
        self.assertEqual("ES-PUB-0085", puertos["inventoryId"])
        self.assertEqual("reg-age-redsara", puertos["profileId"])
        self.assertEqual(
            "https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
            puertos["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", puertos["launchUrl"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", puertos["inventoryStatus"])
        self.assertEqual("E2E_PENDING", puertos["catalogStatus"])
        self.assertEqual([], puertos["observedMechanisms"])
        self.assertEqual([], puertos["observedSignatureFormats"])

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

        educacion = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-educacion-formacion-profesional-y-deportes"
        )
        self.assertEqual("reg-age-redsara", educacion["profileId"])
        self.assertEqual("ES-PUB-0066", educacion["inventoryId"])
        self.assertEqual(
            "https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html",
            educacion["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", educacion["launchUrl"])
        self.assertEqual("E2E_PENDING", educacion["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", educacion["inventoryStatus"])
        self.assertEqual("REVIEWED", educacion["discoveryState"])
        self.assertEqual("2026-08-17", educacion["reviewedOn"])
        self.assertIn("reg-age", educacion["limitations"].lower())
        self.assertIn("qa", educacion["limitations"].lower())
        self.assertIn("e2e", educacion["limitations"].lower())


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

        aemps = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-agencia-espanola-de-medicamentos-y-productos-sanitarios-aemps"
        )
        self.assertEqual("reg-age-redsara", aemps["profileId"])
        self.assertEqual("https://sede.aemps.gob.es/", aemps["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", aemps["launchUrl"])
        self.assertEqual("E2E_PENDING", aemps["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", aemps["inventoryStatus"])
        self.assertEqual("2026-08-17", aemps["reviewedOn"])
        self.assertEqual([], aemps["observedMechanisms"])
        self.assertEqual([], aemps["observedSignatureFormats"])
        self.assertIn("reg-age", aemps["limitations"].lower())
        self.assertIn("qa", aemps["limitations"].lower())
        self.assertIn("e2e", aemps["limitations"].lower())

        puertos = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-puertos-del-estado"
        )
        self.assertEqual("reg-age-redsara", puertos["profileId"])
        self.assertEqual(
            "https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
            puertos["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", puertos["launchUrl"])
        self.assertEqual("E2E_PENDING", puertos["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", puertos["inventoryStatus"])
        self.assertEqual("2026-08-17", puertos["reviewedOn"])
        self.assertEqual([], puertos["observedMechanisms"])
        self.assertEqual([], puertos["observedSignatureFormats"])
        self.assertIn("reg-age", puertos["limitations"].lower())
        self.assertIn("qa", puertos["limitations"].lower())
        self.assertIn("e2e", puertos["limitations"].lower())

        uned = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-universidad-nacional-de-educacion-a-distancia-uned"
        )
        self.assertEqual("ES-PUB-0092", uned["inventoryId"])
        self.assertEqual("reg-age-redsara", uned["profileId"])
        self.assertEqual(
            "https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
            uned["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", uned["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", uned["protocolFamily"])
        self.assertEqual("E2E_PENDING", uned["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", uned["inventoryStatus"])
        self.assertEqual("REVIEWED", uned["discoveryState"])
        self.assertEqual("2026-08-17", uned["reviewedOn"])
        self.assertEqual([], uned["observedMechanisms"])
        self.assertEqual([], uned["observedSignatureFormats"])
        self.assertIn("reg-age", uned["limitations"].lower())
        self.assertIn("qa", uned["limitations"].lower())
        self.assertIn("e2e", uned["limitations"].lower())

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

        cdti = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-centro-para-el-desarrollo-tecnologico-industrial-cdti"
        )
        self.assertEqual("cdti-certificate-validation", cdti["profileId"])
        self.assertEqual("ES-PUB-0030", cdti["inventoryId"])
        self.assertEqual(
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx",
            cdti["entryUrl"],
        )
        self.assertNotIn("launchUrl", cdti)
        self.assertEqual("E2E_PENDING", cdti["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", cdti["inventoryStatus"])
        self.assertEqual("2026-08-16", cdti["reviewedOn"])
        self.assertIn("AUTOSCRIPT", cdti["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", cdti["observedMechanisms"])
        self.assertEqual(["XADES"], cdti["observedSignatureFormats"])
        self.assertIn("e2e", cdti["limitations"].lower())

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

    def test_transportes_qys_xades_profile_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "age-ministerio-de-transportes-y-movilidad-sostenible"
        )

        self.assertEqual("ES-PUB-0075", entry["inventoryId"])
        self.assertEqual("transportes-qys-cert-login", entry["profileId"])
        self.assertEqual(
            "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("REVIEWED", entry["discoveryState"])
        self.assertEqual("2026-08-17", entry["reviewedOn"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", entry["observedMechanisms"])
        self.assertEqual(["XADES"], entry["observedSignatureFormats"])
        self.assertIn("e2e", entry["limitations"].lower())
        self.assertNotEqual("VERIFIED_E2E", entry["inventoryStatus"])

    def test_mites_certificate_login_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        mites = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-trabajo-y-economia-social"
        )

        self.assertEqual("mites-certificate-login", mites["profileId"])
        self.assertEqual("ES-PUB-0074", mites["inventoryId"])
        self.assertEqual("https://sede.mites.gob.es/", mites["entryUrl"])
        self.assertNotIn("launchUrl", mites)
        self.assertEqual("E2E_PENDING", mites["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", mites["inventoryStatus"])
        self.assertEqual("REVIEWED", mites["discoveryState"])
        self.assertEqual("2026-08-17", mites["reviewedOn"])
        self.assertIn("AUTOSCRIPT", mites["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", mites["observedMechanisms"])
        self.assertEqual(["CADES"], mites["observedSignatureFormats"])
        self.assertIn("e2e", mites["limitations"].lower())
        self.assertIn("qa", mites["limitations"].lower())
        self.assertNotEqual("VERIFIED_E2E", mites["inventoryStatus"])

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

    def test_extremadura_legacy_sede_alias_binds_existing_qa_profile(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        legacy = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "extremadura-sede-anterior"
        )

        self.assertEqual("extremadura-tramites", legacy["profileId"])
        self.assertEqual("ES-PUB-0110", legacy["inventoryId"])
        self.assertEqual("https://sede.juntaex.es/SEDE/", legacy["entryUrl"])
        self.assertEqual("https://tramites.juntaex.es/", legacy["launchUrl"])
        self.assertEqual("E2E_PENDING", legacy["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", legacy["inventoryStatus"])
        self.assertEqual("2026-08-18", legacy["reviewedOn"])
        self.assertNotEqual("VERIFIED_E2E", legacy["inventoryStatus"])

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
        self.assertEqual("REVIEWED", huesca["discoveryState"])
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

    def test_burgos_sta_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        burgos = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "diputacion-burgos-portal"
        )

        self.assertEqual("diputacion-burgos-portal", burgos["profileId"])
        self.assertEqual("ES-PUB-0146", burgos["inventoryId"])
        self.assertEqual(
            "https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO",
            burgos["entryUrl"],
        )
        self.assertNotIn("launchUrl", burgos)
        self.assertEqual("E2E_PENDING", burgos["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", burgos["inventoryStatus"])
        self.assertEqual("REVIEWED", burgos["discoveryState"])
        self.assertEqual("2026-08-16", burgos["reviewedOn"])
        self.assertIn("AUTOSCRIPT", burgos["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", burgos["observedMechanisms"])
        self.assertEqual(
            {"CADES", "PADES", "XADES"},
            set(burgos["observedSignatureFormats"]),
        )
        self.assertIn("e2e", burgos["limitations"].lower())
        self.assertIn("qa", burgos["limitations"].lower())
        self.assertNotEqual("VERIFIED_E2E", burgos["inventoryStatus"])

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

        self.assertEqual("jccm-registro-generico", broad["profileId"])
        self.assertEqual("ES-PUB-0103", broad["inventoryId"])
        self.assertEqual(
            "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ",
            broad["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", broad["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", broad["inventoryStatus"])
        self.assertEqual("2026-08-19", broad["reviewedOn"])
        self.assertIn("AUTOSCRIPT", broad["observedMechanisms"])
        self.assertIn("MINIAPPLET", broad["observedMechanisms"])
        self.assertIn("CLIENT_TLS_AUTH", broad["observedMechanisms"])
        self.assertIn("XADES", broad["observedSignatureFormats"])

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

    def test_menorca_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        menorca = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "menorca-portal-institucional"
        )

        self.assertEqual("menorca-carpeta-ciutadana", menorca["profileId"])
        self.assertEqual("ES-PUB-0117", menorca["inventoryId"])
        self.assertEqual(
            "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262",
            menorca["entryUrl"],
        )
        self.assertNotIn("launchUrl", menorca)
        self.assertEqual("E2E_PENDING", menorca["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", menorca["inventoryStatus"])
        self.assertEqual("2026-08-18", menorca["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", menorca["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", menorca["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", menorca["observedMechanisms"])
        self.assertIn("AUTOFIRMA", menorca["observedMechanisms"])
        self.assertEqual([], menorca["observedSignatureFormats"])
        self.assertIn("solo en qa", menorca["limitations"].lower())
        self.assertIn("e2e", menorca["limitations"].lower())

    def test_canarias_certificate_login_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "canarias-sede"
        )

        self.assertEqual("canarias-sede", entry["profileId"])
        self.assertEqual("ES-PUB-0099", entry["inventoryId"])
        self.assertEqual(
            "https://sede.gobiernodecanarias.org/sede/la_sede",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("2026-08-17", entry["reviewedOn"])
        self.assertIn("AUTOSCRIPT", entry["observedMechanisms"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", entry["observedMechanisms"])
        self.assertEqual(["CADES"], entry["observedSignatureFormats"])
        self.assertEqual("AUTOSCRIPT_MINIAPPLET_LOCAL_CADES", entry["protocolFamily"])
        self.assertIn("e2e", entry["limitations"].lower())

    def test_gran_canaria_pades_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "gran-canaria-sede-electronica"
        )

        self.assertEqual("gran-canaria-sede-electronica", entry["profileId"])
        self.assertEqual("ES-PUB-0138", entry["inventoryId"])
        self.assertEqual(
            "https://sede.grancanaria.com/sede-privado/instancia-general?inicio",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("2026-08-17", entry["reviewedOn"])
        self.assertIn("AUTOFIRMA", entry["observedMechanisms"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertEqual(["PADES"], entry["observedSignatureFormats"])
        self.assertIn("e2e", entry["limitations"].lower())

    def test_transparencia_pades_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(
            item for item in catalog["entries"]
            if item["portalId"] == "age-portal-de-la-transparencia"
        )

        self.assertEqual("age-portal-de-la-transparencia", entry["profileId"])
        self.assertEqual("ES-PUB-0083", entry["inventoryId"])
        self.assertEqual(
            "https://transparencia.sede.gob.es/procedimiento/portada?idProc=133628&idAmb=101524",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("2026-08-18", entry["reviewedOn"])
        self.assertIn("AUTOFIRMA", entry["observedMechanisms"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertEqual(["PADES"], entry["observedSignatureFormats"])
        self.assertIn("e2e", entry["limitations"].lower())

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

    def test_policia_autoscript_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        policia = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-cuerpo-nacional-de-policia"
        )

        self.assertEqual("policia-solicitud-generica", policia["profileId"])
        self.assertEqual("ES-PUB-0038", policia["inventoryId"])
        self.assertEqual("https://sede.policia.gob.es/", policia["entryUrl"])
        self.assertNotIn("launchUrl", policia)
        self.assertEqual("E2E_PENDING", policia["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", policia["inventoryStatus"])
        self.assertEqual("2026-08-15", policia["reviewedOn"])
        self.assertIn("AUTOSCRIPT", policia["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", policia["observedMechanisms"])
        self.assertEqual(["XADES"], policia["observedSignatureFormats"])
        self.assertIn("xades", policia["limitations"].lower())
        self.assertIn("e2e", policia["limitations"].lower())

    def test_ceuta_ani_profile_binds_exact_authenticated_form_boundary_without_signing_capability(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        ceuta = next(entry for entry in catalog["entries"] if entry["portalId"] == "ceuta-sede")

        self.assertEqual("ceuta-sede", ceuta["profileId"])
        self.assertEqual("ES-PUB-0106", ceuta["inventoryId"])
        self.assertEqual("https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI", ceuta["entryUrl"])
        self.assertNotIn("launchUrl", ceuta)
        self.assertEqual("CEUTA_AUTHENTICATED_FORM_BOUNDARY", ceuta["protocolFamily"])
        self.assertEqual("E2E_PENDING", ceuta["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", ceuta["inventoryStatus"])
        self.assertEqual("2026-08-19", ceuta["reviewedOn"])
        self.assertEqual(["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"], ceuta["observedMechanisms"])
        self.assertEqual([], ceuta["observedSignatureFormats"])
        self.assertIn("no_verificado", ceuta["limitations"].lower())

    def test_pattex_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        pattex = next(entry for entry in catalog["entries"] if entry["portalId"] == "extremadura-portal-tributario")

        self.assertEqual("extremadura-pattex-client-auth", pattex["profileId"])
        self.assertEqual("ES-PUB-0111", pattex["inventoryId"])
        self.assertEqual(
            "https://pattex.juntaex.es/PATTEX/externos.jsf?info=060~user~pass~SEDE_ALTA~https://pattex.juntaex.es~codigo",
            pattex["entryUrl"],
        )
        self.assertNotIn("launchUrl", pattex)
        self.assertEqual("E2E_PENDING", pattex["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", pattex["inventoryStatus"])
        self.assertEqual("CLIENT_TLS_AUTH", pattex["protocolFamily"])
        self.assertEqual("2026-08-19", pattex["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", pattex["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", pattex["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", pattex["observedMechanisms"])
        self.assertEqual([], pattex["observedSignatureFormats"])
        self.assertIn("qa", pattex["limitations"].lower())
        self.assertIn("firma", pattex["limitations"].lower())

    def test_navarra_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        navarra = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "navarra-sede-registro-general"
        )

        self.assertEqual("navarra-sede-registro-general", navarra["profileId"])
        self.assertEqual("ES-PUB-0114", navarra["inventoryId"])
        self.assertEqual(
            "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico",
            navarra["entryUrl"],
        )
        self.assertNotIn("launchUrl", navarra)
        self.assertEqual("E2E_PENDING", navarra["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", navarra["inventoryStatus"])
        self.assertEqual("2026-08-18", navarra["reviewedOn"])
        self.assertEqual("CLIENT_TLS_AUTH", navarra["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", navarra["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", navarra["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", navarra["observedMechanisms"])
        self.assertEqual([], navarra["observedSignatureFormats"])
        self.assertIn("qa", navarra["limitations"].lower())
        self.assertIn("e2e", navarra["limitations"].lower())
        self.assertIn("firma", navarra["limitations"].lower())
    def test_gva_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        gva = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "gva-sede"
        )

        self.assertEqual("generalitat-valenciana-client-auth", gva["profileId"])
        self.assertEqual("ES-PUB-0108", gva["inventoryId"])
        self.assertEqual(
            "https://www.tramita.gva.es/ctt-att-atr/asistente/iniciarTramite.html?tramite=DGM_GEN&version=4&idioma=es&idProcGuc=15602&idSubfaseGuc=SOLICITUD&idCatGuc=PR",
            gva["entryUrl"],
        )
        self.assertNotIn("launchUrl", gva)
        self.assertEqual("E2E_PENDING", gva["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", gva["inventoryStatus"])
        self.assertEqual("2026-08-18", gva["reviewedOn"])
        self.assertEqual("CLIENT_TLS_AUTH", gva["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", gva["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", gva["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", gva["observedMechanisms"])
        self.assertEqual([], gva["observedSignatureFormats"])
        self.assertIn("firma", gva["limitations"].lower())
        self.assertIn("e2e", gva["limitations"].lower())

    def test_leon_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        leon = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "diputacion-leon-sede"
        )

        self.assertEqual("diputacion-leon-sede", leon["profileId"])
        self.assertEqual("ES-PUB-0161", leon["inventoryId"])
        self.assertEqual(
            "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270",
            leon["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", leon["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", leon["inventoryStatus"])
        self.assertEqual("2026-08-16", leon["reviewedOn"])
        self.assertEqual("CLIENT_TLS_AUTH", leon["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", leon["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", leon["observedMechanisms"])
        self.assertEqual([], leon["observedSignatureFormats"])
        self.assertIn("qa", leon["limitations"].lower())
        self.assertIn("e2e", leon["limitations"].lower())
        self.assertIn("firma", leon["limitations"].lower())

    def test_mallorca_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        mallorca = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "mallorca-sede-electronica"
        )

        self.assertEqual("consell-mallorca-sede", mallorca["profileId"])
        self.assertEqual("ES-PUB-0120", mallorca["inventoryId"])
        self.assertEqual(
            "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082",
            mallorca["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", mallorca["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", mallorca["inventoryStatus"])
        self.assertEqual("2026-08-18", mallorca["reviewedOn"])
        self.assertEqual("CLIENT_TLS_AUTH", mallorca["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", mallorca["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", mallorca["observedMechanisms"])
        self.assertEqual([], mallorca["observedSignatureFormats"])
        self.assertIn("qa", mallorca["limitations"].lower())
        self.assertIn("e2e", mallorca["limitations"].lower())
        self.assertIn("firma", mallorca["limitations"].lower())

    def test_albacete_client_tls_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(e for e in catalog["entries"] if e["portalId"] == "diputacion-albacete-portal")
        self.assertEqual("diputacion-albacete-portal", entry["profileId"])
        self.assertEqual("ES-PUB-0141", entry["inventoryId"])
        self.assertEqual("https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567", entry["entryUrl"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual("2026-08-18", entry["reviewedOn"])
        self.assertEqual("CLIENT_TLS_AUTH", entry["protocolFamily"])
        self.assertIn("CLIENT_TLS_AUTH", entry["observedMechanisms"])
        self.assertIn("CERTIFICATE_ACCESS", entry["observedMechanisms"])
        self.assertEqual([], entry["observedSignatureFormats"])
        self.assertIn("qa", entry["limitations"].lower())
        self.assertIn("e2e", entry["limitations"].lower())
        self.assertIn("firma", entry["limitations"].lower())

    def test_diputacion_lleida_implemented_not_e2e_profile_binds_exact_catalog_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        lleida = next(entry for entry in catalog["entries"] if entry["portalId"] == "diputacion-lleida-sede")

        self.assertEqual("diputacion-lleida-sede", lleida["profileId"])
        self.assertEqual("ES-PUB-0162", lleida["inventoryId"])
        self.assertEqual("https://seu.diputaciolleida.cat", lleida["entryUrl"])
        self.assertNotIn("launchUrl", lleida)
        self.assertEqual("E2E_PENDING", lleida["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", lleida["inventoryStatus"])
        self.assertEqual("2026-07-16", lleida["reviewedOn"])
        self.assertEqual(["CADES"], lleida["observedSignatureFormats"])

    def test_diputacion_badajoz_implemented_not_e2e_profile_binds_exact_catalog_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        badajoz = next(entry for entry in catalog["entries"] if entry["portalId"] == "diputacion-badajoz-portal")

        self.assertEqual("diputacion-badajoz-portal", badajoz["profileId"])
        self.assertEqual("ES-PUB-0144", badajoz["inventoryId"])
        self.assertEqual("https://sede.dip-badajoz.es", badajoz["entryUrl"])
        self.assertNotIn("launchUrl", badajoz)
        self.assertEqual("E2E_PENDING", badajoz["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", badajoz["inventoryStatus"])
        self.assertEqual("2026-08-18", badajoz["reviewedOn"])
        self.assertEqual(["CADES"], badajoz["observedSignatureFormats"])

    def test_diputacion_barcelona_2057_profile_binds_exact_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0145")

        self.assertEqual("diputacion-barcelona-solicitud-generica-2057", target["profileId"])
        self.assertEqual("diputacion-barcelona-portal", target["portalId"])
        self.assertEqual("https://seuelectronica.diba.cat/es/sol%C2%B7licitud-gen%C3%A8rica", target["entryUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("2026-08-18", target["reviewedOn"])
        self.assertIn("CERTIFICATE_ACCESS", target["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])

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

    def test_airef_authenticated_xades_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        airef = next(
            entry for entry in catalog["entries"]
            if entry["inventoryId"] == "ES-PUB-0027"
        )

        self.assertEqual(
            "age-autoridad-independiente-de-responsabilidad-fiscal-airef",
            airef["portalId"],
        )
        self.assertEqual("airef-instancia-general", airef["profileId"])
        self.assertEqual(
            "https://sede.airef.es/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01",
            airef["entryUrl"],
        )
        self.assertNotIn("launchUrl", airef)
        self.assertEqual("AUTOSCRIPT_XADES_CLIENT_TLS_AUTH", airef["protocolFamily"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", airef["inventoryStatus"])
        self.assertEqual("E2E_PENDING", airef["catalogStatus"])
        self.assertEqual("REVIEWED", airef["discoveryState"])
        self.assertEqual("2026-08-18", airef["reviewedOn"])
        self.assertEqual(["XADES"], airef["observedSignatureFormats"])
        self.assertEqual(
            [
                "AUTOSCRIPT",
                "CERTIFICATE_ACCESS",
                "CLIENT_TLS_AUTH",
                "ELECTRONIC_SIGNATURE",
                "MINIAPPLET",
            ],
            airef["observedMechanisms"],
        )
        self.assertIn("qa", airef["limitations"].lower())
        self.assertIn("e2e", airef["limitations"].lower())

    def test_mugeju_client_auth_profile_binds_only_the_bounded_qa_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        mugeju = next(
            entry for entry in catalog["entries"]
            if entry["inventoryId"] == "ES-PUB-0081"
        )

        self.assertEqual("age-mutualidad-general-judicial-mugeju", mugeju["portalId"])
        self.assertEqual("mugeju-remision-documentacion-client-auth", mugeju["profileId"])
        self.assertEqual("https://sedemugeju.gob.es/remisiondocumentacion", mugeju["entryUrl"])
        self.assertNotIn("launchUrl", mugeju)
        self.assertEqual("CLIENT_TLS_AUTH", mugeju["protocolFamily"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", mugeju["inventoryStatus"])
        self.assertEqual("E2E_PENDING", mugeju["catalogStatus"])
        self.assertEqual("REVIEWED", mugeju["discoveryState"])
        self.assertEqual("2026-08-19", mugeju["reviewedOn"])
        self.assertEqual(["CADES"], mugeju["observedSignatureFormats"])
        self.assertEqual(
            [
                "AUTOSCRIPT",
                "CERTIFICATE_ACCESS",
                "CLIENT_TLS_AUTH",
                "ELECTRONIC_SIGNATURE",
                "MINIAPPLET",
            ],
            mugeju["observedMechanisms"],
        )
        self.assertIn("qa_only", mugeju["limitations"].lower())
        self.assertIn("signatureserviceurl", mugeju["limitations"].lower())

    def test_dsca_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        dsca = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-derechos-sociales-consumo-y-agenda-2030")
        self.assertEqual("reg-age-redsara", dsca["profileId"])
        self.assertEqual("ES-PUB-0064", dsca["inventoryId"])
        self.assertEqual("https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje", dsca["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", dsca["launchUrl"])
        self.assertEqual("E2E_PENDING", dsca["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", dsca["inventoryStatus"])
        self.assertEqual("REVIEWED", dsca["discoveryState"])
        self.assertIn("reg-age", dsca["limitations"].lower())
        self.assertIn("qa", dsca["limitations"].lower())
        self.assertIn("e2e", dsca["limitations"].lower())


    def test_inclusion_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        inclusion = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-inclusion-seguridad-social-y-migraciones")
        self.assertEqual("reg-age-redsara", inclusion["profileId"])
        self.assertEqual("ES-PUB-0068", inclusion["inventoryId"])
        self.assertEqual("https://sede.inclusion.gob.es/", inclusion["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", inclusion["launchUrl"])
        self.assertEqual("E2E_PENDING", inclusion["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", inclusion["inventoryStatus"])
        self.assertEqual("REVIEWED", inclusion["discoveryState"])
        self.assertEqual("2026-08-17", inclusion["reviewedOn"])
        self.assertEqual([], inclusion["observedSignatureFormats"])
        self.assertIn("reg-age", inclusion["limitations"].lower())
        self.assertIn("qa", inclusion["limitations"].lower())
        self.assertIn("e2e", inclusion["limitations"].lower())


    def test_cervantes_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        cervantes = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-instituto-cervantes")
        self.assertEqual("reg-age-redsara", cervantes["profileId"])
        self.assertEqual("ES-PUB-0049", cervantes["inventoryId"])
        self.assertEqual("https://cervantes.sede.gob.es/", cervantes["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", cervantes["launchUrl"])
        self.assertEqual("E2E_PENDING", cervantes["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", cervantes["inventoryStatus"])
        self.assertEqual("REVIEWED", cervantes["discoveryState"])
        self.assertEqual("2026-08-17", cervantes["reviewedOn"])
        self.assertEqual([], cervantes["observedSignatureFormats"])
        self.assertIn("reg-age", cervantes["limitations"].lower())
        self.assertIn("qa", cervantes["limitations"].lower())
        self.assertIn("e2e", cervantes["limitations"].lower())


    def test_reina_sofia_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        reina = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-museo-nacional-centro-de-arte-reina-sofia"
        )
        self.assertEqual("reg-age-redsara", reina["profileId"])
        self.assertEqual("ES-PUB-0080", reina["inventoryId"])
        self.assertEqual("https://museoreinasofia.sede.gob.es/", reina["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", reina["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", reina["protocolFamily"])
        self.assertEqual("E2E_PENDING", reina["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", reina["inventoryStatus"])
        self.assertEqual("REVIEWED", reina["discoveryState"])
        self.assertEqual("2026-08-17", reina["reviewedOn"])
        self.assertEqual([], reina["observedMechanisms"])
        self.assertEqual([], reina["observedSignatureFormats"])
        self.assertIn("reg-age", reina["limitations"].lower())
        self.assertIn("qa", reina["limitations"].lower())
        self.assertIn("e2e", reina["limitations"].lower())


    def test_inap_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        inap = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-instituto-nacional-de-administracion-publica-inap")
        self.assertEqual("reg-age-redsara", inap["profileId"])
        self.assertEqual("ES-PUB-0055", inap["inventoryId"])
        self.assertEqual("https://sede.inap.gob.es/", inap["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", inap["launchUrl"])
        self.assertEqual("E2E_PENDING", inap["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", inap["inventoryStatus"])
        self.assertEqual("REVIEWED", inap["discoveryState"])
        self.assertEqual("2026-08-17", inap["reviewedOn"])
        self.assertIn("reg-age", inap["limitations"].lower())
        self.assertIn("qa", inap["limitations"].lower())
        self.assertIn("e2e", inap["limitations"].lower())


    def test_cultura_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        cultura = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-cultura")
        self.assertEqual("reg-age-redsara", cultura["profileId"])
        self.assertEqual("ES-PUB-0062", cultura["inventoryId"])
        self.assertEqual("https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General", cultura["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", cultura["launchUrl"])
        self.assertEqual("E2E_PENDING", cultura["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", cultura["inventoryStatus"])
        self.assertEqual("REVIEWED", cultura["discoveryState"])
        self.assertEqual("2026-08-17", cultura["reviewedOn"])
        self.assertIn("reg-age", cultura["limitations"].lower())
        self.assertIn("qa", cultura["limitations"].lower())
        self.assertIn("e2e", cultura["limitations"].lower())


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


    def test_la_rioja_client_tls_profile_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0116")

        self.assertEqual("la-rioja-oficina-electronica", target["portalId"])
        self.assertEqual("la-rioja-oficina-electronica", target["profileId"])
        self.assertEqual(
            "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697",
            target["entryUrl"],
        )
        self.assertNotIn("launchUrl", target)
        self.assertEqual("CLIENT_TLS_AUTH", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("2026-08-18", target["reviewedOn"])
        self.assertIn("CLIENT_TLS_AUTH", target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())
        self.assertIn("firma", target["limitations"].lower())


    def test_bne_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        bne = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-biblioteca-nacional-de-espana"
        )

        self.assertEqual("ES-PUB-0028", bne["inventoryId"])
        self.assertEqual("reg-age-redsara", bne["profileId"])
        self.assertEqual(
            "https://sede.bne.gob.es/es/tramites/quejas-sugerencias",
            bne["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", bne["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", bne["protocolFamily"])
        self.assertEqual("E2E_PENDING", bne["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", bne["inventoryStatus"])
        self.assertIn("reg-age", bne["limitations"].lower())
        self.assertIn("e2e", bne["limitations"].lower())


    def test_tenerife_institutional_alias_binds_exact_qa_sede_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        tenerife = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "tenerife-portal-institucional"
        )

        self.assertEqual("tenerife-sede-electronica", tenerife["profileId"])
        self.assertEqual("ES-PUB-0127", tenerife["inventoryId"])
        self.assertEqual("https://www.tenerife.es/", tenerife["entryUrl"])
        self.assertEqual("https://sede.tenerife.es/", tenerife["launchUrl"])
        self.assertEqual("E2E_PENDING", tenerife["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", tenerife["inventoryStatus"])
        self.assertEqual("2026-08-16", tenerife["reviewedOn"])
        self.assertEqual("DELEGACION_TENERIFE_SEDE", tenerife["protocolFamily"])
        self.assertEqual([], tenerife["observedMechanisms"])
        self.assertEqual([], tenerife["observedSignatureFormats"])
        self.assertIn("alias", tenerife["limitations"].lower())
        self.assertIn("e2e", tenerife["limitations"].lower())

    def test_cantabria_sede_alias_binds_exact_existing_rec_profile(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["portalId"] == "cantabria-sede")

        self.assertEqual("cantabria-rec-cert-login", target["profileId"])
        self.assertEqual("https://sede.cantabria.es/sede/", target["entryUrl"])
        self.assertEqual("https://rec.cantabria.es/rec/bienvenida.htm", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertIn("alias", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())

    def test_la_palma_institutional_alias_binds_exact_existing_sede_profile(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["portalId"] == "la-palma-portal-institucional")

        self.assertEqual("la-palma-sede-electronica", target["profileId"])
        self.assertEqual("https://www.cabildodelapalma.es/", target["entryUrl"])
        self.assertEqual("https://sedeelectronica.cabildodelapalma.es/", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertIn("alias", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())

    def test_mivau_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        mivau = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-vivienda-y-agenda-urbana"
        )
        self.assertEqual("reg-age-redsara", mivau["profileId"])
        self.assertEqual("ES-PUB-0076", mivau["inventoryId"])
        self.assertEqual(
            "https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General",
            mivau["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", mivau["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", mivau["protocolFamily"])
        self.assertEqual("E2E_PENDING", mivau["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", mivau["inventoryStatus"])
        self.assertEqual("REVIEWED", mivau["discoveryState"])
        self.assertEqual("2026-08-17", mivau["reviewedOn"])
        self.assertEqual([], mivau["observedMechanisms"])
        self.assertEqual([], mivau["observedSignatureFormats"])
        self.assertIn("reg-age", mivau["limitations"].lower())
        self.assertIn("qa", mivau["limitations"].lower())
        self.assertIn("e2e", mivau["limitations"].lower())


    def test_miteco_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        miteco = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-para-la-transicion-ecologica-y-el-reto-demografico"
        )
        self.assertEqual("reg-age-redsara", miteco["profileId"])
        self.assertEqual("ES-PUB-0079", miteco["inventoryId"])
        self.assertEqual(
            "https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html",
            miteco["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", miteco["launchUrl"])
        self.assertEqual("E2E_PENDING", miteco["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", miteco["inventoryStatus"])
        self.assertEqual("REVIEWED", miteco["discoveryState"])
        self.assertEqual("2026-08-17", miteco["reviewedOn"])
        self.assertIn("reg-age", miteco["limitations"].lower())
        self.assertIn("qa", miteco["limitations"].lower())
        self.assertIn("e2e", miteco["limitations"].lower())


    def test_exteriores_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        exteriores = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-asuntos-exteriores-union-europea-y-cooperacion"
        )
        self.assertEqual("reg-age-redsara", exteriores["profileId"])
        self.assertEqual("ES-PUB-0060", exteriores["inventoryId"])
        self.assertEqual(
            "https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula",
            exteriores["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", exteriores["launchUrl"])
        self.assertEqual("E2E_PENDING", exteriores["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", exteriores["inventoryStatus"])
        self.assertEqual("REVIEWED", exteriores["discoveryState"])
        self.assertEqual("2026-08-16", exteriores["reviewedOn"])
        self.assertIn("reg-age", exteriores["limitations"].lower())
        self.assertIn("qa", exteriores["limitations"].lower())
        self.assertIn("e2e", exteriores["limitations"].lower())


    def test_mapa_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        mapa = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-agricultura-pesca-y-alimentacion"
        )
        self.assertEqual("ES-PUB-0059", mapa["inventoryId"])
        self.assertEqual("reg-age-redsara", mapa["profileId"])
        self.assertEqual("https://sede.mapa.gob.es/portal/site/seMAPA", mapa["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", mapa["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", mapa["protocolFamily"])
        self.assertEqual("E2E_PENDING", mapa["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", mapa["inventoryStatus"])
        self.assertEqual("REVIEWED", mapa["discoveryState"])
        self.assertEqual("2026-08-17", mapa["reviewedOn"])
        self.assertEqual([], mapa["observedMechanisms"])
        self.assertEqual([], mapa["observedSignatureFormats"])
        self.assertIn("reg-age", mapa["limitations"].lower())
        self.assertIn("qa", mapa["limitations"].lower())
        self.assertIn("e2e", mapa["limitations"].lower())


    def test_juventud_infancia_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-juventud-e-infancia")
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("ES-PUB-0070", target["inventoryId"])
        self.assertEqual("https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())

    def test_igualdad_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        igualdad = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-igualdad")
        self.assertEqual("reg-age-redsara", igualdad["profileId"])
        self.assertEqual("ES-PUB-0067", igualdad["inventoryId"])
        self.assertEqual("https://igualdad.sede.gob.es/", igualdad["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", igualdad["launchUrl"])
        self.assertEqual("E2E_PENDING", igualdad["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", igualdad["inventoryStatus"])
        self.assertEqual("REVIEWED", igualdad["discoveryState"])
        self.assertEqual("2026-08-17", igualdad["reviewedOn"])
        self.assertEqual([], igualdad["observedSignatureFormats"])
        self.assertIn("reg-age", igualdad["limitations"].lower())
        self.assertIn("qa", igualdad["limitations"].lower())
        self.assertIn("e2e", igualdad["limitations"].lower())

    def test_defensa_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        defensa = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-defensa")
        self.assertEqual("reg-age-redsara", defensa["profileId"])
        self.assertEqual("ES-PUB-0063", defensa["inventoryId"])
        self.assertEqual("https://sede.defensa.gob.es/", defensa["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", defensa["launchUrl"])
        self.assertEqual("E2E_PENDING", defensa["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", defensa["inventoryStatus"])
        self.assertEqual("REVIEWED", defensa["discoveryState"])
        self.assertEqual("2026-08-17", defensa["reviewedOn"])
        self.assertIn("reg-age", defensa["limitations"].lower())
        self.assertIn("qa", defensa["limitations"].lower())
        self.assertIn("e2e", defensa["limitations"].lower())

    def test_mpr_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-la-presidencia-justicia-y-relaciones-con-las-cortes"
        )
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("ES-PUB-0071", target["inventoryId"])
        self.assertEqual("https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())

    def test_mptmd_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-de-politica-territorial-y-memoria-democratica"
        )
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("ES-PUB-0072", target["inventoryId"])
        self.assertEqual("https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())

    def test_industria_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        industria = next(entry for entry in catalog["entries"] if entry["portalId"] == "age-ministerio-de-industria-y-turismo")
        self.assertEqual("reg-age-redsara", industria["profileId"])
        self.assertEqual("ES-PUB-0069", industria["inventoryId"])
        self.assertEqual("https://sede.minetur.gob.es/", industria["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", industria["launchUrl"])
        self.assertEqual("E2E_PENDING", industria["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", industria["inventoryStatus"])
        self.assertEqual("REVIEWED", industria["discoveryState"])
        self.assertEqual("2026-08-17", industria["reviewedOn"])
        self.assertEqual([], industria["observedSignatureFormats"])
        self.assertIn("reg-age", industria["limitations"].lower())
        self.assertIn("qa", industria["limitations"].lower())
        self.assertIn("e2e", industria["limitations"].lower())


    def test_interior_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-del-interior"
        )
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("ES-PUB-0077", target["inventoryId"])
        self.assertEqual("https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_transformacion_digital_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-ministerio-para-la-transformacion-digital-y-de-la-funcion-publica"
        )
        self.assertEqual("ES-PUB-0078", target["inventoryId"])
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("https://digital.sede.gob.es/", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertEqual([], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_alicante_solicitud_general_profile_binds_exact_public_launch_without_sensitive_mechanisms(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["inventoryId"] == "ES-PUB-0139"
        )
        self.assertEqual("diputacion-alicante-solicitud-general", target["profileId"])
        self.assertEqual(
            "https://diputacionalicante.sedelectronica.es/catalog/tw/66192629-8b04-4cf8-a121-e2cb86cd45cb",
            target["entryUrl"],
        )
        self.assertNotIn("launchUrl", target)
        self.assertEqual(
            "ALICANTE_SEDE_SOLICITUD_GENERAL_PUBLIC_LAUNCH",
            target["protocolFamily"],
        )
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-18", target["reviewedOn"])
        self.assertEqual([], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa_only", target["limitations"].lower())
        self.assertIn("client_tls_auth", target["limitations"].lower())

    def test_ctbg_solicitud_informacion_binds_exact_qa_launch_without_signing_capability(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0035")
        self.assertEqual("age-consejo-de-transparencia-y-buen-gobierno-ctbg", target["portalId"])
        self.assertEqual("ctbg-solicitud-informacion", target["profileId"])
        self.assertEqual("https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6", target["entryUrl"])
        self.assertNotIn("launchUrl", target)
        self.assertEqual("CTBG_ESPUBLICO_CLAVE_PUBLIC_LAUNCH", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-23", target["reviewedOn"])
        self.assertEqual(["CERTIFICATE_ACCESS"], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa-only", target["limitations"].lower())
        self.assertIn("client_tls_auth", target["limitations"].lower())
        self.assertIn("sin e2e", target["limitations"].lower())

    def test_diputacion_alava_binds_exact_registro_comun_qa_start_without_signer_capability(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0140")
        self.assertEqual("diputacion-alava-portal", target["portalId"])
        self.assertEqual("diputacion-alava-registro-comun", target["profileId"])
        self.assertEqual("https://egoitza.araba.eus/izapidetu/at/01/es/0000301", target["entryUrl"])
        self.assertNotIn("launchUrl", target)
        self.assertEqual("ALAVA_EGOITZA_REGISTRO_COMUN_QA_LAUNCH", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-18", target["reviewedOn"])
        self.assertEqual(["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa_only", target["limitations"].lower())
        self.assertIn("dinámic", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_oepm_protegeo_profile_binds_exact_public_launch_without_sensitive_mechanisms(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        oepm = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-oficina-espanola-de-patentes-y-marcas"
        )
        self.assertEqual("ES-PUB-0082", oepm["inventoryId"])
        self.assertEqual("oepm-protegeo-general", oepm["profileId"])
        self.assertEqual(
            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM",
            oepm["entryUrl"],
        )
        self.assertNotIn("launchUrl", oepm)
        self.assertEqual("OEPM_PROTEGEO_PUBLIC_LAUNCH", oepm["protocolFamily"])
        self.assertEqual("E2E_PENDING", oepm["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", oepm["inventoryStatus"])
        self.assertEqual("REVIEWED", oepm["discoveryState"])
        self.assertEqual("2026-08-17", oepm["reviewedOn"])
        self.assertEqual([], oepm["observedMechanisms"])
        self.assertEqual([], oepm["observedSignatureFormats"])
        self.assertIn("qa_only", oepm["limitations"].lower())
        self.assertIn("e2e", oepm["limitations"].lower())


    def test_castilla_leon_quju_binds_exact_public_form_without_signing_capability(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        quju = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "castilla-leon-tramita"
        )
        self.assertEqual("ES-PUB-0102", quju["inventoryId"])
        self.assertEqual("castilla-leon-quju-public", quju["profileId"])
        self.assertEqual("https://presidencia.jcyl.es/QUJU?O=1", quju["entryUrl"])
        self.assertNotIn("launchUrl", quju)
        self.assertEqual("JCYL_QUJU_PUBLIC_FORM_BOUNDARY", quju["protocolFamily"])
        self.assertEqual("E2E_PENDING", quju["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", quju["inventoryStatus"])
        self.assertEqual("REVIEWED", quju["discoveryState"])
        self.assertEqual("2026-08-19", quju["reviewedOn"])
        self.assertEqual([], quju["observedMechanisms"])
        self.assertEqual([], quju["observedSignatureFormats"])
        self.assertIn("qa_only", quju["limitations"].lower())
        self.assertIn("no_verificado", quju["limitations"].lower())


    def test_portal_funciona_binds_exact_public_home_without_auth_or_signing_capability(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        funciona = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-portal-funciona"
        )
        self.assertEqual("ES-PUB-0084", funciona["inventoryId"])
        self.assertEqual("portal-funciona-public-home", funciona["profileId"])
        self.assertEqual("https://sede.funciona.gob.es/es/home", funciona["entryUrl"])
        self.assertNotIn("launchUrl", funciona)
        self.assertEqual("OIDC_PKCE_AUTENTICA_SAML_CLIENT_TLS_BOUNDARY", funciona["protocolFamily"])
        self.assertEqual("E2E_PENDING", funciona["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", funciona["inventoryStatus"])
        self.assertEqual("REVIEWED", funciona["discoveryState"])
        self.assertEqual("2026-08-17", funciona["reviewedOn"])
        self.assertEqual(["CERTIFICATE_ACCESS", "CLIENT_TLS_AUTH"], funciona["observedMechanisms"])
        self.assertEqual([], funciona["observedSignatureFormats"])
        self.assertIn("qa_only", funciona["limitations"].lower())
        self.assertIn("auth-api.redsara.es", funciona["limitations"].lower())
        self.assertIn("fnc", funciona["limitations"].lower())
        self.assertIn("e2e", funciona["limitations"].lower())


    def test_diputacion_avila_instancia_general_binds_exact_pending_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0143")
        self.assertEqual("diputacion-avila-portal", target["portalId"])
        self.assertEqual("diputacion-avila-instancia-general", target["profileId"])
        self.assertEqual(
            "https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5",
            target["entryUrl"],
        )
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-18", target["reviewedOn"])
        self.assertIn("CERTIFICATE_ACCESS", target["observedMechanisms"])
        self.assertIn("ELECTRONIC_SIGNATURE", target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertEqual("NO_VERIFICADO", target["protocolFamily"])

    def test_comercio_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-secretaria-de-estado-de-comercio"
        )
        self.assertEqual("ES-PUB-0087", target["inventoryId"])
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual(
            "https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517",
            target["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertEqual([], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_digital_sede_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-sede-electronica-de-la-s-e-de-digitalizacion-e-inteligencia-artificial-y-s-e-de-telecomunica"
        )
        self.assertEqual("ES-PUB-0089", target["inventoryId"])
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual("https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx", target["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertEqual([], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_hacienda_central_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        hacienda = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-sede-electronica-central-del-ministerio"
        )
        self.assertEqual("ES-PUB-0088", hacienda["inventoryId"])
        self.assertEqual("reg-age-redsara", hacienda["profileId"])
        self.assertEqual("https://sede.hacienda.gob.es/", hacienda["entryUrl"])
        self.assertEqual("https://reg.redsara.es/es/", hacienda["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", hacienda["protocolFamily"])
        self.assertEqual("E2E_PENDING", hacienda["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", hacienda["inventoryStatus"])
        self.assertEqual("REVIEWED", hacienda["discoveryState"])
        self.assertEqual("2026-08-17", hacienda["reviewedOn"])
        self.assertEqual([], hacienda["observedMechanisms"])
        self.assertEqual([], hacienda["observedSignatureFormats"])
        self.assertIn("reg-age", hacienda["limitations"].lower())
        self.assertIn("qa", hacienda["limitations"].lower())
        self.assertIn("e2e", hacienda["limitations"].lower())


    def test_tesoro_reg_age_alias_binds_exact_qa_launch(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(
            entry for entry in catalog["entries"]
            if entry["portalId"] == "age-tesoro-publico"
        )
        self.assertEqual("ES-PUB-0091", target["inventoryId"])
        self.assertEqual("reg-age-redsara", target["profileId"])
        self.assertEqual(
            "https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de",
            target["entryUrl"],
        )
        self.assertEqual("https://reg.redsara.es/es/", target["launchUrl"])
        self.assertEqual("DELEGACION_REG_AGE", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertEqual([], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("reg-age", target["limitations"].lower())
        self.assertIn("qa", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_comunidad_madrid_registro_general_binds_exact_qa_launch_only(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0012")

        self.assertEqual("comunidad-madrid-sede", target["portalId"])
        self.assertEqual("comunidad-madrid-registro-general", target["profileId"])
        self.assertEqual(
            "https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm",
            target["entryUrl"],
        )
        self.assertNotIn("launchUrl", target)
        self.assertEqual("MADRID_EREG_MULTIPART_ROUTER", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-19", target["reviewedOn"])
        self.assertEqual(["CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"], target["observedMechanisms"])
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa-only", target["limitations"].lower())
        self.assertIn("formato", target["limitations"].lower())
        self.assertIn("e2e", target["limitations"].lower())


    def test_junta_andalucia_vea_peg_profile_binds_exact_public_start(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0093")

        self.assertEqual("junta-andalucia-sede", target["portalId"])
        self.assertEqual("junta-andalucia-vea-peg", target["profileId"])
        self.assertEqual(
            "https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA",
            target["entryUrl"],
        )
        self.assertNotIn("launchUrl", target)
        self.assertEqual("VEA_AUTOSCRIPT_DYNAMIC", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual("REVIEWED", target["discoveryState"])
        self.assertEqual("2026-08-17", target["reviewedOn"])
        self.assertEqual(
            ["AUTOFIRMA", "AUTOSCRIPT", "CERTIFICATE_ACCESS", "ELECTRONIC_SIGNATURE"],
            target["observedMechanisms"],
        )
        self.assertEqual([], target["observedSignatureFormats"])
        self.assertIn("qa-only", target["limitations"].lower())
        self.assertIn("autenticado", target["limitations"].lower())


    def test_fuerteventura_pades_profile_binds_exact_qa_pending_contract(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        entry = next(item for item in catalog["entries"] if item["inventoryId"] == "ES-PUB-0134")

        self.assertEqual("fuerteventura-sede-electronica", entry["portalId"])
        self.assertEqual("fuerteventura-sede-electronica", entry["profileId"])
        self.assertEqual(
            "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1",
            entry["entryUrl"],
        )
        self.assertNotIn("launchUrl", entry)
        self.assertEqual("MINIAPPLET_LOCAL_PADES", entry["protocolFamily"])
        self.assertEqual("E2E_PENDING", entry["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", entry["inventoryStatus"])
        self.assertEqual(["PADES"], entry["observedSignatureFormats"])
        self.assertIn("AUTOFIRMA", entry["observedMechanisms"])
        self.assertIn("MINIAPPLET", entry["observedMechanisms"])
        self.assertEqual("2026-08-18", entry["reviewedOn"])
        self.assertIn("e2e", entry["limitations"].lower())


    def test_mineco_instancia_generica_binds_exact_qa_profile(self) -> None:
        catalog = GENERATOR.generate(SOURCE, SITE_PROFILES)
        target = next(entry for entry in catalog["entries"] if entry["inventoryId"] == "ES-PUB-0065")

        self.assertEqual("ministerio-economia-instancia-generica", target["profileId"])
        self.assertEqual(
            "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG",
            target["entryUrl"],
        )
        self.assertEqual("MINIAPPLET_LOCAL_PADES", target["protocolFamily"])
        self.assertEqual("E2E_PENDING", target["catalogStatus"])
        self.assertEqual("IMPLEMENTED_NOT_E2E", target["inventoryStatus"])
        self.assertEqual(["PADES"], target["observedSignatureFormats"])
        self.assertIn("AUTOFIRMA", target["observedMechanisms"])
        self.assertIn("MINIAPPLET", target["observedMechanisms"])
        self.assertIn("CLIENT_TLS_AUTH", target["observedMechanisms"])
        self.assertEqual("2026-08-17", target["reviewedOn"])


if __name__ == "__main__":
    unittest.main()


if __name__ == "__main__":
    unittest.main()
