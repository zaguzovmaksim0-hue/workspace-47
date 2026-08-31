from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/real-e2e.yml"
RUNNER = ROOT / "scripts/ci/run-real-e2e.sh"
REPORT_HELPER = ROOT / "scripts/ci/real_e2e_report.py"
INSTRUMENTATION = ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt"
CATALOG = ROOT / "app/src/main/res/raw/public_portal_catalog_v1.json"


class RealE2ePolicyTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def helper(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(REPORT_HELPER), *args],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
        )

    def test_real_e2e_is_manual_main_only_and_read_only(self) -> None:
        source = self.read(WORKFLOW)
        self.assertIn("workflow_dispatch:", source)
        self.assertNotIn("pull_request:", source)
        self.assertNotIn("pull_request_target:", source)
        self.assertNotIn("\n  push:\n", source)
        self.assertIn("permissions:\n  contents: read", source)
        self.assertIn("if: github.ref == 'refs/heads/main'", source)
        self.assertIn("environment: real-e2e", source)
        self.assertIn("ref: ${{ github.sha }}", source)
        self.assertIn("persist-credentials: false", source)
        self.assertIn('test "$GITHUB_REF" = refs/heads/main', source)
        self.assertIn('test "$(git rev-parse HEAD)" = "$GITHUB_SHA"', source)

    def test_real_e2e_actions_are_sha_pinned(self) -> None:
        source = self.read(WORKFLOW)
        for line in source.splitlines():
            stripped = line.strip()
            if not stripped.startswith("uses:"):
                continue
            reference = stripped.split("uses:", 1)[1].strip().split()[0]
            self.assertRegex(reference, r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
        self.assertIn(
            "ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
            source,
        )
        self.assertIn(
            "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
            source,
        )

    def test_real_e2e_secrets_are_scoped_to_the_emulator_step(self) -> None:
        source = self.read(WORKFLOW)
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_P12_B64"))
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_PASSWORD"))
        build = source.index("Build QA test artifacts before credentials are exposed")
        credential = source.index("REAL_E2E_CERT_P12_B64:")
        self.assertLess(build, credential)
        self.assertIn("retention-days: 1", source)
        self.assertIn("build/reports/real-e2e/summary.json", source)
        self.assertIn("build/reports/real-e2e/summary.md", source)
        self.assertIn("build/reports/real-e2e/navigation/", source)
        self.assertNotIn("identity.p12", source)
        self.assertNotIn("password\n", source.lower())

    def test_runner_uses_private_fixture_and_never_clears_app_data(self) -> None:
        source = self.read(RUNNER)
        self.assertTrue(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"))
        self.assertIn('FIXTURE_DIR="files/real-e2e"', source)
        self.assertIn('run-as "$PACKAGE_NAME"', source)
        self.assertIn("base64 --decode", source)
        self.assertIn("unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD", source)
        self.assertIn('rm -rf "$FIXTURE_DIR"', source)
        self.assertNotIn("pm clear", source)
        self.assertNotIn("set -x", source)
        self.assertNotIn("echo $REAL_E2E", source)
        self.assertNotIn("printenv", source)

    def test_runner_selects_catalog_and_supports_one_portal_retry(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        self.assertIn("portal_id:", workflow)
        self.assertIn("deep_auth_signing:", workflow)
        self.assertIn('PORTAL_ID_FILTER: ${{ inputs.portal_id }}', workflow)
        self.assertIn('REAL_E2E_DEEP_AUTH_SIGNING: ${{ inputs.deep_auth_signing }}', workflow)
        self.assertIn('select --catalog "$CATALOG_FILE" --portal "${PORTAL_ID_FILTER:-}"', runner)
        self.assertIn('for portal_id in "${portal_ids[@]}"', runner)
        self.assertIn("-e portalId", runner)
        self.assertIn("-e realE2e true", runner)
        self.assertIn("-e realE2eDeep", runner)

    def test_instrumentation_requires_explicit_opt_in_and_has_no_level_six(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('arguments.getString(REAL_E2E_ARGUMENT) == "true"', source)
        self.assertIn('arguments.getString(PORTAL_ID_ARGUMENT)', source)
        self.assertIn("ProbeClassification", source)
        self.assertNotIn("level = 6", source)
        self.assertNotIn("maxOf(result.level, 6)", source)
        self.assertIn("signingCancelledAtBoundary", source)

    def test_administrative_signing_profiles_are_not_deep_sign_allowlisted(self) -> None:
        source = self.read(INSTRUMENTATION)
        allowlist = source.split("val SAFE_AUTH_SIGN_PROFILES = setOf(", 1)[1].split(")", 1)[0]
        for forbidden in (
            "reg-age-redsara",
            "jccm-registro-generico",
            "airef-instancia-general",
            "ministerio-economia-instancia-generica",
            "xunta-galicia-solicitude-xenerica",
            "age-portal-de-la-transparencia",
            "caib-portafib",
        ):
            self.assertNotIn(f'"{forbidden}"', allowlist)
        for expected in (
            "junta-andalucia",
            "unizar-tramitador",
            "junta-ofvirtual",
            "aragon-siraw",
        ):
            self.assertIn(f'"{expected}"', allowlist)

    def test_report_helper_selects_exactly_the_current_catalog(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        expected = [entry["portalId"] for entry in catalog["entries"]]
        completed = self.helper("select", "--catalog", str(CATALOG))
        self.assertEqual(0, completed.returncode, completed.stderr)
        actual = completed.stdout.splitlines()
        self.assertEqual(expected, actual)
        self.assertEqual(183, len(actual))

    def test_report_helper_single_portal_filter_is_exact(self) -> None:
        selected = "junta-andalucia-carne-joven"
        completed = self.helper("select", "--catalog", str(CATALOG), "--portal", selected)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual([selected], completed.stdout.splitlines())
        rejected = self.helper("select", "--catalog", str(CATALOG), "--portal", "not-a-real-portal")
        self.assertNotEqual(0, rejected.returncode)

    def test_report_helper_rejects_secret_like_extra_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic",
                "--catalog",
                str(CATALOG),
                "--portal",
                "junta-andalucia-carne-joven",
                "--output",
                str(path),
                "--reason",
                "RESULT_MISSING",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            data["certificatePassword"] = "must-never-export"
            path.write_text(json.dumps(data), encoding="ascii")
            validated = self.helper(
                "validate-result",
                "--result",
                str(path),
                "--portal",
                "junta-andalucia-carne-joven",
            )
            self.assertNotEqual(0, validated.returncode)


if __name__ == "__main__":
    unittest.main()
