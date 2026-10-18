"""Bind the reviewed stable WebKit candidate to locks and artifact evidence."""
from pathlib import Path
import json
import re
import tomllib
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
VERSION = '1.17.1'
COORDINATE = f'androidx.webkit:webkit:{VERSION}'
HASHES = {
    'aar': '29dc7a9a32614828c97cf85951eb43c9e455157ab411e5e77d2ca4e72da28a36',
    'module': 'fee069430a913a43fa694ad466682f132d21251b62cd03655142ddafe15b52eb',
    'pom': 'b5294d03f04cf806e975afe48a67e70180e6a1e74086e7e62a1fe64c4a636bae',
}
CONFIGURATIONS = {f'{variant}RuntimeClasspath' for variant in ['debug', 'qa', 'optimized', 'release']}


def validate_runtime_rows(locked):
    rows = re.findall(r'^androidx\.webkit:webkit:([^=]+)=(.*)$', locked, re.M)
    if not rows:
        raise ValueError('No WebKit runtime rows')
    covered = set()
    for version, configurations in rows:
        if version != VERSION:
            raise ValueError('Unreviewed WebKit version: ' + version)
        covered.update(configurations.split(','))
    if not CONFIGURATIONS.issubset(covered):
        raise ValueError('Incomplete WebKit runtime configurations')


class WebKitDependencyPolicyTest(unittest.TestCase):
    def test_stable_catalog_and_all_runtime_locks_match_reviewed_version(self):
        versions = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())['versions']
        self.assertEqual(VERSION, versions['webkit'])
        validate_runtime_rows((ROOT / 'app/gradle.lockfile').read_text())

    def test_stale_split_rows_and_prereleases_are_rejected(self):
        for bad in ['1.16.0', '1.18.0-rc01']:
            for versions in [(VERSION, bad), (bad, VERSION)]:
                with self.subTest(versions=versions):
                    locked = '\n'.join(f'androidx.webkit:webkit:{v}={c}' for v, c in zip(
                        versions, ['debugRuntimeClasspath', 'releaseRuntimeClasspath']))
                    with self.assertRaisesRegex(ValueError, 'Unreviewed WebKit version'):
                        validate_runtime_rows(locked)

    def test_missing_or_incomplete_lock_coverage_is_rejected(self):
        for locked in ['', COORDINATE + '=debugRuntimeClasspath']:
            with self.subTest(locked=locked), self.assertRaises(ValueError):
                validate_runtime_rows(locked)

    def test_official_artifact_checksums_are_preserved_in_strict_verification(self):
        tree = ET.parse(ROOT / 'gradle/verification-metadata.xml')
        components = tree.findall(f'.//{{*}}component[@group="androidx.webkit"][@name="webkit"][@version="{VERSION}"]')
        self.assertEqual(1, len(components))
        for extension, expected in HASHES.items():
            artifact = components[0].find(f'{{*}}artifact[@name="webkit-{VERSION}.{extension}"]')
            self.assertIsNotNone(artifact, extension)
            self.assertEqual([expected], [entry.get('value') for entry in artifact.findall('{*}sha256')])

    def test_vendored_license_evidence_is_for_the_same_artifacts(self):
        legal = ROOT / 'app/src/main/assets/legal'
        inventory = json.loads((legal / 'dependencies/inventory.json').read_text())
        rows = [row for row in inventory['modules'] if row['coordinate'].startswith('androidx.webkit:webkit:')]
        self.assertEqual(1, len(rows))
        row = rows[0]
        self.assertEqual(COORDINATE, row['coordinate'])
        self.assertFalse(inventory['release_approved'])
        self.assertEqual([(f'webkit-{VERSION}.aar', HASHES['aar'])],
                         [(a['file'], a['sha256']) for a in row['artifacts']])
        self.assertEqual([(f'webkit-{VERSION}.pom', HASHES['pom'])],
                         [(p['file'], p['sha256']) for p in row['poms']])
        self.assertTrue(row['artifacts'][0]['notices'])
