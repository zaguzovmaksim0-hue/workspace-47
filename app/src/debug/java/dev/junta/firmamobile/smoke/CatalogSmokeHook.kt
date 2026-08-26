package dev.junta.firmamobile.smoke

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * QA-only shell bridge. This class exists only in debug/QA source sets. The receiver exists only
 * while MainActivity is foreground and the sender must hold the platform DUMP permission.
 * Inputs are bounded identifiers and a closed operation enum; never URLs, scripts or credentials.
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
                    profileId = intent.getStringExtra(EXTRA_PROFILE_ID),
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
        if (registered) return
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

    companion object {
        const val ACTION = "dev.junta.firmamobile.action.CATALOG_SMOKE"
        const val EXTRA_RUN_ID = "runId"
        const val EXTRA_PORTAL_ID = "portalId"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_OPERATION = "operation"
        const val LOG_TAG = "JfmSiteSmoke"
    }
}

internal fun CatalogSmokeOutcome.toJson(): String = JSONObject()
    .put("schemaVersion", 2)
    .put("runId", runId ?: JSONObject.NULL)
    .put("portalId", portalId?.value ?: JSONObject.NULL)
    .put("profileId", profileId?.value ?: JSONObject.NULL)
    .put("adapterId", adapterId ?: JSONObject.NULL)
    .put("entryUrl", entryUrl ?: JSONObject.NULL)
    .put("supportStatus", supportStatus ?: JSONObject.NULL)
    .put("result", result.name)
    .put("runtime", runtime?.toJsonObject() ?: JSONObject.NULL)
    .toString()

private fun CatalogSmokeRuntimeSnapshot.toJsonObject(): JSONObject = JSONObject()
    .put("browserSessionBound", browserSessionBound)
    .put("webViewActive", webViewActive)
    .put("navigationEpoch", navigationEpoch)
    .put("currentHost", currentHost ?: JSONObject.NULL)
    .put("currentPath", currentPath ?: JSONObject.NULL)
    .put("currentUrlAllowed", currentUrlAllowed)
    .put("clientCertRequestObserved", clientCertRequestObserved)
    .put("clientCertAcceptedObserved", clientCertAcceptedObserved)
    .put("clientAuthConfirmationRequired", clientAuthConfirmationRequired)
    .put("certificateSelectionRequired", certificateSelectionRequired)
    .put("afirmaRequestObserved", afirmaRequestObserved)
    .put("autofirmaIntentObserved", autofirmaIntentObserved)
    .put("signingConfirmationRequired", signingConfirmationRequired)
    .put("signingStartedObserved", signingStartedObserved)
    .put("signingCompletedObserved", signingCompletedObserved)
    .put("signingFailedObserved", signingFailedObserved)
    .put("portalCallbackObserved", portalCallbackObserved)
    .put("renderProcessGone", renderProcessGone)
    .put("failureCode", failureCode ?: JSONObject.NULL)
    .put(
        "events",
        JSONArray().apply {
            events.forEach { event ->
                put(
                    JSONObject()
                        .put("sequence", event.sequence)
                        .put("code", event.code.name)
                        .put("navigationEpoch", event.navigationEpoch)
                        .put("host", event.host ?: JSONObject.NULL)
                        .put("path", event.path ?: JSONObject.NULL)
                        .put("detail", event.detail ?: JSONObject.NULL),
                )
            }
        },
    )

internal fun CatalogSmokeResultCode.isOrderedBroadcastFailure(): Boolean = when (this) {
    CatalogSmokeResultCode.INVALID_REQUEST,
    CatalogSmokeResultCode.UNKNOWN_PORTAL,
    CatalogSmokeResultCode.UNKNOWN_PROFILE,
    CatalogSmokeResultCode.AMBIGUOUS_PROFILE,
    CatalogSmokeResultCode.PROFILE_DISABLED,
    CatalogSmokeResultCode.WEBVIEW_NOT_ACTIVE,
    CatalogSmokeResultCode.RUN_NOT_ACTIVE,
    -> true

    CatalogSmokeResultCode.CATALOG_ONLY,
    CatalogSmokeResultCode.PROFILE_RESOLVED,
    CatalogSmokeResultCode.OPEN_REQUESTED,
    CatalogSmokeResultCode.WEBVIEW_ACTIVE,
    -> false
}
