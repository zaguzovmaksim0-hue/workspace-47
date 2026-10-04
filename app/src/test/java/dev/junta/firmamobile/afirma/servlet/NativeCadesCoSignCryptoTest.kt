package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.time.Clock
import java.time.Duration
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test

class NativeCadesCoSignCryptoTest {
    private val f get() = NativeCadesCoSignFixtures
    private val co get() = NativeCadesCoSignEngine(f.clock)

    @Test fun attachedApprovalSupportsEveryRequestedDigestAndPreservesOldSignerRecords() {
        for (algorithm in SigningAlgorithm.entries) {
            val before = f.signed(false); val saved = before.copyOf()
            val result = bytes(co.cosign(before, f.second.identity, algorithm))
            try {
                verifyAll(result, f.data, 2, detached = false)
                assertPreserved(before, result)
                assertArrayEquals(saved, before)
                assertEquals(setOf(NativeCadesEngine.digestOid(SigningAlgorithm.SHA256_WITH_RSA), NativeCadesEngine.digestOid(algorithm)),
                    CMSSignedData(result).digestAlgorithmIDs.map { it.algorithm.id }.toSet())
            } finally { before.fill(0); saved.fill(0); result.fill(0) }
        }
        assertEquals(0, f.first.encodedReads.get()); assertEquals(0, f.second.encodedReads.get())
    }

    @Test fun detachedApprovalUsesTheSameVerifiedDigestAndStaysDetached() {
        for (algorithm in SigningAlgorithm.entries) {
            val before = f.signed(true, algorithm)
            val result = bytes(co.cosign(before, f.second.identity, algorithm))
            try {
                assertNull(CMSSignedData(result).signedContent)
                verifyAll(result, f.data, 2, detached = true)
                assertPreserved(before, result)
            } finally { before.fill(0); result.fill(0) }
        }
    }

    @Test fun aThirdApprovalCanBeAddedWithoutReplacingAnyEarlierSignature() {
        val first = f.signed(false)
        val second = bytes(co.cosign(first, f.second.identity, SigningAlgorithm.SHA384_WITH_RSA))
        val third = bytes(NativeCadesCoSignEngine(Clock.offset(f.clock, Duration.ofSeconds(2))).cosign(second, f.first.identity, SigningAlgorithm.SHA512_WITH_RSA))
        try {
            verifyAll(third, f.data, 3, false); assertPreserved(first, third); assertPreserved(second, third)
        } finally { first.fill(0); second.fill(0); third.fill(0) }
    }

    @Test fun unsupportedDetachedDigestCannotUseTheNewKeyOrInventAnotherFileHash() = OpaqueRsaFixture.use { key ->
        val before = f.signed(true, SigningAlgorithm.SHA256_WITH_RSA)
        try {
            assertTrue(co.cosign(before, key.identity, SigningAlgorithm.SHA512_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0, key.signatures.get()); assertEquals(0, key.initializations.get())
        } finally { before.fill(0) }
    }

    @Test fun tamperedAttachedDocumentIsRejectedBeforeCallingTheNewPrivateKey() = OpaqueRsaFixture.use { key ->
        val before = f.signed(false); val cms = data(before)
        val altered = f.data.also { it[0] = (it[0].toInt() xor 1).toByte() }
        val corrupt = ContentInfo(PKCSObjectIdentifiers.signedData, SignedData(cms.digestAlgorithms,
            ContentInfo(PKCSObjectIdentifiers.data, DEROctetString(altered)), cms.certificates, cms.crLs, cms.signerInfos)).encoded
        try {
            assertTrue(co.cosign(corrupt, key.identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0, key.signatures.get())
        } finally { before.fill(0); corrupt.fill(0); altered.fill(0) }
    }

    @Test fun tamperedOldSignatureIsRejectedEvenIfTheMessageDigestLooksCorrect() = OpaqueRsaFixture.use { key ->
        val before = f.signed(true); val cms = data(before); val old = SignerInfo.getInstance(cms.signerInfos.getObjectAt(0))
        val bad = old.encryptedDigest.octets.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val signer = SignerInfo(old.sid, old.digestAlgorithm, old.authenticatedAttributes, old.digestEncryptionAlgorithm, DEROctetString(bad), old.unauthenticatedAttributes)
        val corrupt = replaceSigners(before, listOf(signer))
        try {
            assertTrue(co.cosign(corrupt, key.identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0, key.initializations.get())
        } finally { before.fill(0); bad.fill(0); corrupt.fill(0) }
    }

    @Test fun duplicateSignerRowsDoNotCountAsAnIndependentValidHistory() = OpaqueRsaFixture.use { key ->
        val before = f.signed(false); val signer = SignerInfo.getInstance(data(before).signerInfos.getObjectAt(0))
        val duplicated = replaceSigners(before, listOf(signer, signer))
        try {
            assertTrue(co.cosign(duplicated, key.identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0, key.signatures.get())
        } finally { before.fill(0); duplicated.fill(0) }
    }

    @Test fun timestampOrCounterSignatureAttributesAreNotSilentlyDropped() = OpaqueRsaFixture.use { key ->
        val before = f.signed(false); val original = SignerInfo.getInstance(data(before).signerInfos.getObjectAt(0))
        val unsigned = DERSet(Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, DERSet(DEROctetString(byteArrayOf(1,2)))))
        val changed = SignerInfo(original.sid, original.digestAlgorithm, original.authenticatedAttributes, original.digestEncryptionAlgorithm, original.encryptedDigest, unsigned)
        val decorated = replaceSigners(before, listOf(changed))
        try {
            assertTrue(co.cosign(decorated, key.identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0, key.signatures.get())
        } finally { before.fill(0); decorated.fill(0) }
    }

    @Test fun twoDetachedSignaturesForDifferentDocumentsDoNotBecomeAConsistentEnvelope() = OpaqueRsaFixture.use { key ->
        val first = f.signed(true)
        val otherContent = "another signed document".toByteArray()
        val other = bytes(NativeCadesEngine(clock=f.clock).sign(otherContent, f.second.identity, SigningAlgorithm.SHA256_WITH_RSA, true))
        val a=data(first); val b=data(other)
        val merged = ContentInfo(PKCSObjectIdentifiers.signedData, SignedData(a.digestAlgorithms, a.encapContentInfo,
            DERSet((elements(a.certificates)+elements(b.certificates)).toTypedArray()), null,
            DERSet((elements(a.signerInfos)+elements(b.signerInfos)).toTypedArray()))).encoded
        try {
            assertTrue(co.cosign(merged, key.identity, SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
        } finally { first.fill(0); other.fill(0); otherContent.fill(0); merged.fill(0) }
    }

    @Test fun detachedHistoryWithUnrelatedDigestFamiliesRequiresOriginalContent() = OpaqueRsaFixture.use { key ->
        val first = f.signed(true, SigningAlgorithm.SHA256_WITH_RSA)
        val other = f.signed(true, SigningAlgorithm.SHA384_WITH_RSA)
        val a=data(first); val b=data(other)
        val merged = ContentInfo(PKCSObjectIdentifiers.signedData, SignedData(DERSet((elements(a.digestAlgorithms)+elements(b.digestAlgorithms)).toTypedArray()),
            a.encapContentInfo, a.certificates, null, DERSet((elements(a.signerInfos)+elements(b.signerInfos)).toTypedArray()))).encoded
        try {
            assertTrue(co.cosign(merged,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
        } finally { first.fill(0); other.fill(0); merged.fill(0) }
    }

    @Test fun explicitPackagingChangesAreNotIgnoredAndBoundsRemainEffective() = OpaqueRsaFixture.use { key ->
        val before=f.signed(true)
        try {
            assertTrue(co.cosign(before,key.identity,SigningAlgorithm.SHA256_WITH_RSA,false) is LocalSignatureResult.Failure)
            assertTrue(NativeCadesCoSignEngine(f.clock,maxInputBytes=before.size-1).cosign(before,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
            assertTrue(NativeCadesCoSignEngine(f.clock,maxOutputBytes=before.size).cosign(before,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        } finally { before.fill(0) }
    }

    @Test fun eightSignerLimitIncludesEveryExistingApproval() {
        var current=f.signed(false)
        try {
            for (i in 1..7) {
                val next=bytes(NativeCadesCoSignEngine(Clock.offset(f.clock,Duration.ofSeconds(i.toLong()))).cosign(current,f.second.identity,SigningAlgorithm.SHA256_WITH_RSA))
                current.fill(0);current=next
            }
            verifyAll(current,f.data,8,false)
            assertTrue(NativeCadesCoSignEngine(Clock.offset(f.clock,Duration.ofSeconds(9))).cosign(current,f.second.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
        } finally { current.fill(0) }
    }

    @Test fun anOpaqueProviderCanSupplyTheNewSignatureWithoutExportingItsKey() = OpaqueRsaFixture.use { key ->
        for (detached in listOf(false,true)) {
            val before=f.signed(detached)
            val result=bytes(co.cosign(before,key.identity,SigningAlgorithm.SHA256_WITH_RSA))
            try { verifyAll(result,f.data,2,detached);assertPreserved(before,result) }
            finally { before.fill(0);result.fill(0) }
        }
        assertEquals(2,key.signatures.get());assertEquals(0,key.encodingReads.get())
    }

    @Test fun corruptNewProviderOutputCannotHideBehindValidEarlierSignatures() = OpaqueRsaFixture.use { key ->
        key.corruptSignature=true
        val before=f.signed(true)
        try {
            assertTrue(co.cosign(before,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(1,key.signatures.get());assertEquals(0,key.encodingReads.get())
        } finally { before.fill(0) }
    }

    private fun bytes(result: LocalSignatureResult): ByteArray {
        assertTrue(result.toString(),result is LocalSignatureResult.Success)
        return (result as LocalSignatureResult.Success).signature.use { it.withBytes(ByteArray::copyOf) }
    }
    private fun data(bytes:ByteArray)=SignedData.getInstance(CMSSignedData(bytes).toASN1Structure().content)
    private fun elements(set:org.bouncycastle.asn1.ASN1Set)=(0 until set.size()).map(set::getObjectAt)
    private fun replaceSigners(bytes:ByteArray,signers:List<SignerInfo>):ByteArray {
        val old=data(bytes)
        return ContentInfo(PKCSObjectIdentifiers.signedData,SignedData(old.digestAlgorithms,old.encapContentInfo,old.certificates,old.crLs,DERSet(signers.toTypedArray()))).encoded
    }
    private fun verifyAll(bytes:ByteArray,payload:ByteArray,count:Int,detached:Boolean) {
        try {
            val original=CMSSignedData(bytes);assertEquals(detached,original.signedContent==null)
            val cms=if(detached)CMSSignedData(CMSProcessableByteArray(payload),bytes) else original
            assertEquals(count,cms.signerInfos.size())
            if(!detached)assertArrayEquals(payload,cms.signedContent.content as ByteArray)
            for(signer in cms.signerInfos.signers) {
                val cert=cms.certificates.getMatches(null).filter(signer.sid::match).single()
                assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(cert)))
            }
        } finally { payload.fill(0) }
    }
    private fun assertPreserved(before:ByteArray,after:ByteArray) {
        val a=data(before);val b=data(after)
        for(signer in elements(a.signerInfos)) assertTrue(elements(b.signerInfos).any { it.toASN1Primitive().getEncoded(ASN1Encoding.DER).contentEquals(signer.toASN1Primitive().getEncoded(ASN1Encoding.DER)) })
        for(cert in elements(a.certificates)) assertTrue(elements(b.certificates).any { it.toASN1Primitive().getEncoded(ASN1Encoding.DER).contentEquals(cert.toASN1Primitive().getEncoded(ASN1Encoding.DER)) })
        assertArrayEquals(a.encapContentInfo.getEncoded(ASN1Encoding.DER),b.encapContentInfo.getEncoded(ASN1Encoding.DER))
    }
}
