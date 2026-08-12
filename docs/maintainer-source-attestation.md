# Maintainer source-rights attestation

**Status:** confirmed by maintainer on 2026-08-12

This document is a publication gate for material intended to be covered by the future Junta Firma Mobile root project license. It does not attempt to transfer or relicense third-party material.

Repository history and the focused provenance review support a project-native implementation history, including incremental development of `app/src/main/res/raw/afirma_shim.js`. History alone does not prove copyright ownership or licensing authority, so the statements below required explicit confirmation by a person with authority over the repository before the root project license could be added.

## Required maintainer confirmations

On 2026-08-12, the maintainer explicitly confirmed all five statements below in the project working conversation. No legal name, handwritten signature, or identity beyond that explicit confirmation is inferred or recorded here.

- [x] I am authorized to publish and license the project-origin source maintained in this repository to the extent that copyright or other licensable rights exist in that material.
- [x] To the best of my knowledge, the project-origin Kotlin/Java, Go, Python and JavaScript implementation was created specifically for Junta Firma Mobile and was not copied from a third-party codebase without a compatible license and required attribution.
- [x] In particular, I am not aware of `app/src/main/res/raw/afirma_shim.js` containing copied implementation text from AutoFirma, Cliente @firma/MiniApplet or another third-party source that is being silently relicensed as Junta Firma Mobile code.
- [x] Third-party dependencies, fonts, protocols, names, public-service interoperability targets and other separately identified material remain under their own licenses/rights and are excluded from any claim that the root project license owns or relicenses them.
- [x] I authorize publication of the project-origin source under the project license selected after the remaining publication gates are complete.

## Visual-resource state

The earlier unresolved `jfm_home_background.webp` and 20 custom launcher PNG paths are no longer part of the publication candidate. Commit `19fe276d3f62a2d6e6e427e3637877318ee18003` removes them and substitutes project-specific XML/vector resources as recorded in `docs/visual-asset-audit.md` and `docs/provenance.md`.

Those replacement resources fall under the general project-origin/source-rights confirmation above to the extent licensable rights exist. This attestation does not claim ownership of third-party names, services, protocols or separately licensed material.

## Gates not replaced by this attestation

Publication still requires:

- final full-history secret scanning on the synchronized candidate;
- Android resource/build verification on a working execution channel;
- final synchronization with the autonomous-development head and repetition of tree-dependent checks;
- exact third-party notice verification before any APK/AAB binary distribution.

## Completion record

Maintainer confirmation recorded: **2026-08-12**.

This closes the source-rights attestation gate only. It does not by itself authorize public visibility before the remaining technical publication gates are complete.
