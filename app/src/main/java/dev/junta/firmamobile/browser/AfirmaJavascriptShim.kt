package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.R

object AfirmaJavascriptShim {
    const val MAX_SCRIPT_CHARS = 32 * 1024

    fun load(context: Context): String = load(context, MiniAppletBridgeMode.FUNCTIONAL)

    fun load(context: Context, mode: MiniAppletBridgeMode): String {
        val script = context.resources.openRawResource(R.raw.afirma_shim)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        check(script.countOccurrences(MODE_PLACEHOLDER) == 1)
        val configured = script.replace(
            MODE_PLACEHOLDER,
            if (mode == MiniAppletBridgeMode.FUNCTIONAL) "true" else "false",
        )
        check(configured.isNotBlank() && configured.length <= MAX_SCRIPT_CHARS)
        return configured
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }

    private const val MODE_PLACEHOLDER = "__JFM_FUNCTIONAL_SIGNING_ENABLED__"
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
