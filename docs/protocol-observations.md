# Observaciones del protocolo del portal

Este documento registra solo metadatos seguros. Nunca debe contener valores
`dat`, challenges, cookies, tokens, firmas, certificados completos, contraseñas
ni claves simétricas.

## Observación 2026-07-11 — página pública de login

Método: petición HTTPS sin credenciales con User-Agent móvil Android; análisis
estático de HTML y JavaScript público. No se ejecutó todavía dentro de WebView
ni se pulsó el botón de firma.

### Entrada

- URL:
  `https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs`
- observed_at_utc: `2026-07-11T13:56:29Z`
- HTTP status: `200`
- Content-Type: `text/html;charset=UTF-8`
- redirects: `0`
- body.length: `26932`
- body.sha256: `9e4a83f5f6c71dd416b7981ff2d76e0a540ed2ab8a89395125218523b3865001`

### Cliente JavaScript cargado

- path:
  `/empleoformacionytrabajoautonomo/ovorion/static/js/firma1/miniapplet.js`
- miniapplet.length: `170907`
- miniapplet.sha256:
  `e5f17e93816d1875c57198917ed9fd1c6d6f9e71dd2d5c9fec3650d76544c713`
- declaración global observada: `var MiniApplet`
- métodos usados por la página: `cargarMiniApplet`, `sign`
- retries configurados por la página: `20`

La biblioteca contiene rutas estáticas para `afirma://sign`,
`afirma://selectcert`, `afirma://websocket`, `afirma://batch`,
`afirma://save`, `afirma://signandsave`, `afirma://service`, un fallback
`intent://`, WebSocket/WSS, StorageService, RetrieveService, loopback
`127.0.0.1` y el puerto `63117`. Su presencia no prueba qué rama se ejecuta en
este dispositivo; el runtime capture debe determinarlo.

### Contrato visible de firma

- input simbólico del primer argumento: `semillaAut`;
- `algorithm`: `SHA1withRSA`;
- `format`: `CAdES`;
- signature mode indicado en enlace/configuración: `EXPLICIT`;
- `serverUrl`:
  `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService`;
- success callback signature:
  `saveSignatureAuthCallback(signatureB64, certificateB64)`;
- error callback signature:
  `showLogCallback(errorType, errorMessage)`;
- campos de formulario relevantes: `firmaB64`, `certificadoB64`;
- form action:
  `/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs`.

Interpretación, aún no prueba E2E: la página espera que el cliente entregue
firma y certificado al callback; el callback rellena un formulario same-site y
lo envía. Esta observación permite diseñar un shim que conserva la referencia
real del callback, sin hardcodear su nombre.

### Dominios vistos

- `www.juntadeandalucia.es` — página, scripts y form submit.
- `ws024.juntadeandalucia.es` — endpoint tri-phase declarado.

No se observaron durante esta petición callbacks hacia otros hosts.

## Runtime capture requerido

La primera build debug debe registrar, sin valores sensibles:

```text
event=AFIRMA_REQUEST_OBSERVED
origin=<scheme+host+port permitido>
operation=<sign/selectcert/websocket/...>
algorithm=<literal no sensible>
format=<literal no sensible>
parameter.<name>.length=<decimal>
parameter.<name>.sha256_8=<ocho hex>
serverurl.host=<host>
serverurl.path.sha256_8=<ocho hex si fuera sensible>
navigation_id=<id aleatorio no correlacionable fuera de la sesión>
```

Debe determinarse con evidencia:

1. rama exacta seleccionada por `cargarMiniApplet` en Android WebView;
2. si `MiniApplet.sign` construye primero websocket, `afirma://` o petición de
   almacenamiento;
3. parámetros, encoding y límites reales del request;
4. contrato pre-sign/post-sign, method, content types y redirects;
5. cookies requeridas por cada host;
6. forma exacta del resultado final y orden de callbacks;
7. respuesta de la página ante cancelación/error;
8. si SHA-1 sigue siendo obligatorio en runtime;
9. si aparece algún dominio oficial adicional.

La implementación de firma remota permanece cerrada hasta registrar estos
puntos y convertir cada observación en una prueba de regresión.

## Política para nuevas observaciones

- Verificar que el host pertenece a la Junta mediante fuente oficial y TLS
  válido antes de allowlist.
- Registrar longitudes y hashes, nunca valores.
- No guardar HAR completo, Cookie headers ni cuerpos de firma.
- Un cambio de hash del JS no implica fallo, pero obliga a repetir el smoke
  capture antes de release.
- Cada nuevo algoritmo, operación, host o codec requiere una prueba y una
  actualización de threat model si cambia una frontera de confianza.
