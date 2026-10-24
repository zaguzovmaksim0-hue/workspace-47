package dev.junta.firmamobile.ui.offlineverification

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Only transient SAF access. No persistable grant is taken and no URI is saved. */
internal class LocalOfflineOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

internal class OfflineVerificationPickers(val cms: () -> Unit, val original: () -> Unit)

@Composable
internal fun rememberOfflineVerificationPickers(controller: OfflineVerificationController): OfflineVerificationPickers =
    OfflineVerificationPickers(
        cms = rememberOfflinePicker(controller, OfflineInputKind.CMS),
        original = rememberOfflinePicker(controller, OfflineInputKind.ORIGINAL),
    )

@Composable
private fun rememberOfflinePicker(controller: OfflineVerificationController, kind: OfflineInputKind): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    // Only a generation ticket; private URIs/bytes never enter Compose saved state.
    var ticket by remember(controller, kind) { mutableStateOf<OfflineSelection?>(null) }
    val launcher = rememberLauncherForActivityResult(LocalOfflineOpenDocument()) { uri ->
        ticket?.let { current ->
            controller.completeSelection(current, uri?.let { SafOfflineDocument(resolver, it) })
        }
        ticket = null
    }
    return {
        controller.beginSelection(kind)?.let { current ->
            ticket = current
            try {
                // Providers do not consistently label CMS files; the verifier rejects other formats.
                launcher.launch(arrayOf("*/*"))
            } catch (_: RuntimeException) {
                ticket = null
                controller.selectionFailed(current)
            }
        }
    }
}
