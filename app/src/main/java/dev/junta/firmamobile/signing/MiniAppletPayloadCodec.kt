package dev.junta.firmamobile.signing

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

internal object MiniAppletPayloadCodec {
    private const val MAGIC = 0x4A464D31
    private const val HEADER_BYTES = 12

    fun encode(data: ByteArray, extraProperties: String): ByteArray {
        val propertiesBytes = extraProperties.toByteArray(StandardCharsets.UTF_8)
        return try {
            val total = HEADER_BYTES.toLong() + data.size + propertiesBytes.size
            require(total <= PendingSignRequestStore.MAX_PAYLOAD_BYTES)
            ByteBuffer.allocate(total.toInt())
                .putInt(MAGIC)
                .putInt(data.size)
                .putInt(propertiesBytes.size)
                .put(data)
                .put(propertiesBytes)
                .array()
        } finally {
            propertiesBytes.fill(0)
        }
    }

    fun <T> withDecoded(
        payload: ByteArray,
        block: (data: ByteArray, extraProperties: String) -> T,
    ): T {
        require(payload.size >= HEADER_BYTES)
        val buffer = ByteBuffer.wrap(payload)
        require(buffer.int == MAGIC)
        val dataLength = buffer.int
        val propertiesLength = buffer.int
        require(dataLength >= 0 && propertiesLength >= 0)
        require(HEADER_BYTES.toLong() + dataLength + propertiesLength == payload.size.toLong())

        val data = ByteArray(dataLength)
        val propertiesBytes = ByteArray(propertiesLength)
        return try {
            buffer.get(data)
            buffer.get(propertiesBytes)
            block(data, String(propertiesBytes, StandardCharsets.UTF_8))
        } finally {
            data.fill(0)
            propertiesBytes.fill(0)
        }
    }
}
