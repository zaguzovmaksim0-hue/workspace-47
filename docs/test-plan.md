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

### ProfileCookieBridgeTest / SiteDataCleanerTest

- cookies nativas se entregan solo al endpoint exacto del perfil activo;
- otro perfil, mismo host con path distinto y perfil sin endpoints fallan antes
  de consultar `CookieManager`;
- `Set-Cookie` se acota, rechaza CR/LF/NUL/oversize y hace flush sin logs;
- borrar el sitio elimina solo WebStorage del origin HTTPS actual;
- si `GET_COOKIE_INFO` no existe, las cookies quedan intactas y nunca se ejecuta
  un borrado global implícito;
- al estar disponible, solo se reutilizan nombre, host exacto, path y `Secure`
  para expirar la cookie; no se copia su valor;
- metadata parent-domain/malformada deja las cookies intactas;
- cerrar sesión, borrar el sitio y borrar todos los datos son acciones y
  confirmaciones diferentes;
- 301/302 a login, 401/403 o HTML inesperado producen `SESSION_EXPIRED`.

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

### PublicIpAddressPolicyTest / ProfileHttpTransportTest / relay upstream tests

- la revisión IANA IPv6 queda fijada a `2025-10-09`;
- IPv4 público y IPv6 global ordinario dentro de `2000::/3` se aceptan;
- unspecified, loopback, mapped, scoped, ULA, link-local, multicast,
  documentation, transition, benchmark y todos los prefijos especiales revisados
  se rechazan en sus límites exactos;
- `64:ff9b::/96` acepta solo IPv4 embebido público y rechaza private, loopback,
  documentation, benchmark y demás IPv4 especiales;
- Android filtra el DNS set y entrega al executor únicamente direcciones
  aprobadas sin cambiar hostname/SNI;
- el network interceptor rechaza un peer fuera del set aprobado;
- el relay rechaza cualquier mixed DNS set inseguro, no unmappea IPv4-mapped,
  marca IPv6 literal con brackets y verifica el peer exacto;
- profiles continúan rechazando IP literals: F-17 afecta solo DNS results.

### AfirmaRequestRouterTest

- `afirma://sign` se enruta a confirmación;
- `selectcert` solo devuelve summary/certificate cuando el contrato lo permite;
- websocket se convierte en flujo shim y no abre socket local;
- market/Play AutoFirma produce evento de fallback y no lanza intent;
- intent con fallback afirma se procesa internamente;
- intent arbitrario/package/component se rechaza;
- requestId duplicado o consumido se rechaza;
- navegación/origin cambiado invalida pending request.

### JuntaWebViewClient Afirma frame-boundary regressions

- callback moderno main-frame permite `afirma://sign` válido desde el signing origin;
- callback moderno main-frame permite embedded-Afirma `intent:` válido;
- subframe directo `afirma:` se consume como `UNTRUSTED_AFIRMA_ORIGIN` y no entrega
  `onAfirmaRequest`;
- subframe embedded-Afirma `intent:` falla cerrado del mismo modo;
- callback String deprecated no puede probar main-frame y no entrega Afirma a native;
- los negativos anteriores conservan top-level trusted como control para aislar el
  límite de frame ownership;
- ejecutar la familia WebView/navigation/WebMessage en Debug y QA; el E2E físico del
  portal sigue siendo un gate separado.

### JuntaWebViewClient external-navigation frame-boundary regressions

- HTTPS externo directo en callback moderno main-frame mantiene un único
  `openExternal`;
- `intent:` validado con `browser_fallback_url=https://...` en main-frame mantiene el
  handoff externo aprobado;
- el mismo HTTPS desde subframe se consume como `UNTRUSTED_EXTERNAL_NAVIGATION` sin
  `openExternal` ni `onNavigationBlocked`;
- el callback String deprecated tampoco puede entregar `OpenExternal` porque no prueba
  propiedad main-frame;
- un `intent:` con browser fallback desde subframe falla cerrado por el mismo límite;
- el diagnóstico bloqueado no conserva query/fragment sensibles del URL de prueba;
- ejecutar la familia WebView/navigation/WebMessage en Debug y QA y mantener el E2E
  físico del navegador/portal como gate separado.

### JuntaWebViewClient blocked-navigation callback ownership regressions

- main-frame POST que no puede usar el upgrade HTTPS sigue publicando
  `INSECURE_HTTP`;
- main-frame cross-profile/invalid/Play block mantiene el callback de aplicación;
- HTTP downgrade subframe, cross-profile HTTPS subframe y scheme no soportado subframe
  se consumen y registran sin `onNavigationBlocked`;
- callback String deprecated tampoco puede publicar un blocked-navigation callback;
- los negativos conservan `main_frame=false` y la razón original en diagnóstico
  sanitizado sin query/fragment canaries;
- `JuntaNavigationPolicy` debe permanecer fuera del diff de esta familia.

### TrustedJuntaWebView capability-hardening regressions

- production source contiene explícitamente `setGeolocationEnabled(false)`;
- el manifest principal no declara `ACCESS_COARSE_LOCATION` ni
  `ACCESS_FINE_LOCATION`;
- `JuntaWebChromeClient.onGeolocationPermissionsShowPrompt` conserva
  `callback.invoke(origin, false, false)` y `onPermissionRequest` conserva `deny()`;
- `TrustedJuntaWebViewTest` mantiene mixed content, file/content access, ventanas y
  media autoplay en sus estados fail-closed existentes;
- ejecutar source/settings regressions en Debug y QA. El comportamiento físico de
  geolocalización queda fuera del claim automatizado.


### JuntaWebViewClient Safe Browsing frame ownership regressions

- main-frame hit siempre llama `backToSafety(true)` y publica `SAFE_BROWSING`;
- subframe hit llama `backToSafety(true)` pero no publica error de aplicación;
- stale WebView también vuelve a seguridad sin mutar UI;
- `proceed` e interstitial permanecen prohibidos.

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

### MonotonicSecurityTimeTest / BoundedReplayLedger / SigningCoordinator hostile tests

- el TTL de request, pending, operación activa y reply se calcula solo con reloj
  monotónico; el reloj civil puede avanzar o retroceder sin alterar autorización;
- el límite exacto de dos minutos expira y un valor monotónico menor que el
  observado falla cerrado;
- los request IDs terminales se retienen cinco minutos, se podan en el boundary
  exacto y liberan capacidad sin permitir replay durante la retención;
- rollback monotónico conserva la evidencia de replay y no poda entradas;
- dos `confirm` concurrentes permiten una sola ejecución de PRE/local/POST;
- success y failure concurrentes producen exactamente un terminal/callback;
- callbacks stale, epoch/origin cambiados y replay posterior al terminal no se
  entregan;
- el shim no contiene `Math.random()` y no reenvía AFIRMA/MiniApplet al bridge si
  Web Crypto no puede generar un UUID seguro.

### ClientAuthNavigationAuthorizerTest / ClientAuthRequestHandlerTest

- Carné Joven conserva `REDIRECT_AFTER_SOURCE` y sus parámetros exactos;
- AEAT usa `DIRECT_FROM_SOURCE` únicamente desde
  `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html` hacia
  `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`;
- legacy callback, subframe, profile/source incorrectos, suffix-host, non-443,
  path distinto/codificado, fragment, query y `?` vacío fallan cerrados;
- el mismo tuple profile/source/target/epoch se consume una sola vez;
- RSA procede únicamente con issuer DER coincidente; issuer vacío se rechaza
  para AEAT y dispara limpieza de preferencias;
- release no resuelve el profile ni sus origins mientras siga
  `VERIFIED_CONTRACT / QA_ONLY`.

### ClientCertPreferenceBarrierTest / ClientCertPreferenceCoordinatorTest

- estado inicial `IDLE`; ninguna navegación se habilita antes del callback;
- timeout exacto de tres segundos deja `FAILED` persistente a nivel de proceso;
- callback tardío, generation anterior y segundo consumo se ignoran;
- callback síncrono no deja una tarea de timeout activa;
- excepción del API y ausencia de callback fallan cerradas;
- una nueva limpieza supersede la generation anterior y solo su callback puede
  recuperar `IDLE`;
- desacoplar el listener de una Activity no cancela la limpieza global;
- el coordinator no conserva certificado, clave, WebView, URL ni datos del
  challenge;
- `BrowserScreen` no llama directamente a
  `WebView.clearClientCertPreferences` y no crea `AndroidView` en `CLEARING` o
  `FAILED`.

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
- `FLAG_SECURE` está inactivo solo en loading/no-certificate idle y activo
  durante password, unlock, certificado desbloqueado, catálogo/WebView y firma;
- borrar datos del sitio no afecta otros origins y muestra resultado
  exacto/limitado/fallido;
- cerrar sesión bloquea el certificado sin borrar datos de otros portales;
- borrar todos los datos web requiere una confirmación separada.
- el callback Android real de `clearClientCertPreferences` produce
  `CLEARING → IDLE` sin abrir WebView, portal ni certificado;
- el classifier IPv6 físico confirma IPv6 global ordinario, NAT64 con IPv4
  público y rechazo de NAT64 con IPv4 no público.

Los tests de WebView usarán contenido controlado e interceptores/test server;
no dependerán del portal real para ser deterministas. La configuración release
no obtiene excepciones cleartext de los tests.

## 4. Smoke de portal en build debug

Con red real y sin introducir secretos en logs:

1. abrir la URL inicial;
2. confirmar status/host visible y ausencia de SSL bypass;
3. capturar metadatos del `MiniApplet.cargarMiniApplet`/`sign` real y aceptar
   una rama solo con UUID idéntico o una única ventana top-level
   SIGN/documento/origin de 250 ms; publicar solo tras `CALL_END`, mostrar
   `PROTOCOL_CORRELATION_REJECTED` si se invalida y nunca emparejar una
   navegación anterior;
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
11. inspeccionar `FLAG_SECURE` mediante window state y usar UI tree/logcat
    sanitizados; no capturar screenshot de certificado, catálogo autenticado,
    WebView ni firma;
12. repetir el camino de cancelación y sesión expirada.

Para F-03 AEAT, la aceptación física se limita a:

1. abrir exactamente `Mi área personal` y seleccionar `Mis datos censales`;
2. observar `onReceivedClientCertRequest` para
   `www1.agenciatributaria.gob.es:443` sin registrar principals completos;
3. confirmar que key types e issuer digest son compatibles con la identidad;
4. aceptar el consentimiento nativo y verificar únicamente la apertura del área
   autenticada de solo lectura;
5. detenerse antes de cualquier modificación, firma, pago o presentación;
6. conservar solo hash del APK, sí/no de callback/aceptación, host/port,
   key-types normalizados, número/digest corto de issuers y categoría path sin
   query ni datos personales.

Si cualquiera de estos gates falla, el profile permanece `QA_ONLY`.

Artefactos permitidos: capturas únicamente de la pantalla inicial sin
certificado o de una Activity de prueba no sensible, UI XML sanitizado, estado
de window flags, lista de eventos/códigos y hashes cortos. Las superficies
protegidas por F-05 no se capturan. No se conserva network trace con cuerpos.

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

## 7. Gate CI y supply chain (F-14)

Cada `push`, pull request y ejecución manual debe conservar estos límites:

- permisos globales `contents: read`; no `pull_request_target` ni credenciales Git
  persistentes;
- todas las Actions de terceros fijadas a SHA completo de 40 caracteres;
- Gradle wrapper 9.4.1 y su distribución verificados por SHA-256 oficial;
- dependency verification con metadata y artifacts SHA-256, sin trusted wildcard;
- Python: descubrimiento completo de `tools/tests/test_*.py`;
- Android: Debug/QA unit, lint, Debug/QA/QA AndroidTest builds;
- APK: `zipalign -c -p -v 4`, v2 signature, exactamente un signer,
  `allowBackup=false`, cleartext false, no `testOnly` inesperado y canarios
  prohibidos ausentes;
- release sin las cuatro entradas privadas de firma: fallo obligatorio y ningún
  `app-release.apk` residual;
- Go 1.26.5: test normal, race en Linux, vet, build y `govulncheck` 1.6.0;
- Gitleaks 8.30.1: archivo descargado con checksum exacto y scan de historial Git
  completo con redacción;
- Gradle runtime locking estricto: `app/gradle.lockfile` contiene solo
  `debugRuntimeClasspath`, `qaRuntimeClasspath` y `releaseRuntimeClasspath`; la
  task de verificación materializa artifacts y falla ante lock ausente o stale;
- OSV-Scanner 2.3.8: solo `app/gradle.lockfile`,
  `tools/requirements.txt` y `ws024-relay/go.mod` como inputs explícitos;
- Dependabot semanal para Gradle, Go modules, GitHub Actions y `pip` en `/tools`.

Separación explícita de claims: `app/gradle.lockfile` fija versiones de los
runtime graphs instalables; `gradle/verification-metadata.xml` autentica metadata
y artifacts descargados por SHA-256; OSV consulta vulnerabilidades conocidas para
los paquetes del lock. Ninguno de estos controles demuestra ausencia de
vulnerabilidades desconocidas, lógica maliciosa o riesgos fuera del alcance de
las bases de datos.

El race detector no es compatible con Android/arm64. Una ejecución local que
retorne `-race is not supported on android/arm64` se clasifica como no ejecutada;
el job Linux debe seguir siendo obligatorio.

## 8. Clasificación del resultado

- **Passed:** evidencia directa cubre el caso completo.
- **Failed — change-caused:** se corrige antes de avanzar de etapa.
- **Failed — pre-existing/external:** se documenta con evidencia; no se marca
  etapa como aprobada.
- **Blocked by human/external state:** se identifica el paso exacto, pero no se
  sustituye por una afirmación de éxito.
- **Not run:** nunca se presenta como implícitamente aprobado.

## G14-04 — Android backup/D2D domain exclusion gate — 2026-08-06

- Parse `app/src/main/res/xml/backup_rules.xml`; require exactly nine `<exclude>` entries,
  one for each supported app backup domain, every `path="."`, and no `<include>`.
- Parse `app/src/main/res/xml/data_extraction_rules.xml`; require exactly
  `<cloud-backup>` and `<device-transfer>`, each with the same exact nine-domain
  exclusion set, every `path="."`, and no `<include>`.
- Keep `android:allowBackup="false"`, `android:fullBackupContent` and
  `android:dataExtractionRules` wired in the manifest; do not treat `allowBackup=false`
  alone as proof of Android 12+ D2D exclusion.
- Regression gate: `python -m unittest tools.tests.test_ci_policy.CiPolicyTest -v` plus
  the existing full Android JVM/lint/build, Python/Go, APK-artifact and release
  fail-closed gates. Physical device/portal execution is not required for this policy
  resource contract.

## G15-01 — Client TLS monotonic TTL regression gate — 2026-08-06

- Exercise pending redirect authorization, direct-transition replay suppression and granted Client TLS lifetime against injected monotonic time, including exact TTL expiry and civil-clock rollback.
- Revalidate the same monotonic grant after asynchronous client-certificate preference clearing and immediately before `ClientCertRequest.proceed`; civil time is permitted only for X.509 validity.
- Preserve hostile profile/origin/path, host/port, epoch, issuer, key algorithm, keyUsage/EKU and one-shot cleanup regressions.
- Run focused Debug/QA Client TLS suites plus full Debug/QA JVM, lint, Debug/QA/QA-AndroidTest assemblies, dependency/toolchain gates, full Python, Go test/vet/build, Android artifact verification and release-signing fail-closed.

## G24-01 — SSL callback UI ownership gate — 2026-08-08

- For normal WebView SSL callbacks, require unconditional `handler.cancel()`, forbid
  `handler.proceed()`, retain sanitized cancellation diagnostics, and forbid promotion to
  top-level `onBrowserError` because this callback has no `isForMainFrame` metadata.
- For dedicated Client TLS SSL callbacks, additionally require unconditional grant
  abandonment/one-shot cleanup before any later certificate request can proceed.
- Do not substitute `SslError.url` equality for frame ownership. Modern resource/error
  callbacks that expose `WebResourceRequest` keep their existing explicit
  `isForMainFrame` UI gates.
- Run focused Debug/QA browser + Client TLS suites, full Debug/QA JVM,
  dependency/toolchain, lint, Debug/QA/QA-AndroidTest assemblies, Python, Go
  test/vet/build, Android artifact verification and release-signing fail-closed.

## G25-01 — JavaScript modal secure-display gate — 2026-08-08

- Require explicit `JuntaWebChromeClient` overrides for `onJsAlert`, `onJsBeforeUnload`,
  `onJsConfirm` and `onJsPrompt`; none may delegate to `super` or create `Dialog`/
  `AlertDialog` UI.
- `alert` and `beforeunload` must resolve with `JsResult.confirm()` and return `true` so
  execution/navigation cannot remain suspended. `confirm` and `prompt` must resolve with
  `cancel()` and return `true`, preserving fail-closed false/null semantics.
- Callback `url`, `message` and prompt `defaultValue` must not be shown, logged, persisted
  or forwarded. Existing popup, generic-permission, geolocation, navigation, TLS,
  Client-TLS and signing gates remain unchanged.
- Run focused Debug/QA runtime + source-contract regressions, full Debug/QA JVM,
  runtime dependency/core/AAPT2 gates, lint, Debug/QA/QA-AndroidTest assemblies, Python,
  Go test/vet/build, Android artifact verification and release-signing fail-closed.
- Automated success proves suppression of the platform-default modal path, not physical
  compatibility with a public portal that intentionally depends on JavaScript dialogs.

## G26-01 — dedicated Client TLS subframe confinement gate — 2026-08-08

- A modern off-origin `WebResourceRequest` with `isForMainFrame=false` must be consumed,
  abandon/clear the one-shot Client TLS grant, and publish no application/UI callback.
- A modern subframe on an already allowed Client TLS source/request origin remains allowed
  (`false`) and must not abandon the grant; this is the compatibility control against an
  over-broad iframe ban.
- A disallowed modern main-frame request remains consumed, abandons the grant and publishes
  exactly `INVALID_URL`; this is the positive ownership control.
- A disallowed deprecated String callback remains consumed and abandons the grant but
  publishes no top-level callback because frame ownership is unavailable.
- Preserve exact Client TLS transition/target, host/port, TTL/epoch, issuer, key type,
  keyUsage/EKU, preference-clearing, profile/release and one-shot regressions. Run focused
  Debug/QA Client TLS tests plus fresh full Debug/QA JVM, runtime lock/core/AAPT2, lint,
  Debug/QA/QA-AndroidTest assemblies, Python, Go test/vet/build, Android artifact and
  release-signing fail-closed gates.

## G27-01 — certificate error live-region gate — 2026-08-08

- Render `AppRoot` with a synthetic locked certificate state containing
  `PASSWORD_INVALID_OR_FILE`; the existing visible error node must expose
  `SemanticsProperties.LiveRegion == LiveRegionMode.Assertive`.
- Preserve existing certificate-selection, password consumption/clearing, safe summary and action
  tests. The accessibility change must not request focus, change error copy/layout, or alter
  certificate state/validation/storage/signing/network/WebView/profile/release behavior.
- Run focused Debug/QA `AppRootTest`, fresh full Debug/QA JVM plus runtime-lock/core/AAPT2 checks,
  lint, Debug/QA/QA-AndroidTest assemblies, Python, Go test/vet/build, Android artifact and
  release-signing fail-closed gates. Physical TalkBack timing/interruption remains a manual gate.
