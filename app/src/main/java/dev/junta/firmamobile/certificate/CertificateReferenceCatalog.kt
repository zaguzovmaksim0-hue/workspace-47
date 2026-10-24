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
    val reference: StoredCertificateReference,
)

data class StoredCertificateCatalog(
    val entries: List<StoredCertificateEntry>,
    val activeId: String?,
) {
    val active: StoredCertificateEntry? get() = entries.singleOrNull { it.id == activeId }
}

/** Storage only: append does not select, unlock, acquire a grant or schedule reminders. */
interface CertificateReferenceCatalogStore : CertificateReferenceStore {
    suspend fun readCatalog(): StoredCertificateCatalog

    suspend fun add(reference: StoredCertificateReference): StoredCertificateEntry

    /** Activates [id] only if it still has [revision]; a stale list row never selects a changed entry. */
    suspend fun selectEntry(id: String, revision: Long): Boolean

    /** Removes [id] only if it still has [revision]; removing the active entry leaves none selected. Returns the
     * removed entry so its caller can release that entry's grant. Catalogue URIs are unique, so no surviving
     * entry can share it. */
    suspend fun remove(id: String, revision: Long): StoredCertificateEntry?
}

internal object CertificateReferenceCatalogCodec {
    const val MAX_ENTRIES = 16
    private const val VERSION = 1
    private const val MAX_ENCODED_LENGTH = 524288
    private const val MAX_URI_LENGTH = 4096
    private const val MAX_NAME_LENGTH = 256
    private const val MAX_MIME_LENGTH = 128
    private const val MAX_SUMMARY_LENGTH = 1024

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
                writeReference(output, entry.reference)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray()).also {
            require(it.length <= MAX_ENCODED_LENGTH)
        }
    }

    fun decode(encoded: String): StoredCertificateCatalog {
        require(encoded.length <= MAX_ENCODED_LENGTH)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            require(input.readInt() == VERSION)
            val activeId = input.readUTF().ifEmpty { null }
            val count = input.readInt()
            require(count in 0..MAX_ENTRIES)
            val entries = List(count) {
                StoredCertificateEntry(input.readUTF(), input.readLong(), readReference(input))
            }
            require(input.available() == 0)
            StoredCertificateCatalog(entries, activeId).also(::validate)
        }
    }

    private fun validate(catalog: StoredCertificateCatalog) {
        require(catalog.entries.size <= MAX_ENTRIES)
        require(catalog.entries.map { it.id }.toSet().size == catalog.entries.size)
        require(catalog.entries.map { it.reference.uri }.toSet().size == catalog.entries.size)
        require(catalog.activeId == null || catalog.entries.any { it.id == catalog.activeId })
        for (entry in catalog.entries) {
            require(UUID.fromString(entry.id).toString() == entry.id && entry.revision > 0)
            validateReference(entry.reference)
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
    }

    private fun readReference(input: DataInputStream): StoredCertificateReference {
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
        return StoredCertificateReference(uri, name, mime, size.takeIf { it >= 0 }, summary)
    }
}
