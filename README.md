# Junta Firma Mobile

> **Independent, unofficial project.** Junta Firma Mobile is not an application of, affiliated with, sponsored by, certified by, or endorsed by Junta de Andalucía or any other public administration, university, agency, or third-party service referenced by the project.

Junta Firma Mobile is an Android client and interoperability project for certificate-backed authentication and electronic-signature flows used by Spanish public-sector web services. It combines a hardened WebView, local PKCS#12 handling, narrowly scoped portal profiles and protocol adapters, plus evidence-driven compatibility cataloging.

The project is currently in **pre-publication / experimental development**. Compatibility is recorded per exact profile and operation; the repository does not claim universal support for every Spanish public portal or every operation exposed by a supported portal.

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

Use the repository Gradle Wrapper. The Android build is configured around Java 17 and current Android/Gradle tooling declared in the repository.

Typical development checks:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Release builds intentionally require private signing configuration and must not fall back to a committed debug key. See `keystore.properties.example` and `app/build.gradle.kts`.

Python inventory/catalog tooling is under `tools/`; the QA relay is under `ws024-relay/`.

## Publication candidate cutoff

The source-publication candidate is intentionally pinned to product commit `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`, the last autonomous product checkpoint with recorded Codex Cloud acceptance. The recorded broad gate for that SHA passed Debug 656/656 and QA 35/35 tests (691/691 total). Later autonomous commits were an interrupted TDD RED sequence and are not part of the publication candidate.

The immutable final branch `oss/publication-candidate-final-20260812` layers publication documentation/policy, security-gate hardening and project-origin visual-resource replacements on top of that green product cutoff. See [`docs/oss-publication-status.md`](docs/oss-publication-status.md) for the exact gate state.

## Test-only credentials

The repository contains one intentionally public synthetic PKCS#12-shaped instrumentation fixture. It belongs to a synthetic test identity, uses a public test password and must never be trusted or reused operationally. Details are in [`docs/test-fixtures.md`](docs/test-fixtures.md).

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before submitting source, compatibility evidence or assets. The contribution rules prohibit real secrets/PII, unauthorized testing of third-party systems and untraceable copied assets/source.

## Provenance and third-party material

Source/asset/data provenance is tracked in [`docs/provenance.md`](docs/provenance.md). Known third-party material retains its own license, including the bundled Bebas Neue font under SIL Open Font License 1.1.

The earlier unresolved custom WebP/launcher PNG artwork has been removed from the publication candidate and replaced with simple project-specific XML/vector resources. See [`docs/visual-asset-audit.md`](docs/visual-asset-audit.md). Their XML structure and manifest wiring have been independently rechecked; final Android resource/Gradle verification is still required before publication approval.

Third-party public-service names, domains, marks and software are referenced descriptively for interoperability. They remain subject to their respective owners' rights and licenses.

## License

Project-origin material in the final private publication candidate is licensed under the **Apache License 2.0**; see [`LICENSE`](LICENSE). Separately licensed third-party material remains under its own terms as recorded in [`NOTICE`](NOTICE), [`docs/provenance.md`](docs/provenance.md), and the license files under `docs/licenses/`.

Apache-2.0 does not grant rights in third-party trademarks, public-service names or independently licensed dependencies/assets beyond the terms applicable to those materials.

The existing author/committer email metadata has been explicitly accepted for publication. The maintainer also explicitly confirmed the five source-rights/no-unlicensed-copy statements in [`docs/maintainer-source-attestation.md`](docs/maintainer-source-attestation.md) on 2026-08-12. Neither item remains an independent publication blocker.

Exactly two hard source-publication evidence gates remain: a successful full-history/all-refs secret scan and final Android/Gradle verification of the exact immutable candidate, including the replacement visual resources and publication policy check. The commands and evidence requirements are recorded in [`docs/oss-execution-gates.md`](docs/oss-execution-gates.md).

The repository must remain private until both gates pass and the publication status in [`docs/oss-publication-status.md`](docs/oss-publication-status.md) is approved for public release.
