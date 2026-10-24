# ADR 0005: Non-exportable signing key in AndroidKeyStore / StrongBox

Status: accepted by the owner on 2026-10-24. The importer and the storage lifecycle are implemented; the lifecycle remains disabled and disconnected from runtime UI/TLS. Slice 2 commit/recovery decisions are in [ADR 0009](0009-keystore-key-catalog-commit.md).

Design background: PR #427 (`docs/handoffs/2026-10-24-keystore-signing-key-import.md`).

## Context

Today the PKCS#12 file and its password stay with the user. Every unlock decrypts the private key in app memory. Feature 7 of the plan moves the signing key into AndroidKeyStore so the app can use it but never read it back.

Key authorizations are fixed at import. Changing any of them later requires re-importing from the original `.p12`, which the user may no longer have. Three of them are therefore decided here, before any code depends on them.

## Decisions

1. **Gate: app-level unlock, never a prompt per signature.** The owner rejected per-sign authentication (PR #424 closed). The key is imported with `setUserAuthenticationRequired(false)`. Access stays behind the existing unlock flow, i.e. the #369 device-credential prompt and the session timeout. Consequences:
   - This blocks extraction of the key, not misuse by code already running in the app process.
   - Removing the screen lock does not destroy the key.
   - A hardware-enforced time window (an auth-bound key) was considered and declined, because it invalidates the key whenever the screen lock is removed.

2. **Digests.** IKeyMintDevice requires StrongBox to support only SHA-256 for RSA signing, while AutoFirma portals often request SHA-512.
   - StrongBox keys are authorized for `DIGEST_SHA256` and `DIGEST_NONE`. SHA-1, SHA-384 and SHA-512 are hashed in software. The chip then signs the PKCS#1 `DigestInfo` through `NONEwithRSA`, the same route Chromium uses for keystore keys. The output is byte-identical to `SHAxwithRSA`, and a unit test checks this.
   - Trusted-environment (TEE) keys are authorized for NONE, SHA-1, SHA-256, SHA-384 and SHA-512 and sign natively.

3. **Paddings: PKCS#1 v1.5 and PSS.** The same key will serve TLS client authentication through WebView, and TLS 1.3 forbids PKCS#1 v1.5 for CertificateVerify. Omitting PSS would break certificate login with no remedy short of re-import.

4. **Placement and fallback.**
   - StrongBox is tried first when `FEATURE_STRONGBOX_KEYSTORE` is present and the device runs Android 12 (API 31) or later. `KeyProtection.Builder#setIsStrongBoxBacked` does not exist before API 31, so on API 28–30 only the consented TEE import is possible.
   - If StrongBox refuses the key, or cannot sign every `SigningAlgorithm` in the post-import challenge, the importer returns `StrongBoxUnsuitable`. The trusted environment is used only when the caller passes the owner's explicit consent (`allowTrustedEnvironment`). There is no silent downgrade.
   - A key whose confirmed security level is SOFTWARE is rejected.

5. **Verification before anything is published.** After import the importer checks three things:
   - `getEncoded() == null`;
   - the security level confirmed by `KeyInfo` (`getSecurityLevel` on API 31+, `isInsideSecureHardware` on API 26–30);
   - a challenge signature, verified against the certificate's public key, for every `SigningAlgorithm`.
   
   Any failure deletes the new alias. Aliases are random, under the owned prefix `dev.junta.firmamobile.signing_key_v1.`. A later orphan sweep may delete only that prefix and never touches the rest of the keystore.

6. **RSA only.** EC stays out of scope (see PR #432). StrongBox does not promise P-384.

## Consequences and limits

- The private key cannot be backed up or moved to another phone. Reinstall, clearing app data or a factory reset loses it. The original `.p12` must be kept safely outside the app, and the UI must say so before migration.
- `android:allowBackup="false"` and the data-extraction rules stay unchanged.
- AndroidKeyStore is not available in JVM or Robolectric. CI exercises the importer through the `SigningKeyStoreBackend` seam with a synthetic opaque-key provider. **The real StrongBox/TEE import is untested** until a separately authorized on-device check.
- The owner's device reports `android.hardware.strongbox_keystore=300` (read-only `pm list features`).

## Next slices (separate PRs)

1. Done: `UnlockedIdentity.keystorePlacement` routes `LocalSignatureEngine`, `CadesSigner`, `NativeCadesEngine` (and PAdES through it) via `KeystoreRsaSigning`. The TLS paths (`ClientAuthWebViewClient`, `InteractiveClientAuthController`, `ClientTlsIdentity`) hand the key to the platform and belong to slice 3.
2. Done (storage API only, disabled): versioned alias/placement/fingerprint records, explicit source loading and verified publication, optional source-reference forgetting, tombstone deletion and owned-prefix recovery, with warning strings in all five locales. See ADR 0009 for the runtime boundary.
3. Enable keystore-backed signing and TLS. This needs a physical acceptance check that the owner authorizes separately.
