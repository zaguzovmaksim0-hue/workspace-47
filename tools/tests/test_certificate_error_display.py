"""Byte guards for the bounded mapping-only change; executable enum behavior is tested in Kotlin/JUnit."""

from hashlib import sha256
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/java/dev/junta/firmamobile/ui'


class CertificateErrorDisplayScopeTest(unittest.TestCase):
    def test_view_model_is_byte_equal_outside_exact_mapping_seam(self):
        source = (UI / 'CertificateViewModel.kt').read_bytes()
        begin = b'    private fun CertificateErrorCode.toUiError()'
        end = b'\n    class Factory('
        self.assertEqual(1, source.count(begin))
        prefix, remainder = source.split(begin, 1)
        mapping, suffix = remainder.split(end, 1)
        self.assertEqual(b' = CertificateErrorPresentation.forCode(this)\n', mapping)
        # Base 54bf22e51225622ba4b9252cd781f8168958b8b3, excluding only this exact mapping method.
        self.assertEqual(
            'b86795de0426f449e8697d819b4330f3545c3ebb77829b537e7496b0f6579555',
            sha256(prefix + end + suffix).hexdigest(),
        )

    def test_ui_state_has_only_the_approved_enum_relocation(self):
        self.assertEqual(
            '5c1b393a272485e6526339d67bf022caf8910c1fa67f33b2593895baa94d4f96',
            sha256((UI / 'CertificateUiState.kt').read_bytes()).hexdigest(),
        )

    def test_real_display_enum_keeps_package_visibility_values_and_order(self):
        source = (UI / 'CertificateErrorPresentation.kt').read_bytes()
        self.assertTrue(source.startswith(b'package dev.junta.firmamobile.ui\n'))
        declaration = b'\nenum class CertificateUiError {'
        self.assertEqual(1, source.count(declaration))
        _, body = source.split(declaration, 1)
        self.assertEqual(
            'dc98c3b9354b1f4e5a7fb2a3d4e21e96071cfc74e00de2a5a528e51500e13047',
            sha256(declaration[1:] + body).hexdigest(),
        )

    def test_only_targeted_view_model_baseline_signature_is_removed(self):
        baseline = ET.parse(ROOT / 'config/detekt/baseline.xml').getroot()
        ids = {node.text for node in baseline.findall('./CurrentIssues/ID')}
        actual = {item for item in ids if 'CertificateViewModel.kt$' in item}
        self.assertEqual({
            'MagicNumber:CertificateViewModel.kt$CertificateViewModel$24',
            'ReturnCount:CertificateViewModel.kt$CertificateViewModel$fun onAppBackgrounded()',
            'ReturnCount:CertificateViewModel.kt$CertificateViewModel$fun onAppForegrounded()',
            'ReturnCount:CertificateViewModel.kt$CertificateViewModel$private suspend fun '
            'restore(reference: StoredCertificateReference): CertificateUiState',
            'TooManyFunctions:CertificateViewModel.kt$CertificateViewModel : ViewModel',
        }, actual)


    def test_actual_detekt_xml_records_only_target_removal(self):
        fixtures = ROOT / 'tools/tests/fixtures/certificate_error_display'

        def findings(name):
            root = ET.parse(fixtures / name).getroot()
            return [
                (Path(file.attrib['name']).name, error.attrib['source'], error.attrib['message'])
                for file in root.findall('file') for error in file.findall('error')
            ]

        before = findings('detekt-before.xml')
        after = findings('detekt-after.xml')
        target = [item for item in before if item[1] == 'detekt.CyclomaticComplexMethod'
                  and 'toUiError' in item[2]]
        self.assertEqual(1, len(target))
        self.assertIn('complexity: 15', target[0][2])
        self.assertEqual(5, len(before))
        self.assertEqual(4, len(after))
        self.assertCountEqual([item for item in before if item not in target], after)
        self.assertNotIn('CertificateErrorPresentation.kt', {item[0] for item in after})
        self.assertEqual([], findings('detekt-after-baseline.xml'))


if __name__ == '__main__':
    unittest.main()
