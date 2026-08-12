# Project license selection

**Status:** provisional; do not add root `LICENSE` yet

## Recommended candidate

**Apache License 2.0** is the current preferred root license for project-origin Junta Firma Mobile source. The maintainer source-rights attestation was explicitly confirmed on 2026-08-12; the remaining prerequisites are the final full-history secret scan, Android build/resource verification and final synchronization.

## Rationale

- The project is intended for public open-source maintenance and interoperability work rather than source-available-only distribution.
- Apache-2.0 gives explicit copyright permissions and an express patent license, which is useful for a security/interoperability-oriented software project.
- A large portion of the reviewed Android/Kotlin ecosystem dependencies is already Apache-2.0; those dependencies remain separately licensed, but using Apache-2.0 for project-origin source keeps the licensing model comparatively simple.
- Reviewed MIT/BSD dependencies remain under their own licenses and do not need to be relicensed as project code.
- The source audit has not identified a vendored GPL/EUPL AutoFirma/Cliente @firma implementation inside the project-owned `afirma_shim.js` path. The maintainer explicitly confirmed the no-unlicensed-copy/source-rights attestation for this and the other project-origin source on 2026-08-12. If later evidence contradicts that attestation, this recommendation must be reopened before publication.
- The earlier unresolved WebP/launcher PNG set has been removed from the publication branch and replaced with simple project-specific XML/vector resources in commit `19fe276d3f62a2d6e6e427e3637877318ee18003`; final Android resource/build verification is still pending.

## Material not covered by the future root license

A root Apache-2.0 file must not be presented as relicensing:

- Bebas Neue, which remains under SIL OFL 1.1;
- Gradle Wrapper material, which remains under its upstream terms;
- external Maven/Python dependencies;
- third-party names, marks, services, public portals or protocols;
- any future third-party or separately licensed asset introduced after this review.

The repository `NOTICE`, `docs/provenance.md`, `docs/visual-asset-audit.md`, and `docs/licenses/runtime-dependency-audit.md` remain part of the publication record.

## Conditions before adding root `LICENSE`

1. Obtain the required final full-history secret scan on the synchronized candidate.
2. Run Android resource/Gradle verification for the project-origin visual replacements on a working execution channel.
3. Synchronize the publication branch with the final autonomous-development head and repeat tree-dependent checks.
4. Recheck that no newly introduced source or asset changes alter the licensing conclusion.

The maintainer attestation and former 21 unresolved visual binary paths are no longer open provenance conditions on the current publication branch. Any contradictory provenance evidence or reintroduction of unresolved material during synchronization reopens the relevant gate.

Until the remaining conditions pass, absence of a root `LICENSE` is deliberate.
