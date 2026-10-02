package dev.junta.firmamobile.afirma.servlet

/** Bind every PRE/result to the approved descriptor, retaining failures rather
 * than interpreting a delivered result list as universal signing success. */
internal object NativeBatchProtocol {
    data class Outcome(val id: String, val status: String, val description: String? = null)
    data class Pre(val session: NativeTriSession?, val errors: List<Outcome>)
    private val statuses = setOf("NOT_STARTED", "DONE_AND_SAVED", "DONE_BUT_NOT_SAVED_YET", "DONE_BUT_SAVED_SKIPPED",
        "DONE_BUT_ERROR_SAVING", "ERROR_PRE", "ERROR_POST", "SKIPPED", "SAVE_ROLLBACKED", "OK", "KO", "NP")

    fun pre(bytes: ByteArray, batch: NativeBatchDescriptor): Pre {
        val ids = batch.items.map { it.id }.toSet()
        if (!batch.json) {
            val session = NativeTriphaseXml.parse(bytes) ?: error("Invalid batch PRE")
            require(session.signs.all { it.id in ids })
            return Pre(session, emptyList())
        }
        val root = NativeProtocolJson.parse(bytes)
        require(root.keys.all { it in setOf("td", "results") })
        require(!root.containsKey("results") || root["results"] is List<*>) { "Invalid batch failure list" }
        val errors = (root["results"] as? List<*>).orEmpty().map(::outcome)
        require(errors.map { it.id }.toSet().size == errors.size && errors.all { it.id in ids && it.status in setOf("ERROR_PRE", "SKIPPED", "NOT_STARTED") })
        val session = root["td"]?.let { NativeTriphaseCodec.parseJson(NativeTriphaseCodec.objectValue(it)) }
        val ready = session?.signs?.map { it.id }?.toSet().orEmpty()
        require(ready.all { it in ids } && errors.none { it.id in ready } && ready + errors.map { it.id } == ids)
        return Pre(session, errors)
    }

    fun descriptorForPost(original: ByteArray, errors: List<Outcome>): ByteArray {
        if (errors.isEmpty()) return original.copyOf()
        val root = LinkedHashMap(NativeProtocolJson.parse(original))
        root["singlesigns"] = (root["singlesigns"] as List<*>).map { item ->
            val row = LinkedHashMap(NativeTriphaseCodec.objectValue(item))
            errors.singleOrNull { it.id == row["id"] }?.let { error ->
                for (key in listOf("datareference", "format", "suboperation", "extraparams")) row.remove(key)
                row["result"] = error.status
                error.description?.let { row["description"] = it }
            }
            row
        }
        return NativeProtocolJson.encode(root)
    }

    fun results(bytes: ByteArray, batch: NativeBatchDescriptor): List<Outcome> {
        val result = if (batch.json) {
            val root = NativeProtocolJson.parse(bytes)
            require(root.keys == setOf("signs"))
            (root["signs"] as? List<*> ?: error("Missing batch results")).map(::outcome)
        } else {
            val root = NativeSigningXml.parse(bytes).documentElement
            require(root.nodeName == "signs" && root.namespaceURI.isNullOrEmpty())
            NativeSigningXml.children(root).map { item ->
                require(item.nodeName == "sign" && item.attributes.length == 1)
                val id = when { item.hasAttribute("id") -> item.getAttribute("id"); item.hasAttribute("Id") -> item.getAttribute("Id"); else -> error("Missing result ID") }
                val fields = NativeSigningXml.children(item)
                require(fields.all { it.nodeName in setOf("result", "reason", "description") && NativeSigningXml.children(it).isEmpty() })
                val status = fields.single { it.nodeName == "result" }.textContent.trim()
                require(status in statuses)
                Outcome(id, status)
            }
        }
        require(result.size == batch.items.size && result.map { it.id }.toSet() == batch.items.map { it.id }.toSet())
        return result
    }
    fun jsonReport(results: List<Outcome>): ByteArray = NativeProtocolJson.encode(mapOf("signs" to results.map {
        linkedMapOf<String, Any?>("id" to it.id, "result" to it.status).apply { it.description?.let { message -> put("description", message) } }
    }))
    fun summary(results: List<Outcome>): String {
        val success = results.count { it.status in setOf("DONE_AND_SAVED", "OK") }
        return "Documentos: ${results.size}; resultado positivo: $success; otros resultados: ${results.size - success}."
    }
    private fun outcome(value: Any?): Outcome {
        val row = NativeTriphaseCodec.objectValue(value)
        require(row.keys.all { it in setOf("id", "result", "description", "signature") })
        val id = row["id"] as? String ?: error("Missing result ID")
        val status = row["result"] as? String ?: error("Missing result status")
        require(id.length in 1..256 && id.none(Char::isISOControl) && status in statuses)
        require(!row.containsKey("description") || row["description"] is String) { "Invalid result description" }
        require(!row.containsKey("signature") || row["signature"] is String) { "Invalid result signature" }
        val description = row["description"] as? String
        require(description == null || description.length <= 4096)
        (row["signature"] as? String)?.let { require(it.length <= 2_796_204) }
        return Outcome(id, status, description)
    }
}
