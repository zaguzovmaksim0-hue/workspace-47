from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import types
import unittest
from unittest import mock


TOOLS_DIR = Path(__file__).parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))
MODULE_PATH = TOOLS_DIR / "ccaa_directory.py"
SPEC = importlib.util.spec_from_file_location("ccaa_directory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
ccaa = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = ccaa
SPEC.loader.exec_module(ccaa)


EXPECTED_SOURCE_LABELS = (
    "Andalucía",
    "Aragón",
    "Asturias, Principado de",
    "Balears, Illes",
    "Canarias",
    "Cantabria",
    "Castilla y León",
    "Castilla-La Mancha",
    "Cataluña",
    "Ciudad de Ceuta",
    "Ciudad de Melilla",
    "Comunitat Valenciana",
    "Extremadura",
    "Galicia",
    "Madrid, Comunidad de",
    "Murcia, Región de",
    "Navarra, Comunidad Foral de",
    "País Vasco",
    "Rioja, La",
)
EXPECTED_TERRITORIES = (
    "Andalucía",
    "Aragón",
    "Principado de Asturias",
    "Illes Balears",
    "Canarias",
    "Cantabria",
    "Castilla y León",
    "Castilla-La Mancha",
    "Cataluña",
    "Ciudad Autónoma de Ceuta",
    "Ciudad Autónoma de Melilla",
    "Comunitat Valenciana",
    "Extremadura",
    "Galicia",
    "Comunidad de Madrid",
    "Región de Murcia",
    "Comunidad Foral de Navarra",
    "País Vasco",
    "La Rioja",
)


def valid_links() -> list[tuple[str, str]]:
    links: list[tuple[str, str]] = []
    for index, name in enumerate(EXPECTED_SOURCE_LABELS):
        scheme = "https" if name in {
            "Extremadura",
            "Galicia",
            "Madrid, Comunidad de",
        } else "http"
        suffix = "?lang=es" if name == "Balears, Illes" else ""
        links.append((name, f"{scheme}://territory-{index}.example.es/sede{suffix}"))
    return links


def fixture_html(
    links: list[tuple[str, str]] | None = None,
    *,
    extra_target_div: bool = False,
) -> str:
    links = links if links is not None else valid_links()
    body = "".join(
        f'<a class="territory" href="{href}"><span>{name}</span></a>'
        for name, href in links
    )
    duplicate = '<div id="pie_comunidad"></div>' if extra_target_div else ""
    return f"""
        <html><body>
          </div>
          <a href="https://outside.example.es/">Andalucía</a>
          <map name="ccaa-map">
            <area href="https://map.example.es/" alt="Andalucía">
            <area href="https://map.example.es/" alt="Aragón">
          </map>
          <div id="unrelated"><a href="https://chrome.example.es/">Galicia</a></div>
          <div id="pie_comunidad">{body}</div>
          {duplicate}
        </body></html>
    """


class RecordingFetcher:
    def __init__(
        self,
        html: str,
        *,
        redirect_chain: tuple[str, ...] = (),
        request_count: int = 1,
    ) -> None:
        self.body = html.encode("utf-8")
        self.redirect_chain = redirect_chain
        self.request_count = request_count
        self.calls: list[tuple[str, dict[str, object]]] = []

    def fetch(self, url: str, **kwargs):
        self.calls.append((url, kwargs))
        return ccaa.inventory.FetchResult(
            requested_url=ccaa.SOURCE_URL,
            final_url=ccaa.SOURCE_URL,
            redirect_chain=self.redirect_chain,
            status=200,
            content_type="text/html",
            body=self.body,
            request_count=self.request_count,
        )


class DirectoryParsingTest(unittest.TestCase):
    def test_closed_allowlist_is_explicit_and_complete(self) -> None:
        self.assertEqual(ccaa.SOURCE_LABEL_ORDER, EXPECTED_SOURCE_LABELS)
        self.assertEqual(len(ccaa.EXPECTED_SOURCE_LABELS), 19)
        self.assertEqual(
            tuple(ccaa.SOURCE_LABEL_TO_TERRITORY[label] for label in EXPECTED_SOURCE_LABELS),
            EXPECTED_TERRITORIES,
        )

    def test_only_unique_pie_comunidad_anchors_are_parsed(self) -> None:
        anchors = ccaa.parse_directory_html(fixture_html())
        self.assertEqual(
            tuple(anchor.source_label for anchor in anchors),
            EXPECTED_SOURCE_LABELS,
        )
        self.assertEqual(tuple(anchor.territory for anchor in anchors), EXPECTED_TERRITORIES)
        self.assertEqual(len(anchors), 19)
        self.assertNotIn("https://outside.example.es/", {anchor.href for anchor in anchors})
        self.assertNotIn("https://map.example.es/", {anchor.href for anchor in anchors})

    def test_nfkc_and_whitespace_are_normalized(self) -> None:
        links = valid_links()
        links[0] = ("  Andaluci\u0301a\n", links[0][1])
        anchors = ccaa.parse_directory_html(fixture_html(links))
        self.assertEqual(anchors[0].source_label, "Andalucía")
        self.assertEqual(anchors[0].territory, "Andalucía")

    def test_unknown_missing_and_duplicate_territories_fail_closed(self) -> None:
        cases: list[list[tuple[str, str]]] = []
        unknown = valid_links()
        unknown[0] = ("Atlantis", unknown[0][1])
        cases.append(unknown)
        cases.append(valid_links()[:-1])
        duplicate = valid_links()
        duplicate[-1] = (duplicate[0][0], duplicate[-1][1])
        cases.append(duplicate)
        for links in cases:
            with self.subTest(names=[name for name, _ in links]):
                with self.assertRaises(ccaa.CcaaDirectoryError):
                    ccaa.parse_directory_html(fixture_html(links))

    def test_unknown_label_is_redacted_and_control_characters_are_rejected(self) -> None:
        marker = "PRIVATE_MARKER_DO_NOT_ECHO"
        unknown = valid_links()
        unknown[0] = (marker, unknown[0][1])
        with self.assertRaises(ccaa.CcaaDirectoryError) as captured:
            ccaa.parse_directory_html(fixture_html(unknown))
        self.assertNotIn(marker, str(captured.exception))

        controlled = valid_links()
        controlled[0] = ("Andalucía\x1b[31m", controlled[0][1])
        with self.assertRaises(ccaa.CcaaDirectoryError) as captured:
            ccaa.parse_directory_html(fixture_html(controlled))
        self.assertNotIn("\x1b", str(captured.exception))

    def test_zero_or_multiple_target_divs_fail_closed(self) -> None:
        without_target = fixture_html().replace('id="pie_comunidad"', 'id="not-it"')
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.parse_directory_html(without_target)
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.parse_directory_html(fixture_html(extra_target_div=True))


class DirectoryEnumerationTest(unittest.TestCase):
    def test_one_source_get_no_target_fetch_and_reviewed_scheme_counts(self) -> None:
        fetcher = RecordingFetcher(fixture_html())
        records = ccaa.enumerate_directory(fetcher, "2026-07-16")
        self.assertEqual(
            fetcher.calls,
            [
                (
                    ccaa.SOURCE_URL,
                    {"allowed_query_keys": (), "allowed_redirect_origins": ()},
                )
            ],
        )
        self.assertEqual(len(records), 19)
        self.assertEqual(
            sum(record["target"]["scheme"] == "https" for record in records),
            3,
        )
        self.assertEqual(
            sum(record["target"]["scheme"] == "http" for record in records),
            16,
        )
        self.assertTrue(all(record["compatibility_status"] == "BROWSE_ONLY" for record in records))
        self.assertTrue(all(record["discovery_state"] == "CANDIDATE" for record in records))
        self.assertTrue(all(record["contract_claims"] == [] for record in records))
        self.assertTrue(all(record["protocol_family"] == "NO_VERIFICADO" for record in records))

    def test_http_rows_are_non_executable_components(self) -> None:
        records = ccaa.enumerate_directory(RecordingFetcher(fixture_html()), "2026-07-16")
        http_records = [record for record in records if record["target"]["scheme"] == "http"]
        self.assertEqual(len(http_records), 16)
        for record in http_records:
            self.assertEqual(record["target_status"], "HTTPS_RESOLUTION_REQUIRED")
            self.assertFalse(record["candidate_seed_eligible"])
            self.assertFalse(record["target_fetch_performed"])
            self.assertEqual(record["target"]["kind"], "HTTP_REFERENCE_COMPONENTS")
            self.assertNotIn("url", record["target"])
            self.assertNotIn("origin", record["target"])
            self.assertIn("host", record["target"])
            self.assertIn("path", record["target"])

    def test_https_rows_use_sanitized_url_and_exact_origin(self) -> None:
        records = ccaa.enumerate_directory(RecordingFetcher(fixture_html()), "2026-07-16")
        https_records = [record for record in records if record["target"]["scheme"] == "https"]
        self.assertEqual(len(https_records), 3)
        for record in https_records:
            self.assertTrue(record["candidate_seed_eligible"])
            self.assertFalse(record["target_fetch_performed"])
            self.assertEqual(record["target_status"], "HTTPS_REFERENCE_VALIDATED")
            self.assertEqual(
                record["target"]["origin"],
                ccaa.inventory.exact_origin(record["target"]["url"]),
            )

    def test_only_balearic_lang_es_query_is_allowed(self) -> None:
        records = ccaa.enumerate_directory(RecordingFetcher(fixture_html()), "2026-07-16")
        queries = {
            record["territory"]: record["target"]["public_query_pairs"]
            for record in records
            if record["target"]["public_query_pairs"]
        }
        self.assertEqual(queries, {"Illes Balears": [["lang", "es"]]})

        bad_value = valid_links()
        index = EXPECTED_SOURCE_LABELS.index("Balears, Illes")
        bad_value[index] = ("Balears, Illes", "http://balears.example.es/?lang=en")
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(RecordingFetcher(fixture_html(bad_value)), "2026-07-16")

        extra_query = valid_links()
        extra_query[0] = ("Andalucía", extra_query[0][1] + "?lang=es")
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(RecordingFetcher(fixture_html(extra_query)), "2026-07-16")

    def test_unreviewed_query_value_is_not_reflected_in_errors(self) -> None:
        marker = "PRIVATE_MARKER_DO_NOT_ECHO"
        links = valid_links()
        links[0] = ("Andalucía", links[0][1] + f"?selector={marker}")
        with self.assertRaises(ccaa.CcaaDirectoryError) as captured:
            ccaa.enumerate_directory(RecordingFetcher(fixture_html(links)), "2026-07-16")
        self.assertNotIn(marker, str(captured.exception))

    def test_malformed_authorities_fail_with_sanitized_domain_error(self) -> None:
        for malformed in ("https://[bad/", "https://exa／mple.example.es/"):
            with self.subTest(malformed=malformed):
                links = valid_links()
                links[0] = ("Andalucía", malformed)
                with self.assertRaises(ccaa.CcaaDirectoryError) as captured:
                    ccaa.enumerate_directory(
                        RecordingFetcher(fixture_html(links)),
                        "2026-07-16",
                    )
                self.assertIn("target authority is invalid", str(captured.exception))
                self.assertNotIn(malformed, str(captured.exception))

    def test_scheme_baseline_drift_fails_closed(self) -> None:
        links = valid_links()
        name, href = links[1]
        links[1] = (name, href.replace("http://", "https://", 1))
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(RecordingFetcher(fixture_html(links)), "2026-07-16")

        same_count_different_territories = valid_links()
        aragon_index = EXPECTED_SOURCE_LABELS.index("Aragón")
        extremadura_index = EXPECTED_SOURCE_LABELS.index("Extremadura")
        aragon_name, aragon_url = same_count_different_territories[aragon_index]
        extremadura_name, extremadura_url = same_count_different_territories[extremadura_index]
        same_count_different_territories[aragon_index] = (
            aragon_name,
            aragon_url.replace("http://", "https://", 1),
        )
        same_count_different_territories[extremadura_index] = (
            extremadura_name,
            extremadura_url.replace("https://", "http://", 1),
        )
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(
                RecordingFetcher(fixture_html(same_count_different_territories)),
                "2026-07-16",
            )

    def test_redirects_and_multiple_requests_fail_closed(self) -> None:
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(
                RecordingFetcher(fixture_html(), redirect_chain=(ccaa.SOURCE_URL,)),
                "2026-07-16",
            )
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.enumerate_directory(
                RecordingFetcher(fixture_html(), request_count=2),
                "2026-07-16",
            )


class FixtureAndOutputTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_fixture_open_rejects_symlink_and_multi_link_metadata(self) -> None:
        source = self.root / "source.html"
        source.write_text(fixture_html(), encoding="utf-8")
        symlink = self.root / "symlink.html"
        symlink.symlink_to(source)
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.read_fixture_secure(symlink)

        metadata = source.stat()
        multi_link = types.SimpleNamespace(
            st_mode=metadata.st_mode,
            st_nlink=2,
            st_size=metadata.st_size,
        )
        with mock.patch.object(ccaa.os, "fstat", return_value=multi_link):
            with self.assertRaises(ccaa.CcaaDirectoryError):
                ccaa.read_fixture_secure(source)

    def test_fixture_open_is_bounded(self) -> None:
        source = self.root / "source.html"
        source.write_bytes(b"x" * 33)
        with self.assertRaises(ccaa.CcaaDirectoryError):
            ccaa.read_fixture_secure(source, max_bytes=32)

    def test_jsonl_output_is_deterministic_atomic_and_mode_0600(self) -> None:
        records = ccaa.enumerate_directory(RecordingFetcher(fixture_html()), "2026-07-16")
        self.assertTrue(
            all(record["snapshot_id"] == "ccaa-directory-2026-07-16" for record in records)
        )
        self.assertTrue(all(record["source_id"] == "D03" for record in records))
        output = self.root / "ccaa.jsonl"
        ccaa.write_jsonl_atomic(output, records)
        first = output.read_bytes()
        output.write_text("old", encoding="utf-8")
        os.chmod(output, 0o644)
        ccaa.write_jsonl_atomic(output, records)
        second = output.read_bytes()
        self.assertEqual(first, second)
        self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
        decoded = [json.loads(line) for line in second.decode("utf-8").splitlines()]
        self.assertEqual(decoded, records)

    def test_cli_stderr_does_not_echo_untrusted_query_values(self) -> None:
        marker = "PRIVATE_MARKER_DO_NOT_ECHO"
        links = valid_links()
        links[0] = ("Andalucía", links[0][1] + f"?selector={marker}")
        fixture = self.root / "source.html"
        fixture.write_text(fixture_html(links), encoding="utf-8")
        output = self.root / "output.jsonl"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            result = ccaa.main(
                [
                    "--fixture-html",
                    str(fixture),
                    "--snapshot-date",
                    "2026-07-16",
                    "--output",
                    str(output),
                ]
            )
        self.assertEqual(result, 2)
        self.assertNotIn(marker, stderr.getvalue())
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
