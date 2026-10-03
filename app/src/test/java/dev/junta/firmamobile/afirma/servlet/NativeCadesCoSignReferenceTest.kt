package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.OpaqueRsaFixture
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.security.MessageDigest
import java.util.Date
import java.util.Hashtable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.ESSCertID
import org.bouncycastle.asn1.ess.SigningCertificate
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.IssuerSerial
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.*
import org.junit.Test

/** Legacy CAdES certificate binding, not bare CMS or trust-chain validation. */
class NativeCadesCoSignReferenceTest {
    @Test fun supportedSigningCertificateV1WithIssuerSerialRemainsVerifiableAfterCosign() {
        val input = legacy(reference = true, issuerMismatch = false, hashMismatch = false)
        val f = NativeCadesCoSignFixtures
        try {
            val result = NativeCadesCoSignEngine(f.clock).cosign(input, f.second.identity, SigningAlgorithm.SHA256_WITH_RSA)
            assertTrue(result.toString(), result is LocalSignatureResult.Success)
            (result as LocalSignatureResult.Success).signature.use { it.withBytes { bytes ->
                val cms = CMSSignedData(bytes)
                assertEquals(2, cms.signerInfos.size())
                for (signer in cms.signerInfos.signers) {
                    val cert = cms.certificates.getMatches(null).filter(signer.sid::match).single()
                    assertTrue(signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(cert)))
                }
            } }
        } finally { input.fill(0) }
    }

    @Test fun bareCmsWithoutCadesCertificateReferenceIsNotSilentlyTreatedAsCades() = OpaqueRsaFixture.use { key ->
        val input=legacy(reference=false,issuerMismatch=false,hashMismatch=false)
        try {
            assertTrue(NativeCadesCoSignEngine(NativeCadesCoSignFixtures.clock).cosign(input,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
        } finally { input.fill(0) }
    }

    @Test fun aCryptographicallyValidSignatureWithTheWrongEssCertificateHashIsRejected() = OpaqueRsaFixture.use { key ->
        val input=legacy(reference=true,issuerMismatch=false,hashMismatch=true)
        try {
            assertTrue(NativeCadesCoSignEngine(NativeCadesCoSignFixtures.clock).cosign(input,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
        } finally { input.fill(0) }
    }

    @Test fun aValidCertificateHashDoesNotOverrideAConflictingSignedIssuerSerial() = OpaqueRsaFixture.use { key ->
        val input=legacy(reference=true,issuerMismatch=true,hashMismatch=false)
        try {
            assertTrue(NativeCadesCoSignEngine(NativeCadesCoSignFixtures.clock).cosign(input,key.identity,SigningAlgorithm.SHA256_WITH_RSA) is LocalSignatureResult.Failure)
            assertEquals(0,key.signatures.get())
        } finally { input.fill(0) }
    }

    private fun legacy(reference:Boolean,issuerMismatch:Boolean,hashMismatch:Boolean):ByteArray {
        val f=NativeCadesCoSignFixtures;val provider=BouncyCastleProvider();val cert=f.first.identity.certificate
        val holder=JcaX509CertificateHolder(cert);val content=f.data
        val attributes=Hashtable<ASN1ObjectIdentifier,Attribute>()
        attributes[CMSAttributes.signingTime]=Attribute(CMSAttributes.signingTime,DERSet(Time(Date.from(f.clock.instant()))))
        if(reference) {
            val hash=MessageDigest.getInstance("SHA-1").digest(cert.encoded)
            if(hashMismatch) hash[0]=(hash[0].toInt() xor 1).toByte()
            val issuer=IssuerSerial(GeneralNames(GeneralName(holder.issuer)),holder.serialNumber.add(if(issuerMismatch)java.math.BigInteger.ONE else java.math.BigInteger.ZERO))
            attributes[PKCSObjectIdentifiers.id_aa_signingCertificate]=Attribute(PKCSObjectIdentifiers.id_aa_signingCertificate,DERSet(SigningCertificate(ESSCertID(hash,issuer))))
        }
        val signer=f.first.identity.withPrivateKey { JcaContentSignerBuilder("SHA1withRSA").setProvider(provider).build(it) }
        val generator=CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(provider).build())
            .setSignedAttributeGenerator(DefaultSignedAttributeTableGenerator(AttributeTable(attributes))).build(signer,holder))
        generator.addCertificates(JcaCertStore(listOf(cert)))
        return try { generator.generate(CMSProcessableByteArray(content),true).encoded } finally { content.fill(0) }
    }
}
