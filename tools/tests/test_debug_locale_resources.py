import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


class DebugLocaleResourcesTest(unittest.TestCase):
    def test_debug_translation_resources(self):
        debug_res = Path(__file__).resolve().parents[2] / "app/src/debug/res"
        translations = {
            "values": {
                "browser_probe_details": "Detalles",
                "browser_probe_hide_details": "Ocultar detalles",
            },
            "values-en": {
                "browser_probe_details": "Details",
                "browser_probe_hide_details": "Hide details",
            },
            "values-ca": {
                "browser_probe_details": "Detalls",
                "browser_probe_hide_details": "Amaga els detalls",
            },
            "values-gl": {
                "browser_probe_details": "Detalles",
                "browser_probe_hide_details": "Agochar os detalles",
            },
            "values-eu": {
                "browser_probe_details": "Xehetasunak",
                "browser_probe_hide_details": "Ezkutatu xehetasunak",
            },
        }

        for directory, expected in translations.items():
            with self.subTest(locale=directory):
                root = ET.parse(debug_res / directory / "strings.xml").getroot()
                self.assertEqual(root.tag, "resources")
                entries = list(root)
                self.assertCountEqual(
                    [entry.get("name") for entry in entries], expected.keys()
                )
                for entry in entries:
                    name = entry.get("name")
                    with self.subTest(key=name):
                        self.assertEqual(entry.tag, "string")
                        self.assertEqual(entry.attrib, {"name": name})
                        self.assertEqual(list(entry), [])
                        self.assertEqual(entry.text, expected[name])


if __name__ == "__main__":
    unittest.main()
