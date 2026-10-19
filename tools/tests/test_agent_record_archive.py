"""Preserve bounded historical agent records without treating them as live tasks."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "docs/archive/agent-records"
EXPECTED = {
    "docs/agent-task-watch-log.md": "2026-07-22-task-watch.md",
    "docs/handoffs/F03_NEXT_CHAT_2026-08-01.md": "2026-08-01-aeat-f03.md",
    "docs/handoffs/NEXT_CHAT_HANDOFF.md": "2026-08-12-portal-cycle.md",
}

class AgentRecordArchiveTest(unittest.TestCase):
    def manifest(self):
        return json.loads((ARCHIVE / "manifest.json").read_text())

    def test_manifest_has_exact_bounded_sources(self):
        m = self.manifest()
        self.assertEqual(set(m), {"schema", "sourceCommit", "records"})
        self.assertEqual(m["schema"], 1)
        self.assertEqual(m["sourceCommit"], "8827cbd7f236cc89714165b97c8d6e8f457afe12")
        self.assertEqual({r["source"]: r["archive"] for r in m["records"]}, EXPECTED)
        self.assertEqual(len(m["records"]), 3)

    def test_each_archived_byte_sequence_matches_pinned_digest_and_length(self):
        for r in self.manifest()["records"]:
            with self.subTest(source=r["source"]):
                self.assertEqual(set(r), {"source", "archive", "sha256", "bytes"})
                b = (ARCHIVE / r["archive"]).read_bytes()
                self.assertEqual(len(b), r["bytes"])
                self.assertEqual(hashlib.sha256(b).hexdigest(), r["sha256"])

    def test_original_routes_are_explicit_non_operational_landings(self):
        for source, target in EXPECTED.items():
            with self.subTest(source=source):
                p = ROOT / source
                s = p.read_text()
                self.assertIn("ARCHIVADO", s)
                self.assertIn("No continuar", s)
                links = re.findall(r"\]\(([^)]+)\)", s)
                resolved = {(p.parent / link).resolve() for link in links if not link.startswith("https:")}
                self.assertIn((ARCHIVE / target).resolve(), resolved)
                self.assertTrue(all(x.is_file() for x in resolved))
                self.assertLess(len(s), 1600)

    def test_archive_index_links_every_record_and_current_guidance(self):
        p = ARCHIVE / "README.md"
        s = p.read_text()
        for name in EXPECTED.values():
            self.assertIn(f"]({name})", s)
        self.assertIn("CONTEXT.md", s)
        self.assertIn("no acredita", s)
        for link in re.findall(r"\]\(([^)]+)\)", s):
            self.assertTrue((p.parent / link).resolve().is_file(), link)

    def test_documentation_entry_points_link_archive(self):
        self.assertIn("archive/agent-records/README.md", (ROOT / "docs/README.md").read_text())
        self.assertIn("docs/archive/agent-records/README.md", (ROOT / "README.en.md").read_text())

    def test_index_describes_nightly_and_manual_instrumentation(self):
        s = (ROOT / "docs/README.md").read_text()
        self.assertIn("nocturna", s)
        self.assertIn("manual explícita", s)
        self.assertIn("PR/push ordinarios", s)

if __name__ == "__main__":
    unittest.main()
