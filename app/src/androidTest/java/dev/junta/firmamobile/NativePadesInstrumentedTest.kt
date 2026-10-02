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
import dev.junta.firmamobile.afirma.servlet.*
import dev.junta.firmamobile.certificate.CertificateSummary
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.ui.NativeAfirmaConsentDialog
import java.io.ByteArrayOutputStream
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

/** Real PDF signature and Android PdfRenderer, using only a generated synthetic
 * key/document. No user KeyChain alias or storage-server request is used. */
@RunWith(AndroidJUnit4::class)
class NativePadesInstrumentedTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun signedPdfRendersIdenticallyAndNeverExportsTheSyntheticPrivateKey() {
        val now = Instant.parse("2030-01-02T03:04:05Z")
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=Native PDF synthetic fixture")
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date.from(now.minusSeconds(600)),
            Date.from(now.plusSeconds(3600)), name, pair.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(pair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val reads = AtomicInteger()
        val sourceKey = pair.private as RSAPrivateKey
        val key = object : RSAPrivateKey {
            override fun getAlgorithm() = "RSA"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray { reads.incrementAndGet(); error("No key export") }
            override fun getModulus() = sourceKey.modulus
            override fun getPrivateExponent() = sourceKey.privateExponent
        }
        val identity = UnlockedIdentity(key, cert, listOf(cert), CertificateSummary("Synthetic only", "Synthetic only", now.minusSeconds(600), now.plusSeconds(3600)))
        val original = nativeInstrumentedPdf()
        val engine = NativePadesEngine(clock = Clock.fixed(now, ZoneOffset.UTC))
        val result = engine.sign(original, identity, SigningAlgorithm.SHA256_WITH_RSA)
        assertTrue(result.toString(), result is LocalSignatureResult.Success)
        (result as LocalSignatureResult.Success).signature.use { signature -> signature.withBytes { signed ->
            assertTrue(engine.verify(signed, original, cert, SigningAlgorithm.SHA256_WITH_RSA))
            assertArrayEquals(original, signed.copyOf(original.size))
            val before = render(original)
            val after = render(signed)
            try { assertTrue("An invisible signature must not alter the displayed page", before.sameAs(after)) }
            finally { before.recycle(); after.recycle() }
        } }
        assertEquals(0, reads.get())
    }

    @Test fun parsedPdfReviewIsLabelledAsPdfAndUnlockDoesNotConfirmOrUpload() {
        val pdf = nativeInstrumentedPdf()
        val parsed = AfirmaServletInvocationParser.parse(
            "afirma://sign?id=PdfUi123&stservlet=https%3A%2F%2Fstore.synthetic.example%2Fput&format=PAdES&algorithm=SHA256withRSA&dat=" +
                Base64.getUrlEncoder().encodeToString(pdf), "https://unprofiled.synthetic.example/form") as AfirmaServletParseResult.Accepted
        var sent = 0; var confirmed = 0; var unlock = 0
        val operation = NativeAfirmaOperation(parsed.invocation, AfirmaResultTransport { _, _, _ -> sent++; error("No storage in UI fixture") })
        try {
            val prompt = AfirmaConsentPrompt(UUID.randomUUID(), operation.details, AfirmaConsentPhase.REVIEW, null, AfirmaConsentProblem.LOCKED)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    NativeAfirmaConsentDialog(prompt, onConfirm = { confirmed++ }, onUnlock = { unlock++ }, onCancel = {}, onDismiss = {})
                } }
                rule.onNodeWithText("PDF · PAdES", substring = true).assertIsDisplayed()
                rule.onNodeWithTag("native-afirma-unlock").assertIsDisplayed().performClick()
                rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
                rule.runOnIdle { assertEquals(1, unlock); assertEquals(0, confirmed); assertEquals(0, sent) }
            }
        } finally { operation.close() }
    }

    private fun render(bytes: ByteArray): Bitmap {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("synthetic-pdf-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
        } finally { file.delete() }
    }
}

internal fun nativeInstrumentedPdf(): ByteArray {
    val bodies = listOf("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R >>",
        "0 0 1 RG 2 w 20 20 260 160 re S\n".let { "<< /Length ${it.length} >>\nstream\n${it}endstream" })
    val out = ByteArrayOutputStream(); fun put(t: String) { out.write(t.toByteArray(Charsets.US_ASCII)) }
    put("%PDF-1.4\n% local-only test\n"); val offsets = mutableListOf<Int>()
    bodies.forEachIndexed { i, b -> offsets.add(out.size()); put("${i+1} 0 obj\n$b\nendobj\n") }
    val xref = out.size(); put("xref\n0 5\n0000000000 65535 f \n")
    for (offset in offsets) put("%010d 00000 n \n".format(java.util.Locale.ROOT, offset))
    put("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}
