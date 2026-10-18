package dev.junta.firmamobile.certificate

import java.io.Closeable
import java.security.MessageDigest

internal sealed interface Pkcs12InspectionResult {
    class Success(val selection: Pkcs12AliasSelection) : Pkcs12InspectionResult

    data class Failure(val code: CertificateErrorCode) : Pkcs12InspectionResult
}

/** Display metadata only; neither an alias nor authority to load an arbitrary entry. */
internal class Pkcs12Candidate internal constructor(
    val number: Int,
    val summary: CertificateSummary,
    val certificateSha256: String,
) {
    override fun toString(): String = "Pkcs12Candidate"
}

/**
 * One import attempt, bound to the exact inspected container. No password, container bytes,
 * KeyStore or private key survives inspection. Close on cancellation or document replacement.
 * Candidates are capabilities by object identity, not by their display metadata or position.
 */
internal class Pkcs12AliasSelection internal constructor(
    inputFingerprint: ByteArray,
    entries: List<Pair<String, Pkcs12Candidate>>,
    private val monotonicNanos: () -> Long,
) : Closeable {
    private val openedAtNanos = monotonicNanos()
    private var ownedFingerprint: ByteArray? = inputFingerprint
    private var ownedEntries: List<Pair<String, Pkcs12Candidate>> = entries.toList()

    val candidates: List<Pkcs12Candidate>
        @Synchronized get() {
            expireIfNecessary()
            return ownedEntries.map { it.second }
        }

    /** Every attempt consumes the entire choice, including null or a foreign candidate. */
    @Synchronized
    internal fun consume(candidate: Pkcs12Candidate?): Pkcs12SelectedEntry? {
        expireIfNecessary()
        val fingerprint = ownedFingerprint ?: return null
        val alias = ownedEntries.singleOrNull { it.second === candidate }?.first
        ownedFingerprint = null
        ownedEntries = emptyList()
        return if (alias == null) {
            fingerprint.fill(0)
            null
        } else {
            Pkcs12SelectedEntry(alias, fingerprint)
        }
    }

    @Synchronized
    override fun close() {
        ownedFingerprint?.fill(0)
        ownedFingerprint = null
        ownedEntries = emptyList()
    }

    private fun expireIfNecessary() {
        val elapsedNanos = monotonicNanos() - openedAtNanos
        if (elapsedNanos < 0 || elapsedNanos >= MAX_SELECTION_AGE_NANOS) close()
    }

    override fun toString(): String = "Pkcs12AliasSelection"
}

/** Temporary ownership transferred only to loadSelected; raw aliases never leave this boundary. */
internal class Pkcs12SelectedEntry(
    alias: String,
    inputFingerprint: ByteArray,
) : Closeable {
    private var ownedAlias: String? = alias
    private var ownedFingerprint: ByteArray? = inputFingerprint

    fun alias(): String = checkNotNull(ownedAlias) { "PKCS12 selection is closed" }

    fun matchesInput(bytes: ByteArray): Boolean {
        val expected = ownedFingerprint ?: return false
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
        return try {
            MessageDigest.isEqual(expected, actual)
        } finally {
            actual.fill(0)
        }
    }

    override fun close() {
        ownedFingerprint?.fill(0)
        ownedFingerprint = null
        ownedAlias = null
    }

    override fun toString(): String = "Pkcs12SelectedEntry"
}

private const val MAX_SELECTION_AGE_NANOS = 300_000_000_000L
