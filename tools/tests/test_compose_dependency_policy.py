"""Keep the reviewed Compose BOM and its runtime family aligned."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ComposeDependencyPolicyTest(unittest.TestCase):
    def test_reviewed_bom_and_runtime_family_are_aligned(self):
        catalog = (ROOT / 'gradle/libs.versions.toml').read_text()
        self.assertRegex(catalog, r'(?m)^compose-bom = "2026\.09\.00"$')
        locked = (ROOT / 'app/gradle.lockfile').read_text()
        modules = dict(re.findall(r'^(androidx\.compose[^:]*:[^:]+):([^=]+)=', locked, re.M))
        self.assertTrue(modules)
        for coordinate, version in modules.items():
            if coordinate == 'androidx.compose:compose-bom':
                expected = '2026.09.00'
            elif coordinate.startswith('androidx.compose.material3:'):
                expected = '1.4.0'
            else:
                expected = '1.12.1'
            self.assertEqual(version, expected, coordinate)

    def test_compose_core_runtime_has_android_artifacts(self):
        locked = (ROOT / 'app/gradle.lockfile').read_text()
        for group, name in [('ui', 'ui'), ('foundation', 'foundation'), ('runtime', 'runtime')]:
            self.assertIn(f'androidx.compose.{group}:{name}-android:1.12.1=', locked)
