# STA responsive identify REAL E2E evidence — 2026-09-02

A protected REAL E2E batch using the real certificate was run for `diputacion-burgos-portal` and `diputacion-huesca-portal` (run `33621480354`). Both results stopped before certificate auth with `INFRASTRUCTURE_ERROR / RECIPE_TARGET_TIMEOUT` while their sanitized navigation remained on `/sta/CarpetaPublic/doEvent`.

A mobile-size headless Chromium inspection of both exact start pages reproduced the cause without clicking: the responsive `Identificate` anchor has `innerText == ""`, while its single direct `span.a-text` has textContent `Identificate`. In both portals the anchor remains visible and has the reviewed exact href, the class tokens `responsive ui-link tamano-defecto iconed`, and the exact onclick guard `if (jQuery(this).hasClass('disabled')) {return false;} else {;}`.

The dedicated E2E helper therefore matches that complete responsive DOM contract and uses the direct `span.a-text` textContent. The generic labeled-anchor helper is intentionally unchanged, so no other portal recipe gains a broader selector.
