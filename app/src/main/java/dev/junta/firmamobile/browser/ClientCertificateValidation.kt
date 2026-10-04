package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.certificate.UnlockedIdentity
import java.security.MessageDigest
import java.time.Clock
import java.util.Date
import javax.security.auth.x500.X500Principal

/** Shared material checks; host, ownership and explicit consent belong to the caller. */
internal fun clientCertificateRejection(
    request: ClientCertRequest,
    identity: UnlockedIdentity,
    allowedKeyAlgorithms: Set<String> = setOf("RSA", "EC"),
    requireOfferedKeyTypeMatch: Boolean = true,
    requireDigitalSignatureKeyUsage: Boolean = true,
    requireTlsClientAuthExtendedKeyUsage: Boolean = true,
    allowEmptyIssuerList: Boolean = true,
    clock: Clock = Clock.systemUTC(),
): ClientAuthRequestDiagnostic? {
    val certificate = identity.certificate
    val algorithm = certificate.publicKey.algorithm.uppercase(java.util.Locale.ROOT)
    if (algorithm !in allowedKeyAlgorithms) {
        return ClientAuthRequestDiagnostic.REJECTED_ALGORITHM
    }
    if (requireOfferedKeyTypeMatch) {
        val offeredKeyTypes = request.keyTypes?.map { it.uppercase(java.util.Locale.ROOT) }?.toSet().orEmpty()
        if (offeredKeyTypes.isEmpty() || offeredKeyTypes.none { it == algorithm || (algorithm == "EC" && it == "ECDSA") }) {
            return ClientAuthRequestDiagnostic.REJECTED_KEY_TYPE
        }
    }
    try {
        certificate.checkValidity(Date.from(clock.instant()))
    } catch (_: Exception) {
        return ClientAuthRequestDiagnostic.REJECTED_VALIDITY
    }
    val keyUsage = certificate.keyUsage
    if (requireDigitalSignatureKeyUsage &&
        keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])
    ) {
        return ClientAuthRequestDiagnostic.REJECTED_KEY_USAGE
    }
    val extendedKeyUsage = try {
        certificate.extendedKeyUsage
    } catch (_: Exception) {
        return ClientAuthRequestDiagnostic.REJECTED_EKU
    }
    if (requireTlsClientAuthExtendedKeyUsage &&
        extendedKeyUsage != null &&
        TLS_CLIENT_AUTH_OID !in extendedKeyUsage && ANY_EXTENDED_KEY_USAGE_OID !in extendedKeyUsage
    ) {
        return ClientAuthRequestDiagnostic.REJECTED_EKU
    }
    val principals = request.principals?.toList().orEmpty()
    if (principals.isEmpty()) {
        return if (allowEmptyIssuerList) {
            null
        } else {
            ClientAuthRequestDiagnostic.REJECTED_ISSUER
        }
    }
    val acceptableIssuerDer = principals.mapNotNull { principal ->
        (principal as? X500Principal)?.encoded
    }
    if (acceptableIssuerDer.size != principals.size) {
        return ClientAuthRequestDiagnostic.REJECTED_ISSUER
    }
    val chain = identity.chain.ifEmpty { listOf(certificate) }
    val issuerMatches = chain.any { chainCertificate ->
        val issuer = chainCertificate.issuerX500Principal.encoded
        acceptableIssuerDer.any { acceptable -> MessageDigest.isEqual(issuer, acceptable) }
    }
    return if (issuerMatches) {
        null
    } else {
        ClientAuthRequestDiagnostic.REJECTED_ISSUER
    }
}

private const val TLS_CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2"
private const val ANY_EXTENDED_KEY_USAGE_OID = "2.5.29.37.0"
