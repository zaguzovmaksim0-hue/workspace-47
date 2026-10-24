"""Ensure Android production wiring enforces the device-unlocked API floor."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/java/dev/junta/firmamobile/certificate/CertificateUnlockCache.kt'


class UnlockPlatformPolicyTest(unittest.TestCase):
    def test_production_cache_supplies_runtime_platform_policy(self):
        source = SOURCE.read_text()
        android_cache = source.split('class AndroidKeystoreCertificateUnlockCache(', 1)[1]
        self.assertIn('persistentCacheAllowed = supportsPersistentCertificateUnlock(android.os.Build.VERSION.SDK_INT)', android_cache)

    def test_both_cache_entry_points_reject_unsupported_persistence_before_io(self):
        source = SOURCE.read_text().split('class EncryptedCertificateUnlockCache internal constructor(', 1)[1]
        for method in ('store', 'restore'):
            body = source.split(f'override suspend fun {method}(', 1)[1].split('        } else {', 1)[0]
            self.assertIn('if (!persistentCacheAllowed)', body)
            self.assertIn('clearAndReport()', body)

    def test_threat_model_scopes_persistent_restore_to_api30_and_above(self):
        text = (ROOT / 'docs/threat-model.md').read_text()
        self.assertIn('solo en API 30+', text)
        self.assertIn('en API 26–29 la persistencia del desbloqueo está\ndeshabilitada', text)
        self.assertIn('Si la limpieza falla, la restauración\nsigue denegada', text)
        self.assertIn('requieren confirmación nativa con biometría/PIN', text)


if __name__ == '__main__':
    unittest.main()
