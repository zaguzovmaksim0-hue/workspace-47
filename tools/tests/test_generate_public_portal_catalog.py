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
        self.assertEqual(
            profile_count,
            sum(entry["profileId"] is not None for entry in catalog["entries"]),
        )
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
