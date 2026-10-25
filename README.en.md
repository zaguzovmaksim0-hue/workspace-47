# Firma Mobile

[Español](README.md) | English

> **Independent, unofficial project.** Firma Mobile is not an application of, affiliated with, sponsored by, certified by, or endorsed by Junta de Andalucía or any other public administration, university, agency, or third-party service referenced by the project.

Firma Mobile is an Android client and interoperability project for certificate-backed authentication and electronic-signature flows used by Spanish public-sector web services. It combines a hardened WebView, local PKCS#12 handling, narrowly scoped portal profiles and protocol adapters, plus evidence-driven compatibility cataloging.

The project is experimental. Compatibility is recorded per exact profile and operation; the repository does not claim universal support for every Spanish public portal or every operation exposed by a supported portal.

## Getting started

- [Installation and first steps (Spanish)](docs/instalacion.md)
- [Version 0.2.11 changes](CHANGELOG.md)
- [Technical documentation](docs/README.md)
- [Security policy](SECURITY.md)
- [Privacy policy (Spanish)](docs/privacidad.md)
- [Contributing](CONTRIBUTING.md)

The visible name is **Firma Mobile** and the daily-use version is **0.2.11**.
The Android application ID remains `dev.junta.firmamobile` for update continuity.
The internal `optimized` build-type name is not part of the visible version.

## Certificates and privacy

Certificate and password processing stays local. Each manual unlock selects a
session lifetime of 15 minutes (the default), one hour or 24 hours. On API 28+
an encrypted cache can restore the certificate without extending that deadline.
The certificate menu's lock action ends the local session early; it does not
delete the original file, revoke the certificate or guarantee logout from an
already authenticated portal. This cache is not a claim that the private key
has been imported as a non-exportable AndroidKeyStore/StrongBox key.

Every signing operation requires confirmation. Tests use synthetic data; never
commit real certificates, passwords, cookies, private documents or authenticated
captures. See [test-fixture rules](docs/test-fixtures.md).

## Security model

Core design principles are fail-closed:

- user certificate/password handling remains local to the device;
- real release signing material is supplied outside Git;
- trusted origins and signing/network capabilities are explicit and versioned;
- arbitrary browse-only HTTPS navigation does not gain signing privileges;
- release transport remains direct-only;
- QA relay/tunnel capability is opt-in, build-variant gated and not a general-purpose proxy;
- protocol/evidence logs retain sanitized metadata rather than raw signing/session payloads;
- real credentials, authenticated captures and personal identifiers must not be committed.

See [`SECURITY.md`](SECURITY.md) and [`docs/test-fixtures.md`](docs/test-fixtures.md).

## Compatibility evidence

The compatibility model distinguishes discovery from verified runtime behavior. A public site, detected JavaScript client or protocol fingerprint does not automatically become a trusted signing profile.

Evidence and methodology live under:

- `docs/compatibility/` — inventory, discovery methodology and catalog evidence;
- `docs/e2e/` — bounded end-to-end evidence for exact tested operations;
- `docs/protocol-observations.md` — sanitized protocol observations;
- `app/src/main/res/raw/public_portal_catalog_v1.json` — generated runtime catalog.

The runtime catalog is generated from reviewed repository sources with `tools/generate_public_portal_catalog.py`; the generator itself does not fetch external URLs.

## Project navigation

See [the documentation index](docs/README.md) for current engineering guidance, compatibility records, and historical archives. Start new work from current `origin/main`; historical worktrees are not continuation bases. The [historical agent-record archive](docs/archive/agent-records/README.md) preserves the original bytes of the July task-watch log and two August handoffs, with SHA-256 records and compatibility links at their previous paths. Archived instructions and results are not current acceptance evidence.

## Build

Use the repository Gradle Wrapper. The Gradle runtime and Java source/target baseline use Java 17. Host unit-test tasks select a Java 21 toolchain, as declared in `app/build.gradle.kts`; use the repository toolchain configuration and the exact versions pinned by CI.

Typical development checks:

```bash
./gradlew testOptimizedUnitTest
./gradlew lintOptimized
./gradlew assembleOptimized
```

Release builds intentionally require private signing configuration and must not fall back to a committed debug key. See [release signing](docs/release-signing.md), `keystore.properties.example` and `app/build.gradle.kts`. Ordinary CI uses disposable test signing material, not the permanent distribution key.

Python inventory/catalog tooling is under `tools/`; the QA relay is under `ws024-relay/`.

Manual Termux build notes are retained in [`docs/building-on-termux.md`](docs/building-on-termux.md). Current broad candidate verification runs on the exact pull-request head through GitHub Actions, including Android unit/lint/APK gates; API 36 emulator instrumentation runs on the default branch nightly schedule or through an explicit manual opt-in, not on ordinary PR/push runs; see [`docs/agents/github-actions-verification.md`](docs/agents/github-actions-verification.md). Agents may use narrow local tests for development feedback, while Codex Cloud is no longer the default verification route. The former one-shot Termux publication runner is retired.

## Historical publication candidate

The candidate was the **2026-08-12 source-publication baseline**, commit
`6b5a2ab13497c6c623a223b4a951338f822ccba6` on
`oss/publication-readiness-20260811`. The [publication record](docs/oss-publication-status.md)
documents its completed gates and the repository becoming public with `main`
as its default branch. It is not a pending publication candidate or approval
for APK/AAB redistribution.

Repository work since that baseline includes:

- Firma Mobile naming, Spanish user documentation, region recovery and
  certificate/session fixes, summarized in the [changelog](CHANGELOG.md).
- Restoration of the approved Apache-2.0 license and attribution, plus a
  [dated public-distribution review](docs/public-distribution-review-2026-10-05.md)
  that separates source publication from binary-release requirements.
- [Bounded offline PDF signature verification](docs/adr/0007-offline-pdf-signature-verification.md),
  which checks supported signature integrity without validating certificate
  trust, revocation or timestamps.
- [GitHub Actions verification on each exact PR-head SHA](docs/agents/github-actions-verification.md),
  replacing the historical Termux publication gate as the current broad
  verification policy; emulator instrumentation is nightly or explicit opt-in.

The August gate counts remain historical evidence, not current-branch test
results or proof that current portals work. New changes require their own CI
evidence and [scoped compatibility records](docs/compatibility/).

## Test-only credentials

The repository contains one intentionally public synthetic PKCS#12-shaped instrumentation fixture. It belongs to a synthetic test identity, uses a public test password and must never be trusted or reused operationally. Details are in [`docs/test-fixtures.md`](docs/test-fixtures.md).

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before submitting source, compatibility evidence or assets. The contribution rules prohibit real secrets/PII, unauthorized testing of third-party systems and untraceable copied assets/source.

## Provenance and third-party material

Source/asset/data provenance is tracked in [`docs/provenance.md`](docs/provenance.md). Known third-party material retains its own license, including the bundled Bebas Neue font under SIL Open Font License 1.1.

The earlier unresolved custom WebP/launcher PNG artwork was removed and replaced with project-origin XML/vector resources; those resources passed the final Android build/resource verification. See [`docs/visual-asset-audit.md`](docs/visual-asset-audit.md).

Third-party public-service names, domains, marks and software are referenced descriptively for interoperability. They remain subject to their respective owners' rights and licenses.

## License

The current root license text is the **Apache License 2.0**. See [`LICENSE`](LICENSE), [`NOTICE`](NOTICE), and [`docs/license-selection.md`](docs/license-selection.md).

Separately licensed third-party material remains under its own terms and is not relicensed by the root license.

The existing author/committer email metadata was explicitly accepted for publication. The maintainer also explicitly confirmed the five source-rights/no-unlicensed-copy statements in [`docs/maintainer-source-attestation.md`](docs/maintainer-source-attestation.md) on 2026-08-12.

The historical source-publication record documents its completed gates. Binary APK/AAB redistribution remains a separate compliance step because exact packaged dependency/NOTICE obligations must be checked for each release artifact.
