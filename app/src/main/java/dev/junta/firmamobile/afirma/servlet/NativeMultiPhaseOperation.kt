package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.LocalSignatureResult
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Generic single triphase, XML/JSON remote batch and local JSON batch. PRE
 * is supplied by the user-approved signing service. It is not represented as
 * independently reconstructed original document content on this device. */
internal class NativeMultiPhaseOperation(
    private val invocation: AfirmaServletInvocation,
    private val services: NativeSigningServiceTransport = NativeHttpsSigningService(),
    private val storage: AfirmaResultTransport = AfirmaServletTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : PreparedAfirmaOperation {
    private val remote = checkNotNull(invocation.remoteOptions)
    private val started = AtomicBoolean(false)
    @Volatile private var closed = false
    override var resultSummary: String? = null
        private set
    @Volatile override var batchReceipt: NativeBatchReceipt? = null
        private set

    private fun recordBatch(outcomes: List<NativeBatchProtocol.Outcome>, origin: NativeBatchReceipt.Origin) {
        val snapshot = NativeBatchReceipt.from(checkNotNull(remote.batch), outcomes, origin)
        batchReceipt = snapshot
        resultSummary = NativeBatchProtocol.summary(outcomes)
    }

    override val details: AfirmaConsentDetails = invocation.payloadCopy().let { bytes ->
        try { AfirmaConsentDetails(invocation.sourceOrigin, destination(invocation.storageUrl),
            invocation.operation.name.lowercase(), if (remote.batch != null) "Batch · ${if (remote.localBatch) "local" else "trifásico"}" +
                if (remote.localBatch && remote.batch.items.any { it.operation == "cosign" && it.format.equals("CAdES", true) }) " · CAdES co-sign" else ""
            else "${remote.format} · trifásico",
            checkNotNull(invocation.algorithm).let { with(NativeTriphaseCodec) { it.wireName() } }, bytes.size,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            serviceDestinations = listOfNotNull(remote.preUrl, remote.postUrl).map(::destination).distinct(),
            batchItems = remote.batch?.items?.size, delegatedSigning = !remote.localBatch,
            requiresExactCertificate = invocation.requiresExactCertificate)
        } finally { bytes.fill(0) }
    }
    override fun certificateCompatible(identity: UnlockedIdentity): Boolean = runCatching {
        if (!invocation.matchesCertificate(identity.certificate)) return@runCatching false
        identity.certificate.checkValidity(Date.from(clock.instant()))
        val usage = identity.certificate.keyUsage
        identity.certificate.publicKey.algorithm.equals("RSA", true) &&
            (usage == null || usage.getOrElse(0) { false } || usage.getOrElse(1) { false })
    }.getOrDefault(false)

    override suspend fun execute(identity: UnlockedIdentity, authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult =
        executeWithCheckpoints(identity, {}, authorizeUpload)

    override suspend fun executeWithCheckpoints(identity: UnlockedIdentity, checkpoint: suspend () -> Unit,
        authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        check(started.compareAndSet(false, true) && !closed)
        val payload = invocation.payloadCopy(); val cipher = invocation.cipherCopy()
        val key = invocation.key; val endpoint = invocation.storageUrl; val id = invocation.sessionId
        var postStarted = false
        suspend fun checkOwner() { currentCoroutineContext().ensureActive(); check(!closed); checkpoint(); check(!closed && certificateCompatible(identity)) }
        try {
            checkOwner()
            val algorithm = checkNotNull(invocation.algorithm)
            val certificate = identity.certificate.encoded
            try {
                val certEncoded = cipher?.encode(certificate) ?: AfirmaIntermediateCipher.encode(certificate, key)
                val result: ByteArray
                if (remote.localBatch) {
                    result = localBatch(payload, identity, ::checkOwner)
                    checkOwner(); authorizeUpload()
                } else if (remote.batch != null) {
                    val batch = remote.batch
                    val label = if (batch.json) "json" else "xml"
                    val certs = identity.chain.ifEmpty { listOf(identity.certificate) }.joinToString(";") { url64(it.encoded) }
                    checkOwner()
                    val preBytes = services.exchange(checkNotNull(remote.preUrl), mapOf(label to url64(payload), "certs" to certs)).use { it.take() }
                    val pre = try { NativeBatchProtocol.pre(preBytes, batch) } finally { preBytes.fill(0) }
                    if (pre.session == null || batch.stopOnError && pre.errors.isNotEmpty()) {
                        val outcomes = batch.items.map { item -> pre.errors.singleOrNull { it.id == item.id } ?: NativeBatchProtocol.Outcome(item.id, "SKIPPED") }
                        result = NativeBatchProtocol.jsonReport(outcomes)
                        recordBatch(outcomes, NativeBatchReceipt.Origin.SERVICE)
                        checkOwner(); authorizeUpload()
                    } else {
                        val signed = NativeTriphaseCodec.sign(pre.session, identity, algorithm, ::checkOwner)
                        // Capture actual completed local contributions before
                        // POST. Repeated counter-signature targets share an ID.
                        val signedIds = signed.signs.map { it.id }.toSet()
                        val td = if (batch.json) NativeTriphaseCodec.encodeJson(signed) else checkNotNull(NativeTriphaseXml.encode(signed))
                        val postDescriptor = if (batch.json) NativeBatchProtocol.descriptorForPost(payload, pre.errors) else payload.copyOf()
                        try {
                            checkOwner(); authorizeUpload(); checkOwner(); postStarted = true
                            result = services.exchange(checkNotNull(remote.postUrl), mapOf(label to url64(postDescriptor), "certs" to certs, "tridata" to url64(td))).use { it.take() }
                        } finally { td.fill(0); postDescriptor.fill(0) }
                        try {
                            val outcomes = NativeBatchProtocol.results(result, batch, pre.errors, signedIds)
                            recordBatch(outcomes, NativeBatchReceipt.Origin.SERVICE)
                        } catch (error: Exception) {
                            result.fill(0)
                            throw error
                        }
                    }
                } else {
                    val operation = when (invocation.operation) {
                        AfirmaServletOperation.SIGN -> "sign"; AfirmaServletOperation.COSIGN -> "cosign"
                        AfirmaServletOperation.COUNTERSIGN -> "countersign"; else -> error("Invalid single operation")
                    }
                    val certs = identity.chain.ifEmpty { listOf(identity.certificate) }.joinToString(",") { url64(it.encoded) }
                    val parameters = NativeJavaProperties.encode(remote.properties)
                    val common = linkedMapOf("cop" to operation, "format" to remote.format,
                        "algo" to with(NativeTriphaseCodec) { algorithm.wireName() }, "cert" to certs,
                        "doc" to url64(payload), "params" to url64(parameters))
                    parameters.fill(0)
                    checkOwner()
                    val pre = services.exchange(checkNotNull(remote.preUrl), common + ("op" to "pre")).use { it.take() }
                    val session = try {
                        val xml = AfirmaServletInvocationParser.strictBase64(pre.toString(Charsets.UTF_8).trim(), 2_097_152)
                        try { NativeTriphaseXml.parse(xml) ?: error("Invalid pre-signature") } finally { xml.fill(0) }
                    } finally { pre.fill(0) }
                    session.format?.let { require(it.equals(remote.format, true)) }
                    val signed = NativeTriphaseCodec.sign(session, identity, algorithm, ::checkOwner)
                    val xml = checkNotNull(NativeTriphaseXml.encode(signed))
                    val response = try {
                        checkOwner(); authorizeUpload(); checkOwner(); postStarted = true
                        services.exchange(checkNotNull(remote.postUrl), common + mapOf("op" to "post", "session" to url64(xml))).use { it.take() }
                    } finally { xml.fill(0) }
                    result = try {
                        val text = response.toString(Charsets.UTF_8).trim(); require(text.startsWith("OK NEWID="))
                        AfirmaServletInvocationParser.strictBase64(text.removePrefix("OK NEWID="), 2_097_152)
                    } finally { response.fill(0) }
                    resultSummary = "El servicio devolvió un resultado. No acredita por sí solo la aceptación del trámite."
                }
                try {
                    checkOwner()
                    val encoded = cipher?.encode(result) ?: AfirmaIntermediateCipher.encode(result, key)
                    val wire = if (remote.batch != null) encoded + if (remote.needCertificate) "|$certEncoded" else "" else "$certEncoded|$encoded"
                    val delivered = storage.store(endpoint, id, wire)
                    // POST may have saved individual signatures even when the
                    // final browser-facing receipt did not reach its servlet.
                    return if (postStarted && delivered != AfirmaDeliveryResult.ACKNOWLEDGED) AfirmaDeliveryResult.UNCERTAIN else delivered
                } finally { result.fill(0) }
            } finally { certificate.fill(0) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (postStarted) return AfirmaDeliveryResult.UNCERTAIN
            throw error
        } finally { payload.fill(0); cipher?.close() }
    }

    private suspend fun localBatch(payload: ByteArray, identity: UnlockedIdentity, checkpoint: suspend () -> Unit): ByteArray {
        val batch = checkNotNull(remote.batch); require(batch.json)
        val rows = mutableListOf<MutableMap<String, Any?>>()
        var failed = false
        var retainedSignatureChars = 0L
        for (item in batch.items) {
            checkpoint()
            if (failed && batch.stopOnError) { rows += linkedMapOf("id" to item.id, "result" to "SKIPPED"); continue }
            var bytes: ByteArray? = null
            try {
                bytes = AfirmaServletInvocationParser.strictBase64(item.dataReference, 524_288)
                val input = bytes
                val signed = withContext(Dispatchers.Default) { signLocal(input, item, identity, batch.algorithm) }
                check(signed is LocalSignatureResult.Success)
                val encoded = signed.signature.use { it.withBytes { data -> Base64.getEncoder().encodeToString(data) } }
                checkpoint()
                retainedSignatureChars += encoded.length
                require(retainedSignatureChars <= 1_900_000L) { "Local batch result exceeds budget" }
                rows += linkedMapOf("id" to item.id, "result" to "DONE_AND_SAVED", "signature" to encoded)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                failed = true
                if (batch.stopOnError) rows.forEach { it["result"] = "SKIPPED"; it.remove("signature") }
                rows += linkedMapOf("id" to item.id, "result" to "ERROR_PRE", "description" to "Local signing failed or requires an unsupported constraint")
            } finally { bytes?.fill(0) }
        }
        val result = NativeProtocolJson.encode(mapOf("signs" to rows)); require(result.size <= 2_097_152)
        recordBatch(NativeBatchProtocol.results(result, batch), NativeBatchReceipt.Origin.LOCAL)
        return result
    }
    private fun signLocal(bytes: ByteArray, item: NativeBatchItem, identity: UnlockedIdentity,
        algorithm: dev.junta.firmamobile.signing.SigningAlgorithm): LocalSignatureResult {
        require(item.operation != "countersign")
        NativePadesOptions.parse(item.extraProperties)?.takeIf { NativePadesOptions.acceptsFormat(item.format) }?.let {
            return NativePadesEngine(clock).sign(bytes, identity, algorithm, it, item.operation == "cosign")
        }
        NativeXadesOptions.parse(item.format, item.extraProperties)?.let {
            require(item.operation == "sign")
            return NativeXadesEngine(clock).sign(bytes, identity, algorithm, it)
        }
        require(item.format.equals("CAdES", true) && item.extraProperties.keys.all { it == "mode" })
        val mode = item.extraProperties["mode"]
        require(mode == null || mode in setOf("explicit", "implicit"))
        if (item.operation == "cosign") return NativeCadesCoSignEngine(clock).cosign(bytes, identity, algorithm, mode?.let { it == "explicit" })
        require(item.operation == "sign")
        return NativeCadesEngine(clock = clock).sign(bytes, identity, algorithm, mode != "implicit")
    }
    override suspend fun notifyCancellation(authorizeUpload: suspend () -> Unit): AfirmaDeliveryResult {
        check(started.compareAndSet(false, true) && !closed)
        val endpoint = invocation.storageUrl; val id = invocation.sessionId
        invocation.close(); authorizeUpload(); currentCoroutineContext().ensureActive(); check(!closed)
        return storage.store(endpoint, id, "CANCEL")
    }
    override fun close() { closed = true; invocation.close(); batchReceipt = null }
    private fun url64(bytes: ByteArray) = Base64.getUrlEncoder().encodeToString(bytes)
    private fun destination(uri: URI) = URI("https", null, uri.host, uri.port, uri.path, null, null).toASCIIString()
}

internal fun nativeOperation(invocation: AfirmaServletInvocation): PreparedAfirmaOperation =
    if (invocation.remoteOptions != null) NativeMultiPhaseOperation(invocation) else NativeAfirmaOperation(invocation)
