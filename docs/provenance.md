# Source, asset and data provenance ledger

**Publication status:** pre-publication review

This ledger records what is known from the repository and what still requires a maintainer decision or additional evidence before a repository-wide open-source license can be applied.

A repository commit proves that a file entered this repository at a point in time. It does **not**, by itself, prove copyright ownership or permission to relicense material. Unknown provenance therefore remains a publication blocker rather than being guessed.

## Status vocabulary

- `PROJECT_SOURCE_REVIEWED` — repository-native implementation with project-specific history and no vendored third-party source identified in the reviewed path; final copyright attestation still belongs to the maintainer.
- `PROJECT_ORIGIN_REPLACEMENT` — replacement material created specifically in this repository to remove an unresolved third-party/provenance dependency; no third-party source material identified in the replacement.
- `THIRD_PARTY_LICENSED` — bundled material has an identified license/notice in the repository.
- `GENERATED_FROM_REVIEWED_SOURCES` — output is produced by project tooling from reviewed factual/source material; source-site rights may differ and are not silently relicensed.
- `DEPENDENCY_NOT_VENDORED` — resolved at build/install time rather than copied into project source.
- `PROVENANCE_UNRESOLVED` — origin or redistribution permission is not sufficiently established. This blocks a blanket repository license/publication until resolved, replaced, or removed.

## Ledger

| Component | Repository evidence | Status | Publication treatment |
| --- | --- | --- | --- |
| Kotlin/Java project source under `app/src/**` | Incremental project history and project-specific package namespace `dev.junta.firmamobile` | `PROJECT_SOURCE_REVIEWED` | Candidate for project license after maintainer copyright/authority attestation. |
| Go relay source under `ws024-relay/**` | Project-controlled QA relay implementation with fixed-purpose design and repository history | `PROJECT_SOURCE_REVIEWED` | Candidate for project license after maintainer attestation. |
| Python tooling under `tools/**` | Project-specific generators, inventory tooling and tests | `PROJECT_SOURCE_REVIEWED` | Candidate for project license after maintainer attestation. |
| `app/src/main/res/raw/afirma_shim.js` | File first appears in project commit `95e068b` as a small JFM-specific document-start shim; the previous parent does not contain the path. Subsequent history grows it incrementally for project interoperability. Current audit has not identified a vendored upstream MiniApplet/AutoFirma source file in this path. | `PROJECT_SOURCE_REVIEWED` with attestation pending | Do **not** claim that public API/protocol behavior grants copyright. Before repository licensing, maintainer should attest that this implementation was authored for the project or replace any copied block whose origin cannot be established. Interoperability references should be cited as references, not relicensed as project code. |
| Synthetic PKCS#12 fixture `app/src/androidTest/assets/synthetic-identity.p12.b64` | Test-only identity, public test passphrase, expected holder `Persona de Prueba`; documented in `docs/test-fixtures.md` | `PROJECT_SOURCE_REVIEWED` test fixture | May remain public only as intentionally public, non-trusted test key material. Never use operationally. |
| Bebas Neue font `app/src/main/res/font/bebas_neue_regular.ttf` | `docs/licenses/BebasNeue-OFL.txt` identifies Dharma Type and SIL Open Font License 1.1 | `THIRD_PARTY_LICENSED` | Retain the OFL notice and attribution. Font remains under OFL; a project-wide license must not overwrite it. |
| Home visual background | Original unresolved `jfm_home_background.webp` entered in commit `86d644c`. Publication remediation commit `19fe276d3f62a2d6e6e427e3637877318ee18003` removes it and adds `app/src/main/res/drawable/jfm_home_background.xml`, a simple geometric vector constructed specifically for this project remediation without a third-party image source. | `PROJECT_ORIGIN_REPLACEMENT` | Original WebP is excluded from the publication candidate. Replacement is eligible for the project-source licensing decision, subject to the general maintainer rights attestation and final build/sync verification. |
| Launcher/custom visual resources | The 20 unresolved density-specific launcher PNGs identified in `docs/visual-asset-audit.md` are removed by commit `19fe276d3f62a2d6e6e427e3637877318ee18003`. Replacement `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, and `mipmap-anydpi` layer-list resources use simple project-specific geometry and preserve the existing adaptive-icon references. | `PROJECT_ORIGIN_REPLACEMENT` | Original PNG set is excluded from the publication candidate. Replacement resources are eligible for the project-source licensing decision, subject to general maintainer attestation and final Android resource/build verification. |
| Public portal runtime catalog `app/src/main/res/raw/public_portal_catalog_v1.json` | Generated by `tools/generate_public_portal_catalog.py` from reviewed local inventory/profile sources; generator does no network fetch | `GENERATED_FROM_REVIEWED_SOURCES` | The generated file may be published as project output only with source-attribution/provenance retained. Do not claim that every upstream public website/dataset is covered by the project license. |
| Portal discovery inventory/evidence | `docs/compatibility/portal-discovery-process.md` documents official enumerator sources, snapshot/evidence rules and read-only public discovery | `GENERATED_FROM_REVIEWED_SOURCES` | Treat names, URLs, classifications and observations as factual interoperability metadata. Preserve source references. Do not copy protected site content beyond what is necessary and justified for factual evidence. |
| Android/Maven runtime dependencies | Declared/locked in `gradle/libs.versions.toml` and `app/gradle.lockfile`; resolved externally by the build; family-level upstream license review is recorded in `docs/licenses/runtime-dependency-audit.md` | `DEPENDENCY_NOT_VENDORED`, reviewed for source publication | No reviewed runtime family requires the Junta Firma Mobile project source itself to be relicensed. Exact APK/AAB redistribution notices remain a separate binary-release gate. |
| Python dependency `PyYAML==6.0.3` | Declared in `tools/requirements.txt`; canonical upstream identifies MIT license | `DEPENDENCY_NOT_VENDORED` | Tooling dependency; not relicensed by project root license. Preserve upstream terms if redistributed. |
| Go module dependencies | `ws024-relay/go.mod` currently contains no third-party module requirements | `DEPENDENCY_NOT_VENDORED` | Re-evaluate if module requirements are added. |
| Gradle Wrapper files | `gradle/wrapper/gradle-wrapper.properties` selects Gradle 9.4.1; generated `gradlew` carries the upstream Apache License 2.0 header and SPDX identifier. Gradle documents the Wrapper/Build Tool as Apache-2.0 and the wrapper JAR as self-attributing. | `THIRD_PARTY_LICENSED` | Keep upstream license metadata intact. Wrapper material remains Apache-2.0 and is not relicensed by the project license. |

## Interoperability and third-party service names

References to Junta de Andalucía, AutoFirma, Cliente @firma, Spanish public administrations, universities, tax authorities, public portals, domains, products and protocols describe interoperability targets or observed public behavior. They do not imply ownership, sponsorship, endorsement, certification, partnership, or an official application.

Third-party names, marks, domains, services and their own software remain subject to their respective owners' rights and licenses.

## Open blockers before adding a root project license

1. Complete the focused source review/maintainer attestation for `afirma_shim.js` and other project-owned source so that project licensing authority is explicit.
2. Complete the final full-history secret scan and final synchronization checks on the publication candidate.
3. Run Android resource/build verification for the project-origin visual replacements on a working execution channel and repeat it after final synchronization.

The maintainer has explicitly accepted publication of the existing author/committer email metadata; a Git-history rewrite for email privacy is not required by this publication plan.

## Separate binary-release gate

A public source repository is not equivalent to an approved APK/AAB release. Before binary distribution, perform the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md`, including inspection of the final resolved dependency graph and packaged license/NOTICE metadata.

Until the source-publication blockers above are resolved, absence of a root `LICENSE` is intentional. Do not describe the repository as fully open-source or change it to public solely on the basis of this ledger.
