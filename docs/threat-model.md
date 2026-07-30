# Threat model — Junta Firma Mobile

Fecha: 2026-07-11
Método: límites de confianza, activos, capacidades del atacante, rutas de abuso
y mitigaciones verificables.

## 1. Activos

| Activo | Impacto de compromiso |
|---|---|
| Clave privada PKCS#12 | Suplantación y firmas no autorizadas |
| Contraseña PKCS#12 | Desbloqueo de la identidad si se obtiene el archivo |
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
  │
  ▼
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

### T3. Intent/Play fallback escapa a AutoFirma externa

**Riesgo:** confusión de app o envío de datos a otro paquete.
**Controles:** interceptar `afirma://`, `intent://`, `market://` y URLs Play;
rechazar package/component explícitos no aprobados; nunca usar
`es.gob.afirma`; evento sanitizado de fallback.
**Verificación:** instrumentation tests sin resolución de Play/AutoFirma.

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

**Riesgo:** firma posterior sin consentimiento.
**Controles:** contraseña `CharArray` borrada en `finally`; no Preferences ni
logs; no `PrivateKey.encoded`; P12 no se copia por defecto; identidad solo en
memoria; bloqueo en lifecycle/timeout/manual/process death; `allowBackup=false`.
La correspondencia de clave se prueba firmando un nonce, no exportando bytes.
**Verificación:** tests del ciclo de sesión y búsquedas estáticas de APIs/campos
prohibidos; inspección de backup config y logcat.

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
y cualquier estado de firma no idle; no copiar secreto; backup desactivado. La
pantalla inicial sin certificado y el probe debug aislado no heredan el flag.
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
race/vet/build/`govulncheck`; OSV pinned sobre manifests Python/Go explícitos.
Release no acepta debug-key fallback y falla cerrado sin keystore privado. Cada
APK se valida con `zipalign`, `apksigner`, signer count, manifest y canarios.
**Verificación:** policy tests del workflow, checksums oficiales, history scan,
unit/lint/build, vulnerability gates y análisis del APK final.
**Riesgo residual:** Gradle verification metadata demuestra identidad/integridad,
no ausencia de vulnerabilidades. El ledger contiene build/test tooling y no se
trata como runtime lockfile. El grafo Gradle completo requiere un SCA separado y
revisado antes de afirmar cobertura CVE total. El race gate se ejecuta en Linux;
Android/arm64 no lo soporta.

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

## 5. Decisiones explícitas

- No localhost WSS, puertos 63117/63118/63119/17629, CA local ni trust bypass.
- No pinning hasta disponer de rotación operativa.
- No copia del P12 en app-private storage en el alcance inicial; por tanto no se
  introduce todavía AES-GCM/Keystore para ese archivo.
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
