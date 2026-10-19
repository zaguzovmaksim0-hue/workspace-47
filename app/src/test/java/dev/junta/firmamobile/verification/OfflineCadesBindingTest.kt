package dev.junta.firmamobile.verification

import java.math.BigInteger
import java.util.Date
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.Test

class OfflineCadesBindingTest {
    @Test
    fun verifiesDefaultExplicitAndDeclaredCertificateHashes() {
        val fixture = OfflineCadesFixtures()
        val sha256 = NISTObjectIdentifiers.id_sha256
        val sha384 = NISTObjectIdentifiers.id_sha384
        val certIds = listOf(
            fixture.certId(),
            fixture.certId(algorithm = AlgorithmIdentifier(sha256)),
            fixture.certId(algorithm = AlgorithmIdentifier(sha256, DERNull.INSTANCE)),
            fixture.certId(algorithm = AlgorithmIdentifier(sha384), hash = fixture.certificateHash(SHA384_NAME)),
            fixture.certId(
                algorithm = AlgorithmIdentifier(sha384, DERNull.INSTANCE),
                hash = fixture.certificateHash(SHA384_NAME),
            ),
        )
        certIds.forEach { certId ->
            OfflineCmsAssertions.assertVerified(fixture.signed(fixture.binding(certId)))
        }
    }

    @Test
    fun verifiesExplicitDeclaredSha512CertificateHash() {
        val fixture = OfflineCadesFixtures(OfflineCmsFixtures.signer(signatureAlgorithm = "SHA512withRSA"))
        val certId = fixture.certId(
            algorithm = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512),
            hash = fixture.certificateHash("SHA-512"),
        )
        OfflineCmsAssertions.assertVerified(fixture.signed(fixture.binding(certId)))
    }

    @Test
    fun rejectsWrongHashesAndDefaultAlgorithmConfusion() {
        val fixture = OfflineCadesFixtures()
        val changed = fixture.certificateHash()
        changed[0] = (changed[0].toInt() xor 1).toByte()
        val certIds = listOf(
            fixture.certId(hash = changed),
            fixture.certId(hash = byteArrayOf()),
            fixture.certId(hash = fixture.certificateHash(SHA384_NAME)),
            fixture.certId(algorithm = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384)),
        )
        certIds.forEach { certId ->
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(certId)))
        }
    }

    @Test
    fun hashesTheUniqueCertificateBytesEvenWithTheSameSidAndKey() {
        val original = OfflineCadesFixtures()
        val replacement = OfflineCmsFixtures.certificate(
            original.material.keyPair,
            original.certificate.getSerialNumber().longValueExact(),
            notBefore = Date(DIFFERENT_CERT_START_MILLIS),
        )
        val fixture = OfflineCadesFixtures(original.material.copy(certificate = replacement))
        val certId = fixture.certId(hash = original.certificateHash())
        fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(certId)))
    }

    @Test
    fun rejectsUnsupportedAndUndeclaredHashAlgorithms() {
        val fixture = OfflineCadesFixtures()
        val certIds = listOf(
            fixture.certId(
                algorithm = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512),
                hash = fixture.certificateHash("SHA-512"),
            ),
            fixture.certId(algorithm = AlgorithmIdentifier(ASN1ObjectIdentifier(UNKNOWN_DIGEST_OID))),
            fixture.certId(
                algorithm = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DEROctetString(byteArrayOf(1))),
            ),
        )
        certIds.forEach { certId ->
            fixture.assertRejected(OfflineCmsStatus.UNSUPPORTED, fixture.signed(fixture.binding(certId)))
        }
    }

    @Test
    fun matchesOptionalIssuerAndSerial() {
        val fixture = OfflineCadesFixtures()
        val certId = fixture.certId(issuerSerial = fixture.issuerSerial())
        OfflineCmsAssertions.assertVerified(fixture.signed(fixture.binding(certId)))
    }

    @Test
    fun rejectsMismatchedOrNonPositiveIssuerSerials() {
        val fixture = OfflineCadesFixtures()
        val serial = fixture.certificate.getSerialNumber()
        val issuers = listOf(
            fixture.issuerSerial(issuer = X500Name("CN=Different issuer")),
            fixture.issuerSerial(serial = serial.add(BigInteger.ONE)),
            fixture.issuerSerial(serial = BigInteger.ZERO),
            fixture.issuerSerial(serial = BigInteger.ONE.negate()),
        )
        issuers.forEach { issuerSerial ->
            val certId = fixture.certId(issuerSerial = issuerSerial)
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(certId)))
        }
    }

    @Test
    fun neverUsesEssToDisambiguateSidMatches() {
        val original = OfflineCadesFixtures()
        val duplicateSid = OfflineCmsFixtures.certificate(
            original.material.keyPair,
            original.certificate.getSerialNumber().longValueExact(),
            notBefore = Date(DIFFERENT_CERT_START_MILLIS),
        )
        val fixture = OfflineCadesFixtures(
            original.material,
            listOf(original.material.certificate, duplicateSid),
        )
        fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding()))
    }

    private companion object {
        const val SHA384_NAME = "SHA-384"
        const val UNKNOWN_DIGEST_OID = "1.2.3.4.99"
        const val DIFFERENT_CERT_START_MILLIS = 1_000L
    }
}
