# ES-PUB-0005 — Seguridad Social / AutoFirma Android handoff

Fecha de revisión: 2026-08-25.

## Alcance

Este trabajo delimita un handoff QA-only desde la Sede Electrónica de la Seguridad Social al cliente Android oficial AutoFirma. No implementa firma criptográfica dentro de Junta Firma Mobile, no cambia el `applicationId` de la aplicación y no ejecuta autenticación, selección de certificado, firma, callback autenticado ni presentación administrativa.

## Evidencia SEDESS

- Trámite público: `https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RegistroElectronicoApod/NREASS_3?changeLanguage=es`.
- El botón público `Obtener Acceso` usa `https://sede.seg-social.gob.es/wps/portal/sede/Seguridad/PortalRedirectorN3A?A=&N3=&idApp=826&idContenido=a061f401-c3ed-426e-9428-82bd9198c223&idPagina=com.ss.sede.RegistroElectronicoDeApoderamiento`.
- Una navegación HTTPS GET, sin seguir el POST de autenticación, devolvió un `302` y una cookie `com.ibm.portal.SUA_WPReqURL` cuyo retorno conserva el host `sede.seg-social.gob.es`, la ruta `/wps/myportal/sede/` y los parámetros exactos `idApp=826`, `idContenido=a061f401-c3ed-426e-9428-82bd9198c223` e `idPagina=com.ss.sede.RegistroElectronicoDeApoderamiento`. El siguiente documento público contenía un formulario `P017_login` con `method=post`; no se envió.
- El manual oficial `https://sede.seg-social.gob.es/binarios/es/1503Autofirma` explica que las aplicaciones web de la Seguridad Social invocan AutoFirma y muestra `Registro Electrónico de Apoderamientos — Apoderamiento por trámites` como ejemplo concreto del flujo.
- La página `https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RequisitosTecnicos/requisitos%2Bde%2Bfirma%2Belectronica/autofirma?changeLanguage=es` mantiene la advertencia general de que la firma AutoFirma de SEDESS no funciona en dispositivos móviles. Esta advertencia se conserva como limitación de E2E; no se interpreta como prueba de que el cliente Android AutoFirma carezca de protocolo web.

## Evidencia del cliente Android AutoFirma

El repositorio oficial `ctt-gob-es/firma-android` declara `WebSignActivity` como `BROWSABLE`/`VIEW` para el scheme `afirma` y el host `sign`. El package externo oficial es `es.gob.afirma`. El runtime de este proyecto no copia ese identificador: `applicationId` sigue siendo `dev.junta.firmamobile`.

Referencias revisadas:

- https://github.com/ctt-gob-es/firma-android/blob/master/afirma-ui-android/app/src/main/AndroidManifest.xml
- https://github.com/ctt-gob-es/firma-android/blob/master/afirma-ui-android/app/src/main/java/es/gob/afirma/android/gui/WebSignActivity.java

## Contrato implementado

El handoff solo se autoriza cuando todas las condiciones siguientes se cumplen:

1. perfil activo exacto `seguridad-social-sede-autofirma`;
2. página source HTTPS, puerto 443, host exacto `sede.seg-social.gob.es`;
3. ruta source bajo `/wps/myportal/sede/`;
4. query source contiene exactamente una vez los valores de `idApp`, `idContenido` e `idPagina` observados arriba;
5. destino es `afirma://sign` o un Android `intent://sign` cuyo package es exactamente el AutoFirma oficial;
6. `intent` no aporta component, selector ni browser fallback; antes de launch se reduce al URI `afirma://sign?...`;
7. la query AutoFirma usa solo parámetros conocidos del protocolo, exige `algorithm` + `format` y exactamente una fuente de datos (`dat` o `fileid`), sin duplicados de campos; `stservlet`, `rtservlet` y `serverurl`, si aparecen, deben ser HTTPS/443 sin userinfo ni fragment; los valores concretos de algoritmo, formato y endpoints no se fijan ni se infieren por analogía.

`MainActivity` запускает sanitized URI только через `Intent.ACTION_VIEW` с `.setPackage("es.gob.afirma")`. Если официальный AutoFirma не установлен, действие закрывается без Play Store fallback.

## Что не доказано

Публично не наблюдались точные значения `algorithm`, `format`, payload, `stservlet`, `rtservlet`, Storage/Retrieve и portal callback для `idApp=826`. Поэтому Junta Firma Mobile их не интерпретирует и не выполняет локальную `SIGN` операцию. Реальная совместимость после аутентификации остаётся `E2E_PENDING` и требует отдельного разрешения на физическую проверку.
