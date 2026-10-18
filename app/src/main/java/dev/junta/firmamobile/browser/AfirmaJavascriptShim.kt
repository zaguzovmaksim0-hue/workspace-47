package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import java.net.URI
import org.json.JSONObject

internal data class AfirmaShimCompatibilityFlags(
    val ugr: Boolean = false,
    val cantabria: Boolean = false,
    val jccm: Boolean = false,
    val jccmRegistro: Boolean = false,
    val sevillaAtse: Boolean = false,
    val airef: Boolean = false,
    val cdti: Boolean = false,
    val policia: Boolean = false,
    val granCanaria: Boolean = false,
    val fuerteventura: Boolean = false,
    val canarias: Boolean = false,
    val mineco: Boolean = false,
    val melillaBatch: Boolean = false,
    val lugoBatch: Boolean = false,
    val caibBatch: Boolean = false,
    val isciiiCertificateSelection: Boolean = false,
    val valenciaCertificateSelection: Boolean = false,
    val xuntaGalicia: Boolean = false,
    val euskadiClientAuthPost: Boolean = false,
    val acceda: Boolean = false,
    val badajoz: Boolean = false,
)

object AfirmaJavascriptShim {
    const val MAX_SCRIPT_CHARS = 96 * 1024

    fun load(context: Context): String = load(
        context,
        MiniAppletBridgeMode.FUNCTIONAL,
        BuildConfig.ALLOW_QA_PROFILES,
    )

    internal fun load(
        context: Context,
        mode: MiniAppletBridgeMode,
        qaDiagnosticsEnabled: Boolean = BuildConfig.ALLOW_QA_PROFILES,
        flags: AfirmaShimCompatibilityFlags = AfirmaShimCompatibilityFlags(),
        staBatchOrigin: String = MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
    ): String {
        val script = context.resources.openRawResource(R.raw.afirma_shim)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        check(script.countOccurrences(MODE_PLACEHOLDER) == 1)
        check(script.countOccurrences(QA_DIAGNOSTICS_PLACEHOLDER) == 1)
        FLAG_PLACEHOLDERS
            .takeWhile { it.first != ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER }
            .forEach { (placeholder, _) -> check(script.countOccurrences(placeholder) == 1) }
        check(script.countOccurrences(STA_BATCH_ORIGIN_PLACEHOLDER) == 1)
        FLAG_PLACEHOLDERS
            .dropWhile { it.first != ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER }
            .take(1)
            .forEach { (placeholder, _) -> check(script.countOccurrences(placeholder) == 1) }
        val batchOrigin = URI(staBatchOrigin)
        require(
            !batchOrigin.isOpaque && batchOrigin.scheme == "https" && batchOrigin.host != null &&
                batchOrigin.userInfo == null && (batchOrigin.port == -1 || batchOrigin.port == 443) &&
                (batchOrigin.rawPath.isNullOrEmpty() || batchOrigin.rawPath == "/") &&
                batchOrigin.rawQuery == null && batchOrigin.rawFragment == null
        )
        FLAG_PLACEHOLDERS
            .dropWhile { it.first != VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER }
            .forEach { (placeholder, _) -> check(script.countOccurrences(placeholder) == 1) }
        var configured = script
            .replace(
                MODE_PLACEHOLDER,
                if (mode == MiniAppletBridgeMode.FUNCTIONAL) "true" else "false",
            )
            .replace(
                QA_DIAGNOSTICS_PLACEHOLDER,
                if (qaDiagnosticsEnabled) "true" else "false",
            )
        FLAG_PLACEHOLDERS.forEach { (placeholder, enabled) ->
            configured = configured.replace(
                placeholder,
                if (enabled(flags)) "true" else "false",
            )
        }
        configured = configured.replace(
            STA_BATCH_ORIGIN_PLACEHOLDER,
            JSONObject.quote(staBatchOrigin.removeSuffix("/")),
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
    private const val JCCM_REGISTRO_COMPATIBILITY_PLACEHOLDER =
        "__JFM_JCCM_REGISTRO_COMPATIBILITY_ENABLED__"
    private const val SEVILLA_ATSE_COMPATIBILITY_PLACEHOLDER =
        "__JFM_SEVILLA_ATSE_COMPATIBILITY_ENABLED__"
    private const val AIREF_COMPATIBILITY_PLACEHOLDER =
        "__JFM_AIREF_COMPATIBILITY_ENABLED__"
    private const val CDTI_COMPATIBILITY_PLACEHOLDER =
        "__JFM_CDTI_COMPATIBILITY_ENABLED__"
    private const val POLICIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_POLICIA_COMPATIBILITY_ENABLED__"
    private const val GRAN_CANARIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_GRAN_CANARIA_COMPATIBILITY_ENABLED__"
    private const val FUERTEVENTURA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_FUERTEVENTURA_COMPATIBILITY_ENABLED__"
    private const val CANARIAS_COMPATIBILITY_PLACEHOLDER =
        "__JFM_CANARIAS_COMPATIBILITY_ENABLED__"
    private const val MINECO_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MINECO_COMPATIBILITY_ENABLED__"
    private const val MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_MELILLA_BATCH_COMPATIBILITY_ENABLED__"
    private const val LUGO_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_LUGO_BATCH_COMPATIBILITY_ENABLED__"
    private const val CAIB_BATCH_COMPATIBILITY_PLACEHOLDER =
        "__JFM_CAIB_BATCH_COMPATIBILITY_ENABLED__"
    private const val STA_BATCH_ORIGIN_PLACEHOLDER = "__JFM_STA_BATCH_ORIGIN__"
    private const val ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_ISCIII_CERTIFICATE_SELECTION_ENABLED__"
    private const val VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER =
        "__JFM_VALENCIA_CERTIFICATE_SELECTION_ENABLED__"
    private const val XUNTA_GALICIA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_XUNTA_GALICIA_COMPATIBILITY_ENABLED__"
    private const val EUSKADI_CLIENT_AUTH_POST_PLACEHOLDER =
        "__JFM_EUSKADI_CLIENT_AUTH_POST_ENABLED__"
    private const val ACCEDA_COMPATIBILITY_PLACEHOLDER =
        "__JFM_ACCEDA_COMPATIBILITY_ENABLED__"
    private const val BADAJOZ_COMPATIBILITY_PLACEHOLDER =
        "__JFM_BADAJOZ_COMPATIBILITY_ENABLED__"

    private val FLAG_PLACEHOLDERS: List<Pair<String, (AfirmaShimCompatibilityFlags) -> Boolean>> =
        listOf(
            UGR_COMPATIBILITY_PLACEHOLDER to { it.ugr },
            CANTABRIA_COMPATIBILITY_PLACEHOLDER to { it.cantabria },
            JCCM_COMPATIBILITY_PLACEHOLDER to { it.jccm },
            JCCM_REGISTRO_COMPATIBILITY_PLACEHOLDER to { it.jccmRegistro },
            SEVILLA_ATSE_COMPATIBILITY_PLACEHOLDER to { it.sevillaAtse },
            AIREF_COMPATIBILITY_PLACEHOLDER to { it.airef },
            CDTI_COMPATIBILITY_PLACEHOLDER to { it.cdti },
            POLICIA_COMPATIBILITY_PLACEHOLDER to { it.policia },
            GRAN_CANARIA_COMPATIBILITY_PLACEHOLDER to { it.granCanaria },
            FUERTEVENTURA_COMPATIBILITY_PLACEHOLDER to { it.fuerteventura },
            CANARIAS_COMPATIBILITY_PLACEHOLDER to { it.canarias },
            MINECO_COMPATIBILITY_PLACEHOLDER to { it.mineco },
            MELILLA_BATCH_COMPATIBILITY_PLACEHOLDER to { it.melillaBatch },
            LUGO_BATCH_COMPATIBILITY_PLACEHOLDER to { it.lugoBatch },
            CAIB_BATCH_COMPATIBILITY_PLACEHOLDER to { it.caibBatch },
            ISCIII_CERTIFICATE_SELECTION_PLACEHOLDER to { it.isciiiCertificateSelection },
            VALENCIA_CERTIFICATE_SELECTION_PLACEHOLDER to { it.valenciaCertificateSelection },
            XUNTA_GALICIA_COMPATIBILITY_PLACEHOLDER to { it.xuntaGalicia },
            EUSKADI_CLIENT_AUTH_POST_PLACEHOLDER to { it.euskadiClientAuthPost },
            ACCEDA_COMPATIBILITY_PLACEHOLDER to { it.acceda },
            BADAJOZ_COMPATIBILITY_PLACEHOLDER to { it.badajoz },
        )
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
