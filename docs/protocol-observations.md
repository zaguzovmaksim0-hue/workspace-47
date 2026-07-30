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

## Contrato tri-phase derivado de fuente oficial — 2026-07-12

La implementación ejecutable se limita al contrato del cliente oficial
Cliente @firma, revisado en el commit
`fe60ef3fdbae3c491e97c262a2179e2787b85776`. Las referencias principales son:

- `AOCAdESTriPhaseSigner.java`: pre, PKCS#1 local, post y `OK NEWID=`;
- `PreSigner.java` y `PostSigner.java`: nombres y orden de campos;
- `TriphaseData.java` y `TriphaseDataSigner.java`: XML, `PRE`, `PK1` y
  `NEED_PRE`;
- `TriphaseUtil.java`: cadena de certificados;
- `AOUtil.java`: serialización Java Properties;
- `UrlHttpManagerImpl.java`: traslado de la cadena después de `?` al cuerpo
  UTF-8 de un POST dirigido al endpoint sin query.

El contrato cerrado usado por la app es:

- endpoint exacto ya observado en la página pública, sin query, fragment,
  userinfo, puerto alternativo ni redirecciones;
- cuerpo `application/x-www-form-urlencoded; charset=UTF-8`, sin percent
  encoding adicional, con Base64URL padded en `doc`, `cert`, `params` y
  `session`;
- Base64 estándar para `PRE` y `PK1` dentro del XML;
- una sola `firma/PRE` para esta operación de autenticación;
- respuesta HTTP 200 `text/plain` y resultado post exacto
  `OK NEWID=<Base64URL>`;
- ningún cookie de WebView, `Cookie`, `Authorization`, `Set-Cookie`, retry ni
  redirect forma parte del transporte nativo.

La ruta desplegada por Junta contiene `miniapplet-1_4`, mientras que el server
stock del checkout oficial usa otra ruta servlet. Por tanto, la fuente oficial
prueba la semántica del cliente, pero no prueba por sí sola el comportamiento
runtime del fork desplegado (MIME, límites o errores). Cualquier divergencia
falla cerrada y debe producir un defecto reproducible antes de ajustar el
contrato. El perfil continúa siendo `EXPERIMENTAL`; no se declara
`FULLY_VERIFIED` hasta que el portal acepte el E2E real.

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

## Observación 2026-07-12 — POCO F6 Pro debug probe

Método: `ProtocolProbeActivity` debug con listener limitado al origin Junta y
sin certificado cargado. La captura visible contiene únicamente campos
cerrados y longitudes.

- top-level host: `www.juntadeandalucia.es`;
- MiniApplet call: `LOAD`;
- argument count: `1`;
- argument lengths: `48`;
- observed runtime branch: `NONE`;
- no se abrió AutoFirma externa ni Google Play;
- no se leyó ningún certificado y no se intentó firmar.

Conclusión: la página pública llama realmente a `cargarMiniApplet`, pero esta
captura todavía no demuestra si `sign` selecciona `afirma`, `intent`,
WebSocket o red directa. Task 1 permanece abierto y no se infiere ninguna rama
de transporte.

## Observación 2026-07-12 — evidencia previa y revalidación fail-closed

Método final: `ProtocolProbeActivity` vincula cada mensaje a un UUID efímero de
documento, al origin HTTPS evaluado en el contexto actual y a una generación
nativa de navegación. Una rama emitida por el shim debe reutilizar el UUID
exacto (`REQUEST_ID`). Una navegación nativa solo puede asociarse si ya existe
exactamente una llamada top-level `SIGN` del mismo documento y origin, con
algoritmo y formato válidos y una antigüedad máxima de 250 ms
(`ACTIVE_CALL_WINDOW`). Si navigation llega antes que WebMessage, no existe
correlación retroactiva: el documento queda fail-closed. Los UUID no se
exportan. Una correlación candidata se publica únicamente después del
`MINIAPPLET_CALL_END` correspondiente; cualquier transición posterior ambigua
emite `PROTOCOL_CORRELATION_REJECTED` una sola vez e invalida la evidencia de
ese documento. La captura no leyó ningún certificado ni ejecutó una firma.

Fuente pública comprobada antes del runtime:

- `miniapplet.js.sha256`:
  `e5f17e93816d1875c57198917ed9fd1c6d6f9e71dd2d5c9fec3650d76544c713`;
- la ruta de Chrome/Android sigue siendo síncrona:
  `sign` → `execAppIntent` → `openUrl` → asignación de
  `document.location`.

Metadatos cerrados observados dos veces consecutivas antes del endurecimiento
final de main-frame/document binding:

- top-level host: `www.juntadeandalucia.es`;
- MiniApplet call: `SIGN`;
- algorithm: `SHA1withRSA`;
- format: `CAdES`;
- argument count: `6`;
- argument lengths: `28, 11, 5, 156, 0, 0`;
- runtime branch: `INTENT`;
- correlation: `ACTIVE_CALL_WINDOW`;
- intervalo entre `SIGN` y la rama: aproximadamente `3 ms`;
- resultado de navegación: `NAVIGATION_BLOCKED`;
- no se abrió AutoFirma externa ni Google Play;
- no se leyó P12, contraseña, certificado ni clave privada;
- no se guardó el URI `intent://`, sus parámetros ni payloads.

Estas dos capturas son evidencia útil del transporte observado, pero no se
presentan como validación de la implementación endurecida posterior.

Revalidación de la build endurecida exacta:

- nueve instrumentation tests en POCO F6 Pro verificaron `REQUEST_ID`, el
  aislamiento de iframe, el rechazo de intent standalone, el resultado
  fail-closed sin emparejamiento inverso, WebMessage no-string, transparencia
  de return/exception del método portal, la ausencia de canaries en history y
  que ninguna Activity externa se abre;
- seis lanzamientos públicos independientes mostraron únicamente
  `NAVIGATION_BLOCKED` o `LOAD` seguido de navegación bloqueada;
- ninguno de esos seis lanzamientos produjo un nuevo
  `ACTIVE_CALL_WINDOW`, por lo que no se contabiliza una reproducción causal
  final;
- el orden WebMessage/navigation es no determinista; el probe prefiere un
  falso negativo seguro antes que atribuir una rama anterior a una llamada
  posterior.

Conclusión delimitada: el hash/fuente pública y las dos capturas iniciales
justifican priorizar un parser interno estricto para transporte `intent`, pero
la correlación pública no quedó reproducida después del endurecimiento. Esta
limitación de investigación no autoriza firma ni `FULLY_VERIFIED`; el adapter
permanece experimental hasta el E2E real. La observación inicial de
`SHA1withRSA` se trata como compatibilidad legacy que requiere advertencia y
revalidación, nunca como algoritmo general.

## Observación 2026-07-18 — UniZAR, autenticación AutoScript tri-phase

La entrada pública exacta `https://tramita.unizar.es` y su integration JS fijan
`AutoScript.sign(webSessionHash, "SHA1withRSA", "CAdES", properties, success,
error)`. El challenge decodificado tiene 20 bytes y rota con la sesión; ningún
valor fue retenido. Las propiedades observadas son únicamente
`precalculatedHashAlgorithm=SHA1` y el `serverUrl` exacto
`/afirma-server-triphase-signer-2.7.3/SignatureService`.

El profile implementado restringe origin, profile, navigation epoch, request
ID, TTL, tuple, tamaño del challenge, propiedades y endpoint antes de red. SHA-1
solo existe detrás de `LEGACY_SHA1`, sin fallback. Aunque AutoScript configura
Storage/Retrieve, esos endpoints no se habilitan: la interceptación directa de
`sign` no prueba que deban exponerse como capabilities separadas. El estado
permaneció `VERIFIED_CONTRACT` / `IMPLEMENTED_NOT_E2E` hasta la autenticación
real segura aceptada por el portal el 2026-07-30.

## Observación 2026-07-30 — UniZAR aceptó la autenticación CAdES

En un dispositivo físico, el profile exacto completó PRE, firma RSA local, POST
y callback AutoScript. El portal abrió el área interna `Buzón Electrónico` y
`Mis Gestiones`, por lo que el resultado se considera aceptación E2E del login y
no solamente entrega de una firma a la página. La comprobación terminó sin
crear, modificar ni presentar ningún trámite.

El profile se promueve a versión 2, `VERIFIED_E2E / ENABLED`, sin modificar el
contrato técnico. Storage/Retrieve, cofirma, contrafirma, firma documental y
presentación administrativa siguen bloqueados. No se retuvieron challenge,
certificado, firma, cookies ni datos identificativos.

## Política para nuevas observaciones

- Verificar que el host pertenece a la Junta mediante fuente oficial y TLS
  válido antes de allowlist.
- Registrar longitudes y hashes, nunca valores.
- No guardar HAR completo, Cookie headers ni cuerpos de firma.
- Un cambio de hash del JS no implica fallo, pero obliga a repetir el smoke
  capture antes de release.
- Cada nuevo algoritmo, operación, host o codec requiere una prueba y una
  actualización de threat model si cambia una frontera de confianza.

## Observación 2026-07-29 — Oficina Virtual MiniApplet 1.5 aceptada E2E

En un dispositivo físico, el profile `junta-ofvirtual` completó el contrato
exacto ya registrado:

- origin `https://ws072.juntadeandalucia.es`;
- `MiniApplet.sign` con `SHA1withRSA` y `CAdES`;
- endpoint PRE/POST MiniApplet 1.5 exacto en `ws024`;
- callback de firma/certificado y posterior submit del formulario de login.

El portal aceptó el resultado y abrió su área interna. Esto cierra la evidencia
pendiente de aceptación portal-side para la autenticación, pero no amplía el
contrato a Storage/Retrieve, firma documental, cofirma, contrafirma o presentación
de solicitudes.

La investigación identificó dos transiciones relevantes:

1. un GET HTTP heredado del mismo host/path debía actualizarse de forma cerrada a
   HTTPS; otras navegaciones HTTP siguen bloqueadas;
2. el cambio a segundo plano no debe bloquear la identidad mientras viva el
   proceso, porque destruiría el WebView y la sesión antes del callback/redirect.

No se registraron valores de firma, certificado, challenge, formulario, cookie,
contraseña ni identificadores de la persona. La captura visual con identidad fue
excluida del repositorio.

## Observación 2026-07-30 — RedSARA no ofrece E2E XAdES público no destructivo

En el dispositivo físico, el profile QA abrió `https://reg.redsara.es/es/` con
el adapter `local-xades-detached-v1`. Tanto `Nuevo registro` como `Mis registros`
condujeron a `/es/login`; la única identificación visible fue `Accede con tu
Cl@ve`.

La firma XAdES contractual no es un login. Se ejecuta sobre el XML de resumen de
una solicitud preparada y el callback continúa con `saveXMLAutoSign`. Probar la
aceptación real exige autenticación Cl@ve y una actuación administrativa real.
La investigación se detuvo antes de autenticarse o crear/modificar un registro.
El estado permanece `VERIFIED_CONTRACT / QA_ONLY`; los tests criptográficos no
se reinterpretan como aceptación E2E.
