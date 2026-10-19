package dev.junta.firmamobile.verification

import dev.junta.firmamobile.signing.BundledBouncyCastle
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers

internal object OfflineCmsEcDomainPolicy {
    fun validatePublicKey(digestOid: String, signatureOid: String, publicKey: PublicKey) {
        val curveName = curveName(digestOid, signatureOid)
        val ecKey = publicKey as? ECPublicKey ?: throw OfflineCmsUnsupportedProfileException()
        val expected = namedCurveParameters(curveName)
        if (!sameEcDomain(ecKey.getParams(), expected)) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun curveName(digestOid: String, signatureOid: String): String {
        return when {
            signatureOid == X9ObjectIdentifiers.ecdsa_with_SHA256.getId() &&
                digestOid == NISTObjectIdentifiers.id_sha256.getId() -> "secp256r1"
            signatureOid == X9ObjectIdentifiers.ecdsa_with_SHA384.getId() &&
                digestOid == NISTObjectIdentifiers.id_sha384.getId() -> "secp384r1"
            else -> throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun namedCurveParameters(name: String): ECParameterSpec {
        return try {
            val parameters = AlgorithmParameters.getInstance("EC", BundledBouncyCastle.provider)
            parameters.init(ECGenParameterSpec(name))
            parameters.getParameterSpec(ECParameterSpec::class.java)
        } catch (expected: GeneralSecurityException) {
            throw OfflineCmsUnsupportedProfileException()
        }
    }

    private fun sameEcDomain(actual: ECParameterSpec, expected: ECParameterSpec): Boolean {
        return actual.getCurve() == expected.getCurve() &&
            actual.getGenerator() == expected.getGenerator() &&
            actual.getOrder() == expected.getOrder() &&
            actual.getCofactor() == expected.getCofactor()
    }
}
