package dev.junta.firmamobile.afirma.servlet

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun interface AfirmaRequestResolver {
    suspend fun resolve(request: AfirmaDeferredInvocation): PreparedAfirmaOperation
}

/** A consumed configuration can produce a reviewable operation, never consent.
 * Retrieval and result cipher configurations are independent, as in the pinned
 * launcher, which reparses the complete downloaded XML instead of merging it.
 */
internal class AfirmaDeferredResolver(
    private val transport: AfirmaRequestTransport = AfirmaServletTransport(),
    private val operationFactory: (AfirmaServletInvocation) -> PreparedAfirmaOperation = { nativeOperation(it) },
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AfirmaRequestResolver {
    override suspend fun resolve(request: AfirmaDeferredInvocation): PreparedAfirmaOperation {
        request.begin()
        var parsed: AfirmaServletInvocation? = null
        var prepared: PreparedAfirmaOperation? = null
        var delivered = false
        try {
            val received = transport.retrieve(request.retrievalUrl, request.fileId)
            var transferred = false
            try {
                val encoded = received.take()
                try {
                    withContext(parsingDispatcher) {
                        currentCoroutineContext().ensureActive()
                        val plain = try { request.decodeResponse(encoded) }
                        catch (_: Exception) { throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID) }
                        try {
                            val document = try { AfirmaConfigurationXml.parse(plain) }
                            catch (_: Exception) { throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID) }
                            if (document.operation != request.operation) throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID)
                            if (request.expectedResponseId != null && document.values["id"] != request.expectedResponseId) {
                                throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID)
                            }
                            val returnedEndpoint = document.values["stservlet"]
                            if (request.expectedStorageUrl != null && (returnedEndpoint == null ||
                                    !sameEndpoint(request.expectedStorageUrl, returnedEndpoint))) {
                                throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID)
                            }
                            when (val result = AfirmaServletInvocationParser.parseRetrieved(
                                document.operation, request.sourceOrigin, document.values,
                            )) {
                                is AfirmaServletParseResult.Accepted -> parsed = result.invocation
                                is AfirmaServletParseResult.Unsupported -> throw AfirmaRetrievalException(AfirmaRetrievalProblem.UNSUPPORTED)
                                is AfirmaServletParseResult.Invalid -> throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID)
                                is AfirmaServletParseResult.Deferred -> {
                                    result.invocation.close()
                                    throw AfirmaRetrievalException(AfirmaRetrievalProblem.UNSUPPORTED)
                                }
                            }
                        } finally { plain.fill(0) }
                        currentCoroutineContext().ensureActive()
                    }
                    currentCoroutineContext().ensureActive()
                    val invocation = checkNotNull(parsed)
                    prepared = operationFactory(invocation)
                    // The operation factory takes ownership only after return.
                    parsed = null
                    currentCoroutineContext().ensureActive()
                    delivered = true
                    transferred = true
                    return checkNotNull(prepared)
                } finally { encoded.fill(0) }
            } finally {
                received.close()
                // A cancelled dispatcher return may skip assignment at the call
                // site. Parsed/prepared are held outside withContext so finally
                // can still erase them. No global coroutine owns this material.
                if (!transferred) parsed?.close()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: AfirmaRetrievalException) { throw e }
        catch (_: Exception) { throw AfirmaRetrievalException(AfirmaRetrievalProblem.INVALID) }
        finally {
            if (!delivered) { parsed?.close(); prepared?.close() }
            request.close()
        }
    }

    private fun sameEndpoint(expected: URI, actual: String): Boolean = runCatching {
        AfirmaEndpointPolicy.accepts(URI(actual)) && expected.toASCIIString().toHttpUrl() == actual.toHttpUrl()
    }.getOrDefault(false)
}
