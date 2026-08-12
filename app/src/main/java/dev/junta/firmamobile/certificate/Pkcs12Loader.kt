package dev.junta.firmamobile.certificate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.Date
import javax.security.auth.x500.X500Principal

class Pkcs12Loader internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val sensitiveCopyObserver: SensitiveCopyObserver = SensitiveCopyObserver { _, _ -> },
) {
    suspend fun load(
        input: InputStream,
        declaredSize: Long?,
        password: CharArray,
    ): CertificateLoadResult {
        if (declaredSize != null && declaredSize >= 0 && declaredSize > MAX_INPUT_BYTES) {
            return CertificateLoadResult.Failure(CertificateErrorCode.FILE_TOO_LARGE)
        }

        val passwordCopy = password.copyOf()
        var pkcs12Bytes: ByteArray? = null
        return try {
            pkcs12Bytes = readBounded(input)
                ?: return CertificateLoadResult.Failure(CertificateErrorCode.FILE_TOO_LARGE)
            loadKeyStore(pkcs12Bytes, passwordCopy)
        } catch (_: Exception) {
            CertificateLoadResult.Failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE)
        } finally {
            passwordCopy.clearAndReport(SensitiveCopyKind.PASSWORD)
            pkcs12Bytes?.clearAndReport(SensitiveCopyKind.PKCS12_BYTES)
        }
    }

    private fun loadKeyStore(
        pkcs12Bytes: ByteArray,
        password: CharArray,
    ): CertificateLoadResult {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(ByteArrayInputStream(pkcs12Bytes), password)

        val aliases = mutableListOf<String>()
        val enumeration = keyStore.aliases()
        while (enumeration.hasMoreElements()) {
            if (aliases.size == MAX_ALIASES) {
                return CertificateLoadResult.Failure(CertificateErrorCode.TOO_MANY_ENTRIES)
            }
            aliases += enumeration.nextElement()
        }

        val privateEntries = aliases.mapNotNull { alias ->
            if (!keyStore.isKeyEntry(alias)) {
                return@mapNotNull null
            }
            val protection = KeyStore.PasswordProtection(password)
            try {
                keyStore.getEntry(alias, protection) as? KeyStore.PrivateKeyEntry
            } finally {
                protection.destroy()
            }
        }
        if (privateEntries.isEmpty()) {
            return CertificateLoadResult.Failure(CertificateErrorCode.PRIVATE_KEY_MISSING)
        }
        if (privateEntries.size > 1) {
            return CertificateLoadResult.Failure(CertificateErrorCode.MULTIPLE_PRIVATE_KEYS)
        }

        val entry = privateEntries.single()
        val rawChain = entry.certificateChain
        if (rawChain.size > MAX_CHAIN_LENGTH) {
            return CertificateLoadResult.Failure(CertificateErrorCode.CHAIN_TOO_LONG)
        }
        val chain = rawChain.map { certificate ->
            certificate as? X509Certificate
                ?: return CertificateLoadResult.Failure(CertificateErrorCode.CERTIFICATE_NOT_X509)
        }
        val certificate = entry.certificate as? X509Certificate
            ?: return CertificateLoadResult.Failure(CertificateErrorCode.CERTIFICATE_NOT_X509)
        val privateKey = entry.privateKey

        if (!privateKey.algorithm.equals("RSA", ignoreCase = true) ||
            !certificate.publicKey.algorithm.equals("RSA", ignoreCase = true)
        ) {
            return CertificateLoadResult.Failure(CertificateErrorCode.UNSUPPORTED_KEY_TYPE)
        }

        try {
            certificate.checkValidity(Date.from(clock.instant()))
        } catch (_: CertificateExpiredException) {
            return CertificateLoadResult.Failure(CertificateErrorCode.CERTIFICATE_EXPIRED)
        } catch (_: CertificateNotYetValidException) {
            return CertificateLoadResult.Failure(CertificateErrorCode.CERTIFICATE_NOT_YET_VALID)
        }

        val keyUsage = certificate.keyUsage
        if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) {
            return CertificateLoadResult.Failure(CertificateErrorCode.KEY_USAGE_NOT_PERMITTED)
        }
        if (!privateKeyMatchesCertificate(privateKey, certificate)) {
            return CertificateLoadResult.Failure(CertificateErrorCode.KEY_CERTIFICATE_MISMATCH)
        }

        val summary = CertificateSummary(
            ownerName = commonName(certificate.subjectX500Principal),
            issuerName = commonName(certificate.issuerX500Principal),
            validFrom = certificate.notBefore.toInstant(),
            validUntil = certificate.notAfter.toInstant(),
        )
        return CertificateLoadResult.Success(
            UnlockedIdentity(
                privateKey = privateKey,
                certificate = certificate,
                chain = chain,
                summary = summary,
            ),
        )
    }

    private fun privateKeyMatchesCertificate(
        privateKey: java.security.PrivateKey,
        certificate: X509Certificate,
    ): Boolean {
        val challenge = ByteArray(CHALLENGE_BYTES)
        var signatureBytes: ByteArray? = null
        return try {
            secureRandom.nextBytes(challenge)
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey, secureRandom)
            signer.update(challenge)
            signatureBytes = signer.sign()

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(certificate.publicKey)
            verifier.update(challenge)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        } finally {
            challenge.clearAndReport(SensitiveCopyKind.KEY_MATCH_CHALLENGE)
            signatureBytes?.fill(0)
        }
    }

    private fun commonName(principal: X500Principal): String {
        val distinguishedName = principal.getName(X500Principal.RFC2253)
        val commonName = splitRdns(distinguishedName)
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.replace(Regex("\\\\([,+=<>#;\\\\\" ])"), "\$1")
            ?.take(MAX_DISPLAY_NAME_LENGTH)
        return commonName.orEmpty().ifBlank {
            distinguishedName.take(MAX_DISPLAY_NAME_LENGTH)
        }
    }

    private fun splitRdns(distinguishedName: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        distinguishedName.forEach { character ->
            when {
                escaped -> {
                    current.append('\\').append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == ',' || character == '+' -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        if (current.isNotEmpty()) parts += current.toString().trim()
        return parts
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ClearingByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return try {
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_INPUT_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } finally {
            buffer.fill(0)
            output.clear()
        }
    }

    private fun CharArray.clearAndReport(kind: SensitiveCopyKind) {
        fill('\u0000')
        sensitiveCopyObserver.onCleared(kind, all { it == '\u0000' })
    }

    private fun ByteArray.clearAndReport(kind: SensitiveCopyKind) {
        fill(0)
        sensitiveCopyObserver.onCleared(kind, all { it == 0.toByte() })
    }

    private class ClearingByteArrayOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }
    }

    companion object {
        const val MAX_INPUT_BYTES = 10 * 1024 * 1024
        private const val MAX_ALIASES = 32
        private const val MAX_CHAIN_LENGTH = 16
        private const val CHALLENGE_BYTES = 32
        private const val MAX_DISPLAY_NAME_LENGTH = 256
    }
}
