package dev.junta.firmamobile.signing

import org.junit.Assert.assertThrows
import org.junit.Test

class SoftwareEcCadesDerGateTest {
    @Test
    fun exactDepthIsAcceptedAndOneFurtherLevelIsRejectedBeforeAsn1Parsing() {
        SoftwareEcCadesDerGate.requireBoundedTree(nested(24))
        assertThrows(IllegalArgumentException::class.java) {
            SoftwareEcCadesDerGate.requireBoundedTree(nested(25))
        }
    }

    @Test
    fun malformedIndefiniteNonminimalHighTagAndTrailingDerAreRejected() {
        listOf(byteArrayOf(), byteArrayOf(0x30), byteArrayOf(0x30, 2, 5),
            byteArrayOf(0x30, 0x80.toByte(), 0, 0), byteArrayOf(0x30, 0x81.toByte(), 0),
            byteArrayOf(0x30, 0x82.toByte(), 0, 0), byteArrayOf(0x1f, 1, 0),
            byteArrayOf(0x30, 0x84.toByte(), 0, 0, 0, 0), byteArrayOf(5, 0, 5, 0)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { SoftwareEcCadesDerGate.requireBoundedTree(bytes) }
        }
    }

    private fun nested(depth: Int): ByteArray {
        var bytes = byteArrayOf(5, 0)
        repeat(depth - 1) { bytes = byteArrayOf(0x30, bytes.size.toByte()) + bytes }
        return bytes
    }
}
