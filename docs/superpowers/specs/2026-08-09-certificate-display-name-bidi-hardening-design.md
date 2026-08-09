# Certificate display-name bidi hardening design

## Finding hypothesis

`CertificateRepository.select()` accepts `OpenableColumns.DISPLAY_NAME` from the external
`ContentProvider`, then persists and later renders it as the selected certificate name. The
current `sanitizeDisplayName()` removes C0 controls and DEL, bounds the string and trims it, but
it preserves Unicode bidirectional control characters. An otherwise valid PKCS#12 provider can
therefore supply a name containing bidi overrides/isolates that changes visual ordering in the
trusted native certificate UI.

This is a UI-integrity/spoofing boundary. It is not evidence that certificate bytes, password,
private key or signing material are exposed.

## Scope

Production:

- `app/src/main/java/dev/junta/firmamobile/certificate/CertificateRepository.kt`

Tests:

- `app/src/test/java/dev/junta/firmamobile/certificate/CertificateRepositoryTest.kt`

Evidence after successful full verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`

No change is permitted to URI validation/persistence, MIME or extension admission, SAF permission
ownership, PKCS#12 parsing, password handling, certificate summary semantics, unlock cache/session,
signing, WebView/network/TLS, portal profiles, release eligibility or dependencies.

## Required behavior

1. Provider display names reaching `StoredCertificateReference.displayName` must contain no Unicode
   `Bidi_Control` characters.
2. Preserve ordinary printable Unicode characters and the existing 256-character bound.
3. Preserve the existing fallback display name when sanitization leaves the value blank.
4. Extension admission for `application/octet-stream` continues to use the provider's original
   trimmed filename before display sanitization. The change is presentation hardening only and must
   not broaden accepted certificate content.
5. Persisted reference state must contain the same sanitized display name returned to the UI.

## Selected approach

Extend the existing display-name predicate with a small explicit Unicode `Bidi_Control` test:

- U+061C ARABIC LETTER MARK;
- U+200E LEFT-TO-RIGHT MARK and U+200F RIGHT-TO-LEFT MARK;
- U+202A..U+202E embedding/override/pop directional formatting;
- U+2066..U+2069 isolate controls.

Remove only those controls in addition to the existing C0/DEL filtering. Do not remove all Unicode
`Cf` characters because that would also discard legitimate join controls such as ZWJ/ZWNJ and
would broaden the compatibility impact beyond the reproduced spoofing class.

## TDD strategy

Add one deterministic repository regression using official PKCS#12 MIME and a synthetic provider
name containing U+202E and U+2066. Require the returned and stored display name to be the same
plain string with those controls removed. On current production this must RED because both controls
survive `sanitizeDisplayName()`.

After the minimum production change, run the complete `CertificateRepositoryTest` in Debug+QA and
adjacent certificate reference/view-model tests, then the standard full Android/Python/Go/artifact/
release/policy gates. Automated evidence proves string-policy behavior only; no physical SAF picker
or certificate is used.
