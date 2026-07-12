# Junta Firma Mobile: Trusted WebView and AutoFirma Interception Plan

> **Status:** active Phase 3 plan. The target runtime is the POCO F6 Pro on
> Android 16; the pending HyperOS first-install confirmation for the separate
> instrumentation APK remains a device-QA limitation, not a desktop task.

**Goal:** Open the Junta sign-in portal inside a hardened, state-preserving
WebView; keep navigation and messages inside exact Junta origins; intercept
AutoFirma/Play Store fallbacks without launching them; and expose only a small
origin-scoped WebMessage path for the later native signing coordinator.

**Architecture:** Pure policy classes decide origins, callback URLs, AutoFirma
URI parsing, and navigation before Android side effects occur. A dedicated
`JuntaWebViewClient` applies those decisions. `TrustedJuntaWebView` owns secure
settings, cookies, state restore, and the WebKit listener/document-start shim.
Compose owns browser chrome and user-visible closed errors. Raw protocol values
remain memory-only; diagnostics receive only typed metadata, lengths, and short
hashes.

## Task 1: Exact origins, bounded AutoFirma parser, and navigation policy

1. RED-test exact HTTPS hosts, default port only, phishing/punycode/userinfo,
   callback SSRF cases, percent decoding exactly once, duplicate critical
   parameters, missing sign fields, unknown operations, and URI bounds.
2. RED-test allowed internal navigation, external browser routing,
   `afirma://`, embedded `intent://` AutoFirma, Play Store suppression, and
   fail-closed unknown schemes/intents.
3. Implement the smallest closed result models and policies; no Android side
   effect is permitted in these classes.

**Gate:** focused origin/parser/navigation unit tests pass.

## Task 2: Hardened WebView, cookies, bridge, and state restore

1. RED-test the bridge JSON envelope, request IDs, message bounds, exact source
   origins, and sanitized protocol metadata.
2. Implement `JuntaWebViewClient`, `TrustedJuntaWebView`,
   `WebViewCookieBridge`, `WebMessageBridge`, and a document-start shim guarded
   by `WebViewFeature` checks. Never fall back to unrestricted
   `addJavascriptInterface`.
3. Configure JavaScript/DOM storage/cookies/Safe Browsing while denying mixed
   content, file-network access, arbitrary windows, SSL bypasses, and third
   party cookies. Ordinary allowed URLs return `false` from
   `shouldOverrideUrlLoading` without calling `loadUrl`.
4. Persist WebView history through Activity instance state and make system Back
   consume WebView history first.

**Gate:** unit tests, Android-test compilation, lint, and debug/release builds
pass; `apksigner` and `zipalign` pass.

## Task 3: Browser UI and POCO runtime observation

1. Wire certificate `Continuar` to the browser, with back/reload/home, secure
   session clearing, certificate status, and menu actions.
2. Add instrumentation tests for settings, allowed/external navigation,
   AutoFirma interception, Play Store suppression, SSL cancellation, bridge
   origins, Back, and recreation.
3. Update/install the APK through `/data/local/tmp`. Launch only for the new
   browser/runtime flow. Capture UI tree, screenshot, and sanitized runtime
   metadata; never capture cookies or raw AutoFirma values.
4. Record only observed facts in `docs/protocol-observations.md`. Do not
   implement or hardcode result callbacks until the runtime contract is proven.

**Device gate:** portal loads in-app, cookies remain enabled, no external
AutoFirma/Play Store opens, and the exact runtime branch is safely observed.
