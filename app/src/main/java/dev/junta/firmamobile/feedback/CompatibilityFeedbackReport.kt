package dev.junta.firmamobile.feedback

import java.net.URLEncoder

/** Display-only allowlist: this API has no certificate, request, device or error input. */
internal class CompatibilityFeedbackReport private constructor(val body: String) {
    fun issueFormUrl(): String =
        "https://github.com/zaguzovmaksim0-hue/workspace-47/issues/new?body=" +
            URLEncoder.encode(body, "UTF-8")

    companion object {
        fun build(appVersion: String, versionCode: Int, androidApi: Int): CompatibilityFeedbackReport =
            CompatibilityFeedbackReport(buildString {
                if (isAllowedVersion(appVersion)) append("appVersion: $appVersion\n")
                if (versionCode > 0) append("versionCode: $versionCode\n")
                if (androidApi > 0) append("androidApi: $androidApi\n")
            })

        // Accept only the app's numeric release version or its configured QA suffix.
        // Unknown values are omitted whole, never truncated or regex-redacted.
        private fun isAllowedVersion(value: String): Boolean {
            if (value.length !in 1..32) return false
            val parts = value.removeSuffix("-qa").split('.')
            return parts.size == 3 && parts.all { part ->
                part.length in 1..9 && part.all { it in '0'..'9' }
            }
        }
    }
}
