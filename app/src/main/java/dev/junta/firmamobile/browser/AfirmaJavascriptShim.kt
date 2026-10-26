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
            AfirmaJavascriptShimFragment.JCCM -> R.raw.afirma_shim_jccm
            AfirmaJavascriptShimFragment.JCCM_REGISTRO -> R.raw.afirma_shim_jccm_registro
            AfirmaJavascriptShimFragment.AIREF -> R.raw.afirma_shim_airef
            AfirmaJavascriptShimFragment.CDTI -> R.raw.afirma_shim_cdti
            AfirmaJavascriptShimFragment.POLICIA -> R.raw.afirma_shim_policia
            AfirmaJavascriptShimFragment.GRAN_CANARIA -> R.raw.afirma_shim_gran_canaria
            AfirmaJavascriptShimFragment.FUERTEVENTURA -> R.raw.afirma_shim_fuerteventura
            AfirmaJavascriptShimFragment.MINECO -> R.raw.afirma_shim_mineco
            AfirmaJavascriptShimFragment.CANARIAS -> R.raw.afirma_shim_canarias
            AfirmaJavascriptShimFragment.XUNTA -> R.raw.afirma_shim_xunta
            AfirmaJavascriptShimFragment.ACCEDA -> R.raw.afirma_shim_acceda
            AfirmaJavascriptShimFragment.SEVILLA_ATSE -> R.raw.afirma_shim_sevilla_atse
            AfirmaJavascriptShimFragment.UGR -> R.raw.afirma_shim_ugr
            AfirmaJavascriptShimFragment.BADAJOZ -> R.raw.afirma_shim_badajoz
            AfirmaJavascriptShimFragment.HANDLERS -> R.raw.afirma_shim_handlers
            AfirmaJavascriptShimFragment.MELILLA_BATCH -> R.raw.afirma_shim_melilla_batch
            AfirmaJavascriptShimFragment.LUGO_BATCH -> R.raw.afirma_shim_lugo_batch
            AfirmaJavascriptShimFragment.BATCH_RESULTS -> R.raw.afirma_shim_batch_results
            AfirmaJavascriptShimFragment.INSTALLATION -> R.raw.afirma_shim_installation
        }
        context.resources.openRawResource(resourceId).bufferedReader(Charsets.UTF_8)
    }
}

enum class MiniAppletBridgeMode {
    FUNCTIONAL,
    OBSERVATION,
}
