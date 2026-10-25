package dev.junta.firmamobile.browser

import android.content.Context
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R

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
    ): String = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(
        AfirmaJavascriptShimConfiguration(mode, qaDiagnosticsEnabled, flags, staBatchOrigin),
    ) { fragment ->
        val resourceId = when (fragment) {
            AfirmaJavascriptShimFragment.BOOTSTRAP -> R.raw.afirma_shim_bootstrap
            AfirmaJavascriptShimFragment.CANTABRIA -> R.raw.afirma_shim_cantabria
            AfirmaJavascriptShimFragment.HANDLERS -> R.raw.afirma_shim_handlers
            AfirmaJavascriptShimFragment.INSTALLATION -> R.raw.afirma_shim_installation
        }
        context.resources.openRawResource(resourceId).bufferedReader(Charsets.UTF_8)
    }
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
