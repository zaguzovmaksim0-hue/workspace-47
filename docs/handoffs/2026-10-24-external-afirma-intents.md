# Safe external `afirma://` intents — design handoff

Date: 2026-10-24. Task: batch task 7 / feature 8 only.
Base: `ae22e7aa15c6d09644287063db3d25a46d05f4a7` (fresh `origin/main`).
Branch: `feat/external-afirma-intents-20261024`.
Worktree: `~/.cache/w47-agent-external-afirma-intents-20261024`.
Status: **design only; incoming external operations remain disabled**.

## Decision and stop-condition

Do not add an exported AutoFirma receiver, an `afirma` intent-filter, an
`onNewIntent` signing shortcut or a partially connected consent dialog in this PR.
An incoming URI is untrusted data, not a trusted portal request. Confirmation is
necessary but cannot manufacture caller authenticity or a web origin.

A safe implementation needs an explicit external-request provenance/ownership
contract and a reviewed binding from that contract to profile operations and
network destinations. Reusing today's browser-bound flow would otherwise require
inventing a `pageUrl`/`TrustedOrigin` or pretending an external Activity is the
current WebView. Generalizing that flow changes **shared portal adapters and the
AutoFirma request/consent contract**, which triggers the batch stop-condition.
No claim that more than 20 production files are necessary has been measured or
used to justify stopping. The independent security condition also blocks shipping:
there is no reviewed external caller-to-profile binding in the current schema.

The deliverable is this plan, not support for signatures from arbitrary Android
browsers. No manifest, production code, UI resources, profile data, security
policy, dependency, lockfile, signing material, verification metadata, baseline or
suppression is changed. No other batch task or another agent's worktree is part
of this change.

## Audited baseline: two routes, not one

The following observations are pinned to the base above, not to historical claims
of universal AutoFirma support:

| Area | Existing boundary and consequence for incoming intents |
| --- | --- |
| [Manifest](../../app/src/main/AndroidManifest.xml) and [MainActivity](../../app/src/main/java/dev/junta/firmamobile/MainActivity.kt) | MainActivity is already exported for the launcher; its only filter is MAIN/LAUNCHER. Startup opens the catalog, not an incoming AutoFirma handler. Absence of a filter does not prevent another app from explicitly targeting an exported Activity. |
| [AfirmaUriParser](../../app/src/main/java/dev/junta/firmamobile/afirma/AfirmaUriParser.kt), [models](../../app/src/main/java/dev/junta/firmamobile/afirma/AfirmaModels.kt) | The profile-bound parser requires a supplied `TrustedOrigin`, an active profile and `AFIRMA_URI`; its enum contains `sign`, `selectcert`, `websocket`. Parser recognition is not permission or end-to-end support. Existing URI/parameter bounds are 1 MiB of characters and 64 parameters. |
| [JuntaOriginPolicy](../../app/src/main/java/dev/junta/firmamobile/network/JuntaOriginPolicy.kt) | `signingOriginFor` checks the selected profile's exact initiator origins. Browse/redirect origins are not interchangeable with signing initiators. An allowed endpoint or a caller-supplied origin string does not prove who initiated a request. |
| [NativeAfirmaNavigation](../../app/src/main/java/dev/junta/firmamobile/browser/NativeAfirmaNavigation.kt), [JuntaWebViewClient](../../app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt) | The separate native servlet route extracts `sign`, `cosign`, `countersign`, `batch`, `selectcert` as data from the current WebView's modern main-frame GET. Do not describe it as identical to the older profile-bound parser. |
| [BrowserScreen](../../app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt), [AfirmaConsentController](../../app/src/main/java/dev/junta/firmamobile/afirma/servlet/AfirmaConsentController.kt) | Native operations belong to an exact live WebView and navigation epoch, require a resumed UI, reject busy ownership and revalidate identity/one-shot upload authorization. Unlock is explicitly not consent. An external Activity has no such WebView provenance. |
| [AfirmaServletInvocationParser](../../app/src/main/java/dev/junta/firmamobile/afirma/servlet/AfirmaServletInvocationParser.kt), [transport](../../app/src/main/java/dev/junta/firmamobile/afirma/servlet/AfirmaServletTransport.kt) | The shared parser says it normalizes syntax, not origin authorization. The generic native transport applies HTTPS/public-DNS, TLS and no-redirect/no-retry protections, not an external-app-to-profile authorization table. A syntactically acceptable public HTTPS server is not an approved external-intent destination. |
| [Profile model](../../app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt), [strict catalog parser](../../app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt) | Profiles declare origins, endpoints, operations and capabilities; the exact schema has no reviewed external caller/package/signer authorization. Do not silently introduce one through URI extras. |

The [2026-10-02 handoff](../autonomous/2026-10-02-external-app-handoff.md)
describes **outgoing** user-confirmed application links. Its requested package is
page-supplied, not identity proof; its browser-return lease proves neither login
nor signature success. It explicitly excludes callbacks into an exported Firma
Mobile entry. Do not reverse that outgoing route into a new incoming trust path.
The [security roadmap](../security-roadmap.md) preserves exact ownership,
main-frame, monotonic expiry, per-operation consent and origin restrictions.
Historical release-only restrictions in that document are not all current:
follow the current runtime registry and the recorded 2026-10-05 release decision,
without promoting any profile or expanding its verified operation scope.

## Caller authenticity: what can honestly be displayed

There are three different facts: an OS-attributed Android sender, a reviewed
application identity, and an authenticated initiating website. None implies the
other two.

- A normal `ACTION_VIEW` launch need not use `startActivityForResult`;
  [`getCallingPackage`](https://developer.android.com/reference/android/app/Activity#getCallingPackage())
  is then null. On API 26–34, absence of usable platform attribution must be
  represented as **caller unknown**, not replaced by the current browser or an
  extra. A result-based caller, when available, identifies the result recipient,
  not a web page or proof of a user click.
- [`getReferrer`](https://developer.android.com/reference/android/app/Activity#getReferrer())
  is expressly not a security feature: `EXTRA_REFERRER` can be returned as supplied
  and spoofed. Referrer names, `android-app://` strings, `Intent.package`, target
  component names, URI parameters and app labels must never authorize a caller.
  `Binder.getCallingUid()` in Activity lifecycle code is not the original launcher
  identity either. Do not infer identity by enumerating installed browsers.
- API 35 adds [`ComponentCaller`](https://developer.android.com/reference/android/app/ComponentCaller)
  with package/UID attribution and Activity caller APIs. Capture attribution for
  the **specific delivery**: initial caller for initial creation, current caller
  inside `onNewIntent` (or its caller-aware overload) for a new delivery.
  [`getInitialCaller`](https://developer.android.com/reference/android/app/Activity#getInitialCaller())
  does not change on later intents;
  [`getCurrentCaller`](https://developer.android.com/reference/android/app/Activity#getCurrentCaller())
  is lifecycle-scoped. Never reuse the first caller to authenticate a replacement
  URI. Availability and attribution across supported API levels need behavioral
  verification; an unavailable or ambiguous identity denies admission.
- Show an OS-attributed package separately from a sanitized, bounded app label;
  strip control/bidi presentation tricks and never show a caller-supplied icon or
  name as a trust badge. If a future reviewed integration trusts a package, bind
  it to its platform UID and reviewed signing-certificate identity/rotation
  policy. A package-name match alone is not publisher authentication; ambiguous
  shared-UID attribution or failed signer lookup fails closed. This is verification
  of the **calling app**, not a change to Firma Mobile release signing or secrets.
- Even a genuine browser package/UID/signer does **not** authenticate its active
  tab, top-level origin, frame, user gesture or request data. Neither API 35 nor a
  consent checkbox repairs that loss. Arbitrary browser-originated signing stays
  unsupported. Do not select a profile using `serverUrl`, referrer, an alleged
  `origin`, the last open portal or a guessed catalog match.

An unknown caller may only receive a local blocked explanation; there is no
"continue anyway" to sign, disclose a certificate, fetch or upload. Existing
in-app browsing may be offered separately, but must not import/replay the URI.
A portal request must be newly issued through the existing controlled browser
path, or use a separately reviewed external app authorization contract. A trusted
app binding would authorize that app as a new explicit request source; it must
not be disguised as a fabricated web `TrustedOrigin`.

## Operation policy

**Effective policy in this PR: no external operation is admitted.** The following
is the maximum proposed first implementation scope, conditional on resolving the
caller/profile/endpoint gates below; it does not enable those operations now.

| Requested operation | Proposed external policy after all gates |
| --- | --- |
| `sign` | At most one inline operation, only with a reviewed caller binding and an active profile's exact allowed operation, format, algorithm, properties, certificate constraints and destination. Explicit final signature consent every time. No new format/algorithm support or delegated signing. If the existing contract cannot express this exact case, deny it. |
| `selectcert` | Separate explicit consent to disclose only the selected public certificate to a reviewed, displayed recipient. Never enumerate all identities, export a private key or use selection as signature consent. No disclosure while locked or to an unbound recipient. |
| `cosign`, `countersign`, `batch` | Denied in the external first slice, even though some in-app native paths recognize them. Their multi-step/delegated/result semantics require separate design and evidence. |
| `websocket`, `load`, `save`, sign-and-save, any unknown operation/version | Denied. Do not open a listener, load a document, invoke another app, start a fallback or guess a nearby supported operation. |
| Indirect `fileid`/Retrieve flows, arbitrary callbacks, automatic server cancellation | Denied in the external first slice. Network retrieval or cancellation is an external effect too; future support needs its own recipient/ownership/consent contract. |

Reject malformed/oversized input, duplicate or conflicting fields, unknown extras
or parameters, double-decoding ambiguity and unsupported format/algorithm/filter
variants before creating an executable operation. Reject headless, silent,
auto-sign, sticky-certificate or consent-suppression requests; do not merely ignore
them and reinterpret the request. Keep all existing limits, with a stricter
external allowlist if needed, never a larger budget. In particular, reuse the
1 MiB/64-parameter upper bounds only as ceilings, not as a reason to accept every
URI of that size or use unchecked Binder payloads.

## Profiles, origin policy and destinations

1. Define a typed external delivery context distinct from browser `TrustedOrigin`:
   delivery-local OS attribution, reviewed app identity, immutable request digest,
   profile ID/version, exact operation and approved recipient set. Initially no
   caller binding exists, so resolution yields denial. Defining/approving that
   binding is a prerequisite, not a task to infer from installed apps.
2. Preserve the runtime registry, activation/compatibility rules and each exact
   profile operation policy. A manual profile selection or a known package cannot
   grant a missing capability. Browse-only or unavailable profiles stay denied;
   QA/experimental availability is not evidence of accepted external signing.
3. An external app contract must explicitly bind the app's allowed operation and
   recipients to a profile without claiming web-origin proof. Otherwise require a
   fresh in-app main-frame request. Never call `AfirmaUriParser.parse` with a
   synthetic catalog origin, or the servlet parser with an invented `pageUrl`.
4. `serverUrl`, `stservlet`, `rtservlet`, result and pre/post-signing endpoints are
   untrusted proposals, never authorities. For any future admitted recipient,
   require exact reviewed endpoint role, HTTPS origin, port, path and permitted
   query contract for that profile/operation. Reject a merely public HTTPS URL,
   cross-profile endpoint, suffix host, redirect, encoded authority/path trick,
   userinfo, fragment, private/local IP or unsupported port. Missing endpoint roles
   mean deny, not a permissive fallback or a new catalog exception.
5. Keep existing public-address/DNS/connected-peer validation, TLS/hostname checks,
   body/time limits, no redirect and no automatic retry behavior. Do not copy site
   cookies, client-TLS grants, authorization, passwords or private-key material into
   an external request. No new relay or arbitrary `serverUrl` escape hatch.
6. Return only through a separately reviewed, request-bound result contract or the
   one explicitly approved network recipient, never an arbitrary URI, component,
   mutable PendingIntent or caller-provided serialized Intent. A minimal native
   result must not expose signature/certificate bytes in URI queries, logs,
   clipboard or an implicit broadcast. No automatic external-app/browser fallback.
   Server acknowledgement is not proof of administrative submission or login.

## Consent and foreground lifecycle (required future behavior)

Proposed states are `ReceivedUntrusted -> ValidatedForReview -> Review ->
ConfirmedOnce -> Executing -> Terminal`, with denial/cancellation possible before
any effect. Parsing is not authorization. No private-key access, certificate
unlock prompt, fetch, upload, service or job is started from `onCreate`,
`onNewIntent` or a background delivery.

- First gate: receiver is resumed, visible, focused and not keyguard-obscured;
  attribution and the reviewed caller/profile/recipient binding are usable; there
  is no pending signing, retrieval, Client TLS, popup or another external request.
  Reject busy/concurrent delivery without replacing or cancelling the current
  portal operation. Do not let attacker traffic destroy the user's live browser.
- Foreground receiver state alone does **not** prove the sender was foreground or
  that a human clicked. Raw VIEW flags and caller-supplied timestamps cannot prove
  this either. Background-originated execution/launch mechanisms are prohibited;
  no receiver/service, WorkManager, AlarmManager, JobScheduler, queued retry,
  notification auto-action or resume-time replay is introduced. An exported
  Activity cannot promise to stop Android from displaying it after every launch;
  it can and must deny sensitive processing. Before enabling a receiver, resolve
  the user-initiated handoff contract; ambiguous cold/background launches remain
  denied, not promoted by simply reaching `RESUMED`.
- Review shows package and attribution status, operation (signature versus public
  certificate disclosure), independently approved portal/profile, recipient
  scheme/host/path without token-bearing query, format/algorithm, bounded payload
  size and SHA-256, and the selected certificate's safe local identity. A digest
  identifies bytes, not their legal meaning; do not present it as document review.
  Show an explicit warning that data will leave the app. Unknown identity or
  unresolved policy leaves the affirmative action disabled.
- Unlocking a certificate is a separate explicit action and never triggers
  confirmation on completion. After unlock/re-selection, refresh the review and
  require a new affirmative action: "Sign once" or "Share public certificate
  once". Cancel/back/dismiss has no signing, network or application-launch effect.
  No "remember this decision", default-checked consent or auto-accept is allowed.
- Bind the one-shot token to the receiver instance/generation, delivery-local
  caller identity, immutable request bytes/digest, profile/version, operation,
  exact destinations and certificate fingerprint. TTL is monotonic, no longer
  than the existing applicable consent window and capped at two minutes; rollback
  and exact expiry deny. A changed caller, payload, endpoint or identity needs a
  new request/review, never an amended already-approved operation.
- Consume before the first authorized effect and revalidate ownership, foreground
  and identity before signing and immediately before the single upload/result.
  Double click, reentrant callback, replay or a second terminal result cannot
  repeat the effect. Use bounded process-local replay protection without extending
  existing TTLs/capacities; do not persist raw URIs or treat caller IDs as secrets.
- Backgrounding, loss of the required foreground ownership, lock, profile switch,
  receiver replacement, cancellation, expiry or process death invalidates pending
  authority. No saved-instance-state/Intent replay or automatic recovery of consent.
  A new delivery may not inherit a prior `ComponentCaller` or token. Preserve the
  browser's existing navigation-epoch and Client TLS cleanup barriers.
- If cancellation/transport failure races a possibly accepted upload, report the
  outcome as uncertain; never retry automatically or claim rollback. Best-effort
  release/zeroization of owned transient buffers is not physical secure erasure.
- Use the existing sensitive-dialog secure-display/overlay protections; do not
  silently change MainActivity's documented screenshot policy. All eventual UI
  text must ship together in `values`, `values-en`, `values-ca`, `values-gl` and
  `values-eu`, with accessible labels and no raw URI/session/key/token disclosure.

## Behavioral acceptance matrix before any implementation is enabled

Tests must exercise real policy/controller boundaries with synthetic data, using
JVM, Robolectric or Compose as appropriate, not Python substring assertions over
Kotlin. No new dependency, baseline finding or suppression is an acceptance route.

| Boundary | Required behavior |
| --- | --- |
| Input | Accept only the explicitly supported envelope; reject malformed escapes, duplicates, conflicting op/version, unknown fields, oversized input, injected Intent extras/components/selectors/ClipData/grants and serialized `intent://` tricks. No execution of a parsed Intent object. |
| Identity | Ordinary VIEW/null caller, spoofed referrer/package/label, unreviewed signer, ambiguous UID and unsupported attribution API all deny with zero network/key effects. Test API 26/34 and 35+ attribution paths, cold delivery and warm re-delivery by a different caller. Browser identity never supplies a website origin. |
| Origin/profile | A trusted-looking `serverUrl`, selected catalog entry, old active portal, browse-only origin or valid browser package cannot mint origin proof. Cross-profile/missing/disabled bindings and unsupported operation policy deny; in-app main-frame/subframe/legacy behavior remains unchanged. |
| Consent | No effect before the explicit final operation action, including after unlock. Cancel, dismiss, double click, stale token, request mutation, exact expiry, clock rollback, signer/certificate/profile changes and reentrant callbacks deny or consume once. |
| Lifecycle | Background/lost ownership, keyguard, busy flow, new delivery, Activity recreation and process death never queue/resume sensitive work or invalidate an unrelated portal operation. Test controllable barriers, not sleeps. |
| Network/result | Wrong endpoint role/path/port/query, public-but-unapproved host, private/mixed DNS results, redirect and TLS failure have no unauthorized result. Keep existing bounds and no-retry checks; uncertain delivery is terminal, not an invitation to resend. |
| Presentation | Attributed package is distinct from an untrusted label and from a website; unknown is honestly unknown. Assert sanitized destination, no sensitive URI data, accessibility, secure dialog policy and all five locales. |

Implementation gates, in dependency order:

1. Maintainer/security review selects a genuine caller-attribution and
   user-initiated handoff contract, plus a non-forged app-to-profile authorization
   model. No arbitrary browser allowlist or "trust this origin" exception.
2. Obtain a synthetic, non-sensitive request/result fixture and an exact reviewed
   operation/recipient contract. No real certificate, account, portal probing or
   administrative submission is needed to design the seam.
3. Re-estimate the complete adapter/owner/consent/schema/test/manifest/locale change.
   If shared portal protocol/adapters still need changes or scope exceeds about
   20 production files, keep it as a separately reviewed architectural plan; do
   not implement the easy exported entry first. Enable the manifest entry only
   with the complete fail-closed boundary and regression evidence, never ahead.
4. A separately authorized bounded implementation must pass applicable existing
   CI on its exact head SHA. Automated parser/controller results alone cannot
   claim physical caller attribution, browser interoperability or portal E2E.
   Any future physical/instrumented acceptance requires separate operator scope;
   none is performed or requested as part of this task.

## Verification and residual limitations for this design PR

Review is against task 7 plus `AGENTS.md`, `CONTEXT.md`, `CONTRIBUTING.md`, the
outgoing handoff and security roadmap. Primary Android Activity/ComponentCaller
references linked above were read on 2026-10-24 through phone-side Chipupa exec.
The Matt workflow router/review guidance is used only within the operator's
no-subagent constraint; the standards and task-spec review are performed directly.

Local documentation whitespace/link and existing policy checks, followed by
automatically triggered PR CI on the exact candidate SHA, are reported in the PR
rather than asserted in advance here. Full phone Gradle/assemble/lint/unit gates,
E2E, instrumentation, emulator/APK activity and manual workflow dispatch are outside
this task. CI's existing ordinary automated jobs are not a new device test.

Residual risks are deliberately unresolved, not waived: raw custom schemes cannot
authenticate a website; older APIs may supply no usable caller; even platform
identity does not prove user initiation or publisher approval; external callback
roles and authenticated app/profile bindings are not currently modeled. This PR
adds no runtime attack surface and makes no new interoperability/E2E claim. The
feature remains unavailable until a complete, reviewed contract can satisfy all
of these gates without weakening the existing policies.
