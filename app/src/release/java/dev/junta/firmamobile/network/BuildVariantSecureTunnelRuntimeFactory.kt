package dev.junta.firmamobile.network

import android.content.Context
import java.io.File

internal object BuildVariantSecureTunnelRuntimeFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): SecureTunnelRuntime =
        DirectOnlyTunnelRuntime()

    /** Same test seam as Debug/QA; release never reads a credential directory. */
    internal fun create(@Suppress("UNUSED_PARAMETER") noBackupDirectory: File): SecureTunnelRuntime =
        DirectOnlyTunnelRuntime()
}
