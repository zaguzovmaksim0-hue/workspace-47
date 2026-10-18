package dev.junta.firmamobile.signing

import java.security.AlgorithmParameters
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec

/** Exact mathematical domains, not an EC label or a field-size heuristic. */
internal object SoftwareEcCurvePolicy {
    private val namedParameters by lazy {
        SoftwareEcSignatureAlgorithm.entries.associateWith { algorithm ->
            AlgorithmParameters.getInstance("EC", BundledBouncyCastle.provider).run {
                init(ECGenParameterSpec(algorithm.namedCurve))
                getParameterSpec(ECParameterSpec::class.java)
            }
        }
    }

    fun parameters(algorithm: SoftwareEcSignatureAlgorithm): ECParameterSpec =
        namedParameters.getValue(algorithm)

    fun privateSpec(key: PrivateKey, expected: ECParameterSpec): ECPrivateKeySpec? = try {
        if (key !is ECPrivateKey || !matches(key.params, expected)) {
            null
        } else {
            val scalar = key.s
            if (scalar == null || scalar.signum() <= 0 || scalar >= expected.order) {
                null
            } else {
                ECPrivateKeySpec(scalar, expected)
            }
        }
    } catch (_: Exception) {
        // Opaque handles without a readable scalar/explicit parameters are unsupported.
        null
    }

    fun publicSpec(certificate: X509Certificate, expected: ECParameterSpec): ECPublicKeySpec? = try {
        val key = certificate.publicKey
        if (key !is ECPublicKey || !matches(key.params, expected)) {
            null
        } else {
            ECPublicKeySpec(key.w, expected)
        }
    } catch (_: Exception) {
        null
    }

    private fun matches(actual: ECParameterSpec?, expected: ECParameterSpec): Boolean =
        actual != null &&
            actual.curve == expected.curve &&
            actual.generator == expected.generator &&
            actual.order == expected.order &&
            actual.cofactor == expected.cofactor
}
