package dev.junta.firmamobile.verification

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.GeneralName
import org.junit.Test

class OfflineCadesStructureTest {
    @Test
    fun refusesMultipleCertificateIdsAndPolicyExtras() {
        val fixture = OfflineCadesFixtures()
        val certs = fixture.sequence(fixture.certId())
        val policy = fixture.sequence(fixture.sequence(NISTObjectIdentifiers.id_sha256))
        val values = listOf(
            fixture.sequence(fixture.sequence(fixture.certId(), fixture.certId())),
            fixture.sequence(certs, policy),
            fixture.sequence(certs, policy, ASN1Integer(1)),
        )
        values.forEach { value ->
            val binding = fixture.attribute(SIGNING_CERTIFICATE, value)
            fixture.assertRejected(OfflineCmsStatus.UNSUPPORTED, fixture.signed(binding))
        }
    }

    @Test
    fun rejectsMalformedCertificateAndAlgorithmFields() {
        val fixture = OfflineCadesFixtures()
        val hash = DEROctetString(fixture.certificateHash())
        val sha256 = NISTObjectIdentifiers.id_sha256
        val certIds = listOf(
            fixture.sequence(),
            fixture.sequence(ASN1Integer(1)),
            fixture.sequence(fixture.sequence(sha256)),
            fixture.sequence(fixture.sequence(), hash),
            fixture.sequence(fixture.sequence(sha256, DERNull.INSTANCE, DERNull.INSTANCE), hash),
            fixture.sequence(hash, fixture.issuerSerial(), ASN1Integer(1)),
        )
        certIds.forEach { certId ->
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(fixture.binding(certId)))
        }
        val invalidBindings = listOf(
            fixture.sequence(),
            fixture.sequence(fixture.sequence()),
            DEROctetString(byteArrayOf(1)),
        )
        invalidBindings.forEach { value ->
            val binding = fixture.attribute(SIGNING_CERTIFICATE, value)
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(binding))
        }
    }

    @Test
    fun rejectsDuplicateAndNonSingletonEssAttributes() {
        val fixture = OfflineCadesFixtures()
        val binding = fixture.binding()
        fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(binding, binding))
        val value = binding.getAttrValues().getObjectAt(0)
        listOf(
            fixture.attribute(SIGNING_CERTIFICATE),
            fixture.attribute(SIGNING_CERTIFICATE, value, value),
        ).forEach { attribute ->
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(attribute))
        }
    }

    @Test
    fun rejectsMalformedIssuerSerialFields() {
        val fixture = OfflineCadesFixtures()
        val issuer = fixture.issuerSerial().getObjectAt(0)
        val serial = ASN1Integer(fixture.certificate.getSerialNumber())
        val fields = listOf(
            fixture.sequence(issuer),
            fixture.sequence(issuer, DEROctetString(byteArrayOf(1))),
            fixture.sequence(fixture.sequence(), serial),
            fixture.sequence(
                fixture.sequence(
                    // X500Name is a CHOICE; pass its sequence so BC does not force explicit tagging.
                    DERTaggedObject(
                        false,
                        GeneralName.directoryName,
                        fixture.certificate.getIssuer().toASN1Primitive(),
                    ),
                ),
                serial,
            ),
        )
        fields.forEach { issuerSerial ->
            val binding = fixture.binding(fixture.certId(issuerSerial = issuerSerial))
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(binding))
        }
    }

    @Test
    fun refusesMultipleIssuerNamesOtherNamesAndIssuerUid() {
        val fixture = OfflineCadesFixtures()
        val issuer = fixture.issuerSerial().getObjectAt(0)
        val serial = ASN1Integer(fixture.certificate.getSerialNumber())
        val directoryName = DERTaggedObject(true, GeneralName.directoryName, fixture.certificate.getIssuer())
        val otherName = DERTaggedObject(false, GeneralName.rfc822Name, DERIA5String("test@example.invalid"))
        val fields = listOf(
            fixture.sequence(fixture.sequence(directoryName, directoryName), serial),
            fixture.sequence(fixture.sequence(otherName), serial),
            fixture.sequence(issuer, serial, DERBitString(byteArrayOf(1))),
        )
        fields.forEach { issuerSerial ->
            val binding = fixture.binding(fixture.certId(issuerSerial = issuerSerial))
            fixture.assertRejected(OfflineCmsStatus.UNSUPPORTED, fixture.signed(binding))
        }
    }

    @Test
    fun preservesRequiredAttributesAndRejectsUnknownFields() {
        val fixture = OfflineCadesFixtures()
        val cms = fixture.signed(fixture.binding())
        listOf(CMSAttributes.contentType, CMSAttributes.messageDigest).forEach { oid ->
            val altered = OfflineCmsAttributeFixtures.removeSignedAttribute(cms, oid)
            fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.resign(altered))
        }
        val binding = fixture.binding()
        val extraField = fixture.sequence(SIGNING_CERTIFICATE, binding.getAttrValues(), ASN1Integer(1))
        fixture.assertRejected(OfflineCmsStatus.INVALID, fixture.signed(extraField))
        val legacy = fixture.attribute(PKCSObjectIdentifiers.id_aa_signingCertificate, DEROctetString(byteArrayOf(1)))
        fixture.assertRejected(OfflineCmsStatus.UNSUPPORTED, fixture.signed(legacy))
    }

    private companion object {
        val SIGNING_CERTIFICATE = PKCSObjectIdentifiers.id_aa_signingCertificateV2
    }
}
