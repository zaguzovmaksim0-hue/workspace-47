# Plan de implementación del cliente universal de sedes públicas

Fecha: 2026-07-15

Estado: investigación y diseño completos; production gate cerrado hasta el
E2E manual Junta de la tarea 3

Documentos vinculantes:

- `docs/compatibility/spanish-government-signing-matrix.md`;
- `docs/superpowers/specs/2026-07-15-spanish-government-universal-client-design.md`;
- `docs/protocol-observations.md`;
- `docs/threat-model.md`.

Objetivo: evolucionar incrementalmente el contorno funcional Junta hacia un
motor profile/adapter fail-closed, con navegación browse-only desconocida,
autenticación TLS cliente separada y perfiles añadidos únicamente tras
evidencia y E2E propios.

## Reglas globales de ejecución

- No crear proyecto, package ni branding nuevos.
- No cambiar production code antes del gate manual Junta.
- Mantener `JuntaOriginPolicy` y las clases Junta como fachadas hasta que sus
  consumidores migren y el E2E se repita.
- Un profile/adapter por commit; no mezclar portal nuevo con refactor core.
- No activar profile desde una matriz o test estático.
- No usar certificado real en tests, terminal, ADB, UIAutomator ni fixtures.
- No guardar query, cookies, challenge, documento, firma, certificado ni datos
  personales en evidencia.
- No abrir AutoFirma/Play/Package Manager como fallback genérico.
- No push, merge, publicación ni instalación externa sin instrucción expresa.
- Si falla un gate por el cambio, corregirlo antes del commit. Clasificar por
  separado fallos preexistentes, de entorno o no verificables.

Cada milestone de production termina con los diez gates completos, aunque el
diff parezca no tocar WebView o criptografía:

1. focused tests;
2. full unit suite;
3. instrumentation completa en el POCO F6 Pro;
4. lint debug/release;
5. clean debug/release build;
6. firma/alignment APK;
7. manifest/DEX/resources;
8. credential/security scans;
9. diff completo y `git diff --check`;
10. commit local limpio.

Comandos base:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease \
  assembleDebug assembleRelease --console=plain
./tools/bootstrap-termux-aapt2.sh verify
git diff --check
git status --short
```

Instrumentation se ejecuta por el runner Android del dispositivo; manifest y
DEX se inspeccionan, APK se verifica con `apksigner`, alignment se comprueba con
la capacidad real del toolchain y se ejecutan scans de credenciales/patrones
TLS. Cada catálogo/profile/adapter participa en una suite contractual
parametrizada; un profile o adapter sin caso de test hace fallar el milestone.

## Tarea 0 — Baseline reproducible

Estado: completada en `8862284`.

- [x] Identificar worktree/branch y contour Junta.
- [x] Ejecutar unit, lint, clean debug/release build.
- [x] Ejecutar 21 instrumentation tests en el POCO F6 Pro mediante Android
  shell porque el `adb` del SDK no es ejecutable en Termux ARM.
- [x] Registrar 191 unit tests, APK SHA-256, manifest, signing, alignment,
  credential scan y limitaciones en `docs/test-report.md` y
  `docs/device-qa.md`.
- [x] Confirmar que Junta sigue `EXPERIMENTAL` y que falta E2E real.

## Tarea 1 — Matriz de evidencia oficial

Estado: completada en `c515f4b`
(`docs: map official signing compatibility`).

- [x] Fijar Cliente @firma y firma-android a commits oficiales.
- [x] Mapear MiniApplet/AutoScript, `afirma://`, `intent://`, WSS,
  Storage/Retrieve, PRE/POST, firma local y `ClientCertRequest`.
- [x] Investigar AGE/REG, AEAT, Seguridad Social/Import@ss, SEPE, DGT,
  Justicia, comunidades, diputación, ayuntamientos y universidades.
- [x] Separar `VERIFIED_CONTRACT` estático de implementación y E2E.
- [x] Dejar campos desconocidos como `No verificado`.
- [x] Pasar auditoría independiente de hechos/links y corregir hallazgos.
- [x] Ejecutar `git diff --check`, revisar diff completo y hacer commit solo de
  la matriz.

## Tarea 2 — Diseño y plan universal

Estado: completada; commit previsto
`docs: design universal government client`.

- [x] Definir catálogo estricto, seis trust modes y capability model.
- [x] Definir adapters sin acceso a clave privada.
- [x] Definir request ID/epoch nativos, reply channel tipado, TTL y single-use.
- [x] Separar y limitar `ClientCertRequest` con armado top-level.
- [x] Definir migración incremental, promoción de profiles, QA y rollback.
- [x] Pasar auditoría de seguridad/implementabilidad independiente.
- [x] Corregir contradicciones con el código actual, matriz o threat model.
- [x] Ejecutar `git diff --check` y commit de spec+plan, sin production code.

## Tarea 3 — Gate manual de regresión Junta

Commit documental previsto: `test: record Junta regression e2e`.

### 3.1. Precondiciones automáticas

- [ ] Confirmar worktree limpio y HEAD de diseño esperado.
- [ ] Repetir focused signing tests, suite unit, lint y builds.
- [ ] Confirmar SHA-256 del APK instalado frente al APK recién construido; si
  difiere, instalar con rollback APK ya respaldado, sin launch automático.
- [ ] Ejecutar instrumentation completa en dispositivo.
- [ ] Verificar red, fecha/hora, WebView provider y start URL oficial.

### 3.2. Acción exclusivamente manual

La app se abre en la pantalla normal. El usuario:

1. elige su PKCS#12 mediante SAF dentro de la app;
2. escribe la contraseña únicamente en el campo protegido de la app;
3. abre el login Junta oficial;
4. solicita el acceso con certificado;
5. comprueba sitio, formato y advertencia SHA-1;
6. pulsa `Firmar` una sola vez;
7. espera la respuesta y confirma si el portal aceptó el acceso.

No se captura pantalla con datos, no se copia error bruto y no se automatiza
ningún paso. El usuario comunica solo uno de estos resultados:

- `aceptado por el portal`;
- `cancelado por mí`;
- `falló`, más el código sanitizado visible de la app.

### 3.3. Cierre

- [ ] Si el portal acepta, registrar fecha UTC, APK hash, profile/version,
  operación/formato/algoritmo y resultado cerrado en `docs/device-qa.md` y
  `docs/test-report.md`; promover Junta a `VERIFIED_E2E` solo para ese contrato.
- [ ] Si falla, reproducir sin datos sensibles, localizar causa y corregir solo
  el contorno Junta. Repetir todos los gates; no abrir tareas 4+.
- [ ] Revisar que docs no contienen identidad, certificado, query, challenge,
  payload, firma, cookie o mensaje remoto bruto.

**Gate:** ninguna tarea de production 4–12, incluidas sus subtareas con letra,
empieza hasta completar 3.3 con
aceptación del portal.

## Tarea 4 — Catálogo y registry puros

Commit previsto: `feat: add strict site profile catalog`.

Archivos previstos:

- crear `app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt`;
- crear `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`;
- crear `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt`;
- crear `app/src/main/res/raw/site_profiles_v1.json`;
- crear tests equivalentes bajo `app/src/test/.../profile/`;
- adaptar `network/JuntaOriginPolicy.kt` solo como fachada, sin cambiar outputs.

Pasos:

- [ ] RED: schema version, unknown/duplicate keys, unknown enum/protocol ID,
  wildcard, userinfo, path en origin, puerto, IP, endpoint ID/override,
  solapamiento de origins/autoridades sensibles, profile duplicado, SHA-1 sin
  capability y capability/policy incoherente.
- [ ] Implementar parser estricto con keys cerradas y registry inmutable.
- [ ] Catalogar solo el ID production estable `junta-andalucia` como profile de
  firma inicial; los portales
  de matriz no reciben activación implícita.
- [ ] Añadir test dorado que compara origins/endpoints Junta actuales y exige
  misma start URL, adapter, endpoint IDs, formato, packaging, modo, algoritmo,
  extraProperties cerradas y callback.
- [ ] Rechazar `QA_ONLY` activo en release mediante artifact test.
- [ ] Mantener consumidores existentes sobre la fachada y demostrar diff de
  comportamiento vacío.
- [ ] Gates globales, review y commit.

Rollback: revertir el commit; todas las clases Junta siguen siendo fuente de
verdad anterior.

## Tarea 5A — URL policy y trust model puros

Commit previsto: `feat: add fail-closed browser trust model`.

Archivos previstos:

- crear `browser/BrowserUrlPolicy.kt`;
- crear `browser/BrowserTrustController.kt`;
- crear `browser/SensitiveFlowInvalidator.kt`;
- añadir unit tests puros por trust mode y transición.

Pasos:

- [ ] RED: los seis modes, unknown HTTPS, HTTP/custom/file/data/javascript,
  userinfo, IP/IDNA, port, redirect directo/sin profile, iframe y cambio de
  profile.
- [ ] Implementar normalización HTTPS y resolución exacta desde top-level.
- [ ] Invalidar firma, callback, tri-phase, selección y client-auth antes de
  cada navegación/reload/Back/Forward/profile switch.
- [ ] Mantener consumidores Junta sin cambios y ejecutar los diez gates.
- [ ] Review y commit.

Rollback: no hay wiring production; revertir elimina solo modelos puros.

## Tarea 5B — Wiring browse-only y navegación WebView

Commit previsto: `feat: enable browse-only unknown sites`.

Archivos previstos:

- crear `browser/BrowserNavigationPolicy.kt`;
- adaptar incrementalmente `JuntaNavigationPolicy.kt`,
  `JuntaWebViewClient.kt`, `TrustedJuntaWebView.kt` y `ui/BrowserScreen.kt`;
- añadir instrumentation por trust mode/transición.

Pasos:

- [ ] RED: iframe, redirect inesperado, cambio de profile y custom URI durante
  salida de trusted.
- [ ] Permitir unknown HTTPS dentro del WebView solo como `BROWSE_ONLY`, sin
  bridge/cookie export/certificate; conservar `EXTERNAL_ONLY` explícito.
- [ ] Mostrar el mensaje español exacto y botón de navegador externo.
- [ ] Ejecutar instrumentación hostil y repetir Junta E2E porque cambia
  navegación.
- [ ] Gates globales, visual QA real móvil, review y commit.

Rollback: fachada Junta vuelve a la policy anterior; no se conserva estado de
confianza nuevo en DataStore.

## Tarea 6 — Registry de input/execution adapters y extracción Junta

Commit previsto: `refactor: register Junta signing adapter`.

Archivos previstos:

- conservar `signing/SigningProtocolAdapter.kt` como fachada PRE/POST actual;
- extender `signing/SigningModels.kt`;
- crear `signing/ProtocolInputAdapter.kt`,
  `signing/TriPhaseExecutionAdapter.kt` y `signing/ProtocolAdapterRegistry.kt`;
- crear `signing/MiniAppletCallbackAdapter.kt`;
- crear `signing/SigningResultEncoder.kt`;
- adaptar `JuntaTriPhaseAdapter.kt`, `MiniAppletBridgeAdapter.kt` y
  `SigningCoordinator.kt` mediante wrappers compatibles;
- añadir contract tests que prohíban `PrivateKey`/`CertificateSession` en APIs.

Pasos:

- [ ] RED: unknown adapter, operation/capability mismatch,
  format/packaging/mode/algorithm, endpoint ID, extraProperty desconocida o
  no permitida, callback arity y no-fallback.
- [ ] Separar `ProtocolInputAdapter`, callback encoding y
  `TriPhaseExecutionAdapter` sin redefinir la interfaz actual ni cambiar bytes
  de Junta.
- [ ] Reconstruir properties desde `SignOperationPolicy`; el request de página
  solo puede confirmar valores exactos o reglas tipadas, nunca reenviar un mapa
  libre.
- [ ] Mantener `CertificateSession` y `LocalSignatureEngine` como única
  frontera de clave.
- [ ] Comparar fixtures Junta byte a byte y mantener todos los tests previos.
- [ ] Repetir Junta E2E por afectar coordinación/callback.
- [ ] Gates globales, API review y commit.

## Tarea 6A — Transport endpoint-scoped sin DNS TOCTOU

Commit previsto: `security: bind profile transport to approved endpoints`.

Archivos previstos:

- adaptar `network/SafeNetworkUrlPolicy.kt` y `ProfileHttpTransport.kt`;
- crear `network/EndpointScopedTransport.kt`, `ResolvedEndpointSet.kt` y
  `ProtocolResultVerifier.kt`;
- ampliar tests de red y adapters Junta.

Pasos:

- [ ] RED: endpoint ID desconocido, override URL/method/header/MIME/codec,
  redirect, DNS privado/mixto, DNS mutable entre check/connect, SNI/hostname,
  tamaño, timeout, HTML y final result sin binding local.
- [ ] Hacer que resolver produzca un set inmutable aprobado y que el executor
  solo pueda conectar a ese set sin segunda resolución, preservando hostname
  original para SNI y hostname verification.
- [ ] Si el stack actual no permite probar lo anterior, evaluar un cliente con
  DNS inyectable usando documentación oficial vigente y añadirlo solo tras
  justificar versión/dependencia; no escribir hostname verifier propio.
- [ ] Derivar URL, método, headers, MIME, codec y límites únicamente del
  `EndpointId` catalogado.
- [ ] Añadir verifier sintáctico/semántico y de contenedor/binding antes de
  entregar callback; un `OK` remoto aislado no basta.
- [ ] Comparar bytes Junta, ejecutar su E2E y todos los gates.

**Gate:** ninguna tarea de segundo profile empieza si el test DNS mutable o el
result verifier no son verdes.

## Tarea 7A — Epoch nativo y estado monotónico

Commit previsto: `security: bind requests to native navigation epoch`.

Archivos previstos:

- crear `browser/SecureRequestIdGenerator.kt`;
- extender `browser/NavigationId.kt` o sustituirlo por
  `NativeNavigationEpoch.kt` conservando fachada temporal;
- adaptar `PendingSignRequestStore.kt`, `SigningCoordinator.kt`,
  `WebViewStateHolder.kt` y `MainActivity.kt`;
- ampliar unit/instrumentation de lifecycle.

Pasos:

- [ ] RED: JS-chosen request ID, fallo de SecureRandom, reload/Back/restore,
  reloj civil alterado, TTL, replay, doble terminal y request concurrente.
- [ ] Generar process session/request ID solo en nativo; bloquear si falla la
  entropía y eliminar todo `Math.random()` del camino de seguridad.
- [ ] Usar deadline monotónico; el tiempo nunca correlaciona llamadas.
- [ ] Ligar request a profile/version/origin/epoch/fingerprint y revalidar en
  PRE, firma, POST y reply.
- [ ] No restaurar Bundle WebView opaco de un flow trusted/pending; solo URL
  sanitizada/profile, con epoch nuevo.
- [ ] Repetir instrumentación hostil y Junta E2E.
- [ ] Gates globales y commit.

## Tarea 7B — ABI bridge REQUEST/ACK/CANCEL/RESULT

Commit previsto: `security: version the native signing bridge`.

Archivos previstos:

- adaptar `WebMessageProtocol.kt`, `WebMessageBridge.kt`,
  `MiniAppletBridgeAdapter.kt` y `res/raw/afirma_shim.js`;
- adaptar WebView creation/profile switching y sus tests.

Pasos:

- [ ] RED: pre-ACK result/cancel, wrong proxy/handle/requestId, duplicate keys,
  unknown fields, subframe, spoofed origin, reordered/duplicate terminal,
  arbitrary callback string y listener restante tras profile switch.
- [ ] Implementar ABI v1 exacto `REQUEST -> ACK -> RESULT`, con CANCEL opcional
  y tuple nativa `(replyProxy, handle, requestId, profile, origin, epoch)`.
- [ ] Usar solo reply proxy y referencias de clausura; prohibir callback
  string/evaluateJavascript.
- [ ] En cambio de profile retirar script/listener en UI; si no se demuestra,
  destruir WebView. Registrar origins exactos antes del primer load del nuevo.
- [ ] Repetir instrumentación hostil, Junta E2E, gates y commit.

## Tarea 7C — Selección de certificado separada

Commit previsto: `feat: add scoped certificate selection protocol`.

Archivos previstos:

- crear `signing/CertificateSelectionModels.kt` y
  `CertificateSelectionCoordinator.kt`;
- ampliar input adapters, callback encoder, UI y tests.

Pasos:

- [ ] RED: `selectcert` sin capability, origin/epoch/TTL, disclosure cancel,
  replay, callback arity, navegación y process recreation.
- [ ] Modelar `CertificateSelectionRequest` separado de firma y TLS auth.
- [ ] Pedir consentimiento para compartir el certificado público, aplicar
  filtros y devolver un único `certificateB64` tipado.
- [ ] Probar que nunca se accede a `PrivateKey` ni al motor de firma.
- [ ] Gates, instrumentación y commit.

## Tarea 7D — Release sin probe

Commit previsto: `security: remove protocol probe from release`.

- [ ] Dividir `afirma_shim.js` entre bridge production y overlay debug.
- [ ] Eliminar de release Activity, recorder, listener, strings y hooks de
  probe; mantener diagnóstico seguro solo en `src/debug`.
- [ ] Inspeccionar manifest/DEX/resources de release y añadir artifact tests.
- [ ] Repetir Junta E2E, diez gates y commit.

## Tarea 8A — Aislamiento y policy de Client TLS Authentication

Commit previsto: `security: isolate client certificate requests`.

Archivos previstos:

- crear paquete `clientauth/` con `ClientAuthModels.kt`,
  `CertificateAuthMetadata.kt`, `ClientCertificateAuthAdapter.kt`,
  `ClientCertPreferenceBarrier.kt` y policy;
- crear factory de WebView one-shot sin documento previo y conectar el
  callback en el WebViewClient genérico;
- añadir fake `ClientCertRequest` y tests unit/instrumentation.

Pasos:

- [ ] RED: WebView usado/no armado, callback después de document-start,
  iframe/subresource, top-level mismatch, host/port, key types, principals,
  empty issuers, redirect, race, replay, cancel/ignore, clear callback tardío o
  ausente, profile switch y process recreation.
- [ ] Implementar WebView nuevo one-shot e intent top-level single-use/TTL
  previo a su primera navegación. Si el challenge no ocurre en el primer
  handshake anterior a documento/subresources, dejar capability unsupported.
- [ ] Validar `CertificateAuthMetadata` público (algoritmo/tamaño, key
  usage/EKU, vigencia y hashes DER de principals). Esta subtarea solo puede
  `ignore()`; todavía no expone `proceed()`.
- [ ] Implementar barrier asíncrono de `clearClientCertPreferences`; ninguna
  carga comienza antes del callback. Timeout/error deja WebView external-only.
- [ ] Gates globales, security review y commit.

## Tarea 8B — Consentimiento y resolución ClientCert

Commit previsto: `feat: add scoped client certificate authentication`.

Archivos previstos:

- crear `clientauth/ClientAuthCoordinator.kt`;
- añadir diálogo Compose distinto de firma;
- ampliar fake request, lifecycle e instrumentation.

Pasos:

- [ ] RED: confirmación/cancel/ignore doble, petición expirada, salida de
  profile, background/lock, process death y scoped key access.
- [ ] Mostrar organización, autoridad, certificado y propósito de login.
- [ ] Resolver la instancia exacta una vez; `ignore` técnico, `cancel` manual,
  `proceed` confirmado, con clave dentro del scope final del coordinator.
- [ ] No activar `CLIENT_TLS_AUTH` en ningún profile mientras falte captura
  exacta y prueba de primer handshake del portal.
- [ ] Gates globales, security review y commit.

## Tarea 9 — UX universal y reporte sanitizado

Commit previsto: `feat: show site trust and signing capabilities`.

Archivos previstos:

- adaptar `ui/BrowserScreen.kt`, `BrowserAddressBar.kt`,
  `SigningConfirmationDialog.kt`, `SigningStatusDialog.kt` y strings;
- crear componentes de profile/status/action y reporte;
- conservar nombre, launcher y estilo visual actuales.

Pasos:

- [ ] Mostrar profile, compatibility status, trust mode y dominio por
  separado.
- [ ] Mostrar entrada/firma/selección, formato, algoritmo y warning SHA-1.
- [ ] Nunca presentar `VERIFIED_CONTRACT` como E2E.
- [ ] Mostrar unknown-site copy exacta y botón externo HTTPS validado.
- [ ] Generar reporte sin query, cookies, payload, callback, issuer/serial ni
  datos personales.
- [ ] Tests Compose, screenshots reales de home/trusted/browse-only/warning/
  client-auth y mobile viewport review.
- [ ] Repetir Junta E2E y gates globales; commit.

## Tarea 10 — Segundo contrato tri-phase: Universidad de Zaragoza

Commit previsto solo tras evidencia/E2E:
`feat: add Universidad de Zaragoza signing profile`.

- [ ] Volver a leer JS público vigente, registrar hash y crear fixture mínima
  sin challenge ni sesión.
- [ ] Confirmar en debug, sin firmar automáticamente, el transporte móvil,
  PRE/POST, MIME, response codec, redirects y uso real de Storage/Retrieve.
- [ ] Verificar que origin, endpoint, CAdES, SHA1 precalculado y callbacks
  coinciden con la matriz; cualquier divergencia detiene la tarea.
- [ ] Añadir profile `QA_ONLY`, exact origins/endpoints y `LEGACY_SHA1`.
- [ ] Reutilizar adapter tri-phase común; no copiar código Junta.
- [ ] Parser/origin/redirect/replay/TTL/iframe/callback/network tests.
- [ ] Ejecutar E2E manual de acceso sin producir trámite administrativo; solo
  después promover status/activation.
- [ ] Repetir Junta E2E, gates globales y commit del profile aislado.

Si el flujo exige datos/decisión personal más allá del login, detenerse en la
pantalla exacta y pedir la acción manual.

## Tarea 11 — Familias adicionales, un commit cada una

Estas subtareas no se ejecutan en paralelo sobre core y no se prometen hasta
resolver sus gaps.

### 11A. Gobierno de Aragón — MiniApplet + Storage/Retrieve

- [ ] Revalidar JS/hash/endpoints y el transporte Android actual.
- [ ] Implementar Storage/Retrieve como capability separada, IDs efímeros y
  endpoints exactos.
- [ ] Advertencia `LEGACY_SHA1`, tests, E2E y commit
  `feat: add Aragon MiniApplet profile`.

### 11B. REG/RedSARA — XAdES Detached

- [ ] Confirmar transporte móvil y respuesta aceptada; el contrato desktop no
  basta.
- [ ] Implementar XAdES solo mediante contrato oficial probado, nunca
  reutilizando CAdES por nombre.
- [ ] Tests, E2E y commit `feat: add REG signing profile`.

### 11C. ACCEDA — PAdES / callback AutoScript actual

- [ ] Elegir un procedimiento no destructivo concreto y fijar origin/path,
  algoritmo y policy; no aceptar valores arbitrarios de campos del formulario.
- [ ] Implementar `LOCAL_PADES` o transporte observado, tests, E2E y commit
  `feat: add ACCEDA signing profile`.

### 11D. AEAT — TLS client certificate

- [ ] Crear probe debug sin `proceed` que registre solo host catalogable,
  puerto, key-type enum, cantidad/hash de principals, top-level epoch y si el
  callback ocurrió antes del marker document-start del WebView one-shot.
- [ ] Observar petición exacta y demostrar primer handshake sin documento; si
  no puede distinguirse de subresource/iframe, mantener `CLIENT_TLS_AUTH`
  unsupported. Completar policy y tests hostiles solo tras esa prueba.
- [ ] Ejecutar login manual; activar profile solo tras aceptación.
- [ ] Commit `feat: add AEAT client certificate profile`.

### 11E. Comunidad de Madrid — firma local PAdES

- [ ] Confirmar PDF de entrada, formato de firma aceptado y contrato de carga
  sin subir un documento personal automáticamente.
- [ ] Implementar plan local PAdES separado, tests y verificación local.
- [ ] E2E manual y commit `feat: add Madrid local PAdES profile`.

Cada subtask reejecuta E2E Junta y nunca habilita cofirma/contrafirma por mera
presencia en una librería.

## Tarea 12 — Release QA y entrega

Commit previsto: `test: validate universal client release`.

- [ ] Clean full unit/lint/debug/release build sin cache relevante.
- [ ] Instrumentation completa en POCO F6 Pro, incluidos iframe, redirects,
  profile switching y ClientCert.
- [ ] E2E Junta y E2E separado de cada profile marcado `VERIFIED_E2E`.
- [ ] Manifest/DEX/resources: no debug probe, WebView debugging, cleartext,
  backup, testOnly ni components exportados inesperados.
- [ ] Scan de credenciales, URLs remotas de catálogo, trust-all, SSL proceed,
  arbitrary callback/evaluateJavascript y private-key export.
- [ ] `apksigner verify`, certificate report, zip alignment y gate 16 KiB con
  tool compatible; si el entorno no puede probarlo, no declarar éxito.
- [ ] SHA-256 y tamaños de debug/release/androidTest APK.
- [ ] Actualizar matriz, test report, device QA, threat model y proceso para
  añadir un profile.
- [ ] Revisar commits, worktree limpio y generar APKs para QA; no publicar.

## Criterio de cierre del objetivo completo

El objetivo solo termina cuando existen conjuntamente:

- arquitectura profile/adapter universal en production;
- Junta sin regresión y con E2E real;
- unknown HTTPS browse-only;
- catálogo local versionado/fail-closed;
- varios profiles que prueban familias independientes, cada uno con status
  honesto y E2E si se presenta como soportado;
- Client TLS auth en un portal donde el contrato esté observado;
- debug APK para QA y release APK sin tooling debug;
- matriz, commits, hashes y limitaciones finales completos.

«Usa AutoFirma» o «es un sitio oficial» nunca satisface este criterio.
