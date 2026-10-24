package dev.junta.firmamobile.signing

import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareEcCadesVerificationTest {
    @Test
    fun acceptsBothAllowedDetachedPairsWithoutChangingCallerArrays() {
        SoftwareEcSignatureAlgorithm.entries.forEach { algorithm ->
            val f = SoftwareEcCadesTestFixtures(algorithm)
            val originalInput = f.input.copyOf()
            val originalCms = f.encoded.copyOf()
            assertTrue(f.verifies())
            assertArrayEquals(originalInput, f.input)
            assertArrayEquals(originalCms, f.encoded)
        }
    }

    @Test
    fun rejectsChangedOriginalBytesAndPrecomputedDigest() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        assertFalse(f.verifies(input = f.input + 1.toByte()))
        assertFalse(f.verifies(input = MessageDigest.getInstance("SHA-256").digest(f.input)))
    }

    @Test
    fun rejectsWrongCertificateAndCrossCurveHashRequest() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        val wrong = SoftwareEcTestFixtures.certificate(SoftwareEcTestFixtures.keys("secp256r1"))
        assertFalse(f.engine.verifyDetached(f.input, f.encoded, wrong, f.algorithm))
        val sameKeyDifferentCertificate = f.otherCertificateWithSameKey()
        assertFalse(f.certificate.encoded.contentEquals(sameKeyDifferentCertificate.encoded))
        assertFalse(f.engine.verifyDetached(f.input, f.encoded, sameKeyDifferentCertificate, f.algorithm))
        assertFalse(f.engine.verifyDetached(f.input, f.encoded, f.certificate,
            SoftwareEcSignatureAlgorithm.P384_SHA384))
        val other = SoftwareEcCadesTestFixtures(SoftwareEcSignatureAlgorithm.P384_SHA384)
        assertFalse(other.engine.verifyDetached(other.input, other.encoded, other.certificate,
            SoftwareEcSignatureAlgorithm.P256_SHA256))
    }

    @Test
    fun rejectsInvalidRawSignatureEvenInsideOtherwiseMatchingCms() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        val signer = f.signer
        val badSignatures = listOf(byteArrayOf(), byteArrayOf(0x30, 0), ByteArray(64),
            signer.encryptedDigest.octets.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        badSignatures.forEach { bytes ->
            val mutated = f.replaceSigner(SignerInfo(signer.sid, signer.digestAlgorithm, signer.authenticatedAttributes,
                signer.digestEncryptionAlgorithm, DEROctetString(bytes), null))
            assertFalse(f.verifies(mutated))
        }
    }

    @Test
    fun rejectsCryptographicallyValidWrongDigestOrCertificateBinding() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        listOf(CMSAttributes.messageDigest, PKCSObjectIdentifiers.id_aa_signingCertificateV2).forEach { oid ->
            val altered = DERSet(f.signer.authenticatedAttributes.map {
                val attribute = Attribute.getInstance(it)
                if (attribute.attrType == oid) {
                    val value: ASN1Encodable = if (oid == PKCSObjectIdentifiers.id_aa_signingCertificateV2) {
                        SigningCertificateV2(ESSCertIDv2(f.signer.digestAlgorithm, ByteArray(32)))
                    } else DEROctetString(ByteArray(32))
                    Attribute(oid, DERSet(value))
                } else attribute
            }.toTypedArray())
            val encoded = f.resign(altered)
            assertTrue(f.independentSignatureValid(encoded))
            assertFalse(f.verifies(encoded))
        }
    }

    @Test
    fun rejectsValidlySignedMissingDuplicateExtraAndWrongContentAttributes() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        val attributes = f.signer.authenticatedAttributes.map { Attribute.getInstance(it) }
        val altered = listOf(
            DERSet(attributes.filterNot { it.attrType == CMSAttributes.messageDigest }.toTypedArray()),
            DERSet((attributes + attributes.first()).toTypedArray()),
            DERSet((attributes + Attribute(CMSAttributes.signingTime, DERSet(DEROctetString(byteArrayOf(1))))).toTypedArray()),
            DERSet(attributes.map { if (it.attrType == CMSAttributes.contentType) {
                Attribute(CMSAttributes.contentType, DERSet(CMSObjectIdentifiers.signedData))
            } else it }.toTypedArray()),
        )
        altered.forEach { attrs ->
            val encoded = f.resign(attrs)
            assertTrue(f.independentSignatureValid(encoded))
            assertFalse(f.verifies(encoded))
        }
    }

    @Test
    fun rejectsWrongAlgorithmOidsAndExplicitNullParameters() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        val wrongDigest = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384)
        val nullDigest = AlgorithmIdentifier(f.signer.digestAlgorithm.algorithm, DERNull.INSTANCE)
        val nullSignature = AlgorithmIdentifier(f.signer.digestEncryptionAlgorithm.algorithm, DERNull.INSTANCE)
        listOf(f.resign(f.signer.authenticatedAttributes, digest = wrongDigest),
            f.resign(f.signer.authenticatedAttributes, digest = nullDigest),
            f.resign(f.signer.authenticatedAttributes, signatureAlgorithm = nullSignature),
            f.resign(f.signer.authenticatedAttributes,
                signatureAlgorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA384))).forEach {
            assertTrue(f.independentSignatureValid(it))
            assertFalse(f.verifies(it))
        }
    }

    @Test
    fun rejectsAttachedMultipleSignersMissingCertificateAndUnsignedAttributes() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        val data = f.signedData
        val signer = f.signer
        val variants = listOf(
            SignedData(data.digestAlgorithms, ContentInfo(CMSObjectIdentifiers.data, DEROctetString(f.input)),
                data.certificates, null, data.signerInfos),
            SignedData(data.digestAlgorithms, data.encapContentInfo, data.certificates, null,
                DERSet(arrayOf<ASN1Encodable>(signer, signer))),
            SignedData(data.digestAlgorithms, data.encapContentInfo, null, null, data.signerInfos),
        )
        variants.forEach { assertFalse(f.verifies(f.replaceData(it))) }
        assertFalse(f.verifies(f.replaceSigner(SignerInfo(signer.sid, signer.digestAlgorithm,
            signer.authenticatedAttributes, signer.digestEncryptionAlgorithm, signer.encryptedDigest,
            DERSet(Attribute(CMSAttributes.counterSignature, DERSet(signer)))))))
    }

    @Test
    fun rejectsMalformedTruncatedTrailingAndDeeplyNestedDer() {
        val f = SoftwareEcCadesTestFixtures()
        assertTrue(f.verifies())
        listOf(byteArrayOf(), byteArrayOf(0x30, 0), byteArrayOf(0x30, -128, 0, 0),
            f.encoded.copyOf(f.encoded.size - 1), f.encoded + byteArrayOf(0)).forEach {
            assertFalse(f.verifies(it))
        }
        var nested = byteArrayOf(0x05, 0)
        repeat(1_000) {
            val length = if (nested.size < 128) byteArrayOf(nested.size.toByte()) else if (nested.size < 256) {
                byteArrayOf(0x81.toByte(), nested.size.toByte())
            } else {
                byteArrayOf(0x82.toByte(), (nested.size ushr 8).toByte(), nested.size.toByte())
            }
            nested = byteArrayOf(0x30) + length + nested
        }
        assertFalse(f.verifies(nested))
    }

    private fun ASN1Set.map(transform: (ASN1Encodable) -> Attribute): List<Attribute> =
        (0 until size()).map { transform(getObjectAt(it)) }
}
