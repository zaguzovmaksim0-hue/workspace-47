# ViewModel, navigation and complete application/signing composition plan

## Scope and evidence

Task 6 only; design, not implementation. Inspected `origin/main` at
`07b8232d1e3db006260fd11c6a40c52b80286a79` on 2026-10-24 in
`~/.cache/w47-agent2-navigation-di`, branch `docs/navigation-di-plan-20261024`.
Only this handoff is changed. No production, dependency, lock, baseline, workflow,
portal-adapter, consent or security changes are authorized by this PR.

Read the complete batch-2 task package, `AGENTS.md`, `CONTEXT.md`,
`CONTRIBUTING.md`, verification/workflow policies and project-plan sections 3/6.
The external plan is a backlog, not proof of current implementation. The earlier
[composition handoff](2026-10-19-app-container-composition.md) is historical;
the source inventory below is based on this main SHA. Open PRs were inspected by
metadata only; their unmerged features are not included as current screens.

Source paths below are relative to `app/src/main/java/dev/junta/firmamobile/`
unless prefixed otherwise. Useful anchors at the inspected SHA:

- `MainActivity.kt`: startup and fields 107–246; graph construction 247–344;
  root rendering and callbacks 350–623; lifecycle/external results 626–736;
  prepare/confirm/cancel and origin providers 739–916; destination model at end.
  Actual size: 966 lines, not a target for line-count-driven refactoring.
- `ui/BrowserScreen.kt`: local owners/state 163–389; Back/disposal/lifecycle
  766–867; native dialogs 1648 onward; retained parent/popup 1830–1887.
  Actual size: 1887 lines. Task 7 owns its concurrent decomposition.
- `AppContainer.kt`, `JuntaFirmaApplication.kt`,
  `certificate/CertificateModule.kt`, `signing/SigningModule.kt`;
  `browser/BrowserSessionStatePolicy.kt`, `ui/AppRoot.kt`;
  `gradle/libs.versions.toml`, `app/build.gradle.kts`.

## 1. Actual screen and transition map

There is no NavHost/back stack today. A private `AppDestination` in MainActivity
selects exactly Certificate, Catalog, or Browser(profileId?, entryUrl: URI).
Dialog visibility is separate from destination. Do not invent extra routes for
features in other draft PRs.

| Surface | Entry and exit today | Important constraints |
| --- | --- | --- |
| Catalog startup/loading | onCreate → beginCatalogStartup → loadCatalogRepository; success initializes ready content | Before normal root; failure shows retry, retry calls beginCatalogStartup; active startup job blocks duplicates |
| Catalog startup error | Repository exception → failed startup UI → retry/loading | Cancellation is rethrown; no ready UI until repository available |
| Certificate home (`AppRoot`) | Initial destination; Catalog's onBackToCertificate returns here; onContinue → Catalog | Browsing without an unlocked certificate is explicitly supported; unlock alone does not navigate |
| Certificate states | Existing CertificateViewModel feeds AppRoot selection/unlock/lock/forget UI | These are certificate state changes, not destinations; keep existing state machine |
| Catalog (`PortalCatalogScreen`) | Certificate continue → Catalog; back button → Certificate; successful portal selection → Browser | Search, region, favorites, recent opens and location messages are catalog VM state; verified-only setting persists then restarts app |
| Default/universal browser | onOpenPortal resolves resolveUniversalOpenTarget; Browser(profileId=null, entryUrl) | Default must remain browse-only; failure stays in Catalog and reports open failure |
| Compatibility browser | Explicit onOpenCompatibilityPortal resolves InApp(profile launch) or PublicWeb(null profile) | Opt-in proprietary route is distinct from universal/default; never silently assign signing authority |
| Public address browser | onOpenPublicWeb validates PublicBrowserAddress then Browser(null, accepted URI) | Reject invalid address; no arbitrary trusted profile inference |
| Browser history/client-auth mode | BrowserScreen BackHandler invokes goBack | Active clientAuthGrant is abandoned first; otherwise clear pending POST, advance epoch, cancel signing, then WebView.goBack or exit to Catalog |
| Certificate panel over browser | Change certificate cancels signing then opens secure full-width Dialog(AppRoot); continue/dismiss closes panel | Browser destination remains mounted; not a push of another Certificate page; hide/suspend relevant prompts while panel is visible |
| Popup browser | BrowserScreen retains parent and recursively mounts child BrowserScreen keyed by popup token | Child has profileId=null; close restores parent ownership, not Catalog; no copied native-key/profile authority |
| Signing/identity dialogs | Coordinator SigningUiState and browser-local native/TLS/selection/retrieval prompts | Transient, owner-bound overlays, not restorable routes; ordinary/batch coordinator consent is not the whole native flow |
| External-app/handoff/error dialogs | Activity pendingExternalApp / pendingExternalHandoff / externalHandoffFailed | External app requires exact owner/epoch ticket and expires after 120 seconds; handoff confirmation checks epoch; launch may fail |
| Legal information/offline verification | AppRoot local remember flags → LegalInformationDialog / OfflineVerificationDialog | Also available when AppRoot is in certificate panel; not new top-level destinations |
| System surfaces | Certificate OpenDocument, browser file picker, DocumentDownloadActivity, coarse-location permission, settings and external app/browser/AutoFirma | ActivityResult/Intent effects, not Compose routes; returns finish matching tokens, not arbitrary pending actions |

Browser exit cancels signing with NAVIGATION before setting Catalog. Catalog
opens and smoke-hook opens cancel signing and clear currentWebView first.
`key(profileId, entryUrl)` defines browser composition identity. AppRoot/Catalog
have explicit UI transitions but no equivalent root BackHandler in MainActivity;
do not silently add a Catalog → Certificate system-back stack as a refactor.
Smoke launch is a controlled separate entry through CatalogSmokeController,
not a general deep link.

## 2. State and lifetime inventory

| Current owner/state | Proposed owner and persistence |
| --- | --- |
| Activity destination, certificatePanelVisible | AppNavigationViewModel exposes immutable root/panel state; initial Certificate and closed panel on a fresh Activity/browser host; in-memory only, no SavedStateHandle restoration of browser URLs or panel |
| Activity startupJob, catalogRepository readiness | Startup ViewModel or existing loader-backed startup owner, Loading/Failed/Ready StateFlow; app owns cached CatalogRepositoryLoader; Activity renders once, retry is an explicit command |
| Activity catalogViewModel / certificateViewModel references | Already ViewModelProvider/by viewModels; retain PortalCatalogViewModel and CertificateViewModel rather than duplicate their data/state in a new VM |
| Activity ordinary/batch coordinators, seven batch bridge adapters | SigningModule constructs session-scoped graph; a SigningViewModel owns orchestration, immutable derived signing UI and active-session binding |
| Activity SigningFlowOwnershipGate, SigningJobRegistry, lazy confirm jobs | Move together with prepare/confirm/cancel/dismiss to SigningViewModel; preserve requestId matching, single owner and completion-before-release ordering |
| Activity currentWebView, epoch, external consent/return leases, picker/download tokens | Browser host owned by current Activity/composition, never retained in VM/container; publish only revocable host identity/epoch observations to signing orchestration |
| Activity pending external request models and failure flags | Keep in host-side controller initially, with ephemeral UI projection; do not replay launch effects or serialize URIs/tickets through SavedStateHandle |
| Activity launchers, window protection, permission/settings calls, AppRestarter, CatalogSmokeHook | Remain Activity adapters; app/container may supply factories, never hold Activity/window/launcher closures |
| BrowserScreen currentUrl, loading/errors, profile/trust controller, epoch | Keep browser-session-local while root orchestration migrates; any later display-only model extraction must not extend grants or WebView lifetime |
| BrowserScreen WebView refs/clients, popup lease/transport, client-auth grants/POST bytes, bridge attachments, clear leases | Remain host/composition-owned; existing disposal/release, invalidation and zeroization remain authoritative |
| BrowserScreen nativeAfirma/nativeFallback/nativeRetrieval/interactiveClientAuth controllers and prompts | Browser lifetime, not global SigningModule singletons; injectable factories later, still tied to live view/epoch and RESUMED checks |
| Application certificate session/cache/gateway | Existing CertificateModule, app lifetime; VM uses session, never saves keys, password arrays or unlocked identities |
| Catalog search/favorites/preferences, certificate unlock state | Existing VM/repository/store owners; no new duplicate persistence or change to retention policy |

Do not put WebView, Context(Activity), reply callbacks, documents, native grants,
request payloads, raw page URLs, private keys or passwords in saved navigation
arguments. No automatic signing/consent restoration after process death.

### Lifecycle contract before changing scope

Today onStart foregrounds CertificateViewModel; onStop clears external-app
consent, stops smoke, cancels signing BACKGROUND and backgrounds certificate VM;
onLowMemory cancels signing and applies memory-pressure policy; onDestroy cancels
picker callbacks, stops smoke, cancels and closes both coordinators. BrowserScreen
also revokes browser-local grants on background except explicitly leased expected
returns, rechecking identity/expiry on foreground. AndroidView.onRelease alone
owns native view destruction.

A retained ViewModel must not make activity-destroyed signing survive. Define a
fresh, nonpersistent host-session ID; detach synchronously revokes its binding,
cancels jobs/closes coordinators, and rejects late replies/tunnel events. The next
host attaches a new graph with fresh live origin/epoch providers. No closure in a
retained VM may capture the destroyed Activity or WebView. Initially keep signing
execution scope activity/session-owned and let the VM orchestrate it through an
explicit attach/detach seam; do not blindly replace lifecycleScope with
viewModelScope. A later scope change needs its own behavioral evidence.

The manifest already handles rotation/resize/night mode without recreation
(`MainActivityConfigurationContractTest`). Preserve that contract; real host
recreation currently resets destination to Certificate. Distinguish rotation
handled in place from forced recreation/process death in tests.

## 3. Dependencies and Navigation Compose decision

Verified declarations, without resolving/building: `gradle/libs.versions.toml`
contains lifecycle 2.10.0 aliases for lifecycle-runtime-compose and
lifecycle-viewmodel-ktx, both consumed by app/build.gradle.kts (409–410).
Activity Compose and coroutines are already present. No `androidx.navigation` or
`navigation-compose` declaration exists in the catalog or inspected build files.
There is no lifecycle-viewmodel-compose alias either. Existing by viewModels /
ViewModelProvider.Factory plus collectAsStateWithLifecycle suffice; do not assume
new Compose helpers or transitive Navigation artifacts are available.

**Chosen no-dependency path:** extract AppDestination to a small typed model,
AppNavigationViewModel commands, and a plain composable AppNavigationRoot using
`when`. Keep existing browser keying, root replacement semantics and secure
certificate overlay. Do not call this custom switch androidx NavHost. Do not
build a generic navigation framework or a saved stack; only model actual existing
transitions. Reset host-bound Browser state on host replacement, while permitting
existing catalog VM state to remain under its current owner.

**Optional Navigation Compose path, blocked on owner approval:** a distinct
follow-up may add an explicit catalog alias and implementation dependency only
after the owner approves version/license/verification metadata/lock implications.
No dependency or verification update belongs in this task. Then replace only the
root switch with NavHost/rememberNavController and typed/simple destinations
Certificate, Catalog and Browser with an opaque in-memory session handle, not a
raw URI/password/serialized request. Resolve a handle through the live host and
existing URL/profile validation. Unknown/restored handles fail closed to
Certificate. Configure pop/replace/back behavior to match the table, including
no retained privileged browser destination in a hidden back stack. Certificate
panel, popup and all consent remain live overlays, not saved routes. Automatic
NavController restore must not revive a Browser or consent operation. If that
cannot be proven with the approved version, keep the no-dependency root.

## 4. Complete AppContainer and SigningModule shape

### What already exists

AppContainer eagerly creates CertificateModule once; createSigningModule accepts
an explicit **current** certificateSession and caller-owned scope and returns a
fresh module. CertificateModule constructs monotonic CertificateSession,
AndroidKeystoreCertificateUnlockCache and CertificateRepository (document access,
preferences reference store, Pkcs12Loader). Do not confuse the cache's AES key
with the certificate signing key.

SigningModule already builds RuntimeSigningAdapters.ordinary/batch,
JcaLocalSignatureEngine, CoroutineSigningExpiryScheduler and the two coordinators.
Ordinary uses JuntaTriPhaseAdapter as default; batch uses MelillaBatchProtocolAdapter
as default and the resolver for other protocols. MainActivity still constructs
transports, supplies live origin/page/epoch functions, creates seven bridge
normalizers/reply wrappers, dispatches batch profile IDs, arbitrates jobs and
ownership, and delivers accepted tunnel-route diagnostics.

JuntaFirmaApplication still separately constructs catalog loader, sanitized
logger, secureTunnelRuntime and clientCertPreferenceCoordinator. A complete
composition root means explicit construction/ownership of these dependencies,
not a global container handed to every composable.

### Target composition and ownership

```text
JuntaFirmaApplication (profile scope configured before catalog use)
  AppContainer (application-only immutable dependencies/factories)
    CertificateModule -> same gateway/session/cache; compatible injection hooks
    catalog loader + preferences/region-detector factories
    sanitized logger + secure tunnel runtime + client-cert preferences
    ViewModel factories -> explicit current dependencies, no service locator
    createSigningSession(current certificateSession, scope, live host binding)
      SigningModule -> transports + runtime registries + engines + schedulers
                     + ordinary/batch coordinators + batch dispatch
      SigningViewModel -> ownership/jobs/commands/UI projection
MainActivity -> launchers/window/lifecycle + current browser host adapter
BrowserScreen -> WebView/popup/TLS/native consent lifetime and release
```

Proposed names are design placeholders, not existing classes. Build the graph in
small constructor/factory steps. Preserve initialization order: profile scope,
PDFBox initialization, logger, secure tunnel runtime, client-cert preferences,
certificate module and its published aliases. Retain current mutable Application
test injection compatibility until an explicitly reviewed replacement exists;
never cache a stale certificateSession in a factory default.

SigningModule responsibilities to add in separate bounded slices:

1. Inject engine/expiry scheduler/transport factories with current production
   defaults. Move direct HttpsProfileHttpTransport and the existing
   secureTunnelRuntime.transportFor(junta-ofvirtual, exact ENDPOINT, observer)
   composition out of Activity, preserving route policy/observer behavior.
2. Own construction of Melilla, Extremadura, LaPalma, Huesca, Lugo, Caib and Burgos
   batch bridge normalizers/reply sinks and their paired dispatch table. Unknown
   profile still INVALID_REQUEST; rejected normalized payloads close. No adapter
   implementation, origin, format, limit or protocol unification in this work.
3. Produce a session-scoped aggregate of both coordinators plus dispatch, with
   deterministic close and explicit caller scope/host-binding lifetime. Keep
   signingFlowOwnership and job arbitration in one orchestration owner, not one
   independent owner per coordinator. Live null origin means reject, not cached
   last-known authority.
4. Accept request-correlated tunnel observation and sanitized diagnostics through
   explicit functions; no app casts/Activity access inside SigningViewModel.
   Preserve main-thread delivery and coordinator acceptance before logging.
5. Native AutoFirma flow is a distinct browser-local path: BrowserScreen creates
   AfirmaConsentController, AfirmaFallbackController and AfirmaRetrievalController
   (with AfirmaDeferredResolver). A later factory can inject their pure operation
   collaborators; do not relocate their WebView owner, prompt token, busy checks,
   RESUMED gating or composition scope to app lifetime under the word "complete".

Excluded: new DI framework, non-exportable key import, per-sign biometric policy,
new portal algorithms/adapters, JS modularization, catalog asset migration,
external afirma intent support and offline/local-sign feature expansion.

## 5. Ordered implementation PRs (not implemented here)

Each slice starts from fresh main, has behavioral tests first and one bounded
commit/PR. Reassess at ~20 production files or any protocol/security-model change;
stop for a separate owner-reviewed plan rather than widening scope.

| PR | Files/call sites | Acceptance and rollback seam |
| --- | --- | --- |
| 1: Characterize root transitions | MainActivity tests, BrowserScreenTest and new navigation tests | Default/compatibility/public open, failed startup/retry, panel close/continue, browser exit and actual Back semantics; no production migration yet |
| 2: Explicit root state | Extract AppDestination/AppNavigationViewModel/AppNavigationRoot; MainActivity.setContent/callbacks | JVM command tests plus Robolectric/Compose UI tests; keep existing screen functions unchanged; revert root wiring alone if semantics differ |
| 3: Application factories | AppContainer/JuntaFirmaApplication; existing catalog and CertificateViewModel factories in MainActivity | Same certificate object identities, current injected session, logger/runtime/order and catalog single-load/retry behavior; no new singleton scopes |
| 4: Complete signing construction | SigningModule, MainActivity.initializeReadyContent and batch prepare dispatch; module tests | Same ordinary/batch resolutions, direct/tunnel selection, all seven paired dispatch paths and invalid profile rejection; no portal adapter source edits |
| 5: Signing orchestration VM | New SigningViewModel/live host binding; Activity prepare/confirm/cancel/dismiss/tunnel methods; ownership/job tests | Single lazy confirm per UUID, cancellation completion releases exact owner, stale epoch/host denied, both coordinator flows observed from initial Idle, close exactly once |
| 6: Lifecycle/host adapter cleanup | MainActivity lifecycle/results; explicit browser host adapter and minimal BrowserScreen callbacks after task 7 lands | Background/low memory/detach revoke; stale file/external replies cannot act; popup parent not double-destroyed; preserve expected-return rules and secure window |
| 7: Optional real NavHost | Owner-approved dependency change and root implementation only | Only after 1–6 and approval; navigation UI/back/restore tests prove same policy; no URL/consent restoration; otherwise do not implement |

PR 3 and 4 can be reviewed independently after characterization; PR 5 depends on
both graph factories and the tested host-lifetime seam; PR 6 follows PR 5.
No simultaneous broad rewrite of BrowserScreen and MainActivity. Rebase/reinspect
task 7 extraction before touching browser callbacks. Coordinate catalog/JS/key
plans via interfaces, not edits to their worktrees or PRs. Reinspect open feature
PRs (#418 local signing, #422 verification journal and #411 PDF preview at audit
time) before later implementation, without assuming any is merged or changing it.

## 6. Verification, risks and open decisions

For this docs-only PR: source inventory and dependency declaration inspection,
`git diff --check`, docs-only scope check and relative Markdown-link checks.
No Python tests reading Kotlin as text and no source SHA-pinning tests are added.
No local Gradle, compilation, builds, E2E, emulator, APK install or workflow
manual dispatch. Automatic CI/security at the exact PR head is the broad gate;
pending checks are handed to the coordinator, never labeled passed.

Future implementation acceptance (CI, not commands run for this plan):

- Keep AppContainerConstructionTest, CertificateModuleTest,
  SigningModuleRegistryRegressionTest, SigningFlowOwnershipGateTest,
  SigningJobRegistryTest, MainActivityConfigurationContractTest,
  BrowserSessionStatePolicyTest, BrowserExternalReturnLeaseTest and
  BrowserScreenTest. New tests must assert behavior via real seams, not Kotlin text.
- Add fake-clock/fake-host JVM cases for stale UUID/epoch/host, cancellation races,
  duplicate confirm, expiry, detach/rebind, rejected payload closure and close
  idempotence; test both initial Idle subscriptions and terminal-state dismissal.
- Add Robolectric/Compose transition tests for certificate-less browsing,
  default-null-profile vs explicit compatibility, browser panel/popup Back,
  permission denial/picker cancellation, unresolvable external launch and startup
  retry. Forced recreation/process death must return safely without restoring
  WebView history, a browser URL, consent, native key grants or queued effects.
- Preserve native controller/TLS, origin/navigation, consent and window-protection
  behavioral suites. CI unit/lint/detekt/security must remain strict, no baseline
  inflation or suppressions. Unrelated failures must be identified, not bypassed.
- Physical provider/WebView/certificate acceptance remains unverified; synthetic
  unit/Compose tests and ordinary PR CI are not portal E2E acceptance. No E2E is
  authorized by this task, including manual workflow opt-in.

Primary risks: retaining a destroyed Activity through callbacks, making signatures
survive background by changing scope, treating a null-profile/public popup as
trusted, resurrecting privileged state through Navigation saved back stack,
double release of WebView, stale ActivityResult/external-return token consumption,
releasing flow ownership before canceled work completes, and divergence between
native and ordinary/batch busy/consent rules. Preserve existing policies; stop for
owner decision if implementing a seam would change them.

Owner decisions still needed: whether to approve Navigation Compose at all;
whether a future product change wants Catalog system-back behavior different
from today's; and any desired browser restoration policy (out of scope, default
remains no restoration). This document does not declare the architecture backlog
or any unrelated feature complete.
