# Comunidad de Madrid — Registro Electrónico General — bounded evidence — 2026-08-19

Target: `ES-PUB-0012` only.

## Current official delegation

The current official Sede page
`https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid`
returned HTTP 200 on 2026-08-19 and publishes an `Acceder` link to the exact public launch
`https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm`.
The official page also states that an admitted electronic identification/signature method is required and recommends AutoFirma, but it does not publish the cryptographic signature ABI.

## Current public launch and upload router

The exact `gestiona.comunidad.madrid` launch returned HTTP 200. Its first-party HTML exposes:

- `POST InicioDistribuidorProcesa.icm`;
- `enctype=multipart/form-data`;
- file field `files`;
- hidden Ajax marker `ajax=1`;
- filename field `nombrefichero` in the modern FormData path;
- response tags consumed as `VP_ERROR`, `VP_FICHERO`, `VP_PROCEDIMIENTO`, `VP_URL_REDIRECCION`, and `VP_MOTIVO_ERROR`.

The file input has no published `accept` attribute. A bounded harmless fixture upload permitted by RUNBOOK v2.4 returned only a generic application error and did not yield a successful `VP_URL_REDIRECCION`; no redirect was followed and no administrative filing/draft/signing state was created. Therefore accepted application format remains unverified.

## Implemented boundary

Workspace-47 adds only a QA-only public-navigation profile for the exact launch above. The profile exposes no `SIGN`, `SELECT_CERTIFICATE`, `CLIENT_TLS_AUTH`, or `AFIRMA_URI` capability. The observed multipart router is documented in inventory evidence but is not exposed as a signing endpoint or automated upload capability.

No private-key document signature, final filing/registration/submission, or payment was performed. No cookies/session identifiers, certificate material, or personal data are retained here.
