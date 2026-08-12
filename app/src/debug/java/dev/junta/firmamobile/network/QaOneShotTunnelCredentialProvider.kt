package dev.junta.firmamobile.network

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

internal class QaOneShotTunnelCredentialProvider internal constructor(
    private val noBackupDirectory: File,
    private val temporaryBytesClearedObserverForTest: ((Boolean) -> Unit)? = null,
) : TunnelCredentialProvider {
    constructor(context: Context) : this(context.noBackupFilesDir)

    @Synchronized
    override fun acquire(): QATunnelCredential? {
        val path = File(noBackupDirectory, FILE_NAME).toPath()
        val temporary = ByteArray(MAX_CREDENTIAL_BYTES + 1)
        var ownedChars: CharArray? = null
        var deleteOnExit = false
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
            deleteOnExit = true
            val attributes = Files.readAttributes(
                path,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            if (!attributes.isRegularFile || attributes.isSymbolicLink) return null

            val options = setOf<OpenOption>(
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS,
            )
            val count = Files.newByteChannel(path, options).use { channel ->
                var total = 0
                var emptyReads = 0
                while (total < temporary.size) {
                    val target = ByteBuffer.wrap(temporary, total, temporary.size - total)
                    val read = channel.read(target)
                    if (read < 0) break
                    if (read == 0) {
                        emptyReads++
                        if (emptyReads >= MAX_EMPTY_READS) return null
                    } else {
                        total += read
                        emptyReads = 0
                    }
                }
                total
            }
            if (count !in 1..MAX_CREDENTIAL_BYTES) return null
            if ((0 until count).any { index -> temporary[index].toInt() !in PRINTABLE_ASCII }) return null

            ownedChars = CharArray(count) { index -> temporary[index].toInt().toChar() }
            if (!Files.deleteIfExists(path)) return null
            deleteOnExit = false
            return QATunnelCredential(ownedChars).also { ownedChars = null }
        } catch (_: Exception) {
            return null
        } finally {
            if (deleteOnExit) {
                runCatching { Files.deleteIfExists(path) }
            }
            temporary.fill(0)
            ownedChars?.fill('\u0000')
            temporaryBytesClearedObserverForTest?.invoke(temporary.all { it == 0.toByte() })
        }
    }

    internal companion object {
        const val FILE_NAME = "ws024-qa-credential.once"
        const val MAX_CREDENTIAL_BYTES = 512
        private const val MAX_EMPTY_READS = 8
        private val PRINTABLE_ASCII = '!'.code..'~'.code
    }
}
