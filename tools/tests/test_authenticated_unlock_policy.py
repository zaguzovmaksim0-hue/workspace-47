"""Source boundaries complement Kotlin cache and view-model behavior tests."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
class AuthenticatedUnlockPolicyTest(unittest.TestCase):
    def test_keystore_requires_per_operation_authentication_and_new_alias(self):
        text = (ROOT/'app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt').read_text()
        self.assertIn('.setUserAuthenticationRequired(true)', text)
        self.assertIn('.setUserAuthenticationParameters(0,', text)
        self.assertIn('certificate_unlock_aes_v2', text)
        self.assertNotIn('.setUserAuthenticationRequired(false)', text)
    def test_cipher_authorization_is_at_both_crypto_boundaries(self):
        text = (ROOT/'app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt').read_text()
        self.assertEqual(2, text.count('keyProvider.authenticateCipher(cipher)'))
    def test_native_prompt_binds_exact_cipher_and_strong_or_device_credential(self):
        text = (ROOT/'app/src/main/java/dev/junta/firmamobile/certificate/AndroidCertificateUnlockAuthentication.kt').read_text()
        self.assertIn('BiometricPrompt.CryptoObject(cipher)', text)
        self.assertIn('BIOMETRIC_STRONG', text)
        self.assertIn('DEVICE_CREDENTIAL', text)
    def test_explicit_restore_is_wired_at_both_activity_entry_points(self):
        text = (ROOT/'app/src/main/java/dev/junta/firmamobile/MainActivity.kt').read_text()
        self.assertEqual(2, text.count('onRestoreSavedUnlock = certificateViewModel::restoreSavedUnlock'))
