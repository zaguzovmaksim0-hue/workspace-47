"""Keep the reviewed Compose BOM and its runtime family aligned."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


def validate_reviewed_runtime_rows(locked):
    modules = re.findall(r'^(androidx\.compose[^:]*:[^:]+):([^=]+)=', locked, re.M)
    if not modules:
        raise ValueError('No Compose runtime rows')
    for coordinate, version in modules:
        if coordinate == 'androidx.compose:compose-bom':
            expected = '2026.09.00'
        elif coordinate.startswith('androidx.compose.material3:'):
            expected = '1.4.0'
        else:
            expected = '1.12.1'
        if version != expected:
            raise ValueError(f'{coordinate}: expected {expected}, got {version}')


class ComposeDependencyPolicyTest(unittest.TestCase):
    def test_reviewed_bom_and_runtime_family_are_aligned(self):
        catalog = (ROOT / 'gradle/libs.versions.toml').read_text()
        self.assertRegex(catalog, r'(?m)^compose-bom = "2026\.09\.00"$')
        validate_reviewed_runtime_rows((ROOT / 'app/gradle.lockfile').read_text())

    def test_split_runtime_versions_cannot_hide_stale_configuration(self):
        for versions in [('1.11.3', '1.12.1'), ('1.12.1', '1.11.3')]:
            with self.subTest(versions=versions):
                locked = '\n'.join(
                    f'androidx.compose.ui:ui-android:{version}={configuration}'
                    for version, configuration in zip(versions, ['releaseRuntimeClasspath', 'debugRuntimeClasspath'])
                )
                with self.assertRaisesRegex(ValueError, 'ui-android: expected 1.12.1, got 1.11.3'):
                    validate_reviewed_runtime_rows(locked)

    def test_empty_runtime_rows_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'No Compose runtime rows'):
            validate_reviewed_runtime_rows('# empty lock\n')

    def test_compose_core_runtime_has_android_artifacts(self):
        locked = (ROOT / 'app/gradle.lockfile').read_text()
        for group, name in [('ui', 'ui'), ('foundation', 'foundation'), ('runtime', 'runtime')]:
            self.assertIn(f'androidx.compose.{group}:{name}-android:1.12.1=', locked)
