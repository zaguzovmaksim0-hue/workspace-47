package dev.junta.firmamobile.browser.download

import dev.junta.firmamobile.browser.PublicBrowserAddress
import java.net.URI
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Reads the browser's current cookies for one request address. */
internal fun interface CookieLookup { fun cookieFor(url: String): String? }

/** Never a data class: URLs, cookies and form fields must not appear in
 * generated toString output, Intent extras, saved state or diagnostics. */
internal class DocumentDownloadPlan private constructor(
    /** Null for a document that the page already holds (blob: or data:). */
    val url: URI?,
    val sourceOrigin: String,
    val targetOrigin: String,
    val fileName: String,
    val mimeType: String,
    val lengthHint: Long,
    val userAgent: String,
    internal val referrer: URI,
    internal val cookies: CookieLookup?,
    internal val form: FormSubmission?,
    internal val inline: StagedDocument?,
    internal val clientIdentity: ClientTlsIdentity?,
) {
    /** Same registrable domain (sede.example.es and www.example.es), as browsers scope cookies. */
    val sameSite: Boolean get() = site(sourceOrigin.removePrefix("https://")) == site(targetOrigin.removePrefix("https://"))
    val sendsSession: Boolean get() = cookies != null && sameSite
    val replaysForm: Boolean get() = form != null
    val isInline: Boolean get() = inline != null

    /** Releases the form body and any document already staged for this offer. */
    fun discard() { form?.close(); inline?.close() }

    companion object {
        const val MAX_BYTES = 32L * 1024 * 1024

        /** Takes ownership of [form]; it is discarded when no plan is returned. */
        fun create(page: String, url: String, proposedName: String?, mimeType: String?, lengthHint: Long,
            userAgent: String?, cookies: CookieLookup? = null, form: FormSubmission? = null,
            clientIdentity: ClientTlsIdentity? = null): DocumentDownloadPlan? {
            val source = PublicBrowserAddress.parse(page.substringBefore('#'))
            // A fragment has no HTTP meaning; it is dropped, never sent.
            val target = PublicBrowserAddress.parse(url.substringBefore('#'))
            val ua = agent(userAgent)
            if (source == null || target == null || ua == null || lengthHint < -1 || lengthHint > MAX_BYTES) {
                form?.close(); return null
            }
            return DocumentDownloadPlan(target, origin(source), origin(target), DownloadFileName.safe(proposedName),
                mime(mimeType), lengthHint, ua, source, cookies, form, null,
                clientIdentity?.takeIf { it.appliesTo(target.host) })
        }

        /** Takes ownership of [staged]; it is deleted when no plan is returned. */
        fun inline(page: String, staged: StagedDocument, proposedName: String?, mimeType: String?, userAgent: String?): DocumentDownloadPlan? {
            val source = PublicBrowserAddress.parse(page.substringBefore('#'))
            val ua = agent(userAgent)
            if (source == null || ua == null || staged.bytes > MAX_BYTES) { staged.close(); return null }
            return DocumentDownloadPlan(null, origin(source), origin(source), DownloadFileName.safe(proposedName),
                mime(mimeType), staged.bytes, ua, source, null, null, staged, null)
        }

        fun site(host: String): String {
            val lower = host.lowercase(java.util.Locale.ROOT)
            return runCatching { "https://$lower/".toHttpUrl().topPrivateDomain() }.getOrNull() ?: lower
        }
        private fun agent(value: String?) = value.orEmpty().takeIf { ua -> ua.length <= 2048 && ua.all { it.code in 0x20..0x7e } }
        private fun mime(value: String?) = value?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
            ?.takeIf { it.length <= 127 && MIME.matches(it) } ?: "application/octet-stream"
        private val MIME = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
        private fun origin(uri: URI) = "https://" + uri.host.lowercase(java.util.Locale.ROOT)
    }
}

/** One short-lived in-process ticket: no URL/cookie is placed in an Intent.
 * Process death or a rejected launch discards the offer instead of replaying it. */
internal class DownloadTicketStore(private val nowNanos: () -> Long = System::nanoTime) {
    private class Slot(val token: String, val plan: DocumentDownloadPlan, val born: Long)
    private var slot: Slot? = null
    /** On success the store owns [plan] until it is taken; otherwise the caller still does. */
    @Synchronized fun offer(plan: DocumentDownloadPlan): String? {
        expire()
        if (slot != null) return null
        return UUID.randomUUID().toString().also { slot = Slot(it, plan, nowNanos()) }
    }
    @Synchronized fun take(token: String): DocumentDownloadPlan? {
        expire()
        val value = slot?.takeIf { it.token == token } ?: return null
        slot = null
        return value.plan
    }
    @Synchronized fun revoke(token: String) {
        val value = slot?.takeIf { it.token == token } ?: return
        slot = null; value.plan.discard()
    }
    private fun expire() {
        val value = slot ?: return
        val age = nowNanos() - value.born
        if (age < 0 || age >= 120_000_000_000L) { slot = null; value.plan.discard() }
    }
}

internal object DocumentDownloadTickets { val store = DownloadTicketStore() }
