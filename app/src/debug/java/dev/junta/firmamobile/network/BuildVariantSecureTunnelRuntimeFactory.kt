package dev.junta.firmamobile.network

import android.content.Context
import dev.junta.firmamobile.BuildConfig
import java.io.File

internal object BuildVariantSecureTunnelRuntimeFactory {
    fun create(context: Context): SecureTunnelRuntime = create(context.noBackupFilesDir)

    internal fun create(noBackupDirectory: File): SecureTunnelRuntime {
        val config = SecureTunnelPublicConfig(
            enabled = BuildConfig.ENABLE_WS024_QA_TUNNEL,
            relayHost = BuildConfig.WS024_QA_RELAY_HOST,
            relayPort = BuildConfig.WS024_QA_RELAY_PORT,
            relaySpkiPins = BuildConfig.WS024_QA_RELAY_SPKI_PINS
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet(),
        )
        val credentialProvider = if (config.enabled) {
            QaOneShotTunnelCredentialProvider(noBackupDirectory)
        } else {
            null
        }
        return SecureTunnelRuntimes.create(config, credentialProvider)
    }
}
