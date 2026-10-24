"""PDF preview strings exist in every shipped locale."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"


class PdfPreviewPolicyTest(unittest.TestCase):
    def test_all_five_locales_have_preview_and_accessibility_strings(self):
        keys = {"pdf_preview_show", "pdf_preview_loading", "pdf_preview_failed",
                "pdf_preview_unavailable", "pdf_preview_pages", "pdf_preview_description"}
        for locale in ("values", "values-en", "values-ca", "values-gl", "values-eu"):
            strings = {e.attrib.get("name"): e.text for e in ET.parse(MAIN / f"res/{locale}/strings.xml").getroot()}
            self.assertTrue(keys <= strings.keys(), locale)
            self.assertTrue(all(strings[k] for k in keys), locale)
            self.assertIn("%1$d", strings["pdf_preview_pages"])


if __name__ == "__main__":
    unittest.main()
