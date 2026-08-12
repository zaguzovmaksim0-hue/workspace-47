# Project license selection

**Status:** provisional; do not add root `LICENSE` yet

## Recommended candidate

**Apache License 2.0** is the current preferred root license for project-origin Junta Firma Mobile source, subject to completion of the source-rights attestation, custom-asset provenance resolution, final history scan and final synchronization.

## Rationale

- The project is intended for public open-source maintenance and interoperability work rather than source-available-only distribution.
- Apache-2.0 gives explicit copyright permissions and an express patent license, which is useful for a security/interoperability-oriented software project.
- A large portion of the reviewed Android/Kotlin ecosystem dependencies is already Apache-2.0; those dependencies remain separately licensed, but using Apache-2.0 for project-origin source keeps the licensing model comparatively simple.
- Reviewed MIT/BSD dependencies remain under their own licenses and do not need to be relicensed as project code.
- The source audit has not identified a vendored GPL/EUPL AutoFirma/Cliente @firma implementation inside the project-owned `afirma_shim.js` path. If later review finds copied copyleft implementation text, this recommendation must be reopened before publication.

## Material not covered by the future root license

A root Apache-2.0 file must not be presented as relicensing:

- Bebas Neue, which remains under SIL OFL 1.1;
- Gradle Wrapper material, which remains under its upstream terms;
- external Maven/Python dependencies;
- third-party names, marks, services, public portals or protocols;
- any custom visual asset whose source/redistribution rights have not been established.

The repository `NOTICE`, `docs/provenance.md`, and `docs/licenses/runtime-dependency-audit.md` remain part of the publication record.

## Conditions before adding root `LICENSE`

1. Maintainer confirms `docs/maintainer-source-attestation.md`.
2. Resolve or replace all unresolved custom visual binary assets.
3. Obtain the required final full-history secret scan on the synchronized candidate.
4. Synchronize the publication branch with the final autonomous-development head and repeat tree-dependent checks.
5. Recheck that no newly introduced source or asset changes alter the licensing conclusion.

Until these conditions pass, absence of a root `LICENSE` is deliberate.
