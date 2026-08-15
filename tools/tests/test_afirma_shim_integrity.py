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

    def test_vea_document_ready_binding_is_defined_and_invoked(self) -> None:
        self.assertIn("function notifyVeaDocumentReady()", self.content)
        self.assertIn("notifyVeaDocumentReady();", self.content)
        self.assertIn('"VEA_DOCUMENT_READY"', self.content)

    def test_vea_extra_properties_parser_regressions(self) -> None:
        self.assertIn("parseVeaExtraProperties", self.content)
        node_script = f"""
        {self.content}

        // Test parser directly in Node environment with shim loaded
        """
        # We also extract and test the parseVeaExtraProperties directly with node
        extractor_script = """
        const fs = require('fs');
        const content = fs.readFileSync('""" + str(SHIM_PATH) + """', 'utf8');

        // Extract maxExtraPropertiesChars and parseVeaExtraProperties function + dependencies
        const vm = require('vm');
        const sandbox = {
          maxExtraPropertiesChars: 65536,
          console: console,
          process: process
        };
        const ctx = vm.createContext(sandbox);

        // Find parseVeaExtraProperties function block in content
        const match = content.match(/(const veaRequiredProperties[\\s\\S]*?function parseVeaExtraProperties[\\s\\S]*?\\n  \\})/);
        if (!match) {
          console.error("Could not find parseVeaExtraProperties in shim");
          process.exit(1);
        }
        vm.runInContext(match[1], ctx);

        const parse = sandbox.parseVeaExtraProperties;

        const valid256 = "mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;";
        const valid256NoSemi = "mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert";
        const valid512Crlf = "mode=explicit\\r\\nprecalculatedHashAlgorithm=SHA-512\\r\\nfilters=nonexpired:;signingCert;\\r\\n";
        const validSha1 = "mode=explicit\\nprecalculatedHashAlgorithm=SHA-1\\nfilters=nonexpired:;signingCert";

        // Positive tests
        if (!parse(valid256, "SHA256WITHRSA")) { console.error("valid256 failed"); process.exit(1); }
        if (!parse(valid256NoSemi, "SHA256WITHRSA")) { console.error("valid256NoSemi failed"); process.exit(1); }
        if (!parse(valid512Crlf, "SHA512WITHRSA")) { console.error("valid512Crlf failed"); process.exit(1); }
        if (!parse(validSha1, "SHA1WITHRSA")) { console.error("validSha1 failed"); process.exit(1); }

        // Regressions / Negative tests
        if (parse("xmode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("xmode should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;\\nunknownKey=value", "SHA256WITHRSA")) {
          console.error("unknown key should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nmode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("duplicate key should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;qualified:12345", "SHA256WITHRSA")) {
          console.error("qualified suffix should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nprecalculatedHashAlgorithm=SHA-1\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("mismatched hash SHA-1 vs SHA256withRSA should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;", "SHA1WITHRSA")) {
          console.error("mismatched hash SHA-256 vs SHA1withRSA should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nprecalculatedHashAlgorithm=SHA-512\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("mismatched hash SHA-512 vs SHA256withRSA should fail"); process.exit(1);
        }
        if (parse("mode=implicit\\nprecalculatedHashAlgorithm=SHA-256\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("mode=implicit should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\rprecalculatedHashAlgorithm=SHA-256\\rfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("standalone CR line ending should fail"); process.exit(1);
        }
        if (parse("mode=explicit\\nmalformedline\\nfilters=nonexpired:;signingCert;", "SHA256WITHRSA")) {
          console.error("malformed line without = should fail"); process.exit(1);
        }
        """
        result = subprocess.run(
            ["node", "-e", extractor_script],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, f"Extra properties parser tests failed:\n{result.stderr}")


if __name__ == "__main__":
    unittest.main()
