package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal object SiteProfileCatalogResource {
    private const val RESOURCE_PATH = "/dev/junta/firmamobile/profile/site_profiles_v1.json"
    private const val MAX_BYTES = 1_048_576
    private const val SHA256_HEX_LENGTH = 64
    private const val HEX_CHUNK_LENGTH = 2
    private const val HEX_RADIX = 16

    fun read(): String {
        val stream = SiteProfileCatalogResource::class.java.classLoader
            ?.getResourceAsStream(RESOURCE_PATH.removePrefix("/"))
            ?: error("Site profile catalog resource is missing.")
        stream.use { input ->
            val buffer = ByteArray(MAX_BYTES + 1)
            var offset = 0
            while (true) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) break
                offset += read
                if (offset > MAX_BYTES) {
                    error("Site profile catalog resource is too large.")
                }
            }
            return decode(buffer.copyOf(offset), BuildConfig.SITE_PROFILE_CATALOG_SHA256)
        }
    }

    internal fun decode(bytes: ByteArray, expectedSha256: String): String {
        validateCatalogSize(bytes)
        val expected = parseExpectedDigest(expectedSha256)
        validateCatalogDigest(bytes, expected)
        return decodeStrictUtf8(bytes)
    }

    private fun validateCatalogSize(bytes: ByteArray) {
        check(bytes.isNotEmpty()) { "Site profile catalog resource is empty." }
        check(bytes.size <= MAX_BYTES) { "Site profile catalog resource is too large." }
    }

    private fun parseExpectedDigest(expectedSha256: String): ByteArray {
        check(expectedSha256.matches(Regex("[0-9a-f]{$SHA256_HEX_LENGTH}"))) {
            "Site profile catalog digest is invalid."
        }
        return expectedSha256.chunked(HEX_CHUNK_LENGTH)
            .map { it.toInt(HEX_RADIX).toByte() }
            .toByteArray()
    }

    private fun validateCatalogDigest(bytes: ByteArray, expected: ByteArray) {
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
        check(MessageDigest.isEqual(actual, expected)) {
            "Site profile catalog digest mismatch."
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { error("Site profile catalog resource is not valid UTF-8.") }
    }
}
