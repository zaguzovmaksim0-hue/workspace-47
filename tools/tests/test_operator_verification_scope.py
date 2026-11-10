"""Keep nightly/manual emulator checks separate from ordinary unit/build gates."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OperatorVerificationScopeTest(unittest.TestCase):
    def test_unit_build_and_security_jobs_are_not_conditioned_on_the_label(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        sections = dict(re.findall(r"^  ([a-z-]+):\n(.*?)(?=^  [a-z-]+:\n|\Z)", ci.split("jobs:\n", 1)[1], re.M | re.S))
        for name in ("android-variants", "android-release", "detekt", "python", "go"):
            self.assertNotIn("verification:no-e2e", sections[name])
            self.assertIsNone(re.search(r"^    if:", sections[name], re.M))
        self.assertNotIn("verification:no-e2e", sections["android"])
        self.assertRegex(
            sections["android"],
            re.compile(r"^    if: \$\{\{ always\(\) \}\}$", re.M),
        )
        self.assertIn("testDebugUnitTest testQaUnitTest", sections["android-variants"])
        self.assertIn("lintDebug lintQa", sections["android-variants"])
        self.assertIn("assembleDebug assembleQa assembleQaAndroidTest", sections["android-variants"])
        self.assertIn("verify-release-fail-closed.sh", sections["android-release"])
        security = (ROOT / ".github/workflows/security.yml").read_text()
        self.assertNotIn("verification:no-e2e", security)


if __name__ == "__main__":
    unittest.main()
