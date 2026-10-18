# Internal software EC detached CMS candidate

Base: `dfc4744599ab7fb39e2f9da385622480fe160acb`.
Branch: `feat/software-ec-cades-engine-20261018`.

## Scope

This adds an internal, offline detached CMS engine. It is not a production signing workflow or portal compatibility claim. Supported pairs are only P-256 with SHA-256 and P-384 with SHA-384, using the existing isolated software EC raw engine. Existing RSA, shared signing algorithms, parsers, adapters, profiles, catalogs, import, TLS and runtime callers are unchanged.

The narrow DER profile contains detached data, one signer, one supplied X.509 certificate and exactly content-type, message-digest and signing-certificate-v2 attributes (ESSCertIDv2). Verification reconstructs the exact expected envelope and performs a fresh raw signature verification. Structural acceptance alone is insufficient. Signing returns output only after that verification gate. No provider retry or global provider mutation is introduced.

Owned input and certificate snapshots prevent caller mutation during the operation. Temporary owned arrays are cleared on success and failure; the successful output array belongs to the caller. JVM and provider-owned object zeroization is not guaranteed.

## Bounds

- Input: at most 524,288 bytes, including empty input.
- CMS output: at most 131,072 bytes.
- Signed attributes: at most 4,096 bytes.
- Encoded certificate: at most 65,536 bytes.
- Limit overrides can only reduce the defaults; raw DER signature limits remain 72/104 bytes.

These are application boundary limits, not a proof of a hard provider CPU or heap budget.

## Actual verification

The preceding implementation session executed `run-tests.sh` from `~/.cache/w47-ec-cades-tests-20261018/` against these source bytes: 67 focused JVM tests passed, comprising the existing raw-engine regressions and new CMS tests. Compilation used cached BC 1.85, JUnit 4.13.2 and Hamcrest 1.3 with `-Werror -jvm-target 17`; compiler diagnostics were empty. Checksum-pinned cached detekt 1.23.8 reported zero findings on the six new production files, without baseline or suppression changes.

Tests cover both positive pairs with independent CMS/JCA verification; wrong input, certificate, curve and hash; validly re-signed incorrect attributes; strict DER and malformed certificate extensions; bounds, owned snapshots, cleanup, no encoding/fallback and global provider stability.

Scratch controls performed by that session in temporary copies produced the expected failures for double hashing, profile bypass, raw verification bypass, missing cleanup and provider retry. Deliberate generated-signature corruption was rejected with the final gate present and exposed by tests when the gate was removed. The parent checked the saved results and source manifest; it did not independently rerun those scratch controls.

Evidence directory: `~/.cache/w47-ec-cades-tests-20261018/`, including `final-jvm.log`, `final-compile.log`, `detekt-final.log`, `control-manifest-final.log` and `controls-summary.log`.

## Not completed

Canonical exact-head CI, Android compilation and release verification are separate requirements. No Gradle, Android/device, emulator, instrumentation, R8 startup, portal, real-certificate, permanent-key or final-APK actions were performed for this slice. Runtime/import/TLS activation, PDF/XML/bridge/batch integration and the full EC roadmap item remain off/unchecked. This is not a general-purpose imported-CMS verifier, trust-chain validator, timestamp validator or revocation service.
