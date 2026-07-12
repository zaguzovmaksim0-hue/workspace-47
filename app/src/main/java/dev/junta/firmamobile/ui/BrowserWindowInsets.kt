package dev.junta.firmamobile.ui

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.core.graphics.Insets as CoreInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import java.util.WeakHashMap

object BrowserWindowInsetsPolicy {
    @Composable
    fun current(): WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)

    fun install(root: View) {
        val initial = synchronized(initialPadding) {
            initialPadding.getOrPut(root) {
                CoreInsets.of(
                    root.paddingLeft,
                    root.paddingTop,
                    root.paddingRight,
                    root.paddingBottom,
                )
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, source ->
            val safe = source.getInsets(SAFE_INSET_TYPES)
            view.setPadding(
                initial.left + safe.left,
                initial.top + safe.top,
                initial.right + safe.right,
                initial.bottom + safe.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        if (root.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(root)
        } else {
            root.doOnAttach(ViewCompat::requestApplyInsets)
        }
    }

    private val initialPadding = WeakHashMap<View, CoreInsets>()
    private val SAFE_INSET_TYPES =
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
}
