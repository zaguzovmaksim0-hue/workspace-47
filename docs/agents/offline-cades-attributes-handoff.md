# Offline CAdES attribute handoff

The result remains INTEGRITY_ONLY / NOT_EVALUATED. No caller or signing, import, TLS
or runtime source changes are included.

- Signed attributes are limited to contentType, messageDigest, signingCertificateV2
  and signingTime. Required attributes remain exactly once; unsigned attributes
  and countersignatures remain unsupported. Raw attribute fields are checked.
- ESS accepts one certID, no policies and no extra fields. SHA-256 may be default
  or explicit; other supported hashes must be declared CMS digests. The hash
  binds the unique SID-matched certificate DER, never just its public key.
- Optional issuerSerial requires one explicit directoryName with the exact
  certificate issuer encoding and its positive serial. Other name forms,
  multiple names and issuerUID are unsupported.
- signingTime accepts seconds and Z only: UTCTime for 1950-2049, GeneralizedTime
  for other positive four-digit years. Calendar parsing is strict. This remains
  signer-asserted metadata and never establishes timestamp trust or validity.

## Actual local implementation/test evidence

These results were obtained on the implementation snapshot based on
`065dc0860a354c71f36828b7076d16fa909ee1c9`, before merging the current parent
`feat/offline-cms-verifier-20261018` at
`acdf6e43aa3f3c1d36582fa4c1fcd0cce76f90de`.

- Cached Kotlin compilation with `-Werror -jvm-target 17`, BC 1.85 and JUnit
  4.13.2 passed with an empty compiler-diagnostics log. The combined suite
  passed **107/107** tests: 21 existing OfflineCms tests, 19 new OfflineCades
  tests and 67 existing raw/software-CAdES EC tests (16 classes in total).
  Compilation included BundledBouncyCastle and the unchanged raw EC and
  software CAdES engine production sources, not verification sources alone.
- The new integration test creates detached CMS using SoftwareEcCadesEngine
  for both supported curves, verifies the actual content, and rejects
  same-length tampered content. Synthetic ESS binding, structure and strict
  signingTime cases run in the same combined suite.
- Scoped production detekt 1.23.8 reported zero findings. The cached CLI JAR
  was checksum-verified as
  `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`.
  The repository baseline, configuration and suppressions were not changed.
- Scratch negative controls completed with exit 0 in
  `job_20261019_073801_8229bb5b`: bypassing the ESS certificate-hash comparison
  caused two expected failures among 12 focused tests; separately bypassing
  signingTime validation caused two expected failures among the same 12 tests.
  Both control runs failed as intended. All 44 original candidate source/test
  files still matched the saved SHA-256 manifest after the controls; the
  bypasses were never applied to the candidate worktree.

- Commit-time `git diff --check` and `git diff --cached --check` passed.
  Scoped staged gitleaks reported zero findings; its synthetic detector canary
  returned the expected secret-detection exit 86. No all-ref scan was run.
  The cached Android-native scanner has an unset version banner; its exact
  binary SHA-256 is recorded in `gitleaks-binary.sha256` alongside the
  redacted `precommit-gitleaks.log` and JSON report.

Evidence is retained locally under
`~/.cache/w47-offline-cades-attributes-checks-20261019`: `run-combined.sh`,
`fixed-junit.log`, `fixed/compile.log`, `detekt.xml`, `negative-hash-bypass.log`,
`negative-time-bypass.log` and `candidate-manifest.json`. Exact final-head
results after the normal parent merge are supplied separately with the
implementation handoff; the snapshot results above are not final-head CI
claims.

## Execution and publication limits

This continuation is implementation, fixes and tests only; no review or audit
was performed and no Codex runtime was used. No local Android Gradle,
bootstrap/download, device, emulator/instrumentation, E2E, portal,
real-certificate/key or APK operations were run. No push, GitHub PR or GitHub
merge was performed. Local focused checks do not replace the canonical
GitHub Actions integration and security gates on the exact published head.
The parent owns publication and those pending broad gates.
