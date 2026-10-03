package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.browser.PublicBrowserAddress
import java.net.URI

/** Transient address draft, not a favorite, history item or trust entry.
 * Keep it outside a lazy list item, but never in a saved-state bundle. */
internal class CatalogAddressInput private constructor(
    val text: String,
    val error: Error?,
    val opening: Boolean,
) {
    enum class Error { EMPTY, INVALID, TOO_LONG, CLIPBOARD_EMPTY }

    val destination: URI?
        get() = if (opening || error == Error.TOO_LONG || error == Error.CLIPBOARD_EMPTY) null else parse(text)
    val canSubmit: Boolean get() = !opening && text.isNotBlank() && error != Error.TOO_LONG && error != Error.CLIPBOARD_EMPTY
    val hostPreview: String? get() = destination?.host

    fun edit(value: String): CatalogAddressInput = when {
        opening -> this
        value.length > MAX_INPUT_CHARS -> CatalogAddressInput(text, Error.TOO_LONG, false)
        else -> CatalogAddressInput(value, null, false)
    }

    fun paste(value: CharSequence?): CatalogAddressInput {
        if (opening) return this
        if (value == null) return CatalogAddressInput(text, Error.CLIPBOARD_EMPTY, false)
        // Never turn the first part of a long clipboard value into a different URL.
        if (value.length > MAX_INPUT_CHARS) return CatalogAddressInput(text, Error.TOO_LONG, false)
        if (value.isBlank()) return CatalogAddressInput(text, Error.CLIPBOARD_EMPTY, false)
        val draft = edit(value.toString().trim())
        return if (draft.destination == null) CatalogAddressInput(draft.text, errorFor(draft.text), false) else draft
    }

    fun clear(): CatalogAddressInput = if (opening) this else empty()

    /** A button and IME action share this one-shot transition. No URL is logged
     * or stored; the owning screen immediately leaves after accepting the URI. */
    fun submit(): Submission {
        if (opening) return Submission(this, null)
        val target = destination
        if (target == null) return Submission(CatalogAddressInput(text, error ?: errorFor(text), false), null)
        return Submission(CatalogAddressInput(text, null, true), target)
    }

    override fun toString() = "CatalogAddressInput(hasText=${text.isNotEmpty()}, error=$error, opening=$opening)"

    class Submission(val state: CatalogAddressInput, val destination: URI?) {
        override fun toString() = "CatalogAddressSubmission(accepted=${destination != null})"
    }

    companion object {
        const val MAX_INPUT_CHARS = 8192
        private val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
        fun empty() = CatalogAddressInput("", null, false)
        private fun candidate(raw: String): String {
            val text = raw.trim()
            // Do not upgrade an explicit http:// URL, decode query tokens or
            // treat pasted text as a search query. Bare public hosts use HTTPS.
            return if (explicitScheme.containsMatchIn(text)) text else "https://$text"
        }
        private fun parse(raw: String): URI? {
            if (raw.isBlank() || raw.length > MAX_INPUT_CHARS) return null
            val parsed = PublicBrowserAddress.parse(candidate(raw)) ?: return null
            // MainActivity rechecks the ASCII request form. Do not consume an
            // input that would exceed its budget only after Unicode encoding.
            return parsed.takeIf { PublicBrowserAddress.parse(it.toASCIIString()) != null }
        }
        private fun errorFor(raw: String) = when {
            raw.isBlank() -> Error.EMPTY
            raw.length > MAX_INPUT_CHARS || candidate(raw).length > MAX_INPUT_CHARS -> Error.TOO_LONG
            else -> Error.INVALID
        }
    }
}
