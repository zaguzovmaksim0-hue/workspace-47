package dev.junta.firmamobile.ui.offlineverification

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedOfflineDocumentFailureTest {
    @Test
    fun declaredOversizeIsRejectedBeforeOpeningForBothKinds() {
        for (kind in OfflineInputKind.entries) {
            for (size in listOf(kind.maxBytes.toLong() + 1, Long.MAX_VALUE)) {
                val source = SyntheticOfflineDocument(byteArrayOf(), size)
                assertThrows(OfflineInputTooLargeException::class.java) {
                    BoundedOfflineDocument.read(source, kind) {}
                }
                assertEquals(0, source.opens)
            }
        }
    }

    @Test
    fun descriptorOversizeClosesBeforeReading() {
        for (kind in OfflineInputKind.entries) {
            val source = SyntheticOfflineDocument(byteArrayOf(), null, kind.maxBytes.toLong() + 1)
            assertThrows(OfflineInputTooLargeException::class.java) {
                BoundedOfflineDocument.read(source, kind) {}
            }
            assertEquals(1, source.closes)
            assertTrue(source.buffers.isEmpty())
        }
    }

    @Test
    fun unknownNegativeAndLyingDeclaredSizesCannotBypassActualLimit() {
        for (kind in OfflineInputKind.entries) {
            for (size in listOf(null, -1L, 0L)) {
                val source = SyntheticOfflineDocument(ByteArray(kind.maxBytes + 19) { 23 }, size)
                assertThrows(OfflineInputTooLargeException::class.java) {
                    BoundedOfflineDocument.read(source, kind) {}
                }
                assertEquals(1, source.closes)
                assertTrue(source.buffers.all { buffer -> buffer.all { it == 0.toByte() } })
            }
        }
    }

    @Test
    fun cmsAcceptsExactlyOneMiBAndEmptyOriginalRemainsPresentButEmpty() {
        val source = SyntheticOfflineDocument(ByteArray(1_048_576) { 19 }, null)
        val cms = BoundedOfflineDocument.read(source, OfflineInputKind.CMS) {}
        assertEquals(1_048_576, cms.size)
        assertTrue(cms.all { it == 19.toByte() })
        cms.fill(0)
        val empty = BoundedOfflineDocument.read(SyntheticOfflineDocument(byteArrayOf()), OfflineInputKind.ORIGINAL) {}
        assertArrayEquals(byteArrayOf(), empty)
    }

    @Test
    fun readFailureAndCancellationCloseStreamAndClearPreviouslyReadBytes() {
        for (failure in listOf(IOException("synthetic private canary"), CancellationException("cancelled"))) {
            var reads = 0
            val source = SyntheticOfflineDocument(byteArrayOf(31, 32, 33), beforeRead = {
                if (++reads == 2) throw failure
            })
            assertThrows(failure.javaClass) {
                BoundedOfflineDocument.read(source, OfflineInputKind.CMS) {}
            }
            assertEquals(1, source.closes)
            assertTrue(source.buffers.all { buffer -> buffer.all { it == 0.toByte() } })
        }
    }

    @Test
    fun closeFailureDoesNotLeakAnUnreturnedSnapshot() {
        var scratch: ByteArray? = null
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long? = null
            override fun open() = OfflineOpenedDocument(null, object : ByteArrayInputStream(byteArrayOf(42)) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    scratch = buffer
                    return super.read(buffer, offset, length)
                }
                override fun close() { throw IOException("synthetic close canary") }
            })
        }
        assertThrows(IOException::class.java) { BoundedOfflineDocument.read(source, OfflineInputKind.CMS) {} }
        assertTrue(scratch!!.all { it == 0.toByte() })
    }

    @Test
    fun zeroProgressProviderIsHandledWithoutSpinning() {
        var next = 11
        var closed = false
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long? = null
            override fun open() = OfflineOpenedDocument(null, object : InputStream() {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
                override fun read(): Int = if (next < 13) next++ else -1
                override fun close() { closed = true }
            })
        }
        val result = BoundedOfflineDocument.read(source, OfflineInputKind.CMS) {}
        assertArrayEquals(byteArrayOf(11, 12), result)
        assertTrue(closed)
        result.fill(0)
    }

    @Test
    fun cancellationBeforeOpeningDoesNotTouchStream() {
        val source = SyntheticOfflineDocument(byteArrayOf(1))
        assertThrows(CancellationException::class.java) {
            BoundedOfflineDocument.read(source, OfflineInputKind.CMS) { throw CancellationException() }
        }
        assertEquals(0, source.opens)
    }
}
