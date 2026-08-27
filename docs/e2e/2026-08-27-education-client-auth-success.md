# Educación convocatoria 46 — E2E client-TLS login aceptado

- Fecha: `2026-08-27 UTC`
- Dispositivo: Android físico con Shizuku
- Profile: `educacion-convocatoria`, versión `2`
- Estado de la entrada: `VERIFIED_E2E / E2E_VERIFIED`
- Entrada: `https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46`
- QA APK exact-SHA: `4a8196edd29bd964e475c111bb7bbf666afe97d41d2dd44f62fc57d5a92af566`
- Run: `e2e-education-20260827-post-fix-final`

## Flujo observado

1. La aplicación abrió la entrada exacta de la Convocatoria 46 mediante la
   operación QA `OPEN`.
2. La entrada oficial pasó por la pasarela Cl@ve y se seleccionó
   `eIdentifier`.
3. La aplicación mostró la confirmación nativa de acceso con certificado para
   el host de autenticación TLS.
4. Tras confirmar, el WebView aceptó la petición de certificado cliente y
   regresó a `sede.educacion.gob.es`.

No se conservaron cuerpos POST, headers, cookies, tokens, valores de campos,
datos personales, certificado, clave privada ni contraseña.

## Resultado de la inspección QA

La operación `INSPECT` inmediatamente después del retorno informó:

- `browserSessionBound=true`;
- `webViewActive=true`;
- `currentHost=sede.educacion.gob.es`;
- `currentUrlAllowed=true`;
- `clientCertRequestObserved=true`;
- `clientCertAcceptedObserved=true`;
- `portalCallbackObserved=false`;
- `signingStartedObserved=false`;
- `signingCompletedObserved=false`;
- `renderProcessGone=false`;
- `failureCode=null`.

La secuencia de eventos sanitizada incluyó:

`WEBVIEW_ATTACHED` → navegación por `sede.educacion.gob.es`,
`www.educacion.gob.es` y `pasarela.clave.gob.es` → `CLIENT_CERT_REQUEST` →
confirmación nativa → `CLIENT_CERT_ACCEPTED` → retorno a
`sede.educacion.gob.es`.

## Alcance y límite

Esto verifica E2E el acceso/login `CLIENT_TLS_AUTH` de la Convocatoria 46 en
el dispositivo físico. No se ejecutó firma documental, callback de firma,
relleno de una solicitud ni presentación administrativa. La marca
`VERIFIED_E2E` se refiere únicamente al capability de autenticación con
certificado implementado en este profile.
