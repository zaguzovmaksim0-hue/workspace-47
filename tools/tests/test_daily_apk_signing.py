import unittest
from pathlib import Path
from tools.verify_daily_apk_signature import validate_report

ROOT = Path(__file__).resolve().parents[2]
FINGERPRINT = "ab" * 32


def report(fingerprint=FINGERPRINT, signers=1, v2="true"):
    return (f"Verified using v2 scheme (APK Signature Scheme v2): {v2}\n"
            f"Number of signers: {signers}\n"
            f"Signer #1 certificate SHA-256 digest: {fingerprint}\n")


class DailyApkSigningTest(unittest.TestCase):
    def test_accepts_exact_single_pinned_signer(self):
        validate_report(report(), FINGERPRINT)

    def test_accepts_scheme_label_used_by_phone_apksigner(self):
        text = report().replace("Signer #1 certificate", "V3.0 Signer: certificate")
        validate_report(text, FINGERPRINT)

    def test_rejects_conflicting_scheme_certificate(self):
        extra = "V3.0 Signer: certificate SHA-256 digest: " + "cd" * 32 + "\n"
        with self.assertRaises(ValueError):
            validate_report(report() + extra, FINGERPRINT)

    def test_rejects_other_signer(self):
        with self.assertRaises(ValueError):
            validate_report(report("cd" * 32), FINGERPRINT)

    def test_rejects_multiple_signers(self):
        with self.assertRaises(ValueError):
            validate_report(report(signers=2), FINGERPRINT)

    def test_rejects_conflicting_signer_counts(self):
        with self.assertRaises(ValueError):
            validate_report(report() + "Number of signers: 2\n", FINGERPRINT)

    def test_rejects_missing_or_disabled_v2(self):
        for text in ("", report(v2="false"), report().replace("Number of signers: 1\n", "")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_report(text, FINGERPRINT)

    def test_rejects_malformed_expected_fingerprint(self):
        for fingerprint in ("", "ab", FINGERPRINT + "\nextra", "gg" * 32):
            with self.subTest(fingerprint=fingerprint), self.assertRaises(ValueError):
                validate_report(report(), fingerprint)

    def test_rejects_duplicate_or_extra_certificate_lines(self):
        for extra in (f"Signer #1 certificate SHA-256 digest: {FINGERPRINT}\n",
                      f"Signer #2 certificate SHA-256 digest: {FINGERPRINT}\n"):
            with self.assertRaises(ValueError):
                validate_report(report() + extra, FINGERPRINT)

    def test_workflow_exposes_secrets_only_to_signing_step(self):
        workflow = (ROOT / ".github/workflows/daily-apk.yml").read_text()
        self.assertIn("environment: daily-signing", workflow)
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("pull_request", workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("ref: ${{ github.sha }}", workflow)
        self.assertNotIn("secrets.JFM_DAILY", workflow.split("- name: Sign with persistent daily key")[0])
        self.assertIn("if: always()", workflow)
        self.assertNotIn("path: ${{ runner.temp }}", workflow)

    def test_no_secret_or_debug_fallback_in_signing_script(self):
        script = (ROOT / "scripts/ci/sign-daily-apk.sh").read_text()
        self.assertIn("set -euo pipefail", script)
        self.assertIn("env:JFM_DAILY_STORE_PASSWORD", script)
        self.assertIn("env:JFM_DAILY_KEY_PASSWORD", script)
        self.assertIn("verify_daily_apk_signature.py", script)
        self.assertNotIn("keytool -genkey", script)
        self.assertNotIn("|| true", script)


if __name__ == "__main__":
    unittest.main()
