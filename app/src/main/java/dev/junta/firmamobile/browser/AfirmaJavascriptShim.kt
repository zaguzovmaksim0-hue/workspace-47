package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.R

object AfirmaJavascriptShim {
    const val MAX_SCRIPT_CHARS = 16 * 1024

    fun load(context: Context): String {
        val script = context.resources.openRawResource(R.raw.afirma_shim)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        check(script.isNotBlank() && script.length <= MAX_SCRIPT_CHARS)
        return script
    }
}
