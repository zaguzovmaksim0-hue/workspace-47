package dev.junta.firmamobile.afirma.servlet

internal class AfirmaCancellationOutcome {
    private var authorized: Boolean = false

    var result: AfirmaConsentProblem? = null
        private set

    fun markAuthorized(): Boolean {
        if (result != null) return false
        authorized = true
        return true
    }

    fun complete(delivery: AfirmaDeliveryResult): AfirmaConsentProblem {
        result?.let { return it }
        val outcome = if (!authorized) AfirmaConsentProblem.CANCEL_NOT_SENT else when (delivery) {
            AfirmaDeliveryResult.ACKNOWLEDGED -> AfirmaConsentProblem.CANCEL_ACKNOWLEDGED
            AfirmaDeliveryResult.REJECTED -> AfirmaConsentProblem.CANCEL_REJECTED
            AfirmaDeliveryResult.NOT_SENT -> AfirmaConsentProblem.CANCEL_NOT_SENT
            AfirmaDeliveryResult.UNCERTAIN -> AfirmaConsentProblem.CANCEL_UNCERTAIN
        }
        result = outcome
        return outcome
    }

    fun interrupt(): AfirmaConsentProblem {
        result?.let { return it }
        val outcome = if (authorized) AfirmaConsentProblem.CANCEL_UNCERTAIN
            else AfirmaConsentProblem.CANCEL_NOT_SENT
        result = outcome
        return outcome
    }
}
