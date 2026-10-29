package dev.junta.firmamobile.afirma.servlet

import java.net.URI

internal class RelayResultTransport(
    private val store: AfirmaRelayStore,
    private val scope: AfirmaRelayStore.Scope,
    private val fallback: AfirmaResultTransport,
    private val consumeTimeoutMillis: Long = 15_000,
) : AfirmaResultTransport {
    override suspend fun store(endpoint: URI, sessionId: String, result: String): AfirmaDeliveryResult {
        val relayed = relayResult(endpoint, sessionId, result)
        return relayed ?: fallback.store(endpoint, sessionId, result)
    }

    private suspend fun relayResult(
        endpoint: URI,
        sessionId: String,
        result: String,
    ): AfirmaDeliveryResult? {
        val blocked = store.isBlocked(scope, endpointOrigin(endpoint))
        val receipt = if (blocked) store.offerResult(scope, sessionId, result) else null
        return when {
            receipt == null -> null
            store.awaitReceipt(receipt, consumeTimeoutMillis) -> AfirmaDeliveryResult.ACKNOWLEDGED
            else -> {
                val withdrawn = store.withdraw(scope, sessionId)
                if (!withdrawn && receipt.isCompleted && receipt.getCompleted()) {
                    AfirmaDeliveryResult.ACKNOWLEDGED
                } else {
                    null
                }
            }
        }
    }
}

internal class RelayRequestTransport(
    private val store: AfirmaRelayStore,
    private val scope: AfirmaRelayStore.Scope,
    private val fallback: AfirmaRequestTransport,
) : AfirmaRequestTransport {
    override suspend fun retrieve(endpoint: URI, fileId: String): AfirmaRetrievedBytes {
        val data = store.takeUpload(scope, fileId)
        return data?.let { AfirmaRetrievedBytes(it.toByteArray(Charsets.UTF_8)) }
            ?: fallback.retrieve(endpoint, fileId)
    }
}

private fun endpointOrigin(endpoint: URI): String {
    val host = checkNotNull(endpoint.host) { "Endpoint has no host" }
    val port = endpoint.port
    return when {
        port == -1 || port == DEFAULT_HTTPS_PORT -> "https://$host"
        else -> "https://$host:$port"
    }
}

private const val DEFAULT_HTTPS_PORT = 443
