# Security hardening roadmap

Completed:

- Trust lifecycle and effective top-level profile binding.
- Junta tri-phase filter contract and real E2E signing.
- Fail-closed MiniApplet routing when the effective profile is missing (F-02).
- Private release signing with no debug-key fallback (F-01).
- `qa` build variant for unverified portal work.
- Release activation restricted to sensitive `VERIFIED_E2E` profiles (F-04).
- Junta, Carné Joven and Aragón SIRAW login marked `VERIFIED_E2E` (Carné Joven: CLIENT_TLS_AUTH verified on physical device 2026-07-21 after commit dc3c231; Aragón: login CAdES accepted on physical device 2026-07-28); RedSARA and UniZAR remain `QA_ONLY`.
- Browser navigation, WebMessage bridge and signing origin bound to the selected profile (F-06).
- Cross-profile and external HTTP navigation blocked (F-06, F-07).
- Renderer loss invalidates bridge/signing state and creates a fresh WebView.
- Aragón SIRAW login profile enabled after physical-device E2E acceptance: exact
  origin, 20-byte challenge, local detached CAdES, `SHA1withRSA`, exact
  `mode=explicit` and `filter=nonexpired`; Storage/Retrieve and document-signing
  branches remain blocked.
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

1. Client TLS state machine and Carné Joven E2E (F-03, F-13).
2. Profile-scoped cookies/session transport and IPv6 handling (F-08, F-17).
3. TTL-bounded replay protection and behavioral security tests (F-09, F-10).
4. Remaining portal E2E and document-signing branches after local CAdES/XAdES validation (F-12).
5. CI, lint, secret/dependency scanning and signer verification (F-14).
6. Catalog single source of truth and remaining maintenance work (F-15).

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

Still required before physical-device E2E:

1. Deploy a project-controlled QA relay outside the blocked path with a valid
   outer TLS certificate and reviewed SPKI pins.
2. Provision a revocable QA credential outside source control and deliver it
   through the one-shot QA credential path.
3. Build and verify a QA APK with the complete public relay tuple; the current
   validated QA APK is direct-only because no tuple was supplied.
4. Execute one manually approved Oficina Virtual login on a physical device and
   retain only sanitized route/result evidence.
5. Re-run Go race tests in a supported Linux CI environment.

Production decision remains unchanged: release is direct-only, contains no QA
credential or relay tuple, and `junta-ofvirtual` remains
`VERIFIED_CONTRACT / QA_ONLY / E2E_PENDING`.
