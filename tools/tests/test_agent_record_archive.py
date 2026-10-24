"""Validate historical archive integrity without pinning its contents or prose."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "docs/archive/agent-records"


class AgentRecordArchiveTest(unittest.TestCase):
    def manifest(self):
        return json.loads((ARCHIVE / "manifest.json").read_text())

    def record_path(self, base, relative):
        self.assertIsInstance(relative, str)
        self.assertTrue(relative)
        path = Path(relative)
        self.assertFalse(path.is_absolute())
        self.assertNotIn("..", path.parts)
        self.assertNotIn("\\", relative)
        resolved = (base / path).resolve()
        self.assertTrue(resolved.is_relative_to(base.resolve()), relative)
        return resolved

    def test_manifest_schema_and_paths_are_valid(self):
        m = self.manifest()
        self.assertEqual(set(m), {"schema", "sourceCommit", "records"})
        self.assertEqual(m["schema"], 1)
        self.assertRegex(m["sourceCommit"], r"\A[0-9a-f]{40}\Z")
        self.assertIsInstance(m["records"], list)
        self.assertTrue(m["records"])
        for r in m["records"]:
            self.assertEqual(set(r), {"source", "archive", "sha256", "bytes"})
            self.record_path(ROOT, r["source"])
            self.record_path(ARCHIVE, r["archive"])
            self.assertRegex(r["sha256"], r"\A[0-9a-f]{64}\Z")
            self.assertIs(type(r["bytes"]), int)
            self.assertGreaterEqual(r["bytes"], 0)
        for key in ("source", "archive"):
            paths = [self.record_path(ROOT if key == "source" else ARCHIVE, r[key]) for r in m["records"]]
            self.assertEqual(len(paths), len(set(paths)))

    def test_each_archived_byte_sequence_matches_pinned_digest_and_length(self):
        for r in self.manifest()["records"]:
            with self.subTest(source=r["source"]):
                b = self.record_path(ARCHIVE, r["archive"]).read_bytes()
                self.assertEqual(len(b), r["bytes"])
                self.assertEqual(hashlib.sha256(b).hexdigest(), r["sha256"])

    def test_original_routes_link_archived_records(self):
        for r in self.manifest()["records"]:
            with self.subTest(source=r["source"]):
                p = self.record_path(ROOT, r["source"])
                links = re.findall(r"\]\(([^)]+)\)", p.read_text())
                resolved = {(p.parent / link).resolve() for link in links if not link.startswith("https:")}
                self.assertIn(self.record_path(ARCHIVE, r["archive"]), resolved)
                self.assertTrue(all(x.is_file() for x in resolved))

    def test_archive_index_links_every_record(self):
        p = ARCHIVE / "README.md"
        links = {(p.parent / link).resolve() for link in re.findall(r"\]\(([^)]+)\)", p.read_text())}
        for r in self.manifest()["records"]:
            self.assertIn(self.record_path(ARCHIVE, r["archive"]), links)
        self.assertTrue(all(link.is_file() for link in links))


if __name__ == "__main__":
    unittest.main()
