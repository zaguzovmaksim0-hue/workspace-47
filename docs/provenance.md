# Source, asset and data provenance ledger

**Publication status:** pre-publication review

This ledger records what is known from the repository and what still requires additional technical evidence before a repository-wide open-source license can be applied.

A repository commit proves that a file entered this repository at a point in time. It does **not**, by itself, prove copyright ownership or permission to relicense material. Unknown provenance therefore remains a publication blocker rather than being guessed.

## Status vocabulary

- `PROJECT_SOURCE_REVIEWED` — repository-native implementation with project-specific history and no vendored third-party source identified in the reviewed path; maintainer source-rights attestation was explicitly confirmed on 2026-08-12.
- `PROJECT_ORIGIN_REPLACEMENT` — replacement material created specifically in this repository to remove an unresolved third-party/provenance dependency; no third-party source material identified in the replacement.
- `THIRD_PARTY_LICENSED` — bundled material has an identified license/notice in the repository.
- `GENERATED_FROM_REVIEWED_SOURCES` — output is produced by project tooling from reviewed factual/source material; source-site rights may differ and are not silently relicensed.
- `DEPENDENCY_NOT_VENDORED` — resolved at build/install time rather than copied into project source.
- `PROVENANCE_UNRESOLVED` — origin or redistribution permission is not sufficiently established. This blocks a blanket repository license/publication until resolved, replaced, or removed.

## Ledger

| Component | Repository evidence | Status | Publication treatment |
| --- | --- | --- | --- |
| Kotlin/Java project source under `app/src/**` | Incremental project history and project-specific package namespace `dev.junta.firmamobile`; maintainer source-rights/no-unlicensed-copy attestation confirmed 2026-08-12 | `PROJECT_SOURCE_REVIEWED` | Candidate for the selected project license after the remaining technical publication gates pass. |
| Go relay source under `ws024-relay/**` | Project-controlled QA relay implementation with fixed-purpose design and repository history; maintainer attestation confirmed 2026-08-12 | `PROJECT_SOURCE_REVIEWED` | Candidate for the selected project license after the remaining technical publication gates pass. |
| Python tooling under `tools/**` | Project-specific generators, inventory tooling and tests; maintainer attestation confirmed 2026-08-12 | `PROJECT_SOURCE_REVIEWED` | Candidate for the selected project license after the remaining technical publication gates pass. |
| `app/src/main/res/raw/afirma_shim.js` | File first appears in project commit `95e068b` as a small JFM-specific document-start shim; the previous parent does not contain the path. Subsequent history grows it incrementally for project interoperability. Current audit has not identified a vendored upstream MiniApplet/AutoFirma source file in this path. Maintainer explicitly confirmed on 2026-08-12 that, to the best of their knowledge, it does not contain copied implementation text being silently relicensed. | `PROJECT_SOURCE_REVIEWED` / `PASS_USER_ATTESTED` | Interoperability references remain references rather than project-owned upstream code. Reopen this conclusion if later evidence contradicts the attestation. |
| Synthetic PKCS#12 fixture `app/src/androidTest/assets/synthetic-identity.p12.b64` | Test-only identity, public test passphrase, expected holder `Persona de Prueba`; documented in `docs/test-fixtures.md` | `PROJECT_SOURCE_REVIEWED` test fixture | May remain public only as intentionally public, non-trusted test key material. Never use operationally. |
| Bebas Neue font `app/src/main/res/font/bebas_neue_regular.ttf` | `docs/licenses/BebasNeue-OFL.txt` identifies Dharma Type and SIL Open Font License 1.1 | `THIRD_PARTY_LICENSED` | Retain the OFL notice and attribution. Font remains under OFL; a project-wide license must not overwrite it. |
| Home visual background | Original unresolved `jfm_home_background.webp` entered in commit `86d644c`. Publication remediation commit `19fe276d3f62a2d6e6e427e3637877318ee18003` removes it and adds `app/src/main/res/drawable/jfm_home_background.xml`, a simple geometric vector constructed specifically for this project remediation without a third-party image source. | `PROJECT_ORIGIN_REPLACEMENT` | Original WebP is excluded from the publication candidate. Replacement falls within the maintainer project-origin attestation; final Android build/sync verification is still required. |
| Launcher/custom visual resources | The 20 unresolved density-specific launcher PNGs identified in `docs/visual-asset-audit.md` are removed by commit `19fe276d3f62a2d6e6e427e3637877318ee18003`. Replacement `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, and `mipmap-anydpi` layer-list resources use simple project-specific geometry and preserve the existing adaptive-icon references. | `PROJECT_ORIGIN_REPLACEMENT` | Original PNG set is excluded from the publication candidate. Replacement resources fall within the maintainer project-origin attestation; final Android resource/build verification remains required. |
| Public portal runtime catalog `app/src/main/res/raw/public_portal_catalog_v1.json` | Generated by `tools/generate_public_portal_catalog.py` from reviewed local inventory/profile sources; generator does no network fetch | `GENERATED_FROM_REVIEWED_SOURCES` | The generated file may be published as project output only with source-attribution/provenance retained. Do not claim that every upstream public website/dataset is covered by the project license. |
| Portal discovery inventory/evidence | `docs/compatibility/portal-discovery-process.md` documents official enumerator sources, snapshot/evidence rules and read-only public discovery | `GENERATED_FROM_REVIEWED_SOURCES` | Treat names, URLs, classifications and observations as factual interoperability metadata. Preserve source references. Do not copy protected site content beyond what is necessary and justified for factual evidence. |
| Euskadi/Izenpe client-TLS interoperability evidence | `docs/autonomous/2026-08-19-euskadi-registro-clienttls-research.md` records reviewed public endpoints and the bounded QA-only handoff contract for Registro General procedure 1017701 | `GENERATED_FROM_REVIEWED_SOURCES` | These are factual interoperability observations and sanitized public URL/field metadata, not copied Izenpe source code or private credential material. Preserve the upstream source references and QA-only boundary. |
| Tarragona/VALId client-TLS interoperability evidence | `docs/autonomous/2026-08-21-fresh22-diputacion-tarragona-client-tls.md` records reviewed public endpoints and the bounded QA-only handoff contract for Sol·licitud genèrica | `GENERATED_FROM_REVIEWED_SOURCES` | These are factual interoperability observations and sanitized public URL/field metadata, not copied AOC/VALId source code or private credential material. Preserve the upstream source references and keep signature/submission outside this capability. |
| Android/Maven runtime dependencies | Declared/locked in `gradle/libs.versions.toml` and `app/gradle.lockfile`; resolved externally by the build; family-level upstream license review is recorded in `docs/licenses/runtime-dependency-audit.md` | `DEPENDENCY_NOT_VENDORED`, reviewed for source publication | No reviewed runtime family requires the Junta Firma Mobile project source itself to be relicensed. Exact APK/AAB redistribution notices remain a separate binary-release gate. |
| Python dependency `PyYAML==6.0.3` | Declared in `tools/requirements.txt`; canonical upstream identifies MIT license | `DEPENDENCY_NOT_VENDORED` | Tooling dependency; not relicensed by project root license. Preserve upstream terms if redistributed. |
| Go module dependencies | `ws024-relay/go.mod` currently contains no third-party module requirements | `DEPENDENCY_NOT_VENDORED` | Re-evaluate if module requirements are added. |
| Gradle Wrapper files | `gradle/wrapper/gradle-wrapper.properties` selects Gradle 9.4.1; generated `gradlew` carries the upstream Apache License 2.0 header and SPDX identifier. Gradle documents the Wrapper/Build Tool as Apache-2.0 and the wrapper JAR as self-attributing. | `THIRD_PARTY_LICENSED` | Keep upstream license metadata intact. Wrapper material remains Apache-2.0 and is not relicensed by the project license. |

## Interoperability and third-party service names

References to Junta de Andalucía, AutoFirma, Cliente @firma, Spanish public administrations, universities, tax authorities, public portals, domains, products and protocols describe interoperability targets or observed public behavior. They do not imply ownership, sponsorship, endorsement, certification, partnership, or an official application.

Third-party names, marks, domains, services and their own software remain subject to their respective owners' rights and licenses.

## Open blockers before adding a root project license

1. Complete the final full-history secret scan on the final synchronized publication candidate.
2. Run Android resource/build verification for the project-origin visual replacements on a working execution channel and repeat the relevant tree-dependent checks after final synchronization.
3. Synchronize with the latest autonomous integration head and re-review newly introduced source/assets for provenance or license-impact changes.

The maintainer has explicitly accepted publication of the existing author/committer email metadata and explicitly confirmed the five source-rights/no-unlicensed-copy statements in `docs/maintainer-source-attestation.md` on 2026-08-12. Neither remains an independent publication blocker.

## Separate binary-release gate

A public source repository is not equivalent to an approved APK/AAB release. Before binary distribution, perform the exact-artifact dependency/NOTICE procedure in `docs/licenses/runtime-dependency-audit.md`, including inspection of the final resolved dependency graph and packaged license/NOTICE metadata.

Until the technical source-publication blockers above are resolved, absence of a root `LICENSE` is intentional. Do not describe the repository as fully open-source or change it to public solely on the basis of this ledger.
