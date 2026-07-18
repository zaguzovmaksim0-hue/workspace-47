package dev.junta.firmamobile.signing

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.Clock
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.utils.IdResolver
import org.w3c.dom.Element

@RunWith(AndroidJUnit4::class)
class LocalXadesDetachedAdapterInstrumentedTest {
    @Test
    fun androidRuntimeCreatesXadesAcceptedByStandardXmlDsigValidator() = runBlocking {
        val identityBytes = syntheticPkcs12()
        val password = TEST_PASSPHRASE.toCharArray()
        val identity = try {
            val loaded = Pkcs12Loader().load(
                ByteArrayInputStream(identityBytes),
                identityBytes.size.toLong(),
                password,
            )
            (loaded as CertificateLoadResult.Success).identity
        } finally {
            password.fill('\u0000')
            identityBytes.fill(0)
        }
        XadesDetachedCodec.createPreSign(
            "<resumen><dato>runtime-probe</dato></resumen>".encodeToByteArray(),
            identity.chain,
            Clock.systemUTC(),
        ).also { material ->
            material.unsignedDocument.fill(0)
            material.signedInfo.fill(0)
            material.signingCertificateFingerprint.fill(0)
        }
        val request = request()
        val adapter = LocalXadesDetachedAdapter()
        val prepared = adapter.prepare(request, identity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { bytes ->
            JcaLocalSignatureEngine().sign(bytes, identity, SigningAlgorithm.SHA512_WITH_RSA)
        } as LocalSignatureResult.Success
        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes(ByteArray::copyOf)

        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
        try {
            assertTrue(validateWithStandardXmlDsig(result, identity.certificate.publicKey))
        } finally {
            result.fill(0)
        }
    }

    private fun validateWithStandardXmlDsig(bytes: ByteArray, publicKey: java.security.PublicKey): Boolean {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        markIds(document.documentElement)
        val signature = document.getElementsByTagNameNS(
            "http://www.w3.org/2000/09/xmldsig#",
            "Signature",
        ).item(0)
        return XMLSignature(signature as Element, "", true).checkSignatureValue(publicKey)
    }

    private fun markIds(element: Element) {
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true)
            IdResolver.registerElementById(element, element.getAttributeNode("Id"))
        }
        val children = element.childNodes
        for (index in 0 until children.length) {
            (children.item(index) as? Element)?.let(::markIds)
        }
    }

    private fun request(): NormalizedSignRequest {
        val xml = "<resumen><dato>synthetic-registry</dato></resumen>".encodeToByteArray()
        return NormalizedSignRequest(
            requestId = UUID.randomUUID(),
            protocolId = LocalXadesDetachedAdapter.ID,
            context = SigningContext(
                profileId = "reg-age-redsara",
                profileVersion = 1,
                origin = TrustedOrigin("https", "reg.redsara.es", 443),
                navigationId = NavigationId(UUID.randomUUID().toString()),
                navigationEpoch = 1L,
                observedAt = Instant.now(),
            ),
            algorithm = SigningAlgorithm.SHA512_WITH_RSA,
            format = SigningFormat.XADES,
            safeDescription = "Synthetic registry summary",
            payload = MiniAppletPayloadCodec.encode(xml, ""),
        ).also { xml.fill(0) }
    }

    private fun syntheticPkcs12(): ByteArray {
        val context = InstrumentationRegistry.getInstrumentation().context
        val encoded = context.assets.open("synthetic-identity.p12.b64")
            .bufferedReader()
            .use { it.readText() }
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private companion object {
        const val TEST_PASSPHRASE = "test-password-123"
    }
}
