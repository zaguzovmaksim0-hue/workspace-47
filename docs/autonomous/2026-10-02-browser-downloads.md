# Explicit browser document download and save

## Scope

Continue PR338 from verified installed `8ae0a332749fce19bf296b86577729f1c1d38e83`. Previously no DownloadListener was installed. This slice offers a bounded download of a direct public HTTPS attachment whose actual main-frame request was observed as GET. It stages the file privately, then lets the user select a new destination using Android ACTION_CREATE_DOCUMENT.

The callback does not supply an original response stream or request method. The application therefore records request metadata without taking over the browser's request or response. Only an exact recent main-frame GET can produce an offer; an observed POST, unknown method, redirected/unobserved final URL, blob/data address, expired event or another page cannot be converted into a GET silently. This is intentionally not a claim of complete browser download support.

The confirmation explains that the downloader makes a NEW GET. The original URL might already have been consumed by WebView; one-use URLs may fail. No original form, POST body or signature is replayed. Failure does not cause an automatic retry. A new provider destination after a save failure only recopies the existing private staged file, never issues another GET.

## Session and transport boundaries

Only cookies retrieved for the same origin as the visible page may be included in the confirmed request. A different target origin gets no source-page cookies. No HTTP-auth password, Authorization header, Referer, client certificate or private key is imported into the downloader. Servers requiring mutual TLS or separate HTTP authentication may reject this independent request; this limitation must be shown rather than bypassed.

The downloader uses verified HTTPS, public DNS-result validation, no redirects/auth negotiation/cookie jar/proxy, a fresh connection pool, a two-minute call deadline, and a one-shot network interceptor. The interceptor also prevents OkHttp's possible follow-up after `503 Retry-After:0`; merely disabling connection retries is insufficient. Response bodies are streamed with a 32 MiB decompressed-byte budget, not buffered in full memory. An HTML login response is not silently stored as a PDF. A received content-length mismatch or interrupted transfer is a failure, not a saved document.

The original callback creates only an in-memory random ticket and an explicit non-exported activity Intent. URL, session cookie and bytes do not enter the Intent extras or saved-instance data. The ticket expires and is consumed once. The user starts the network operation explicitly; cancellation before that point performs no GET. The activity participates in MainActivity's existing bounded external-return lease, also when the source is a genuine child window. Rotation uses a retained ViewModel; process-death recovery does not reconstruct or replay a private URL.

## Storage and UI

Temporary data stays under the app cache's `browser-documents` directory. Cancellation/error deletes the owned staging file; a completed document remains available until saved or closed. Recoverable old `document-*.part` files are pruned only in this private directory. No unrelated files are removed.

The user chooses a new document through ACTION_CREATE_DOCUMENT; no broad storage permission is added. Export streams the same bytes to the returned content URI and compares byte count and SHA-256 against the staging receipt. Saving means the selected provider accepted the stream; cloud synchronization beyond the provider is not guaranteed. If copying fails, only the newly-created partial destination is eligible for cleanup, and the local stage can be saved to another location without re-downloading. File names are bounded advisory names, not paths; controls, bidi formatting, reserved paths and separators are neutralized. No file is opened or executed automatically.

The secure activity displays the sanitized filename, target origin, current state and received bytes. Indeterminate progress does not invent a percentage. It distinguishes download, destination selection, copying, completed save, expired request and provider save failure. It reports unsupported download types instead of silently handing cookies to another app.

## Authorship and verification

Three initial GPT-6.1 Sol Max implementation-output requests did not return source within their bounds; at least one reported workspace-context loading failures. They are not counted as successful implementation. A smaller follow-up using the project context successfully authored `DownloadRequestOrderingTest` (three cases), applied unchanged. The integrator wrote the production implementation and other tests. Author output and hashes remain in the task state; no agent is claimed to have executed the tests.

Focused pure-JVM tests exercise names, byte limits/digest, exact request evidence, URL/session scope and ticket consumption. A local trusted TLS server verifies real GET framing, cookie scope, no redirects/retries/auth negotiation, cancellation cleanup, and rejection of untrusted TLS. Provider tests exercise actual output streams and save recovery without another network request. Instrumentation covers an actual Chromium attachment callback, private ticket activity, explicit confirmation and local-save UI. Synthetic fixtures use .example domains, local HTML and made-up cookie values only; no real account, personal certificate or government document is accessed.

Canonical acceptance remains all six GitHub Actions checks on the exact pushed head, followed separately by the existing in-place/same-signer/unchanged-payload/old-installed-hash/non-foreground/UID/first-install verification before replacing the phone APK. Earlier successful builds are not evidence for this candidate.

## Primary references checked 2026-10-02

- DownloadListener: https://developer.android.com/reference/android/webkit/DownloadListener
- Request method/main-frame evidence: https://developer.android.com/reference/android/webkit/WebResourceRequest
- System document creation: https://developer.android.com/training/data-storage/shared/documents-files

## Remaining limits

Direct observed main-frame HTTPS GET only, default/443 public host, up to 32 MiB. Blob/data URLs, downloads originating in iframes, unobserved redirects, original POST response capture, mTLS/HTTP-authenticated downloads, automatic retries, persistent background/service recovery, WebAuthn, missing AutoFirma formats and guaranteed cloud-provider durability are not implemented. A completed save is not proof that a government service accepted or registered a document. Existing browser/certificate/signature security and WARP/Codex settings are not relaxed by this feature.
