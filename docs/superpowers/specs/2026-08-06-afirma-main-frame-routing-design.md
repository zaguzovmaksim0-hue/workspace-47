# Afirma Main-Frame Routing Design

## Problem

The binding universal-client design treats iframes as hostile and requires native code
to be authoritative for the top-level origin. It explicitly states that only a
main-frame `afirma:`/`intent:` URI may be recognized by the AutoFirma adapter and lists
`iframe no abre firma ni selección` as a mandatory regression.

`JuntaWebViewClient` already receives `WebResourceRequest.isForMainFrame`, but passes
that bit only to Client TLS authorization and diagnostics. After
`navigationPolicy.decide(...)`, both `NavigationDecision.HandleAfirma` and
`OpenExternal` ignore it. Therefore a subframe navigation from a trusted top-level page
can currently be parsed as a valid `AfirmaRequest` and delivered to
`callbacks.onAfirmaRequest(...)`. The deprecated String callback also supplies
`isModernMainFrame = false` yet can deliver the same native request. Human signing
confirmation remains downstream, so this is a trust-boundary bypass to the native
request surface rather than evidence of an automatic signature.

## Constraints

- Remediate only AutoFirma native routing in this milestone; do not change ordinary
  HTTPS navigation, external-browser handoff, Client TLS, bridge, certificate or signing
  execution semantics.
- Preserve current top-level modern `afirma:` and embedded-Afirma `intent:` behavior.
- Treat the deprecated String callback as unable to prove main-frame identity; it must
  not deliver AutoFirma requests.
- Use the existing `UNTRUSTED_AFIRMA_ORIGIN` closed error for frame ambiguity; do not add
  a new public error enum for the same trust failure.
- Preserve diagnostic redaction and do not log raw `dat`, callbacks or payloads.
- Do not require `hasGesture`: the binding design requires main-frame identity but not a
  gesture bit, and JS-compatible top-level portal flows may not preserve that bit.

## Considered approaches

1. **Recommended: gate `HandleAfirma` at the WebView callback boundary.** Let the
   existing navigation policy parse/validate as before, but before native delivery
   require `isModernMainFrame`. If false, record the existing blocked-navigation event,
   surface `UNTRUSTED_AFIRMA_ORIGIN`, and consume the navigation. This is the smallest
   change and keeps generic policy compatibility intact.
2. Move frame state into `JuntaNavigationPolicy`. Rejected because the policy is also
   used by `WebMessageRouter`, which already has an independent main-frame contract;
   adding WebView callback metadata there would conflate interfaces.
3. Require `request.hasGesture` as well. Rejected for this milestone because it is not
   required by the approved invariant and could reject legitimate JS-driven top-level
   AutoFirma transitions.

## Contract

- Modern main-frame GET `afirma:` from the selected profile signing origin continues to
  call `onAfirmaRequest` exactly once.
- Modern main-frame embedded-Afirma `intent:` continues to call it exactly once.
- Subframe `afirma:` and embedded-Afirma `intent:` are consumed and report
  `UNTRUSTED_AFIRMA_ORIGIN`; no native signing/select callback is delivered.
- Deprecated String callback for an Afirma URI is consumed and reports the same closed
  error because frame identity is unknown.
- Cross-profile/origin, malformed URI, Play fallback, Client TLS and ordinary navigation
  decisions remain unchanged.

## Exact files

- Tests: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`.
- Production: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`.
- Evidence after verification: audit ledger, test report, security roadmap, threat model,
  test plan and durable handoff where the new frame boundary materially changes stated
  coverage.

## Verification

Observe RED with unchanged production source for subframe/legacy valid Afirma routing.
Apply the minimum frame gate, run focused Debug/QA WebView/navigation/bridge regressions,
then fresh full Debug/QA JVM, lint, three assemblies, dependency/toolchain, Python, Go,
Android artifact and release-fail-closed gates. Inspect exact diff, sensitive material,
unsafe WebView/TLS patterns, generated artifacts and remote SHA. No physical portal or
device action is required for the callback-frame contract.
