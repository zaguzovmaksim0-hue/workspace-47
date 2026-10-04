package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import java.util.Locale

/** Typed phase endpoints belong to the accepted browser request, not to a
 * later PRE response. No URL or class embedded in a batch is executed locally. */
internal class NativeRemoteOptions(
    val format: String,
    val preUrl: URI?,
    val postUrl: URI?,
    properties: Map<String, String>,
    val batch: NativeBatchDescriptor? = null,
    val needCertificate: Boolean = false,
    val localBatch: Boolean = false,
) {
    val properties: Map<String, String> = LinkedHashMap(properties)
    init {
        require(format.isNotBlank() && format.length <= 128)
        require(if (localBatch) batch?.json == true && preUrl == null && postUrl == null else
            preUrl != null && postUrl != null && AfirmaEndpointPolicy.accepts(preUrl) && AfirmaEndpointPolicy.accepts(postUrl))
        require(properties.size <= 64 && properties.entries.sumOf { it.key.length + it.value.length } <= 16_384)
    }
    override fun toString() = "NativeRemoteOptions(batch=${batch?.items?.size ?: 0},local=$localBatch)"
    companion object {
        fun isTriphaseFormat(label: String): Boolean = label.lowercase(Locale.ROOT) in setOf(
            "cadestri", "padestri", "xadestri", "cades triphase", "pades triphase", "xades triphase",
        )
        fun wireFormat(label: String): String? {
            val format = label.lowercase(Locale.ROOT).removeSuffix(" triphase").removeSuffix("tri")
            return when {
                format == "cades" -> "CAdES"
                NativePadesOptions.acceptsFormat(format) -> "pades"
                NativeXadesOptions.acceptsFormat(format) -> "XAdES"
                else -> null
            }
        }
    }
}
