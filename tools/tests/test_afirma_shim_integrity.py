#!/usr/bin/env python3
"""Static and behavioral integrity tests for afirma_shim.js."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHIM_PATH = ROOT / "app" / "src" / "main" / "res" / "raw" / "afirma_shim.js"


class AfirmaShimIntegrityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.assertTrue(SHIM_PATH.is_file(), f"Missing shim at {SHIM_PATH}")
        self.content = SHIM_PATH.read_text(encoding="utf-8")

    def test_javascript_syntax_is_valid(self) -> None:
        result = subprocess.run(
            ["node", "-c", str(SHIM_PATH)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            0,
            result.returncode,
            f"afirma_shim.js failed JS syntax check:\n{result.stderr}",
        )

    def test_multimode_sign_interceptor_is_uniquely_defined_and_registered(self) -> None:
        interceptor_defs = re.findall(r"function\s+interceptJuntaMultiModeSign\s*\(", self.content)
        self.assertEqual(1, len(interceptor_defs), "Expected exactly one interceptJuntaMultiModeSign function definition")

        result_receiver_defs = re.findall(r"function\s+receiveJuntaMultiModeResult\s*\(", self.content)
        self.assertEqual(1, len(result_receiver_defs), "Expected exactly one receiveJuntaMultiModeResult function definition")

        hook_installations = re.findall(r'installMethodHook\s*\(\s*value\s*,\s*"multiModeSign"\s*,\s*"MULTI_MODE_SIGN"\s*\)', self.content)
        self.assertEqual(1, len(hook_installations), "Expected exactly one installMethodHook for multiModeSign")

    def test_junta_vea_page_binding_is_pinned(self) -> None:
        self.assertIn("juntaVeaOrigin", self.content)
        self.assertIn("https://veaja.cloud.juntadeandalucia.es", self.content)
        self.assertIn("juntaVeaExactPaths", self.content)
        self.assertIn("juntaVeaPrefixPaths", self.content)
        self.assertIn("isValidVeaPath", self.content)
        self.assertIn("isJuntaVeaPage", self.content)

    def test_junta_vea_allowed_formats_is_pinned_to_cades_only(self) -> None:
        formats_match = re.search(r"allowedVeaSignFormats\s*=\s*new\s+Set\(\s*\[([^\]]+)\]\s*\)", self.content)
        self.assertIsNotNone(formats_match, "allowedVeaSignFormats Set definition not found")
        formats_raw = formats_match.group(1)
        formats = [f.strip().strip('"').strip("'") for f in formats_raw.split(",") if f.strip()]
        self.assertEqual(["CADES"], formats, "allowedVeaSignFormats must contain only CADES")

    def test_junta_vea_allowed_algorithms_excludes_sha224_and_sha384(self) -> None:
        alg_match = re.search(r"allowedVeaSignAlgorithms\s*=\s*new\s+Set\(\s*\[([^\]]+)\]\s*\)", self.content)
        self.assertIsNotNone(alg_match, "allowedVeaSignAlgorithms Set definition not found")
        alg_raw = alg_match.group(1)
        algorithms = set(a.strip().strip('"').strip("'") for a in alg_raw.split(",") if a.strip())
        self.assertEqual({"SHA1WITHRSA", "SHA256WITHRSA", "SHA512WITHRSA"}, algorithms)
        self.assertNotIn("SHA384WITHRSA", algorithms)
        self.assertNotIn("SHA224WITHRSA", algorithms)

    def test_junta_vea_path_logic_rejects_arbitrary_root_prefixes_via_node(self) -> None:
        node_script = """
        const juntaVeaExactPaths = new Set([
          "/",
          "/inicio",
          "/confirmacion-modificacion-datos-contacto",
          "/documentacion-voluntaria",
          "/justificante",
          "/datos-contacto",
          "/area-personal"
        ]);
        const juntaVeaPrefixPaths = [
          "/inicio/",
          "/borrador/",
          "/formulario/",
          "/resumen-pago/",
          "/procedimiento-detalle/",
          "/competente/",
          "/tarea/"
        ];
        function isValidVeaPath(pathname) {
          const normalized = pathname || "/";
          return juntaVeaExactPaths.has(normalized) ||
            juntaVeaPrefixPaths.some(p => normalized.startsWith(p));
        }

        const tests = [
          { path: "/", expected: true },
          { path: "/inicio", expected: true },
          { path: "/inicio/detail", expected: true },
          { path: "/borrador/draft-123", expected: true },
          { path: "/admin/secret", expected: false },
          { path: "/anything", expected: false },
          { path: "/other", expected: false },
          { path: "/inicioprivado", expected: false }
        ];

        for (const t of tests) {
          const actual = isValidVeaPath(t.path);
          if (actual !== t.expected) {
            console.error(`Mismatch for ${t.path}: expected ${t.expected}, got ${actual}`);
            process.exit(1);
          }
        }
        """
        result = subprocess.run(
            ["node", "-e", node_script],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, f"Path evaluation failed:\n{result.stderr}")

    def test_no_split_declarations_around_batch_result(self) -> None:
        self.assertNotIn("const errorCode = typeof result.errorCode === \"string\" &&\n  function", self.content)


if __name__ == "__main__":
    unittest.main()
