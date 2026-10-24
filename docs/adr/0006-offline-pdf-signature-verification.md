# 0006 — Bounded offline PDF signature verification

**Date:** 2026-10-24
**Status:** Accepted

## Context

The existing read-only SAF CMS dialog and session journal need PDF/PAdES integrity checks without new dependencies, document persistence or network access. The signing engine already authenticates PDF approval history, exact Contents gaps, ByteRange coverage, certificate matching and historical dictionaries. Its signing policy deliberately rejects unsigned trailing revisions.

## Decision

Reuse PDFBox, NativePdfSignatureHistory and NativePdfRevisionRange. Add a separate read-only history inspection entrypoint that permits trailing unsigned bytes, preserves historical dictionary binding, and leaves signing inspection strict. Verify each supported embedded CMS with the matched certificate public key through the bundled BouncyCastle provider; certificate validity, trust, revocation and timestamps are not evaluated. Surface claimed dates and bounded certificate subjects only in transient secure dialog state. Report each revision independently; unsigned trailing changes prevent a successful current-document outcome. Journal only the existing typed terminal outcome, never document/certificate metadata.

Keep the existing maximum PDF size of 2 MiB, eight signatures and RSA Adobe/ETSI approval profiles. The generic SAF selection accepts up to 2 MiB; CMS verification still enforces its 1 MiB limit and optional original remains limited to 512 KiB. Do not treat unrecognized PDF profiles as verified. XML/XAdES remains unsupported: the existing generated-profile self-check is not a generic incoming signature validator.

## Consequences

No dependency, network, permission or signing-policy changes. Historical revisions are reopened under existing parser budgets to detect hidden or rebound signatures. Parsing and crypto remain synchronous inside the existing process-wide worker gate; cancellation discards stale results but cannot preempt a blocking parser. The journal retains its existing metadata schema and session lifetime. A valid older signature does not establish integrity of a later unsigned revision.

## Alternatives

1. Build a separate general PDF/PAdES verifier. This would duplicate security-sensitive range/history checks and require substantially broader hostile-input testing.
2. Invoke strict signing-history inspection unchanged. This would collapse valid signatures with unsigned trailing changes into one opaque failure and could not display their individual results.
3. Use an isolated parser service. This gives a hard termination boundary but requires a new lifecycle/IPC design beyond this bounded feature.

## Payoff trigger

Extend the reviewed verifier when additional PDF signature profiles or a hard parser deadline are required; implement incoming XAdES only with reviewed profile/reference discovery and wrapping defenses.
