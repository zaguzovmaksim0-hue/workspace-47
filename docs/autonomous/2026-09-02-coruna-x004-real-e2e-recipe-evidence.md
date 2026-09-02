# Deputación da Coruña X004 — REAL E2E recipe evidence — 2026-09-02

Scope: public X004 authentication routing only. No filing data is entered, no filing is submitted, and no signing action is performed by this recipe.

The 2026-08-21 bounded evidence already recorded that the QA profile should launch from the exact X004 tramitador URL while the public catalog card remains the institutional portal. The implementation had drifted from that decision: both the profile `startUrl` and the generated catalog effective launch remained `https://www.dacoruna.gal/portada`, causing REAL E2E to stop at the institutional home page with `RECIPE_REQUIRED`.

A fresh 2026-09-02 headless Chromium inspection of the exact reviewed X004 URL confirmed the current public authentication form:

- form id/name: `formularioExternoClave`;
- method: `POST`;
- action: `/SP2/TiWorksRequest` (`https://sede.dacoruna.gal/SP2/TiWorksRequest`);
- hidden identity: `entrada=ciudadano`, `idEntidad=diputacion`, `idioma=gl`, `modo=Clave2CiudadanoAuthentication`, `tipoDeLogado=externo`, `idLogica=accesoDirecto`, `idExpediente=X004`;
- the remaining reviewed hidden fields (`detalleExpediente`, `idExpedienteOrigen`, `idVersionProceso`, `idConvocatoria`) are present exactly once and empty;
- the single auth submit is `button#acceso[name=acceso]`, visible label `Entrar con Cl@ve`, with no inline `onclick` or `formaction` override.

The recipe validates that complete form contract before clicking. After the site's own POST reaches Cl@ve `/Proxy2/ServiceProvider`, the existing shared fail-closed AFIRMA selector validates the live SAML form and chooses only the qualified-certificate branch.

Catalog binding is corrected through the existing `launch_url` mechanism: the public `entry_url` remains `https://www.dacoruna.gal/portada`, while the QA launch is the exact X004 URL. No generic direct navigation helper is introduced.
