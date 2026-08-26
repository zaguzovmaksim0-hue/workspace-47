package dev.junta.firmamobile.control

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import dev.junta.firmamobile.smoke.toJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * QA/debug-only typed control ingress for device E2E. The receiver is lifecycle-bound and the
 * sender must hold android.permission.DUMP. Secrets are never accepted as Intent extras.
 */
internal class E2eControlHook(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val controller: E2eControlController,
) {
    private var registered = false

    private val actionReceiver = receiver()
    private val contentReceiver = receiver()

    private fun receiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION || !isOrderedBroadcast) return
            val parsed = E2eControlIntentParser.parse(intent)
            val pending = goAsync()
            scope.launch {
                try {
                    val outcome = if (parsed == null) {
                        controller.execute(
                            E2eControlRequest(
                                runId = intent.getStringExtra(EXTRA_RUN_ID),
                                command = null,
                            ),
                        )
                    } else {
                        controller.execute(parsed)
                    }
                    val json = outcome.toJson()
                    pending.setResultCode(
                        if (outcome.success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                    )
                    pending.setResultData(json)
                    Log.i(LOG_TAG, json)
                } catch (_: Exception) {
                    pending.setResultCode(Activity.RESULT_CANCELED)
                    pending.setResultData(internalErrorJson())
                } finally {
                    pending.finish()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            activity,
            actionReceiver,
            e2eControlActionFilter(),
            Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            ContextCompat.registerReceiver(
                activity,
                contentReceiver,
                e2eControlContentFilter(),
                Manifest.permission.DUMP,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (error: Exception) {
            runCatching { activity.unregisterReceiver(actionReceiver) }
            throw error
        }
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { activity.unregisterReceiver(contentReceiver) }
        runCatching { activity.unregisterReceiver(actionReceiver) }
        registered = false
    }

    companion object {
        const val ACTION = "dev.junta.firmamobile.action.E2E_CONTROL"
        const val EXTRA_RUN_ID = "runId"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_PORTAL_ID = "portalId"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_SECRET_HANDLE = "secretHandle"
        const val LOG_TAG = "JfmE2eControl"
    }
}


internal fun e2eControlActionFilter(): IntentFilter = IntentFilter(E2eControlHook.ACTION)

internal fun e2eControlContentFilter(): IntentFilter = IntentFilter(E2eControlHook.ACTION).apply {
    addDataScheme("content")
}

private fun internalErrorJson(): String = JSONObject()
    .put("schemaVersion", 3)
    .put("runId", JSONObject.NULL)
    .put("command", JSONObject.NULL)
    .put("result", "INTERNAL_ERROR")
    .put("success", false)
    .put("certificate", JSONObject.NULL)
    .put("signingState", "UNKNOWN")
    .put("portal", JSONObject.NULL)
    .toString()

internal object E2eControlIntentParser {
    private val ALLOWED_EXTRAS = setOf(
        E2eControlHook.EXTRA_RUN_ID,
        E2eControlHook.EXTRA_COMMAND,
        E2eControlHook.EXTRA_PORTAL_ID,
        E2eControlHook.EXTRA_PROFILE_ID,
        E2eControlHook.EXTRA_SECRET_HANDLE,
    )

    fun parse(intent: Intent): E2eControlRequest? {
        if (intent.action != E2eControlHook.ACTION) return null
        val keys = intent.extras?.keySet().orEmpty()
        if (keys.any { it !in ALLOWED_EXTRAS }) return null
        return E2eControlRequest(
            runId = intent.getStringExtra(E2eControlHook.EXTRA_RUN_ID),
            command = intent.getStringExtra(E2eControlHook.EXTRA_COMMAND),
            portalId = intent.getStringExtra(E2eControlHook.EXTRA_PORTAL_ID),
            profileId = intent.getStringExtra(E2eControlHook.EXTRA_PROFILE_ID),
            secretHandle = intent.getStringExtra(E2eControlHook.EXTRA_SECRET_HANDLE),
            certificateUri = intent.data,
            intentFlags = intent.flags,
        )
    }
}

internal fun E2eControlOutcome.toJson(): String = JSONObject()
    .put("schemaVersion", 3)
    .put("runId", runId ?: JSONObject.NULL)
    .put("command", command ?: JSONObject.NULL)
    .put("result", result)
    .put("success", success)
    .put(
        "certificate",
        JSONObject()
            .put("state", certificate.state)
            .put("error", certificate.error ?: JSONObject.NULL),
    )
    .put("signingState", signingState)
    .put("portal", portal?.toJsonObject() ?: JSONObject.NULL)
    .toString()
