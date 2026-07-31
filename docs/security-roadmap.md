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
  the reviewed inventory. All seven profile bindings require exact equality of
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
- The generator reads both canonical sources and binds all seven profiles only
  by exact full-URL equality. Duplicate IDs/URLs, missing matches, multiple
  matches, collisions and unexpected profile-catalog root keys fail closed.
- Semantic comparison against the preceding generated catalog found no added or
  removed portal and no changed trust/evidence field; only the two former null
  inventory IDs and the deterministic source revision changed.
- No portal request, certificate operation, signature or physical-device E2E was
  performed for this refactor.

Next isolated PRs:

1. Stabilize the order-sensitive bounded DNS-executor unit-test teardown without
   weakening the production fail-closed policy or expanding network scope.
2. Additional Client TLS portals beyond Carné Joven only after exact runtime
   contract and physical E2E evidence (F-03).
3. Remaining portal E2E and document-signing branches after local CAdES/XAdES
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
