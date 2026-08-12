package dev.junta.firmamobile.ui

import android.net.Uri
import java.net.IDN
import java.util.Locale

internal const val BROWSER_TOOLBAR_TAG = "browser_toolbar"
internal const val BROWSER_ADDRESS_LABEL_TAG = "browser_address_label"
internal const val BROWSER_BOTTOM_BAR_TAG = "browser_bottom_bar"
internal const val BROWSER_CONTENT_TAG = "browser_content"

object BrowserAddressPresentation {
    fun hostOf(url: String): String = runCatching {
        val uri = Uri.parse(url)
        require(!uri.isOpaque)
        require(uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true))
        require(uri.encodedUserInfo == null)
        val rawHost = uri.host?.takeIf { it.isNotBlank() } ?: return INVALID_ADDRESS
        IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrDefault(INVALID_ADDRESS)

    private const val HTTPS_SCHEME = "https"
    private const val INVALID_ADDRESS = "dirección no disponible"
}
