from __future__ import annotations

import contextlib
import hashlib
import html
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
from urllib.parse import urlencode


TOOLS_DIR = Path(__file__).parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))
MODULE_PATH = TOOLS_DIR / "pag_local_directory.py"
SPEC = importlib.util.spec_from_file_location("pag_local_directory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
pag = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = pag
SPEC.loader.exec_module(pag)


def valid_links(kind: str) -> list[tuple[str, str]]:
    spec = pag.DIRECTORIES[kind]
    links: list[tuple[str, str]] = []
    for index, label in enumerate(spec.labels):
        scheme = "https" if label in spec.https_labels else "http"
        query = spec.reviewed_queries.get(label, ())
        suffix = "?" + urlencode(query) if query else ""
        href = f"{scheme}://target-{index}.example.es/directory{suffix}"
        links.append((label, href))
    if kind == "municipal_queues":
        by_label = {label: index for index, (label, _) in enumerate(links)}
        shared = "https://shared.example.es/municipalities?"
        links[by_label["Coruña, A"]] = ("Coruña, A", shared)
        links[by_label["Girona/Gerona"]] = ("Girona/Gerona", shared)
        cadiz = by_label["Cádiz"]
        links[cadiz] = (links[cadiz][0], links[cadiz][1] + "#")
        leon = by_label["León"]
        links[leon] = (links[leon][0], links[leon][1] + " ")
        lleida = by_label["Lleida/Lerida"]
        links[lleida] = (links[lleida][0], links[lleida][1] + "#A")
    return links


def fixture_html(
    kind: str,
    links: list[tuple[str, str]] | None = None,
    *,
    map_links: list[tuple[str, str]] | None = None,
    h1: str | None = None,
) -> str:
    spec = pag.DIRECTORIES[kind]
    links = links if links is not None else valid_links(kind)
    h1 = spec.expected_h1 if h1 is None else h1
    if kind in {"diputaciones", "municipal_queues"}:
        map_links = map_links if map_links is not None else links
        footer = "".join(
            f'<p><a href="{html.escape(href, quote=True)}"><span>{html.escape(label)}</span></a></p>'
            for label, href in links
        )
        areas = "".join(
            f'<area alt="{html.escape(label, quote=True)}" href="{html.escape(href, quote=True)}">'
            for label, href in map_links
        )
        canonical = (
            f'<div class="mapas_provincia extra"><map id="MapProvincias" name="provincias">{areas}</map></div>'
            f'<div class="chrome piemapa extra" id="pie_provincia">{footer}</div>'
        )
    else:
        rows = "".join(
            f'<li><a class="other enlacenegrita" href="{html.escape(href, quote=True)}"><span>{html.escape(label)}</span></a></li>'
            for label, href in links
        )
        canonical = f"""
            <div class="title_mb_30 extra"><ul class="lista1">{rows}</ul></div>
            <div class="textojustificado"><ul class="lista1"></ul></div>
        """
    return f"""
        <html><body>
          <h1><span>{html.escape(h1)}</span></h1>
          <a href="https://outside.example.es/">{html.escape(spec.labels[0])}</a>
          <div class="unrelated"><ul class="lista1">
            <li><a class="enlacenegrita" href="https://wrong.example.es/">{html.escape(spec.labels[0])}</a></li>
          </ul></div>
          {canonical}
        </body></html>
    """


class RecordingFetcher:
    def __init__(
        self,
        kind: str,
        body: str,
        *,
        final_url: str | None = None,
        redirect_chain: tuple[str, ...] = (),
        request_count: int = 1,
    ) -> None:
        self.kind = kind
        self.body = body.encode("utf-8")
        self.final_url = final_url
        self.redirect_chain = redirect_chain
        self.request_count = request_count
        self.calls: list[tuple[str, dict[str, object]]] = []

    def fetch(self, url: str, **kwargs):
        self.calls.append((url, kwargs))
        source_url = pag.DIRECTORIES[self.kind].source_url
        return pag.inventory.FetchResult(
            requested_url=source_url,
            final_url=self.final_url or source_url,
            redirect_chain=self.redirect_chain,
            status=200,
            content_type="text/html",
            body=self.body,
            request_count=self.request_count,
        )


class ParsingTest(unittest.TestCase):
    def test_reviewed_label_order_and_https_sets_have_fixed_regression_digests(self) -> None:
        expected = {
            "diputaciones": (
                41,
                "f65979f675dbf1250988fb1742793ffdcc284a72e8322748fc913bfc4815fc43",
                "074a88e301446c324b3b848bad59f11c93758ead67e75b5f09a5915a46341adb",
            ),
            "insular": (
                11,
                "8c90c557367fd189a1fec1d711ea1a245b63ae092ae80ec33e9a9e4c8d8ac412",
                "9afee6b187c21ca9cd66677b7c41f9b6ec096b74ddbae31f9a9486bb2a2aa132",
            ),
            "municipal_queues": (
                52,
                "5d458edc68df90f60a5bae365119356d8adaa3b70c0f88d8e1b58322707a0eb3",
                "6949c71aa7aa19a9ea7547af34a91d9e380339c34d65e41da970d264eb533356",
            ),
        }
        for kind, (count, label_digest, https_digest) in expected.items():
            spec = pag.DIRECTORIES[kind]
            with self.subTest(kind=kind):
                self.assertEqual(len(spec.labels), count)
                self.assertEqual(
                    hashlib.sha256("\0".join(spec.labels).encode()).hexdigest(),
                    label_digest,
                )
                self.assertEqual(
                    hashlib.sha256("\0".join(sorted(spec.https_labels)).encode()).hexdigest(),
                    https_digest,
                )

    def test_closed_labels_h1_and_dom_scopes(self) -> None:
        expected_counts = {"diputaciones": 41, "insular": 11, "municipal_queues": 52}
        for kind, expected_count in expected_counts.items():
            with self.subTest(kind=kind):
                anchors = pag.parse_directory_html(fixture_html(kind), kind)
                self.assertEqual(tuple(anchor.source_label for anchor in anchors), pag.DIRECTORIES[kind].labels)
                self.assertEqual(len(anchors), expected_count)
                self.assertNotIn("https://outside.example.es/", {anchor.href for anchor in anchors})
                self.assertNotIn("https://wrong.example.es/", {anchor.href for anchor in anchors})

    def test_h1_label_order_and_container_drift_fail_closed_without_echo(self) -> None:
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.parse_directory_html(fixture_html("diputaciones", h1="Wrong"), "diputaciones")
        links = valid_links("insular")
        links[0] = ("PRIVATE_UNKNOWN_LABEL", links[0][1])
        with self.assertRaises(pag.PagLocalDirectoryError) as captured:
            pag.parse_directory_html(fixture_html("insular", links), "insular")
        self.assertNotIn("PRIVATE_UNKNOWN_LABEL", str(captured.exception))
        duplicate_container = fixture_html("insular").replace(
            "</body>", '<div class="title_mb_30"><ul class="lista1"></ul></div></body>',
        )
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.parse_directory_html(duplicate_container, "insular")

    def test_map_and_footer_must_match_decoded_hrefs_before_normalization(self) -> None:
        for kind in ("diputaciones", "municipal_queues"):
            links = valid_links(kind)
            changed_map = list(links)
            label, href = changed_map[0]
            changed_map[0] = (label, href + " ")
            with self.subTest(kind=kind), self.assertRaises(pag.PagLocalDirectoryError):
                pag.parse_directory_html(fixture_html(kind, links, map_links=changed_map), kind)

    def test_map_must_remain_inside_reviewed_mapas_provincia_container(self) -> None:
        html_body = fixture_html("diputaciones").replace(
            '<div class="mapas_provincia extra">',
            '<div class="unreviewed-map-container">',
            1,
        )
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.parse_directory_html(html_body, "diputaciones")

    def test_only_closed_cli_kinds_are_accepted(self) -> None:
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            pag.build_parser().parse_args(
                ["--kind", "other", "--fixture-html", "x", "--snapshot-date", "2026-07-16", "--output", "y"]
            )


class EnumerationTest(unittest.TestCase):
    def enumerate(self, kind: str, links: list[tuple[str, str]] | None = None):
        fetcher = RecordingFetcher(kind, fixture_html(kind, links))
        records = pag.enumerate_directory(fetcher, kind, "2026-07-16")
        self.assertEqual(
            fetcher.calls,
            [(pag.DIRECTORIES[kind].source_url, {"allowed_query_keys": (), "allowed_redirect_origins": ()})],
        )
        return records

    def test_one_source_get_no_target_fetch_and_closed_scheme_sets(self) -> None:
        expected = {
            "diputaciones": (7, 34),
            "insular": (3, 8),
            "municipal_queues": (32, 20),
        }
        for kind, counts in expected.items():
            with self.subTest(kind=kind):
                records = self.enumerate(kind)
                self.assertEqual(sum(row["target"]["scheme"] == "https" for row in records), counts[0])
                self.assertEqual(sum(row["target"]["scheme"] == "http" for row in records), counts[1])
                self.assertTrue(all(row["target_fetch_performed"] is False for row in records))
                self.assertTrue(all(row["compatibility_status"] == "BROWSE_ONLY" for row in records))
                self.assertTrue(all(row["discovery_state"] == "CANDIDATE" for row in records))
                actual_https = {row["source_label"] for row in records if row["target"]["scheme"] == "https"}
                self.assertEqual(actual_https, pag.DIRECTORIES[kind].https_labels)

    def test_http_references_are_non_executable(self) -> None:
        for kind in pag.DIRECTORIES:
            for row in self.enumerate(kind):
                if row["target"]["scheme"] != "http" or row["target_status"].startswith("SOURCE_"):
                    continue
                self.assertEqual(row["target"]["kind"], "HTTP_REFERENCE_COMPONENTS")
                self.assertEqual(row["target_status"], "HTTPS_RESOLUTION_REQUIRED")
                self.assertFalse(row["candidate_seed_eligible"])
                self.assertNotIn("url", row["target"])
                self.assertNotIn("origin", row["target"])

    def test_insular_exact_public_queries_and_https_candidate(self) -> None:
        records = self.enumerate("insular")
        queries = {row["source_label"]: row["target"]["public_query_pairs"] for row in records if row["target"]["public_query_pairs"]}
        self.assertEqual(queries, {key: [list(pair) for pair in value] for key, value in pag.INSULAR_QUERIES.items()})
        tenerife = next(row for row in records if row["source_label"] == "Cabildo Insular de Tenerife")
        self.assertTrue(tenerife["candidate_seed_eligible"])
        self.assertIn("?lang=es", tenerife["target"]["url"])

    def test_municipal_required_quarantines_and_secondary_queue_kind(self) -> None:
        records = {row["source_label"]: row for row in self.enumerate("municipal_queues")}
        expected = {
            "Girona/Gerona": "SOURCE_CONFLICT",
            "León": "SOURCE_URL_WHITESPACE",
            "Cádiz": "SOURCE_FRAGMENT",
            "Lleida/Lerida": "SOURCE_FRAGMENT",
            "Coruña, A": "SOURCE_QUERY_MARKER",
            "Murcia": "SOURCE_QUERY_UNSAFE",
        }
        for label, status in expected.items():
            row = records[label]
            self.assertEqual(row["target_status"], status)
            self.assertFalse(row["candidate_seed_eligible"])
            self.assertEqual(row["target"]["kind"], "QUARANTINED_REFERENCE_COMPONENTS")
            self.assertNotIn("url", row["target"])
            self.assertNotIn("origin", row["target"])
        self.assertTrue(all(row["record_kind"] == "MUNICIPAL_DIRECTORY_QUEUE" for row in records.values()))
        self.assertTrue(all(row["administrative_level"] == "MUNICIPAL_QUEUE" for row in records.values()))

    def test_municipal_leon_trailing_space_is_an_exact_closed_baseline(self) -> None:
        for suffix in ("", "  "):
            links = valid_links("municipal_queues")
            index = pag.MUNICIPAL_LABELS.index("León")
            label, href = links[index]
            links[index] = (label, href.rstrip(" ") + suffix)
            with self.subTest(suffix=repr(suffix)), self.assertRaises(
                pag.PagLocalDirectoryError
            ):
                pag.enumerate_directory(
                    RecordingFetcher(
                        "municipal_queues",
                        fixture_html("municipal_queues", links),
                    ),
                    "municipal_queues",
                    "2026-07-16",
                )

    def test_municipal_reviewed_query_pairs_are_exact_and_never_in_errors(self) -> None:
        records = self.enumerate("municipal_queues")
        queries = {row["source_label"]: row["target"]["public_query_pairs"] for row in records if row["target"]["public_query_pairs"]}
        self.assertEqual(queries, {key: [list(pair) for pair in value] for key, value in pag.MUNICIPAL_QUERIES.items()})
        self.assertTrue(next(row for row in records if row["source_label"] == "Córdoba")["candidate_seed_eligible"])

        marker = "PRIVATE_QUERY_VALUE_DO_NOT_ECHO"
        links = valid_links("municipal_queues")
        index = pag.MUNICIPAL_LABELS.index("Asturias")
        links[index] = ("Asturias", f"http://target.example.es/?page_id={marker}")
        with self.assertRaises(pag.PagLocalDirectoryError) as captured:
            pag.enumerate_directory(
                RecordingFetcher("municipal_queues", fixture_html("municipal_queues", links)),
                "municipal_queues",
                "2026-07-16",
            )
        self.assertNotIn(marker, str(captured.exception))

    def test_empty_query_markers_are_rejected_outside_closed_exceptions(self) -> None:
        for kind in ("diputaciones", "insular"):
            links = valid_links(kind)
            index = next(
                index
                for index, (label, _) in enumerate(links)
                if not pag.DIRECTORIES[kind].reviewed_queries.get(label)
            )
            label, href = links[index]
            links[index] = (label, href + "?")
            with self.subTest(kind=kind), self.assertRaises(
                pag.PagLocalDirectoryError
            ):
                pag.enumerate_directory(
                    RecordingFetcher(kind, fixture_html(kind, links)),
                    kind,
                    "2026-07-16",
                )

    def test_scheme_map_conflict_and_anomaly_drift_fail_closed(self) -> None:
        links = valid_links("diputaciones")
        label, href = links[0]
        links[0] = (label, href.replace("http://", "https://", 1))
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(RecordingFetcher("diputaciones", fixture_html("diputaciones", links)), "diputaciones", "2026-07-16")

        municipal = valid_links("municipal_queues")
        girona = pag.MUNICIPAL_LABELS.index("Girona/Gerona")
        municipal[girona] = ("Girona/Gerona", "https://different.example.es/?")
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(RecordingFetcher("municipal_queues", fixture_html("municipal_queues", municipal)), "municipal_queues", "2026-07-16")

    def test_only_the_reviewed_municipal_href_duplicate_is_allowed(self) -> None:
        diputaciones = valid_links("diputaciones")
        duplicate_index = 2
        duplicate_label, _ = diputaciones[duplicate_index]
        diputaciones[duplicate_index] = (duplicate_label, diputaciones[0][1])
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(
                RecordingFetcher(
                    "diputaciones",
                    fixture_html("diputaciones", diputaciones),
                ),
                "diputaciones",
                "2026-07-16",
            )

        municipal = valid_links("municipal_queues")
        by_label = {label: index for index, (label, _) in enumerate(municipal)}
        extra_label = "Araba/Álava"
        municipal[by_label[extra_label]] = (
            extra_label,
            municipal[by_label["Alacant/Alicante"]][1],
        )
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(
                RecordingFetcher(
                    "municipal_queues",
                    fixture_html("municipal_queues", municipal),
                ),
                "municipal_queues",
                "2026-07-16",
            )

    def test_source_ids_distinguish_diputaciones_and_insular_catalogs(self) -> None:
        self.assertEqual(pag.DIRECTORIES["diputaciones"].source_id, "D06")
        self.assertEqual(pag.DIRECTORIES["insular"].source_id, "D12")
        self.assertEqual(pag.DIRECTORIES["municipal_queues"].source_id, "D05")

    def test_malformed_authority_fails_with_generic_sanitized_error(self) -> None:
        for malformed in ("https://[bad/", "https://exa／mple.example.es/"):
            links = valid_links("diputaciones")
            index = pag.DIPUTACIONES_LABELS.index("Araba/Álava")
            links[index] = ("Araba/Álava", malformed)
            with self.subTest(malformed=malformed), self.assertRaises(pag.PagLocalDirectoryError) as captured:
                pag.enumerate_directory(RecordingFetcher("diputaciones", fixture_html("diputaciones", links)), "diputaciones", "2026-07-16")
            self.assertIn("target authority is invalid", str(captured.exception))
            self.assertNotIn(malformed, str(captured.exception))

    def test_redirect_or_multiple_request_result_fails_closed(self) -> None:
        source = pag.DIRECTORIES["insular"].source_url
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(RecordingFetcher("insular", fixture_html("insular"), redirect_chain=(source,)), "insular", "2026-07-16")
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.enumerate_directory(RecordingFetcher("insular", fixture_html("insular"), request_count=2), "insular", "2026-07-16")


class FixtureOutputAndCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_fixture_rejects_symlink_hardlink_metadata_and_oversize(self) -> None:
        source = self.root / "source.html"
        source.write_text(fixture_html("diputaciones"), encoding="utf-8")
        symlink = self.root / "link.html"
        symlink.symlink_to(source)
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.read_fixture_secure(symlink)
        metadata = source.stat()
        multi = types.SimpleNamespace(st_mode=metadata.st_mode, st_nlink=2, st_size=metadata.st_size)
        with mock.patch.object(pag.os, "fstat", return_value=multi), self.assertRaises(pag.PagLocalDirectoryError):
            pag.read_fixture_secure(source)
        with self.assertRaises(pag.PagLocalDirectoryError):
            pag.read_fixture_secure(source, max_bytes=32)

    def test_output_is_deterministic_atomic_jsonl_and_mode_0600(self) -> None:
        records = pag.enumerate_directory(
            RecordingFetcher("diputaciones", fixture_html("diputaciones")),
            "diputaciones",
            "2026-07-16",
        )
        output = self.root / "records.jsonl"
        pag.write_jsonl_atomic(output, records)
        first = output.read_bytes()
        output.write_text("old", encoding="utf-8")
        os.chmod(output, 0o644)
        pag.write_jsonl_atomic(output, records)
        self.assertEqual(output.read_bytes(), first)
        self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
        self.assertEqual([json.loads(line) for line in first.decode().splitlines()], records)

    def test_cli_redacts_untrusted_query_and_does_not_create_output(self) -> None:
        marker = "PRIVATE_QUERY_VALUE_DO_NOT_ECHO"
        links = valid_links("insular")
        index = pag.INSULAR_LABELS.index("Consell Insular de Formentera")
        links[index] = (links[index][0], f"http://target.example.es/?lang={marker}")
        fixture = self.root / "source.html"
        fixture.write_text(fixture_html("insular", links), encoding="utf-8")
        output = self.root / "output.jsonl"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            result = pag.main(["--kind", "insular", "--fixture-html", str(fixture), "--snapshot-date", "2026-07-16", "--output", str(output)])
        self.assertEqual(result, 2)
        self.assertNotIn(marker, stderr.getvalue())
        self.assertFalse(output.exists())

    def test_live_cli_constructs_zero_redirect_fetcher(self) -> None:
        output = self.root / "output.jsonl"
        recording = RecordingFetcher("diputaciones", fixture_html("diputaciones"))
        captured: list[object] = []

        def fake_live(limits):
            captured.append(limits)
            return recording

        with mock.patch.object(pag.inventory, "LiveFetcher", side_effect=fake_live):
            result = pag.main(["--kind", "diputaciones", "--live", "--snapshot-date", "2026-07-16", "--output", str(output)])
        self.assertEqual(result, 0)
        self.assertEqual(len(captured), 1)
        self.assertEqual(captured[0].max_redirects, 0)
        self.assertEqual(captured[0].max_assets, 0)
        self.assertEqual(len(recording.calls), 1)


if __name__ == "__main__":
    unittest.main()
