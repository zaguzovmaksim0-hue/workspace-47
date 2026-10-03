"""Keep an explicit operator-requested no-E2E scope narrow and observable."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OperatorVerificationScopeTest(unittest.TestCase):
    def test_only_the_named_pr_with_the_explicit_label_can_skip_instrumentation(self):
        text = (ROOT / ".github/workflows/ci.yml").read_text()
        job = text.split("  android-instrumented:\n", 1)[1].split("\n  python:", 1)[0]
        condition = re.search(r"^    if: (.+)$", job, re.M)
        self.assertIsNotNone(condition)
        self.assertEqual(
            "${{ github.event_name != 'pull_request' || github.event.pull_request.number != 338 || !contains(github.event.pull_request.labels.*.name, 'verification:no-e2e') }}",
            condition.group(1),
        )
        self.assertIn("script: bash scripts/run-android-instrumentation-ci.sh", job)
        self.assertNotIn("continue-on-error", job)
        # Removing the label, using another PR, pushing main or manually running
        # CI retains instrumentation. No global CI/test skip is introduced.
        for event, number, labels, expected in [
            ("pull_request", 338, {"verification:no-e2e"}, False),
            ("pull_request", 338, set(), True),
            ("pull_request", 339, {"verification:no-e2e"}, True),
            ("push", None, {"verification:no-e2e"}, True),
            ("workflow_dispatch", None, {"verification:no-e2e"}, True),
        ]:
            run = event != "pull_request" or number != 338 or "verification:no-e2e" not in labels
            self.assertEqual(expected, run)

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
