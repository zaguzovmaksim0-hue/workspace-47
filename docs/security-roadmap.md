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
  pinned Gitleaks; Dependabot covers Gradle, Go modules and Actions; Android APKs
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
- Dependabot covers Gradle, Go modules and GitHub Actions. Go is pinned to the
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
