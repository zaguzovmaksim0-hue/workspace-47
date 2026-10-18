"""Keep the compiler update's runtime lock and reviewed evidence coherent."""
from pathlib import Path
import hashlib
import json
import re
import tomllib
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


class ComposeCompilerPolicyTest(unittest.TestCase):
    def setUp(self):
        catalog = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())
        self.compiler = catalog['versions']['compose-compiler']
        self.lock = (ROOT / 'app/gradle.lockfile').read_bytes()

    def test_stdlib_follows_compiler_in_every_runtime_configuration(self):
        rows = re.findall(r'^org\.jetbrains\.kotlin:kotlin-stdlib:([^=]+)=(.+)$',
                          self.lock.decode(), re.M)
        self.assertTrue(rows)
        configurations = set()
        for version, configured in rows:
            self.assertEqual(self.compiler, version)
            configurations.update(configured.split(','))
        self.assertEqual({'debugRuntimeClasspath', 'qaRuntimeClasspath',
                          'optimizedRuntimeClasspath', 'releaseRuntimeClasspath'}, configurations)

    def test_vendored_runtime_evidence_matches_compiler_and_lock(self):
        inventory = json.loads((ROOT / 'app/src/main/assets/legal/dependencies/inventory.json').read_text())
        self.assertEqual(hashlib.sha256(self.lock).hexdigest(), inventory['lock_sha256'])
        versions = [row['coordinate'].split(':')[2] for row in inventory['modules']
                    if row['coordinate'].startswith('org.jetbrains.kotlin:kotlin-stdlib:')]
        self.assertEqual([self.compiler], versions)

    def test_plugin_marker_has_reviewed_sha256(self):
        tree = ET.parse(ROOT / 'gradle/verification-metadata.xml')
        ns = {'v': 'https://schema.gradle.org/dependency-verification'}
        marker = tree.find(f".//v:component[@group='org.jetbrains.kotlin.plugin.compose']"
                           f"[@name='org.jetbrains.kotlin.plugin.compose.gradle.plugin']"
                           f"[@version='{self.compiler}']", ns)
        self.assertIsNotNone(marker)
        artifact = marker.find(f"v:artifact[@name='org.jetbrains.kotlin.plugin.compose.gradle.plugin-{self.compiler}.pom']", ns)
        self.assertIsNotNone(artifact)
        self.assertTrue(artifact.findall('v:sha256', ns))
