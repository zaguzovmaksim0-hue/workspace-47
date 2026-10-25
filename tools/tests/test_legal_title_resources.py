"""Bundled legal navigation titles have resources in every app language."""

import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
LOCALES = ("values", "values-en", "values-ca", "values-gl", "values-eu")


class LegalTitleResourcesTest(unittest.TestCase):
    def test_static_legal_titles_have_complete_translations(self):
        documents = json.loads((ROOT / "app/src/main/assets/legal/documents.json").read_text())
        titles = [item["title"] for item in documents if not item["asset"].startswith("legal/dependencies/")]
        reference = None
        for locale in LOCALES:
            with self.subTest(locale=locale):
                nodes = list(ET.parse(ROOT / "app/src/main/res" / locale / "legal_title_strings.xml").getroot())
                strings = {node.get("name"): node.text for node in nodes}
                self.assertEqual(len(titles), len(nodes))
                self.assertEqual(len(nodes), len(strings))
                self.assertTrue(all(value and value.strip() for value in strings.values()))
                self.assertFalse(any("%" in value for value in strings.values()))
                if locale == "values":
                    self.assertCountEqual(titles, strings.values())
                    reference = strings.keys()
                self.assertEqual(reference, strings.keys())
                self.assertIn("Apache", strings["legal_title_apache"])
                self.assertIn("PDFBox Android", strings["legal_title_pdfbox_license"])


if __name__ == "__main__":
    unittest.main()
