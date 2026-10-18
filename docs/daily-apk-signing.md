# Persistent daily APK signing

The manually dispatched `Signed daily APK` workflow builds the existing optimized
payload, then replaces its runner debug signature with the persistent daily
identity using Android apksigner. No application behavior, Gradle signing config,
profile availability, dependency locks or R8 policy is changed.

## Trust boundary

- Only `main` dispatches may run; checkout uses the exact dispatch SHA.
- CI and security push workflows must have succeeded on that exact SHA.
- Private material is provided only to the signing step, after Gradle finishes.
- The `daily-signing` GitHub environment permits deployments only from `main`.
- Environment secrets: `JFM_DAILY_KEYSTORE_BASE64`, `JFM_DAILY_STORE_PASSWORD`,
  `JFM_DAILY_KEY_ALIAS`, `JFM_DAILY_KEY_PASSWORD`. There is no debug fallback.
- Temporary runner keystore is removed on exit and by an always-run cleanup.
- APK verification requires v2, exactly one signer, and the public certificate
  SHA-256 in `config/signing/daily-cert-sha256.txt`; a mismatch blocks upload.
- Only the verified APK, checksums, commit and public certificate report are
  uploaded. The daily key is not the user's PKCS#12 document-signing certificate.

The first key was created as RSA-4096 / SHA256withRSA for 10,000 days, under
owner-only Termux storage. The owner must keep an offline protected backup of
both the keystore and its credentials: losing them prevents ordinary updates.
Never commit or upload the private key/password as a public artifact, and never
paste credentials into chat. Repository write/admin access is security-sensitive
because trusted main workflows can use environment secrets.

Run the workflow only after exact-commit gates pass. Download `firmamobile-daily-…`
and verify `SHA256SUMS`. Keep using the same daily key for subsequent builds.
A key change is an explicit migration, not an automated recovery action.
See [installation and migration](instalacion.md).
