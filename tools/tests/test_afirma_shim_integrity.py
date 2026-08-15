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
        self.assertIn("juntaVeaAllowedPaths", self.content)
        self.assertIn("isJuntaVeaPage", self.content)

    def test_no_split_declarations_around_batch_result(self) -> None:
        self.assertNotIn("const errorCode = typeof result.errorCode === \"string\" &&\n  function", self.content)


if __name__ == "__main__":
    unittest.main()
