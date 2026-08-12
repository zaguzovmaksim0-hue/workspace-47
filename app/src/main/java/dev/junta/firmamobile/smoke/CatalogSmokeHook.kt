package dev.junta.firmamobile.smoke

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import dev.junta.firmamobile.BuildConfig
import org.json.JSONObject

/**
 * QA-only shell bridge. The receiver exists only while MainActivity is in the foreground and the
 * sender must hold the platform DUMP permission (normally shell/system). It accepts identifiers,
 * never URLs, scripts, certificate material or signing payloads.
 */
internal class CatalogSmokeHook(
    private val activity: Activity,
    private val execute: (CatalogSmokeRequest) -> CatalogSmokeOutcome,
) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val outcome = execute(
                CatalogSmokeRequest(
                    runId = intent.getStringExtra(EXTRA_RUN_ID),
                    portalId = intent.getStringExtra(EXTRA_PORTAL_ID),
                    operation = intent.getStringExtra(EXTRA_OPERATION),
                ),
            )
            val result = outcome.toJson()
            resultCode = if (outcome.result.isOrderedBroadcastFailure()) {
                Activity.RESULT_CANCELED
            } else {
                Activity.RESULT_OK
            }
            resultData = result
            Log.i(LOG_TAG, result)
        }
    }

    fun start() {
        if (!BuildConfig.ALLOW_QA_PROFILES || registered) return
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION),
            Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    private fun CatalogSmokeOutcome.toJson(): String = JSONObject()
        .put("schemaVersion", 1)
        .put("runId", runId ?: JSONObject.NULL)
        .put("portalId", portalId?.value ?: JSONObject.NULL)
        .put("profileId", profileId?.value ?: JSONObject.NULL)
        .put("adapterId", adapterId ?: JSONObject.NULL)
        .put("entryUrl", entryUrl ?: JSONObject.NULL)
        .put("supportStatus", supportStatus ?: JSONObject.NULL)
        .put("result", result.name)
        .toString()

    companion object {
        const val ACTION = "dev.junta.firmamobile.action.CATALOG_SMOKE"
        const val EXTRA_RUN_ID = "runId"
        const val EXTRA_PORTAL_ID = "portalId"
        const val EXTRA_OPERATION = "operation"
        const val LOG_TAG = "JfmSiteSmoke"

    }
}

internal fun CatalogSmokeResultCode.isOrderedBroadcastFailure(): Boolean = when (this) {
    CatalogSmokeResultCode.INVALID_REQUEST,
    CatalogSmokeResultCode.UNKNOWN_PORTAL,
    CatalogSmokeResultCode.PROFILE_DISABLED,
    CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
    -> true

    CatalogSmokeResultCode.CATALOG_ONLY,
    CatalogSmokeResultCode.PROFILE_RESOLVED,
    CatalogSmokeResultCode.OPEN_REQUESTED,
    CatalogSmokeResultCode.WEBVIEW_ACTIVE,
    -> false
}
