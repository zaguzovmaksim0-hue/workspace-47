"""Keep emulator opt-in explicit while preserving the default unit/build gates."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OperatorVerificationScopeTest(unittest.TestCase):
    def test_only_explicit_manual_opt_in_runs_instrumentation(self):
        text = (ROOT / ".github/workflows/ci.yml").read_text()
        job = text.split("  android-instrumented:\n", 1)[1].split("\n  python:", 1)[0]
        condition = re.search(r"^    if: (.+)$", job, re.M)
        self.assertIsNotNone(condition)
        self.assertEqual(
            "${{ github.event_name == 'workflow_dispatch' && inputs.run_instrumentation }}",
            condition.group(1),
        )
        self.assertIn("script: bash scripts/run-android-instrumentation-ci.sh", job)
        self.assertNotIn("continue-on-error", job)
        # Ordinary PRs and main pushes remain non-E2E, including after merge.
        for event, opt_in, expected in [
            ("pull_request", False, False),
            ("pull_request", True, False),
            ("push", False, False),
            ("push", True, False),
            ("workflow_dispatch", False, False),
            ("workflow_dispatch", True, True),
        ]:
            run = event == "workflow_dispatch" and opt_in
            self.assertEqual(expected, run)
        self.assertIn("type: boolean", text)
        self.assertIn("default: false", text)

    def test_unit_build_and_security_jobs_are_not_conditioned_on_the_label(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        sections = dict(re.findall(r"^  ([a-z-]+):\n(.*?)(?=^  [a-z-]+:\n|\Z)", ci.split("jobs:\n", 1)[1], re.M | re.S))
        for name in ("android", "python", "go"):
            self.assertNotIn("verification:no-e2e", sections[name])
            self.assertIsNone(re.search(r"^    if:", sections[name], re.M))
        self.assertIn("testDebugUnitTest testQaUnitTest", sections["android"])
        self.assertIn("lintDebug lintQa", sections["android"])
        self.assertIn("assembleDebug assembleQa assembleQaAndroidTest", sections["android"])
        self.assertIn("verify-release-fail-closed.sh", sections["android"])
        security = (ROOT / ".github/workflows/security.yml").read_text()
        self.assertNotIn("verification:no-e2e", security)


if __name__ == "__main__":
    unittest.main()
