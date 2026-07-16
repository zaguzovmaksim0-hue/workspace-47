from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock


TOOLS_DIR = Path(__file__).parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))
MODULE_PATH = TOOLS_DIR / "age_sede_directory.py"
SPEC = importlib.util.spec_from_file_location("age_sede_directory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
age = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = age
SPEC.loader.exec_module(age)


FIXTURE = """<!doctype html>
<html><body>
  <a href="https://outside.example.es/">Outside chrome</a>
  <div class="cmp-accordion list-container">
    <div class="cmp-accordion__item pagination-sgad">
      <button class="cmp-accordion__button"><span>Ministerio Uno</span></button>
      <div class="cmp-accordion__panel">
        <div class="cmp-text dnt-title-default">
          <p><a href="https://sede.one.gob.es/path">Organismo Uno</a>
             <a href="https://sede.one.gob.es/path"><span></span></a></p>
        </div>
        <div class="cmp-text dnt-title-default">
          <a href="/">Punto de Acceso General (PAGe)</a>
        </div>
        <div class="cmp-text dnt-title-default">
          <a href="https://sede.ciemat.gob.es/app;jsessionid=opaque?IDM=private&amp;NM=private2">
            Centro de Investigaciones Energéticas, Medioambientales y Tecnológicas (CIEMAT)
          </a>
        </div>
      </div>
    </div>
    <div class="cmp-accordion__item pagination-sgad">
      <button class="cmp-accordion__button">Ministerio Dos</button>
      <div class="cmp-accordion__panel">
        <div class="cmp-text dnt-title-default">
          <a href="https://sede.one.gob.es/path">Organismo Uno</a>
        </div>
      </div>
    </div>
  </div>
</body></html>""".encode("utf-8")


class DirectoryParserTest(unittest.TestCase):
    def test_collapses_card_links_and_cross_ministry_duplicates(self) -> None:
        parsed = age.parse_directory_html(FIXTURE)
        self.assertEqual(parsed.ministry_count, 2)
        self.assertEqual(parsed.card_count, 4)
        self.assertEqual(parsed.href_occurrence_count, 5)
        self.assertEqual(len(parsed.entries), 3)

        by_origin = {entry.origin: entry for entry in parsed.entries}
        one = by_origin["https://sede.one.gob.es"]
        self.assertEqual(one.entry_url, "https://sede.one.gob.es/path")
        self.assertEqual(one.ministries, ("Ministerio Dos", "Ministerio Uno"))
        self.assertEqual(one.source_occurrence_count, 2)
        self.assertEqual(one.href_occurrence_count, 3)

        page = by_origin["https://sede.administracion.gob.es"]
        self.assertEqual(page.entry_url, "https://sede.administracion.gob.es/")
        self.assertEqual(page.url_sanitization, "EXACT")

    def test_redacts_allowlisted_sessionized_source_url_to_origin(self) -> None:
        parsed = age.parse_directory_html(FIXTURE)
        ciemat = next(entry for entry in parsed.entries if "CIEMAT" in entry.institution_name)
        self.assertEqual(ciemat.origin, "https://sede.ciemat.gob.es")
        self.assertEqual(ciemat.entry_url, "https://sede.ciemat.gob.es/")
        self.assertEqual(ciemat.url_sanitization, "ORIGIN_FALLBACK")
        self.assertNotIn("opaque", repr(parsed))
        self.assertNotIn("private", repr(parsed))

    def test_rejects_unexpected_query_bearing_directory_link(self) -> None:
        body = FIXTURE.replace(
            b"https://sede.one.gob.es/path",
            b"https://sede.one.gob.es/path?session=secret",
        )
        with self.assertRaisesRegex(age.InventoryError, "unexpected unsafe"):
            age.parse_directory_html(body)

    def test_rejects_ambiguous_entry_paths_inside_one_card(self) -> None:
        body = FIXTURE.replace(
            b'<a href="https://sede.one.gob.es/path"><span></span></a>',
            b'<a href="https://sede.one.gob.es/other"><span></span></a>',
        )
        with self.assertRaisesRegex(age.InventoryError, "multiple entry URLs"):
            age.parse_directory_html(body)

    def test_rejects_missing_or_truncated_accordion(self) -> None:
        with self.assertRaisesRegex(age.InventoryError, "accordion was not found"):
            age.parse_directory_html(b"<html><body>No directory</body></html>")
        with self.assertRaisesRegex(age.InventoryError, "truncated"):
            age.parse_directory_html(
                b'<div class="cmp-accordion list-container">'
                b'<div class="cmp-accordion__item pagination-sgad">'
                b'<button class="cmp-accordion__button">Ministry'
            )

    def test_ignores_item_shaped_chrome_outside_the_catalog(self) -> None:
        body = b"""
          <div class="cmp-accordion__item pagination-sgad">
            <button class="cmp-accordion__button">Fake</button>
            <div class="cmp-text"><a href="https://fake.example.es/">Fake</a></div>
          </div>
        """
        with self.assertRaisesRegex(age.InventoryError, "accordion was not found"):
            age.parse_directory_html(body)

    def test_rejects_empty_item_and_multiple_catalog_accordions(self) -> None:
        empty = b"""
          <div class="cmp-accordion list-container">
            <div class="cmp-accordion__item pagination-sgad">
              <button class="cmp-accordion__button">Empty ministry</button>
            </div>
          </div>
        """
        with self.assertRaisesRegex(age.InventoryError, "no institution cards"):
            age.parse_directory_html(empty)
        with self.assertRaisesRegex(age.InventoryError, "multiple or nested"):
            age.parse_directory_html(FIXTURE + FIXTURE)

    def test_ciemat_fallback_does_not_hide_malformed_paths(self) -> None:
        malformed = FIXTURE.replace(b"/app;jsessionid=opaque", b"/app%252f;jsessionid=opaque")
        with self.assertRaisesRegex(age.InventoryError, "malformed"):
            age.parse_directory_html(malformed)


class OutputTest(unittest.TestCase):
    def test_builds_browse_only_records_without_raw_source_values(self) -> None:
        parsed = age.parse_directory_html(FIXTURE)
        fetched = age.FetchResult(
            requested_url=age.SOURCE_URL,
            final_url=age.SOURCE_URL,
            redirect_chain=(),
            status=200,
            content_type="text/html; charset=utf-8",
            body=FIXTURE,
        )
        records = age.build_records(parsed, fetched, "2026-07-16")
        self.assertEqual(len(records), 3)
        self.assertTrue(all(record["compatibility_status"] == "BROWSE_ONLY" for record in records))
        self.assertTrue(all(record["discovery_state"] == "DISCOVERED" for record in records))
        payload = json.dumps(records, ensure_ascii=False)
        self.assertNotIn("opaque", payload)
        self.assertNotIn("private", payload)
        self.assertNotIn("jsessionid", payload.casefold())

    def test_cli_writes_deterministic_private_jsonl(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = root / "directory.html"
            output = root / "snapshot.jsonl"
            fixture.write_bytes(FIXTURE)
            self.assertEqual(
                age.main(
                    [
                        "--html-fixture",
                        str(fixture),
                        "--snapshot-date",
                        "2026-07-16",
                        "--output",
                        str(output),
                    ]
                ),
                0,
            )
            first = output.read_bytes()
            first_mode = stat.S_IMODE(output.stat().st_mode)
            self.assertEqual(first_mode, 0o600)
            self.assertEqual(
                age.main(
                    [
                        "--html-fixture",
                        str(fixture),
                        "--snapshot-date",
                        "2026-07-16",
                        "--output",
                        str(output),
                    ]
                ),
                0,
            )
            self.assertEqual(output.read_bytes(), first)
            self.assertEqual(len(first.splitlines()), 3)

    @unittest.skipUnless(hasattr(os, "symlink"), "symlinks unavailable")
    def test_fixture_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            real = root / "real.html"
            link = root / "link.html"
            real.write_bytes(FIXTURE)
            link.symlink_to(real)
            with self.assertRaisesRegex(age.InventoryError, "single-link"):
                age._load_fixture(link)

    @unittest.skipUnless(hasattr(os, "link"), "hardlinks unavailable")
    def test_fixture_hardlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            real = root / "real.html"
            link = root / "hardlink.html"
            real.write_bytes(FIXTURE)
            os.link(real, link)
            with self.assertRaisesRegex(age.InventoryError, "single-link"):
                age._load_fixture(link)

    def test_live_fetch_has_zero_redirect_budget_and_one_source_call(self) -> None:
        observed: dict[str, object] = {}

        class FakeFetcher:
            def __init__(self, limits) -> None:
                observed["limits"] = limits
                observed["calls"] = 0

            def fetch(self, url: str, **kwargs):
                observed["calls"] = int(observed["calls"]) + 1
                observed["url"] = url
                observed["kwargs"] = kwargs
                return age.FetchResult(
                    requested_url=url,
                    final_url=url,
                    redirect_chain=(),
                    status=200,
                    content_type="text/html",
                    body=FIXTURE,
                )

        with mock.patch.object(age, "LiveFetcher", FakeFetcher):
            result = age.fetch_live_directory(1024 * 1024, 5.0)
        self.assertEqual(result.status, 200)
        self.assertEqual(observed["calls"], 1)
        self.assertEqual(observed["url"], age.SOURCE_URL)
        self.assertEqual(observed["kwargs"], {"same_origin": age.SOURCE_ORIGIN})
        self.assertEqual(observed["limits"].max_redirects, 0)
        self.assertEqual(observed["limits"].max_assets, 0)

    def test_reviewed_live_baseline_rejects_drift(self) -> None:
        parsed = age.parse_directory_html(FIXTURE)
        with self.assertRaisesRegex(age.InventoryError, "baseline required"):
            age.validate_reviewed_live_baseline(parsed)


if __name__ == "__main__":
    unittest.main()
