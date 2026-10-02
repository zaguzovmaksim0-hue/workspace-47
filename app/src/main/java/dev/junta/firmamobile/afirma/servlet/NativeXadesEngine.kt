package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.LocalSignatureError
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.xml.XMLConstants
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Generic, profile-independent local XAdES-BES. References are generated and
 * verified locally; no external URI is dereferenced by signing or validation. */
internal class NativeXadesEngine(private val clock: Clock = Clock.systemUTC()) {
    private val rawSigner = JcaLocalSignatureEngine(maxInputBytes = MAX_OUTPUT, maxOutputBytes = 16_384)

    fun sign(payload: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm, options: NativeXadesOptions): LocalSignatureResult {
        if (payload.isEmpty() || payload.size > MAX_INPUT) return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        return try {
            identity.certificate.checkValidity(Date.from(clock.instant()))
            val chain = identity.chain.ifEmpty { listOf(identity.certificate) }
            require(chain.size <= 8 && chain.first() == identity.certificate)
            val shape = construct(payload, identity.certificate, chain, algorithm, options)
            val info = NativeSigningXml.canonical(shape.info)
            val raw = try { rawSigner.sign(info, identity, algorithm) } finally { info.fill(0) }
            when (raw) {
                is LocalSignatureResult.Failure -> raw
                is LocalSignatureResult.Success -> raw.signature.use { signature ->
                    shape.value.textContent = signature.withBytes { b64(it) }
                    val output = NativeSigningXml.serialize(shape.document)
                    if (!verify(output, payload, identity.certificate, algorithm, options)) {
                        output.fill(0); LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
                    } else LocalSignatureResult.Success(LocalSignature(output))
                }
            }
        } catch (_: Exception) { LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED) }
    }

    fun verify(encoded: ByteArray, payload: ByteArray, certificate: X509Certificate,
        algorithm: SigningAlgorithm, options: NativeXadesOptions): Boolean = runCatching {
        require(payload.isNotEmpty() && payload.size <= MAX_INPUT && encoded.size <= MAX_OUTPUT)
        val doc = NativeSigningXml.parse(encoded)
        val signatures = doc.getElementsByTagNameNS(DS, "Signature")
        require(signatures.length == 1)
        val signature = signatures.item(0) as Element
        val info = one(signature, DS, "SignedInfo")
        require(one(info, DS, "CanonicalizationMethod").getAttribute("Algorithm") == NativeSigningXml.C14N)
        require(one(info, DS, "SignatureMethod").getAttribute("Algorithm") == signatureUri(algorithm))
        val references = NativeSigningXml.children(info).filter { it.namespaceURI == DS && it.localName == "Reference" }
        require(references.size == 3 && NativeSigningXml.children(info).size == 5)
        val keyInfo = one(signature, DS, "KeyInfo")
        val certNodes = keyInfo.getElementsByTagNameNS(DS, "X509Certificate")
        require(certNodes.length in 1..8)
        require(MessageDigest.isEqual(decode(certNodes.item(0).textContent, 65_536), certificate.encoded))
        val objectNodes = NativeSigningXml.children(signature).filter { it.namespaceURI == DS && it.localName == "Object" }
        val qualifying = objectNodes.flatMap(NativeSigningXml::children).single { it.namespaceURI == XA && it.localName == "QualifyingProperties" }
        require(qualifying.getAttribute("Target") == "#" + signature.getAttribute("Id"))
        val properties = one(qualifying, XA, "SignedProperties")
        val signedSignature = one(properties, XA, "SignedSignatureProperties")
        val certReference = one(one(signedSignature, XA, "SigningCertificate"), XA, "Cert")
        val certDigest = one(certReference, XA, "CertDigest")
        require(one(certDigest, DS, "DigestMethod").getAttribute("Algorithm") == SHA256_URI)
        require(MessageDigest.isEqual(decode(one(certDigest, DS, "DigestValue").textContent, 32), MessageDigest.getInstance("SHA-256").digest(certificate.encoded)))
        val issuer = one(certReference, XA, "IssuerSerial")
        require(one(issuer, DS, "X509IssuerName").textContent == certificate.issuerX500Principal.name)
        require(one(issuer, DS, "X509SerialNumber").textContent == certificate.serialNumber.toString())
        one(one(signedSignature, XA, "SignaturePolicyIdentifier"), XA, "SignaturePolicyImplied")
        val format = one(one(properties, XA, "SignedDataObjectProperties"), XA, "DataObjectFormat")
        require(format.getAttribute("ObjectReference") == "#" + references[0].getAttribute("Id"))
        require(one(format, XA, "MimeType").textContent == options.mimeType)
        val descriptions = NativeSigningXml.children(format).filter { it.localName == "Description" && it.namespaceURI == XA }
        require(if (options.contentDescription == null) descriptions.isEmpty() else descriptions.single().textContent == options.contentDescription)
        val expectedPropertyUri = "#" + properties.getAttribute("Id")
        require(references[1].getAttribute("URI") == expectedPropertyUri && references[1].getAttribute("Type") == SIGNED_PROPERTIES)
        require(references[2].getAttribute("URI") == "#" + keyInfo.getAttribute("Id"))
        val dataReference = references[0]
        val dataBytes = when (options.packaging) {
            NativeXadesPackaging.ENVELOPED -> {
                require(dataReference.getAttribute("URI").isEmpty())
                require(signature.parentNode === doc.documentElement)
                val original = NativeSigningXml.parse(payload, MAX_INPUT)
                val clone = NativeSigningXml.parse(encoded)
                clone.getElementsByTagNameNS(DS, "Signature").item(0).let { it.parentNode.removeChild(it) }
                val covered = NativeSigningXml.canonical(clone)
                require(MessageDigest.isEqual(covered, NativeSigningXml.canonical(original)))
                require(transforms(dataReference) == listOf(ENVELOPED, NativeSigningXml.C14N))
                covered
            }
            NativeXadesPackaging.EXTERNALLY_DETACHED -> {
                require(dataReference.getAttribute("URI") == externalId(payload) && transforms(dataReference).isEmpty())
                payload.copyOf()
            }
            else -> {
                val target = NativeSigningXml.findId(doc, dataReference.getAttribute("URI").removePrefix("#"))
                require(dataReference.getAttribute("URI").startsWith("#"))
                require(target.getAttribute("MimeType") == options.mimeType && transforms(dataReference) == listOf(BASE64_TRANSFORM))
                require(NativeSigningXml.children(target).isEmpty())
                if (options.packaging == NativeXadesPackaging.ENVELOPING) require(target.namespaceURI == DS && target.localName == "Object" && target.parentNode === signature)
                else require(target.nodeName == "CONTENT" && target.parentNode === doc.documentElement && doc.documentElement.nodeName == "AFIRMA")
                decode(target.textContent, MAX_INPUT).also { require(MessageDigest.isEqual(it, payload)) }
            }
        }
        val referenceData = listOf(dataBytes, NativeSigningXml.canonical(properties), NativeSigningXml.canonical(keyInfo))
        for (index in references.indices) {
            val ref = references[index]
            require(one(ref, DS, "DigestMethod").getAttribute("Algorithm") == digestUri(algorithm))
            if (index > 0) require(transforms(ref) == listOf(NativeSigningXml.C14N))
            require(MessageDigest.isEqual(digest(referenceData[index], algorithm), decode(one(ref, DS, "DigestValue").textContent, 64)))
        }
        val signatureValue = decode(one(signature, DS, "SignatureValue").textContent, 16_384)
        Signature.getInstance(algorithm.jcaName()).run {
            initVerify(certificate.publicKey); update(NativeSigningXml.canonical(info)); verify(signatureValue)
        }
    }.getOrDefault(false)

    private class Shape(val document: Document, val info: Element, val value: Element)
    private fun construct(payload: ByteArray, certificate: X509Certificate, chain: List<X509Certificate>,
        algorithm: SigningAlgorithm, options: NativeXadesOptions): Shape {
        val doc = if (options.packaging == NativeXadesPackaging.ENVELOPED) NativeSigningXml.parse(payload, MAX_INPUT) else NativeSigningXml.newDocument()
        require(doc.getElementsByTagNameNS(DS, "Signature").length == 0)
        val stem = "sig-" + UUID.randomUUID()
        val signature = element(doc, DS, "ds:Signature").apply {
            setAttribute("Id", stem)
            setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ds", DS)
            setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xades", XA)
        }
        when (options.packaging) {
            NativeXadesPackaging.ENVELOPED -> doc.documentElement.appendChild(signature)
            NativeXadesPackaging.DETACHED -> doc.createElement("AFIRMA").also { doc.appendChild(it); it.appendChild(signature) }
            else -> doc.appendChild(signature)
        }
        val dataId = "$stem-data"
        val referenceId = "$stem-reference"
        val content = if (options.packaging in setOf(NativeXadesPackaging.DETACHED, NativeXadesPackaging.ENVELOPING)) {
            (if (options.packaging == NativeXadesPackaging.DETACHED) doc.createElement("CONTENT") else element(doc, DS, "ds:Object")).apply {
                setAttribute("Id", dataId); setAttribute("MimeType", options.mimeType); setAttribute("Encoding", BASE64_TRANSFORM); textContent = b64(payload)
                if (options.packaging == NativeXadesPackaging.DETACHED) doc.documentElement.insertBefore(this, signature) else signature.appendChild(this)
            }
        } else null
        val info = element(doc, DS, "ds:SignedInfo")
        signature.insertBefore(info, signature.firstChild)
        info.appendChild(element(doc, DS, "ds:CanonicalizationMethod").apply { setAttribute("Algorithm", NativeSigningXml.C14N) })
        info.appendChild(element(doc, DS, "ds:SignatureMethod").apply { setAttribute("Algorithm", signatureUri(algorithm)) })
        val value = element(doc, DS, "ds:SignatureValue")
        signature.insertBefore(value, content?.takeIf { it.parentNode === signature })
        val keyInfo = element(doc, DS, "ds:KeyInfo").apply { setAttribute("Id", "$stem-key") }
        val x509 = element(doc, DS, "ds:X509Data")
        chain.forEach { cert -> x509.appendChild(element(doc, DS, "ds:X509Certificate", b64(cert.encoded))) }
        keyInfo.appendChild(x509); signature.insertBefore(keyInfo, content?.takeIf { it.parentNode === signature })
        val obj = element(doc, DS, "ds:Object"); signature.appendChild(obj)
        val qualifying = element(doc, XA, "xades:QualifyingProperties").apply { setAttribute("Target", "#$stem") }; obj.appendChild(qualifying)
        val props = element(doc, XA, "xades:SignedProperties").apply { setAttribute("Id", "$stem-props") }; qualifying.appendChild(props)
        val signed = element(doc, XA, "xades:SignedSignatureProperties"); props.appendChild(signed)
        signed.appendChild(element(doc, XA, "xades:SigningTime", DateTimeFormatter.ISO_INSTANT.format(clock.instant())))
        val signingCert = element(doc, XA, "xades:SigningCertificate"); signed.appendChild(signingCert)
        val cert = element(doc, XA, "xades:Cert"); signingCert.appendChild(cert)
        val certDigest = element(doc, XA, "xades:CertDigest"); cert.appendChild(certDigest)
        certDigest.appendChild(element(doc, DS, "ds:DigestMethod").apply { setAttribute("Algorithm", SHA256_URI) })
        certDigest.appendChild(element(doc, DS, "ds:DigestValue", b64(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))))
        val issuer = element(doc, XA, "xades:IssuerSerial"); cert.appendChild(issuer)
        issuer.appendChild(element(doc, DS, "ds:X509IssuerName", certificate.issuerX500Principal.name))
        issuer.appendChild(element(doc, DS, "ds:X509SerialNumber", certificate.serialNumber.toString()))
        signed.appendChild(element(doc, XA, "xades:SignaturePolicyIdentifier").apply { appendChild(element(doc, XA, "xades:SignaturePolicyImplied")) })
        val dataProps = element(doc, XA, "xades:SignedDataObjectProperties"); props.appendChild(dataProps)
        dataProps.appendChild(element(doc, XA, "xades:DataObjectFormat").apply {
            setAttribute("ObjectReference", "#$referenceId")
            options.contentDescription?.let { appendChild(element(doc, XA, "xades:Description", it)) }
            appendChild(element(doc, XA, "xades:MimeType", options.mimeType))
        })
        val dataUri: String
        val dataTransforms: List<String>
        val covered: ByteArray
        when (options.packaging) {
            NativeXadesPackaging.ENVELOPED -> {
                // An empty reference identifies the complete XML document,
                // including processing instructions before and after its root.
                dataUri = ""; dataTransforms = listOf(ENVELOPED, NativeSigningXml.C14N)
                doc.documentElement.removeChild(signature)
                covered = NativeSigningXml.canonical(doc)
                doc.documentElement.appendChild(signature)
            }
            NativeXadesPackaging.EXTERNALLY_DETACHED -> { dataUri = externalId(payload); dataTransforms = emptyList(); covered = payload }
            else -> { dataUri = "#$dataId"; dataTransforms = listOf(BASE64_TRANSFORM); covered = payload }
        }
        info.appendChild(reference(doc, dataUri, covered, algorithm, dataTransforms).apply { setAttribute("Id", referenceId) })
        info.appendChild(reference(doc, "#$stem-props", NativeSigningXml.canonical(props), algorithm, listOf(NativeSigningXml.C14N)).apply { setAttribute("Type", SIGNED_PROPERTIES) })
        info.appendChild(reference(doc, "#$stem-key", NativeSigningXml.canonical(keyInfo), algorithm, listOf(NativeSigningXml.C14N)))
        return Shape(doc, info, value)
    }
    private fun reference(doc: Document, uri: String, bytes: ByteArray, algorithm: SigningAlgorithm, transformations: List<String>) =
        element(doc, DS, "ds:Reference").apply {
            setAttribute("URI", uri)
            if (transformations.isNotEmpty()) appendChild(element(doc, DS, "ds:Transforms").apply {
                transformations.forEach { appendChild(element(doc, DS, "ds:Transform").apply { setAttribute("Algorithm", it) }) }
            })
            appendChild(element(doc, DS, "ds:DigestMethod").apply { setAttribute("Algorithm", digestUri(algorithm)) })
            appendChild(element(doc, DS, "ds:DigestValue", b64(digest(bytes, algorithm))))
        }
    private fun transforms(reference: Element): List<String> = NativeSigningXml.children(reference)
        .filter { it.namespaceURI == DS && it.localName == "Transforms" }.also { require(it.size <= 1) }
        .flatMap(NativeSigningXml::children).map { require(it.namespaceURI == DS && it.localName == "Transform" && NativeSigningXml.children(it).isEmpty()); it.getAttribute("Algorithm") }
    private fun element(doc: Document, ns: String, name: String, text: String? = null) = doc.createElementNS(ns, name).apply { if (text != null) textContent = text }
    private fun one(node: Node, ns: String, name: String) = NativeSigningXml.one(node, ns, name)
    private fun b64(value: ByteArray) = Base64.getEncoder().encodeToString(value)
    private fun decode(value: String, limit: Int) = AfirmaServletInvocationParser.strictBase64(value, limit)
    private fun digest(value: ByteArray, alg: SigningAlgorithm) = MessageDigest.getInstance(when (alg) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA-1"; SigningAlgorithm.SHA256_WITH_RSA -> "SHA-256"; SigningAlgorithm.SHA512_WITH_RSA -> "SHA-512"
    }).digest(value)
    private fun externalId(payload: ByteArray) = "urn:sha256:" + MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private fun digestUri(alg: SigningAlgorithm) = when (alg) {
        SigningAlgorithm.SHA1_WITH_RSA -> DS + "sha1"; SigningAlgorithm.SHA256_WITH_RSA -> SHA256_URI; SigningAlgorithm.SHA512_WITH_RSA -> "http://www.w3.org/2001/04/xmlenc#sha512"
    }
    private fun signatureUri(alg: SigningAlgorithm) = when (alg) {
        SigningAlgorithm.SHA1_WITH_RSA -> DS + "rsa-sha1"; SigningAlgorithm.SHA256_WITH_RSA -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"; SigningAlgorithm.SHA512_WITH_RSA -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
    }
    private fun SigningAlgorithm.jcaName() = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"; SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"; SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
    }
    companion object {
        private const val DS = NativeSigningXml.DS
        private const val XA = NativeSigningXml.XADES
        private const val SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties"
        private const val SHA256_URI = "http://www.w3.org/2001/04/xmlenc#sha256"
        private const val ENVELOPED = DS + "enveloped-signature"
        private const val BASE64_TRANSFORM = DS + "base64"
        const val MAX_INPUT = 524_288
        const val MAX_OUTPUT = 2_097_152
    }
}
