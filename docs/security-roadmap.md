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
