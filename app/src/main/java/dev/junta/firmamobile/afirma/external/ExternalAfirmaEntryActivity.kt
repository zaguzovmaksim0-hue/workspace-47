package dev.junta.firmamobile.afirma.external

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.junta.firmamobile.JuntaFirmaApplication
import dev.junta.firmamobile.R

/** Exported trampoline for external `afirma://` links (ADR 0006). It never parses, signs or forwards the URI in
 * an Intent: it parks it in the in-process inbox and opens the app exactly like its launcher icon. MainActivity
 * admits the delivery only when it is resumed, focused, unlocked and no portal operation is active. */
class ExternalAfirmaEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawUri = ExternalAfirmaEnvelope.accept(intent?.action, intent?.dataString)
        if (savedInstanceState == null && rawUri != null) {
            val app = application as JuntaFirmaApplication
            if (app.externalAfirmaInbox.put(rawUri)) {
                // Same flags as the launcher: an existing app task is brought forward, not duplicated.
                packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    ?.let(::startActivity)
            } else {
                Toast.makeText(this, R.string.external_afirma_busy, Toast.LENGTH_LONG).show()
            }
        }
        finish() // Theme.NoDisplay requires finishing before onResume.
    }
}
