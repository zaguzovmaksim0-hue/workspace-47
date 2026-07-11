# Plan de pruebas — Junta Firma Mobile

Fecha: 2026-07-11
Estrategia: TDD para protocolo, certificados, crypto y límites; tests de
instrumentación para Android/WebView; E2E real separado; release gates al final.

## 1. Reglas generales

- Para cada comportamiento nuevo: test rojo, implementación mínima, test verde,
  refactor con suite verde.
- Ninguna fixture contiene un certificado real del usuario. Las identidades de
  test se generan localmente o están marcadas inequívocamente como sintéticas.
- Un fallo se investiga con una hipótesis y evidencia antes de cambiar código.
- Los tests nunca imprimen password, P12, key material, `dat`, cookies, firma ni
  certificado completo.
- Los tests de red usan servidor controlado y no convierten cleartext en una
  excepción de producción.
- Los resultados se guardan en `docs/test-report.md` con comandos y salida
  resumida real.

## 2. Unit tests obligatorios

### AfirmaUriParserTest

- sign URI válido;
- percent encoding una sola vez;
- `dat` base64url;
- `dat` duplicado rechazado;
- `algorithm` ausente rechazado;
- operación desconocida rechazada;
- URI por encima del máximo rechazada antes de parsear payload;
- callback de host externo rechazado;
- callback localhost/loopback/private IP rechazado;
- callback `javascript:` rechazado;
- caso de doble encoding conserva raw y decoded sin doble decode;
- parámetro crítico con distinto casing/duplicación rechazado;
- userinfo, puerto no permitido e IP literal rechazados;
- origin del caller no permitido rechazado.

### CertificateRepositoryTest / Pkcs12LoaderTest

- P12 RSA correcto;
- contraseña incorrecta;
- P12 sin private key;
- certificado expirado y todavía-no-válido;
- cadena de certificados legible;
- certificado EC rechazado en alcance RSA;
- keyUsage sin digitalSignature rechazado;
- múltiples private entries exigen selección inequívoca;
- private/public key no coincidentes rechazadas por challenge sign/verify;
- archivo vacío, corrupto y por encima del límite;
- todas las copias `CharArray` se limpian también en excepción.

### LocalSignatureEngineTest

- vector `SHA256withRSA` determinista en verificación;
- vector compatibility `SHA1withRSA` solo con policy explícita;
- cambiar un byte produce `verify=false`;
- firma valida con public key;
- algoritmo no soportado rechazado;
- key EC rechazada;
- no se consulta/exporta `PrivateKey.encoded`.

### CadesSignerTest

- estructura CMS/CAdES verificable con test certificate;
- detached/explicit según contrato confirmado;
- signed attributes esperados y digest correcto;
- alteración de contenido invalida verificación;
- algoritmo/formato no permitidos rechazados;
- límites de input y error de provider sanitizado.

### CookieBridgeTest

- cookies se entregan al mismo host;
- cookies no se entregan a otro host permitido ni externo;
- `Set-Cookie` se sincroniza con URL exacta y flush;
- 301/302 a login produce `SESSION_EXPIRED`;
- 401/403 produce `SESSION_EXPIRED`;
- HTML login con status 200 no se toma como respuesta de protocolo;
- redirects se revalidan por salto;
- ningún error/log contiene header Cookie o Set-Cookie.

### OriginAllowlistTest / SafeUrlValidatorTest

- cada origin HTTPS enumerado se acepta;
- HTTP se rechaza;
- `www.juntadeandalucia.es.evil.example` se rechaza;
- subdominio no enumerado se rechaza;
- punycode y Unicode se normalizan/rechazan de forma segura;
- trailing dot, mayúsculas y puerto explícito tienen política determinista;
- localhost, IPv4/IPv6 loopback, link-local, private y reserved se rechazan;
- userinfo y esquemas file/content/javascript/data se rechazan;
- DNS result no público y redirect externo se rechazan.

### AfirmaRequestRouterTest

- `afirma://sign` se enruta a confirmación;
- `selectcert` solo devuelve summary/certificate cuando el contrato lo permite;
- websocket se convierte en flujo shim y no abre socket local;
- market/Play AutoFirma produce evento de fallback y no lanza intent;
- intent con fallback afirma se procesa internamente;
- intent arbitrario/package/component se rechaza;
- requestId duplicado o consumido se rechaza;
- navegación/origin cambiado invalida pending request.

### TriPhaseClientTest

- request exacto derivado de fixture observada;
- status, content type y size válidos;
- pre-sign malformado, HTML, XXE, DOCTYPE y oversized rechazados;
- local signature se inserta sin alterar encoding esperado;
- post-sign válido devuelve resultado tipado;
- redirect/login/401/403 producen `SESSION_EXPIRED`;
- callback SSRF y redirect hostil bloqueados;
- timeout/cancel no reintenta una firma de forma ambigua;
- cookies se aíslan por host;
- cuerpos sensibles no aparecen en logger de test.

### WebMessageProtocolTest / PendingSignRequestStoreTest

- JSON válido y versionado;
- mensaje desconocido, gigante o con campos extra críticos rechazado;
- origin y URL principal deben coincidir;
- requestId UUID único y single-use;
- result/cancel/error limpian estado;
- strings con comillas, saltos y secuencias JS no producen inyección;
- mensajes fuera del navigation id activo se rechazan.

### SanitizedLoggerTest

- solo acepta campos allowlisted;
- secretos conocidos se rechazan/redactan antes de persistencia;
- hash se limita a ocho hex;
- export y copy contienen únicamente texto sanitizado;
- clear elimina el journal;
- release no emite debug payload metadata innecesaria.

## 3. Instrumentation tests obligatorios

Ejecutar en API 36 real o emulador equivalente:

- Activity arranca y muestra disclosure no oficial;
- primer flujo abre `ACTION_OPEN_DOCUMENT` para P12/PFX;
- fixture SAF se selecciona, contraseña se valida y aparece summary;
- WebView carga la URL inicial permitida;
- Back navega al historial antes de cerrar;
- refresh funciona y el indicador de error se recupera;
- `afirma://sign` se intercepta sin resolver app externa;
- market/Play AutoFirma no abre Play Store;
- external HTTPS válido emite intent al navegador;
- SSL error llama `cancel()` y nunca `proceed()`;
- bridge funciona en origin permitido y no existe en origin externo/subframe;
- popup/new window arbitrario se rechaza;
- rotación/recreación conserva WebView state sin conservar contraseña en Bundle;
- bloqueo manual obliga a introducir contraseña otra vez;
- background timeout bloquea identidad;
- una hoja de confirmación aparece y cancelar no firma;
- `FLAG_SECURE` está activo durante entrada de password;
- borrar sesión limpia cookies/storage según UI y vuelve a estado esperado.

Los tests de WebView usarán contenido controlado e interceptores/test server;
no dependerán del portal real para ser deterministas. La configuración release
no obtiene excepciones cleartext de los tests.

## 4. Smoke de portal en build debug

Con red real y sin introducir secretos en logs:

1. abrir la URL inicial;
2. confirmar status/host visible y ausencia de SSL bypass;
3. capturar metadatos del `MiniApplet.cargarMiniApplet`/`sign` real;
4. pulsar firma solo con certificado de prueba autorizado;
5. verificar que no se abre Play/AutoFirma;
6. registrar nombres, longitudes, hashes cortos, algoritmo, formato y hosts;
7. actualizar `protocol-observations.md` y fixtures de regresión.

## 5. E2E real obligatorio

Prerequisitos operativos, no sustituidos por mocks:

- dispositivo Android 16 disponible por ADB o Shizuku;
- certificado P12/PFX de prueba autorizado y su contraseña introducida por el
  usuario sin revelarla al agente/logs;
- acceso legítimo al trámite/portal;
- interacción del usuario para cualquier consentimiento legal.

Secuencia de aceptación:

1. instalar APK release con `adb install -r` o equivalente Shizuku autorizado;
2. iniciar `dev.junta.firmamobile/.MainActivity`;
3. seleccionar P12 por SAF;
4. validar contraseña y summary;
5. abrir portal y completar SSO dentro del WebView;
6. iniciar firma y comprobar que no salen Play/AutoFirma;
7. ver confirmación nativa con origin/cert/formato/algoritmo correctos;
8. aceptar, completar pre-sign/local/post-sign y entregar resultado;
9. confirmar que el portal acepta y continúa;
10. bloquear certificado y confirmar que otra firma exige contraseña;
11. inspeccionar UI tree, screenshot no sensible y logcat sanitizado;
12. repetir el camino de cancelación y sesión expirada.

Artefactos permitidos: capturas sin datos personales, UI XML sanitizado, lista
de eventos/códigos y hashes cortos. No se conserva network trace con cuerpos.

## 6. Comandos y gates de build/release

Comandos previstos desde la raíz:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Gates adicionales:

- manifest merged: package, min/target, `allowBackup=false`, cleartext false;
- release `debuggable=false`, `testOnly=false`, WebView debugging false;
- búsqueda estática de `handler.proceed`, trust-all, permissive hostname,
  `addJavascriptInterface`, localhost ports, `es.gob.afirma` y logs sensibles;
- APK instalado y Activity iniciada sin `ClassNotFoundException`;
- v2/v3 signature verificada y fingerprint guardado;
- diff final revisado y dependencias/licencias documentadas.

## 7. Clasificación del resultado

- **Passed:** evidencia directa cubre el caso completo.
- **Failed — change-caused:** se corrige antes de avanzar de etapa.
- **Failed — pre-existing/external:** se documenta con evidencia; no se marca
  etapa como aprobada.
- **Blocked by human/external state:** se identifica el paso exacto, pero no se
  sustituye por una afirmación de éxito.
- **Not run:** nunca se presenta como implícitamente aprobado.
