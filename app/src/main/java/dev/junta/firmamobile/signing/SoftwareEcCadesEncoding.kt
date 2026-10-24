package dev.junta.firmamobile.signing

import java.io.OutputStream
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Encoding

internal class SoftwareEcCadesFailure(val error: SoftwareEcCadesError) : Exception()

/** Owns only this layer's temporary arrays, never caller arrays or opaque provider memory. */
internal class SoftwareEcCadesBuffers : AutoCloseable {
    private val arrays = mutableListOf<ByteArray>()

    fun own(bytes: ByteArray): ByteArray = bytes.also { arrays.add(it) }

    fun release(bytes: ByteArray): ByteArray = bytes.also {
        check(arrays.remove(bytes))
    }

    fun encode(value: ASN1Encodable, limit: Int, error: SoftwareEcCadesError): ByteArray {
        val stream = BoundedOutput(limit, error)
        return try {
            value.toASN1Primitive().encodeTo(stream, ASN1Encoding.DER)
            own(stream.bytes())
        } finally {
            stream.close()
        }
    }

    override fun close() {
        arrays.forEach { it.fill(0) }
        arrays.clear()
    }

    private class BoundedOutput(limit: Int, private val error: SoftwareEcCadesError) : OutputStream() {
        private val buffer = ByteArray(limit)
        private var size = 0

        override fun write(value: Int) {
            if (size == buffer.size) throw SoftwareEcCadesFailure(error)
            buffer[size++] = value.toByte()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > buffer.size - size) throw SoftwareEcCadesFailure(error)
            bytes.copyInto(buffer, size, offset, offset + length)
            size += length
        }

        fun bytes(): ByteArray = buffer.copyOf(size)

        override fun close() { buffer.fill(0) }
    }
}
