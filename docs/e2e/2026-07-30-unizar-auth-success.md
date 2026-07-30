# Universidad de Zaragoza — autenticación E2E aceptada

- Fecha de la aceptación real: `2026-07-30 10:51 Europe/Madrid`
- Dispositivo: Android API 36, dispositivo físico
- Profile durante la aceptación: `unizar-tramitador`, versión 1
- Adapter: `unizar-autoscript-triphase-cades-v1`
- QA APK SHA-256:
  `190115079eba9c942db9e1fa3a20b4119eac445fef9406c90c4254729cc5fc7f`
- Resultado: `VERIFIED_E2E_AUTHENTICATION`

## Evidencia delimitada

La persona usuaria abrió manualmente el profile exacto de la Universidad de
Zaragoza con un certificado personal ya desbloqueado en el dispositivo. La
aplicación procesó únicamente el flujo de autenticación observado:

1. origin exacto `https://tramita.unizar.es`;
2. challenge de sesión precalculado de 20 bytes;
3. `SHA1withRSA`, `CAdES`, detached;
4. propiedades exactas `precalculatedHashAlgorithm=SHA1` y `serverUrl`;
5. PRE contra el único `SignatureService` permitido;
6. firma RSA local tras confirmación nativa;
7. POST y callback AutoScript;
8. entrega del resultado a la página;
9. aceptación por el portal y apertura del área interna autenticada.

La pantalla final observada mostraba el `Buzón Electrónico`, el bloque
`Mis Gestiones` y contadores internos de expedientes. No permaneció en la
pantalla pública de acceso y no apareció un error de firma. Esto demuestra que
el portal aceptó la autenticación; no se infiere el resultado únicamente del
mensaje Android `Firma enviada al portal`.

## Límite de la afirmación

La verificación se limita al login CAdES descrito. No se ejecutaron ni se
habilitan por esta evidencia:

- `StorageService` o `RetrieveService`;
- cofirma o contrafirma;
- firma documental;
- presentación, registro o envío administrativo;
- `afirma://` genérico;
- otro origin, endpoint, formato o algoritmo.

La comprobación terminó en el buzón interno sin iniciar ni modificar un trámite.

## Privacidad

Las capturas originales no se incorporan al repositorio porque contienen datos
identificativos visibles del certificado y de la sesión. No se conservaron ni
se documentan contraseña, PKCS#12, clave privada, certificado, firma, challenge,
cookies, campos de sesión, identificadores personales ni contenido de
expedientes.

## Promoción posterior

Tras la aceptación real, el cambio de catálogo es exclusivamente de evidencia:

- profile version `2`;
- `compatibilityStatus = VERIFIED_E2E`;
- `activation = ENABLED`;
- public catalog `E2E_VERIFIED / VERIFIED_E2E`;
- catálogo de profiles version `10`.

No se amplían origins, endpoints, capabilities, formato, algoritmo, propiedades,
callback ni transporte.
