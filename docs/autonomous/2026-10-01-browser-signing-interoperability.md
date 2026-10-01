# Browser and signing interoperability — first implementation slice

Base: `09dd6114c5b980d9e6ee1c6f7859d48d591f08fc`.
Branch: `fix/firmamobile-browser-interop-20261001`.

## Accepted requirement

Stop making ordinary browsing and login depend on a pre-imported, unlocked
certificate or a separate recipe for every HTTPS navigation. Repair missed
AutoScript requests and needless exact-string rejections. Keep actual key use,
TLS validation, protocol ownership and user confirmation separate from browsing.

## Implemented in this slice

- Catalog/browser are accessible without an unlocked certificate; the existing
  certificate screen has an explicit browse action. A secure certificate panel
  can be opened while keeping the ordinary browser composed.
- Valid public HTTPS navigation, including fragments and cross-profile URLs,
  is allowed without granting that destination a native signing capability.
  HTTP, unsafe authorities/schemes and untrusted native calls remain blocked.
- JavaScript dialogs are delegated to WebView's user dialogs, not automatically
  confirmed/canceled on behalf of the user.
- An Activity-owned document picker handles user-selected content URIs and
  verifies document ownership, navigation epoch, read access and result bounds.
  A canceled outstanding picker retains a tombstone until its old result arrives.
- Explicit browser/official AutoFirma handoffs have consent and error UI. Intents
  are reconstructed, not executed from arbitrary page-supplied Intent extras.
  Official AutoFirma is package-pinned. A browser handoff does not promise shared
  cookies or transfer of an existing WebView authentication session.
- MiniApplet/AutoScript are repaired after late page script replacement using
  capture-phase events and a visibility-bound 500ms guard, with no 120s cliff.
  Existing wrappers are not repeatedly nested; hidden/unloaded documents stop
  the guard and a restored page resumes it.
- Badajoz's equivalent property order, line endings and CAdES spelling are
  normalized in both JS and Kotlin. Duplicate/unknown properties, changed values
  and escape ambiguities are still refused; cryptographic parameters are not
  silently dropped or changed.

## Verification contract

`node --test scripts/tests/afirma-interoperability.test.mjs` executes the actual
application JavaScript against isolated browser primitives. Before implementation
the new behavior tests reproduced the missed hooks and semantic mismatches; after
implementation 16/16 passed. No real certificate, government account or network
connection is used by these tests.

New Android tests exercise picker ownership, safe external intents and literal
property normalization. Existing browser tests are updated only where the
intentionally changed ordinary-navigation/dialog behavior invalidated their old
expectations. Certificate granting and native bridge tests remain separate.

The authoritative build/test/lint/instrumentation gate remains GitHub Actions on
the exact candidate SHA. A successful Node run alone does not certify the APK.
No production certificate is used for verification; no live statement is signed
or submitted. The original working tree and its untracked documents are preserved.

## Explicitly not completed by this slice

- A universal native implementation of `afirma://` download/sign/upload/callback,
  server storage encryption, batch signing and tri-phase formats. External
  AutoFirma handoff is a fallback, not a claim that this native protocol exists.
- Generic consent-driven mTLS for previously unknown certificate endpoints.
- Full PDF/PAdES parser replacement or larger document budgets.
- Full popup/opener support, downloads and WebAuthn.
- Preservation of every dedicated client-auth session during backgrounding;
  the existing dedicated TLS revocation policy is retained pending integration
  tests. The normal file picker/certificate panel does not imply this is solved.

## Acceptance boundaries

Never disable SSL error cancellation, use wildcard native WebMessage origins,
export private keys or auto-confirm a document-signing operation to make an
unsupported site appear compatible. Native signing success must not be reported
until the expected result has been delivered and verified by its protocol.
