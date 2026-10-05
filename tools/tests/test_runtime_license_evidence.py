import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from tools.collect_runtime_license_evidence import archive_notices, inventory, locked_modules


def jar(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return output.getvalue()


class RuntimeLicenseEvidenceTest(unittest.TestCase):
    def test_configuration_is_exact_and_paths_are_rejected(self):
        text = 'a:b:1=releaseRuntimeClasspath\nc:d:2=optimizedRuntimeClasspath\n'
        self.assertEqual(['c:d:2'], locked_modules(text, 'optimizedRuntimeClasspath'))
        with self.assertRaises(ValueError):
            locked_modules('../a:b:1=optimizedRuntimeClasspath', 'optimizedRuntimeClasspath')

    def test_nested_notices_preserved_without_archive_path_extraction(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            raw = jar({'../../NOTICE': b'outer', 'classes.jar': jar({'META-INF/LICENSE': b'inner', 'org/License.class': b'not a notice'})})
            notices = archive_notices(raw, output)
            self.assertEqual(2, len(notices))
            self.assertEqual({b'outer', b'inner'}, {p.read_bytes() for p in output.iterdir()})
            self.assertTrue(all(p.name.endswith('.txt') and len(p.stem) == 64 for p in output.iterdir()))

    def test_inventory_records_missing_metadata_without_claiming_approval(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            lock = root / 'lock'; lock.write_text('a:b:1=optimizedRuntimeClasspath\n')
            report = inventory(lock, root / 'cache', 'optimizedRuntimeClasspath', root / 'out')
            self.assertFalse(report['release_approved'])
            self.assertFalse(report['modules'][0]['cached_evidence_present'])
            self.assertEqual(report, json.loads((root / 'out/inventory.json').read_text()))

    def test_pom_license_supports_namespaced_and_plain_xml(self):
        for namespace in ('', ' xmlns="http://maven.apache.org/POM/4.0.0"'):
            with self.subTest(namespace=namespace), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                lock = root / 'lock'; lock.write_text('a:b:1=optimizedRuntimeClasspath\n')
                module = root / 'cache/a/b/1/hash'; module.mkdir(parents=True)
                (module / 'b-1.pom').write_text(
                    f'<project{namespace}><licenses><license><name>MIT</name>'
                    '<url>https://example.test/license</url></license></licenses></project>')
                report = inventory(lock, root / 'cache', 'optimizedRuntimeClasspath', root / 'out')
                self.assertEqual('MIT', report['modules'][0]['poms'][0]['licenses'][0]['name'])

    def test_project_license_and_notices_match_approved_holder(self):
        root = Path(__file__).resolve().parents[2]
        license_text = (root / 'LICENSE').read_text()
        self.assertIn('Apache License', license_text)
        self.assertIn('Version 2.0, January 2004', license_text)
        self.assertIn('Copyright 2026 Maksim Zaguzov', license_text)
        self.assertNotIn('2026 Kai', license_text)
        notice = (root / 'NOTICE').read_text()
        self.assertIn('Dharma Type', notice)
        self.assertIn('SIL Open Font License', notice)
        self.assertIn('Copyright 2026 Maksim Zaguzov', notice)
