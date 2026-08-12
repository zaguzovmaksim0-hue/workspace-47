package dev.junta.firmamobile.signing

import dev.junta.firmamobile.profile.CallbackContractId
import java.util.UUID
import org.json.JSONObject

interface SigningResultEncoder {
    val contractId: CallbackContractId

    fun encodeSuccess(requestId: UUID, signatureBase64: String, certificateBase64: String): String

    fun encodeError(requestId: UUID, code: SigningErrorCode): String
}

class MiniAppletCallbackAdapter : SigningResultEncoder {
    override val contractId = CONTRACT_ID

    override fun encodeSuccess(
        requestId: UUID,
        signatureBase64: String,
        certificateBase64: String,
    ): String = JSONObject()
        .put(TYPE_FIELD, RESULT_TYPE)
        .put(REQUEST_ID_FIELD, requestId.toString())
        .put(STATUS_FIELD, SUCCESS_STATUS)
        .put(SIGNATURE_FIELD, signatureBase64)
        .put(CERTIFICATE_FIELD, certificateBase64)
        .toString()

    override fun encodeError(requestId: UUID, code: SigningErrorCode): String = JSONObject()
        .put(TYPE_FIELD, RESULT_TYPE)
        .put(REQUEST_ID_FIELD, requestId.toString())
        .put(STATUS_FIELD, ERROR_STATUS)
        .put(ERROR_CODE_FIELD, code.name)
        .toString()

    companion object {
        val CONTRACT_ID = CallbackContractId("miniapplet-sign-callback-v1")
        private const val TYPE_FIELD = "type"
        private const val REQUEST_ID_FIELD = "requestId"
        private const val STATUS_FIELD = "status"
        private const val SIGNATURE_FIELD = "signature"
        private const val CERTIFICATE_FIELD = "certificate"
        private const val ERROR_CODE_FIELD = "errorCode"
        private const val RESULT_TYPE = "MINIAPPLET_RESULT"
        private const val SUCCESS_STATUS = "success"
        private const val ERROR_STATUS = "error"
    }
}
