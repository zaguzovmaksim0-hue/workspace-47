package dev.junta.firmamobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.junta.firmamobile.R
import dev.junta.firmamobile.signing.PdfPreviewLimits
import dev.junta.firmamobile.signing.PdfPreviewSource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val previewRenderer = Semaphore(1)
private class PreviewImage(val bitmap: Bitmap, val pageCount: Int)

@Composable
internal fun pdfPreview(source: PdfPreviewSource?) {
    if (source == null) {
        Text(stringResource(R.string.pdf_preview_unavailable), Modifier.testTag("pdf-preview-unavailable"))
        return
    }
    val context = LocalContext.current
    var requested by remember(source) { mutableStateOf(false) }
    var image by remember(source) { mutableStateOf<PreviewImage?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    DisposableEffect(source) {
        onDispose { image?.bitmap?.recycle(); image = null }
    }
    if (requested) {
        LaunchedEffect(source) {
            try {
                renderPreview(context, source) { image = it }
            } catch (_: TimeoutCancellationException) {
                failed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        val rendered = image
        when {
            failed -> Text(stringResource(R.string.pdf_preview_failed), Modifier.testTag("pdf-preview-failed"))
            rendered == null -> Text(stringResource(R.string.pdf_preview_loading))
            else -> {
                Text(stringResource(R.string.pdf_preview_pages, rendered.pageCount))
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.pdf_preview_description),
                    modifier = Modifier.fillMaxWidth().testTag("pdf-preview-image"),
                )
            }
        }
    } else {
        TextButton(onClick = { requested = true }, modifier = Modifier.testTag("pdf-preview-show")) {
            Text(stringResource(R.string.pdf_preview_show))
        }
    }
}

private suspend fun renderPreview(context: Context, source: PdfPreviewSource, publish: (PreviewImage) -> Unit) {
    var bitmap: Bitmap? = null
    var published = false
    try {
        val pageCount = withTimeout(PdfPreviewLimits.TIMEOUT_MS) {
            previewRenderer.withPermit {
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    val file = File.createTempFile("pdf-preview-", ".tmp", context.cacheDir)
                    try {
                        source.withBytes { bytes -> file.outputStream().use { it.write(bytes) } }
                        currentCoroutineContext().ensureActive()
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            // Unlink before parsing: only the seekable descriptor keeps the private PDF alive.
                            check(file.delete())
                            PdfRenderer(descriptor).use { renderer ->
                                require(renderer.pageCount > 0)
                                currentCoroutineContext().ensureActive()
                                renderer.openPage(0).use { page ->
                                    val (width, height) = PdfPreviewLimits.dimensions(page.width, page.height)
                                    val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    bitmap = target
                                    target.eraseColor(Color.WHITE)
                                    // shortcut: native calls block cancellation; use a service for hard deadlines.
                                    page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    currentCoroutineContext().ensureActive()
                                }
                                renderer.pageCount
                            }
                        }
                    } finally { file.delete() }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        publish(PreviewImage(checkNotNull(bitmap), pageCount))
        published = true
    } finally { if (!published) bitmap?.recycle() }
}
