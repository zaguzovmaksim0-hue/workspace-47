# REG-AGE / RedSARA — revalidación client-TLS de Cl@ve

- Fecha: `2026-08-26 Europe/Madrid`
- Dispositivo: Android físico; QA exact-SHA `3c01081949e14cb797cb1eea853669a5b6889eec`
- Profile: `reg-age-redsara`
- Estado al observar: `IMPLEMENTED_NOT_E2E / E2E_PENDING`

## Evidencia física sanitizada

Desde `https://reg.redsara.es/es/`, `Nuevo registro` abrió la pasarela oficial
Cl@ve. La opción `eIdentifier` se describe en la propia página como válida para
cualquier certificado electrónico cualificado. Al activarla, el WebView observó
esta cadena de documentos, sin conservar bodies, headers, cookies, tokens ni
valores de formulario:

1. `POST https://pasarela.clave.gob.es/Proxy2/ServiceProvider`
2. `POST https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`
3. `POST https://pasarela.clave.gob.es/Proxy2/ResponseRedirect`
4. `POST https://reg-api.redsara.es/auth/login/CLAVE2`
5. retorno a `https://reg.redsara.es/es/loginKo`

El target de certificado no llevaba query. Una conexión TLS estricta independiente
a `pasarela-ident.clave.gob.es:443` verificó el servidor (`Verify return code: 0`)
y observó un TLS `CertificateRequest`; el servidor no envió lista de CA aceptables.
No se deshabilitó TLS y no se presentó certificado cliente en esa comprobación.

## Diagnóstico y contrato mínimo

El QA observado tenía `clientAuthPolicy=null`. `JuntaWebViewClient` ignora
fail-closed un `ClientCertRequest` sin grant previo. REG mostró `El acceso mediante
Cl@ve ha fallado`, sin error SSL/network/Safe Browsing en la telemetría sanitizada.

El delta representa solo lo observado: main-frame `POST` desde
`https://pasarela.clave.gob.es/Proxy2/ServiceProvider` hacia
`https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`, puerto `443`, sin
query y con issuer list vacía permitida. Se usa `IN_PLACE_FROM_SOURCE` porque el
target es POST y el authorizer ya dispone de una ruta exacta para resource requests
main-frame.

## Límite de evidencia

Esto prueba el contrato client-TLS, no todavía una sesión autenticada exitosa ni
aceptación XAdES por REG-AGE. No se promueve a `VERIFIED_E2E` hasta repetir el
flujo físicamente con el nuevo contrato y observar la aceptación del portal. No se
conservaron certificados, private keys, passwords, cookies, tokens, POST bodies,
datos personales ni screenshots autenticados.
