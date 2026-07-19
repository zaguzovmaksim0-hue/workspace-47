# Security hardening roadmap

Completed:

- Trust lifecycle and effective top-level profile binding.
- Junta tri-phase filter contract and real E2E signing.
- Renderer recreation after render-process loss.
- Fail-closed MiniApplet routing when the effective profile is missing (F-02).

Current PR — release gating:

- Private release signing; no debug-key fallback (F-01).
- `qa` build variant for unverified portal work.
- Release activation restricted to sensitive `VERIFIED_E2E` profiles (F-04).
- Junta marked `VERIFIED_E2E`; RedSARA, UniZAR and Carné Joven remain `QA_ONLY`.

Next isolated PRs:

1. WebView profile isolation, HTTPS-only external routing and safe history policy (F-06, F-07, F-11).
2. Client TLS state machine and Carné Joven E2E (F-03, F-13).
3. Profile-scoped cookies/session transport and IPv6 handling (F-08, F-17).
4. TTL-bounded replay protection and behavioral security tests (F-09, F-10).
5. Local CAdES/XAdES validation and portal E2E (F-12).
6. CI, lint, secret/dependency scanning and signer verification (F-14).
7. Catalog single source of truth and remaining maintenance work (F-15).

Open privacy item to schedule separately: keep `FLAG_SECURE` enabled throughout unlocked
certificate, browser and signing states (F-05).
