package dev.junta.firmamobile

import android.net.Uri
import android.util.Base64
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.certificate.CertificateDocumentAccess
import dev.junta.firmamobile.certificate.CertificateDocumentMetadata
import dev.junta.firmamobile.certificate.CertificateReferenceStore
import dev.junta.firmamobile.certificate.CertificateRepository
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.CertificateSessionState
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.certificate.StoredCertificateReference
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CertificateSetupFlowTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun wrongPasswordThenUnlockBackgroundRecreateAndManualLockKeepsKeyMemoryOnly() {
        val uri = Uri.parse("content://dev.junta.firmamobile.tests/synthetic-identity.p12")
        val bytes = syntheticPkcs12()
        val reference = StoredCertificateReference(
            uri = uri,
            displayName = "synthetic-identity.p12",
            mimeType = CertificateRepository.MIME_X_PKCS12,
            size = bytes.size.toLong(),
            summary = null,
        )
        val repository = CertificateRepository(
            documentAccess = SyntheticDocumentAccess(uri, bytes),
            referenceStore = MemoryReferenceStore(reference),
            loader = Pkcs12Loader(),
        )
        val session = CertificateSession()

        TestCertificateDependencies.install(repository, session).use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.onNodeWithContentDescription("Contraseña del certificado")
                    .performScrollTo()
                    .assertIsDisplayed()
                assertSecureFlag(scenario, expected = true)

                enterPassword("wrong-password")
                waitForText(
                    "La contraseña no es correcta o el archivo PKCS#12 no es válido.",
                )
                rule.onNodeWithText(
                    "La contraseña no es correcta o el archivo PKCS#12 no es válido.",
                ).performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("wrong-password").assertDoesNotExist()
                assertSecureFlag(scenario, expected = true)

                enterPassword(TEST_PASSPHRASE)
                waitForText("Certificado encontrado")
                rule.onNodeWithText("Certificado encontrado")
                    .performScrollTo()
                    .assertIsDisplayed()
                rule.onNodeWithText("Titular: Persona de Prueba", substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
                rule.runOnIdle {
                    check(session.state() is CertificateSessionState.Unlocked)
                }
                assertSecureFlag(scenario, expected = false)

                scenario.moveToState(Lifecycle.State.CREATED)
                rule.runOnIdle {
                    check(session.state() is CertificateSessionState.Unlocked)
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForText("Certificado encontrado")
                rule.onNodeWithContentDescription("Contraseña del certificado").assertDoesNotExist()
                assertSecureFlag(scenario, expected = false)

                scenario.recreate()
                waitForText("Certificado encontrado")
                rule.onNodeWithContentDescription("Contraseña del certificado").assertDoesNotExist()
                rule.runOnIdle {
                    check(session.state() is CertificateSessionState.Unlocked)
                }
                assertSecureFlag(scenario, expected = false)

                rule.onNodeWithText("Bloquear certificado")
                    .performScrollTo()
                    .performClick()
                rule.onNodeWithContentDescription("Contraseña del certificado")
                    .performScrollTo()
                    .assertIsDisplayed()
                rule.runOnIdle {
                    check(session.state() is CertificateSessionState.Locked)
                }
                assertSecureFlag(scenario, expected = true)
            }
        }
    }

    private fun enterPassword(password: String) {
        rule.onNodeWithContentDescription("Contraseña del certificado")
            .performScrollTo()
            .performTextInput(password)
        rule.onNodeWithText("Desbloquear certificado")
            .performScrollTo()
            .performClick()
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSecureFlag(
        scenario: ActivityScenario<MainActivity>,
        expected: Boolean,
    ) {
        scenario.onActivity { activity ->
            val isSecure = activity.window.attributes.flags and FLAG_SECURE != 0
            check(isSecure == expected) {
                "FLAG_SECURE expected=$expected actual=$isSecure"
            }
        }
    }

    private fun syntheticPkcs12(): ByteArray {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val base64 = testContext.assets.open("synthetic-identity.p12.b64")
            .bufferedReader()
            .use { it.readText() }
        return Base64.decode(base64, Base64.DEFAULT)
    }

    private class SyntheticDocumentAccess(
        private val expectedUri: Uri,
        private val bytes: ByteArray,
    ) : CertificateDocumentAccess {
        override fun queryMetadata(uri: Uri) = CertificateDocumentMetadata(
            displayName = "synthetic-identity.p12",
            mimeType = CertificateRepository.MIME_X_PKCS12,
            size = bytes.size.toLong(),
        )

        override fun takePersistableReadPermission(uri: Uri) = Unit

        override fun releasePersistableReadPermission(uri: Uri) = Unit

        override fun open(uri: Uri): InputStream {
            check(uri == expectedUri)
            return ByteArrayInputStream(bytes)
        }
    }

    private class MemoryReferenceStore(
        private var reference: StoredCertificateReference?,
    ) : CertificateReferenceStore {
        override suspend fun read(): StoredCertificateReference? = reference

        override suspend fun write(reference: StoredCertificateReference) {
            this.reference = reference
        }

        override suspend fun clear() {
            reference = null
        }
    }

    companion object {
        private const val TEST_PASSPHRASE = "test-password-123"
    }
}
