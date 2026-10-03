package dev.junta.firmamobile

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dev.junta.firmamobile.afirma.servlet.*
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual PDFBox, two generated identities and Android PdfRenderer. No personal
 * certificate, real document, network endpoint or live government operation. */
@RunWith(AndroidJUnit4::class)
class NativePdfCoSignInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val now = Instant.parse("2030-01-02T03:04:05Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun twoIndependentApprovalsRemainValidAndRenderTheOriginalPageUnchanged() {
        val reads = AtomicInteger(); val firstKey = identity(1, reads); val nextKey = identity(2, reads)
        val engine = NativePadesEngine(clock = clock); val input = nativeInstrumentedPdf()
        val first = signed(engine, input, firstKey)
        val second = signed(engine, first, nextKey, true)
        try {
            assertArrayEquals(first, second.copyOf(first.size))
            assertTrue(engine.verify(second, first, nextKey.certificate, SigningAlgorithm.SHA256_WITH_RSA))
            PDDocument.load(second).use { doc ->
                val history = checkNotNull(NativePdfSignatureHistory.inspect(second, doc))
                assertEquals(2, history.size)
                assertNotEquals(history[0].certificateSha256, history[1].certificateSha256)
                assertEquals(first.size, history[0].revisionEnd)
                assertEquals(second.size, history[1].revisionEnd)
            }
            val original = render(input); val once = render(first); val twice = render(second)
            try { assertTrue(original.sameAs(once)); assertTrue(original.sameAs(twice)) }
            finally { original.recycle(); once.recycle(); twice.recycle() }
            assertEquals(0, reads.get())
        } finally { first.fill(0); second.fill(0) }
    }

    @Test fun explicitPdfCoSignIsLabelledAndUnlockStillDoesNotApproveTheOperation() {
        val reads = AtomicInteger(); val firstKey = identity(3, reads)
        val first = signed(NativePadesEngine(clock = clock), nativeInstrumentedPdf(), firstKey)
        val request = "afirma://cosign?id=CoUi123&stservlet=https%3A%2F%2Fstore.synthetic.example%2Fput&format=PAdES&algorithm=SHA256withRSA&dat=" +
            Base64.getUrlEncoder().encodeToString(first)
        val parsed = AfirmaServletInvocationParser.parse(request, "https://unprofiled.synthetic.example/form") as AfirmaServletParseResult.Accepted
        var sent = 0; var confirmed = 0; var unlocked = 0
        val operation = NativeAfirmaOperation(parsed.invocation, AfirmaResultTransport { _, _, _ -> sent++; error("No storage in this fixture") })
        try {
            assertEquals("cosign", operation.details.operation)
            val prompt = AfirmaConsentPrompt(UUID.randomUUID(), operation.details, AfirmaConsentPhase.REVIEW, null, AfirmaConsentProblem.LOCKED)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    NativeAfirmaConsentDialog(prompt, onConfirm = { confirmed++ }, onUnlock = { unlocked++ }, onCancel = {}, onDismiss = {})
                } }
                rule.onNodeWithText("Añadir una firma al PDF ya firmado").assertIsDisplayed()
                rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
                rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
                rule.runOnIdle { assertEquals(1, unlocked); assertEquals(0, confirmed); assertEquals(0, sent) }
            }
        } finally { operation.close(); first.fill(0) }
    }

    private fun signed(engine: NativePadesEngine, bytes: ByteArray, key: UnlockedIdentity, existing: Boolean = false): ByteArray {
        val result = engine.sign(bytes, key, SigningAlgorithm.SHA256_WITH_RSA, requireExistingSignature = existing)
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        return (result as LocalSignatureResult.Success).signature.use { it.withBytes { data -> data.copyOf() } }
    }
    private fun identity(serial: Long, reads: AtomicInteger): UnlockedIdentity {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=PDF cosign synthetic $serial")
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(serial), Date.from(now.minusSeconds(600)),
            Date.from(now.plusSeconds(3600)), name, pair.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(pair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder); val source = pair.private as RSAPrivateKey
        val key = object : RSAPrivateKey {
            override fun getAlgorithm() = "RSA"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray { reads.incrementAndGet(); error("No private-key export") }
            override fun getModulus() = source.modulus
            override fun getPrivateExponent() = source.privateExponent
        }
        return UnlockedIdentity(key, cert, listOf(cert), CertificateSummary("Synthetic", "Synthetic", now.minusSeconds(600), now.plusSeconds(3600)))
    }
    private fun render(bytes: ByteArray): Bitmap {
        val file = File.createTempFile("cosign-test-", ".pdf", ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                } }
            }
        } finally { file.delete() }
    }
}
