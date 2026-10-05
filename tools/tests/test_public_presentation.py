from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

class PublicPresentationTest(unittest.TestCase):
    def test_visible_brand_is_consistent_and_package_is_preserved(self):
        values = {n.attrib.get('name'): n.text or '' for n in ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()}
        self.assertEqual('Firma Mobile', values['app_name'])
        self.assertEqual('Firma Mobile', values['launcher_name'])
        self.assertFalse(any('Junta Firma' in text or 'FirmaMobile' in text for text in values.values()))
        build = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertIn('applicationId = "dev.junta.firmamobile"', build)
        block = build.split('buildTypes.create("optimized") {', 1)[1].split('\n    }', 1)[0]
        self.assertIn('versionNameSuffix = ""', block)
        self.assertIn('isDebuggable = false', block)

    def test_spanish_entry_points_and_english_readme_exist(self):
        readme = (ROOT / 'README.md').read_text()
        for value in ('# Firma Mobile', 'Proyecto independiente y no oficial', 'README.en.md', 'docs/instalacion.md', 'CHANGELOG.md'):
            self.assertIn(value, readme)
        for path in ('README.en.md', 'docs/instalacion.md', 'CHANGELOG.md'):
            self.assertTrue((ROOT / path).is_file())
