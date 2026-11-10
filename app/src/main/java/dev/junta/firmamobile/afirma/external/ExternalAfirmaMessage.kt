package dev.junta.firmamobile.afirma.external

import dev.junta.firmamobile.R
import dev.junta.firmamobile.afirma.servlet.ExternalAfirmaReceiveResult

/** All displayed text comes from local resources, never from an incoming request. */
internal fun externalAfirmaMessage(result: ExternalAfirmaReceiveResult): Int? = when (result) {
    ExternalAfirmaReceiveResult.OFFERED -> null
    ExternalAfirmaReceiveResult.BUSY -> R.string.external_afirma_busy
    ExternalAfirmaReceiveResult.UNAVAILABLE -> R.string.external_afirma_unavailable
    ExternalAfirmaReceiveResult.INVALID_REQUEST -> R.string.external_afirma_invalid_request
    ExternalAfirmaReceiveResult.INDIRECT_RETRIEVAL_UNSUPPORTED -> R.string.external_afirma_indirect_unsupported
    ExternalAfirmaReceiveResult.DESKTOP_WEBSOCKET_UNSUPPORTED -> R.string.external_afirma_websocket_unsupported
    ExternalAfirmaReceiveResult.CERTIFICATE_FILTER_UNSUPPORTED -> R.string.external_afirma_filter_unsupported
    ExternalAfirmaReceiveResult.REQUEST_CONSTRAINT_UNSUPPORTED -> R.string.external_afirma_constraint_unsupported
    ExternalAfirmaReceiveResult.CONFIRMATION_REQUIRED -> R.string.external_afirma_confirmation_required
    ExternalAfirmaReceiveResult.UNSUPPORTED_REQUEST -> R.string.external_afirma_rejected
    ExternalAfirmaReceiveResult.REVIEW_FAILED -> R.string.external_afirma_review_failed
}
