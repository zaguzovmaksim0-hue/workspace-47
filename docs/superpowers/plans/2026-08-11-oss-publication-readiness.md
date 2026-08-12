# OSS publication readiness implementation plan

**Date:** 2026-08-11
**Target branch:** `oss/publication-readiness-20260811`
**Design:** `docs/superpowers/specs/2026-08-11-oss-publication-readiness-design.md`

## Objective

Turn the current private repository into a publication candidate while preserving a fail-closed rule: no visibility change until secrets/PII, research boundaries, provenance, branding and licensing have all been verified.

## Task 1 — freeze the publication baseline

- Record the autonomous integration SHA used to create the publication branch.
- Keep publication work isolated from ongoing autonomous development.
- Before final review, compare/synchronize with the latest autonomous head.

## Task 2 — close current-tree secret/PII hazards

Files:
- `.gitignore`
- `.gitleaks.toml`
- `app/src/androidTest/assets/synthetic-identity.p12.b64`
- certificate test helpers
- release signing configuration
- relay/QA configuration

Actions:
1. Review all credential-like artifacts found by the recursive tree inventory.
2. Document the synthetic PKCS#12 fixture and public test passphrase as test-only.
3. Extend `.gitignore` for real PKCS#12/PFX/JKS/keystore/PEM/private-key material, one-shot QA credentials, `.env` files, HAR/pcap captures and logs.
4. Preserve only narrowly scoped Gitleaks allowlists.
5. Run/obtain a full-history Gitleaks pass before publication.

Stop condition: any unresolved real secret, private key, session token, cookie, PII capture or credential blocks publication.

## Task 3 — resolve Git-history PII

- Verify whether author/committer emails in existing history are already intentionally public.
- The current public GitHub profile does not expose an email, while commit metadata contains a personal address.
- Before publication either:
  - rewrite affected history to an approved GitHub noreply/public identity and re-verify the rewritten graph; or
  - record explicit acceptance of publishing the existing metadata.

Do not rewrite history as part of unrelated commits because it changes commit SHAs and coordination semantics.

## Task 4 — audit QA/security-research boundary

Review:
- release/debug `BuildVariantSecureTunnelRuntimeFactory.kt`;
- `SecureTunnelPolicy.kt` and tunnel runtime/protocol;
- `ws024-relay/` configuration, upstream and credential handling;
- `ProtocolObservationRecorder.kt`, `SanitizedLogger.kt`, protocol observation docs;
- E2E/research evidence documents for retained sensitive values.

Required outcome:
- release direct-only;
- QA opt-in only;
- fixed relay upstream, not arbitrary proxying;
- no real relay host/pins/credentials/private TLS material committed;
- no raw authenticated captures or user identity data retained;
- public security/research policy prohibits unauthorized testing.

## Task 5 — create provenance ledger

Review and record provenance/license status for:
- project source files;
- `app/src/main/res/raw/afirma_shim.js`;
- generated portal inventory/catalog data;
- launcher/background/vector artwork;
- `app/src/main/res/font/bebas_neue_regular.ttf` and its OFL notice;
- Gradle/Python/Go dependencies;
- any copied/derived interoperability material.

Do not select a repository-wide license until this ledger has no unresolved incompatible or unknown-origin shipped material.

## Task 6 — add public project documentation

Create/review:
- `README.md`;
- `SECURITY.md`;
- `CONTRIBUTING.md`;
- `NOTICE` / third-party provenance document;
- root `LICENSE` after Task 5 passes.

README/security docs must state that the project is independent and unofficial, not affiliated with or endorsed by any named public administration.

## Task 7 — publication-candidate verification

Evidence required:
1. recursive tree review completed;
2. full-history Gitleaks pass completed;
3. current tree has no real signing/relay/auth secrets;
4. test-only certificate fixture documented;
5. QA/research boundary verified;
6. provenance ledger complete;
7. public docs present;
8. repository-wide license legally compatible with known bundled material;
9. history-email issue resolved;
10. publication branch synchronized/reviewed against latest autonomous integration state.

## Task 8 — public release and Codex for OSS preparation

Only after Task 7:
- change repository visibility to public;
- verify public README/license/security presentation;
- verify GitHub profile/repository URLs and maintainer role;
- gather truthful maintenance/activity/ecosystem evidence;
- draft Codex for OSS form answers without inventing stars, downloads, users, personal identity fields or OpenAI organization ID.

GitHub Actions repair is deliberately outside the critical path until publication readiness is otherwise complete, but CI should be restored as follow-up OSS maintenance work.
