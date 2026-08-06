# External Navigation Main-Frame Boundary Design

## Finding

`JuntaWebViewClient` receives authoritative `WebResourceRequest.isForMainFrame`
metadata and already uses it for Client TLS, the narrow OFVirtual HTTP upgrade, and
native Afirma delivery. `NavigationDecision.OpenExternal` remains the exception: it
records `EXTERNAL_NAVIGATION` and calls `callbacks.openExternal(...)` regardless of
frame ownership. The deprecated String callback also supplies
`isModernMainFrame = false` but can reach the same branch.

This is a native side-effect boundary, not merely a rendering choice. In production,
`BrowserScreen.openExternal` clears pending Client TLS state and pending Afirma state,
abandons Client TLS, advances the navigation epoch, cancels signing, and then calls the
Activity callback. `MainActivity` subsequently launches an Android `ACTION_VIEW`
intent for the validated HTTPS URI. Therefore an iframe or legacy callback can
currently cause top-level security-state invalidation and an external Activity launch
without proving top-level ownership.

The binding browser designs require native top-level state to be authoritative: an
iframe does not change profile or trust mode, while `EXTERNAL_ONLY` represents an
explicit HTTPS handoff selected by the top-level policy. Frame-ambiguous callbacks
must not be allowed to invoke this native handoff.

## Scope

This milestone changes only the WebView callback-to-native external-navigation
boundary.

In scope:

- direct safe third-party HTTPS decisions that become `OpenExternal`;
- validated `intent:` browser-fallback decisions that become `OpenExternal`;
- modern main-frame versus subframe ownership;
- the deprecated String callback, whose frame ownership is unknown;
- sanitized diagnostics and regression coverage.

Out of scope:

- changing `JuntaNavigationPolicy` HTTPS/intent parsing or allowlists, except adding a
  typed block reason used by the callback boundary;
- changing which top-level URLs qualify as `OpenExternal`;
- adding a user-gesture requirement;
- changing BrowserScreen/MainActivity external-intent behavior for an approved
  main-frame handoff;
- Client TLS, WebMessage, Afirma parsing/signing, cookies, certificates, profiles,
  release activation, dependencies, or portal contracts.

## Considered approaches

1. **Consume ambiguous external navigation without any native callback — selected.**
   When `OpenExternal` is reached from a non-modern-main-frame callback, record a
   sanitized `NAVIGATION_BLOCKED` diagnostic with a dedicated
   `UNTRUSTED_EXTERNAL_NAVIGATION` reason and return `true`. Do not call
   `openExternal` and do not call `onNavigationBlocked`. This prevents the iframe or
   legacy callback from launching Android UI, cancelling signing, invalidating Client
   TLS, advancing the navigation epoch, or changing the top-level notice.
2. Return `false` for a direct HTTPS subframe. Rejected because it converts an
   `EXTERNAL_ONLY` decision into embedded content and cannot be applied safely to an
   `intent:` fallback without handing a custom scheme back to WebView.
3. Consume and call `onNavigationBlocked`. Rejected because the external Activity is
   prevented but an untrusted iframe can still mutate top-level UI state through the
   blocked-navigation notice. There is no need for that side effect to enforce the
   security boundary.

## Contract

- A modern main-frame direct external HTTPS navigation continues to emit one sanitized
  `EXTERNAL_NAVIGATION` event and exactly one `openExternal` callback.
- A modern main-frame validated `intent:` HTTPS browser fallback retains the same
  external handoff behavior.
- A direct external HTTPS subframe is consumed, records only a sanitized
  `NAVIGATION_BLOCKED` event with reason `UNTRUSTED_EXTERNAL_NAVIGATION`, and emits no
  `openExternal` or `onNavigationBlocked` callback.
- An `intent:` browser fallback reached from a subframe follows the same closed path.
- A deprecated String callback that resolves to `OpenExternal` follows the same closed
  path because it cannot prove main-frame ownership.
- The raw URL query/fragment is not added to external diagnostics. Existing sanitized
  navigation logging remains authoritative for the blocked path.
- Existing stale-WebView ownership rejection remains earlier than this decision and is
  unchanged.

## Exact production interfaces

- `app/src/main/java/dev/junta/firmamobile/browser/JuntaNavigationPolicy.kt`
  - add `NavigationBlockReason.UNTRUSTED_EXTERNAL_NAVIGATION` only;
  - do not change `decide(...)`, `decideHttpsUrl(...)`, `decideIntent(...)`, URL
    validation, profile matching, or `OpenExternal` construction.
- `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
  - gate only `NavigationDecision.OpenExternal` native delivery on
    `isModernMainFrame`;
  - non-main/legacy path records the typed sanitized block diagnostic and returns
    consumed without callbacks;
  - modern main-frame branch remains behaviorally identical.
- `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
  - reproduce direct HTTPS and validated browser-fallback subframe/legacy delivery;
  - prove modern main-frame direct/fallback positive controls remain.

## Security properties

The remediation removes a cross-frame capability: only a modern callback explicitly
marked main-frame may cause the WebView client to deliver an external-navigation
command into application state and Android Activity routing. It does not claim that an
external browser itself is trusted, and it does not broaden any portal/profile trust.

No user gesture is added because the verified defect is frame ownership. Gesture
semantics are a separate compatibility/security question and changing them here would
expand scope without evidence.

## Verification strategy

1. Write the smallest Debug regression proving a direct HTTPS subframe and deprecated
   callback currently reach `openExternal`; observe RED before production mutation.
2. Add the validated `intent:` browser-fallback frame-negative and modern main-frame
   positive control while production is still unchanged; observe the expected RED for
   the non-main path.
3. Implement the minimum typed reason and WebView branch gate.
4. Run focused Debug/QA WebView/navigation/WebMessage regressions.
5. Because production WebView behavior changes, run fresh dependency/toolchain locks,
   full Debug/QA JVM, lint, Debug/QA/QA-AndroidTest assemblies, Python, Go, Android
   artifact checks and release-signing fail-closed gates.
6. Inspect complete diff; run `git diff --check`, exact-scope, sensitive-data and
   unsafe WebView/TLS scans; confirm relay and release APK absence.
7. Update the audit ledger, test report, security roadmap, threat model, test plan and
   durable handoff with the exact automated claim. No physical portal/device E2E is
   claimed or required for this callback ownership regression.
