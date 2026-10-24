package dev.junta.firmamobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.junta.firmamobile.R
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.signing.LocalFileSigningController
import dev.junta.firmamobile.signing.LocalFileSigningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun localFileSigningEntry(unlocked: Boolean) {
    (LocalContext.current.applicationContext as? dev.junta.firmamobile.JuntaFirmaApplication)?.let {
        localFileSigningAction(it.certificateSession, unlocked)
    }
}

@Composable
private fun localFileSigningAction(session: CertificateSession, unlocked: Boolean) {
    val context = LocalContext.current
    val controller = remember(session) { LocalFileSigningController(session) }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var openToken by remember { mutableStateOf<Long?>(null) }
    var saveToken by remember { mutableStateOf<Long?>(null) }
    val unnamed = stringResource(R.string.local_signing_unnamed)
    val picker = rememberLauncherForActivityResult(LocalSigningOpenDocument) { uri ->
        val token = openToken
        openToken = null
        if (uri == null || token == null) {
            controller.close()
            visible = false
        } else {
            scope.launch(Dispatchers.IO) {
                loadLocalSigningFile(controller, token, context, uri, unnamed)
            }
        }
    }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pkcs7-signature"),
    ) { uri ->
        val token = saveToken
        saveToken = null
        if (uri == null || token == null) {
            controller.close()
            visible = false
        } else {
            scope.launch(Dispatchers.IO) {
                saveLocalSigningFile(controller, token, context, uri)
            }
        }
    }
    localFileSigningLifetime(controller, unlocked) { visible = false }
    val choose = {
        visible = true
        val token = controller.begin()
        launchLocalSigningPicker(controller, token, { openToken = it }) { picker.launch(arrayOf("*/*")) }
        Unit
    }
    localFileSigningButton(unlocked, openToken != null || saveToken != null, choose)
    if (visible) {
        localFileSigningDialog(
            state = state,
            pickerPending = openToken != null || saveToken != null,
            onConfirm = { displayed ->
                confirmLocalFileSigning(displayed, controller, scope) { ready ->
                    launchLocalSigningPicker(controller, ready.token, { saveToken = it }) {
                        saver.launch("${ready.name}.csig")
                    }
                }
            },
            onChoose = choose,
            onClose = { controller.close(); visible = false },
        )
    }
}

@Composable
private fun localFileSigningDialog(
    state: LocalFileSigningState,
    pickerPending: Boolean,
    onConfirm: (LocalFileSigningState) -> Unit,
    onChoose: () -> Unit,
    onClose: () -> Unit,
) {
    val armed = rememberConsentArmed(state)
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.local_signing_title)) },
        text = {
            FilterObscuredTouches()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.local_signing_scope))
                localFileSigningDetails(state)
                if (!pickerPending) {
                    TextButton(onClick = onChoose) { Text(stringResource(R.string.local_signing_choose)) }
                }
            }
        },
        confirmButton = {
            if (state is LocalFileSigningState.Review || state is LocalFileSigningState.Ready) {
                TextButton(onClick = { onConfirm(state) }, enabled = !pickerPending &&
                    (state !is LocalFileSigningState.Review || armed)) {
                    Text(stringResource(if (state is LocalFileSigningState.Review) {
                        R.string.signing_confirm
                    } else R.string.local_signing_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun localFileSigningDetails(state: LocalFileSigningState) {
    when (state) {
        is LocalFileSigningState.Review -> {
            Text(stringResource(R.string.local_signing_name, state.name))
            Text(stringResource(R.string.signing_document_bytes, state.document.byteCount))
            Text(stringResource(R.string.signing_document_sha256, state.document.sha256ForDisplay().orEmpty()))
            Text(stringResource(R.string.signing_certificate, state.certificateOwner))
        }
        else -> Text(stringResource(when (state) {
            LocalFileSigningState.Loading -> R.string.local_signing_loading
            LocalFileSigningState.Signing -> R.string.local_signing_signing
            is LocalFileSigningState.Ready -> R.string.local_signing_ready
            LocalFileSigningState.Saving -> R.string.local_signing_saving
            LocalFileSigningState.Saved -> R.string.local_signing_saved
            else -> R.string.local_signing_failed
        }))
    }
}

@Composable
private fun localFileSigningLifetime(controller: LocalFileSigningController, unlocked: Boolean, onLock: () -> Unit) {
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(unlocked) {
        if (!unlocked) { controller.close(); onLock() }
    }
}

@Composable
private fun localFileSigningButton(unlocked: Boolean, pickerPending: Boolean, onOpen: () -> Unit) {
    TextButton(enabled = unlocked && !pickerPending, onClick = onOpen) {
        Text(stringResource(R.string.local_signing_title))
    }
    if (!unlocked) Text(stringResource(R.string.local_signing_unlock))
}

private fun confirmLocalFileSigning(
    displayed: LocalFileSigningState,
    controller: LocalFileSigningController,
    scope: kotlinx.coroutines.CoroutineScope,
    save: (LocalFileSigningState.Ready) -> Unit,
) {
    if (controller.state.value !== displayed) return
    when (val current = displayed) {
        is LocalFileSigningState.Review -> scope.launch(Dispatchers.IO) { controller.sign(current.token) }
        is LocalFileSigningState.Ready -> save(current)
        else -> Unit
    }
}

private fun launchLocalSigningPicker(
    controller: LocalFileSigningController,
    token: Long,
    setPending: (Long?) -> Unit,
    launch: () -> Unit,
) {
    setPending(token)
    runCatching(launch).onFailure { setPending(null); controller.fail(token) }
}
