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

### T4. Callback o server URL produce SSRF

**Riesgo:** acceso a localhost/red privada o envío de cookies/datos a tercero.
**Controles:** HTTPS exclusivo, hosts exactos, sin userinfo, puerto restringido,
rechazo de IP/localhost/private/reserved, DNS y redirects revalidados, cookies
solo al mismo host, límites de respuesta.
**Verificación:** unit tests de URL, redirect y cookie isolation; test de DNS
abstraction con IP no pública.

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

### T8. Confusión, replay o entrega JS inyectada

**Riesgo:** resultado A vuelve a solicitud B o se ejecuta JavaScript construido
con concatenación.
**Controles:** estado de un solo uso enlazado a requestId+origin+navigation;
mensajes JSON; callbacks guardados como referencias dentro del shim; no se
construye código JS con datos; solicitudes pendientes borradas en todos los
terminales.
**Verificación:** tests de requestId repetido, origin cambiado, navegación,
cancelación y payload con caracteres de inyección.

### T9. Cookies cruzan de host o sesión expirada se toma por éxito

**Riesgo:** secuestro de sesión o firma enviada a login HTML.
**Controles:** obtención/aplicación por URL exacta, redirects allowlisted,
`Set-Cookie` sincronizado sin logs, detección de 401/403/login redirect/HTML
inesperado.
**Verificación:** CookieBridge tests y respuestas simuladas.

### T10. Diagnóstico, clipboard, screenshots o backup filtran datos

**Riesgo:** secreto disponible a otras apps o soporte.
**Controles:** logger con esquema allowlist, hashes truncados, almacenamiento
privado, export sanitizado; `FLAG_SECURE` mientras se introduce contraseña o se
muestra material sensible; no copiar secreto; backup desactivado.
**Verificación:** tests del redactor, inspección de exports, logcat y manifest.

### T11. Build/release comprometido

**Riesgo:** APK alterado, debug habilitado o pérdida del keystore.
**Controles:** wrapper fijado, checksums/dependency locking donde sea viable,
release no debug, WebView debugging condicionado a `BuildConfig.DEBUG`,
keystore fuera del repo con permisos privados, v2/v3, `apksigner`, `zipalign`,
SHA-256 y fingerprint registrados.
**Verificación:** release checklist y análisis del APK final.

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
- momento exacto en que `cargarMiniApplet` intenta WebSocket/intent;
- si la página cambia el algoritmo o endpoints tras autenticación;
- aceptación final por el portal y bloqueo correcto tras background.

Estos puntos no se cerrarán por inferencia de código estático. Se consideran
resueltos únicamente con capture sanitizado y ejecución real autorizada.
