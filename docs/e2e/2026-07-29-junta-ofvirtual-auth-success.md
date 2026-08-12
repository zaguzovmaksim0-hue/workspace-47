# Junta de Andalucía — Oficina Virtual: autenticación E2E aceptada

- Fecha de la aceptación real: `2026-07-29 21:26 Europe/Madrid`
- Dispositivo: POCO F6 Pro (`24069PC21G`), Android API 36
- Profile: `junta-ofvirtual`
- Resultado: `VERIFIED_E2E_AUTHENTICATION`

## Evidencia delimitada

Una persona usuaria ejecutó manualmente el flujo real con un certificado personal
FNMT en el dispositivo físico. La aplicación completó la operación de
autenticación y el portal abrió su área interna. La pantalla observada mostró la
sección de trámites pendientes y comunicó que no existían trámites pendientes
para la cuenta autenticada.

La captura original no se incorpora al repositorio porque contiene datos
identificativos del certificado. Este informe conserva únicamente resultado y
metadatos técnicos cerrados. No se guardan contraseña, archivo PKCS#12, clave
privada, certificado, firma, challenge, cookies, contenido del formulario ni
identificadores personales.

## Contrato que quedó verificado

- entrada exacta:
  `https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs`;
- origin iniciador exacto: `https://ws072.juntadeandalucia.es`;
- API pública: `MiniApplet.sign`;
- operación: autenticación con certificado;
- algoritmo: `SHA1withRSA`, limitado a `LEGACY_SHA1` en este profile;
- formato: `CAdES` detached, modo explícito;
- endpoint PRE/POST exacto:
  `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService`;
- secuencia observada: PRE, firma local, POST, callback JavaScript, envío del
  formulario de autenticación y aceptación por el portal;
- transporte de la build verificada: direct-only, sin relay ni credencial QA.

Esta evidencia prueba el login observado. No prueba la presentación de una
solicitud administrativa, firma documental posterior, cofirma, contrafirma,
Storage/Retrieve ni todas las funciones posibles de Oficina Virtual.

## Causa raíz y correcciones

### 1. Redirect HTTP heredado bloqueado

El portal podía emitir una navegación heredada desde el origin HTTPS hacia una
URL HTTP del mismo host y contorno `/ofvirtual/`. La política anterior la
bloqueaba como navegación insegura y mostraba `Se bloqueó una navegación no
permitida`.

Commit `6538e1a` añadió una actualización cerrada a HTTPS únicamente cuando se
cumplen simultáneamente estas condiciones:

- profile activo `junta-ofvirtual`;
- host exacto `ws072.juntadeandalucia.es`;
- path exacto dentro de `/ofvirtual/`;
- página actual HTTPS del mismo profile;
- navegación top-level `GET`;
- sin userinfo ni puerto no permitido.

POST HTTP, subframes, paths próximos, otros hosts y otros profiles continúan
bloqueados.

### 2. Bloqueo de certificado en cada `onStop`

La Activity bloqueaba la identidad en memoria al pasar a segundo plano. Compose
volvía inmediatamente a la pantalla de contraseña, destruía el WebView y perdía
la sesión del portal.

Commit `26230ab` cambió el ciclo de vida:

- segundo plano y recreación de Activity dentro del mismo proceso conservan la
  identidad y el WebView;
- la sesión desbloqueada dura como máximo dos horas;
- bloqueo manual, olvidar certificado, presión crítica de memoria, expiración y
  muerte del proceso siguen eliminando la identidad de memoria;
- la contraseña no se persiste.

## Builds y commits relevantes

La aceptación real se observó con:

- HEAD: `26230abac82c791901f6c45e6dfb9b02ff62547b`;
- QA APK SHA-256:
  `6c14b2d95187b89261973a221d391f0ea469d43149e9a3bf3e1358355ca69779`.

La promoción posterior del catálogo quedó en:

- commit: `b3f1817c36324394a1816befc172340d6f5cd180`;
- profile version: `2`;
- `compatibilityStatus`: `VERIFIED_E2E`;
- `activation`: `ENABLED`;
- catálogo público: `E2E_VERIFIED / VERIFIED_E2E`;
- UI: `VALIDADO CON EL PORTAL` y `Verificado: Firma electrónica`.

La build posterior a la promoción no modifica navegación, bridge, codec,
criptografía ni transporte. Su QA APK fue instalada con datos preservados:

- QA APK e installed `base.apk` SHA-256:
  `ba82c501c4e1e4d9843dc263648d4b051ea2d9bbbbefd6f7ff451ab197b30e34`;
- `firstInstallTime` se mantuvo en `2026-07-11 20:55:37`;
- instalación: `pm install -r` → `Success`.

## Gate posterior a la promoción

- Python: 75 tests, 0 failures/errors, 1 skipped;
- `testDebugUnitTest`: 431 tests, 0 failures/errors;
- `testQaUnitTest`: 431 tests, 0 failures/errors;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Debug APK SHA-256:
  `2a5c0b45595efeafd336a11bdbc27f04bc28d669c7237f10423a679462685d47`;
- QA APK SHA-256:
  `ba82c501c4e1e4d9843dc263648d4b051ea2d9bbbbefd6f7ff451ab197b30e34`;
- QA AndroidTest APK SHA-256:
  `8a67d0ca4c32590022de4cf9728a09e32cac501ba85ff62cb71a2021dd4e250f`;
- los tres APK usan el signer SHA-256
  `b2608b0b4e334edba44177b36f4ce24ce8311b25bd8d7c617ae0a6e07e2ce768`;
- QA `BuildConfig`: tunnel `false`, relay host vacío y pins vacíos.

No se construyó un APK release de distribución: el proyecto mantiene su gate de
firma release privada y no usa fallback a la clave debug. La política release del
profile se verifica mediante tests de registry exactos.
