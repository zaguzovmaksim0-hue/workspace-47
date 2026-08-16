package dev.junta.firmamobile.signing

import android.util.JsonReader
import android.util.JsonToken
import dev.junta.firmamobile.network.ExtremaduraBatchUrlPolicy
import dev.junta.firmamobile.network.HuescaBatchUrlPolicy
import dev.junta.firmamobile.network.LaPalmaBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlOperation
import dev.junta.firmamobile.network.MelillaBatchUrlPolicy
import dev.junta.firmamobile.network.MelillaBatchUrlValidation
import dev.junta.firmamobile.network.ProfileHttpCancellation
import dev.junta.firmamobile.network.ProfileHttpFailure
import dev.junta.firmamobile.network.ProfileHttpRequest
import dev.junta.firmamobile.network.ProfileHttpResult
import dev.junta.firmamobile.network.ProfileHttpTransport
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.network.ValidatedNetworkUrl
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes the public Melilla AutoFirma JSON batch presign/postsign contract.
 * Runtime URLs remain fixed to [MelillaBatchUrlPolicy].
 */
class MelillaBatchProtocolAdapter internal constructor(
    transport: ProfileHttpTransport,
    urlPolicy: MelillaBatchUrlPolicy = MelillaBatchUrlPolicy(),
) : BatchSigningProtocolAdapter {
    private val delegate = StaBatchProtocolAdapter(
        transport = transport,
        contract = StaBatchProtocolContract(
            id = ID,
            profileId = PROFILE_ID,
            profileVersion = PROFILE_VERSION,
            origin = TrustedOrigin("https", "sede.melilla.es", 443),
            urlValidator = StaBatchUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    override val id: SigningProtocolId = ID

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult = delegate.prepare(request, certificateChain)

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult = delegate.complete(request, preSign, localSignatures)

    companion object {
        val ID = SigningProtocolId("melilla-batch-autoscript-v1")
        private const val PROFILE_ID = "melilla-sede"
        private const val PROFILE_VERSION = 1
    }
}

/**
 * Executes the same observed STA batch wire contract for the fixed Extremadura profile.
 * Its protocol id, profile identity, origin, and runtime URL policy are independent from Melilla.
 */
class ExtremaduraBatchProtocolAdapter internal constructor(
    transport: ProfileHttpTransport,
    urlPolicy: ExtremaduraBatchUrlPolicy = ExtremaduraBatchUrlPolicy(),
) : BatchSigningProtocolAdapter {
    private val delegate = StaBatchProtocolAdapter(
        transport = transport,
        contract = StaBatchProtocolContract(
            id = ID,
            profileId = PROFILE_ID,
            profileVersion = PROFILE_VERSION,
            origin = TrustedOrigin("https", "tramites.juntaex.es", 443),
            urlValidator = StaBatchUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    override val id: SigningProtocolId = ID

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult = delegate.prepare(request, certificateChain)

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult = delegate.complete(request, preSign, localSignatures)

    companion object {
        val ID = SigningProtocolId("extremadura-batch-autoscript-v1")
        private const val PROFILE_ID = "extremadura-tramites"
        private const val PROFILE_VERSION = 1
    }
}

/**
 * Executes the observed STA batch wire contract for the fixed Cabildo de La Palma profile.
 * The protocol id, profile identity, origin, and runtime URL policy remain portal-specific.
 */
class LaPalmaBatchProtocolAdapter internal constructor(
    transport: ProfileHttpTransport,
    urlPolicy: LaPalmaBatchUrlPolicy = LaPalmaBatchUrlPolicy(),
) : BatchSigningProtocolAdapter {
    private val delegate = StaBatchProtocolAdapter(
        transport = transport,
        contract = StaBatchProtocolContract(
            id = ID,
            profileId = PROFILE_ID,
            profileVersion = PROFILE_VERSION,
            origin = TrustedOrigin("https", "sedeelectronica.cabildodelapalma.es", 443),
            urlValidator = StaBatchUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    override val id: SigningProtocolId = ID

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult = delegate.prepare(request, certificateChain)

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult = delegate.complete(request, preSign, localSignatures)

    companion object {
        val ID = SigningProtocolId("la-palma-batch-autoscript-v1")
        private const val PROFILE_ID = "la-palma-sede-electronica"
        private const val PROFILE_VERSION = 1
    }
}

/**
 * Executes the observed STA batch wire contract for Diputación de Huesca's fixed OVC profile.
 */
class HuescaBatchProtocolAdapter internal constructor(
    transport: ProfileHttpTransport,
    urlPolicy: HuescaBatchUrlPolicy = HuescaBatchUrlPolicy(),
) : BatchSigningProtocolAdapter {
    private val delegate = StaBatchProtocolAdapter(
        transport = transport,
        contract = StaBatchProtocolContract(
            id = ID,
            profileId = PROFILE_ID,
            profileVersion = PROFILE_VERSION,
            origin = TrustedOrigin("https", "ovc24.dphuesca.es", 443),
            urlValidator = StaBatchUrlValidator { rawUrl, operation, operationId, documentId ->
                urlPolicy.validate(rawUrl, operation, operationId, documentId)
            },
        ),
    )

    override val id: SigningProtocolId = ID

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult = delegate.prepare(request, certificateChain)

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult = delegate.complete(request, preSign, localSignatures)

    companion object {
        val ID = SigningProtocolId("huesca-batch-autoscript-v1")
        private const val PROFILE_ID = "diputacion-huesca-portal"
        private const val PROFILE_VERSION = 1
    }
}

private data class StaBatchProtocolContract(
    val id: SigningProtocolId,
    val profileId: String,
    val profileVersion: Int,
    val origin: TrustedOrigin,
    val urlValidator: StaBatchUrlValidator,
)

private fun interface StaBatchUrlValidator {
    fun validate(
        rawUrl: String,
        expectedOperation: MelillaBatchUrlOperation,
        expectedOperationId: String?,
        expectedDocumentId: String?,
    ): MelillaBatchUrlValidation
}

private class StaBatchProtocolAdapter(
    private val transport: ProfileHttpTransport,
    private val contract: StaBatchProtocolContract,
) : BatchSigningProtocolAdapter {
    override val id: SigningProtocolId = contract.id

    override fun prepare(
        request: NormalizedBatchSigningRequest,
        certificateChain: List<X509Certificate>,
    ): BatchProtocolPrepareResult {
        if (!request.isOpen() || !request.matchesContract() || certificateChain.isEmpty() ||
            certificateChain.size > MAX_CERTIFICATES
        ) {
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        val urls = validateUrls(request)
            ?: return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        val batchJson = try {
            buildBatchJson(request)
        } catch (_: Exception) {
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        val jsonParameter = encodeUrlBase64(batchJson)
        batchJson.fill(0)
        val certsParameter = try {
            encodeCertificateChain(certificateChain)
        } catch (_: Exception) {
            jsonParameter.fill(0)
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }

        val preBody = try {
            buildFormBody(
                "json" to jsonParameter,
                "certs" to certsParameter,
            )
        } catch (_: Exception) {
            jsonParameter.fill(0)
            certsParameter.fill(0)
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.INVALID_REQUEST)
        }
        val networkResult = try {
            ProfileHttpRequest(ValidatedNetworkUrl(urls.preUrl), preBody).use { httpRequest ->
                post(httpRequest)
            }
        } catch (_: Exception) {
            preBody.fill(0)
            jsonParameter.fill(0)
            certsParameter.fill(0)
            return BatchProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        preBody.fill(0)

        return when (networkResult) {
            is ProfileHttpResult.Failure -> {
                jsonParameter.fill(0)
                certsParameter.fill(0)
                BatchProtocolPrepareResult.Failure(networkResult.toSigningError())
            }
            is ProfileHttpResult.Success -> networkResult.response.use { response ->
                response.withBody { body ->
                    val parsed = parsePreResponse(request, body)
                    if (parsed == null) {
                        jsonParameter.fill(0)
                        certsParameter.fill(0)
                        BatchProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                    } else {
                        val state = StaBatchPreSignState(
                            postUrl = urls.postUrl,
                            jsonParameter = jsonParameter,
                            certsParameter = certsParameter,
                            triData = parsed.triData,
                        )
                        val result = BatchPreSignResult(request, parsed.inputs, state)
                        parsed.inputs.forEach { it.fill(0) }
                        BatchProtocolPrepareResult.Success(result)
                    }
                }
            }
        }
    }

    override fun complete(
        request: NormalizedBatchSigningRequest,
        preSign: BatchPreSignResult,
        localSignatures: List<LocalSignature>,
    ): BatchProtocolCompletionResult {
        if (!request.isOpen() || !request.matchesContract() || localSignatures.size != preSign.inputCount) {
            localSignatures.forEach(LocalSignature::close)
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        val state = preSign.consumeState(request, localSignatures.size) as? StaBatchPreSignState
        if (state == null) {
            localSignatures.forEach(LocalSignature::close)
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }

        val postBody = try {
            state.buildPostBody(localSignatures)
        } catch (_: Exception) {
            localSignatures.forEach(LocalSignature::close)
            state.close()
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        } finally {
            localSignatures.forEach(LocalSignature::close)
        }

        val networkResult = try {
            ProfileHttpRequest(ValidatedNetworkUrl(state.postUrl), postBody).use { httpRequest ->
                post(httpRequest)
            }
        } catch (_: Exception) {
            postBody.fill(0)
            state.close()
            return BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
        }
        postBody.fill(0)
        state.close()

        return when (networkResult) {
            is ProfileHttpResult.Failure ->
                BatchProtocolCompletionResult.Failure(networkResult.toSigningError())
            is ProfileHttpResult.Success -> networkResult.response.use { response ->
                response.withBody { body ->
                    if (body.isEmpty() || body.size > MAX_WIRE_RESPONSE_BYTES || !isStrictJson(body)) {
                        BatchProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED)
                    } else {
                        BatchProtocolCompletionResult.Success(BatchProtocolResponse(body.copyOf()))
                    }
                }
            }
        }
    }

    private fun NormalizedBatchSigningRequest.matchesContract(): Boolean =
        protocolId == contract.id &&
            context.profileId == contract.profileId &&
            context.profileVersion == contract.profileVersion &&
            context.origin == contract.origin &&
            algorithm == SigningAlgorithm.SHA256_WITH_RSA &&
            format == BatchSigningFormat.CADES &&
            suboperation == SUBOPERATION_SIGN &&
            !stopOnError &&
            documents.isNotEmpty() &&
            documents.size <= MAX_BATCH_DOCUMENTS &&
            documents.map { it.id }.toSet().size == documents.size &&
            documents.all { document ->
                document.suboperation == null || document.suboperation == SUBOPERATION_SIGN
            }

    private fun validateUrls(request: NormalizedBatchSigningRequest): ValidatedUrls? {
        val pre = contract.urlValidator.validate(
            request.preSignerUrl,
            MelillaBatchUrlOperation.PRESIGN,
            request.operationId,
            null,
        ) as? MelillaBatchUrlValidation.Allowed ?: return null
        val post = contract.urlValidator.validate(
            request.postSignerUrl,
            MelillaBatchUrlOperation.POSTSIGN,
            request.operationId,
            null,
        ) as? MelillaBatchUrlValidation.Allowed ?: return null
        request.documents.forEach { document ->
            val data = contract.urlValidator.validate(
                document.dataReference,
                MelillaBatchUrlOperation.GETDATA,
                request.operationId,
                document.id,
            )
            if (data !is MelillaBatchUrlValidation.Allowed) return null
        }
        return ValidatedUrls(pre.url, post.url)
    }

    private fun buildBatchJson(request: NormalizedBatchSigningRequest): ByteArray {
        val signs = JSONArray()
        request.documents.forEach { document ->
            val sign = JSONObject()
                .put("id", document.id)
                .put("datareference", document.dataReference)
                .put("format", document.format.wireName())
            document.suboperation?.let { sign.put("suboperation", it) }
            document.format.extraParams()?.let { extra ->
                val raw = extra.encodeToByteArray()
                val encoded = Base64.getEncoder().encode(raw)
                raw.fill(0)
                try {
                    sign.put("extraparams", String(encoded, StandardCharsets.US_ASCII))
                } finally {
                    encoded.fill(0)
                }
            }
            signs.put(sign)
        }
        return JSONObject()
            .put("algorithm", request.algorithm.wireName())
            .put("format", request.format.wireName())
            .put("suboperation", request.suboperation)
            .put("singlesigns", signs)
            .put("stoponerror", request.stopOnError)
            .toString()
            .encodeToByteArray()
    }

    private fun encodeCertificateChain(certificateChain: List<X509Certificate>): ByteArray {
        val output = ByteArrayOutputStream()
        certificateChain.forEachIndexed { index, certificate ->
            val der = certificate.encoded
            if (der.isEmpty() || der.size > MAX_CERTIFICATE_BYTES) {
                der.fill(0)
                throw IllegalArgumentException("Invalid certificate")
            }
            val encoded = Base64.getUrlEncoder().encode(der)
            der.fill(0)
            try {
                if (index > 0) output.write(';'.code)
                output.write(encoded)
            } finally {
                encoded.fill(0)
            }
            if (output.size() > MAX_CERTS_PARAMETER_BYTES) throw IllegalArgumentException("Certificate chain too large")
        }
        return output.toByteArray()
    }

    private fun parsePreResponse(
        request: NormalizedBatchSigningRequest,
        body: ByteArray,
    ): ParsedPreResponse? {
        if (body.isEmpty() || body.size > MAX_WIRE_RESPONSE_BYTES || !isStrictJson(body)) return null
        val raw = decodeUtf8(body) ?: return null
        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return null
        }
        if (root.keyNames() != ROOT_KEYS) return null
        val triData = root.optJSONObject("td") ?: return null
        if (triData.keyNames() != TRI_DATA_KEYS) return null
        val signInfo = triData.optJSONArray("signinfo") ?: return null
        if (signInfo.length() != request.documents.size) return null

        val inputs = ArrayList<ByteArray>(request.documents.size)
        try {
            request.documents.forEachIndexed { index, expectedDocument ->
                val item = signInfo.optJSONObject(index) ?: run {
                    inputs.clearAndZero()
                    return null
                }
                if (item.keyNames() != SIGN_INFO_KEYS || item.opt("id") !is String ||
                    item.optString("id") != expectedDocument.id
                ) {
                    inputs.clearAndZero()
                    return null
                }
                val params = item.optJSONObject("params") ?: run {
                    inputs.clearAndZero()
                    return null
                }
                val preValue = params.opt("PRE") as? String ?: run {
                    inputs.clearAndZero()
                    return null
                }
                val needPre = params.opt("NEED_PRE")
                if (needPre != null && needPre !is String ||
                    needPre is String && needPre != "true" && needPre != "false"
                ) {
                    inputs.clearAndZero()
                    return null
                }
                val decoded = try {
                    Base64.getDecoder().decode(preValue)
                } catch (_: IllegalArgumentException) {
                    inputs.clearAndZero()
                    return null
                }
                if (decoded.isEmpty() || decoded.size > MAX_PRE_SIGN_BYTES) {
                    decoded.fill(0)
                    inputs.clearAndZero()
                    return null
                }
                inputs += decoded
            }
            val triDataBytes = triData.toString().encodeToByteArray()
            if (triDataBytes.size > MAX_WIRE_RESPONSE_BYTES) {
                triDataBytes.fill(0)
                inputs.clearAndZero()
                return null
            }
            return ParsedPreResponse(inputs, triDataBytes)
        } catch (_: RuntimeException) {
            inputs.clearAndZero()
            return null
        }
    }

    private fun post(request: ProfileHttpRequest): ProfileHttpResult {
        val cancellation = ProfileHttpCancellation()
        return try {
            transport.post(request, cancellation)
        } catch (_: Exception) {
            ProfileHttpResult.Failure(
                cancellation.snapshotFailure(ProfileHttpFailure.NETWORK_ERROR),
            )
        }
    }

    private data class ValidatedUrls(val preUrl: URI, val postUrl: URI)

    private data class ParsedPreResponse(
        val inputs: MutableList<ByteArray>,
        val triData: ByteArray,
    )

    private class StaBatchPreSignState(
        val postUrl: URI,
        jsonParameter: ByteArray,
        certsParameter: ByteArray,
        triData: ByteArray,
    ) : BatchPreSignState {
        private var jsonParameter: ByteArray? = jsonParameter
        private var certsParameter: ByteArray? = certsParameter
        private var triData: ByteArray? = triData

        @Synchronized
        fun buildPostBody(signatures: List<LocalSignature>): ByteArray {
            val json = checkNotNull(jsonParameter) { "Batch state is closed" }
            val certs = checkNotNull(certsParameter) { "Batch state is closed" }
            val triDataBytes = checkNotNull(triData) { "Batch state is closed" }
            val triDataJson = JSONObject(decodeUtf8(triDataBytes) ?: error("Invalid tri-data"))
            val signInfo = triDataJson.getJSONArray("signinfo")
            if (signInfo.length() != signatures.size) error("Signature count changed")
            signatures.forEachIndexed { index, signature ->
                val params = signInfo.getJSONObject(index).getJSONObject("params")
                val encoded = signature.withBytes { bytes -> Base64.getEncoder().encode(bytes) }
                try {
                    params.put("PK1", String(encoded, StandardCharsets.US_ASCII))
                } finally {
                    encoded.fill(0)
                }
                if (params.optString("NEED_PRE") != "true") {
                    params.remove("PRE")
                }
            }
            val serialized = triDataJson.toString().encodeToByteArray()
            val encodedTriData = encodeUrlBase64(serialized)
            serialized.fill(0)
            return try {
                buildFormBody(
                    "json" to json,
                    "certs" to certs,
                    "tridata" to encodedTriData,
                )
            } finally {
                encodedTriData.fill(0)
            }
        }

        @Synchronized
        override fun close() {
            jsonParameter?.fill(0)
            jsonParameter = null
            certsParameter?.fill(0)
            certsParameter = null
            triData?.fill(0)
            triData = null
        }
    }

    private companion object {
        const val SUBOPERATION_SIGN = "sign"
        private val ROOT_KEYS = setOf("td")
        private val TRI_DATA_KEYS = setOf("signinfo")
        private val SIGN_INFO_KEYS = setOf("id", "params")
        private const val MAX_CERTIFICATES = 16
        private const val MAX_CERTIFICATE_BYTES = 128 * 1024
        private const val MAX_CERTS_PARAMETER_BYTES = 2 * 1024 * 1024
        private const val MAX_WIRE_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_PRE_SIGN_BYTES = 1024 * 1024
    }
}

private fun BatchSigningFormat.wireName(): String = when (this) {
    BatchSigningFormat.CADES -> "CAdES"
    BatchSigningFormat.PADES -> "PAdES"
    BatchSigningFormat.XADES -> "XAdES"
}

private fun BatchSigningFormat.extraParams(): String? = when (this) {
    BatchSigningFormat.CADES -> null
    BatchSigningFormat.PADES -> "signatureSubFilter=ETSI.CAdES.detached"
    BatchSigningFormat.XADES -> "mode=implicit"
}

private fun SigningAlgorithm.wireName(): String = when (this) {
    SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
    SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
    SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
}

private fun encodeUrlBase64(value: ByteArray): ByteArray = Base64.getUrlEncoder().encode(value)

private fun buildFormBody(vararg fields: Pair<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    fields.forEachIndexed { index, (name, value) ->
        if (index > 0) output.write('&'.code)
        output.write(name.toByteArray(StandardCharsets.US_ASCII))
        output.write('='.code)
        output.write(value)
        if (output.size() > ProfileHttpRequest.MAX_REQUEST_BYTES) {
            throw IllegalArgumentException("Batch form body too large")
        }
    }
    return output.toByteArray()
}

private fun decodeUtf8(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: Exception) {
    null
}

private fun isStrictJson(bytes: ByteArray): Boolean {
    val raw = decodeUtf8(bytes) ?: return false
    return try {
        JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            readStrictJsonValue(reader, 0)
            reader.peek() == JsonToken.END_DOCUMENT
        }
    } catch (_: Exception) {
        false
    }
}

private fun readStrictJsonValue(reader: JsonReader, depth: Int) {
    require(depth <= MAX_BATCH_JSON_DEPTH)
    when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val names = linkedSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                require(names.add(reader.nextName()))
                readStrictJsonValue(reader, depth + 1)
            }
            reader.endObject()
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            while (reader.hasNext()) readStrictJsonValue(reader, depth + 1)
            reader.endArray()
        }
        JsonToken.STRING -> reader.nextString()
        JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull()
        else -> error("Unexpected JSON token")
    }
}

private fun JSONObject.keyNames(): Set<String> = buildSet {
    val names = keys()
    while (names.hasNext()) add(names.next())
}

private fun MutableList<ByteArray>.clearAndZero() {
    forEach { it.fill(0) }
    clear()
}

private const val MAX_BATCH_JSON_DEPTH = 24
