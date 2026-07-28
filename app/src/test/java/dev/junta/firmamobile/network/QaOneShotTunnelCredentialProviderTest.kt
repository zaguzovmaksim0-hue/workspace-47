package dev.junta.firmamobile.network

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QaOneShotTunnelCredentialProviderTest {
    @Test
    fun validCredentialIsDeletedBeforeReturnAndAllTemporaryBytesAreCleared() {
        val directory = Files.createTempDirectory("ws024-credential-test").toFile()
        val file = File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME)
        val token = "qa-one-shot-token-123"
        file.writeBytes(token.encodeToByteArray())
        var temporaryBytesCleared = false
        val provider = QaOneShotTunnelCredentialProvider(directory) { cleared ->
            temporaryBytesCleared = cleared
        }

        val credential = provider.acquire()

        assertFalse(file.exists())
        assertTrue(temporaryBytesCleared)
        val owned = credential!!.withValue { value ->
            assertArrayEquals(token.toCharArray(), value)
            value
        }
        credential.close()
        assertArrayEquals(CharArray(token.length), owned)
        assertThrows(IllegalStateException::class.java) {
            credential.withValue { it.size }
        }
        directory.deleteRecursively()
    }

    @Test
    fun secondReadIsNullBecauseTheCredentialIsStrictlyOneShot() {
        val directory = Files.createTempDirectory("ws024-credential-test").toFile()
        File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME)
            .writeBytes("single-use-token".encodeToByteArray())
        val provider = QaOneShotTunnelCredentialProvider(directory)

        provider.acquire()!!.close()

        assertNull(provider.acquire())
        directory.deleteRecursively()
    }

    @Test
    fun emptyOversizedControlWhitespaceAndNonAsciiCredentialsAreRejectedAndDeleted() {
        val invalidValues = listOf(
            ByteArray(0),
            ByteArray(QaOneShotTunnelCredentialProvider.MAX_CREDENTIAL_BYTES + 1) { 'a'.code.toByte() },
            "contains space".encodeToByteArray(),
            "contains\nnewline".encodeToByteArray(),
            byteArrayOf(0x7f),
            "токен".encodeToByteArray(),
        )
        for (value in invalidValues) {
            val directory = Files.createTempDirectory("ws024-invalid-credential").toFile()
            val file = File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME)
            file.writeBytes(value)
            var cleared = false
            val provider = QaOneShotTunnelCredentialProvider(directory) { cleared = it }

            assertNull("size=${value.size}", provider.acquire())
            assertFalse("size=${value.size}", file.exists())
            assertTrue("size=${value.size}", cleared)
            directory.deleteRecursively()
        }
    }

    @Test
    fun symlinkAndNonRegularTargetAreRejectedWithoutReadingTheirContents() {
        val directory = Files.createTempDirectory("ws024-link-credential").toFile()
        val outside = File(directory.parentFile, "outside-${System.nanoTime()}.txt")
        outside.writeText("must-not-be-read")
        val link = File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME).toPath()
        Files.createSymbolicLink(link, outside.toPath())

        assertNull(QaOneShotTunnelCredentialProvider(directory).acquire())
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("must-not-be-read", outside.readText())

        val directoryTarget = File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME)
        assertTrue(directoryTarget.mkdir())
        assertNull(QaOneShotTunnelCredentialProvider(directory).acquire())
        assertFalse(directoryTarget.exists())
        outside.delete()
        directory.deleteRecursively()
    }

    @Test
    fun missingDirectoryOrMissingFileReturnsNullWithoutCreatingAnything() {
        val root = Files.createTempDirectory("ws024-missing-credential").toFile()
        val missingDirectory = File(root, "missing")
        assertNull(QaOneShotTunnelCredentialProvider(missingDirectory).acquire())
        assertFalse(missingDirectory.exists())

        assertNull(QaOneShotTunnelCredentialProvider(root).acquire())
        assertFalse(File(root, QaOneShotTunnelCredentialProvider.FILE_NAME).exists())
        root.deleteRecursively()
    }

    @Test
    fun concurrentAcquireReturnsAtMostOneCredential() {
        val directory = Files.createTempDirectory("ws024-concurrent-credential").toFile()
        File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME)
            .writeBytes("concurrent-one-shot".encodeToByteArray())
        val provider = QaOneShotTunnelCredentialProvider(directory)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<QATunnelCredential?>())
        val executor = Executors.newFixedThreadPool(8)
        repeat(8) {
            executor.submit {
                start.await()
                results += provider.acquire()
            }
        }

        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
        assertEquals(1, results.count { it != null })
        results.filterNotNull().forEach(QATunnelCredential::close)
        assertFalse(File(directory, QaOneShotTunnelCredentialProvider.FILE_NAME).exists())
        directory.deleteRecursively()
    }
}
