package dev.junta.firmamobile.certificate

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class StoredCertificateEntry(
    val id: String,
    val revision: Long,
    val reference: StoredCertificateReference?,
    val signingKey: StoredSigningKeyReference? = null,
)

data class StoredCertificateCatalog(
    val entries: List<StoredCertificateEntry>,
    val activeId: String?,
    val tombstones: Set<String> = emptySet(),
) {
    val active: StoredCertificateEntry? get() = entries.singleOrNull { it.id == activeId }
}

/** Storage only: append does not select, unlock, acquire a grant or schedule reminders. */
interface CertificateReferenceCatalogStore : CertificateReferenceStore {
    /** Legacy file facade: key-only entries require the authenticated keystore flow. */
    override suspend fun read(): StoredCertificateReference? = readCatalog().active?.reference

    /** Inactive entries keep their grants too; catalogue URIs are unique. */
    override suspend fun references(uri: Uri): Boolean = readCatalog().entries.any { it.reference?.uri == uri }

    suspend fun readCatalog(): StoredCertificateCatalog

    suspend fun add(reference: StoredCertificateReference): StoredCertificateEntry

    /** Activates [id] only if it still has [revision] and a file reference; a stale list row never selects a
     * changed entry, and a key-only entry stays behind the authenticated keystore flow (see [read]). */
    suspend fun selectEntry(id: String, revision: Long): Boolean

    /** Compare the complete entry, including its revision, inside the atomic edit. */
    suspend fun replace(expected: StoredCertificateEntry, replacement: StoredCertificateEntry)

    /** With [onlyIfInactive] the active entry is refused inside the same atomic edit: it leaves only through
     * [clear], after its saved unlock is revoked. */
    suspend fun remove(expected: StoredCertificateEntry, onlyIfInactive: Boolean = false)

    suspend fun acknowledgeKeyDeletion(alias: String)
}

internal object CertificateReferenceCatalogCodec {
    const val MAX_ENTRIES = 16
    private const val VERSION = 3
    private const val SELECTED_ENTRY_VERSION = 3
    private const val MAX_TOMBSTONES = 128
    private const val MAX_ENCODED_LENGTH = 524288
    private const val MAX_URI_LENGTH = 4096
    private const val MAX_NAME_LENGTH = 256
    private const val MAX_MIME_LENGTH = 128
    private const val MAX_SUMMARY_LENGTH = 1024
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    fun entry(reference: StoredCertificateReference) = StoredCertificateEntry(
        id = UUID.randomUUID().toString(), revision = 1, reference = reference,
    )

    fun validateReference(reference: StoredCertificateReference) {
        val uri = reference.uri
        require(uri.scheme.equals("content", ignoreCase = true))
        require(!uri.authority.isNullOrBlank() && uri.fragment == null)
        require(uri.toString().length <= MAX_URI_LENGTH)
        require(reference.displayName.length in 1..MAX_NAME_LENGTH)
        require(reference.displayName == CertificateDisplayNamePolicy.sanitize(reference.displayName))
        require(reference.mimeType.isNotBlank() && reference.mimeType.length <= MAX_MIME_LENGTH)
        require(reference.size == null || reference.size >= 0)
        reference.summary?.let {
            require(it.ownerName.length <= MAX_SUMMARY_LENGTH && it.issuerName.length <= MAX_SUMMARY_LENGTH)
            require(!it.validUntil.isBefore(it.validFrom))
        }
        reference.selectedCertificateSha256?.let { require(SHA256_HEX.matches(it)) }
    }

    fun encode(catalog: StoredCertificateCatalog): String {
        validate(catalog)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeUTF(catalog.activeId.orEmpty())
            output.writeInt(catalog.entries.size)
            for (entry in catalog.entries) {
                output.writeUTF(entry.id)
                output.writeLong(entry.revision)
                output.writeBoolean(entry.reference != null)
                entry.reference?.let { writeReference(output, it) }
                output.writeBoolean(entry.signingKey != null)
                entry.signingKey?.let { key ->
                    output.writeInt(key.version)
                    output.writeUTF(key.alias)
                    output.writeUTF(key.level.name)
                    output.writeUTF(key.certificateSha256)
                    output.writeUTF(key.displayName)
                }
            }
            output.writeInt(catalog.tombstones.size)
            catalog.tombstones.sorted().forEach(output::writeUTF)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray()).also {
            require(it.length <= MAX_ENCODED_LENGTH)
        }
    }

    fun decode(encoded: String): StoredCertificateCatalog {
        require(encoded.length <= MAX_ENCODED_LENGTH)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            val version = input.readInt()
            require(version in 1..VERSION)
            val activeId = input.readUTF().ifEmpty { null }
            val count = input.readInt()
            require(count in 0..MAX_ENTRIES)
            val entries = List(count) {
                val id = input.readUTF()
                val revision = input.readLong()
                val reference = if (version == 1 || input.readBoolean()) readReference(input, version) else null
                val key = if (version >= 2 && input.readBoolean()) {
                    StoredSigningKeyReference(
                        input.readInt(), input.readUTF(), StoredKeystoreLevel.valueOf(input.readUTF()),
                        input.readUTF(), input.readUTF(),
                    )
                } else null
                StoredCertificateEntry(id, revision, reference, key)
            }
            val tombstones = if (version >= 2) {
                val size = input.readInt()
                require(size in 0..MAX_TOMBSTONES)
                List(size) { input.readUTF() }.also { require(it.toSet().size == size) }.toSet()
            } else emptySet()
            require(input.available() == 0)
            StoredCertificateCatalog(entries, activeId, tombstones).also(::validate)
        }
    }

    private fun validate(catalog: StoredCertificateCatalog) {
        require(catalog.entries.size <= MAX_ENTRIES)
        require(catalog.entries.map { it.id }.toSet().size == catalog.entries.size)
        require(catalog.entries.mapNotNull { it.reference?.uri }.let { it.toSet().size == it.size })
        require(catalog.activeId == null || catalog.entries.any { it.id == catalog.activeId })
        val aliases = catalog.entries.mapNotNull { it.signingKey?.alias }
        require(aliases.toSet().size == aliases.size)
        require(catalog.tombstones.size <= MAX_TOMBSTONES)
        require(catalog.tombstones.none { it in aliases })
        catalog.tombstones.forEach { require(StoredSigningKeyReference.isOwnedAlias(it)) }
        for (entry in catalog.entries) {
            require(UUID.fromString(entry.id).toString() == entry.id && entry.revision > 0)
            require(entry.reference != null || entry.signingKey != null)
            entry.reference?.let(::validateReference)
            entry.signingKey?.validate()
        }
    }

    private fun writeReference(output: DataOutputStream, reference: StoredCertificateReference) {
        output.writeUTF(reference.uri.toString())
        output.writeUTF(reference.displayName)
        output.writeUTF(reference.mimeType)
        output.writeLong(reference.size ?: -1)
        output.writeBoolean(reference.summary != null)
        reference.summary?.let {
            output.writeUTF(it.ownerName)
            output.writeUTF(it.issuerName)
            output.writeLong(it.validFrom.toEpochMilli())
            output.writeLong(it.validUntil.toEpochMilli())
        }
        output.writeUTF(reference.selectedCertificateSha256.orEmpty())
    }

    private fun readReference(input: DataInputStream, version: Int): StoredCertificateReference {
        val uri = Uri.parse(input.readUTF())
        val name = input.readUTF()
        val mime = input.readUTF()
        val size = input.readLong()
        require(size >= -1)
        val summary = if (input.readBoolean()) {
            CertificateSummary(
                input.readUTF(), input.readUTF(),
                Instant.ofEpochMilli(input.readLong()), Instant.ofEpochMilli(input.readLong()),
            )
        } else {
            null
        }
        val selected = if (version >= SELECTED_ENTRY_VERSION) input.readUTF().ifEmpty { null } else null
        return StoredCertificateReference(uri, name, mime, size.takeIf { it >= 0 }, summary, selected)
    }
}
