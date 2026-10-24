"""Source-wiring contracts only; these are not Android runtime acceptance."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/dev/junta/firmamobile"


class AppContainerCompositionTest(unittest.TestCase):
    def test_application_creates_one_eager_certificate_graph_at_the_same_point(self):
        container = (MAIN / "AppContainer.kt").read_text()
        application = (MAIN / "JuntaFirmaApplication.kt").read_text()
        self.assertIn(
            "val certificateModule: CertificateModule = certificateModuleFactory()",
            container,
        )
        self.assertNotIn("val certificateModuleFactory", container)
        self.assertEqual(1, container.count("CertificateModule.create(application)"))
        self.assertEqual(1, application.count("AppContainer.create(this)"))
        expected_order = [
            "clientCertPreferenceCoordinator = ClientCertPreferenceCoordinator()",
            "appContainer = AppContainer.create(this)",
            "val certificateModule = appContainer.certificateModule",
            "certificateSession = certificateModule.session",
            "certificateUnlockCache = certificateModule.unlockCache",
            "certificateGateway = certificateModule.gateway",
        ]
        positions = [application.index(item) for item in expected_order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("internal lateinit var appContainer: AppContainer\n        private set", application)


    def test_signing_uses_current_application_session_and_request_scope(self):
        container = (MAIN / "AppContainer.kt").read_text()
        activity = (MAIN / "MainActivity.kt").read_text()
        marker = "val signingModule = app.appContainer.createSigningModule("
        self.assertEqual(1, activity.count(marker))
        arguments = activity.split(marker, 1)[1].split("\n        )", 1)[0]
        self.assertIn("certificateSession = app.certificateSession", arguments)
        self.assertIn("scope = lifecycleScope", arguments)
        self.assertLess(arguments.index("certificateSession ="), arguments.index("scope ="))
        self.assertNotIn("certificateModule.session", arguments)
        self.assertNotIn("val signingModule = SigningModule(", activity)
        self.assertIn("certificateSession: CertificateSession,", container)
        self.assertIn("): SigningModule = SigningModule(certificateSession, scope)", container)

    def test_container_stores_neither_activity_scope_nor_factories(self):
        container = (MAIN / "AppContainer.kt").read_text()
        stored_properties = re.findall(
            r"^\s+(?:private\s+)?(?:val|var)\s+(\w+)", container, re.MULTILINE,
        )
        self.assertEqual(["certificateModule"], stored_properties)
        self.assertNotIn("android.app.Activity", container)
        self.assertNotIn("lifecycleScope", container)
        self.assertNotIn("currentNavigationEpoch", container)
        self.assertNotIn("currentOrigin", container)

    def test_application_mutable_injection_surface_stays_compatible(self):
        application = (MAIN / "JuntaFirmaApplication.kt").read_text()
        for name, kind in [
            ("certificateGateway", "CertificateGateway"),
            ("certificateSession", "CertificateSession"),
            ("certificateUnlockCache", "CertificateUnlockCache"),
            ("sanitizedLogger", "SanitizedLogger"),
            ("clientCertPreferenceCoordinator", "ClientCertPreferenceCoordinator"),
        ]:
            self.assertIn(f"lateinit var {name}: {kind}\n        internal set", application)
        self.assertIn(
            "internal lateinit var secureTunnelRuntime: SecureTunnelRuntime\n        private set",
            application,
        )


if __name__ == "__main__":
    unittest.main()
