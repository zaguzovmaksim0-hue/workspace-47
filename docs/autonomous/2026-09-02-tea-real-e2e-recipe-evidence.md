# TEA Alegaciones CLIENT_TLS_AUTH REAL E2E recipe evidence — 2026-09-02

Scope: fresh public unauthenticated DOM evidence only. No client certificate was sent and no allegation was filed or submitted.

Catalog/profile: `age-sede-electronica-de-los-tribunales-economico-administrativos-tea` / `tea-alegaciones-certificado`.

The reviewed start URL is also the profile's CLIENT_TLS source:

`https://sede.tea.hacienda.gob.es/TEA/alegaciones.html`

Fresh HTTP 200 DOM contains two anchors whose visible text is `Alegaciones`, but only one has the reviewed certificate-authentication target:

`https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0`

That exact target occurs once in the document and carries the transaction-link class `js-componente-enlace-tramite`; the other `Alegaciones` anchor is only the current navigation-tab link back to `/TEA/alegaciones.html`.

The REAL E2E recipe therefore matches both the exact label and the exact full target URL before clicking. The existing QA profile independently constrains the resulting client-certificate request to origin `https://www1.tea.hacienda.gob.es`, path `/wlpl/TEAC-TRAM/SedeTRAM`, and fixed query `tram=0`.

The recipe performs no later allegation-content entry, filing, or submission action.
