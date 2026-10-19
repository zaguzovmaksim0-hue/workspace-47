"""Deterministic source composition without changing the packaged shim."""
import hashlib
import json
import pathlib
import tempfile
import unittest
from unittest import mock
from tools.generate_afirma_shim import compose, synchronize

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE_SHA256 = "1aa66f0381553973a642df4ffc38f46215ce04a810f79a31ca31fe5e46cdc26d"


class AfirmaShimGenerationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.parts = self.root / "config/afirma_shim"
        self.parts.mkdir(parents=True)
        self.manifest = self.parts / "manifest.json"
        self.output = self.root / "app/src/main/res/raw/afirma_shim.js"
        self.output.parent.mkdir(parents=True)
        self.names = ["00-bootstrap.js", "10-feature.js"]
        self.write_manifest()
        (self.parts / self.names[0]).write_bytes(b"(() => {\n")
        (self.parts / self.names[1]).write_bytes(b"})();\n")

    def write_manifest(self, **changes):
        data = {"format": 1, "parts": self.names}
        data.update(changes)
        self.manifest.write_text(json.dumps(data), encoding="utf-8")

    def test_repository_resource_is_generated_exactly(self):
        actual = compose(ROOT)
        self.assertEqual((ROOT / "app/src/main/res/raw/afirma_shim.js").read_bytes(), actual)
        self.assertEqual(BASELINE_SHA256, hashlib.sha256(actual).hexdigest())
        self.assertFalse(synchronize(ROOT, write=False))

    def test_concatenation_is_ordered_without_extra_separators(self):
        self.assertEqual(b"(() => {\n})();\n", compose(self.root))
        self.names.reverse()
        self.write_manifest()
        self.assertEqual(b"})();\n(() => {\n", compose(self.root))

    def test_check_is_read_only_and_detects_stale_output(self):
        self.output.write_bytes(b"stale")
        with self.assertRaises(ValueError):
            synchronize(self.root, write=False)
        self.assertEqual(b"stale", self.output.read_bytes())

    def test_write_is_explicit_idempotent_and_preserves_mode(self):
        self.output.write_bytes(b"stale")
        self.output.chmod(0o640)
        self.assertTrue(synchronize(self.root, write=True))
        self.assertEqual(0o640, self.output.stat().st_mode & 0o777)
        self.assertFalse(synchronize(self.root, write=True))
        self.assertFalse(synchronize(self.root, write=False))
        self.assertEqual([], list(self.output.parent.glob(".afirma-shim-*")))

    def test_failed_replace_preserves_existing_resource_and_removes_temporary_file(self):
        self.output.write_bytes(b"original")
        with mock.patch("tools.generate_afirma_shim.os.replace", side_effect=OSError("synthetic failure")):
            with self.assertRaises(OSError):
                synchronize(self.root, write=True)
        self.assertEqual(b"original", self.output.read_bytes())
        self.assertEqual([], list(self.output.parent.glob(".afirma-shim-*")))

    def test_missing_and_unlisted_fragments_fail_closed(self):
        (self.parts / self.names[0]).unlink()
        with self.assertRaises(ValueError):
            compose(self.root)
        (self.parts / self.names[0]).write_text("bootstrap\n")
        (self.parts / "99-unused.js").write_text("extra\n")
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_duplicate_and_unsafe_names_fail_closed(self):
        for names in [[self.names[0]] * 2, ["../escape.js"], ["/absolute.js"], ["subdir/10-file.js"], [], [1]]:
            with self.subTest(names=names):
                self.write_manifest(parts=names)
                with self.assertRaises(ValueError):
                    compose(self.root)

    def test_unknown_manifest_fields_and_versions_fail_closed(self):
        for changes in [{"format": 2}, {"format": True}, {"extra": "ignored"}, {"parts": "00-bootstrap.js"}]:
            with self.subTest(changes=changes):
                self.write_manifest(**changes)
                with self.assertRaises(ValueError):
                    compose(self.root)
        self.manifest.write_text('{"format":1,"format":1,"parts":[]}')
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_non_utf8_empty_and_crlf_parts_are_rejected(self):
        for data in [b"", b"\xff", b"line\r\n"]:
            with self.subTest(data=data):
                (self.parts / self.names[0]).write_bytes(data)
                with self.assertRaises(ValueError):
                    compose(self.root)

    def test_source_and_manifest_symlinks_are_rejected(self):
        p = self.parts / self.names[0]
        p.unlink()
        p.symlink_to(self.parts / self.names[1])
        with self.assertRaises(ValueError):
            compose(self.root)
        p.unlink()
        p.write_text("bootstrap\n")
        real = self.parts / "saved-manifest.json"
        self.manifest.rename(real)
        self.manifest.symlink_to(real)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_source_directory_symlink_is_rejected(self):
        real = self.root / "real-parts"
        self.parts.rename(real)
        self.parts.symlink_to(real, target_is_directory=True)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_output_symlink_is_rejected_without_touching_target(self):
        target = self.root / "target"
        target.write_bytes(b"untouched")
        self.output.symlink_to(target)
        with self.assertRaises(ValueError):
            synchronize(self.root, write=True)
        self.assertEqual(b"untouched", target.read_bytes())

    def test_runtime_bound_counts_utf16_units_not_unicode_codepoints(self):
        (self.parts / self.names[0]).write_text("😀" * (48 * 1024) + "\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_final_newline_and_manifest_byte_bound_are_enforced(self):
        (self.parts / self.names[0]).write_bytes(b"missing final newline")
        with self.assertRaises(ValueError):
            compose(self.root)
        self.manifest.write_bytes(b" " * 8193)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_script_character_and_byte_bounds_are_enforced(self):
        (self.parts / self.names[0]).write_bytes(b"x" * (96 * 1024))
        with self.assertRaises(ValueError):
            compose(self.root)
        (self.parts / self.names[0]).write_bytes(b"x" * (384 * 1024 + 1))
        with self.assertRaises(ValueError):
            compose(self.root)


if __name__ == "__main__":
    unittest.main()
