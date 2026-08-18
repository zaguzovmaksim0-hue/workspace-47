package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import java.net.URI
import org.json.JSONObject

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
        cdtiCompatibilityEnabled: Boolean = false,
        policiaCompatibilityEnabled: Boolean = false,
        granCanariaCompatibilityEnabled: Boolean = false,
        minecoCompatibilityEnabled: Boolean = false,
        melillaBatchCompatibilityEnabled: Boolean = false,
        lugoBatchCompatibilityEnabled: Boolean = false,
        staBatchOrigin: String = MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
        isciiiCertificateSelectionEnabled: Boolean = false,
        valenciaCertificateSelectionEnabled: Boolean = false,
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
        check(script.countOccurrences(CDTI_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(POLICIA_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(GRAN_CANARIA_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(MINECO_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(LUGO_BATCH_COMPATIBILITY_PLACEHOLDER) == 1)
        check(script.countOccurrences(STA_BATCH_ORIGIN_PLACEHOLDER) == 1)
        check(script.countOccurrences(ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER) == 1)
        val batchOrigin = URI(staBatchOrigin)
        require(
            !batchOrigin.isOpaque && batchOrigin.scheme == "https" && batchOrigin.host != null &&
                batchOrigin.userInfo == null && (batchOrigin.port == -1 || batchOrigin.port == 443) &&
                (batchOrigin.rawPath.isNullOrEmpty() || batchOrigin.rawPath == "/") &&
                batchOrigin.rawQuery == null && batchOrigin.rawFragment == null
        )
        check(script.countOccurrences(VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER) == 1)
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
                CDTI_COMPATIBILITY_PLACEHOLDER,
                if (cdtiCompatibilityEnabled) "true" else "false",
            )
            .replace(
                POLICIA_COMPATIBILITY_PLACEHOLDER,
                if (policiaCompatibilityEnabled) "true" else "false",
            )
            .replace(
                GRAN_CANARIA_COMPATIBILITY_PLACEHOLDER,
                if (granCanariaCompatibilityEnabled) "true" else "false",
            )
            .replace(
                MINECO_COMPATIBILITY_PLACEHOLDER,
                if (minecoCompatibilityEnabled) "true" else "false",
            )
            .replace(
                MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER,
                if (melillaBatchCompatibilityEnabled) "true" else "false",
            )
            .replace(
                LUGO_BATCH_COMPATIBILITY_PLACEHOLDER,
                if (lugoBatchCompatibilityEnabled) "true" else "false",
            )
            .replace(STA_BATCH_ORIGIN_PLACEHOLDER, JSONObject.quote(staBatchOrigin.removeSuffix("/")))
            .replace(
                ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER,
                if (isciiiCertificateSelectionEnabled) "true" else "false",
            )
            .replace(
                VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER,
                if (valenciaCertificateSelectionEnabled) "true" else "false",
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
    private const val CDTI_COMPATIBILITY_PLACEHOLDER =
        "__JFM_CDTI_COMPATIBILITY_ENABLED__"
    private const val POLICIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_POLICIA_COMPATIBILITY_ENABLED__"
    private const val GRAN_CANARIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_GRAN_CANARIA_COMPATIBILITY_ENABLED__"
    private const val MINECO_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MINECO_COMPATIBILITY_ENABLED__"
    private const val MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MELILLA_BATCH_COMPATIBILITY_ENABLED__"
    private const val LUGO_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_LUGO_BATCH_COMPATIBILITY_ENABLED__"
    private const val STA_BATCH_ORIGIN_PLACEHOLDER = "__JFM_STA_BATCH_ORIGIN__"
    private const val ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_ISCIII_CERTIFICATE_SELECTION_ENABLED__"
    private const val VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_VALENCIA_CERTIFICATE_SELECTION_ENABLED__"
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
