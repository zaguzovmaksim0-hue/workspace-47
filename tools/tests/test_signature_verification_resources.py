"""Offline PDF verification has complete localized scope and result labels."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"
LOCALES = ("values", "values-en", "values-ca", "values-gl", "values-eu")


class SignatureVerificationResourcesTest(unittest.TestCase):
    def test_all_locales_have_same_keys_and_format_arguments(self):
        reference = None
        for locale in LOCALES:
            with self.subTest(locale=locale):
                path = RES / locale / "signature_verification_strings.xml"
                self.assertTrue(path.is_file())
                nodes = list(ET.parse(path).getroot())
                strings = {node.get("name"): node.text for node in nodes}
                self.assertEqual(len(nodes), len(strings))
                self.assertTrue(all(value and value.strip() for value in strings.values()))
                arguments = {key: sorted(re.findall(r"%\d+\$[ds]", value)) for key, value in strings.items()}
                if reference is None:
                    reference = arguments
                self.assertEqual(reference, arguments)
                self.assertEqual(["%1$d", "%2$s"], arguments["signature_verification_signer"])
                for phrase in ("2 MiB", "1 MiB", "512 KiB"):
                    self.assertIn(phrase, strings["signature_verification_limits"])
                self.assertIn("OCSP/CRL", strings["signature_verification_scope"])
                self.assertIn("XML/XAdES", strings["signature_verification_scope"])

    def test_english_explains_revision_and_trust_boundaries(self):
        strings = {node.get("name"): node.text.lower() for node in ET.parse(
            RES / "values-en/signature_verification_strings.xml").getroot()}
        for phrase in ("revocation not checked", "not identity", "timestamps are not validated"):
            self.assertIn(phrase, strings["signature_verification_scope"])
        self.assertIn("not trusted", strings["signature_verification_date"])
        self.assertIn("current pdf", strings["signature_verification_unsigned_changes"])
        self.assertIn("only its signed revision", strings["signature_verification_later_changes"])

    def test_journal_describes_pdf_as_well_as_cms(self):
        for locale in LOCALES:
            strings = {node.get("name"): node.text for node in ET.parse(
                RES / locale / "verification_journal_strings.xml").getroot()}
            for key in ("scope", "integrity", "kind_verify"):
                with self.subTest(locale=locale, key=key):
                    self.assertIn("CMS/PDF", strings[f"verification_journal_{key}"])


if __name__ == "__main__":
    unittest.main()
