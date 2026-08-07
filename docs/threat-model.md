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
`onAfirmaRequest`; subframes y el callback String deprecated se consumen con
`UNTRUSTED_AFIRMA_ORIGIN`. De forma independiente, `OpenExternal` solo puede llegar a
la aplicación desde un callback moderno main-frame; subframe/legacy se consume con
`UNTRUSTED_EXTERNAL_NAVIGATION` y sin `openExternal` ni callback UI de bloqueo.
**Verificación:** regressions Debug/QA con controles positivos main-frame y negativos
subframe/legacy para Afirma, HTTPS externo e `intent:` con browser fallback;
instrumentation sin resolución de Play/AutoFirma. La cobertura automatizada no
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
plano ni Preferences/logs. El registro queda ligado mediante AAD a la referencia
del certificado y a los timestamps originales de emisión/expiración. La clave AES
es material no exportable de Android Keystore; en API 28+ el provider actual exige
que el dispositivo esté desbloqueado para usarla. `noBackupFilesDir` excluye el
registro cifrado del backup/transfer. Además, el manifest mantiene `allowBackup=false`
y los recursos legacy/Android 12+ excluyen
explícitamente `root`, `file`, `database`, `sharedpref`, `external` y los cuatro
dominios device-protected, tanto para cloud backup como para device transfer; por tanto
`allowBackup=false` no es la única barrera D2D. La
restauración automática conserva la expiración original y no renueva la ventana.
Los buffers temporales `CharArray`/`ByteArray` en claro se limpian best-effort.

**Semántica lifecycle:** bloqueo manual, clear session, cambio u olvido del
certificado, expiración, reference mismatch, ciphertext malformado/manipulado o
unlock cacheado fallido eliminan el registro. Background, process death,
force-stop, reinicio del dispositivo y actualización ordinaria no eliminan por sí
solos un registro todavía válido. Ante memory pressure, `CertificateSession`
suelta primero la identidad en memoria, pero el ViewModel puede restaurarla desde
el cache válido; por tanto memory pressure o process death no garantizan un estado
locked persistente dentro de la ventana de 24 horas.

**Riesgo residual:** antes de expirar el registro, código que ejecute con los
privilegios de la app en un dispositivo elegible y desbloqueado puede intentar
activar la recuperación local. En API 26–27 no existe la protección adicional
`setUnlockedDeviceRequired(true)` usada por este provider desde API 28. Un
dispositivo/root con control del proceso sigue fuera de la garantía. Recuperar la
identidad no omite la confirmación independiente exigida para cada solicitud de
firma. La correspondencia de clave se prueba firmando un nonce, no exportando
bytes.

**Verificación:** tests de `CertificateUnlockCache`, `CertificateSession` y
`CertificateViewModel` cubren cifrado/tamper/expiración, limpieza, process
recreation y memory-pressure recovery; búsquedas estáticas cubren APIs/campos
prohibidos; se inspeccionan backup config y logs. La evidencia física P07C confirma
un cold launch tras terminación del proceso sin nuevo password prompt y sin
registrar el secreto.

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
privado y export sanitizado; `FLAG_SECURE` state-driven en `MainActivity` durante
password, unlock, certificado desbloqueado, catálogo asociado, WebView de portal
y cualquier estado de firma no idle; no copiar secreto; `allowBackup=false` y
exclusiones explícitas completas por dominio en legacy/cloud/D2D. La pantalla inicial
sin certificado y el probe debug
aislado no heredan el flag.
**Verificación:** tests del redactor y de la policy de estados, instrumentation
sobre unlock/recreate/lock, inspección de exports/logcat/manifest y `dumpsys
window` en dispositivo físico para certificado restaurado y WebView activo.

### T11. Build/release o cadena de suministro comprometidos

**Riesgo:** Action/tag mutable, wrapper o dependencia sustituida, secreto en el
historial, toolchain vulnerable, APK alterado, debug habilitado o pérdida del
keystore.
**Controles:** workflows con `contents: read`, sin `pull_request_target` y con
Actions fijadas a SHA de 40 caracteres; Gradle 9.4.1 wrapper/distribution y
artifacts resueltos verificados por SHA-256 sin trusted wildcard; Gitleaks pinned
sobre historial completo; Dependabot para Gradle, Go y Actions; Go 1.26.5 con
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
`setGeolocationEnabled(false)`; el manifest no declara `ACCESS_COARSE_LOCATION` ni
`ACCESS_FINE_LOCATION`; `JuntaWebChromeClient` rechaza tanto el prompt específico de
geolocalización como `PermissionRequest` genérico. File/content access y ventanas
múltiples siguen desactivados de forma independiente.
**Verificación:** source regression exige el setter explícito; pre-commit scan comprueba
la ausencia de permisos location y conserva el callback `allow=false, retain=false`;
lint/build compilan también el contrato instrumentado. No se afirma E2E físico de
geolocalización.

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
