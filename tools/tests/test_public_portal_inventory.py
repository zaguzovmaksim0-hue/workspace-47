from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = Path(__file__).parents[1] / "public_portal_inventory.py"
SPEC = importlib.util.spec_from_file_location("public_portal_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = inventory
SPEC.loader.exec_module(inventory)


class RecordingFetcher:
    def __init__(self, root_url: str, html: str) -> None:
        self.root_url = root_url
        self.html = html.encode("utf-8")
        self.calls: list[str] = []

    def fetch(
        self,
        url: str,
        *,
        same_origin: str | None = None,
        allowed_query_keys=(),
        allowed_redirect_origins=(),
    ):
        self.calls.append(url)
        if url != self.root_url:
            raise inventory.InventoryError("synthetic missing asset")
        return inventory.FetchResult(
            requested_url=url,
            final_url=url,
            redirect_chain=(),
            status=200,
            content_type="text/html",
            body=self.html,
        )


class UrlPolicyTest(unittest.TestCase):
    def test_canonicalizes_origin_and_drops_query_by_default(self) -> None:
        self.assertEqual(
            inventory.sanitize_url("https://Sede.Example.es:443/path?lang=es"),
            "https://sede.example.es/path",
        )
        self.assertEqual(
            inventory.exact_origin("https://sede.example.es:8443/path"),
            "https://sede.example.es:8443",
        )

    def test_retains_only_explicit_safe_public_query_keys(self) -> None:
        self.assertEqual(
            inventory.sanitize_url(
                "https://catalog.example.es/list?idProvincia=03&ignored=value",
                {"idProvincia"},
            ),
            "https://catalog.example.es/list?idProvincia=03",
        )
        with self.assertRaises(inventory.InventoryError):
            inventory.sanitize_url("https://catalog.example.es/?token=x", {"token"})

    def test_rejects_sensitive_equivalent_and_duplicate_query_keys(self) -> None:
        sensitive = (
            "accessToken",
            "sessionKey",
            "SAMLRequest",
            "RelayState",
            "idSesion",
            "clave",
            "authorizationCode",
            "certificateId",
            "signatureValue",
            "ticketId",
            "stateParam",
            "nonceValue",
            "challengeId",
            "sigValue",
            "pkcs12File",
            "p12Data",
        )
        for key in sensitive:
            with self.subTest(key=key), self.assertRaises(inventory.InventoryError):
                inventory.sanitize_url(f"https://catalog.example.es/?{key}=x", {key})
        with self.assertRaises(inventory.InventoryError):
            inventory.sanitize_url(
                "https://catalog.example.es/?idProvincia=03&idProvincia=04",
                {"idProvincia"},
            )

    def test_redirect_query_requires_an_explicit_public_key(self) -> None:
        with self.assertRaises(inventory.InventoryError):
            inventory.sanitize_url(
                "https://portal.example.es/login?sessionKey=secret",
                (),
                reject_unlisted_query=True,
            )

    def test_rejects_non_public_or_ambiguous_urls(self) -> None:
        invalid = (
            "http://sede.example.es/",
            "https://user:secret@sede.example.es/",
            "https://127.0.0.1/",
            "https://localhost/",
            "https://sede.example.es./",
            "https://*.example.es/",
            "https://bad_host.example.es/",
            "https://sede.example.es/a//b",
            "https://sede.example.es/a%2fb",
            "https://sede.example.es/a%ZZb",
            "https://sede.example.es/a%0ab",
            "https://sede.example.es/a%3Bjsessionid=value",
            "https://sede.example.es/a/%252e%252e/b",
            "https://sede.example.es/a/../b",
            "https://sede.example.es/a;jsessionid=value",
            "https://sede.example.es/#fragment",
        )
        for url in invalid:
            with self.subTest(url=url), self.assertRaises(inventory.InventoryError):
                inventory.sanitize_url(url)


class FingerprintTest(unittest.TestCase):
    def test_correlates_static_family_without_promoting_contract(self) -> None:
        text = """
            AutoScript.sign(data, 'SHA512withRSA', 'XAdES Detached', properties);
            const endpoint = 'https://firma.example.es/TriPhaseSignatureService';
        """
        fingerprints = inventory.fingerprint_text(text)
        self.assertIn("CLIENT_AUTOSCRIPT", fingerprints)
        self.assertIn("OP_SIGN", fingerprints)
        self.assertIn("FORMAT_XADES", fingerprints)
        self.assertEqual(inventory.evidence_confidence(fingerprints), "LIKELY_FAMILY")
        self.assertEqual(
            inventory.endpoint_candidates(text, "https://portal.example.es/start"),
            ("https://firma.example.es/TriPhaseSignatureService",),
        )

    def test_plain_cms_pkcs7_is_a_local_signature_format_without_becoming_cades(self) -> None:
        fingerprints = inventory.fingerprint_text(
            "AutoScript.sign(data, 'SHA256withRSA', 'CMS/PKCS#7', 'mode=implicit')"
        )
        self.assertIn("CLIENT_AUTOSCRIPT", fingerprints)
        self.assertIn("OP_SIGN", fingerprints)
        self.assertIn("FORMAT_CMS", fingerprints)
        self.assertNotIn("FORMAT_CADES", fingerprints)
        self.assertIn("LOCAL_SIGNATURE_FORMAT", inventory.protocol_families(fingerprints))
        self.assertEqual("LIKELY_FAMILY", inventory.evidence_confidence(fingerprints))

    def test_intent_requires_an_autofirma_marker(self) -> None:
        self.assertNotIn(
            "AUTOFIRMA_INTENT_URI",
            inventory.fingerprint_text("location.href = 'intent://generic';"),
        )
        self.assertIn(
            "AUTOFIRMA_INTENT_URI",
            inventory.fingerprint_text("location.href = 'intent://x#Intent;package=es.gob.afirma';"),
        )

    def test_generic_sign_method_is_not_an_autofirma_operation(self) -> None:
        self.assertNotIn("OP_SIGN", inventory.fingerprint_text("window.crypto.sign(data);"))

    def test_extracts_relative_servlet_candidates_without_fetching_them(self) -> None:
        text = "MiniApplet.setServlets('/firma/StorageService', '../RetrieveService');"
        self.assertEqual(
            inventory.endpoint_candidates(text, "https://portal.example.es/app/start"),
            (
                "https://portal.example.es/RetrieveService",
                "https://portal.example.es/firma/StorageService",
            ),
        )


class DeadlineTest(unittest.TestCase):
    def test_body_read_uses_one_monotonic_deadline_across_chunks(self) -> None:
        class FakeSocket:
            def __init__(self) -> None:
                self.timeouts: list[float] = []

            def settimeout(self, value: float) -> None:
                self.timeouts.append(value)

        class TrickleResponse:
            def read1(self, size: int) -> bytes:
                return b"x"

        connection = type("Connection", (), {"sock": FakeSocket(), "close": lambda self: None})()
        with mock.patch.object(
            inventory.time,
            "monotonic",
            side_effect=[0.0, 0.2, 0.4, 0.6, 1.1],
        ):
            with self.assertRaises(inventory.InventoryError):
                inventory._read_bounded_body(
                    TrickleResponse(),
                    connection,
                    max_body_bytes=1024,
                    deadline=1.0,
                )
        self.assertEqual(len(connection.sock.timeouts), 2)

    def test_expired_post_start_deadline_still_runs_timeout_cleanup(self) -> None:
        import threading

        cleanup_called = threading.Event()
        release_worker = threading.Event()

        def blocking_operation() -> bytes:
            release_worker.wait(1.0)
            return b""

        def cleanup() -> None:
            cleanup_called.set()
            release_worker.set()

        try:
            with mock.patch.object(inventory.time, "monotonic", return_value=2.0):
                with self.assertRaises(inventory.InventoryError):
                    inventory._run_with_deadline(
                        blocking_operation,
                        deadline=1.0,
                        on_timeout=cleanup,
                    )
            self.assertTrue(cleanup_called.is_set())
        finally:
            release_worker.set()

    def test_one_blocking_read_is_cancelled_at_the_wall_clock_deadline(self) -> None:
        import threading
        import time

        released = threading.Event()

        class FakeSocket:
            def settimeout(self, value: float) -> None:
                pass

        class BlockingResponse:
            def read1(self, size: int) -> bytes:
                released.wait(1.0)
                return b""

        class Connection:
            sock = FakeSocket()

            def close(self) -> None:
                released.set()

        started = time.monotonic()
        with self.assertRaises(inventory.InventoryError):
            inventory._read_bounded_body(
                BlockingResponse(),
                Connection(),
                max_body_bytes=1024,
                deadline=started + 0.02,
            )
        elapsed = time.monotonic() - started
        self.assertTrue(released.is_set())
        self.assertLess(elapsed, 0.2)


class OfflineScanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _write_json(self, name: str, value: object) -> Path:
        path = self.root / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def _seed_input(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "snapshot_id": "test-snapshot",
            "snapshot_date": "2026-07-16",
            "seeds": [
                {
                    "seed_id": "ES-TEST-0001",
                    "institution_name": "Institución de prueba",
                    "administrative_level": "ESTATAL",
                    "autonomous_community": "",
                    "province_or_municipality": "",
                    "source_url": "https://catalog.example.es/official",
                    "entry_urls": ["https://portal.example.es/start"],
                    "public_query_keys": [],
                    "allowed_redirect_origins": [],
                }
            ],
        }

    def test_scans_only_same_origin_scripts_and_outputs_browse_only(self) -> None:
        (self.root / "entry.html").write_text(
            """
            <script>const ignored = 'intent://generic';</script>
            <script src="/assets/app.js?v=public"></script>
            <script src="https://cdn.other.es/foreign.js"></script>
            """,
            encoding="utf-8",
        )
        (self.root / "app.js").write_text(
            """
            MiniApplet.sign(data, 'SHA256withRSA', 'CAdES', props);
            const pre = 'https://firma.portal.example.es/TriPhaseSignatureService?session=secret';
            const launch = 'afirma://sign';
            """,
            encoding="utf-8",
        )
        fixtures = self._write_json(
            "fixtures.json",
            {
                "schema_version": 1,
                "responses": [
                    {
                        "url": "https://portal.example.es/start",
                        "public_query_keys": [],
                        "status": 200,
                        "content_type": "text/html",
                        "body_file": "entry.html",
                        "final_url": "https://portal.example.es/start",
                        "redirect_chain": [],
                        "etag": None,
                        "last_modified": None,
                    },
                    {
                        "url": "https://portal.example.es/assets/app.js",
                        "public_query_keys": [],
                        "status": 200,
                        "content_type": "application/javascript",
                        "body_file": "app.js",
                        "final_url": "https://portal.example.es/assets/app.js",
                        "redirect_chain": [],
                        "etag": None,
                        "last_modified": None,
                    },
                ],
            },
        )
        input_path = self._write_json("input.json", self._seed_input())
        output_path = self.root / "output.jsonl"

        result = inventory.main(
            [
                "--input",
                str(input_path),
                "--output",
                str(output_path),
                "--offline-fixtures",
                str(fixtures),
            ]
        )

        self.assertEqual(result, 0)
        record = json.loads(output_path.read_text(encoding="utf-8"))
        self.assertEqual(record["compatibility_status"], "BROWSE_ONLY")
        self.assertEqual(record["static_evidence_confidence"], "LIKELY_FAMILY")
        self.assertEqual(len(record["assets"]), 1)
        self.assertEqual(record["assets"][0]["url"], "https://portal.example.es/assets/app.js")
        self.assertNotIn("cdn.other.es", json.dumps(record))
        self.assertEqual(
            record["endpoint_candidates"],
            ["https://firma.portal.example.es/TriPhaseSignatureService"],
        )
        self.assertNotIn("secret", json.dumps(record))

    def test_output_is_deterministic_and_private_at_creation(self) -> None:
        (self.root / "entry.html").write_text("<html></html>", encoding="utf-8")
        fixtures = self._write_json(
            "fixtures.json",
            {
                "schema_version": 1,
                "responses": [
                    {
                        "url": "https://portal.example.es/start",
                        "public_query_keys": [],
                        "status": 200,
                        "content_type": "text/html",
                        "body_file": "entry.html",
                        "final_url": "https://portal.example.es/start",
                        "redirect_chain": [],
                        "etag": None,
                        "last_modified": None,
                    }
                ],
            },
        )
        parsed = inventory.load_inventory(self._write_json("input.json", self._seed_input()))
        fetcher = inventory.OfflineFixtureFetcher(fixtures, inventory.ScanLimits())
        records = inventory.scan_inventory(parsed, fetcher, inventory.ScanLimits())
        first = self.root / "first.jsonl"
        second = self.root / "second.jsonl"
        inventory.write_jsonl_atomic(first, records)
        inventory.write_jsonl_atomic(second, records)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        self.assertEqual(first.stat().st_mode & 0o777, 0o600)

    def test_rejects_unknown_input_keys_and_duplicate_seed_ids(self) -> None:
        raw = self._seed_input()
        raw["unexpected"] = True
        with self.assertRaises(inventory.InventoryError):
            inventory.load_inventory(self._write_json("bad-root.json", raw))

        duplicate = self._seed_input()
        duplicate["seeds"] = [duplicate["seeds"][0], duplicate["seeds"][0]]
        with self.assertRaises(inventory.InventoryError):
            inventory.load_inventory(self._write_json("duplicate.json", duplicate))

    def test_missing_fixture_is_a_sanitized_candidate_failure(self) -> None:
        (self.root / "unused.html").write_text("unused", encoding="utf-8")
        fixtures = self._write_json(
            "fixtures.json",
            {"schema_version": 1, "responses": []},
        )
        parsed = inventory.load_inventory(self._write_json("input.json", self._seed_input()))
        fetcher = inventory.OfflineFixtureFetcher(fixtures, inventory.ScanLimits())
        records = inventory.scan_inventory(parsed, fetcher, inventory.ScanLimits())
        self.assertEqual(records[0]["fetch_result"], "INACCESSIBLE_CANDIDATE")
        self.assertEqual(records[0]["error_class"], "InventoryError")
        self.assertNotIn("offline fixture is missing", json.dumps(records[0]))

    def test_failed_assets_consume_budget_and_depth_zero_fetches_none(self) -> None:
        parsed = inventory.load_inventory(self._write_json("input.json", self._seed_input()))
        seed = parsed.seeds[0]
        scripts = "".join(f'<script src="/asset-{index}.js"></script>' for index in range(10))

        bounded = RecordingFetcher(seed.entry_urls[0], scripts)
        record = inventory.scan_entry(
            seed,
            seed.entry_urls[0],
            bounded,
            inventory.ScanLimits(max_assets=2, max_script_depth=1),
        )
        self.assertEqual(len(bounded.calls), 3)
        self.assertEqual(record["asset_attempt_count"], 2)

        root_only = RecordingFetcher(seed.entry_urls[0], scripts)
        record = inventory.scan_entry(
            seed,
            seed.entry_urls[0],
            root_only,
            inventory.ScanLimits(max_assets=2, max_script_depth=0),
        )
        self.assertEqual(root_only.calls, [seed.entry_urls[0]])
        self.assertEqual(record["asset_attempt_count"], 0)

    def test_allowlisted_query_fixtures_do_not_collide(self) -> None:
        (self.root / "province-03.html").write_text("province 03", encoding="utf-8")
        (self.root / "province-04.html").write_text("province 04", encoding="utf-8")
        responses = []
        for code in ("03", "04"):
            responses.append(
                {
                    "url": f"https://catalog.example.es/list?idProvincia={code}",
                    "public_query_keys": ["idProvincia"],
                    "status": 200,
                    "content_type": "text/html",
                    "body_file": f"province-{code}.html",
                    "final_url": "https://catalog.example.es/list",
                    "redirect_chain": [],
                    "etag": None,
                    "last_modified": None,
                }
            )
        fixture_path = self._write_json(
            "query-fixtures.json",
            {"schema_version": 1, "responses": responses},
        )
        fetcher = inventory.OfflineFixtureFetcher(fixture_path, inventory.ScanLimits())
        result_03 = fetcher.fetch(
            "https://catalog.example.es/list?idProvincia=03",
            allowed_query_keys={"idProvincia"},
        )
        result_04 = fetcher.fetch(
            "https://catalog.example.es/list?idProvincia=04",
            allowed_query_keys={"idProvincia"},
        )
        self.assertNotEqual(result_03.body, result_04.body)

    def test_cross_origin_redirect_requires_explicit_seed_origin(self) -> None:
        (self.root / "landing.html").write_text("<html></html>", encoding="utf-8")
        fixture_path = self._write_json(
            "redirect-fixtures.json",
            {
                "schema_version": 1,
                "responses": [
                    {
                        "url": "https://portal.example.es/start",
                        "public_query_keys": [],
                        "status": 200,
                        "content_type": "text/html",
                        "body_file": "landing.html",
                        "final_url": "https://landing.example.es/home",
                        "redirect_chain": ["https://landing.example.es/home"],
                        "etag": None,
                        "last_modified": None,
                    }
                ],
            },
        )
        fetcher = inventory.OfflineFixtureFetcher(fixture_path, inventory.ScanLimits())
        parsed = inventory.load_inventory(self._write_json("input.json", self._seed_input()))
        blocked = inventory.scan_inventory(parsed, fetcher, inventory.ScanLimits())
        self.assertEqual(blocked[0]["fetch_result"], "INACCESSIBLE_CANDIDATE")

        allowed_input = self._seed_input()
        allowed_input["seeds"][0]["allowed_redirect_origins"] = [
            "https://landing.example.es"
        ]
        parsed_allowed = inventory.load_inventory(
            self._write_json("input-allowed.json", allowed_input)
        )
        accepted = inventory.scan_inventory(parsed_allowed, fetcher, inventory.ScanLimits())
        self.assertEqual(accepted[0]["final_url"], "https://landing.example.es/home")

    def test_fixture_redirect_chain_obeys_configured_limit(self) -> None:
        (self.root / "landing.html").write_text("<html></html>", encoding="utf-8")
        fixture_path = self._write_json(
            "too-many-redirects.json",
            {
                "schema_version": 1,
                "responses": [
                    {
                        "url": "https://portal.example.es/start",
                        "public_query_keys": [],
                        "status": 200,
                        "content_type": "text/html",
                        "body_file": "landing.html",
                        "final_url": "https://portal.example.es/final",
                        "redirect_chain": [
                            "https://portal.example.es/intermediate",
                            "https://portal.example.es/final",
                        ],
                        "etag": None,
                        "last_modified": None,
                    }
                ],
            },
        )
        with self.assertRaises(inventory.InventoryError):
            inventory.OfflineFixtureFetcher(
                fixture_path,
                inventory.ScanLimits(max_redirects=1),
            )


if __name__ == "__main__":
    unittest.main()
