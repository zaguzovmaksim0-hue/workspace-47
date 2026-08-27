# REG-AGE / RedSARA — E2E client-TLS login aceptado

- Fecha: `2026-08-27 UTC`
- Dispositivo: Android físico con Shizuku
- Profile: `reg-age-redsara`, versión `1`
- Estado de la entrada: `IMPLEMENTED_NOT_E2E / E2E_PENDING`
- Entrada: `https://reg.redsara.es/es/`
- QA APK exact-SHA: `0a8045a44d7846b566cce05e0f884fe0b1d914b696e3baf15895b42c3d935a05`
- Run: `e2e-redsara-20260827-fix`

## Flujo observado

1. La aplicación abrió la entrada exacta de REG mediante la operación QA
   `OPEN`.
2. `Nuevo registro` abrió la pasarela oficial Cl@ve y se seleccionó
   `eIdentifier`.
3. La aplicación mostró la confirmación nativa de acceso con certificado para
   `pasarela-ident.clave.gob.es`.
4. Tras confirmar, el WebView aceptó la petición de certificado cliente y
   regresó a REG.
5. REG mostró de nuevo `Nuevo registro` y el formulario
   `Datos del solicitante (Paso 1 de 4)` con la sesión autenticada.

No se conservaron cuerpos POST, headers, cookies, tokens, valores de campos,
datos personales, certificado, clave privada ni contraseña.

## Resultado de la inspección QA

La operación `INSPECT` inmediatamente después del retorno informó:

- `browserSessionBound=true`;
- `webViewActive=true`;
- `currentHost=reg.redsara.es`;
- `currentUrlAllowed=true`;
- `clientCertRequestObserved=true`;
- `clientCertAcceptedObserved=true`;
- `portalCallbackObserved=false`;
- `signingStartedObserved=false`;
- `signingCompletedObserved=false`;
- `renderProcessGone=false`;
- `failureCode=null`.

La secuencia de eventos sanitizada fue:

`WEBVIEW_ATTACHED` → navegación a `reg.redsara.es` → navegación a
`pasarela.clave.gob.es` → `CLIENT_CERT_REQUEST` en
`pasarela-ident.clave.gob.es:443` → confirmación nativa →
`CLIENT_CERT_ACCEPTED` → retorno a `reg.redsara.es`.

## Alcance y límite

Esto verifica E2E el acceso/login `CLIENT_TLS_AUTH` de REG-AGE mediante Cl@ve
eIdentifier en el dispositivo físico. La entrada completa conserva
`IMPLEMENTED_NOT_E2E / E2E_PENDING`, y el profile conserva
`VERIFIED_CONTRACT / QA_ONLY`, porque la comprobación se detuvo antes de
rellenar o enviar una actuación administrativa y antes de ejecutar la firma
XAdES. La firma XAdES, la presentación administrativa y el registro final
requieren una evidencia independiente.
