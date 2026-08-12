# Maintainer source-rights attestation

**Status:** awaiting explicit maintainer confirmation

This document is a publication gate for material intended to be covered by the future Junta Firma Mobile root project license. It does not attempt to transfer or relicense third-party material.

Repository history and the focused provenance review support a project-native implementation history, including incremental development of `app/src/main/res/raw/afirma_shim.js`. History alone does not prove copyright ownership or licensing authority, so the statements below must be explicitly confirmed by a person with authority over the repository before the root project license is added.

## Required maintainer confirmations

The maintainer should confirm all of the following statements exactly or identify an exception that must be resolved:

- [ ] I am authorized to publish and license the project-origin source maintained in this repository to the extent that copyright or other licensable rights exist in that material.
- [ ] To the best of my knowledge, the project-origin Kotlin/Java, Go, Python and JavaScript implementation was created specifically for Junta Firma Mobile and was not copied from a third-party codebase without a compatible license and required attribution.
- [ ] In particular, I am not aware of `app/src/main/res/raw/afirma_shim.js` containing copied implementation text from AutoFirma, Cliente @firma/MiniApplet or another third-party source that is being silently relicensed as Junta Firma Mobile code.
- [ ] Third-party dependencies, fonts, protocols, names, public-service interoperability targets and other separately identified material remain under their own licenses/rights and are excluded from any claim that the root project license owns or relicenses them.
- [ ] I authorize publication of the project-origin source under the project license selected after the remaining publication gates are complete.

## Visual-resource state

The earlier unresolved `jfm_home_background.webp` and 20 custom launcher PNG paths are no longer part of the publication candidate. Commit `19fe276d3f62a2d6e6e427e3637877318ee18003` removes them and substitutes project-specific XML/vector resources as recorded in `docs/visual-asset-audit.md` and `docs/provenance.md`.

Those replacement resources fall under the general project-origin/source-rights confirmation above to the extent licensable rights exist. This attestation does not claim ownership of third-party names, services, protocols or separately licensed material.

## Gates not replaced by this attestation

Even after maintainer confirmation, publication still requires:

- final full-history secret scanning on the synchronized candidate;
- Android resource/build verification on a working execution channel;
- final synchronization with the autonomous-development head and repetition of tree-dependent checks;
- exact third-party notice verification before any APK/AAB binary distribution.

## Completion record

Do not mark this gate complete merely because this template exists. When the maintainer explicitly confirms all five statements, record the confirmation date and corresponding publication-status update here without inventing a signature or identity that was not supplied.
