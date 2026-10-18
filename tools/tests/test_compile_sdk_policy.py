"""Compile-only API37 migration must preserve runtime support and CI consistency."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CompileSdkPolicyTest(unittest.TestCase):
    def test_compile_only_migration_keeps_target_and_minimum(self):
        text = (ROOT / 'app/build.gradle.kts').read_text()
        for name, value in [('compileSdk', 37), ('targetSdk', 36), ('minSdk', 26)]:
            self.assertRegex(text, rf'\b{name}\s*=\s*{value}\b')

    def test_each_build_workflow_installs_compile_platform(self):
        for name in ['ci.yml', 'security.yml', 'daily-apk.yml']:
            text = (ROOT / '.github/workflows' / name).read_text()
            installs = re.findall(r'^.*sdkmanager.*platforms;android-.*$', text, re.M)
            self.assertTrue(installs, name)
            for command in installs:
                self.assertIn('platforms;android-37.0', command, name)

    def test_okhttp_catalog_and_locked_runtime_stay_together(self):
        text = (ROOT / 'gradle/libs.versions.toml').read_text()
        version = re.search(r'^okhttp = "([^"]+)"', text, re.M).group(1)
        lock = (ROOT / 'app/gradle.lockfile').read_text()
        for module in ['okhttp', 'okhttp-android']:
            self.assertIn(f'com.squareup.okhttp3:{module}:{version}=', lock)
