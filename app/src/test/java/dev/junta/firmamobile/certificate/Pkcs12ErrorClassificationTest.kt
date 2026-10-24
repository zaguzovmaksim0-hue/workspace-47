package dev.junta.firmamobile.certificate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.NoSuchPaddingException
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe
import org.bouncycastle.asn1.pkcs.ContentInfo
import org.bouncycastle.asn1.pkcs.EncryptedData
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo
import org.bouncycastle.asn1.pkcs.Pfx
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.junit.Assert.assertEquals
import org.junit.Test

class Pkcs12ErrorClassificationTest {
    @Test
    fun unavailableAlgorithmInsideKeyRecoveryIsNotReportedAsWrongPassword() {
        val error = UnrecoverableKeyException().apply { initCause(NoSuchAlgorithmException()) }
        assertEquals(
            CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION,
            classifyPkcs12Failure(IOException(error), null),
        )
        assertEquals(
            CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION,
            classifyPkcs12Failure(IOException(NoSuchPaddingException()), null),
        )
    }

    @Test
    fun truncatedKeyEncodingIsNotReportedAsWrongPassword() {
        val error = UnrecoverableKeyException().apply { initCause(EOFException()) }
        assertEquals(CertificateErrorCode.INVALID_PKCS12_FILE, classifyPkcs12Failure(error, null))
    }

    @Test
    fun lostCausesAndBadPaddingRemainAmbiguousRegardlessOfMessage() {
        listOf(
            IOException("wrong password: /private/person.p12"),
            IOException("unsupported AES-256 PBES2"),
            IOException(BadPaddingException()),
            IOException(UnrecoverableKeyException()),
        ).forEach { error ->
            assertEquals(CertificateErrorCode.INVALID_PASSWORD_OR_FILE, classifyPkcs12Failure(error, null))
        }
    }

    @Test
    fun cyclicCauseChainTerminatesWithoutInspectingMessages() {
        val first = IOException()
        val second = IOException(first)
        first.initCause(second)
        assertEquals(CertificateErrorCode.INVALID_PASSWORD_OR_FILE, classifyPkcs12Failure(first, null))
    }

    @Test
    fun outerLengthValidationRejectsOverflowButDoesNotGuessAboutBer() {
        val overflowing = byteArrayOf(0x30, 0x89.toByte()) + ByteArray(9) { 0xff.toByte() }
        assertEquals(
            CertificateErrorCode.INVALID_PKCS12_FILE,
            classifyPkcs12Failure(IOException(), overflowing),
        )
        assertEquals(
            CertificateErrorCode.INVALID_PASSWORD_OR_FILE,
            classifyPkcs12Failure(IOException(), byteArrayOf(0x30, 0x80.toByte(), 0, 0)),
        )
    }

    @Test
    fun emptyAndTruncatedContainersAreInvalidFiles() = runBlocking {
        val bytes = TestCertificateFactory.validRsa()
        listOf(byteArrayOf(), byteArrayOf(0x30), bytes.copyOf(bytes.size - 1)).forEach { malformed ->
            assertEquals(
                CertificateLoadResult.Failure(CertificateErrorCode.INVALID_PKCS12_FILE),
                Pkcs12Loader().load(ByteArrayInputStream(malformed), null, TestCertificateFactory.password()),
            )
        }
    }

    @Test
    fun unavailablePrivateKeyEncryptionRetainsTypedCause() = runBlocking {
        val password = TestCertificateFactory.password()
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, password) }
        val encryptedKey = EncryptedPrivateKeyInfo(
            AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1")),
            byteArrayOf(1, 2, 3, 4),
        )
        keyStore.setKeyEntry("synthetic", encryptedKey.encoded, arrayOf(TestCertificateFactory.digitalSignatureCertificate()))
        val bytes = ByteArrayOutputStream().use { output ->
            keyStore.store(output, password)
            output.toByteArray()
        }
        assertEquals(
            CertificateLoadResult.Failure(CertificateErrorCode.UNSUPPORTED_PKCS12_ENCRYPTION),
            Pkcs12Loader().load(ByteArrayInputStream(bytes), null, password),
        )
    }

    @Test
    fun flattenedUnavailableEncryptionRemainsAmbiguousInsteadOfBlamingPassword() = runBlocking {
        val pfx = Pfx.getInstance(TestCertificateFactory.validRsa())
        val safe = AuthenticatedSafe.getInstance(ASN1OctetString.getInstance(pfx.authSafe.content).octets)
        val contents = safe.contentInfo.map { info ->
            if (info.contentType == PKCSObjectIdentifiers.encryptedData) {
                val encrypted = EncryptedData.getInstance(info.content)
                val unsupported = AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1"))
                ContentInfo(info.contentType, EncryptedData(encrypted.contentType, unsupported, encrypted.content))
            } else info
        }.toTypedArray()
        // No MAC: the test exercises encryption lookup rather than a deliberate MAC mismatch.
        val bytes = Pfx(ContentInfo(
            PKCSObjectIdentifiers.data,
            DEROctetString(AuthenticatedSafe(contents).encoded),
        ), null).encoded
        assertEquals(
            CertificateLoadResult.Failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE),
            Pkcs12Loader().load(ByteArrayInputStream(bytes), null, TestCertificateFactory.password()),
        )
    }
}
