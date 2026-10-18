from pathlib import Path
import unittest
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


class DetektPolicyTest(unittest.TestCase):
    def test_cli_is_pinned_and_fail_closed(self):
        script = (ROOT / 'scripts/ci/detekt.sh').read_text()
        self.assertIn('set -euo pipefail', script)
        self.assertIn('DETEKT_VERSION=1.23.8', script)
        self.assertRegex(script, r'DETEKT_SHA256=[0-9a-f]{64}')
        self.assertIn('sha256sum --check --status', script)
        self.assertIn('--max-issues 0', script)
        self.assertIn('--input app/src/main/java', script)
        self.assertNotIn('|| true', script)

    def test_corrupt_cached_jar_fails_before_execution(self):
        with tempfile.TemporaryDirectory() as cache:
            jar = Path(cache) / 'firmamobile/detekt/1.23.8/detekt-cli-1.23.8-all.jar'
            jar.parent.mkdir(parents=True)
            jar.write_text('not a trusted executable')
            result = subprocess.run(
                ['bash', str(ROOT / 'scripts/ci/detekt.sh')],
                env={**os.environ, 'XDG_CACHE_HOME': cache},
                capture_output=True, text=True, check=False, timeout=10,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Cached detekt checksum mismatch', result.stderr)
            self.assertNotIn('Invalid or corrupt jarfile', result.stderr)

    def test_ci_checks_without_regenerating_the_baseline(self):
        workflow = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('name: Kotlin static analysis (detekt)', workflow)
        self.assertIn('run: bash scripts/ci/detekt.sh', workflow)
        self.assertNotIn('--create-baseline', workflow)
        self.assertIn('app/build/reports/detekt/', workflow)

    def test_baseline_is_portable_and_has_no_manual_suppressions(self):
        baseline = ROOT / 'config/detekt/baseline.xml'
        root = ET.parse(baseline).getroot()
        self.assertEqual('SmellBaseline', root.tag)
        self.assertEqual([], root.findall('./ManuallySuppressedIssues/ID'))
        ids = [node.text for node in root.findall('./CurrentIssues/ID')]
        self.assertTrue(ids)
        self.assertEqual(len(ids), len(set(ids)))
        self.assertFalse(any('/data/data/com.termux/' in item for item in ids))


if __name__ == '__main__':
    unittest.main()
