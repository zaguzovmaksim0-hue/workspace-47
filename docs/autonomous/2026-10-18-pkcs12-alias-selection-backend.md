# PKCS#12 explicit entry selection — backend-only slice

Source: certificate continuation audit in issue #374, comment
https://github.com/zaguzovmaksim0-hue/workspace-47/issues/374#issuecomment-6253397228.
Base: `5cb79e778aa8ffaf633d9123abb0607d8f97fe9a`.

## Scope and current behavior

This is a **partial backend API**, not an enabled multi-identity import UI.
`CertificateGateway`, `CertificateRepository`, `CertificateViewModel`, reference storage,
`CertificateSession` and the encrypted unlock cache are intentionally not changed.
The existing application path still calls `Pkcs12Loader.load`; multiple private entries
still fail with `MULTIPLE_PRIVATE_KEYS`. Single-private-entry behavior, including stores
with additional certificate-only entries, remains supported. RSA-only policy remains.
No portal, signature primitive, provider order, opaque-key handling, signing coordinator,
batch consent, catalog, permissions or compatibility status is changed.

## Backend contract

- `inspect` accepts the same bounded input and caller-owned password as `load`. It returns
  at most 32 candidate descriptors; the alias bound includes certificate-only entries.
  Only private-key entries become candidates. Preview is metadata, not proof of validity,
  supported key type, key match or suitability for signing.
- A descriptor exposes a display number, bounded sanitized certificate owner/issuer,
  validity dates and the public certificate SHA-256 fingerprint. It exposes no raw alias,
  password, private key, KeyStore or container bytes. Candidate/selection result strings
  do not print aliases or certificate metadata. Do not log descriptor fields or credentials.
- The closeable selection holds an input SHA-256 binding and a private, bounded mapping
  from issued candidate objects to aliases. Raw aliases remain process-local and are
  dropped on close/consumption, never displayed, logged or persisted. Neither password,
  PKCS#12 bytes, private key nor KeyStore is retained between calls.
- `loadSelected` requires a candidate object actually issued by that exact selection.
  Matching by number, summary, alias text or certificate display fields is insufficient.
  Foreign/null/closed/consumed choices fail with `NO_CERTIFICATE_SELECTED` before reading
  input; they never fall back to the first, sole remaining, or another valid entry.
- Every attempt consumes the entire selection, including all sibling candidates, even
  when the password, input size, parse, binding or selected-entry validation fails.
  Callers must preview again after any failed attempt. Closing a pending selection cancels it.
  A pending choice expires after five monotonic minutes; a backward/invalid elapsed interval
  fails closed as well. Reading candidates or attempting consumption discards an expired choice.
- A successful choice is bound to the exact inspected PKCS#12 bytes. Replacing or even
  reserializing the container requires a fresh preview. A changed input fails with the
  sanitized `INVALID_PASSWORD_OR_FILE` result before KeyStore parsing, not a raw provider exception.
- Only the selected private entry is opened. The same RSA, X.509, chain-length, validity,
  key-usage and private/public-key-match checks used by ordinary load apply, at load time.
  Expired, future, EC, wrong-usage, overlong-chain and mismatched selected entries fail;
  an alternative valid entry cannot rescue the selection.
- Input/password copies and PasswordProtection are cleared/destroyed as before. Cancellation
  exceptions propagate through cleanup. The loader does not close caller-owned streams or
  clear the caller-owned password; callers retain those existing responsibilities.
  JVM/provider-owned objects are not claimed to support deterministic zeroization.

## Required before enabling UI or persistent multi-entry unlock

The audit found that `StoredCertificateReference` identifies a document and summary, not
an explicitly selected private entry. Repository unlock currently reopens that document;
ViewModel/cache restoration works at that reference level. Wiring only a picker into
initial import would therefore leave later unlock/restore without the chosen identity.

- Persist a versioned, non-secret binding to the explicitly confirmed certificate identity,
  never a raw alias or selection object. On unlock, require exactly that identity and apply
  all validation again. If missing, duplicate/ambiguous or changed, stop; do not ask a cache
  restore to choose a different key or silently accept the first entry.
- Bind the encrypted cache record/authenticated reference to the confirmed identity; define
  legacy-record migration and invalidation. Preserve same-boot, monotonic lease, generation,
  revocation and exact certificate-signing-snapshot protections.
- Make import confirmation/reference installation/cache invalidation/session replacement a
  generation-checked operation. Reimport must explicitly confirm identity replacement;
  never infer consent from matching filenames, URIs, aliases, subject names or dates.
- Add a pending-choice UI state with bounded sanitized metadata, no preselected/default key,
  explicit confirm/cancel, accessibility and typed error handling. Close pending choices on
  cancellation, source/reference replacement, forget, lock, background/lifecycle invalidation
  and stale operation completion. The backend has a five-minute monotonic ceiling, checked
  lazily without a background worker; the owning operation must enforce its shorter lifecycle
  and eagerly close abandoned choices. Do not retain a password while waiting.
- Preserve `ensureActive` and reference/generation checks before writing reference/cache or
  unlocking the session. Closing a pending ticket is not a substitute for cancelling an
  already-running load or rejecting its stale completion.
- Add repository, reference-store, ViewModel, session and encrypted-cache regression tests
  for reimport, restore, cancellation, races, legacy records and selected-identity mismatch
  before exposing the new backend through the application gateway.

EC support, portal/authenticated acceptance, instrumentation and release/APK work remain
separate and are not implied by this slice.

The multi-key user workflow remains unavailable. The corresponding issue #374 roadmap
checkbox must remain unchecked until the complete selected-identity gateway/UI/cache flow
is implemented, independently reviewed and verified. Preview/import APIs do not broaden
the legacy gateway or its acceptance policy.

## Local supporting verification

Focused Kotlin/JVM compilation and JUnitCore ran only `Pkcs12AliasSelectionTest` and
`Pkcs12LoaderTest`: **35 tests passed** (19 new, 16 existing). The new suite also covers
unknown-size streams, provider-independent non-first selection, concurrency, cancellation,
cleanup, five-minute expiry and rollback. Runtime mutation checks outside the worktree
confirmed that removing the exact-input binding and forcing automatic first-entry selection
each make their targeted regression fail. No mutant was written to the worktree.

Scoped detekt 1.23.8 passed for the two production files against the unchanged repository
baseline. `git diff --check` passed. The existing private-key match primitive remains
byte-identical to the base. These are local supporting checks, not Android/Gradle, CI,
instrumentation, real-certificate, portal or compatibility acceptance evidence.
