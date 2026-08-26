package dev.junta.firmamobile.control

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface E2eSecretReadResult {
    data class Success(val secret: OwnedE2eSecret) : E2eSecretReadResult
    data object Missing : E2eSecretReadResult
    data object Invalid : E2eSecretReadResult
}

internal class OwnedE2eSecret internal constructor(
    private var ownedChars: CharArray?,
) : Closeable {
    fun take(): CharArray {
        val value = checkNotNull(ownedChars) { "Secret already consumed" }
        ownedChars = null
        return value
    }

    override fun close() {
        ownedChars?.fill('\u0000')
        ownedChars = null
    }
}

/**
 * One-shot QA secret inbox. The shell stages a password into the app-private cache with run-as;
 * the broadcast carries only an opaque handle. The secret file is always deleted after a read
 * attempt and its byte/char copies are cleared.
 */
internal class E2eSecretInbox(
    private val rootDirectory: File,
) {
    suspend fun consume(handle: String): E2eSecretReadResult = withContext(Dispatchers.IO) {
        if (!HANDLE.matches(handle)) return@withContext E2eSecretReadResult.Invalid
        val root = rootDirectory.toPath().toAbsolutePath().normalize()
        val path = root.resolve(handle).normalize()
        if (path.parent != root) return@withContext E2eSecretReadResult.Invalid

        var bytes: ByteArray? = null
        try {
            Files.createDirectories(root)
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                return@withContext E2eSecretReadResult.Invalid
            }
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext E2eSecretReadResult.Missing
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                return@withContext E2eSecretReadResult.Invalid
            }
            val size = Files.size(path)
            if (size !in 1..MAX_PASSWORD_BYTES.toLong()) {
                return@withContext E2eSecretReadResult.Invalid
            }
            bytes = Files.readAllBytes(path)
            if (bytes.isEmpty() || bytes.size > MAX_PASSWORD_BYTES) {
                return@withContext E2eSecretReadResult.Invalid
            }
            val chars = bytes.toUtf8CharsOrNull()
                ?: return@withContext E2eSecretReadResult.Invalid
            if (chars.isEmpty() || chars.size > MAX_PASSWORD_CHARS) {
                chars.fill('\u0000')
                return@withContext E2eSecretReadResult.Invalid
            }
            E2eSecretReadResult.Success(OwnedE2eSecret(chars))
        } catch (_: Exception) {
            E2eSecretReadResult.Invalid
        } finally {
            bytes?.fill(0)
            runCatching { Files.deleteIfExists(path) }
        }
    }

    private fun ByteArray.toUtf8CharsOrNull(): CharArray? = try {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = decoder.decode(ByteBuffer.wrap(this))
        try {
            CharArray(decoded.remaining()).also(decoded::get)
        } finally {
            if (decoded.hasArray()) decoded.array().fill('\u0000')
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val RELATIVE_DIRECTORY = "e2e-control/secrets"
        private const val MAX_PASSWORD_CHARS = 2048
        private const val MAX_PASSWORD_BYTES = 8192
        internal val HANDLE = Regex("[A-Za-z0-9][A-Za-z0-9_-]{15,63}")
    }
}
