package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.LocalSignatureError
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.time.Clock
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cms.CMSSignedData

/** Adds one parallel approval signer, never a counter-signature. Existing
 * SignerInfo structures, certificates and content packaging are retained. */
internal class NativeCadesCoSignEngine(
    private val clock: Clock = Clock.systemUTC(),
    private val maxInputBytes: Int = NativeCadesEngine.MAX_INPUT_BYTES,
    private val maxOutputBytes: Int = NativeCadesEngine.MAX_OUTPUT_BYTES,
) {
    init { require(maxInputBytes > 0 && maxOutputBytes > 0) }

    fun cosign(encoded: ByteArray, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        requestedDetached: Boolean? = null): LocalSignatureResult {
        if (encoded.size > maxInputBytes) return LocalSignatureResult.Failure(LocalSignatureError.INPUT_TOO_LARGE)
        val input = encoded.copyOf()
        var output: ByteArray? = null
        return try {
            val previous = NativeCadesHistory.inspect(input, maxInputBytes)
                ?: return LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
            previous.use {
                require(previous.signerRecords.size < NativeCadesHistory.MAX_SIGNERS)
                require(requestedDetached == null || requestedDetached == previous.detached) { "Do not silently change original packaging" }
                val engine = NativeCadesEngine(clock = clock)
                val digest = previous.hashes[NativeCadesEngine.digestOid(algorithm)]
                if (previous.detached) require(digest != null) { "The original file is absent and the requested digest is unavailable" }
                // Validate certificate/size/history constraints before invoking
                // the new key. For detached data this signs a verified digest.
                val prospectiveCerts = previous.certificates.map { it.encoded } +
                    identity.chain.ifEmpty { listOf(identity.certificate) }.map { it.encoded }
                require(prospectiveCerts.distinctBy(NativeCadesHistory::digest).size <= NativeCadesHistory.MAX_CERTIFICATES)
                val newSignature = if (previous.detached) engine.signDigest(checkNotNull(digest), identity, algorithm)
                    else engine.sign(checkNotNull(previous.content), identity, algorithm, false)
                if (newSignature !is LocalSignatureResult.Success) return newSignature
                newSignature.signature.use { fresh ->
                    fresh.withBytes { bytes ->
                        val added = CMSSignedData(bytes)
                        val newSigner = added.signerInfos.signers.single()
                        val newRecord = newSigner.toASN1Structure().getEncoded(ASN1Encoding.DER)
                        try {
                            require(previous.signerRecords.none { it.contentEquals(newRecord) }) { "An identical signer record cannot count twice" }
                            val original = previous.structure
                            val newData = SignedData.getInstance(added.toASN1Structure().content)
                            val digestAlgorithms = elements(original.digestAlgorithms) + elements(newData.digestAlgorithms)
                            val certificates = (elements(original.certificates) + elements(newData.certificates))
                                .distinctBy { NativeCadesHistory.digest(it.toASN1Primitive().getEncoded(ASN1Encoding.DER)) }
                            val signers = elements(original.signerInfos) + newSigner.toASN1Structure()
                            val combined = SignedData(
                                DERSet(digestAlgorithms.distinctBy { AlgorithmIdentifier.getInstance(it).algorithm.id }.toTypedArray()),
                                original.encapContentInfo,
                                DERSet(certificates.toTypedArray()), original.crLs, DERSet(signers.toTypedArray()),
                            )
                            output = ContentInfo(PKCSObjectIdentifiers.signedData, combined).getEncoded(ASN1Encoding.DER)
                            if (checkNotNull(output).size > maxOutputBytes) throw OutputLimit()
                            val after = NativeCadesHistory.inspect(checkNotNull(output), maxOutputBytes)
                                ?: error("Combined signature validation failed")
                            after.use {
                                require(after.detached == previous.detached)
                                require(after.structure.encapContentInfo.getEncoded(ASN1Encoding.DER).contentEquals(original.encapContentInfo.getEncoded(ASN1Encoding.DER)))
                                val expected = (previous.signerRecords + newRecord).map(NativeCadesHistory::digest).sorted()
                                require(after.signerRecords.map(NativeCadesHistory::digest).sorted() == expected)
                                val expectedCerts = certificates.map { NativeCadesHistory.digest(it.toASN1Primitive().getEncoded(ASN1Encoding.DER)) }.sorted()
                                require(after.certificates.map { NativeCadesHistory.digest(it.encoded) }.sorted() == expectedCerts)
                                require(after.envelope.signerInfos.signers.any { it.toASN1Structure().getEncoded(ASN1Encoding.DER).contentEquals(newRecord) })
                            }
                        } finally { newRecord.fill(0); prospectiveCerts.forEach { it.fill(0) } }
                    }
                }
            }
            val result = checkNotNull(output); output = null
            LocalSignatureResult.Success(LocalSignature(result))
        } catch (_: OutputLimit) {
            LocalSignatureResult.Failure(LocalSignatureError.OUTPUT_TOO_LARGE)
        } catch (_: Exception) {
            LocalSignatureResult.Failure(LocalSignatureError.SIGNATURE_FAILED)
        } finally { input.fill(0); output?.fill(0) }
    }

    private class OutputLimit : Exception()

    private fun elements(values: org.bouncycastle.asn1.ASN1Set): List<ASN1Encodable> =
        (0 until values.size()).map(values::getObjectAt)
}
