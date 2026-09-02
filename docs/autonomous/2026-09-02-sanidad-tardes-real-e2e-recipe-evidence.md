# Ministerio de Sanidad — Tardes con Plan CLIENT_TLS_AUTH REAL E2E evidence — 2026-09-02

Scope: fresh public unauthenticated HTML/JSON/first-party JavaScript evidence. No client certificate was sent and no application was filled, signed, or submitted.

Catalog/profile: `age-ministerio-de-sanidad` / `ministerio-sanidad-certificado`.

## Current public chain

1. Profile start `https://sede.mscbs.gob.es/` exposes the exact navigation anchor `Registro electrónico` with href `/registroElectronico/home.htm`.
2. `https://sede.mscbs.gob.es/registroElectronico/home.htm` returns HTTP 200 and contains the exact anchor `índice de formularios` to `https://sede.mscbs.gob.es/registroElectronico/formularios.htm`.
3. The forms page loads first-party `/diseno/js/form_gen.js` and calls `cargarIndice("/SIGEM_RegistroTelematicoWeb/indiceForm")` into `#formularios-gen-handler`.
4. Fresh public GET of `/SIGEM_RegistroTelematicoWeb/indiceForm` returns an active entry with id `TRAM_TARDESCONPLAN`, description `Tardes con Plan`, publication interval 07/07/2026–01/10/2026, `activoEnServidor=1`, `visible=1`, and `firma=1`.
5. Current first-party `form_gen.js` renders each procedure in a panel whose DOM id equals the procedure id and creates its certificate item as `li.concertificado > a`, label `Certificado digital`, targeting `../SIGEM_AutenticacionWeb/validacionCertificado.do?REDIRECCION=RegistroTelematico&tramiteId=$o.id$&ENTIDAD_ID=000&LANG=es&COUNTRY=ES`.
6. For `TRAM_TARDESCONPLAN`, this resolves exactly to the target already pinned by the QA profile: `https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&ENTIDAD_ID=000&LANG=es&COUNTRY=ES`.

## Fail-closed recipe

The recipe follows the two exact navigation links. On the dynamic forms page it waits specifically for panel `#TRAM_TARDESCONPLAN`, requires title element `#idTRAM_TARDESCONPLAN` to start with `Tardes con Plan`, then searches only `li.concertificado a` inside that panel. It clicks only if exactly one link has normalized label `Certificado digital` and resolves to the exact profile-pinned certificate target.

It cannot select another Ministry procedure and does not interact with later form fields, uploads, signing, or final submission.
