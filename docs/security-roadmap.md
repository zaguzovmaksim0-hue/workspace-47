# Security hardening roadmap

Completed:

- Trust lifecycle and effective top-level profile binding.
- Junta tri-phase filter contract and real E2E signing.
- Fail-closed MiniApplet routing when the effective profile is missing (F-02).
- Private release signing with no debug-key fallback (F-01).
- `qa` build variant for unverified portal work.
- Release activation restricted to sensitive `VERIFIED_E2E` profiles (F-04).
- Junta marked `VERIFIED_E2E`; RedSARA, UniZAR and Carné Joven remain `QA_ONLY`.
- Browser navigation, WebMessage bridge and signing origin bound to the selected profile (F-06).
- Cross-profile and external HTTP navigation blocked (F-06, F-07).
- Renderer loss invalidates bridge/signing state and creates a fresh WebView.

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
4. Local CAdES/XAdES validation and portal E2E (F-12).
5. CI, lint, secret/dependency scanning and signer verification (F-14).
6. Catalog single source of truth and remaining maintenance work (F-15).

Open privacy item to schedule separately: keep `FLAG_SECURE` enabled throughout unlocked
certificate, browser and signing states (F-05).
