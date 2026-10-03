package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.*
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.math.BigInteger
import java.net.URLEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCertificateBindingInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val now = Instant.parse("2030-01-02T03:04:05Z")

    @Test fun androidBindsTheFullCertificateEvenWhenAnotherCertificateUsesTheSameKey() = runBlocking<Unit> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encodedReads = AtomicInteger(); val expected = identity(pair, 1, encodedReads); val another = identity(pair, 2, encodedReads)
        assertArrayEquals(expected.certificate.publicKey.encoded, another.certificate.publicKey.encoded)
        assertFalse(expected.certificate.encoded.contentEquals(another.certificate.encoded))
        var sent = 0; var approved = 0
        val operation = NativeAfirmaOperation(invocation(expected), AfirmaResultTransport { _, _, result ->
            assertEquals(1, approved); sent++
            assertArrayEquals(expected.certificate.encoded, AfirmaIntermediateCipher.decode(result, null))
            AfirmaDeliveryResult.ACKNOWLEDGED
        }, Clock.fixed(now, ZoneOffset.UTC))
        try {
            assertTrue(operation.certificateCompatible(expected)); assertFalse(operation.certificateCompatible(another))
            assertEquals(0, sent)
            assertEquals(AfirmaDeliveryResult.ACKNOWLEDGED, operation.execute(expected) { approved++ })
            assertEquals(1, sent); assertEquals(0, encodedReads.get())
        } finally { operation.close() }
    }

    @Test fun exactCertificateAndHeadlessRequestStillShowsUnlockWithoutAutomaticConfirmation() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encodedReads = AtomicInteger(); val expected = identity(pair, 3, encodedReads)
        var unlocks = 0; var confirmations = 0; var sends = 0
        val operation = NativeAfirmaOperation(invocation(expected), AfirmaResultTransport { _, _, _ -> sends++; error("No network in UI fixture") }, Clock.fixed(now, ZoneOffset.UTC))
        try {
            val prompt = AfirmaConsentPrompt(UUID.randomUUID(), operation.details, AfirmaConsentPhase.REVIEW, null, AfirmaConsentProblem.LOCKED)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    NativeAfirmaConsentDialog(prompt, onConfirm = { confirmations++ }, onUnlock = { unlocks++ }, onCancel = {}, onDismiss = {})
                } }
                rule.onNodeWithTag("native-afirma-exact-certificate").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
                rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
                rule.runOnIdle { assertEquals(1, unlocks); assertEquals(0, confirmations); assertEquals(0, sends); assertEquals(0, encodedReads.get()) }
            }
        } finally { operation.close() }
    }

    private fun invocation(identity: UnlockedIdentity): AfirmaServletInvocation {
        val properties = "filters=encodedcert:" + Base64.getEncoder().encodeToString(identity.certificate.encoded) + "\nheadless=true"
        val raw = "afirma://selectcert?id=BoundUi123&stservlet=https%3A%2F%2Fstorage.synthetic.example%2Fput&properties=" +
            URLEncoder.encode(Base64.getUrlEncoder().encodeToString(properties.toByteArray()), "UTF-8")
        val parsed = AfirmaServletInvocationParser.parse(raw, "https://portal.synthetic.example/form")
        assertTrue(parsed.toString(), parsed is AfirmaServletParseResult.Accepted)
        return (parsed as AfirmaServletParseResult.Accepted).invocation
    }
    private fun identity(pair: KeyPair, serial: Long, reads: AtomicInteger): UnlockedIdentity {
        val name = X500Name("CN=Synthetic exact certificate")
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(serial), Date.from(now.minusSeconds(600)),
            Date.from(now.plusSeconds(3600)), name, pair.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(pair.private))
        val certificate = JcaX509CertificateConverter().getCertificate(holder); val source = pair.private as RSAPrivateKey
        val key = object : RSAPrivateKey {
            override fun getAlgorithm() = "RSA"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray { reads.incrementAndGet(); error("No key export") }
            override fun getModulus() = source.modulus
            override fun getPrivateExponent() = source.privateExponent
        }
        return UnlockedIdentity(key, certificate, listOf(certificate), CertificateSummary("Synthetic only", "Synthetic only", now.minusSeconds(600), now.plusSeconds(3600)))
    }
}
