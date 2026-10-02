package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.util.Locale

internal class NativeBatchItem(val id: String, val format: String, val operation: String,
    val dataReference: String, val extraProperties: Map<String, String>) {
    override fun toString() = "NativeBatchItem(format=$format,operation=$operation)"
}

/** Bounded declaration of a batch. Service-only metadata is retained in the
 * original descriptor; the client never interprets it as local instructions. */
internal class NativeBatchDescriptor(val json: Boolean, val algorithm: SigningAlgorithm,
    val stopOnError: Boolean, val items: List<NativeBatchItem>) {
    companion object {
        fun parse(bytes: ByteArray, json: Boolean): NativeBatchDescriptor? = runCatching {
            require(bytes.isNotEmpty() && bytes.size <= 524_288)
            if (json) parseJson(bytes) else parseXml(bytes)
        }.getOrNull()
        private fun parseJson(bytes: ByteArray): NativeBatchDescriptor {
            val root = NativeTriphaseCodec.objectValue(NativeProtocolJson.parse(bytes))
            require(root.keys.all { it in setOf("algorithm", "format", "suboperation", "extraparams", "stoponerror", "singlesigns") })
            for (field in listOf("format", "suboperation", "extraparams")) {
                require(!root.containsKey(field) || root[field] is String) { "Batch default must be text" }
            }
            val algorithm = algorithm(root["algorithm"] as? String ?: error("Missing algorithm"))
            val stop = if (root.containsKey("stoponerror")) root["stoponerror"] as? Boolean ?: error("Invalid stop rule") else false
            val rows = root["singlesigns"] as? List<*> ?: error("Missing documents")
            require(rows.size in 1..32)
            val items = rows.map { value ->
                val row = NativeTriphaseCodec.objectValue(value)
                require(row.keys.all { it in setOf("id", "datareference", "format", "suboperation", "extraparams") })
                for (field in listOf("format", "suboperation", "extraparams")) {
                    require(!row.containsKey(field) || row[field] is String) { "Batch option must be text" }
                }
                item(row["id"] as? String ?: error("Missing ID"),
                    (row["format"] ?: root["format"]) as? String ?: error("Missing format"),
                    (row["suboperation"] ?: root["suboperation"] ?: "sign") as? String ?: error("Invalid operation"),
                    row["datareference"] as? String ?: error("Missing data"),
                    (row["extraparams"] ?: root["extraparams"]) as? String)
            }
            require(items.map { it.id }.toSet().size == items.size)
            return NativeBatchDescriptor(true, algorithm, stop, items)
        }
        private fun parseXml(bytes: ByteArray): NativeBatchDescriptor {
            val root = NativeSigningXml.parse(bytes, 524_288).documentElement
            require(root.nodeName == "signbatch" && root.namespaceURI.isNullOrEmpty())
            require((0 until root.attributes.length).all { root.attributes.item(it).nodeName in setOf("algorithm", "stoponerror", "concurrenttimeout") })
            val algorithm = algorithm(root.getAttribute("algorithm"))
            val rule = root.getAttribute("stoponerror").ifEmpty { "false" }; require(rule in setOf("true", "false"))
            val rows = NativeSigningXml.children(root); require(rows.size in 1..32)
            val items = rows.map { row ->
                require(row.nodeName == "singlesign" && row.namespaceURI.isNullOrEmpty() && row.attributes.length == 1 && (row.hasAttribute("Id") || row.hasAttribute("id")))
                val parts = NativeSigningXml.children(row)
                require(parts.all { it.nodeName in setOf("datasource", "format", "suboperation", "extraparams", "signsaver") && it.namespaceURI.isNullOrEmpty() })
                require(parts.map { it.nodeName }.toSet().size == parts.size)
                fun text(name: String) = parts.singleOrNull { it.nodeName == name }?.let {
                    require(it.attributes.length == 0 && NativeSigningXml.children(it).isEmpty()); it.textContent.trim()
                }
                item(if (row.hasAttribute("Id")) row.getAttribute("Id") else row.getAttribute("id"), text("format") ?: error("Missing format"), text("suboperation") ?: "sign",
                    text("datasource") ?: error("Missing datasource"), text("extraparams"))
            }
            require(items.map { it.id }.toSet().size == items.size)
            return NativeBatchDescriptor(false, algorithm, rule == "true", items)
        }
        internal fun algorithm(name: String): SigningAlgorithm = when (name.lowercase(Locale.ROOT)) {
            "sha1", "sha1withrsa" -> SigningAlgorithm.SHA1_WITH_RSA
            "sha256", "sha256withrsa" -> SigningAlgorithm.SHA256_WITH_RSA
            "sha512", "sha512withrsa" -> SigningAlgorithm.SHA512_WITH_RSA
            else -> error("Unsupported algorithm")
        }
        private fun item(id: String, format: String, operation: String, data: String, encoded: String?): NativeBatchItem {
            require(id.length in 1..256 && id.none(Char::isISOControl))
            require(format.length in 1..128 && format.none(Char::isISOControl))
            val op = operation.lowercase(Locale.ROOT); require(op in setOf("sign", "cosign", "countersign"))
            require(data.length in 1..699_052 && data.none(Char::isISOControl))
            val properties = encoded?.takeIf(String::isNotEmpty)?.let(AfirmaServletInvocationParser::properties).orEmpty()
            require(id.none { it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' })
            return NativeBatchItem(id, format, op, data, properties)
        }
    }
}
