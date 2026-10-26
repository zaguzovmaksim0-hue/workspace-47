"""Deterministic source composition without changing the packaged shim."""
import json
import pathlib
import tempfile
import unittest
from unittest import mock
from tools.generate_afirma_shim import compose, synchronize

ROOT = pathlib.Path(__file__).resolve().parents[2]


def airef_extraction(legacy):
    constants = legacy[legacy.index("  const airefOrigin ="):legacy.index("  const cdtiOrigin =")]
    helpers = legacy[legacy.index("  function isValidAirefPayload"):legacy.index("  function isExactCanariasChallenge")]
    start = legacy.index("    const isAirefOrigin =")
    branch = legacy[start:legacy.index("    if (isAirefOrigin && !isExactAirefCall)", start)]
    module = ("\n  matchesAirefSign = (args, successCallback, errorCallback) => {\n" +
              constants.replace("  const", "    const") +
              "\n" + "".join("  " + line if line.strip() else line for line in helpers.splitlines(keepends=True)) +
              branch + "    return isAirefOrigin ? isExactAirefCall : null;\n  };\n")
    return constants, helpers, branch, module


def cdti_extraction(legacy):
    constants = legacy[legacy.index("  const cdtiOrigin ="):legacy.index("  const policiaOrigin =")]
    start = legacy.index("    const isCdtiOrigin =")
    branch = legacy[start:legacy.index("    if (isCdtiOrigin && !isExactCdtiCall)", start)]
    module = ("\n  matchesCdtiSign = (args, successCallback, errorCallback) => {\n" +
              constants.replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              "    return isCdtiOrigin ? isExactCdtiCall : null;\n  };\n")
    return constants, branch, module


def gran_canaria_extraction(legacy):
    origin = legacy[legacy.index('  const granCanariaOrigin ='):legacy.index('  const canariasOrigin =')]
    constants = legacy[legacy.index("  const granCanariaExtraProperties ="):legacy.index('  const fuerteventuraOrigin =')]
    start = legacy.index('    const isGranCanariaOrigin =')
    branch = legacy[start:legacy.index('    if (isGranCanariaOrigin && !isExactGranCanariaCall)', start)]
    module = ('\n  matchesGranCanariaSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isGranCanariaOrigin ? isExactGranCanariaCall : null;\n  };\n')
    return origin, constants, branch, module


def fuerteventura_extraction(legacy):
    origin = ""
    constants = legacy[legacy.index('  const fuerteventuraOrigin ='):legacy.index('  const minecoOrigin =')]
    start = legacy.index('    const isFuerteventuraOrigin =')
    branch = legacy[start:legacy.index('    if (isFuerteventuraOrigin && !isExactFuerteventuraCall)', start)]
    module = ('\n  matchesFuerteventuraSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isFuerteventuraOrigin ? isExactFuerteventuraCall : null;\n  };\n')
    return origin, constants, branch, module


class AfirmaShimGenerationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.parts = self.root / "config/afirma_shim"
        self.parts.mkdir(parents=True)
        self.manifest = self.parts / "manifest.json"
        self.output = self.root / "app/src/main/res/raw/afirma_shim_bootstrap.js"
        self.output.parent.mkdir(parents=True)
        self.names = [f"{i:02d}-section.js" for i in range(20)]
        self.write_manifest()
        for name in self.names:
            (self.parts / name).write_bytes((name + "\n").encode())

    def write_manifest(self, **changes):
        data = {"format": 1, "parts": self.names}
        data.update(changes)
        self.manifest.write_text(json.dumps(data), encoding="utf-8")

    def test_cantabria_is_packaged_separately_from_unchanged_core(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_cantabria.js"
        self.assertTrue(module.is_file(), "Cantabria needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const cantabriaOrigin"):legacy.index("  const ugrLiteral =")]
        start = legacy.index("    const isExactCantabriaCall =")
        end = legacy.index("    const isJccmOrigin =", start)
        branch = legacy[start:end]
        expression = branch.split("=\n", 1)[1]
        expected_module = "\n  matchesCantabriaSign = args => {\n" + constants.replace("  const", "    const") + "    return " + expression.lstrip() + "  };\n"
        self.assertEqual(expected_module, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        expected_core = legacy.replace(constants, "  let matchesCantabriaSign = () => false;\n").replace(
            branch, "    const isExactCantabriaCall = matchesCantabriaSign(args);\n")
        jccm_constants = legacy[legacy.index("  const jccmOrigin ="):legacy.index("  const jccmRegistroOrigin =")]
        jccm_start = legacy.index("    const isJccmOrigin =")
        jccm_branch = legacy[jccm_start:legacy.index("    const dataB64 =", jccm_start)]
        expected_core = expected_core.replace(jccm_constants, "  let matchesJccmSign = () => false;\n").replace(
            jccm_branch, "    const isExactJccmCall = matchesJccmSign(args);\n")
        registro_constants = legacy[legacy.index("  const jccmRegistroOrigin ="):legacy.index("  const sevillaAtseOrigin =")]
        registro_start = legacy.index("    const isJccmRegistroOrigin =")
        registro_branch = legacy[registro_start:legacy.index("    if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", registro_start)]
        expected_core = expected_core.replace(registro_constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesJccmRegistroSign = () => null;\n").replace(
            registro_branch, "    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);\n").replace(
            "if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", "if (isExactJccmRegistroCall === false)")
        constants, helpers, branch, _ = airef_extraction(legacy)
        expected_core = expected_core.replace(constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesAirefSign = () => null;\n").replace(
            helpers, "").replace(branch,
            "    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);\n").replace(
            "if (isAirefOrigin && !isExactAirefCall)", "if (isExactAirefCall === false)")
        constants, branch, _ = cdti_extraction(legacy)
        expected_core = expected_core.replace(constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesCdtiSign = () => null;\n").replace(
            branch, "    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);\n").replace(
            "if (isCdtiOrigin && !isExactCdtiCall)", "if (isExactCdtiCall === false)")
        origin = legacy[legacy.index("  const policiaOrigin ="):legacy.index("  const granCanariaOrigin =")]
        constants = legacy[legacy.index("  const policiaProcedurePage ="):legacy.index("  const staBatchOrigin =")]
        start = legacy.index("    const isPoliciaOrigin =")
        branch = legacy[start:legacy.index("    if (isPoliciaOrigin && !isExactPoliciaCall)", start)]
        expected_core = expected_core.replace(origin,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesPoliciaSign = () => null;\n").replace(
            constants, "").replace(branch,
            "    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);\n").replace(
            "if (isPoliciaOrigin && !isExactPoliciaCall)", "if (isExactPoliciaCall === false)")
        origin, constants, branch, _ = gran_canaria_extraction(legacy)
        expected_core = expected_core.replace(origin, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesGranCanariaSign = () => null;\n').replace(constants, "").replace(
            branch, '    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);\n').replace(
            'if (isGranCanariaOrigin && !isExactGranCanariaCall)', 'if (isExactGranCanariaCall === false)')
        origin, constants, branch, _ = fuerteventura_extraction(legacy)
        expected_core = expected_core.replace(constants, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesFuerteventuraSign = () => null;\n').replace(
            branch, '    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);\n').replace(
            'if (isFuerteventuraOrigin && !isExactFuerteventuraCall)', 'if (isExactFuerteventuraCall === false)')
        self.assertEqual(expected_core, core)

    def test_jccm_is_packaged_separately_with_unchanged_exact_tuple(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_jccm.js"
        self.assertTrue(module.is_file(), "JCCM needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const jccmOrigin ="):legacy.index("  const jccmRegistroOrigin =")]
        start = legacy.index("    const isJccmOrigin =")
        branch = legacy[start:legacy.index("    const dataB64 =", start)]
        expected = "\n  matchesJccmSign = args => {\n" + constants.replace("  const", "    const") + branch + "    return isExactJccmCall;\n  };\n"
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://ventanillaelectronica.jccm.es", core)
        self.assertNotIn("QUJDREU=", core)
        self.assertIn("    const isExactJccmCall = matchesJccmSign(args);\n", core)

    def test_jccm_registro_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_jccm_registro.js"
        self.assertTrue(module.is_file(), "JCCM Registro needs an independent optional body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const jccmRegistroOrigin ="):legacy.index("  const sevillaAtseOrigin =")]
        start = legacy.index("    const isJccmRegistroOrigin =")
        end = legacy.index("    if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", start)
        branch = legacy[start:end]
        expected = "\n  matchesJccmRegistroSign = (args, successCallback, errorCallback) => {\n" + constants.replace("  const", "    const").replace('\n    "', '\n      "') + branch + "    return isJccmRegistroOrigin ? isExactJccmRegistroCall : null;\n  };\n"
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://registrounicociudadanos.jccm.es", core)
        self.assertNotIn("format=XAdES Detached\\nmode=implicit", core)
        self.assertIn("    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);\n", core)
        rejection = '    if (isExactJccmRegistroCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n'
        self.assertIn(rejection, core)

    def test_airef_is_packaged_with_helpers_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_airef.js"
        self.assertTrue(module.is_file(), "AIReF needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = airef_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        for body in ("https://sede.airef.es", "/invesiteRE/action/solicitud/view",
                     "function isValidAirefPayload", "function isExactAirefSigningPage"):
            self.assertNotIn(body, core)
        self.assertIn("    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);\n", core)
        self.assertIn('    if (isExactAirefCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_cdti_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_cdti.js"
        self.assertTrue(module.is_file(), "CDTI needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, expected = cdti_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        for body in ("https://sede.cdti.gob.es", "const cdtiPage =", "const cdtiChallengePattern =",
                     "const cdtiExtraProperties ="):
            self.assertNotIn(body, core)
        self.assertIn("    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);\n", core)
        self.assertIn('    if (isExactCdtiCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)
        self.assertIn('isExactCdtiCall ? args[0] + "=" : args[0]', core)

    def test_policia_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_policia.js"
        self.assertTrue(module.is_file(), "Policia needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        origin = legacy[legacy.index("  const policiaOrigin ="):legacy.index("  const granCanariaOrigin =")]
        constants = legacy[legacy.index("  const policiaProcedurePage ="):legacy.index("  const staBatchOrigin =")]
        start = legacy.index("    const isPoliciaOrigin =")
        branch = legacy[start:legacy.index("    if (isPoliciaOrigin && !isExactPoliciaCall)", start)]
        expected = ("\n  matchesPoliciaSign = (args, successCallback, errorCallback) => {\n" +
                    (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
                    "    return isPoliciaOrigin ? isExactPoliciaCall : null;\n  };\n")
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://sede.policia.gob.es", core)
        self.assertNotIn("const policiaExtraProperties =", core)
        dispatch = "    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);\n"
        self.assertIn(dispatch, core)
        self.assertLess(core.index("const isExactCdtiCall = matchesCdtiSign"), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index("const isExactJccmRegistroCall = matchesJccmRegistroSign"))
        self.assertIn('    if (isExactPoliciaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_gran_canaria_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_gran_canaria.js"
        self.assertTrue(module.is_file(), "GranCanaria needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = gran_canaria_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://sede.grancanaria.com', core)
        self.assertNotIn("const granCanariaExtraProperties =", core)
        dispatch = '    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isCanariasOrigin ='), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactFuerteventuraCall = matchesFuerteventuraSign'))
        self.assertIn('    if (isExactGranCanariaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_fuerteventura_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_fuerteventura.js"
        self.assertTrue(module.is_file(), "Fuerteventura needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = fuerteventura_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://sede.cabildofuer.es', core)
        self.assertNotIn("const fuerteventuraExtraProperties =", core)
        dispatch = '    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isExactGranCanariaCall = matchesGranCanariaSign'), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactMinecoCall ='))
        self.assertIn('    if (isExactFuerteventuraCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_repository_resource_is_generated_exactly(self):
        actual = compose(ROOT)
        self.assertIn(b"matchesCantabriaSign(args)", actual)
        self.assertFalse(synchronize(ROOT, write=False))

    def test_packaged_fragments_reconstruct_source_bytes(self):
        raw = ROOT / "app/src/main/res/raw"
        names = ["afirma_shim_bootstrap.js", "afirma_shim_cantabria.js", "afirma_shim_jccm.js",
                 "afirma_shim_jccm_registro.js", "afirma_shim_airef.js", "afirma_shim_cdti.js", "afirma_shim_policia.js", "afirma_shim_gran_canaria.js", "afirma_shim_fuerteventura.js", "afirma_shim_handlers.js", "afirma_shim_installation.js"]
        self.assertEqual(compose(ROOT), b"".join((raw / name).read_bytes() for name in names))
        self.assertEqual(set(names), {p.name for p in raw.glob("afirma_shim*.js")})

    def test_concatenation_is_ordered_without_extra_separators(self):
        self.assertEqual("".join(name + "\n" for name in self.names).encode(), compose(self.root))
        self.names.reverse()
        self.write_manifest()
        self.assertEqual("".join(name + "\n" for name in self.names).encode(), compose(self.root))

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

    def test_missing_and_extra_packaged_fragments_fail_closed(self):
        synchronize(self.root, write=True)
        self.output.unlink()
        with self.assertRaises(ValueError):
            synchronize(self.root)
        synchronize(self.root, write=True)
        (self.output.parent / "afirma_shim_unused.js").write_text("extra\n")
        for write in (False, True):
            with self.subTest(write=write), self.assertRaises(ValueError):
                synchronize(self.root, write=write)

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
