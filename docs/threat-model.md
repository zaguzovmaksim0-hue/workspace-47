# Threat model — Junta Firma Mobile

Fecha inicial: 2026-07-11
Última reconciliación de límites: 2026-08-04
Método: límites de confianza, activos, capacidades del atacante, rutas de abuso
y mitigaciones verificables.

## 1. Activos

| Activo | Impacto de compromiso |
|---|---|
| Clave privada PKCS#12 | Suplantación y firmas no autorizadas |
| Contraseña PKCS#12 | Desbloqueo de la identidad si se obtiene el archivo |
| Registro cifrado de unlock PKCS#12 | Recuperación local del secreto de desbloqueo dentro de la ventana válida |
| Clave AES de unlock en Android Keystore | Descifrado del registro local si se compromete su uso autorizado |
| Documento/challenge `dat` | Divulgación de datos y manipulación de la firma |
| Firma y certificado completo | Privacidad, replay o entrega al trámite incorrecto |
| Cookies y tokens SSO | Secuestro de sesión del portal |
| Resultado/callback activo | Confusión de solicitudes y firma sobre origen equivocado |
| Keystore de firma del APK | Actualizaciones maliciosas o pérdida de capacidad de update |
| Diagnóstico | Canal secundario de filtración de secretos |

## 2. Límites de confianza

```text
Usuario
  │ SAF + contraseña + confirmación
  ▼
UI/estado de la app ─────► memoria de identidad desbloqueada
  │                              ▲
  │ store/restore ≤ 24 h         │ recarga validada del PKCS#12
  ▼                              │
registro AES-256-GCM ────────────┘
`noBackupFilesDir`
  │
  └────► clave AES no exportable en Android Keystore

WebView (contenido remoto) ⇄ WebMessage bridge ⇄ router/signing
  │ cookies                                      │
  ▼                                              ▼
CookieManager                           cliente HTTPS restringido
                                                 │
                                                 ▼
                            hosts Junta allowlisted / tri-phase
```

Límites especialmente hostiles:

- todo JavaScript remoto, incluso en un host permitido, se considera entrada no
  confiable;
- archivos SAF pueden estar corruptos o ser maliciosos;
- URLs y respuestas de protocolo son entrada remota;
- intents externos y redirects cruzan fuera de la app;
- logs, portapapeles, screenshots y backups son canales de salida.

## 3. Capacidades del atacante

Se consideran:

- página de phishing con nombre parecido o subframe malicioso;
- XSS/compromiso de contenido en un origin permitido;
- URI `afirma://` o `intent://` sobredimensionada/manipulada;
- callback SSRF a localhost, red privada, IP literal o redirect hostil;
- respuesta tri-phase XML/JSON malformada, gigante o con XXE;
- P12 hostil, múltiples aliases, cadena inconsistente o contraseña errónea;
- aplicación externa que intenta recibir intents;
- lectura casual de almacenamiento compartido, backups, clipboard o logcat;
- replay/doble entrega de request IDs;
- lifecycle race al navegar, rotar o mandar la app a background;
- dispositivo comprometido/root o instrumentación del proceso.

Un dispositivo con root y control total del proceso está fuera de la garantía:
la app reduce exposición pero no puede proteger una clave mientras se usa en
memoria frente a ese adversario.

## 4. Rutas de abuso y controles

### T1. Un origin externo invoca el bridge

**Riesgo:** firma arbitraria o exfiltración.
**Controles:** origin rules exactas en WebKit; allowlist Kotlin independiente;
comprobación de URL principal actual; mensajes versionados y limitados; bridge
sin `addJavascriptInterface`; invalidación al navegar.
**Verificación:** tests de origin permitido, HTTP, phishing suffix, punycode,
subframe externo y navegación fuera del allowlist.

### T2. XSS en un origin permitido solicita una firma

**Riesgo:** un origin legítimo comprometido intenta firmar datos no visibles.
**Controles:** confirmación humana obligatoria mostrando host, titular, formato
y algoritmo; operaciones/formats cerrados; contrato exacto por portal;
`requestId` único; no se expone API de firma arbitraria; SHA-1 requiere
advertencia adicional.
**Riesgo residual:** el usuario no ve el documento completo del login challenge.
El E2E debe confirmar qué se firma y el UI debe describirlo con precisión.

### T3. Intent/Play fallback escapa a AutoFirma externa o un frame ambiguo alcanza native

**Riesgo:** confusión de app, envío de datos a otro paquete, entrega de una petición
Afirma nativa o lanzamiento del handoff a navegador externo desde un subframe/callback
legacy sin propiedad top-level probada. El handoff externo además puede invalidar
estado Client TLS/Afirma/firma de nivel superior antes de abrir la Activity.
**Controles:** interceptar `afirma://`, `intent://`, `market://` y URLs Play; rechazar
package/component explícitos no aprobados; nunca usar `es.gob.afirma`; evento
sanitizado de fallback. Solo una navegación Afirma directa o embedded-Afirma recibida
por el callback moderno y marcada `isForMainFrame=true` puede llegar a
`onAfirmaRequest`; subframes y el callback String deprecated se consumen con diagnóstico
`UNTRUSTED_AFIRMA_ORIGIN` pero no publican `onNavigationBlocked` ni otra señal top-level de
aplicación. De forma independiente, `OpenExternal` solo puede llegar a
la aplicación desde un callback moderno main-frame; subframe/legacy se consume con
`UNTRUSTED_EXTERNAL_NAVIGATION` y sin `openExternal` ni callback UI de bloqueo.
**Verificación:** regressions Debug/QA con controles positivos main-frame y negativos
subframe/legacy para Afirma, incluyendo ausencia de entrega native y de callback UI top-level,
HTTPS externo e `intent:` con browser fallback; instrumentation sin resolución de
Play/AutoFirma. La cobertura automatizada no
sustituye el E2E físico del portal/dispositivo.

### T4. Callback, server URL o DNS produce SSRF

**Riesgo:** acceso a localhost/red privada/especial o envío de cookies/datos a
tercero mediante DNS rebinding, IPv6 ambiguo o NAT64 hacia IPv4 no público.
**Controles:** HTTPS exclusivo, hosts exactos, sin userinfo ni IP literal en el
profile, puerto restringido y redirects revalidados. Cada resultado DNS pasa por
`PublicIpAddressPolicy`, revisada contra el registro IANA IPv6 Special-Purpose
revision 2025-10-09: IPv6 ordinario solo en `2000::/3`; scope IDs, IPv4-mapped,
ULA, link-local, documentation, transition, benchmark, multicast y demás bloques
especiales se rechazan. `64:ff9b::/96` solo se acepta si el IPv4 embebido pasa la
misma policy pública IPv4. OkHttp conserva hostname/SNI, recibe exclusivamente el
set aprobado y verifica la dirección conectada. El relay rechaza el conjunto DNS
completo si contiene una respuesta insegura, marca mapped/zoned como inválida,
marca el literal IPv6 con brackets y verifica el peer exacto.
**Verificación:** tests table-driven de límites/prefijos/NAT64, filtering y DNS
pinning Android; tests Go de clasificación, mixed-set rejection, literal IPv6 y
remote-peer verification; no se registra la dirección resuelta.

### T5. Robo o persistencia de contraseña/clave

**Riesgo:** recuperación posterior de la identidad o firma sin el consentimiento
esperado durante la ventana de desbloqueo.
**Controles:** la clave privada y los bytes PKCS#12 no se persisten por este
mecanismo y `PrivateKey.encoded` no se consulta. Tras un desbloqueo manual
correcto, la contraseña puede persistir durante un máximo de 24 horas únicamente
como ciphertext autenticado AES-256-GCM en `noBackupFilesDir`, nunca en texto
plano ni Preferences/logs. El registro `JFMUC002` queda ligado mediante AAD a la referencia
del certificado, a los timestamps civiles originales de emisión/expiración y a la
observación de mismo arranque (`Settings.Global.BOOT_COUNT` +
`SystemClock.elapsedRealtimeNanos()`). La clave AES es material no exportable de
Android Keystore; en API 28+ el provider actual exige que el dispositivo esté
desbloqueado para usarla. `noBackupFilesDir` excluye el
registro cifrado del backup/transfer. Además, el manifest mantiene `allowBackup=false`
y los recursos legacy/Android 12+ excluyen
explícitamente `root`, `file`, `database`, `sharedpref`, `external` y los cuatro
dominios device-protected, tanto para cloud backup como para device transfer; por tanto
`allowBackup=false` no es la única barrera D2D. La
persistencia autentica la observación monotónica de la lease creada antes del IO, por
lo que un retraso de escritura no desplaza el origen de autorización. La restauración
automática conserva la expiración civil original, **no renueva** la ventana y, además,
solo es válida durante el mismo arranque del dispositivo y durante el tiempo monotónico
restante autenticado. Un rollback de `elapsedRealtime`, un cambio de `BOOT_COUNT`,
una fuente de tiempo/boot no disponible o el límite exacto/ulterior de la lease
fallan cerrados. Un rollback del reloj civil no amplía la lease; un salto civil hacia
adelante puede acortarla como límite conservador. Los registros legacy `JFMUC001`
carecen de evidencia de mismo arranque y se eliminan en restore, lo que puede exigir
una única reentrada de contraseña tras la actualización. Los buffers temporales
`CharArray`/`ByteArray` en claro se limpian best-effort.

**Semántica lifecycle:** bloqueo manual, clear session, cambio u olvido del
certificado, expiración, reference mismatch, ciphertext malformado/manipulado o
unlock cacheado fallido eliminan el registro. Background, process death y force-stop
no eliminan por sí solos un registro todavía válido **durante el mismo device boot**;
una actualización ordinaria tampoco lo invalida si el boot y la lease siguen siendo
válidos. En cambio, un reinicio del dispositivo cambia `BOOT_COUNT`: la siguiente
restauración elimina el registro y exige introducir de nuevo la contraseña PKCS#12.
Ante memory pressure, `CertificateSession` suelta primero la identidad en memoria,
pero el ViewModel puede restaurarla desde el cache válido del mismo boot y solo por
la duración monotónica restante. Por tanto memory pressure o process death no
garantizan un estado locked persistente dentro de esa lease; **device reboot sí es
una frontera de bloqueo para la restauración automática**.

**Riesgo residual:** antes de expirar el registro, código que ejecute con los
privilegios de la app en un dispositivo elegible y desbloqueado puede intentar
activar la recuperación local. En API 26–27 no existe la protección adicional
`setUnlockedDeviceRequired(true)` usada por este provider desde API 28. Un
dispositivo/root con control del proceso sigue fuera de la garantía. Recuperar la
identidad no omite la confirmación independiente exigida para cada solicitud de
firma. La correspondencia de clave se prueba firmando un nonce, no exportando
bytes.

**Verificación:** tests de `CertificateUnlockCache`, `CertificateSession` y
`CertificateViewModel` cubren cifrado/tamper/expiración, limpieza, rollback civil,
cambio de boot, rollback monotónico, límite exacto, lease restante, process
recreation y memory-pressure recovery; búsquedas estáticas cubren APIs/campos
prohibidos; se inspeccionan backup config y logs. La evidencia física histórica P07C
confirma únicamente un cold launch tras terminación del proceso sin nuevo password
prompt y sin registrar el secreto; **no prueba restauración tras reboot**. El contrato
actual exige reentrada de contraseña después de un reboot.

### T6. P12 malicioso agota recursos o selecciona identidad equivocada

**Riesgo:** DoS, uso de certificado no esperado o parser edge case.
**Controles:** límite de tamaño antes de cargar; número de aliases y cadena
limitados; selección explícita si hay varias entradas; vigencia, keyUsage,
algoritmo y correspondencia de clave comprobados; errores sin datos internos.
**Verificación:** fixtures correctas, contraseña errónea, sin private key,
expirada, cadena, EC y archivo sobredimensionado.

### T7. XXE/deserialización/respuesta gigante

**Riesgo:** lectura local, SSRF o memory exhaustion.
**Controles:** XML secure processing, DOCTYPE/DTD/entities externos
desactivados; parser streaming cuando sea posible; content type y tamaño
obligatorios; JSON con modelo cerrado; no Java serialization.
**Verificación:** tests XXE, billion-laughs/DOCTYPE, tamaño y content type.

### T8. Confusión, replay, manipulación del reloj o entrega JS inyectada

**Riesgo:** resultado A vuelve a solicitud B, un cambio del reloj civil amplía la
ventana de firma, el ledger de replay queda lleno de forma permanente o se ejecuta
JavaScript construido con concatenación/identificadores débiles.
**Controles:** estado one-shot enlazado a requestId+profile+origin+navigation;
TTL de dos minutos medido exclusivamente con reloj monotónico desde la observación
bridge hasta PRE, firma local, POST y callback; IDs terminales retenidos en un
ledger acotado durante cinco minutos y podados antes del límite de capacidad. Un
retroceso del reloj monotónico falla cerrado. Los mensajes son JSON, los callbacks
son referencias internas del shim y los request IDs exigen Web Crypto; no existe
fallback `Math.random()` ni construcción de código JS con datos.
**Verificación:** tests de boundary exacto, salto del reloj civil, rollback
monotónico, pruning/capacity, requestId repetido, origin/navigation cambiados,
confirmaciones concurrentes, success/failure concurrentes, callbacks stale y
payload con caracteres de inyección.

### T9. Cookies cruzan de perfil o una limpieza local borra otras sesiones

**Riesgo:** secuestro/confusión de sesión, firma enviada a login HTML o pérdida
involuntaria de sesiones de otros portales.
**Controles:** el bridge nativo se construye con un único `SiteProfile` y acepta
solo URLs exactas declaradas como endpoints de red; los perfiles sin endpoint no
pueden crear el bridge. `Set-Cookie` queda acotado, sin CR/LF/NUL ni logs. La
limpieza del sitio usa `WebStorage.deleteOrigin` y solo expira nombres de cookies
con host exacto cuando `GET_COOKIE_INFO` está disponible; metadata parent-domain
o malformada queda intacta. Cerrar el certificado no borra cookies, y la limpieza
global requiere una acción y confirmación diferentes. Redirects, 401/403 y HTML
de login siguen clasificándose como sesión expirada.
**Verificación:** tests de cruce de perfil/path, límites y excepciones del store;
tests del cleaner que prohíben fallback global; Compose/source regressions para
las tres acciones; instrumentation de capabilities sin iniciar la UI.

### T10. Diagnóstico, clipboard, screenshots o backup filtran datos

**Riesgo:** secreto disponible a otras apps o soporte.
**Controles:** logger con esquema allowlist, hashes truncados, almacenamiento
privado y export sanitizado; por requisito de producto las capturas de pantalla están
permitidas en toda la aplicación y `MainActivity` no aplica `FLAG_SECURE`, incluso
durante password, unlock, certificado desbloqueado, catálogo, WebView o firma. La
prevención de capturas deja por tanto de ser un control de confidencialidad: no se
deben conservar ni adjuntar como evidencia capturas que contengan identidad, secretos
o datos autenticados. Se mantiene la prohibición de copiar secretos, `allowBackup=false`
y las exclusiones explícitas completas por dominio en legacy/cloud/D2D.
**Verificación:** tests del redactor y de la policy de ventana que exigen captura
habilitada para todos los estados, inspección de exports/logcat/manifest y `dumpsys
window` en dispositivo físico confirmando ausencia de `FLAG_SECURE`; la QA no persiste
capturas con datos sensibles.

### T11. Build/release o cadena de suministro comprometidos

**Riesgo:** Action/tag mutable, wrapper o dependencia sustituida, secreto en el
historial, toolchain vulnerable, APK alterado, debug habilitado o pérdida del
keystore.
**Controles:** workflows con `contents: read`, sin `pull_request_target` y con
Actions fijadas a SHA de 40 caracteres; Gradle 9.4.1 wrapper/distribution y
artifacts resueltos verificados por SHA-256 sin trusted wildcard; Gitleaks pinned
sobre historial completo; Dependabot para Gradle, Go y Actions; CI Go 1.26.6 con
race/vet/build/`govulncheck`; locking Gradle `STRICT` limitado a los tres
runtime classpaths instalables; OSV pinned sobre el runtime lock Android y los
manifests Python/Go explícitos. Release no acepta debug-key fallback y falla
cerrado sin keystore privado. Cada
APK se valida con `zipalign`, `apksigner`, signer count, manifest y canarios.
**Verificación:** policy tests del workflow, checksums oficiales, history scan,
unit/lint/build, vulnerability gates y análisis del APK final.
**Riesgo residual:** dependency locking demuestra reproducibilidad de versiones,
verification metadata demuestra identidad/integridad de artifacts y OSV cubre
solo vulnerabilidades conocidas en sus fuentes. No prueban ausencia de CVEs no
publicadas, paquetes maliciosos ni riesgos en build/test tooling fuera del claim
runtime. El race gate se ejecuta en Linux; Android/arm64 no lo soporta.

### T12. Una decisión Client TLS cacheada sobrevive al lifecycle o se reutiliza

**Riesgo:** WebView reutiliza una preferencia de certificado cliente después de
background, cambio de profile, bloqueo, renderer death o recreación de Activity;
un callback tardío puede activar un grant viejo antes de que la limpieza global
haya terminado.
**Controles:** `ClientCertPreferenceCoordinator` pertenece al proceso y modela
`IDLE/CLEARING/FAILED` con generation token. La creación de cualquier WebView de
portal queda bloqueada durante `CLEARING` y `FAILED`. El timeout es de tres
segundos; excepción, ausencia de callback o mismatch posterior de profile, epoch
o TTL fallan cerrados. El callback UI puede desacoplarse sin cancelar la limpieza
del proceso y una generation anterior nunca puede reactivar un grant. Solo una
limpieza posterior confirmada vuelve a `IDLE`. La clave y el certificado no se
guardan en el coordinator.
**Verificación:** tests deterministas de callback síncrono/tardío/ausente,
timeout, excepción, supersession, cancelación del listener y retry; source
regressions para impedir el API estático en `BrowserScreen`; instrumentation del
callback Android real sin WebView ni portal.

### T13. Un portal Client TLS amplía el grant por semejanza de dominio o URL

**Riesgo:** una navegación desde otra página AEAT, un subframe, callback legacy,
host suffix, puerto distinto, path codificado, query añadido o un `?` vacío
obtiene acceso al certificado porque comparte organización u origin.
**Controles:** cada `ClientAuthPolicy` declara un modo de transición explícito.
AEAT usa `DIRECT_FROM_SOURCE`: solo una petición main-frame moderna desde la URL
source completa puede autorizar el target completo `www1:443/MdcAcceso`; query,
fragment, userinfo, non-443, path alternativo y repetición del mismo grant fallan
cerrados. Carné Joven conserva separadamente `REDIRECT_AFTER_SOURCE`. El profile
AEAT es `VERIFIED_CONTRACT / QA_ONLY`; release no lo carga antes del E2E físico.
La entrega final vuelve a validar host/port, epoch, TTL, key type, validez,
keyUsage, EKU e issuer no vacío antes de `ClientCertRequest.proceed`. El TTL de la
transición pendiente, la supresión de replay directo y el grant confirmado usan tiempo
monotónico de proceso; un rollback del reloj civil falla cerrado y no prolonga el grant.
El reloj civil se conserva únicamente para comprobar la vigencia X.509 del certificado.
**Verificación:** tests hostiles de transición directa, query vacía, suffix-host,
encoded path, legacy/subframe/wrong-source y consumo único; tests del request
handler para RSA con issuer coincidente y rechazo de issuer vacío; registry tests
que demuestran ausencia completa del profile/origins en release.

### T14. Contenido WebView solicita geolocalización o capacidad del dispositivo

**Riesgo:** contenido remoto intenta convertir una capacidad WebView opcional en acceso
a ubicación u otros recursos del dispositivo.
**Controles:** `TrustedJuntaWebView` desactiva explícitamente geolocalización con
`setGeolocationEnabled(false)` y `JuntaWebChromeClient` rechaza tanto el prompt específico
de geolocalización como `PermissionRequest` genérico. La aplicación declara únicamente
`ACCESS_COARSE_LOCATION` para el selector nativo de región: se solicita después de que el
usuario pulse «Usar mi ubicación», se obtiene una sola posición foreground y se descartan
inmediatamente coordenadas y dirección tras resolver el código territorial. No se declaran
`ACCESS_FINE_LOCATION` ni `ACCESS_BACKGROUND_LOCATION`, no se guarda ni registra la
posición y ese permiso nunca se expone al WebView. File/content access y ventanas múltiples
siguen desactivados de forma independiente.
**Verificación:** source regression exige el setter explícito y el callback
`allow=false, retain=false`; tests de manifest exigen coarse y rechazan fine/background;
tests de preferencias comprueban que solo persiste el código regional. Lint/build compilan
también el contrato instrumentado.

### T15. Un subframe bloqueado altera el estado UI de nivel superior

**Riesgo:** iframe o callback WebView legacy no puede navegar por una decisión fail-closed,
pero provoca `onNavigationBlocked` y con ello un aviso assertive de nivel superior que el
usuario puede interpretar como fallo de su navegación principal.
**Controles:** `JuntaWebViewClient` consume y registra todos los blocks como antes, pero
solo una petición moderna `isForMainFrame=true` puede publicar `onNavigationBlocked` a la
aplicación. Los paths subframe/legacy no reciben `loadUrl`, external handoff ni native
Afirma y tampoco modifican `blockedReason`. `JuntaNavigationPolicy` permanece sin cambios.
**Verificación:** regression cubre HTTP downgrade subframe, cross-profile HTTPS, scheme
no soportado y callback String deprecated; controles main-frame conservan los callbacks
positivos y los diagnósticos negativos no retienen query/fragment canaries.


### T16. Safe Browsing de un subframe altera el UI superior

**Riesgo:** un iframe malicioso es bloqueado por Safe Browsing pero provoca estado de error de nivel superior.
**Controles:** `backToSafety(true)` es incondicional; `SAFE_BROWSING` solo llega a la aplicación para el WebView activo y `isForMainFrame=true`. Subframes siguen bloqueados y diagnosticados sin `proceed`.
**Verificación:** main-frame positivo, subframe negativo y stale-owner fail-closed; full Android/Python/Go gates.


### T17. Un SSL error sin ownership de frame altera el UI superior

**Riesgo:** `onReceivedSslError` no entrega `WebResourceRequest` ni
`isForMainFrame`; si una aplicación trata cualquier callback del WebView activo como
error top-level, un recurso con ownership no demostrada puede activar un aviso/retry de
nivel superior.
**Controles:** ambos clientes llaman `SslErrorHandler.cancel()` de forma incondicional y
antes de cualquier otra acción. El cliente normal conserva solo diagnóstico sanitizado;
el cliente Client TLS además abandona incondicionalmente el grant one-shot. El callback
SSL no publica `BrowserErrorCode.SSL_ERROR` a la aplicación porque no puede demostrar
main-frame ownership, y no se usa `SslError.url` como sustituto. Los callbacks modernos
con `WebResourceRequest` conservan gates explícitos `isForMainFrame`.
**Verificación:** RED en ambos clientes, asserts de `cancel`/no-`proceed`, abandono del
grant y request posterior rechazado; full Android/Python/Go/artifact/release gates. La
renderización física de error WebView sigue siendo gate manual y no modifica el
fail-closed TLS contract.

## 5. Decisiones explícitas

- No localhost WSS, puertos 63117/63118/63119/17629, CA local ni trust bypass.
- No pinning hasta disponer de rotación operativa.
- No copia del P12 en app-private storage: el AES-256-GCM/Android Keystore
  existente protege únicamente el registro cifrado de la contraseña de unlock, no
  una copia del PKCS#12 ni de la clave privada.
- No SHA-1 general: solo compatibilidad delimitada si el runtime confirma que el
  portal continúa exigiéndolo.
- No DES hasta evidencia runtime, test vector y aislamiento en codec legacy.
- No wildcard de dominio.

## 6. Riesgos que requieren evidencia E2E

- contrato exacto pre-sign/post-sign y sus content types;
- significado y sensibilidad del challenge `semillaAut`;
- comportamiento del SSO y cookies entre hosts;
- ramas de transporte distintas de la página pública: dos capturas anteriores
  al endurecimiento vincularon `SIGN` con `INTENT`, mientras seis repeticiones
  de la build endurecida dieron un falso negativo seguro; por tanto el parser
  `intent` puede priorizarse, pero el perfil sigue experimental hasta una nueva
  correlación endurecida y el E2E real;
- si la página cambia el algoritmo o endpoints tras autenticación;
- aceptación final por el portal y bloqueo correcto tras background.

Estos puntos no se cerrarán por inferencia de código estático. Se consideran
resueltos únicamente con capture sanitizado y ejecución real autorizada.

### T18. JavaScript remoto crea un modal fuera de la protección de pantalla

**Riesgo:** el navegador privilegiado aplica `FLAG_SECURE`, pero Android documenta que los
modales JavaScript por defecto de `WebChromeClient` no heredan esa protección del parent.
Si `alert`, `confirm`, `prompt` u `onbeforeunload` caen al comportamiento por defecto,
contenido controlado por la página puede aparecer en otra ventana fuera de la frontera de
captura/screen-share protegida.
**Controles:** `JuntaWebChromeClient` maneja explícitamente los cuatro callbacks y devuelve
`true` siempre. `alert` y `beforeunload` llaman `confirm()` inmediatamente para no dejar
JavaScript/navegación suspendidos; `confirm` y `prompt` llaman `cancel()` para resolver de
forma fail-closed. No se crea UI custom ni se muestra, registra, persiste o reenvía
`url`, `message` o `defaultValue`. Las denegaciones existentes de popup, permisos y
geolocalización permanecen.
**Verificación:** RED sobre el path heredado, runtime/source regressions para los cuatro
callbacks, scan que prohíbe `Dialog`/`AlertDialog` y `super.onJs*`, y full Android/Python/
Go/artifact/release gates. La compatibilidad física con portales que dependan de modales
JavaScript sigue siendo gate manual; no se infiere E2E.

### T19. Un subframe externo amplía la superficie del WebView Client TLS dedicado

**Riesgo:** el WebView one-shot usado para Client TLS aplica una allowlist estricta a la
navegación principal, pero un callback moderno de subframe omite esa validación y permite
contenido de otro origin dentro de la vista autenticada. Aunque `ClientCertRequest` sigue
revalidando host/port/certificado y no se demostró divulgación de credenciales, esa carga
amplía innecesariamente la superficie remota y un frame no propietario tampoco debe poder
crear estado UI top-level.

**Controles:** `ClientAuthWebViewClient` evalúa el mismo `isAllowed()` para toda navegación
moderna. Un subframe en los origins source/request ya autorizados conserva compatibilidad;
un subframe fuera de esos origins se consume, abandona/limpia el grant y no publica
`onNavigationBlocked`. Solo una petición moderna `isForMainFrame=true` puede publicar el
block `INVALID_URL`. El callback String deprecated sigue bloqueando/abandonando URLs no
permitidas, pero no publica UI porque no expone ownership de frame. No cambian allowlists,
TTL/epoch, issuer/keyUsage/EKU, host/port, preference barrier, TLS ni release profiles.

**Verificación:** RED directo sobre subframe off-origin, controles positivos same-origin y
main-frame, callback legacy UI-silent, full Debug/QA JVM, lint/build, Python, Go, artifact y
release fail-closed. La evidencia automatizada no demuestra ni amplía E2E de AEAT/Carné
Joven ni sustituye validación física de compatibilidad del portal.

### T20. Una limpieza confirmada deja activo el contexto bridge anterior hasta reload

**Riesgo:** el usuario confirma borrar datos del sitio o todos los datos WebView, pero la
invalidez del contexto native se aplaza hasta `onPageStarted`. Mientras el documento remoto
anterior sigue siendo el main-frame/origin actual, puede emitir una nueva solicitud Afirma o
MiniApplet después de la confirmación. Cancelar solo la solicitud ya visible no bloquea esa
nueva entrega; en la limpieza global el callback asíncrono de cookies amplía la ventana.

**Controles:** ambas acciones confirmadas abandonan primero Client TLS y llaman
`advanceNavigationEpoch()` antes de cancelar signing y antes de `clearOrigin` o
`clearAllConfirmed`. Ese primitive abandona replies MiniApplet pendientes, incrementa la
generación native y notifica al owner de firma. Un `onPageStarted` posterior puede avanzar la
generación otra vez; no se interpreta el epoch como contador de páginas. La lease existente de
la limpieza global sigue ligando el completion al WebView iniciador.

**Verificación:** regression de source/order RED→GREEN para ambos handlers, controles
MiniApplet de epoch stale, full Debug/QA JVM, lint/build, Python, Go, artifact y release
fail-closed. No se modifica la policy de origin, el alcance de cookie deletion ni el protocolo
de firma; el E2E físico del portal sigue separado.


### T21. Поздний transport callback загрязняет diagnostic provenance после потери owner

**Риск:** tri-phase HTTP выполняется вне lifecycle owner и может завершить transport callback
после cancellation, timeout, completion или замены signing request. Координатор уже умел
игнорировать такой callback для UI/signing state, но activity-level logger ранее записывал
санитизированный route event безусловно. В результате diagnostic mirror мог содержать
наблюдение от уже неактивной операции и ошибочно выглядеть относящимся к текущему состоянию.
Событие не содержит request ID, URL, host, credential или payload, поэтому утечка raw secret не
воспроизведена; риск — происхождение/корреляция diagnostics.

**Контроли:** `SigningCoordinator.onTunnelRouteEvent` является единственным owner-decision для
route observation и возвращает `false` при отсутствии active operation, несовпадающем request
или cancellation/non-active state. `MainActivity` вызывает
`SanitizedLogger.recordTunnelRouteEvent` только после `true`. Owned direct-fallback observation
сохраняется без UI mutation; secure-tunnel stages сохраняют прежние `Signing` /
`ConnectingSecurely` transitions. Схема события и network/TLS/signing policies не расширены.

**Верификация:** source regression RED→GREEN, state/ownership tests для foreign,
pre-confirmation, active, cancelled и post-completion cases, adjacent route tests, full
Debug/QA JVM, lint/build, Python, Go, Android artifact и release fail-closed gates. Реальный
portal/device E2E не требуется и из этих тестов не выводится.

### T22. La limpieza global deja la caché WebView o informa éxito sin owner activo

**Riesgo:** la acción confirmada de borrar todos los datos web elimina cookies y WebStorage,
pero si no borra la caché de recursos de WebView pueden sobrevivir respuestas o recursos de
portales ya abiertos. Además, si el WebView iniciador desaparece por lifecycle/render process o
una barrera de preferencia, un owner nullable puede omitir el borrado de caché y aun así iniciar
la eliminación asíncrona de cookies/WebStorage, terminando con un éxito parcial engañoso.

**Controles:** solo la acción global confirmada usa `WebView.clearCache(true)`, después de
`stopLoading()` y antes de la eliminación global de cookies/WebStorage. La acción de sitio actual
no usa esa API porque su alcance es application-wide, no por origin. La lease de completion está
tipada como `WebView` no nullable. Si `webViewRef` no tiene owner activo, se invalida cualquier
lease anterior, se publica el estado de fallo existente y no se inicia una eliminación parcial.
Con owner válido, el callback final solo se consume una vez y la recarga se ejecuta únicamente si
ese mismo WebView sigue siendo el owner actual. Se conservan la invalidación de navigation epoch,
el abandono Client TLS y la cancelación de firma previos al borrado.

**Verificación:** dos RED→GREEN específicos, incluido el caso null-owner hallado por review
independiente antes del commit; full Debug/QA JVM 564/564 por variante; lint/build, Python, Go,
Android artifact y release fail-closed pasan. `clearCache(true)` cubre la caché de recursos y sus
archivos de disco según la API, pero esta evidencia no afirma el borrado de todas las clases de
persistencia WebView ni sustituye E2E físico del portal.

### T23. El callback sin cookies se interpreta como fallo del borrado global

**Riesgo:** `CookieManager.removeAllCookies` entrega un Boolean que indica si se eliminó alguna
cookie. Interpretarlo como éxito/fallo convierte el caso válido «cero cookies presentes» en fallo,
aunque WebStorage ya se haya borrado. El usuario recibe un estado de limpieza engañoso y se omite
la recarga normal ligada al owner, sin evidencia de que una cookie haya sobrevivido.

**Controles:** `SiteDataCleaner` considera la llegada del callback como finalización de la operación.
`cookiesRemoved=false` es un no-op completado y no fuerza `flush`; `cookiesRemoved=true` conserva
el `flush` explícito y exige que termine sin excepción. Fallo de WebStorage, excepción síncrona al
iniciar `removeAllCookies` y excepción del `flush` requerido siguen devolviendo fallo. No cambia el
alcance por origin, la admisión de owner/caché G31 ni los límites de navigation epoch, Client TLS o
firma.

**Verificación:** RED específico `job_20260808_235600_41550741` con fallo exacto de semántica,
GREEN Debug+QA, controles negativos, suites adyacentes 34/34 por variante y full JVM 568/568 por
variante; lint/build, Python, Go, Android artifact y release fail-closed pasan. La evidencia es JVM
y no sustituye una validación física de WebView/portal.

### T24. Un nombre de certificado del proveedor reordena visualmente la UI nativa

**Riesgo:** `OpenableColumns.DISPLAY_NAME` procede de un `ContentProvider` externo. Aunque el
archivo sea admisible como PKCS#12, caracteres Unicode de control bidireccional pueden alterar el
orden visual del nombre persistido y mostrado dentro de la UI nativa de confianza. El riesgo es
suplantación/integridad de presentación; no demuestra exposición de bytes del certificado,
contraseña, clave privada ni firma.

**Controles:** `CertificateRepository` elimina en el límite de presentación exactamente
`Bidi_Control` U+061C, U+200E..U+200F, U+202A..U+202E y U+2066..U+2069 antes de persistir el
`displayName`. Se conservan Unicode imprimible ordinario, límite de 256 caracteres y fallback
vacío. Para `application/octet-stream`, la admisión por extensión sigue usando el nombre original
trimmed antes de esta sanitización; por tanto el cambio no amplía archivos aceptados ni modifica
URI/SAF, parsing PKCS#12, contraseña o firma.

**Verificación:** RED específico con U+202E/U+2066, GREEN Debug+QA, suites adyacentes, reviewer
independiente sin hallazgos Critical/Important, full JVM 569/569 por variante, lint/build,
Python/Go, artifact y release fail-closed. La evidencia es automatizada y no sustituye picker SAF,
certificado físico ni E2E de portal.

### T25. Un nombre de certificado persistido antes de G33 reaparece tras una actualización

**Riesgo:** una versión anterior podía persistir `display_name` con controles bidireccionales. G33
cerró la selección nueva, pero una referencia antigua seguía entrando por
`PreferencesCertificateReferenceStore.read()` sin normalización y podía volver a mostrarse en la UI
nativa después de actualizar la aplicación.

**Controles:** selección y lectura persistida comparten `CertificateDisplayNamePolicy`, que conserva
el contrato G33 y elimina C0/DEL y `Bidi_Control` U+061C, U+200E..U+200F, U+202A..U+202E y
U+2066..U+2069, con límite de 256 caracteres, `trim` y fallback. La lectura no ejecuta una migración
DataStore ni renueva otro estado; sólo normaliza el valor devuelto. La admisión octet-stream sigue
usando el nombre original trimmed del proveedor antes de la política de presentación.

**Verificación:** RED específico de DataStore legacy; GREEN store/repository 20/20 por variante;
reviewer sin hallazgos Critical/Important; full JVM 570/570 por variante; lint/build, Python/Go,
artifact y release fail-closed pasan. La ausencia de escritura en `read()` se verifica por inspección
de la implementación; el test no hace snapshot separado del DataStore. No se infiere E2E físico.
