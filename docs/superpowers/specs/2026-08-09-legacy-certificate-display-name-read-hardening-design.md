# Legacy certificate display-name read hardening design

## Finding hypothesis

G33-01 sanitizes `OpenableColumns.DISPLAY_NAME` while a new certificate is selected, but
`PreferencesCertificateReferenceStore.read()` still returns the persisted `display_name` string
verbatim. A certificate reference selected by an older vulnerable app version can therefore retain
Unicode bidi controls across an app upgrade and be restored into trusted native UI despite the new
selection-time policy.

This is a migration/read-boundary bypass of G33-01, not a new claim about certificate bytes,
passwords, private keys, signatures, PKCS#12 validity, or Android DataStore confidentiality.

## Scope

Production:

- create `app/src/main/java/dev/junta/firmamobile/certificate/CertificateDisplayNamePolicy.kt`;
- modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt` so new
  selection continues to use the same policy through the shared helper;
- modify `app/src/main/java/dev/junta/firmamobile/certificate/CertificateReferenceStore.kt` so a
  persisted reference is sanitized when read.

Tests:

- modify `app/src/test/java/dev/junta/firmamobile/certificate/CertificateReferenceStoreTest.kt`.

Evidence after successful full verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`;
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`;
- `docs/security-roadmap.md`;
- `docs/test-plan.md`;
- `docs/test-report.md`;
- `docs/threat-model.md`.

No change is permitted to URI parsing/validation, MIME or extension admission, size or certificate
summary semantics, SAF permission ownership, PKCS#12 loading, certificate validity/key checks,
password/unlock cache/session behavior, signing, WebView/network/TLS, portal profiles, release
eligibility, dependencies, or backup policy.

## Required behavior

1. `PreferencesCertificateReferenceStore.read()` must never return a certificate `displayName`
   containing the Unicode `Bidi_Control` code points already closed by G33-01: U+061C,
   U+200E..U+200F, U+202A..U+202E, U+2066..U+2069.
2. The read path must also preserve the existing G33 rules for C0 controls, DEL, ordinary printable
   Unicode, the 256-character presentation bound, trimming, and the default display-name fallback.
3. New selection must remain behaviorally identical to G33-01 and use the same policy implementation
   so read-time and selection-time normalization cannot drift.
4. The read path must be side-effect free. It must not rewrite DataStore merely because a legacy
   value was normalized; this avoids introducing a new storage failure mode during restore.
5. If a legacy reference is later successfully unlocked, the repository's existing summary write may
   naturally persist the already-normalized reference returned by the store.
6. Octet-stream `.p12`/`.pfx` admission must continue to inspect the provider's original trimmed
   filename before presentation sanitization.

## Selected approach

Extract the exact G33 display-name predicate and fallback into an internal pure
`CertificateDisplayNamePolicy`. Use it in both `CertificateRepository.select()` and
`PreferencesCertificateReferenceStore.read()`.

This is preferred over repository-only read wrapping because every production consumer of
`CertificateReferenceStore` then receives the same safe presentation metadata, while the storage
read remains free of migration writes. It is preferred over duplicating the G33 predicate in the
store because duplicate Unicode ranges and length/fallback rules could drift.

## TDD strategy

At the public store seam, seed the test DataStore directly with a syntactically complete legacy
reference whose `display_name` contains U+202E and U+2066. Call
`PreferencesCertificateReferenceStore.read()` and require the returned name to equal the same plain
text with the controls removed. Current production must RED because `read()` assigns the persisted
string verbatim.

After the minimum shared-policy change, run the store regression in Debug+QA, the existing G33
selection regression and adjacent certificate repository/view-model suites, then the standard full
Android/Python/Go/artifact/release/policy gates. Automated evidence proves the DataStore/string
migration boundary only; it does not claim physical picker, certificate, device, or portal E2E
validation.
