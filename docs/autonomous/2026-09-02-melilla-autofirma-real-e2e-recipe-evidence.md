# Melilla — AutoFirma REAL E2E entry recipe evidence — 2026-09-02

Scope: transition from the already-reviewed public procedure into its AutoFirma/authentication flow only. The recipe does not fill filing data, does not confirm a signature, and does not submit a completed administrative filing.

A fresh unauthenticated headless Chromium load of the exact QA profile URL exposed the live runtime function `submitFormulario(tramites, autofirma)`. For `submitFormulario(false, true)` the function sets `autoFirma=true`, changes `webAppPageForm.action` to `window.catser.urlauth + "/frame.jsp"`, and submits that form. The live `catser` object pins `urlauth=https://sede.melilla.es:443/sta` and `dboid=6269000018479610199999`.

Before that transition, the recipe requires the exact public procedure state:

- exact profile URL and `dboidSolicitud=6269000018479610199999`;
- `form#webAppPageForm[name=webAppPageForm]`, POST, initial action `CarpetaPublic/submitAjax.aa`;
- all 16 observed hidden inputs, including `APP_CODE=STA`, `PAGE_CODE=CATALOGO`, `autoFirma=false`, `fire=false`, and `url=Relec/TramitaForm`;
- live `catser` auth base and procedure id;
- exactly one `Con Autofirma` anchor with `href=javascript:;`, exact handler `submitFormulario(false,true);`, and the reviewed STA CSS classes.

The Melilla profile is `SIGN` but is deliberately absent from `SAFE_AUTH_SIGN_PROFILES`. Consequently REAL E2E may observe the native signing confirmation boundary, but its generic observer cancels that confirmation instead of authorizing the cryptographic signature. This recipe therefore improves mechanism-boundary coverage without enabling a real administrative signature or final submission.
