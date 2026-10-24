"""The session journal has its own complete, honest five-locale resource group."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
KEYS = {
    "entry", "title", "scope", "integrity", "copies", "provider_warning",
    "empty", "clear", "export", "close", "kind_verify", "completed",
    "cancelled", "failed", "row", "choosing", "writing", "exported",
    "export_cancelled", "export_failed",
}


class VerificationJournalResourcesTest(unittest.TestCase):
    def test_dedicated_group_is_complete_in_spanish_english_catalan_galician_and_basque(self):
        for folder in ("values", "values-en", "values-ca", "values-gl", "values-eu"):
            with self.subTest(locale=folder):
                path = RES / folder / "verification_journal_strings.xml"
                self.assertTrue(path.is_file(), f"Missing dedicated journal resources: {folder}")
                nodes = list(ET.parse(path).getroot())
                self.assertEqual(len(KEYS), len(nodes))
                self.assertEqual({f"verification_journal_{key}" for key in KEYS}, {node.get("name") for node in nodes})
                self.assertTrue(all(node.tag == "string" and node.text and node.text.strip() for node in nodes))
                row = next(node.text for node in nodes if node.get("name") == "verification_journal_row")
                for placeholder in ("%1$d", "%2$s", "%3$s", "%4$s"):
                    self.assertEqual(1, row.count(placeholder))

    def test_english_states_session_integrity_and_external_copy_limits(self):
        path = RES / "values-en/verification_journal_strings.xml"
        self.assertTrue(path.is_file())
        text = {node.get("name"): node.text for node in ET.parse(path).getroot()}
        scope = text["verification_journal_scope"].lower()
        for phrase in ("cms", "this session", "not a complete signing history", "recreated", "closed"):
            self.assertIn(phrase, scope)
        integrity = text["verification_journal_integrity"].lower()
        for phrase in ("integrity only", "trust", "remote acceptance"):
            self.assertIn(phrase, integrity)
        self.assertIn("cannot be revoked", text["verification_journal_copies"].lower())
        self.assertIn("partial", text["verification_journal_provider_warning"].lower())
        self.assertIn("already written", text["verification_journal_provider_warning"].lower())
        self.assertIn("partial", text["verification_journal_export_failed"].lower())


if __name__ == "__main__":
    unittest.main()
