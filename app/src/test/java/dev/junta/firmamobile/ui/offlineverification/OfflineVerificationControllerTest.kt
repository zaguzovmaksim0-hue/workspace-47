package dev.junta.firmamobile.ui.offlineverification

import dev.junta.firmamobile.verification.OfflineCmsResult
import dev.junta.firmamobile.verification.OfflineCmsStatus
import java.io.ByteArrayInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVerificationControllerTest {
    @Test
    fun selectionDoesNotReadOrVerifyUntilExplicitActionAndOwnedBytesAreCleared() = runTest {
        val source = SyntheticOfflineDocument(byteArrayOf(7, 8, 9))
        var calls = 0
        var snapshot: ByteArray? = null
        val controller = OfflineVerificationController(this, StandardTestDispatcher(testScheduler)) { cms, original ->
            calls++
            snapshot = cms
            assertNull(original)
            assertEquals(7.toByte(), cms[0])
            OfflineCmsResult(OfflineCmsStatus.VERIFIED, 1)
        }
        val token = controller.beginSelection(OfflineInputKind.CMS)!!
        controller.completeSelection(token, source)
        advanceUntilIdle()
        assertEquals(0, source.opens)
        assertEquals(0, calls)
        assertTrue(controller.state.value.canVerify)
        assertNull(controller.state.value.outcome)

        controller.verify()
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(OfflineVerificationOutcome.VERIFIED, controller.state.value.outcome)
        assertEquals(1, controller.state.value.verifiedSigners)
        assertFalse(controller.state.value.busy)
        assertTrue(snapshot!!.all { it == 0.toByte() })
        assertEquals(1, source.closes)
        assertTrue(source.buffers.all { buffer -> buffer.all { it == 0.toByte() } })
        controller.close()
    }
}

internal class SyntheticOfflineDocument(
    private val bytes: ByteArray,
    private val size: Long? = bytes.size.toLong(),
    private val openedSize: Long? = null,
    private val beforeRead: () -> Unit = {},
) : OfflineDocumentSource {
    var opens = 0
    var closes = 0
    val buffers = mutableListOf<ByteArray>()
    override fun declaredSize(): Long? = size
    override fun open(): OfflineOpenedDocument {
        opens++
        return OfflineOpenedDocument(openedSize, object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffers.add(buffer)
                beforeRead()
                return super.read(buffer, offset, length)
            }
            override fun close() { closes++; super.close() }
        })
    }
}
