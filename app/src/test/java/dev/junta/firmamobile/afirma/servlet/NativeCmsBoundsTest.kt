package dev.junta.firmamobile.afirma.servlet

import org.junit.Assert.*
import org.junit.Test

class NativeCmsBoundsTest {
    @Test fun acceptsDefiniteAndIndefiniteConstructedContainers() {
        assertTrue(NativeCmsBounds.accepts(byteArrayOf(0x30, 2, 5, 0), 64))
        assertTrue(NativeCmsBounds.accepts(byteArrayOf(0x30, 0x80.toByte(), 5, 0, 0, 0), 64))
        assertTrue(NativeCmsBounds.accepts(byteArrayOf(4, 4, 0, 0, 0x30, 0x80.toByte()), 64))
    }
    @Test fun missingEndMarkersAndTrailingSecondObjectsAreRejected() {
        for (bytes in listOf(byteArrayOf(0x30,0x80.toByte(),5,0),byteArrayOf(0x30,2,5,0,5,0),byteArrayOf(0x30,2,0,0))) {
            assertFalse(NativeCmsBounds.accepts(bytes,64))
        }
    }
    @Test fun malformedPrimitiveLengthOrOverrunIsRejectedBeforeAsn1Construction() {
        for (bytes in listOf(byteArrayOf(4,0x80.toByte(),0,0),byteArrayOf(4,0x84.toByte(),0x7f,0x7f,0x7f,0x7f),
            byteArrayOf(0x30,3,4,2,1),byteArrayOf(0x1f,0x80.toByte(),1,0))) {
            assertFalse(NativeCmsBounds.accepts(bytes,64))
        }
    }
    @Test fun deeplyNestedContainersHaveAnIterativeDepthLimit() {
        fun nested(depth:Int)=ByteArray(depth*2) { if(it%2==0)0x30 else 0x80.toByte() }+byteArrayOf(5,0)+ByteArray(depth*2)
        assertTrue(NativeCmsBounds.accepts(nested(10),4096))
        assertFalse(NativeCmsBounds.accepts(nested(40),4096))
    }
    @Test fun largeNumbersOfTinyObjectsHaveANodeBudget() {
        val children=ByteArray(24_000) { if(it%2==0)5 else 0 }
        val many=byteArrayOf(0x30,0x80.toByte())+children+byteArrayOf(0,0)
        assertFalse(NativeCmsBounds.accepts(many,32768))
        assertFalse(NativeCmsBounds.accepts(byteArrayOf(0x30,2,5,0),3))
    }
    @Test fun malformedCmsAndMissingSignersDoNotPassShapeAdmission() {
        for(bytes in listOf(byteArrayOf(0x30,0),byteArrayOf(4,2,1,2),"plain text".toByteArray())) {
            assertNull(NativeCadesHistory.isDetached(bytes))
            assertNull(NativeCadesHistory.inspect(bytes,1024))
        }
    }
}
