package dev.junta.firmamobile.network

import android.content.Context

internal object BuildVariantSecureTunnelRuntimeFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): SecureTunnelRuntime =
        DirectOnlyTunnelRuntime()
}
