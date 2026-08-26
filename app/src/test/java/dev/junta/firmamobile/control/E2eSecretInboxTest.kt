package dev.junta.firmamobile.control

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class E2eSecretInboxTest {
    @Test
    fun `valid secret is consumed once and staged file is deleted`() = runTest {
        val root = Files.createTempDirectory("jfm-e2e-secret").toFile()
        val handle = "0123456789abcdef0123456789abcdef"
        val file = root.resolve(handle)
        file.writeBytes("synthetic-password".encodeToByteArray())
        val inbox = E2eSecretInbox(root)

        val first = inbox.consume(handle)
        assertTrue(first is E2eSecretReadResult.Success)
        val secret = (first as E2eSecretReadResult.Success).secret.use { it.take() }
        try {
            assertArrayEquals("synthetic-password".toCharArray(), secret)
        } finally {
            secret.fill('\u0000')
        }
        assertFalse(file.exists())
        assertTrue(inbox.consume(handle) is E2eSecretReadResult.Missing)
        root.deleteRecursively()
    }

    @Test
    fun `invalid traversal and oversized secrets fail closed and are not retained`() = runTest {
        val root = Files.createTempDirectory("jfm-e2e-secret-invalid").toFile()
        val inbox = E2eSecretInbox(root)

        assertTrue(inbox.consume("../outside-secret") is E2eSecretReadResult.Invalid)

        val handle = "abcdef0123456789abcdef0123456789"
        val file = root.resolve(handle)
        file.writeBytes(ByteArray(8193) { 'x'.code.toByte() })
        assertTrue(inbox.consume(handle) is E2eSecretReadResult.Invalid)
        assertFalse(file.exists())
        root.deleteRecursively()
    }
}
