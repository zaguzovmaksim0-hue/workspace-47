package dev.junta.firmamobile.ui.offlineverification

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedOfflineDocumentTest {
    @Test
    fun exactLimitIsReadAsOwnedSnapshotAndStreamAndScratchAreCleaned() {
        val bytes = ByteArray(512 * 1024) { 37 }
        var closed = false
        var scratch: ByteArray? = null
        val source = object : OfflineDocumentSource {
            override fun declaredSize(): Long = bytes.size.toLong()
            override fun open(): OfflineOpenedDocument = OfflineOpenedDocument(
                bytes.size.toLong(),
                object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        scratch = buffer
                        return super.read(buffer, offset, length)
                    }
                    override fun close() { closed = true; super.close() }
                },
            )
        }

        val result = BoundedOfflineDocument.read(source, OfflineInputKind.ORIGINAL) {}

        assertArrayEquals(bytes, result)
        assertTrue(closed)
        assertTrue(scratch!!.all { it == 0.toByte() })
        result.fill(0)
        assertTrue(bytes.all { it == 37.toByte() })
    }
}
