"""Contract tests for the operating-system app-language declaration."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class AppLocaleConfigTest(unittest.TestCase):
    def test_application_references_locale_config(self):
        application = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')
        self.assertEqual('@xml/locales_config', application.get(ANDROID + 'localeConfig'))

    def test_supported_locales_are_unique_and_explicit(self):
        root = ET.parse(ROOT / 'app/src/main/res/xml/locales_config.xml').getroot()
        self.assertEqual('locale-config', root.tag)
        names = [locale.get(ANDROID + 'name') for locale in root]
        self.assertEqual(['es', 'en', 'ca', 'gl', 'eu'], names)
        self.assertEqual(len(names), len(set(names)))
        self.assertTrue(all(locale.tag == 'locale' for locale in root))

    def test_locale_change_uses_activity_recreation(self):
        application = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')
        main = next(activity for activity in application.findall('activity')
                    if activity.get(ANDROID + 'name') == '.MainActivity')
        handled = main.get(ANDROID + 'configChanges', '').split('|')
        self.assertNotIn('locale', handled)
        self.assertNotIn('layoutDirection', handled)


if __name__ == '__main__':
    unittest.main()
