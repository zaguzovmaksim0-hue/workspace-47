# Project license selection

**Status:** approved for source publication

## Selected project license

**Apache License 2.0** applies to project-origin Junta Firma Mobile source and documentation unless a file or directory is explicitly identified as third-party or separately licensed.

The root `LICENSE` contains the unmodified Apache License 2.0 text. `NOTICE`, `docs/provenance.md`, `docs/visual-asset-audit.md`, and `docs/licenses/runtime-dependency-audit.md` remain part of the licensing/provenance record.

## Approval evidence

The source-publication candidate was verified in native Termux at candidate SHA `6b5a2ab13497c6c623a223b4a951338f822ccba6`, with product cutoff `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb` as an ancestor.

Mandatory publication gates passed:

- Gitleaks 8.30.1, full history/all refs: 424 commits scanned, 0 findings, exit 0;
- publication visual policy: exit 0, no legacy 21 binary visual paths returned;
- Gradle 9.4.1 / Java 17 with verified Termux AAPT2: configuration verification, Debug/QA unit tests, Debug/QA lint, Debug/QA/QA-AndroidTest assemblies all exit 0;
- `verify-android-artifacts.sh`: PASS;
- `verify-release-fail-closed.sh`: PASS;
- Python tooling tests: 113 tests, 1 skipped, 0 failures, exit 0;
- Go relay test/vet/build: PASS. Native `-race` is unsupported on Android/arm64 and remains optional supporting evidence rather than a source-publication blocker.

The working tree was clean and origin matched the verified candidate.

## Rationale

- Apache-2.0 provides explicit copyright permissions and an express patent license suitable for an interoperability/security-oriented open-source project.
- Reviewed Android/Kotlin ecosystem dependencies remain under their own upstream licenses; choosing Apache-2.0 for project-origin material does not relicense them.
- Reviewed MIT/BSD dependencies remain separately licensed.
- The source/provenance review did not identify a vendored GPL/EUPL AutoFirma/Cliente @firma implementation in project-owned source; the maintainer explicitly confirmed the source-rights/no-unlicensed-copy attestation on 2026-08-12.
- The previously unresolved WebP/launcher PNG set was removed and replaced with project-origin XML/vector resources, and the final Android resource/build gates passed.

## Material not relicensed by the root license

The root Apache-2.0 license does not relicense:

- Bebas Neue, which remains under SIL Open Font License 1.1;
- Gradle Wrapper/upstream material under its existing upstream terms;
- external Maven, Go, Python or other dependencies;
- third-party names, marks, services, public portals or protocols;
- any separately licensed third-party material identified by `NOTICE`, file headers, or `docs/licenses/`.

## Binary distribution boundary

Approval here is for **source publication**. APK/AAB distribution remains subject to the exact-artifact dependency and NOTICE review described in `docs/licenses/runtime-dependency-audit.md`.
