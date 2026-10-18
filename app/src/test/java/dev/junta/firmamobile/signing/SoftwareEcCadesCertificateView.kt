package dev.junta.firmamobile.signing

import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date

/** Synthetic certificate with deliberate key/encoding views; no certificate trust claim. */
internal class SoftwareEcCadesCertificateView(
    private val delegate: X509Certificate,
    private val key: PublicKey = delegate.publicKey,
    private val encoding: () -> ByteArray = { delegate.encoded },
) : X509Certificate() {
    override fun getPublicKey(): PublicKey = key
    override fun getEncoded(): ByteArray = encoding()
    override fun verify(key: PublicKey) = delegate.verify(key)
    override fun verify(key: PublicKey, provider: String) = delegate.verify(key, provider)
    override fun toString(): String = "Synthetic EC certificate public-key view"
    override fun checkValidity() = delegate.checkValidity()
    override fun checkValidity(date: Date) = delegate.checkValidity(date)
    override fun getVersion(): Int = delegate.version
    override fun getSerialNumber(): BigInteger = delegate.serialNumber
    @Deprecated("Use issuerX500Principal instead")
    override fun getIssuerDN(): Principal = delegate.issuerX500Principal
    @Deprecated("Use subjectX500Principal instead")
    override fun getSubjectDN(): Principal = delegate.subjectX500Principal
    override fun getNotBefore(): Date = delegate.notBefore
    override fun getNotAfter(): Date = delegate.notAfter
    override fun getTBSCertificate(): ByteArray = delegate.tbsCertificate
    override fun getSignature(): ByteArray = delegate.signature
    override fun getSigAlgName(): String = delegate.sigAlgName
    override fun getSigAlgOID(): String = delegate.sigAlgOID
    override fun getSigAlgParams(): ByteArray? = delegate.sigAlgParams
    override fun getIssuerUniqueID(): BooleanArray? = delegate.issuerUniqueID
    override fun getSubjectUniqueID(): BooleanArray? = delegate.subjectUniqueID
    override fun getKeyUsage(): BooleanArray? = delegate.keyUsage
    override fun getBasicConstraints(): Int = delegate.basicConstraints
    override fun hasUnsupportedCriticalExtension(): Boolean = delegate.hasUnsupportedCriticalExtension()
    override fun getCriticalExtensionOIDs(): MutableSet<String>? = delegate.criticalExtensionOIDs
    override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = delegate.nonCriticalExtensionOIDs
    override fun getExtensionValue(oid: String): ByteArray? = delegate.getExtensionValue(oid)
}
