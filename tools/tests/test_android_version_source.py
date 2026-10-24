"""Run the build's actual version validator without configuring Android/Gradle."""
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AndroidVersionSourceTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('kotlinc'), 'Standalone Kotlin compiler unavailable')
    def test_build_validator_rejects_decreased_missing_and_malformed_versions(self):
        source = (ROOT / 'app/build.gradle.kts').read_text()
        validator = re.search(r'fun parseAndroidVersion\(.*?\n\}', source, re.S)
        self.assertIsNotNone(validator, 'Build must validate before assigning Android versions')
        valid = 'versionCode=13\nversionName=0.2.11\n'
        invalid = [valid.replace('versionCode=13', 'versionCode=' + code)
                   for code in ['12', '0', '-1', '013', '+13', '13.0', '١٣',
                                '2100000001', '999999999999999999999999']]
        invalid += ['', 'versionCode=13\n', valid + 'versionCode=14\n',
                    valid.replace('0.2.11', '0.2.011'), valid + 'unknown=value\n',
                    valid.rstrip('\n') + '\r']
        checks = [f'check(parseAndroidVersion({json.dumps(valid)}) == (13 to "0.2.11"))',
                  'check(parseAndroidVersion("versionCode=14\\nversionName=0.2.12\\n") == (14 to "0.2.12"))',
                  'check(parseAndroidVersion("versionCode=2100000000\\r\\nversionName=1.0.0\\r\\n") == (2100000000 to "1.0.0"))']
        checks += [f'check(runCatching {{ parseAndroidVersion({json.dumps(text)}) }}.isFailure)'
                   for text in invalid]
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / 'version-check.kts'
            script.write_text(validator[0] + '\n' + '\n'.join(checks) + '\nprintln("version validator OK")\n')
            result = subprocess.run(['kotlinc', '-script', str(script)], capture_output=True,
                                    text=True, timeout=60)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('version validator OK', result.stdout)
