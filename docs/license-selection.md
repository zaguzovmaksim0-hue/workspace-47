# Project license selection

**Status:** selected and applied to the private final publication candidate; public visibility still blocked on execution evidence

## Selected project license

**Apache License 2.0** is the root license for project-origin Junta Firma Mobile material in `oss/publication-candidate-final-20260812`. The exact standard license text is present in the repository root as `LICENSE`.

The maintainer source-rights attestation was explicitly confirmed on 2026-08-12. The existing author/committer Gmail metadata was separately accepted for publication. The candidate is based on the last recorded Codex Cloud-green product SHA, `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`; later interrupted TDD RED work is intentionally excluded.

Adding the license to the private candidate does **not** authorize public visibility before the final security/build gates pass. It allows the exact content intended for publication to be verified without introducing a post-verification license commit.

## Rationale

- The project is intended for public open-source maintenance and interoperability work rather than source-available-only distribution.
- Apache-2.0 provides explicit copyright permissions and an express patent license, useful for a security/interoperability-oriented software project.
- Reviewed Apache/MIT/BSD ecosystem dependencies remain separately licensed; the root license does not silently relicense them.
- The source audit did not identify a vendored GPL/EUPL AutoFirma/Cliente @firma implementation in project-owned `afirma_shim.js`; the maintainer explicitly confirmed the source-rights/no-unlicensed-copy attestation.
- The earlier unresolved WebP/launcher PNG set was removed and replaced by simple project-specific XML/vector resources; final Android resource/build verification is still mandatory before public release.

## Material not relicensed by the root `LICENSE`

Apache-2.0 at repository root must not be represented as changing the terms applicable to:

- Bebas Neue, which remains under SIL OFL 1.1;
- Gradle Wrapper/upstream Gradle material;
- external Maven/Python/Go dependencies;
- third-party names, marks, services, public portals or protocols;
- any separately licensed third-party asset or future contribution whose own license is retained.

`NOTICE`, `docs/provenance.md`, `docs/visual-asset-audit.md`, and `docs/licenses/runtime-dependency-audit.md` are part of the publication record and clarify these boundaries.

## Remaining publication conditions

The root license is now part of the exact private candidate. Public visibility remains forbidden until the same frozen candidate SHA obtains:

1. full-history/all-refs Gitleaks PASS using the pinned/self-tested scanner; and
2. canonical Codex Cloud Android PASS, including unit tests, lint, resource/build assembly, artifacts and release fail-closed verification.

If a source/asset change alters the candidate after those checks, the relevant verification must be rerun before publication.
