package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R

object AfirmaJavascriptShim {
    const val MAX_SCRIPT_CHARS = 48 * 1024

    fun load(context: Context): String = load(
        context,
        MiniAppletBridgeMode.FUNCTIONAL,
        BuildConfig.ALLOW_QA_PROFILES,
    )

    fun load(
        context: Context,
        mode: MiniAppletBridgeMode,
        qaDiagnosticsEnabled: Boolean = BuildConfig.ALLOW_QA_PROFILES,
        ugrCompatibilityEnabled: Boolean = false,
        cantabriaCompatibilityEnabled: Boolean = false,
        jccmCompatibilityEnabled: Boolean = false,
        sevillaAtseCompatibilityEnabled: Boolean = false,
        policiaCompatibilityEnabled: Boolean = false,
        melillaBatchCompatibilityEnabled: Boolean = false,
        isciiiCertificateSelectionEnabled: Boolean = false,
        valenciaCertificateSelectionEnabled: Boolean = false,
        murciaCompatibilityEnabled: Boolean = false,
    ): String {
        val script = context.resources.openRawResource(R.raw.afirma_shim)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        check(script.countOccurrences(MODE_PLACEHOLDER) == 1)
        check(script.countOccurrences(QA_DIAGNOSTICS_PLACEHOLDER) == 1)
        check(script.countOccurrences(UGR_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(CANTABRIA_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(JCCM_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(SEVILLA_ATSE_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(POLICIA_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER) == 1)
        check(script.countOccurrences(VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER) == 1)
        check(script.countOccurrences(MURCIA_COMPATIBILITY_PLACEHOLDER) == 1)
        val configured = script
            .replace(
                MODE_PLACEHOLDER,
                if (mode == MiniAppletBridgeMode.FUNCTIONAL) "true" else "false",
            )
            .replace(
                QA_DIAGNOSTICS_PLACEHOLDER,
                if (qaDiagnosticsEnabled) "true" else "false",
            )
            .replace(
                UGR_COMPATIBILITY_PLACEHOLDER,
                if (ugrCompatibilityEnabled) "true" else "false",
            )
            .replace(
                CANTABRIA_COMPATIBILITY_PLACEHOLDER,
                if (cantabriaCompatibilityEnabled) "true" else "false",
            )
            .replace(
                JCCM_COMPATIBILITY_PLACEHOLDER,
                if (jccmCompatibilityEnabled) "true" else "false",
            )
            .replace(
                SEVILLA_ATSE_COMPATIBILITY_PLACEHOLDER,
                if (sevillaAtseCompatibilityEnabled) "true" else "false",
            )
            .replace(
                POLICIA_COMPATIBILITY_PLACEHOLDER,
                if (policiaCompatibilityEnabled) "true" else "false",
            )
            .replace(
                MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER,
                if (melillaBatchCompatibilityEnabled) "true" else "false",
            )
            .replace(
                ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER,
                if (isciiiCertificateSelectionEnabled) "true" else "false",
            )
            .replace(
                VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER,
                if (valenciaCertificateSelectionEnabled) "true" else "false",
                MURCIA_COMPATIBILITY_PLACEHOLDER,
                if (murciaCompatibilityEnabled) "true" else "false",
            )
        check(configured.isNotBlank() && configured.length <= MAX_SCRIPT_CHARS)
        return configured
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }

    private const val MODE_PLACEHOLDER = "__JFM_FUNCTIONAL_SIGNING_ENABLED__"
    private const val QA_DIAGNOSTICS_PLACEHOLDER = "__JFM_QA_DIAGNOSTICS_ENABLED__"
    private const val UGR_COMPATIBILITY_PLACEHOLDER = "__JFM_UGR_COMPATIBILITY_ENABLED__"
    private const val CANTABRIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_CANTABRIA_COMPATIBILITY_ENABLED__"
    private const val JCCM_COMPATIBILITY_PLACEHOLDER = "__JFM_JCCM_COMPATIBILITY_ENABLED__"
    private const val SEVILLA_ATSE_COMPATIBILITY_PLACEHOLDER =
        "__JFM_SEVILLA_ATSE_COMPATIBILITY_ENABLED__"
    private const val POLICIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_POLICIA_COMPATIBILITY_ENABLED__"
    private const val MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MELILLA_BATCH_COMPATIBILITY_ENABLED__"
    private const val ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_ISCIII_CERTIFICATE_SELECTION_ENABLED__"
    private const val VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_VALENCIA_CERTIFICATE_SELECTION_ENABLED__"
    private const val MURCIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MURCIA_COMPATIBILITY_ENABLED__"
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
