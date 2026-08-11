# OSS publication readiness design

**Date:** 2026-08-11
**Status:** approved for implementation on `oss/publication-readiness-20260811`

## Goal

Prepare Junta Firma Mobile for a safe public open-source release without exposing secrets or personal data, overstating government affiliation, redistributing material without a known license, or turning QA/research infrastructure into a release capability.

The repository MUST remain private until every publication gate below is closed.

## Non-goals

- Debugging GitHub Actions is not a publication prerequisite in this pass. CI can be repaired after publication readiness is otherwise established.
- No live security scanning, exploitation, destructive testing, or authenticated probing of third-party systems is authorized by this work.
- This work does not expand QA tunnel behavior into release builds.
- This work does not claim compatibility that is not supported by repository evidence.

## Gate 1 — secrets and personal data

### Current tree

- Inventory credential-like files, certificates, keys, captures, logs, environment files, signing material, relay material, tokens, cookies and personal identifiers.
- Keep intentionally synthetic test credentials only when their test-only nature and public test passphrase are explicit.
- Harden `.gitignore` against accidental future commits of real key material, captures and local credentials.
- Keep `.gitleaks.toml` allowlists narrow and explainable.

### Git history

A full-history secret scan is mandatory before public visibility. The existing security workflow already defines a full-history Gitleaks scan, but a successful run against the publication candidate is still required.

Current commit metadata contains a non-noreply personal email address while the public GitHub profile does not publish an email address. Before publication, choose one of these outcomes:

1. rewrite affected history to a GitHub noreply/approved public author identity and re-verify all rewritten refs; or
2. explicitly accept publication of the existing commit email metadata.

History rewriting is SHA-changing and MUST NOT be performed silently as an incidental documentation change.

## Gate 2 — security research and QA relay boundary

The public tree may contain interoperability research only when it is bounded to public client behavior or project-controlled infrastructure and contains no retained authentication material.

Required properties:

- release builds remain direct-only;
- release tunnel policy remains empty;
- QA tunnel configuration remains debug/QA-only and opt-in;
- relay destination remains fixed in code rather than becoming an arbitrary proxy;
- raw credentials, TLS private keys and real SPKI pins are not committed;
- protocol observations contain sanitized metadata only, not cookies, session tokens, signature payloads, private certificate material, HAR files or packet captures;
- documentation does not instruct contributors to probe systems they do not own or lack permission to test.

Any violation blocks publication.

## Gate 3 — provenance and licensing

Create a provenance ledger for material shipped or redistributed by the repository:

- project Kotlin/JavaScript/Python/Go/shell source;
- `afirma_shim.js` and any code derived from public interoperability references;
- bundled launcher/background artwork and vector assets;
- bundled fonts;
- generated public-portal data snapshots/catalogs;
- Gradle, Python and Go dependencies;
- copied standards/specification snippets, if any.

Known third-party material must retain its required notice/license. The bundled Bebas Neue font already has a SIL OFL 1.1 license file; it must be represented in the final NOTICE/provenance documentation.

Do not add a repository-wide license until the provenance review shows that the repository-owned material can actually be offered under that license without conflicting embedded-source obligations.

## Gate 4 — public OSS documentation and branding

Before publication, add and review at minimum:

- `README.md` with purpose, supported scope, build/test entry points and maturity;
- `SECURITY.md` with private vulnerability-reporting guidance and research boundaries;
- `CONTRIBUTING.md` with test, privacy and provenance requirements;
- `NOTICE` or equivalent third-party attribution/provenance file;
- a root `LICENSE` only after Gate 3 passes;
- explicit independent-project disclaimer: no affiliation with, sponsorship by, or endorsement from Junta de Andalucía or any other public administration whose public services are interoperated with;
- trademark/domain statement that third-party names and public service domains remain property of their respective owners.

The project name may describe interoperability, but presentation must not imply that this is an official government application.

## Gate 5 — pre-publication verification

The publication candidate must have evidence for all of the following:

1. current-tree credential/capture inventory reviewed;
2. successful full-history Gitleaks scan with no unresolved finding;
3. no real release signing material in Git;
4. synthetic certificate fixture documented and isolated to tests;
5. security-research/QA boundary reviewed;
6. provenance ledger complete for shipped source/assets/data;
7. public docs and disclaimer present;
8. license/notice review complete;
9. personal-email history decision resolved;
10. candidate diff reviewed against the current autonomous integration branch.

Only after all ten items are satisfied may repository visibility be changed to public.

## Branch strategy

All publication-readiness edits are isolated on `oss/publication-readiness-20260811`, created from autonomous integration commit `c1090f6431486501b46f44e1494289f5f48e9cfb`.

Autonomous development may continue independently. Before final review, synchronize the publication branch with the then-current autonomous head and repeat all tree-dependent checks. Do not merge stale publication assumptions.
