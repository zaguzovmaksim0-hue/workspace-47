"""Contracts for the typed manual-unlock retention path."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class UnlockRetentionPolicyTest(unittest.TestCase):
    def test_view_model_defaults_to_fifteen_minutes(self):
        text = (ROOT / 'app/src/main/java/dev/junta/firmamobile/ui/CertificateViewModel.kt').read_text()
        self.assertIn('private val unlockDuration: Duration = CertificateUnlockRetention.FIFTEEN_MINUTES.duration', text)

    def test_ui_submits_retention_with_password(self):
        text = (ROOT / 'app/src/main/java/dev/junta/firmamobile/ui/AppRoot.kt').read_text()
        self.assertIn('onUnlock: (CharArray, CertificateUnlockRetention) -> Unit', text)
        self.assertIn('onUnlock(password, retention)', text)

    def test_copy_no_longer_promises_unconditional_twenty_four_hours(self):
        import xml.etree.ElementTree as ET
        strings = {el.attrib.get('name'): el.text for el in ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()}
        for key in ('certificate_status_locked_copy', 'certificate_status_unlocked_copy'):
            self.assertNotIn('24 horas', strings[key])


if __name__ == '__main__':
    unittest.main()
