package dev.junta.firmamobile.browser

import java.io.Reader
import java.net.URI
import org.json.JSONObject

internal data class AfirmaJavascriptShimConfiguration(
    val mode: MiniAppletBridgeMode,
    val qaDiagnosticsEnabled: Boolean,
    val flags: AfirmaShimCompatibilityFlags,
    val staBatchOrigin: String,
)

internal enum class AfirmaJavascriptShimFragment {
    BOOTSTRAP,
    CANTABRIA,
    JCCM,
    JCCM_REGISTRO,
    AIREF,
    CDTI,
    POLICIA,
    GRAN_CANARIA,
    HANDLERS,
    INSTALLATION,
}

/** Builds the complete script or throws; never returns a partial fallback. */
internal object AfirmaJavascriptShimAssembler {
    fun readAndAssemble(
        configuration: AfirmaJavascriptShimConfiguration,
        readResource: () -> Reader,
    ): String = readTemplates(configuration, listOf(readResource))

    fun readFragmentsAndAssemble(
        configuration: AfirmaJavascriptShimConfiguration,
        fragments: List<AfirmaJavascriptShimFragment> = AfirmaJavascriptShimFragment.entries,
        readResource: (AfirmaJavascriptShimFragment) -> Reader,
    ): String {
        check(fragments == AfirmaJavascriptShimFragment.entries) { "Invalid shim fragment manifest" }
        val selected = fragments.filter { fragment ->
            when (fragment) {
                AfirmaJavascriptShimFragment.CANTABRIA -> configuration.flags.cantabria
                AfirmaJavascriptShimFragment.JCCM -> configuration.flags.jccm
                AfirmaJavascriptShimFragment.JCCM_REGISTRO -> configuration.flags.jccmRegistro
                AfirmaJavascriptShimFragment.AIREF -> configuration.flags.airef
                AfirmaJavascriptShimFragment.CDTI -> configuration.flags.cdti
                AfirmaJavascriptShimFragment.POLICIA -> configuration.flags.policia
                AfirmaJavascriptShimFragment.GRAN_CANARIA -> configuration.flags.granCanaria
                else -> true
            }
        }
        return readTemplates(configuration, selected.map { fragment -> { readResource(fragment) } })
    }

    private fun readTemplates(
        configuration: AfirmaJavascriptShimConfiguration,
        readers: List<() -> Reader>,
    ): String {
        // Tokens only shrink when replaced except the origin. This conservative bound
        // permits every template whose configured output fits the unchanged final cap.
        val templateLimit = AfirmaJavascriptShim.MAX_SCRIPT_CHARS + PLACEHOLDERS.sumOf { it.length }
        val result = StringBuilder()
        readers.forEach { open ->
            open().use { reader ->
                val start = result.length
                val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                var count = reader.read(buffer, 0, minOf(buffer.size, templateLimit + 1 - result.length))
                while (count != -1) {
                    result.append(buffer, 0, count)
                    check(result.length <= templateLimit) { "Shim resources exceed template limit" }
                    count = reader.read(buffer, 0, minOf(buffer.size, templateLimit + 1 - result.length))
                }
                check(result.substring(start).isNotBlank()) { "Blank shim fragment" }
            }
        }
        val template = result.toString()
        return assemble(configuration, template)
    }

    /** Pure transformation; no Context, resource access, mutable cache or installation. */
    fun assemble(configuration: AfirmaJavascriptShimConfiguration, template: String): String {
        validateTemplate(template)
        var configured = template
        replacements(configuration).forEach { (placeholder, value) ->
            configured = configured.replace(placeholder, value)
        }
        check(!configured.contains("__JFM_")) { "Unresolved shim placeholder" }
        check(configured.isNotBlank() && configured.length <= AfirmaJavascriptShim.MAX_SCRIPT_CHARS)
        return configured
    }

    private fun validateTemplate(template: String) {
        var remainder = template
        PLACEHOLDERS.forEach { placeholder ->
            val first = template.indexOf(placeholder)
            check(first >= 0 && template.indexOf(placeholder, first + 1) == -1) {
                "Shim placeholder must occur exactly once: $placeholder"
            }
            remainder = remainder.replace(placeholder, "")
        }
        check(!remainder.contains("__JFM_")) { "Unexpected shim placeholder" }
    }

    private fun replacements(configuration: AfirmaJavascriptShimConfiguration): Map<String, String> {
        validateOrigin(configuration.staBatchOrigin)
        return buildMap {
            put(MODE_PLACEHOLDER, (configuration.mode == MiniAppletBridgeMode.FUNCTIONAL).toString())
            put(QA_DIAGNOSTICS_PLACEHOLDER, configuration.qaDiagnosticsEnabled.toString())
            FLAG_PLACEHOLDERS.forEach { (placeholder, enabled) ->
                put(placeholder, enabled(configuration.flags).toString())
            }
            put(STA_BATCH_ORIGIN_PLACEHOLDER, JSONObject.quote(configuration.staBatchOrigin.removeSuffix("/")))
        }
    }

    private fun validateOrigin(origin: String) {
        val uri = URI(origin)
        require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        require(uri.port == -1 || uri.port == HTTPS_PORT)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        require(uri.rawQuery == null && uri.rawFragment == null)
    }

    private const val HTTPS_PORT = 443

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

    private val PLACEHOLDERS = listOf(
        MODE_PLACEHOLDER,
        QA_DIAGNOSTICS_PLACEHOLDER,
        STA_BATCH_ORIGIN_PLACEHOLDER,
    ) + FLAG_PLACEHOLDERS.map { it.first }
}
