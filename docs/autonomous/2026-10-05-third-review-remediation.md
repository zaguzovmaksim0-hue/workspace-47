# Third review remediation

Baseline: 715c2098485dd4e3ad9b4178663da58bdd6fe784. The user requested fixes for all five findings on 2026-10-05.

- STA batch HTTP now admits only the exact PRE/POST endpoint already validated by its fixed-host policy. MainActivity wires all five adapters to this transport; arbitrary URLs and GETDATA are not admitted.
- Batch PRE, signing and POST run outside the UI dispatcher. Ownership checks and result callbacks remain on the caller. One request-scoped cancellation reaches active HTTP; late results are disposed and input ownership is retained until the worker exits.
- Certificate lock denies key access and restore immediately. Durable cache revocation runs on IO with a visible pending state and unlock disabled until completion. The serialized persistence barrier is retained.
- Logout locks before any fallible browser cleanup, and exits after cleanup success. Failed cleanup cannot keep the local key unlocked.
- Automatic Junta session preparation no longer deletes global session cookies. Only explicit logout opts into the disclosed application-wide temporary-cookie cleanup.

Regression coverage includes all five STA host policies with actual HttpsProfileHttpTransport policy/DNS seams, dispatcher ownership and cancellation, queued durable revoke, and explicit versus automatic cookie cleanup. Existing logout source assertions now require lock-first ordering rather than the old unsafe ordering.

No E2E, real certificate use, government submission, version increment or merge. Exact-head GitHub Actions remains the broad gate before the in-place device update.
