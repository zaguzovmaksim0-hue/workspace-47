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
- Identical in-flight MiniApplet signing calls are coalesced without invoking the
  portal error callback; any differing concurrent request remains fail-closed.

Current isolated PR — WebView session-state hardening:

- Remove raw `WebView.saveState()` / `restoreState()` history from Activity bundles (F-11).
- Explicitly discard the legacy `junta_webview_history` saved-state key.
- Start every recreated WebView from a catalog-selected URL revalidated against its profile.
- Never persist dedicated Client TLS WebView state.
- Route renderer death with the exact affected WebView and abandon one-shot Client TLS grants.
- Recreate a bridge-free, history-free WebView after renderer termination.
- Unit and device tests cover process-state sanitization and renderer-session invalidation.

Next isolated PRs:

1. Remaining Client TLS portals and grant-lifecycle hardening beyond the already
   verified Carné Joven scenario (F-03, F-13).
2. Profile-scoped cookies/session transport and IPv6 handling (F-08, F-17).
3. TTL-bounded replay protection and behavioral security tests (F-09, F-10).
4. Remaining portal E2E and document-signing branches after local CAdES/XAdES validation (F-12).
5. CI, lint, secret/dependency scanning and signer verification (F-14).
6. Remaining catalog-generation deduplication after the completed E2E consistency
   gate (F-15B).

Open privacy item to schedule separately: keep `FLAG_SECURE` enabled throughout unlocked
certificate, browser and signing states (F-05).

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
