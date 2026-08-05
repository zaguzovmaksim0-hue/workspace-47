# Security hardening roadmap

Completed:

- Trust lifecycle and effective top-level profile binding.
- Junta tri-phase filter contract and real E2E signing.
- Fail-closed MiniApplet routing when the effective profile is missing (F-02).
- Private release signing with no debug-key fallback (F-01).
- `qa` build variant for unverified portal work.
- Release activation restricted to sensitive `VERIFIED_E2E` profiles (F-04).
- Junta legacy Ovorion, Carné Joven, Aragón SIRAW and Junta Oficina Virtual are
  tracked separately. Carné Joven `CLIENT_TLS_AUTH` was verified on a physical
  device on 2026-07-21; Aragón login CAdES on 2026-07-28; Oficina Virtual
  MiniApplet 1.5 login CAdES on 2026-07-29; UniZAR login CAdES on 2026-07-30.
  RedSARA remains `QA_ONLY`; historical Ovorion MiniApplet 1.4 is `EXPERIMENTAL`, is available only under
  QA policy for sensitive operations, and is excluded from release.
- Profile/public-catalog E2E consistency gate (F-15A): every bound profile marked
  `VERIFIED_E2E` must have the exact public metadata pair
  `E2E_VERIFIED / VERIFIED_E2E`, and non-E2E profiles cannot carry that pair.
- Catalog-generation deduplication completed (F-15B):
  `config/site_profiles_v1.json` is the sole committed profile data source and
  is embedded through `BuildConfig`; the public catalog is generated only from
  the reviewed inventory. All eight profile bindings require exact equality of
  `startUrl` and `entry_url`; missing, duplicate or colliding mappings fail
  closed. The former Python binding table and supplemental entries are removed.
- RedSARA live gate revalidated on 2026-07-30: both public entry paths require
  Cl@ve and the XAdES operation belongs to a prepared administrative request;
  the profile remains `VERIFIED_CONTRACT / QA_ONLY` until a real authorized case.
- Browser navigation, WebMessage bridge and signing origin bound to the selected profile (F-06).
- Cross-profile and external HTTP navigation blocked (F-06, F-07).
- Renderer loss invalidates bridge/signing state and creates a fresh WebView.
- Aragón SIRAW login profile enabled after physical-device E2E acceptance: exact
  origin, 20-byte challenge, local detached CAdES, `SHA1withRSA`, exact
  `mode=explicit` and `filter=nonexpired`; Storage/Retrieve and document-signing
  branches remain blocked.
- UniZAR login profile version 2 enabled after physical-device E2E acceptance:
  exact origin, 20-byte precalculated challenge, detached CAdES,
  `SHA1withRSA`, exact `precalculatedHashAlgorithm=SHA1` and `serverUrl`;
  Storage/Retrieve, co-sign, counter-sign and document-signing remain blocked.
- MainActivity secure-window policy completed (F-05): `FLAG_SECURE` remains
  active during password entry, certificate unlocking, every unlocked certificate
  surface, portal WebView and every non-idle signing state. First-run/no-certificate
  idle UI remains capturable; the isolated debug probe policy is unchanged.
- Profile-scoped cookie/session hardening completed (F-08): native cookie
  access requires an active `SiteProfile` and an exact declared network endpoint;
  the historical WebView facade is profile-bound; current-site cleanup deletes
  only the current HTTPS origin and never falls back to global deletion. Closing
  the certificate session and deleting all WebView data are separate confirmed
  actions. WebView provider capabilities are measured without calling
  `WebViewCompat.setProfile`.
- Public IPv6 DNS-result hardening completed (F-17): Android and the QA
  relay use equivalent public-address policy reviewed against the IANA IPv6
  Special-Purpose Address Space registry revision 2025-10-09. Ordinary IPv6 is
  limited to `2000::/3`; scoped, mapped and special-purpose ranges are blocked;
  well-known NAT64 `64:ff9b::/96` is accepted only when the embedded IPv4 passes
  the existing public IPv4 policy. Hostname/SNI and connected-peer pinning are
  unchanged.
- Process-scoped Client TLS preference lifecycle completed (F-13): a single
  `ClientCertPreferenceCoordinator` owns the asynchronous WebView preference
  clear for the process. `CLEARING` and sticky `FAILED` suppress every portal
  WebView; a dedicated Client TLS grant is activated only after the generation-
  bound callback, exact profile/epoch/TTL revalidation and one-shot consumption.
  Timeout is three seconds; late callbacks, Activity recreation, background,
  renderer death, certificate lock and profile switch fail closed. A later
  successful clear is the only in-process recovery.
- Monotonic TTL and replay hardening completed (F-09/F-10): pending signing,
  active operation and MiniApplet reply windows are decided only by process
  monotonic time. Request IDs remain replay-blocked for a bounded five-minute
  retention window and are then pruned before capacity checks. Clock rollback,
  exact-boundary expiry, concurrent confirmation and concurrent terminal reply
  races are covered by hostile tests. Bridge-generated security IDs require Web
  Crypto and fail closed; `Math.random()` is absent from the shim.
- Identical in-flight MiniApplet signing calls are coalesced without invoking the
  portal error callback; any differing concurrent request remains fail-closed.
- CI and supply-chain gate completed (F-14): GitHub Actions use read-only
  permissions and exact action commit SHAs; Gradle 9.4.1 wrapper/distribution and
  resolved artifacts are SHA-256 verified; complete Git history is scanned with
  pinned Gitleaks; Dependabot covers Gradle, Go modules, GitHub Actions and pip at `/tools`; Android APKs
  are checked for alignment, signature count, manifest hardening and forbidden
  canaries; release still fails closed without the private signing key. Go is
  pinned to 1.26.5 and OSV scans the explicit Python and Go manifests.

Completed isolated PR — public IPv6 DNS results (F-17):

- `PublicIpAddressPolicy` classifies resolved `InetAddress` values before any
  HTTP bytes are created.
- Ordinary IPv6 must be within `2000::/3` and outside the reviewed IANA
  special-purpose deny-set; scoped and IPv4-mapped addresses fail closed.
- Well-known NAT64 is accepted only for a public embedded IPv4; private,
  loopback, documentation, benchmark and other special embedded IPv4 fail.
- OkHttp keeps the original endpoint hostname for URL/SNI/hostname verification,
  uses only the approved DNS set and verifies the actual connected address.
- The Go relay mirrors the classifier, rejects unsafe mixed DNS sets, dials a
  bracketed IPv6 literal and verifies the exact remote peer.
- Physical-device `PublicIpAddressPolicyInstrumentedTest` now passes on the
  production Android `InetAddress` implementation. This closes the deferred
  classifier gate; it is not a live IPv6 route or portal E2E claim.

Completed isolated PR — process-scoped Client TLS preference barrier (F-13):

- `JuntaFirmaApplication` owns one coordinator for the entire app process.
- `WebView.clearClientCertPreferences` is isolated behind one Android adapter;
  `BrowserScreen` cannot call it directly.
- `CLEARING` and `FAILED` prevent normal and dedicated `AndroidView` creation.
- Timeout, exception and post-callback profile/epoch/TTL mismatch fail closed;
  the `FAILED` state survives Activity recreation and ignores stale callbacks.
- Retry issues a new generation and only a completed platform callback returns
  the process to `IDLE`.
- Physical-device instrumentation exercised the real Android clear callback
  without opening a portal, reading a certificate or starting a signature.

Latest completed isolated PR — CI and supply-chain gate (F-14):

- CI separates Android, Python and Go gates and uses `contents: read`, immutable
  action SHAs, bounded timeouts and concurrency cancellation.
- Gradle 9.4.1 distribution and wrapper JAR are pinned by official SHA-256;
  dependency verification rejects unrecorded artifacts and contains no wildcard
  trust rule.
- Android gates run unit tests, lint and APK builds, then verify 16 KiB alignment,
  v2 signature, exactly one signer, manifest hardening and forbidden canaries.
- A release build without private signing inputs must fail and must not leave an
  APK; debug signing is never accepted as a fallback.
- Gitleaks 8.30.1 scans complete history with redaction; its exact Linux x64
  archive checksum is verified before execution.
- Dependabot covers Gradle, Go modules, GitHub Actions and pip at `/tools`. Go is pinned to the
  patched 1.26.5 toolchain; `govulncheck` and OSV cover the relay and the explicit
  Python/Go manifests.
- OSV intentionally does not parse `gradle/verification-metadata.xml` as a runtime
  lockfile: it is an integrity checksum ledger containing build-tool artifacts.
  Full Gradle-graph vulnerability reachability remains a residual limitation;
  integrity verification and update automation do not prove absence of CVEs.
- Go race instrumentation remains a required Linux CI gate. The local
  Android/arm64 toolchain cannot run it and is not recorded as passed.


Latest completed isolated PR — catalog-generation deduplication (F-15B):

- The unchanged profile catalog moved from Android `res/raw` to the canonical
  `config/site_profiles_v1.json`; its SHA-256 remains
  `a45cf2bbfe13d3492a963d0b8866c676ec13e5e95fac99e0cf2e0eeac568dc4c`.
- Gradle emits the canonical JSON as `BuildConfig.SITE_PROFILE_CATALOG_JSON`;
  `BuiltInSiteProfiles` no longer contains a handwritten JSON body and the APK
  no longer packages a second raw profile copy.
- The reviewed inventory now contains 182 records. Oficina Virtual and
  Educación convocatoria 46 have stable IDs `ES-PUB-0181` and `ES-PUB-0182`;
  there are no supplemental public entries outside the inventory.
- The generator reads both canonical sources and binds all eight profiles only
  by exact full-URL equality. Duplicate IDs/URLs, missing matches, multiple
  matches, collisions and unexpected profile-catalog root keys fail closed.
- Semantic comparison against the preceding generated catalog found no added or
  removed portal and no changed trust/evidence field; only the two former null
  inventory IDs and the deterministic source revision changed.
- No portal request, certificate operation, signature or physical-device E2E was
  performed for this refactor.



Latest autonomous hardening — network failure-detail visibility (G1-02):

- `ProfileHttpResult.Failure` no longer exposes internal fallback phase/write-state
  through a public data-class property or primary constructor.
- The class retains public `Failure(ProfileHttpFailure)` and `code`, while `detail`
  and the primary constructor are internal; `EXPOSED_*` suppressions are removed.
- A rejected data-class/internal-constructor alternative was compiler-tested and
  produced the Kotlin generated-`copy()` visibility warning, so the remediation
  does not merely replace one suppression with another.
- No retry/fallback/DNS/TLS/tunnel/signing semantics changed. Fresh gates: Debug
  509/509, QA 509/509, lint/build/APKs, Python 96 with one environmental hardlink
  skip, Go test/vet/build, artifact verification and release fail-closed PASS.

Latest autonomous hardening — QA WebView debugging boundary (G1-01):

- `TrustedJuntaWebView` no longer derives Chrome DevTools exposure from the broad
  `BuildConfig.DEBUG` flag.
- `ENABLE_WEBVIEW_CONTENTS_DEBUGGING` is explicit and fail-safe: `true` only for
  the ordinary developer Debug build; QA and Release are pinned `false`.
- QA intentionally remains `android:debuggable=true` for its existing controlled
  diagnostics, but that no longer turns on application-wide WebView remote
  debugging during portal acceptance work.
- TDD policy checks bind each build block independently and reject both a missing
  override and accidental cross-block matching.
- Fresh gates: Debug 509/509, QA 509/509, lint/builds, Android artifacts, Python
  95 with one environmental hardlink skip, Go test/vet/build, and release
  fail-closed all passed. No portal/profile/TLS/signing/certificate scope changed.

Latest completed isolated PR — deterministic DNS executor test isolation:

- `HttpsProfileHttpTransport` accepts an internal `ExecutorService`; runtime
  callers still default to the unchanged process-wide bounded executor.
- The production executor remains `0..2` workers, 30-second keep-alive,
  `SynchronousQueue`, daemon threads, `AbortPolicy` and core-thread timeout.
- The initial saturation-only isolation passed early repeats but was rejected
  after a fresh QA run returned transient `NETWORK_ERROR` inside the ordinary
  non-global-address loop. This established that all JVM transport tests, not
  only saturation, had to stop sharing process executor timing.
- All 18 JVM-test transport constructions now specify a test-owned executor.
  Synchronous resolvers run inline; timeout, cancellation and saturation each
  own bounded asynchronous pools and wait for termination.
- Final evidence: exact combined focused command PASS, then Debug 5/5 and QA 5/5
  additional repeats, followed by complete 500-test Debug and QA suites.
- No production retry, queue, fallback, timeout, DNS policy or network scope
  changed.

Latest completed isolated PR — Android runtime dependency SCA:

- `app/gradle.lockfile` records 140 exact external Maven components for only
  `debugRuntimeClasspath`, `qaRuntimeClasspath` and
  `releaseRuntimeClasspath`; `LockMode.STRICT` is active only on those graphs.
- `verifyRuntimeDependencyLocks` materializes artifact views. Hostile testing
  first proved that `resolutionResult` alone accepted a stale lock, then proved
  the final task rejects `0.0.0-stale-lock` as enforced dependency locking.
- `scripts/ci/update-android-runtime-lock.sh` reproduces the lock byte-for-byte,
  accepts and removes only the exact Gradle version-catalog settings sentinel,
  and rejects every other root lockfile.
- The security workflow verifies lock state before pinned OSV-Scanner 2.3.8 and
  scans only `app/gradle.lockfile`, `tools/requirements.txt` and
  `ws024-relay/go.mod`.
- Local checksum-verified OSV under Debian/proot found 140 Android, one Python
  and one Go package and reported no known issues. Native Android execution is
  blocked by seccomp `faccessat2` before scanning.
- Version locking, artifact SHA-256 verification and vulnerability-database
  coverage remain distinct claims; OSV results do not prove absence of unknown
  or unreported vulnerabilities.

Latest F-03 progress — AEAT exact Client TLS contract:

- `aeat-mis-datos-censales` is profile version 1,
  `VERIFIED_CONTRACT / QA_ONLY`; release resolves neither its profile nor its
  source/request origins.
- The exact public transition is `Mi área personal` to queryless
  `www1.agenciatributaria.gob.es:443/wlpl/BUGC-JDIT/MdcAcceso`.
- A certificate-free TLS 1.2 probe observed `CertificateRequest` with a non-empty
  issuer list and then a safe 403 path. This proves the endpoint contract only.
- `DIRECT_FROM_SOURCE` is distinct from Carné Joven's
  `REDIRECT_AFTER_SOURCE`; legacy/subframe, wrong source, suffix host, path/query
  expansion, empty `?`, fragment and non-443 requests fail closed.
- RSA/EC are permitted by policy, issuer remains mandatory, and all existing
  validity/keyUsage/EKU/epoch/TTL checks remain unchanged.
- Public catalog status is `E2E_PENDING / IMPLEMENTED_NOT_E2E`; physical WebView
  callback and accepted read-only authentication remain mandatory before any
  release promotion.
- The exact QA APK was installed and hash-matched on 2026-07-31. Protected smoke
  resolved the profile but the certificate was locked, so no WebView callback or
  portal authentication occurred. Status remains QA-only; see the blocked E2E
  record.

Next isolated PRs:

1. Complete AEAT physical WebView/E2E validation for the exact QA-only
   `Mi área personal → Mis datos censales` Client TLS profile (F-03); keep it
   out of release unless the read-only login is accepted.
2. Remaining portal E2E and document-signing branches after local CAdES/XAdES
   validation and an authorized real administrative case (F-12).


## WS024 secure-tunnel QA status — 2026-07-29

Completed and locally verified:

- Direct-first routing retries exactly once only after a classified pre-HTTP
  failure; after-write and unknown outcomes remain fail-closed.
- Tunnel eligibility is restricted to the exact `junta-ofvirtual` / MiniApplet
  1.5 endpoint tuple and is unavailable to debug and release variants.
- Outer TLS requires TLS 1.2+, hostname verification, SPKI pins and ALPN
  `http/1.1`; inner TLS remains a separate verification boundary for
  `ws024.juntadeandalucia.es`.
- The Go relay accepts only the fixed WS024 CONNECT authority, validates a
  revocable QA credential, applies admission and pump bounds, and records only
  coarse closed audit fields. It exposes no arbitrary upstream option.
- Synthetic double-TLS integration covers success, forbidden after-write
  fallback and wrong-inner-SAN rejection. Relay output and audit remain opaque
  to the tri-phase payload.
- Debug and QA JVM suites, lint, APK build/alignment/signature checks, Go
  tests/vet/build, release fail-closed checks and forbidden-value scans pass.
- Go race instrumentation is not available in the current Android/arm64 Go
  toolchain and remains an external CI requirement, not a passed gate.

Status after physical-device E2E:

1. A direct-only QA build completed and Oficina Virtual accepted the real login
   on 2026-07-29; an external relay was not required.
2. `junta-ofvirtual` is profile version 2, `VERIFIED_E2E / ENABLED`, limited to
   the observed CAdES authentication flow.
3. The relay implementation remains experimental, QA-only and disabled. No
   credential, host or pins are present in the verified QA build or release.
4. Release remains direct-only. This decision is independent of the profile
   promotion and must not be relaxed without a separate reviewed change.
5. Go race tests still require a supported Linux CI environment.

Remaining portal work concerns document-signing/submission branches and other
profiles; the successful login does not verify those operations.


## Autonomous release-registry invariant coverage — 2026-08-04

- Runtime release eligibility already rejected sensitive non-E2E profiles; no
  production change was required.
- The prior downgrade regression test was order-dependent and asserted an unrelated
  already-ineligible profile, so it did not prove the advertised invariant.
- Coverage is now catalog-driven for `SIGN`, `SELECT_CERTIFICATE`, and
  `CLIENT_TLS_AUTH`: every non-E2E sensitive profile must be absent from release, and
  every current E2E sensitive profile is independently downgraded to prove the
  release gate closes while QA remains available.
- Current profile/catalog status did not change: RedSARA and AEAT remain QA-only /
  implemented-not-E2E; verified release profiles retain their existing evidence scope.
- Next trust-boundary leads are QA diagnostic-journal clear semantics and temporary
  signature/certificate-copy lifetime; neither is promoted to a defect without a
  reproducible contract violation.


## Autonomous QA diagnostic journal clear boundary — 2026-08-04

- The test-plan `clear elimina el journal` contract was incomplete in QA: memory
  cleared, but the app-private `qa-navigation.log` survived during the same process.
- A focused integration test reproduced the defect before production mutation.
- `SanitizedLogSink` now has a default no-op clear lifecycle hook, preserving lambda
  compatibility; `SanitizedLogger.clear()` delegates best-effort, and the QA file
  sink truncates the persisted sanitized journal.
- Release/non-QA still persists no QA journal, and no diagnostic event fields,
  allowlists, hashes, capacities, portal behavior, certificate behavior or signing
  behavior changed.
- This is logical app-journal clearing, not a claim of physical secure erasure on
  flash or erasure of system Logcat history.
- Fresh full gates passed at 510/510 Debug and 510/510 QA plus lint/build/artifact,
  release fail-closed, Python and Go gates.
- Next trust-boundary review lead: temporary final-signature/certificate byte-copy
  lifetime in local CAdES/XAdES verification and related signing code.


## Autonomous CAdES capture-buffer zeroization — 2026-08-04 (G3-01)

- The CAdES pre-sign capturer previously cleared only a `toByteArray()` copy; a JVM
  backing-buffer probe reproduced retention of the original canary after the exact
  old sequence.
- A TDD source-policy regression pins the owned-buffer invariant. The first attempted
  `close()` override was rejected by focused tests because BouncyCastle closes the
  supplied stream before the capturer consumes it.
- The final implementation follows the repository's established pattern: inherited
  `ByteArrayOutputStream.close()` semantics remain untouched and an explicit
  `clear()` zeros protected `buf` plus resets it at capturer lifecycle close.
- Cryptographic format/algorithm/provider, certificate ownership, portal profiles,
  network/TLS/WebView and release policy are unchanged. The claim is managed-heap
  best-effort zeroization only.
- Fresh gates: Debug 510/510, QA 510/510, lint 0 errors / 27 warnings per variant,
  Debug/QA/QA-AndroidTest builds, Android artifact verification, release fail-closed,
  Python 97 with one environmental hardlink skip, and Go test/vet/build all PASS.
- Continue the trust-boundary audit with XAdES/final-signature/certificate temporary
  copies; do not classify public certificate/signature copies as defects solely for
  existing briefly in memory without excess lifetime or a persistence/logging leak.

## Autonomous XAdES temporary stream zeroization — 2026-08-04 (G4-01)

- XAdES serialization and canonicalization previously returned an intentional
  `toByteArray()` result while leaving a duplicate in the ordinary stream backing
  buffer until GC; a standalone JVM canary probe reproduced that exact retention
  mechanism.
- TDD first pinned the defect with a source-policy RED. Both helpers now use an
  XAdES-local clearing stream and zero its protected backing `buf` from `finally`
  after the intended result copy is obtained; inherited close semantics are not
  overridden.
- No XAdES cryptographic/protocol bytes, profile/catalog state, network/TLS/WebView,
  certificate storage or release policy were intentionally changed. The guarantee is
  limited to app-owned managed-heap buffers.
- Fresh gates: Debug 510/510, QA 510/510, lint 0 errors / 27 warnings per variant,
  Debug/QA/QA-AndroidTest builds, Android artifact verification, release fail-closed,
  Python 98 with one environmental hardlink skip, and Go test/vet/build all PASS.
- Generated relay binary was removed. No device/app/portal/credential/certificate/
  real-signing/upload/payment/submission action occurred.
- Next autonomous priority: fresh architecture/lifecycle/concurrency/recovery audit;
  return to signing-copy lifetime only for a reproducible excess-lifetime,
  persistence or logging boundary.


Latest autonomous reconciliation — persisted certificate unlock threat model (G4-02):

- Runtime behavior was not changed. The existing bounded recovery design persists
  only the unlock password as authenticated AES-256-GCM ciphertext in
  `noBackupFilesDir`; the AES key remains non-exportable Android Keystore material,
  while PKCS#12 bytes/private-key objects are not persisted by that feature.
- `docs/threat-model.md` had a stale T5 lifecycle/process-death claim that contradicted
  intentional process-recreation and memory-pressure restoration within the original
  24-hour expiry. T5, the asset list and trust-boundary diagram now describe the
  actual retention, clearing, recovery and residual-risk boundary without implying
  that recovery bypasses per-signature confirmation.
- Documentation-policy TDD was observed RED then GREEN. Fresh Python discovery: 99
  tests, zero failures/errors, one environmental hardlink skip. Fresh Debug+QA
  lifecycle focus: three named regressions, `BUILD SUCCESSFUL`, 60/60 Gradle tasks
  executed. Mis-scoped parallel retry invocations were discarded as operator/command
  evidence, not recorded as product failures; the exact task-scoped rerun passed.
- No device/app/portal/credential/certificate/real-signing/upload/payment/submission
  action occurred. Next autonomous priority is a fresh
  architecture/lifecycle/concurrency/recovery audit.

## Autonomous stale WebView callback ownership lease — 2026-08-04 (G5-01)

- Ordinary and dedicated Client TLS WebView clients previously lacked the exact
  active-instance ownership guard already used for progress and renderer recovery.
  A released/replaced view could therefore deliver obsolete navigation or lifecycle
  callbacks into current browser state.
- Both clients now receive an active-view identity predicate from `BrowserScreen`;
  predicate failure is treated as stale. Stale navigation is consumed and stale
  UI/native callbacks are suppressed.
- Platform security responses remain fail closed: stale SSL callbacks still cancel,
  safe browsing still returns to safety, and stale Client TLS requests are ignored
  while the one-shot request handler is abandoned and cleanup is retained.
- URL/origin/path policy, DNS/TLS verification, Client TLS authorization, certificate
  selection/storage, signing, portal profiles/catalog, release eligibility and
  dependencies are unchanged.
- Fresh final evidence: Debug 513/513, QA 513/513; lint zero errors / 27 warnings per
  variant; Debug/QA/QA-AndroidTest builds; artifact and release fail-closed checks;
  Python 100 with one environmental hardlink skip; Go test/vet/build; exact-scope,
  sensitive-content and unsafe WebView/TLS scans PASS.
- APK hashes: Debug
  `ee01227e286ab371a24d326a1a414f822e7e975b80892c6e2266ba866aaf3365`, QA
  `d4eb3e09b4430e3a6a0007064577943195a1d8c9bfa02335aa33ab0ec9820dae`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No device, app, portal, credential, certificate, signature, upload, payment or
  submission action occurred. Physical AEAT F-03 and Go race remain external gates.

Next autonomous priority: continue a fresh architecture/lifecycle/concurrency audit
for stale asynchronous completions and ownership transfer; any behavior change
requires a separate design/plan and observed TDD RED.

## Autonomous global data-clear completion ownership — 2026-08-04 (G6-02)

- A delayed process-wide browser-data-clear callback could outlive its initiating
  profile and mutate current result state or reload the old profile URL on a replacement
  WebView.
- Confirmed clears now use a unique atomic completion lease bound to the initiating
  WebView; later requests supersede earlier ones, disposal invalidates ownership, and
  completion is consumed once.
- Reload occurs only when the exact initiating WebView is still active. Stale
  completion is ignored; the global deletion itself is not cancelled or falsely
  reported as rolled back.
- No deletion scope, URL/origin allowlist, WebView/TLS/Client-TLS, certificate,
  signing, portal-profile, release or dependency policy changed.
- Fresh gates: Debug 517/517, QA 517/517, lint 0 errors / 27 warnings per variant,
  Debug/QA/QA-AndroidTest builds, Android artifacts, release fail-closed, Python 100
  with one environmental hardlink skip, and Go test/vet/build all PASS. Generated
  relay binary and release APK are absent.
- Next autonomous priority: continue the architecture/lifecycle/concurrency audit for
  other delayed completions or ownership-transfer boundaries; require a separate
  design/plan and observed RED before any further behavior change.

## Autonomous WebMessageBridge compatibility-error ownership — 2026-08-04 (G7-01)

- A queued WebMessageBridge attachment-failure runnable could outlive its initiating
  WebView and publish compatibility state into a replacement profile/WebView UI.
- Deferred compatibility-error delivery now requires exact identity between the
  initiating WebView and the active `webViewRef`, matching the existing page-progress
  ownership boundary.
- Current active-WebView failures remain visible; stale released/destroyed/replaced
  WebView callbacks are ignored.
- No bridge attachment, origin, script, profile/catalog, WebView TLS, Client TLS,
  certificate, signing, release or dependency policy changed.
- Fresh gates: Debug 518/518, QA 518/518, lint 0 errors / 27 warnings per variant,
  Debug/QA/QA-AndroidTest builds, Android artifacts, release fail-closed, Python 100
  with one environmental hardlink skip, and Go test/vet/build all PASS. Generated
  relay binary and release APK are absent.
- Next autonomous priority: continue the lifecycle/concurrency audit for delayed UI
  deliveries and ownership transfer; require a separate design/plan and observed TDD
  RED before any further behavior change.

## Autonomous certificate unlock invalidation linearization — 2026-08-05 (G7-02)

- A blocked encrypted-cache store could previously finish after explicit `clear()` and
  recreate the 24-hour unlock record; the deterministic RED showed the store returning
  success after the clear.
- The ViewModel also published `CertificateSession` identity before awaiting cache
  persistence; a separate RED observed a signing identity while store remained
  suspended and UI was still unlocking.
- `EncryptedCertificateUnlockCache` now uses a monotonic atomic invalidation generation:
  clear advances it before storage deletion and every successful write validates the
  generation before becoming committed. A stale late write is deleted and reports
  failure.
- `CertificateViewModel.unlock()` persists first, checks cancellation, then publishes
  session and `Unlocked` UI without another suspension. Cache availability failure
  still does not prevent an in-memory unlock; explicit invalidation/cancellation cannot
  publish the cancelled identity.
- The intended 24-hour retention, AES-GCM/Android Keystore design, cache contents,
  signing/profile/network/WebView/release boundaries and dependency pins are unchanged.
- Fresh final gates: Debug 520/520, QA 520/520, pins and Debug/QA/QA-AndroidTest builds,
  lint 0 errors / 27 warnings per variant, Python 100 with one environmental hardlink
  skip, Android artifacts, release fail-closed and Go test/vet/build all PASS. Release
  APK and generated relay binary are absent.
- Next autonomous priority: continue a fresh architecture/lifecycle/concurrency pass for
  other asynchronous ownership or invalidation boundaries; require a separate design,
  plan and observed RED before another behavior change.

## Autonomous cancelled certificate-selection permission cleanup — 2026-08-05 (G8-01)

- A cancelled certificate selection could retain a newly acquired persistable SAF read
  permission when cancellation occurred after permission acquisition but while the reference
  store was still suspended before commit.
- Deterministic RED proved the selected reference remained absent while the new URI was never
  released; this retained unnecessary app access to the cancelled PKCS#12/PFX document.
- The `CancellationException` write path now mirrors the existing ordinary write-failure
  cleanup: a newly acquired URI differing from the previous persisted URI is released
  best-effort before the original cancellation is rethrown. Same-URI ownership is preserved.
- Successful replacement ordering, PKCS#12 parsing, password/unlock cache, certificate session,
  signing, WebView/network/TLS/profile/release/dependency policy are unchanged.
- Fresh gates PASS: Debug 521/521, QA 521/521, pins and Debug/QA/QA-AndroidTest builds, lint
  0 errors / 27 warnings per variant, Python 100 with one environmental hardlink skip, Android
  artifacts, release fail-closed and Go test/vet/build. Release APK and relay binary are absent.
- Next autonomous priority: continue the lifecycle/concurrency audit for reference selection,
  repository cancellation and other stale ownership boundaries; require a new design/plan and
  observed RED before another behavior change.

## Autonomous cancelled certificate-unlock stale-reference write barrier — 2026-08-05 (G8-02)

- A cancelled unlock could outlive blocking PKCS#12/document loading and, after that blocking
  work returned, initiate a safe-summary write using the old selected certificate reference.
- Deterministic RED used a blocked valid synthetic PKCS#12 read plus a non-suspending reference
  store and observed the stale write after cancellation.
- `CertificateRepository.unlock()` now explicitly checks coroutine activity immediately after
  blocking loading and before any successful-result reference-summary write.
- Cancellation propagation, non-cancelled success/error mapping, certificate validation,
  password/cache/session/signing, WebView/network/TLS/profile/release/dependency policy are
  otherwise unchanged; no threat-model wording change was required.
- Fresh gates PASS: Debug 522/522, QA 522/522, pins and Debug/QA/QA-AndroidTest builds, lint
  0 errors / 27 warnings per variant, Python 100 with one environmental hardlink skip, Android
  artifacts, release fail-closed and Go test/vet/build. Release APK and relay binary are absent.
- APK SHA-256: Debug
  `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`, QA
  `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Next autonomous priority: move to a fresh independent architecture/lifecycle/concurrency or
  UX/CI audit. Do not repeat G8-02; any new behavior change requires its own design/plan and RED.

## Autonomous agent-branch CI trigger coverage — 2026-08-05 (G9-01)

- Mandatory milestone pushes to `agent/**` were outside both GitHub Actions push allowlists, so a
  remotely present autonomous commit did not necessarily start ordinary CI or security workflows.
- Policy TDD observed RED against unchanged workflows, then GREEN after adding only `agent/**` to
  both explicit `push.branches` lists.
- Permissions remain `contents: read`; checkout credentials remain disabled; action SHAs, jobs,
  schedules, commands, timeouts, dependency pins and release-signing behavior are unchanged.
- Fresh gates PASS: `CiPolicyTest` 19/19; Python 101 with one environmental hardlink skip; Debug
  522/522 and QA 522/522; all three assemblies; lint 0 errors / 27 warnings per variant; Android
  artifacts; release fail-closed with zero release APK; Go test/vet/build with relay binary removed.
- Local verification establishes trigger syntax/policy and referenced gate behavior. Do not claim
  an actual GitHub-hosted run unless separately observed after push.
- Next autonomous priority: continue a fresh independent UX/accessibility, lifecycle/concurrency
  or CI/supply-chain audit. Any behavior change requires its own subordinate design/plan and RED.
## Browser notice live-region semantics — 2026-08-05 (G10-01)

- Dynamic portal/network error notices previously had no live-region semantics, so assistive
  technology was not instructed to announce a newly appearing blocking failure.
- TDD RED observed the exact absent `LiveRegion` property; the minimum fix adds only
  `LiveRegionMode.Assertive` to the existing banner container without moving focus.
- Message copy, layout, retry action/touch target, descendant semantics and all WebView/network/TLS/
  certificate/signing boundaries remain unchanged.
- Fresh gates PASS: focused Debug+QA 2/2 per variant; full Debug 523/523 and QA 523/523; three
  assemblies; lint 0 errors / 27 warnings per variant; Python 101 with one environmental hardlink
  skip; Go test/vet/build; Android artifacts; release fail-closed with zero release APK.
- Robolectric proves the semantics contract only. Physical TalkBack announcement timing remains a
  manual acceptance gate and is not promoted to automated evidence.
- Next autonomous priority: continue a fresh lifecycle/concurrency, accessibility or supply-chain
  audit. Any behavior change requires its own subordinate design/plan and observed RED.

## Autonomous WebMessage bridge release ownership — 2026-08-05 (G11-01)

- A normal WebView could be released while its `WebMessageBridgeAttachment` remained
  active because `AndroidView.onRelease` destroyed the view without closing its bridge.
  Later attachment creation could overwrite the only raw reference, retaining stale
  listener/script/pending-reply state beyond the initiating WebView lifetime.
- An atomic exact-owner lease now owns each bridge attachment. Replacement closes the
  superseded attachment; stale-owner release cannot close the current attachment; exact
  release and full disposal close once. `onRelease` performs exact-owner release before
  WebView destruction.
- Navigation, renderer-death, Client TLS and full-disposal cleanup now share that lease.
  WebMessage protocol/script, origins, TLS, certificate, signing, profiles, release and
  dependencies are unchanged.
- TDD RED was observed for both the missing lease and absent BrowserScreen integration.
  Fresh gates PASS: focused Debug+QA 16/16 per variant; full Debug 525/525 and QA
  525/525; three assemblies; lint 0 errors / 27 warnings per variant; Python 101 with
  one environmental hardlink skip; Go test/vet/build; Android artifacts; release
  fail-closed with zero release APK. Generated relay binary is absent.
- APK SHA-256: Debug
  `6bf8e4722fe865b1137a7a4498bc824b83e4413ca9b9dd4c8c8e64414703e195`, QA
  `3a263176016595ec449bbaab3ee352c7a674bf79c48f5d9f0e954efa06aa8f37`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Next autonomous priority: continue a fresh independent lifecycle/concurrency,
  accessibility or supply-chain audit. Do not repeat G11-01; any behavior change
  requires its own subordinate design/plan and observed RED.

## Autonomous stale WebView network-diagnostic ownership — 2026-08-05 (G12-01)

- `JuntaWebViewClient.shouldInterceptRequest()` previously logged a sanitized
  main-frame `NETWORK_REQUEST` even when the callback came from a released/replaced
  WebView whose other state-mutating callbacks were already ownership-gated.
- TDD RED captured the stale diagnostic exactly; the minimum fix reuses the existing
  exact active-WebView predicate and leaves the interception result `null`.
- Active request metadata, subframe behavior, SSL/Safe Browsing rejection,
  navigation/origin/TLS/certificate/signing/profile/release/dependency behavior are
  unchanged. No new trust edge was introduced; threat-model wording is unchanged.
- Fresh gates PASS: focused Debug+QA 18/18 per variant; full Debug 526/526 and QA
  526/526; three assemblies and pin/lock checks; lint 0 errors / 27 warnings per
  variant; Python 101 with one environmental hardlink skip; Go test/vet/build;
  Android artifacts; release fail-closed with zero release APK. Generated relay binary
  is absent.
- APK SHA-256: Debug
  `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`, QA
  `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Next autonomous priority: continue a fresh independent certificate/signing/storage,
  accessibility or CI/supply-chain audit. Do not repeat G12-01; any behavior change
  requires its own subordinate design/plan and observed RED.

## Autonomous Python Dependabot coverage — 2026-08-05 (G12-02)

- `tools/requirements.txt` was already an OSV-scanned supply-chain input but was the
  only explicit dependency ecosystem without matching Dependabot version-update
  coverage.
- Added exactly one `pip` Dependabot entry scoped to `/tools`, weekly Monday, PR limit
  5; existing Gradle/Go/GitHub-Actions entries are unchanged.
- Strengthened the CI policy test to fail on a missing/duplicate/mis-scoped Python
  entry. RED proved zero pip entries; focused GREEN and all 19 CI policy tests pass.
- No dependency version, requirements file, lockfile, Gradle verification metadata,
  action SHA, workflow permission, runtime code or release rule changed. This is
  update-monitoring coverage only; no hosted Dependabot run is claimed.
- Fresh gates PASS: Android Debug 526/526 and QA 526/526; three assemblies and
  pin/lock checks; lint 0 errors / unchanged 27 warnings per variant; Python 101 with
  one environmental hardlink skip; Go test/vet/build; Android artifacts; release
  fail-closed; zero release APK; generated relay binary removed.
- APK hashes remain Debug
  `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`, QA
  `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Next audit line: continue fresh architecture/lifecycle, accessibility, certificate/
  signing/storage/logging or supply-chain review; the residual
  `ProfileHttpCallPhaseTracker` override-parameter-name compiler warning is low-risk
  cleanup and must not be used to change transport semantics without a separate
  reproducible reason.

## Autonomous browser notice live-region severity — 2026-08-06 (G13-02)

- Browser notices previously inherited `LiveRegionMode.Assertive` regardless of
  severity, so Client TLS preparation and successful browser-data clears could
  interrupt assistive-technology speech like an urgent failure.
- `BrowserNoticeBanner` now keeps `Assertive` as the fail-safe default but accepts an
  explicit mode. One pure browser-state policy marks only Client TLS `CLEARING` and
  exact site/global clear success `Polite`; failures, warnings, navigation blocks and
  browser/compatibility errors remain assertive with error precedence preserved.
- This is accessibility semantics only: strings, visuals, WebView/network/TLS/Client
  TLS/certificate/signing/data-clear/profile/release/dependency behavior are unchanged.
- TDD RED and focused/full automated gates passed; Debug/QA are 528/528 JVM tests,
  lint remains 0 errors / 27 warnings per variant, and artifact/release/Python/Go
  gates passed. Physical TalkBack behavior and visual correctness remain manual gates.
- Threat-model wording is unchanged because no asset, credential flow or trust edge
  changed. Continue independent logging/privacy, lifecycle/concurrency or
  supply-chain review after remote verification of the containing commit.
