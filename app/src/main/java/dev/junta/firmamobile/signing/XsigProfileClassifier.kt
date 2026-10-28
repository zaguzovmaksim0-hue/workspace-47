package dev.junta.firmamobile.signing

import dev.junta.firmamobile.afirma.servlet.AfirmaServletInvocationParser
import dev.junta.firmamobile.afirma.servlet.NativeSigningXml
import dev.junta.firmamobile.afirma.servlet.NativeXadesEngine
import dev.junta.firmamobile.afirma.servlet.NativeXadesOptions
import dev.junta.firmamobile.afirma.servlet.NativeXadesPackaging
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import org.w3c.dom.Document
import org.w3c.dom.Element

internal data class XsigProfile(
    val packaging: NativeXadesPackaging,
    val certificate: X509Certificate,
    val algorithm: SigningAlgorithm,
    val options: NativeXadesOptions,
    val embeddedPayload: ByteArray?,
)

// Detekt: the classifier intentionally keeps profile recognition in one bounded
// entry point; every branch returns an explicit unsupported classification.
@Suppress("CyclomaticComplexMethod", "TooManyFunctions", "ReturnCount", "MagicNumber")
internal object XsigProfileClassifier {
    private const val SHA256_URN_PREFIX = "urn:sha256:"
    private const val SHA256_URN_LENGTH = 75
    private const val ENVELOPED_TRANSFORM = NativeSigningXml.DS + "enveloped-signature"
    private const val BASE64_TRANSFORM = NativeSigningXml.DS + "base64"

    fun classify(bytes: ByteArray): XsigProfile? = runCatching {
        val document = NativeSigningXml.parse(bytes)
        val signature = singleSignature(document) ?: return@runCatching null
        val signedInfo = NativeSigningXml.children(signature)
            .singleOrNull { it.namespaceURI == NativeSigningXml.DS && it.localName == "SignedInfo" }
            ?: return@runCatching null
        val references = NativeSigningXml.children(signedInfo)
            .filter { it.namespaceURI == NativeSigningXml.DS && it.localName == "Reference" }
        if (references.size != 3 || references.any { !hasAllowedUri(it) }) return@runCatching null

        val dataReference = references.first()
        val transforms = transforms(dataReference) ?: return@runCatching null
        val mimeType = dataObjectMimeType(signature) ?: return@runCatching null
        if (nativeOptions(mimeType, contentDescription(signature), null) == null) return@runCatching null

        val packaging: NativeXadesPackaging
        val embeddedPayload: ByteArray?
        when {
            dataReference.getAttribute("URI").isEmpty() -> {
                if (transforms != listOf(ENVELOPED_TRANSFORM, NativeSigningXml.C14N)) return@runCatching null
                if (signature.parentNode !== document.documentElement) return@runCatching null
                packaging = NativeXadesPackaging.ENVELOPED
                embeddedPayload = null
            }
            dataReference.getAttribute("URI").startsWith(SHA256_URN_PREFIX) -> {
                if (transforms.isNotEmpty() || !isSha256Urn(dataReference.getAttribute("URI"))) {
                    return@runCatching null
                }
                packaging = NativeXadesPackaging.EXTERNALLY_DETACHED
                embeddedPayload = null
            }
            else -> {
                if (transforms != listOf(BASE64_TRANSFORM)) return@runCatching null
                val target = NativeSigningXml.findId(
                    document,
                    dataReference.getAttribute("URI").removePrefix("#"),
                )
                val decoded = decodeEmbedded(target.textContent) ?: return@runCatching null
                packaging = when {
                    isEnvelopingTarget(target, signature) -> NativeXadesPackaging.ENVELOPING
                    isInternalDetachedTarget(target, document) -> NativeXadesPackaging.DETACHED
                    else -> return@runCatching null
                }
                embeddedPayload = decoded
            }
        }

        val certificate = embeddedCertificate(signature) ?: return@runCatching null
        val algorithm = signatureAlgorithm(signedInfo) ?: return@runCatching null
        val options = nativeOptions(mimeType, contentDescription(signature), packaging)
            ?: return@runCatching null
        XsigProfile(packaging, certificate, algorithm, options, embeddedPayload)
    }.getOrNull()

    private fun singleSignature(document: Document): Element? {
        val signatures = document.getElementsByTagNameNS(NativeSigningXml.DS, "Signature")
        return signatures.length.takeIf { it == 1 }?.let { signatures.item(0) as Element }
    }

    private fun hasAllowedUri(reference: Element): Boolean {
        val uri = reference.getAttribute("URI")
        return uri.isEmpty() || uri.startsWith("#") || uri.startsWith(SHA256_URN_PREFIX)
    }

    private fun transforms(reference: Element): List<String>? {
        val groups = NativeSigningXml.children(reference)
            .filter { it.namespaceURI == NativeSigningXml.DS && it.localName == "Transforms" }
        if (groups.size > 1) return null
        val result = mutableListOf<String>()
        for (group in groups) {
            for (transform in NativeSigningXml.children(group)) {
                if (transform.namespaceURI != NativeSigningXml.DS || transform.localName != "Transform") {
                    return null
                }
                result.add(transform.getAttribute("Algorithm"))
            }
        }
        return result
    }

    private fun isSha256Urn(uri: String): Boolean =
        uri.length == SHA256_URN_LENGTH &&
            uri.removePrefix(SHA256_URN_PREFIX).all { it.isDigit() || it in 'a'..'f' }

    private fun isEnvelopingTarget(target: Element, signature: Element): Boolean =
        target.namespaceURI == NativeSigningXml.DS &&
            target.localName == "Object" &&
            target.parentNode === signature

    private fun isInternalDetachedTarget(target: Element, document: Document): Boolean =
        target.nodeName == "CONTENT" &&
            target.parentNode === document.documentElement &&
            document.documentElement.nodeName == "AFIRMA"

    private fun embeddedCertificate(signature: Element): X509Certificate? = runCatching {
        val keyInfo = NativeSigningXml.children(signature)
            .single { it.namespaceURI == NativeSigningXml.DS && it.localName == "KeyInfo" }
        val certificates = keyInfo.getElementsByTagNameNS(NativeSigningXml.DS, "X509Certificate")
        if (certificates.length == 0) return@runCatching null
        val encoded = Base64.getMimeDecoder().decode(certificates.item(0).textContent)
        CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as X509Certificate
    }.getOrNull()

    private fun signatureAlgorithm(signedInfo: Element): SigningAlgorithm? {
        val method = NativeSigningXml.children(signedInfo)
            .singleOrNull { it.namespaceURI == NativeSigningXml.DS && it.localName == "SignatureMethod" }
            ?: return null
        return when (method.getAttribute("Algorithm")) {
            "http://www.w3.org/2000/09/xmldsig#rsa-sha1" -> SigningAlgorithm.SHA1_WITH_RSA
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" -> SigningAlgorithm.SHA256_WITH_RSA
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384" -> SigningAlgorithm.SHA384_WITH_RSA
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" -> SigningAlgorithm.SHA512_WITH_RSA
            else -> null
        }
    }

    private fun qualifyingProperties(signature: Element): Element? = NativeSigningXml.children(signature)
        .filter { it.namespaceURI == NativeSigningXml.DS && it.localName == "Object" }
        .flatMap(NativeSigningXml::children)
        .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "QualifyingProperties" }

    private fun dataObjectFormat(signature: Element): Element? {
        val properties = qualifyingProperties(signature)
            ?.let { qualifying ->
                NativeSigningXml.children(qualifying)
                    .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "SignedProperties" }
            } ?: return null
        val dataProperties = NativeSigningXml.children(properties)
            .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "SignedDataObjectProperties" }
            ?: return null
        return NativeSigningXml.children(dataProperties)
            .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "DataObjectFormat" }
    }

    private fun dataObjectMimeType(signature: Element): String? {
        val format = dataObjectFormat(signature) ?: return null
        return NativeSigningXml.children(format)
            .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "MimeType" }
            ?.textContent
    }

    private fun contentDescription(signature: Element): String? {
        val format = dataObjectFormat(signature) ?: return null
        return NativeSigningXml.children(format)
            .singleOrNull { it.namespaceURI == NativeSigningXml.XADES && it.localName == "Description" }
            ?.textContent
    }

    private fun nativeOptions(
        mimeType: String,
        contentDescription: String?,
        packaging: NativeXadesPackaging?,
    ): NativeXadesOptions? {
        val properties = buildMap {
            put("mimeType", mimeType)
            contentDescription?.let { put("contentDescription", it) }
        }
        val parsed = NativeXadesOptions.parse("xades", properties) ?: return null
        return packaging?.let { parsed.copy(packaging = it) } ?: parsed
    }

    private fun decodeEmbedded(value: String): ByteArray? = runCatching {
        val decoded = AfirmaServletInvocationParser.strictBase64(value, NativeXadesEngine.MAX_INPUT)
        decoded.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
