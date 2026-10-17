package dev.junta.firmamobile.profile

import androidx.annotation.VisibleForTesting

object RuntimeProfileScope {
    @Volatile
    private var verifiedOnly: Boolean? = null

    /**
     * Configure before the first runtimeRegistry access. Repeating the same value is allowed.
     */
    @Synchronized
    fun configure(verifiedOnly: Boolean) {
        val configured = this.verifiedOnly
        check(configured == null || configured == verifiedOnly) {
            "Runtime profile scope is already configured"
        }
        this.verifiedOnly = verifiedOnly
    }

    internal fun verifiedOnly(): Boolean = verifiedOnly ?: false

    @VisibleForTesting
    internal fun resetForTest() {
        verifiedOnly = null
    }
}
