# Explicit HTTP authentication

## Scope

Continue PR338 from `9da61e2977ce12224ff45a1850eef48aaf926e30`. The ordinary WebView did not override `onReceivedHttpAuthRequest`; Android's default action cancels the request. This slice adds an explicit username/password response to that real platform challenge, independently of HTML forms, client certificates and document signing.

The callback only supplies a host and realm, not a top-frame flag or complete requesting URL. This implementation therefore only admits a challenge whose DNS hostname matches the currently displayed public HTTPS/default-443 page. Cross-host resources, HTTP, invalid authorities and local/IP-literal entry points remain unsupported. The UI distinguishes server, page origin and the untrusted realm. A request can originate from a page resource; the UI does not falsely identify it as a proven top-level request.

## Ownership and lifetime

One request is bound to its original WebView, document generation, foreground Activity lifecycle and a two-minute monotonic lifetime. A second request cannot replace its prompt. Changing page, client, Activity lifecycle or detaching/destroying the view cancels pending authentication. A dialog observer cannot transfer an old confirmation to a new request. The platform callback receives at most one terminal response attempt, including a `proceed` exception.

No username/password is stored in the controller, preferences, WebViewDatabase, a saved-instance Bundle or diagnostics. The standard Android password input is masked and excluded from saved/autofill data; the dialog uses FLAG_SECURE. Clearing UI fields is not a claim that immutable JVM Strings have been securely erased. Chromium may retain active HTTP-auth session state after explicit confirmation; this slice does not promise server logout or erase its internal session cache.

The existing WebViewClient is preserved as the delegate for navigation, certificate challenges, SSL errors, safe browsing, form resubmission, renderer failure and resource interception. No personal certificate or signing operation is invoked by HTTP authentication. SSL verification is not bypassed. Downloads, popup/opener, WebAuthn and missing AutoFirma formats are not implemented by this change.

## Evidence and authoring

Primary Android API references consulted on 2026-10-02:
- https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedHttpAuthRequest(android.webkit.WebView,%20android.webkit.HttpAuthHandler,%20java.lang.String,%20java.lang.String)
- https://developer.android.com/reference/android/webkit/HttpAuthHandler
- https://developer.android.com/reference/android/webkit/WebView#setWebViewClient(android.webkit.WebViewClient)

Initial GPT-6.1 Sol Max code workers were attempted on an isolated alternative runtime after Chipupa returned 502. They produced no accepted code: requests failed with authentication/model-availability errors. Those attempts must not be described as successful implementation or review. After primary runtime connectivity recovered, two narrow GPT-6.1 Sol Max code-output tasks successfully authored HttpAuthPlatformCallbackTest and HttpAuthSslDelegationTest (three cases each). The integrator inspected and applied their exact source, then ran canonical checks; the agents did not execute tests. The production implementation and original tests were authored by the integrator. No upstream implementation was copied.

The focused JVM harness executes the real new scope/controller Kotlin classes with synthetic callbacks. It is supporting evidence, not a replacement for the repository's actual JUnit/Android/CI gate. New JUnit cases cover scope privacy, input bounds, expiry, reentrant observers, duplicate callbacks and one-time terminal responses. Delegation tests retain mTLS/navigation behavior. Android instrumentation uses synthetic callback adapters and local example-domain HTML; it does not authenticate a real account or send a real password to a server.

Publication must be followed by all six existing exact-head GitHub Actions checks. A tested source commit is not an installed APK. Physical update remains separate and requires the existing signer, unchanged application payload, current installed hash, non-foreground target and preserved UID/first-install time. Unrelated user worktrees, VPN, signing identities and Codex configuration are outside this change.
