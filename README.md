# Junta Firma Mobile

> **Independent, unofficial project.** Junta Firma Mobile is not an application of, affiliated with, sponsored by, certified by, or endorsed by Junta de Andalucía or any other public administration, university, agency, or third-party service referenced by the project.

Junta Firma Mobile is an Android client and interoperability project for certificate-backed authentication and electronic-signature flows used by Spanish public-sector web services. It combines a hardened WebView, local PKCS#12 handling, narrowly scoped portal profiles and protocol adapters, plus evidence-driven compatibility cataloging.

The project is experimental. Compatibility is recorded per exact profile and operation; the repository does not claim universal support for every Spanish public portal or every operation exposed by a supported portal.

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

## Build

Use the repository Gradle Wrapper. The Android build is configured around Java 17 and the Android/Gradle tooling declared in the repository.

Typical development checks:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Release builds intentionally require private signing configuration and must not fall back to a committed debug key. See `keystore.properties.example` and `app/build.gradle.kts`.

Python inventory/catalog tooling is under `tools/`; the QA relay is under `ws024-relay/`.

Termux-specific build instructions and the full publication gate are documented in [`docs/building-on-termux.md`](docs/building-on-termux.md) and `scripts/oss/run-termux-publication-gates.sh`.

## Verified publication candidate

The source-publication candidate is based on product commit `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`, the last autonomous product checkpoint with recorded Codex Cloud acceptance. Later autonomous TDD RED work was deliberately excluded.

Final private pre-publication verification was completed at candidate SHA `6b5a2ab13497c6c623a223b4a951338f822ccba6`:

- Gitleaks 8.30.1 scanned the full history/all refs: 424 commits, 0 findings, exit 0;
- publication visual-resource policy: PASS;
- Debug and QA unit tests: 656/656 each, no failures/errors;
- Debug/QA lint: PASS;
- Debug, QA and QA AndroidTest assemblies: PASS;
- Android artifact verification: PASS;
- release-signing fail-closed verification: PASS;
- Python policy/catalog tests: 113 tests, 1 skipped, 0 failures;
- Go relay test, vet and build: PASS. Native Go `-race` is not supported on Android/arm64 and is optional supporting evidence, not a source-publication blocker.

See [`docs/oss-publication-status.md`](docs/oss-publication-status.md) for the publication record.

## Test-only credentials

The repository contains one intentionally public synthetic PKCS#12-shaped instrumentation fixture. It belongs to a synthetic test identity, uses a public test password and must never be trusted or reused operationally. Details are in [`docs/test-fixtures.md`](docs/test-fixtures.md).

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before submitting source, compatibility evidence or assets. The contribution rules prohibit real secrets/PII, unauthorized testing of third-party systems and untraceable copied assets/source.

## Provenance and third-party material

Source/asset/data provenance is tracked in [`docs/provenance.md`](docs/provenance.md). Known third-party material retains its own license, including the bundled Bebas Neue font under SIL Open Font License 1.1.

The earlier unresolved custom WebP/launcher PNG artwork was removed and replaced with project-origin XML/vector resources; those resources passed the final Android build/resource verification. See [`docs/visual-asset-audit.md`](docs/visual-asset-audit.md).

Third-party public-service names, domains, marks and software are referenced descriptively for interoperability. They remain subject to their respective owners' rights and licenses.

## License

Project-origin source and documentation are licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE), [`NOTICE`](NOTICE), and [`docs/license-selection.md`](docs/license-selection.md).

Separately licensed third-party material remains under its own terms and is not relicensed by the repository-wide Apache-2.0 license.

The existing author/committer email metadata was explicitly accepted for publication. The maintainer also explicitly confirmed the five source-rights/no-unlicensed-copy statements in [`docs/maintainer-source-attestation.md`](docs/maintainer-source-attestation.md) on 2026-08-12.

Source-publication gates are complete. Binary APK/AAB redistribution remains a separate compliance step because exact packaged dependency/NOTICE obligations must be checked for each release artifact.
