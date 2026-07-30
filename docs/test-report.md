# Informe de baseline y pruebas

Fecha: 2026-07-14
Rama: `feature/research-shell`
Commit: `86d644c76036eecc9cfda8617e11f31770f379d4`
Estado Git inicial/final: limpio

## Veredicto

`CONTRACT_BASELINE_PASSED_E2E_UNVERIFIED`

El contorno técnico actual de Junta compila y supera sus suites unitarias e
instrumentadas. Este informe no demuestra una firma aceptada por el portal
real. El perfil Junta conserva el estado `EXPERIMENTAL` y el gate de cambios
de producción para la universalización permanece cerrado hasta completar el
E2E manual descrito más abajo.

## Resultados ejecutados

| Gate | Resultado | Evidencia |
|---|---|---|
| AAPT2 Termux verify | Passed | `./tools/bootstrap-termux-aapt2.sh verify` |
| AAPT2 corruption self-test | Passed | Missing install, native corruption, runtime package corruption y extracted-runtime corruption fueron rechazados |
| Gradle toolchain verification | Passed | `verifyPortableAapt2Configuration` y `verifyResolvedCoreVersion` |
| Clean unit/lint/build | Passed | `clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`; 108 tasks, exit 0 |
| Unit suite sin build cache | Passed | `testDebugUnitTest lintDebug lintRelease --rerun-tasks`; 54/54 tasks ejecutadas, exit 0 |
| Unit tests | Passed | 38 suites, 191 tests, 0 skipped, 0 failures, 0 errors; agregado desde `app/build/test-results/testDebugUnitTest/*.xml` |
| Instrumentation compile | Passed | Target y androidTest APK generados por `connectedDebugAndroidTest` antes del transporte |
| Instrumentation device | Passed | `am instrument -w dev.junta.firmamobile.test/androidx.test.runner.AndroidJUnitRunner` mediante shell local autorizado; `OK (21 tests)` |
| Lint debug | Passed with warnings | 0 errors; 20 warnings: 5 `RequiresFeature`, 1 `TrustAllX509TrustManager`, 14 `UseKtx` |
| Lint release | Passed with warnings | 0 errors; 19 warnings: 4 `RequiresFeature`, 1 `TrustAllX509TrustManager`, 12 `UseKtx`, 2 `MonochromeLauncherIcon` |
| Credential scan | Passed | Ningún tracked file coincidió con patrones de alta confianza de private keys o tokens; solo existe `keystore.properties.example` por nombre |

El warning `TrustAllX509TrustManager` procede de una dependencia empaquetada y
no autoriza ningún bypass TLS en el código de la aplicación. Sigue registrado
para revisión de dependencias.

## Artefactos APK

| Variante | SHA-256 | Tamaño |
|---|---|---:|
| debug | `5519e6af1a3889b606f64ef9e4db5f8c6da870887d42a22373c809ab28e7c277` | 18,715,135 bytes |
| release | `97a58d4e4fd92a8c295ed5977ed9d4b2d8c87cea184c4e541fc192e3515042f3` | 14,663,914 bytes |
| androidTest debug | `d08e1ae0a7a4bc4874cde312e393cdb1f7d50f7844d7e516e91811b2feeb41ef` | 1,152,051 bytes |

Los tres APK verifican con APK Signature Scheme v2 y el certificado local
`CN=Android Debug`. No verifican con v3. Esto coincide con el alcance QA-only
documentado en `docs/building-on-termux.md`; no es una firma de distribución.

El `zipalign` nativo de Termux acepta `zipalign -c -p -v 4` para los tres APK.
No soporta la opción moderna `-P 16`, por lo que el gate exacto de alineación
16 KiB queda `UNVERIFIED` y no se sustituye por el check antiguo.

## Manifest, DEX y release security

- Package `dev.junta.firmamobile`, version `1/0.1.0`, minSdk 26, targetSdk 36.
- Release no declara `debuggable` ni `testOnly`.
- `allowBackup=false` y `usesCleartextTraffic=false` están presentes.
- La Activity nativa `ProtocolProbeActivity` y
  `ProtocolObservationRecorder` no aparecen en manifest/DEX release.
- No se encontró `handler.proceed`; los errores SSL no tienen bypass.
- `setWebContentsDebuggingEnabled` permanece como llamada, pero recibe
  `BuildConfig.DEBUG`; para release el argumento es `false`.
- El recurso principal `afirma_shim.js` todavía contiene hooks inertes de
  observación/probe. El gate estricto «release no contiene debug probe» queda
  `FAILED_PRE_EXISTING` hasta separar o eliminar ese código del source set main.
- El release incluye referencias a `es.gob.afirma` y loopback únicamente en
  rutas que interceptan/bloquean AutoFirma y WebSocket legacy; no prueban una
  capacidad de firma genérica.

## Baseline funcional Junta

Entrada observada:

`https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs`

Origins exactos codificados:

- `https://www.juntadeandalucia.es`;
- `https://sede.juntadeandalucia.es`;
- `https://ssoweb.juntadeandalucia.es`;
- `https://pfirma.juntadeandalucia.es`;
- `https://ws024.juntadeandalucia.es`;
- `https://ws050.juntadeandalucia.es`.

Solo `https://www.juntadeandalucia.es` puede iniciar el contorno de firma
actual. La evidencia de protocolo solo confirma directamente `www` y el
endpoint `ws024`; los otros cuatro hosts no se promueven por aparecer en el
allowlist.

Endpoint tri-phase exacto:

`https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService`

Contrato implementado: `MiniApplet.sign`, confirmación nativa, PRE/PK1/POST,
CAdES, RSA SHA-256 y una rama legacy RSA SHA-1. `afirma://`, `intent://`,
`selectcert` y WebSocket se observan/interceptan, pero no son operaciones de
firma funcionales. No hay Client TLS Authentication.

## Gates pendientes y riesgos preexistentes

1. No existe evidencia anonimizada de aceptación real por Junta; el E2E queda
   `NOT_RUN`.
2. El release usa debug key, solo v2, y carece del check exacto `-P 16`.
3. `afirma_shim.js` conserva fallback `Math.random()` para IDs no direct-sign;
   esto contradice el nuevo contrato fail-closed.
4. El navigation epoch no es todavía authoritative desde native.
5. `extraProperties` no exige `mode=explicit` ni usa una allowlist cerrada.
6. El resultado `OK NEWID=` se decodifica, pero no se verifica localmente como
   CMS/CAdES.
7. `WebView.saveState()` conserva un Bundle opaco sin demostrar que history,
   query o fragment sensibles estén excluidos.
8. El recurso main todavía contiene lógica de debug probe.
9. Cuatro de los seis origins Junta carecen de protocol evidence específico.

Estos puntos no se reinterpretan como soporte de otros portales.

## Gate E2E manual pendiente

Para promover Junta a `VERIFIED_E2E` hace falta, en el dispositivo y sin
automatizar credenciales:

1. abrir la página oficial;
2. seleccionar manualmente el certificado autorizado mediante SAF;
3. introducir manualmente su contraseña;
4. iniciar y confirmar la firma;
5. comprobar PRE, firma local, POST y callback;
6. comprobar que el portal acepta el resultado y continúa;
7. probar cancelación, sesión expirada y relock;
8. revisar logcat sanitizado;
9. guardar únicamente resultado anonimizado, sin URL query, cookies, payload,
   certificado, serial, issuer completo ni datos personales.

No se solicitará ni almacenará la contraseña en terminal, chat, ADB,
UIAutomator o shell.

## Milestone P04 — catálogo, registry y trust model — 2026-07-16

Se añadió un catálogo local estricto con un único profile production estable,
`junta-andalucia`, que conserva la URL inicial, los seis hosts de la fachada
legacy, el endpoint tri-phase exacto y el estado `EXPERIMENTAL`. El cambio no
promueve Junta a `VERIFIED_CONTRACT` ni a `VERIFIED_E2E`.

Controles nuevos:

- parser JSON cerrado con rechazo de keys duplicadas/desconocidas, schema o
  enum no soportado, origins ambiguos, IDs repetidos y policies incoherentes;
- origins HTTPS exactos, sin wildcard, userinfo, path, IP, localhost, trailing
  dot ni puerto no documentado;
- endpoint tipado con method, MIME, límites de cuerpo y redirects denegados;
- `serverUrl` y `mode=explicit` fijos por profile, sin properties abiertas;
- `LEGACY_SHA1` obligatorio y limitado a la policy Junta;
- `QA_ONLY` no se resuelve como profile activo en release;
- redirect directo queda `BROWSE_ONLY` y solo pasa a `TRUSTED_BROWSE`, sin
  acceso sensible, como transición del mismo profile activo;
- navegación, reload, Back, Forward y cambio de profile invalidan el estado
  sensible antes de avanzar el epoch del trust controller.

Validación ejecutada:

- focused profile/trust/Junta tests: PASS;
- suite unit completa: 201 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintRelease`, clean debug/release build: PASS;
- APK debug/release: firma v2 y `zipalign -c 4` PASS;
- manifest release: `allowBackup=false`, `usesCleartextTraffic=false`, sin
  `ProtocolProbeActivity`/`ProtocolObservationRecorder` en manifest o DEX;
- recurso release contiene solo Junta `EXPERIMENTAL`; no contiene profile
  `QA_ONLY`, `VERIFIED_CONTRACT` ni `VERIFIED_E2E`;
- scan del diff: sin PKCS#12, password, private key, cookies ni datos personales;
- `git diff --check`: PASS.

El device gate de este milestone queda `NOT_RUN_ENVIRONMENTAL`: ADB no tenía
dispositivo conectado y mDNS no anunció wireless debugging. No se interpreta
como PASS. El `zipalign` Termux sigue sin soportar `-P 16`; release continúa
firmado con debug key para QA local. Los riesgos preexistentes de shim/epoch y
verificación CMS permanecen hasta sus milestones específicos.

## Milestone P05 — AutoScript/MiniApplet input y callback — 2026-07-16

La fachada `MiniAppletBridgeAdapter` conserva su API y el JSON externo Junta,
pero delega normalización a un adapter gobernado por profile y a un registry de
bindings exactos `(profile, operation, input adapter, callback, protocol)`.
No existe fallback a otro profile o adapter.

El input adapter ahora exige origin iniciador `TRUSTED_SIGNING`, capability y
policy `SIGN`, algoritmo/formato declarados y el contrato callback exacto. Las
`extraProperties` de página solo confirman el mapa fijo del profile;
`serverUrl` y `mode=explicit` se reconstruyen canónicamente, y keys duplicadas,
desconocidas o con otro valor se rechazan antes de crear el request.

`MiniAppletCallbackAdapter` codifica los envelopes success/error comunes. El
canal legacy Junta sigue siendo one-shot, limpia firma/certificado y conserva
los mismos campos y aridad de callback. Ninguna API nueva contiene
`PrivateKey`, password, `CertificateSession`, cookies o URI PKCS#12.

Validación: focused PASS; suite completa 203 tests, 0 failures/errors/skips;
debug/release lint, assemble y artifact boundaries PASS. Device gate sigue
`NOT_RUN_ENVIRONMENTAL` por ausencia de conexión ADB; status Junta permanece
`EXPERIMENTAL` y no hay claim E2E.

## Milestone P06 — adaptador tri-phase común — 2026-07-16

`JuntaTriPhaseAdapter` conserva la misma interfaz y el mismo codec/wire
contract, pero ahora delega la coordinación PRE → firma local → POST a
`AutoFirmaTriPhaseExecutionAdapter`. El adapter común no recibe clave privada,
password, sesión de certificado, WebView ni cookies.

El execution contract se deriva del profile Junta empaquetado y vincula de
forma exacta protocol ID, profile/version, origin iniciador, algoritmo,
formato y endpoint. Cada request PRE y POST se compara con el endpoint del
profile antes del transport; no existe redirect ni fallback. El profile
también debe seguir declarando CAdES detached/explicit, POST y los valores
fijos `serverUrl`/`mode` observados.

La ejecución usa structured cancellation: el deadline propio se convierte en
error de protocolo, mientras que una cancelación externa se propaga y cancela
el transport. Rechazo del executor, excepción inesperada del codec, cambio de
contract o reutilización de state fallan cerrados y liberan cuerpos, firma
local y state sensible. La fachada Junta pasa flujos SHA-1 legacy y SHA-256.

Validación: 210 unit tests, 0 failures/errors/skips; `lintDebug`,
`lintRelease`, `assembleDebug` y `assembleRelease` PASS; ambos APK verifican
firma v2 y `zipalign -c 4`. Device gate permanece `NOT_RUN_ENVIRONMENTAL`: ADB
no enumeró ningún dispositivo. Junta continúa `EXPERIMENTAL` /
`IMPLEMENTED_NOT_E2E`; este milestone no prueba aceptación por el portal.

## Milestone P06A — transporte HTTP vinculado al profile y DNS — 2026-07-16

El transporte tri-phase acepta ahora únicamente el conjunto exacto de
endpoints HTTPS del profile activo. La resolución DNS se ejecuta con deadline
y un pool global acotado a dos tareas; saturación, cancelación, resolución
vacía o direcciones no globales fallan cerradas antes de iniciar HTTP.

OkHttp 5.4.0 recibe exclusivamente las direcciones públicas previamente
aprobadas, conserva el hostname original para SNI y hostname verification,
deshabilita proxy, redirects, cookies, autenticadores, cache y retry. Un
network interceptor verifica además la dirección realmente conectada. El POST
usa un body one-shot para impedir reenvíos automáticos, incluido
`503 Retry-After`, y mantiene límites de tiempo y tamaño de respuesta.

La revisión original bloqueaba IPv6 por completo. Esta limitación fue sustituida
por F-17 el 2026-07-30: el clasificador IPv4/IPv6 se contrastó con el registro
IANA IPv6 Special-Purpose revision 2025-10-09. IPv6 ordinario se restringe a
`2000::/3`; los bloques especiales, mapped y scoped se bloquean. Well-known
NAT64 se admite solo si su IPv4 embebido es público. Esto habilita el transporte,
pero no constituye por sí solo E2E de un portal IPv6-only.

Validación local: tests focused de policy/transport PASS, incluido intercambio
TLS real HTTP/2 con SNI, route pinning y ausencia de segundo POST; timeout,
cancelación y saturación DNS PASS. La suite completa suma 214 tests sin
failures/errors/skips; `lintDebug`, `lintRelease`, `assembleDebug` y
`assembleRelease` PASS. Ambos APK verifican firma v2 y `zipalign -c 4`.
`dependencyInsight` confirma OkHttp 5.4.0 y Okio 3.17.0. Las pruebas Android de
API 26, TLS/SNI y `Call.cancel()` durante connect/write/read siguen
`NOT_RUN_ENVIRONMENTAL`: ADB no enumeró ningún dispositivo.

## Milestone V01 — pantalla principal y launcher — 2026-07-16

La pantalla principal conserva únicamente el brand header, disclosure,
controles Compose reales del certificado y su banner de estado. Se eliminaron
por completo las tabs decorativas `Inicio`, `Historial`, `Ajustes` y `Ayuda`,
sus iconos, strings y el test que exigía su presencia. El control Home real
del navegador no se modificó. La composición se compactó sin convertir textos,
botones o estados en imágenes.

El label del launcher es ahora `Junta Firma`; el nombre de producto visible en
la aplicación sigue siendo `Junta Firma Mobile`. El foreground adaptive se
redujo de un alpha bbox `55×66` a `42×51` sobre canvas `108×108`, centrado con
offset `+35,+29`. El monochrome aplica la misma escala óptica; background y las
variantes legacy/round de cinco densidades se regeneraron con margen exterior.
Un render determinista de las capas reales confirma margen bajo máscaras round
y squircle representativa.

Validación local: 214 unit tests, `lintDebug`, `lintRelease`, `assembleDebug` y
`assembleRelease` PASS; ambos APK verifican firma v2 y `zipalign -c 4`.
`assembleDebugAndroidTest` compila el test que captura la pantalla real y exige
ausencia de las cuatro tabs. La instalación y captura en POCO/Xiaomi quedan
`NOT_RUN_ENVIRONMENTAL`: ADB/mDNS no enumeran dispositivo y el teléfono no
ofrece root/package-manager autónomo. El render Compose JVM tampoco está
disponible en Termux; no se presenta un mockup como screenshot real.

El diff V01 no toca `MainActivity`, certificate logic, signing, WebView,
network, trust, bridge ni protocol. El artefacto sanitizado de máscaras está en
`/storage/emulated/0/Codex/Outputs/junta-firma-visual-qa-20260716/launcher-mask-preview.png`.

## Milestone P07 — REG-AGE / RedSARA — 2026-07-18

El catálogo release incorpora un segundo profile `TRUSTED_SIGNING` limitado al
origin exacto `https://reg.redsara.es`. Su contrato se revalidó contra el
JavaScript oficial: `AutoScript.sign(xml, "SHA512withRSA", "XAdES Detached",
null, success, error)`. No se permite redirect, endpoint servidor inferido,
algoritmo alternativo ni `extraProperties` no nulas.

El adapter local genera XAdES-BES 1.3.2 detached en contenedor `AFIRMA`, firma
`SignedInfo` con SHA512withRSA y valida antes de devolver el resultado las tres
referencias SHA-512, el fingerprint del certificado y la firma RSA. El parser
XML deshabilita DTD, entidades externas y XInclude. Junta conserva su adapter
tri-phase y sus tuples anteriores sin fallback entre perfiles.

El callback AutoScript/MiniApplet es one-shot y queda vinculado a profile,
origin exacto, navigation ID, navigation epoch, request ID y TTL de dos
minutos. Replay, iframe, origin incorrecto, navegación, reload, cancelación y
expiración fallan cerrados; firma y certificado temporales se limpian.

El estado de producto permanece `VERIFIED_CONTRACT` / `IMPLEMENTED_NOT_E2E`:
los contract tests no demuestran aceptación por RedSARA y no se publica como
`VERIFIED_E2E` hasta completar un escenario seguro real.

Validación final: 228 unit tests, 0 failures/errors/skips; `lintDebug`,
`lintRelease`, `assembleDebug`, `assembleRelease` y
`assembleDebugAndroidTest` PASS. En Android real pasan cuatro pruebas focused:
creación XAdES y verificación mediante el validador XMLDSig estándar de
Santuario, instalación document-start, interceptación exacta AutoScript
RedSARA y regresión MiniApplet Junta. Los APK debug/release verifican firma v2
y `zipalign -c 4`; el manifest release mantiene `allowBackup=false` y
`usesCleartextTraffic=false`.

La build debug se instaló mediante Shizuku/rish con `pm install -r -t`, salida
exacta `Success`, código interno 0 y SHA-256 idéntico entre el artefacto, la
copia temporal y `base.apk`. Se lanzó la `MainActivity` normal y se eliminaron
el APK temporal y el paquete de instrumentación. El E2E real del portal no se
ejecutó: no existe todavía un escenario público seguro que confirme aceptación
sin avanzar hacia una presentación administrativa, por lo que no se eleva el
estado.

## Milestone P08 — UniZAR authentication — 2026-07-18

El catálogo incorpora `unizar-tramitador` para el origin exacto
`https://tramita.unizar.es`. El contrato observado se limita a autenticación:
challenge precalculado de 20 bytes, `SHA1withRSA`, `CAdES`, propiedades exactas
`precalculatedHashAlgorithm=SHA1` y `serverUrl`, y PRE/POST contra el único
`SignatureService` permitido. La rama legacy requiere `LEGACY_SHA1`; no existe
fallback ni capability `AFIRMA_URI`.

El codec común AutoFirma CAdES valida además el tamaño del challenge y el mapa
exacto de propiedades antes de cualquier llamada de red. Los tests de contrato
cubren PRE/local PKCS#1/POST, origin/protocol/algorithm/properties incorrectos,
challenges de 19 y 21 bytes y resolución exacta del profile. Storage/Retrieve,
co-sign y counter-sign permanecen deshabilitados. El estado sigue siendo
`VERIFIED_CONTRACT` / `IMPLEMENTED_NOT_E2E` hasta que el portal acepte una
autenticación real segura.

Validación: los focused tests UniZAR/Junta/bridge/catalog/registry pasan. El
suite completo ejecutó 232 tests: tras corregir una expectativa de catálogo,
231 pasaron y un test DNS preexistente sufrió una saturación transitoria del
resolver (`2001:2::1` se cerró como `NETWORK_ERROR` en vez de
`PRIVATE_ADDRESS`); la misma prueba pasó aislada sin cambiar código de red.
`lintDebug`, `lintRelease`, debug/release y androidTest builds pasan; ambos APK
verifican firma v2 y `zipalign -c 4`, y el manifest release conserva
`allowBackup=false` y `usesCleartextTraffic=false`.

El debug APK se instaló mediante Shizuku/rish con salida exacta `Success` y
código interno 0. Su SHA-256 coincide con la copia de `/data/local/tmp` y con
el `base.apk` instalado; `pm path`, `dumpsys package` y el cold start de la
`MainActivity` normal fueron correctos. MIUI rechazó la instalación separada
del APK de instrumentation con `INSTALL_FAILED_USER_RESTRICTED`, incluso usando
una package session, por lo que el WebView test UniZAR quedó compilado pero no
ejecutado en este device gate. No se rebaja ninguna política para sortear esa
restricción. El E2E real no se declara: no se dispuso de un desbloqueo secreto
completo y seguro en este turno, y no se simuló aceptación del portal.

## Milestone P16A — Aragón SIRAW login CAdES — 2026-07-28

Se activó el profile `aragon-siraw` exclusivamente en builds QA. La revisión
live del portal confirmó el origin exacto `https://aplicaciones.aragon.es`, la
entrada pública SIRAW y el tuple `MiniApplet.sign` usado para login:
`SHA1withRSA`, `CAdES`, `mode=explicit` y `filter=nonexpired`. El challenge
observado se decodifica a 20 bytes.

El nuevo profile no declara endpoints. `ProtocolAdapterRegistry` dirige solo
`(aragon-siraw, SIGN)` al `LocalCadesDetachedAdapter` existente. El bridge
requiere origin principal, profile seleccionado, algoritmo, formato y
properties exactos. El adapter genera CAdES detached local, valida la firma CMS
y rechaza challenge, origin, profile, properties o firma manipulados. Los
servlets Storage/Retrieve presentes en el JavaScript público y la rama
documental con `precalculatedHashAlgorithm=SHA1` permanecen fuera del runtime.

El catálogo público conserva 182 entradas y ahora contiene siete bindings de
profile. Aragón pasa de `VERIFIED_CONTRACT` sin implementación a
`IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; continúa deshabilitado en release. El
snapshot nacional conserva 180 registros: 1 `VERIFIED_E2E`, 4
`IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 168 `BROWSE_ONLY`, 2
`UNSUPPORTED_PROTOCOL` y 4 `INACCESSIBLE`.

Validación ejecutada:

- direct Context7 MCP: servidor local 3.2.4, tools `resolve-library-id` y
  `query-docs`, consulta AndroidX/WebKit completada;
- tests focused de profile, registry, bridge, adapter CAdES, catálogo y
  generador: PASS;
- `testDebugUnitTest`: 319 tests, 0 failures;
- `testQaUnitTest`: 319 tests, 0 failures;
- `lintDebug` y `lintQa`: 0 errores, 25 warnings en cada variante;
- `assembleDebug` y `assembleQa`: PASS;
- catálogo generado dos veces byte-for-byte idéntico;
- debug APK SHA-256:
  `42996105646208835b534813a47069673c72212b2ad3de3eb17a742d1a47538f`;
- QA APK SHA-256:
  `b2864a594e032e8a5f2be5be84ac3b34b1a4519383edaee9f50c471942c28830`.

La build QA se instaló en el dispositivo mediante Shizuku/rish con `pm install
-r -t`: salida `Success`, código 0 y SHA-256 idéntico entre artefacto, staging y
`base.apk` instalado. Con la Activity realmente `RESUMED`, el smoke protegido
por permiso `DUMP` devolvió `PROFILE_RESOLVED` para `aragon-siraw` y el adapter
exacto `aragon-siraw-local-cades-v1`; no hubo crash ni ANR. El WebView no se
abrió porque el certificado permanecía bloqueado, por diseño.

Este milestone no prueba aceptación del portal, no ejecuta una operación
administrativa y no eleva Aragón a `VERIFIED_E2E`.

## Milestone P16B — Aragón SIRAW login VERIFIED_E2E — 2026-07-28

El usuario ejecutó manualmente el flujo real en el dispositivo físico con el
profile exacto `aragon-siraw`. La página pública solicitó el certificado, la
aplicación presentó la confirmación nativa con `CAdES` y `SHA1withRSA`, y tras
confirmar la firma el portal continuó al área interna observada. Esto prueba la
aceptación portal-side del resultado de autenticación para ese login.

La evidencia se registra de forma sanitizada. Las capturas originales no se
incorporan al repositorio porque muestran datos identificativos del certificado.
No se conservaron contraseña, clave privada, PKCS#12, firma, certificado, cookies,
query sensible ni datos del formulario interno.

El profile pasa a `VERIFIED_E2E` / `ENABLED`, versión 2, sin ampliar origin,
capabilities, algoritmos, formato ni properties. La promoción se limita al login
CAdES observado. Storage/Retrieve, la rama documental con hash precalculado,
cofirma, contrafirma y cualquier presentación administrativa siguen bloqueados.

Validación posterior a la promoción:

- `testDebugUnitTest`: 319 tests, 0 failures/errors/skips;
- `testQaUnitTest`: 319 tests, 0 failures/errors/skips;
- `lintDebug` y `lintQa`: 0 errores y 25 warnings por variante;
- `assembleDebug` y `assembleQa`: PASS;
- catálogo público reproducible byte-for-byte y profile resource idéntico al
  catálogo fail-safe compilado;
- APK debug y QA: firma v2 y `zipalign -c -p 4` PASS;
- debug APK SHA-256:
  `b8fea4506fac55e3d0c506e81aa35d2d5cc07d8c1edf1b0f3d32acd6de41b495`;
- QA APK SHA-256:
  `13293fe42f409311543fb8fa6e6e1523216f8d0d91d2476bf5d15f1541ca72e7`.

`lintRelease` y `assembleRelease` no se ejecutaron: ambos alcanzan el gate
`verifyReleaseSigning`, que exige las cuatro credenciales privadas de firma. La
worktree no las contiene y no se habilitó fallback a la clave debug. La política
release del profile sí queda cubierta por los tests exactos de registry y por la
igualdad entre el recurso y el catálogo compilado.

## Oficina Virtual — duplicate MiniApplet request fix — 2026-07-28

El flujo real de `junta-ofvirtual` mostró `PROTOCOL_FAILED` y provocó que la
página enviara su formulario de error. La investigación separó las capas antes
de modificar producción: el contrato público de `ws072`, el orden del callback,
el Base64 estándar, DNS, TLS, PRE, firma RSA local y POST contra el endpoint
`afirma-validator-miniapplet-1_5` fueron verificados. Un probe efímero dentro de
Android completó PRE y POST con una identidad de prueba generada en memoria; no
se utilizó ni conservó el certificado personal.

La reproducción determinista encontró la regresión en el shim: un segundo
`MiniApplet.sign` idéntico mientras la primera operación seguía en curso era
respondido con `PROTOCOL_FAILED`. El callback de error de Oficina Virtual enviaba
inmediatamente `msjerror`, anulando la operación original. El test WebView pasó
de `DUPLICATE_ERROR` antes del cambio a `PASS` después del cambio.

El shim ahora coalesce únicamente una repetición byte-for-byte idéntica, con los
mismos callbacks, mientras la primera solicitud permanece activa. No se genera
un segundo mensaje nativo ni se llama al callback de error. Una solicitud
con datos, algoritmo, formato, properties o callbacks distintos continúa
rechazándose de forma fail-closed con `PROTOCOL_FAILED`.

Validación:

- `testDebugUnitTest`: 319 tests, 0 failures/errors/skips;
- `testQaUnitTest`: 319 tests, 0 failures/errors/skips;
- `lintDebug` y `lintQa`: 0 errores, 25 warnings por variante;
- `assembleDebug`, `assembleQa` y `assembleQaAndroidTest`: PASS;
- `WebMessageBridgeInstrumentedTest` en dispositivo físico: 7/7;
- prueba RED previa: `expected PASS but was DUPLICATE_ERROR`;
- prueba GREEN posterior: caso idéntico PASS y caso conflictivo PASS;
- firma APK v2 y `zipalign -c -p 4`: PASS;
- debug APK SHA-256:
  `ee6ea8a77ca8499d99e43e3231b3fbc92fe56437c1d4c2d48f9de9af0937cbce`;
- QA APK SHA-256 e instalado `base.apk`:
  `632e1f79f5e5ddf2aaa676857e0147e888a46d0e74ebd675a25b8eeee2daa9cf`;
- androidTest APK SHA-256:
  `b49f56812af4ca97c147529338fb580a7cea1bef380614b9afd051a8d57ee0d2`.

En ese milestone, `junta-ofvirtual` permanecía `VERIFIED_CONTRACT / QA_ONLY`:
la corrección eliminaba el fallo reproducido, pero todavía faltaba un nuevo
acceso manual. Este estado histórico quedó superado por el milestone P07B del
2026-07-29, donde Oficina Virtual aceptó el login real.

## Milestone WS024-QA — gates completos y límites documentados — 2026-07-29

Base verificada antes de este cambio documental: `07c3b053480529ee15fd5d1fa486c0ca0cafff0f`.

Veredicto: `SYNTHETIC_DOUBLE_TLS_VERIFIED_EXTERNAL_E2E_PENDING`.

Este milestone verifica la arquitectura de transporte seguro con infraestructura
sintética y ejecuta todos los gates locales disponibles. No demuestra que la
Oficina Virtual real acepte una firma ni autoriza el estado `VERIFIED_E2E`.

### Evidencia de Android

| Gate | Resultado exacto |
|---|---|
| `testDebugUnitTest` | 68 suites, 408 tests, 0 failures, 0 errors, 0 skipped |
| `testQaUnitTest` | 68 suites, 408 tests, 0 failures, 0 errors, 0 skipped |
| `lintDebug` | 0 errors, 25 warnings |
| `lintQa` | 0 errors, 25 warnings |
| `assembleDebug` | PASS |
| `assembleQa` | PASS |
| Tests focused de política release/túnel | PASS |
| `assembleRelease` sin secretos | rechazo esperado en `:app:verifyReleaseSigning`; no existe fallback a debug key |

Nota de repetición: una ejecución adicional conjunta de Debug+QA con
`--rerun-tasks` produjo una vez un failure en
`ProfileHttpTransportTest.publicExactEndpointReturnsOneOwnedBoundedBody`. El
test exacto pasó de forma aislada y después los suites completos QA y Debug
pasaron por separado con `--rerun-tasks` (408/408 cada uno). La causa exacta de
ese resultado transitorio no quedó demostrada; se registra y no se usa como
evidencia de PASS ni como prueba de una regresión production. La evidencia final
del cuadro anterior procede de los suites completos separados con cero fallos.

### Integridad de APK

| Variante | SHA-256 | Tamaño | Firma | Alineación |
|---|---|---:|---|---|
| debug | `998f581634056dff70fa18c68b7be13fb5880be4ba01e6465b5b74677a2ffce0` | 20,870,052 bytes | v2, 1 signer `CN=Android Debug`; v1/v3/v4 false | `zipalign -c -p -v 4` PASS |
| qa | `e4b0bf6f75baadde1ebd06be5e4692b2a6b037960c2929a18edf7c70a0890192` | 20,689,877 bytes | v2, 1 signer `CN=Android Debug`; v1/v3/v4 false | `zipalign -c -p -v 4` PASS |

Estas APK son artefactos locales de desarrollo/QA, no artefactos de distribución.
La QA APK se construyó sin tuple externo: su `BuildConfig` generado contiene
`ENABLE_WS024_QA_TUNNEL=false`, host vacío, puerto `443` y pins vacíos. Por
tanto, este hash corresponde a una QA build direct-only; no contiene una
credencial ni una configuración de relay desplegado.

### Evidencia del relay y double TLS sintético

- `go test ./... -count=1`: PASS en los 2 paquetes production.
- `go vet ./...`: PASS.
- `go build ./cmd/ws024-relay`: PASS.
- `go test ./... -race -count=1`: `NOT_AVAILABLE_ENVIRONMENTAL`; Go devolvió
  exactamente `-race is not supported on android/arm64`. No se presenta como PASS.
- `scripts/verify-ws024-tunnel.sh`: PASS y stdout exacto:

```json
{"direct":"TCP_BEFORE_HTTP_BYTES","tunnel":"ESTABLISHED","innerTls":"VERIFIED_WS024","httpPosts":1,"relayPayloadVisible":false}
```

El harness usa dos PKI temporales independientes, outer TLS con hostname y SPKI
pinning, ALPN `http/1.1`, CONNECT de destino fijo, transporte opaco y una segunda
verificación TLS para `ws024.juntadeandalucia.es`. También prueba que un fallo
directo después de iniciar HTTP no permite fallback y que un leaf inner con SAN
incorrecto no alcanza el POST. Todos los procesos, claves y archivos temporales
se eliminan al terminar.

### Scan de secretos y límites de alcance

El scan de source, generated BuildConfig y strings de las APK debug/QA no encontró:

- private keys o marcadores PEM privados;
- bearer tokens con valor real o sintético;
- credenciales QA;
- canaries o payloads del harness;
- variables del harness JVM;
- clases/comandos build-tagged de integración;
- certificados, firmas o payload tri-phase completos añadidos por este milestone.

La configuración release permanece explícitamente direct-only:
`ENABLE_WS024_QA_TUNNEL=false`, relay host vacío y pins vacíos. El código de
integración Go está protegido por `//go:build integration` y no forma parte del
binario production.

### Estado funcional y trabajo pendiente

En el milestone WS024-QA, `junta-ofvirtual` conservaba
`VERIFIED_CONTRACT / QA_ONLY / E2E_PENDING`. Ese estado histórico quedó
superado por P07B; el tuple experimental descrito a continuación no fue necesario
para el E2E directo aceptado. El único tuple elegible para el túnel QA era:

- profile: `junta-ofvirtual`;
- initiator: `https://ws072.juntadeandalucia.es`;
- endpoint: `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService`.

No se ha desplegado un relay externo controlado por el proyecto, no existen
credenciales production, no se ha construido una QA APK con tuple/pins reales y
no se ejecutó el flujo físico de Oficina Virtual después de este cambio. El
Task 12 permanece bloqueado hasta cumplir esas precondiciones. No se emite
`VERIFIED_E2E`, no se promueve el profile y no se habilita el túnel en release.

## Milestone P07B — Junta Oficina Virtual login VERIFIED_E2E — 2026-07-29

El flujo real `junta-ofvirtual` fue ejecutado manualmente en un POCO F6 Pro con
un certificado personal. La aplicación completó PRE, firma RSA local, POST,
callback y envío del formulario; Oficina Virtual aceptó la autenticación y abrió
su área interna. La pantalla observada correspondía a los trámites pendientes de
la persona autenticada.

La evidencia se limita al login CAdES observado. No acredita presentación de
solicitudes, firma documental posterior, cofirma, contrafirma ni todas las
funciones del portal. La captura original no se guarda porque contiene datos
identificativos; tampoco se conservaron contraseña, PKCS#12, clave privada,
certificado, firma, cookies o payloads.

Dos defectos explicaban el fallo anterior:

- el redirect HTTP heredado exacto de Oficina Virtual era bloqueado; `6538e1a`
  permite únicamente su upgrade seguro a HTTPS para top-level GET dentro del
  mismo host/profile/path;
- `onStop()` bloqueaba el certificado y destruía el WebView; `26230ab` conserva
  identidad y navegador mientras vive el proceso, con expiración a dos horas y
  limpieza en lock/forget/memory pressure/process death.

La aceptación real se obtuvo en `26230ab` con QA APK SHA-256
`6c14b2d95187b89261973a221d391f0ea469d43149e9a3bf3e1358355ca69779`.
El profile fue promovido después en `b3f1817` a versión 2,
`VERIFIED_E2E / ENABLED`; el catálogo muestra `VALIDADO CON EL PORTAL` y
`Verificado: Firma electrónica`.

Gate posterior a la promoción:

- Python: 75 tests, 0 failures/errors, 1 skipped;
- `testDebugUnitTest`: 431 tests, 0 failures/errors;
- `testQaUnitTest`: 431 tests, 0 failures/errors;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Debug APK SHA-256:
  `2a5c0b45595efeafd336a11bdbc27f04bc28d669c7237f10423a679462685d47`;
- QA APK e installed `base.apk` SHA-256:
  `ba82c501c4e1e4d9843dc263648d4b051ea2d9bbbbefd6f7ff451ab197b30e34`;
- QA AndroidTest APK SHA-256:
  `8a67d0ca4c32590022de4cf9728a09e32cac501ba85ff62cb71a2021dd4e250f`;
- QA sigue direct-only: tunnel deshabilitado, relay host/pins vacíos.

Informe completo:
`docs/e2e/2026-07-29-junta-ofvirtual-auth-success.md`.

## Milestone P07C — 24-hour certificate unlock and physical revalidation — 2026-07-30

The certificate unlock lifecycle was extended from an in-memory two-hour window to an
encrypted 24-hour device-local window. The cache stores only the minimum unlock secret,
encrypted with an AES-GCM key generated in Android Keystore. The ciphertext is written
atomically under `noBackupFilesDir`; the plaintext password is not written to logs,
preferences, the repository or APK resources. The original expiry timestamp is retained,
so process restarts do not renew the 24-hour window.

Early invalidation remains intentional: manual lock, session clear, certificate replacement,
forget, an incorrect cached password, an expired record, malformed/tampered ciphertext or a
certificate-reference mismatch clears the cache. Clearing app data or uninstalling the app
also removes it.

### Physical verification on the installed QA build

- A cold launch after process termination restored the selected certificate without showing
  the password prompt.
- The encrypted cache file remained present at 101 bytes across `pm install -r` and a
  subsequent force-stop/cold launch.
- The unlocked UI exposed the normal actions (`Bloquear certificado`, `Elegir otro`,
  `Olvidar certificado`) with no password prompt.
- The corrected resource text states the 24-hour behavior and is present in the installed APK.
- The installed `base.apk` SHA-256 exactly matched the locally verified QA APK:
  `880e72d7cd4e69bc61412ae3a75ed976a6857da0c56f37c031073136a1938a11`.

### Oficina Virtual revalidation

The real `junta-ofvirtual` profile was opened through the QA catalog smoke hook using only
its identifiers (`portalId=junta-andalucia-ofvirtual`, `profileId=junta-ofvirtual`). The
active WebView was confirmed as `https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs`.
After the user-authorized native signing confirmation:

- the bridge returned `MINIAPPLET_RESULT` with `status=success`;
- the signature and certificate fields were non-empty strings and passed the closed standard
  Base64 shape check; their values were not stored or printed;
- the portal callback reached `CALLBACK_STARTED` and `CALLBACK_RETURNED`;
- Oficina Virtual accepted the authentication and navigated to
  `/ofvirtual/ovMisTramites/index` with HTTP 200;
- the resulting page showed `Mis trámites pendientes` and no login button or
  `No se pudo completar la firma` marker.

This confirms certificate login only. It does not claim that filing, later document signing,
co-signing, countersigning or every authenticated portal feature was tested.

### Transient network incident observed during revalidation

Before the successful run, the bridge returned the closed error
`SIGNING_SERVICE_UNAVAILABLE`. Safe diagnostics showed that DNS resolution for `ws024`
succeeded while TCP/TLS connection attempts timed out before the PRE request. The same
failure was reproduced outside the app from Termux. Later, with WARP reported active, the
exact endpoint completed TCP/TLS and returned HTTP 200, and the unchanged app completed the
full login. Therefore the incident is recorded as a transient route/service reachability
failure; its exact external cause was not proven and no speculative code workaround was
added.

### Fresh QA gate and APK checks

- `testQaUnitTest`: 442 tests, 0 failures, 0 errors, 0 skipped.
- `lintQa`: PASS.
- `assembleQa`: PASS.
- `zipalign -c -p -v 4`: PASS.
- APK Signature Scheme v2: verified, one signer.
- QA APK / installed `base.apk` SHA-256:
  `880e72d7cd4e69bc61412ae3a75ed976a6857da0c56f37c031073136a1938a11`.
- QA remains direct-only; the external relay tuple is not enabled in this build.

No screenshot of the authenticated area, password, PKCS#12, private key, certificate body,
signature, cookie or form payload was committed or retained.

## Milestone P07D — Oficina Virtual legacy UI compatibility — 2026-07-30

After the verified certificate login, the authenticated `junta-ofvirtual` pages exposed three
independent defects in the legacy portal UI:

- the server-produced mobile label contained the exact mojibake string `MenÃº`;
- the page used Font Awesome 5 classes (`fas`) while loading only Font Awesome 4.1;
- the markup declared Bootstrap Collapse controls, but Bootstrap Collapse JavaScript was not
  loaded, so the mobile navigation and Oficina submenu did not open.

The application now applies a narrow, idempotent compatibility patch only to exact HTTPS pages
on `ws072.juntadeandalucia.es` whose path begins with `/ofvirtual/`. It does not run on other
hosts, ports, schemes, profiles or paths. The patch:

- replaces only the exact broken navigation label with `Menú`;
- adds the compatible Font Awesome 4 class only when a rendered `i.fas` glyph is not already
  using a Font Awesome family;
- provides a Collapse fallback only when neither jQuery Collapse nor native Bootstrap is
  available;
- synchronizes all controls targeting the same collapse element and keeps `aria-expanded` and
  `collapsed` consistent;
- observes later DOM additions without reading forms, cookies, certificate fields, signature
  fields or portal payloads.

Physical-device verification on the installed QA build confirmed that the authenticated
`/ofvirtual/ovMisTramites/index` page displayed the corrected label and icons, and both the
mobile navigation menu and the Oficina submenu opened and closed normally. The user independently
confirmed the corrected display and menu behavior. The patch remains limited to UI compatibility;
it does not broaden the signing profile or claim validation of later administrative procedures.

Fresh verification:

- `testDebugUnitTest`: 449 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 449 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- QA APK `zipalign -c -p -v 4`: PASS;
- QA APK Signature Scheme v2: verified, one signer;
- Debug APK SHA-256:
  `67b17672e7a681658253af27b2d46840f1bd0c4087d481fd3d64ae2363f55f3d`;
- QA APK and installed `base.apk` SHA-256:
  `262244e7aa7267808f668ef8ddd67c233266e4dd439dae1fc46c5ad2dcd00518`;
- QA AndroidTest APK SHA-256:
  `8a67d0ca4c32590022de4cf9728a09e32cac501ba85ff62cb71a2021dd4e250f`;
- encrypted certificate-unlock cache remained present at 101 bytes with mode `600`.

No authenticated screenshot, password, PKCS#12, private key, certificate body, signature, cookie
or form payload was committed.

## Milestone F15A — profile/public-catalog evidence consistency — 2026-07-30

A cross-source review found that the legacy `junta-andalucia` / Ovorion MiniApplet 1.4
profile had been changed from `EXPERIMENTAL` to `VERIFIED_E2E` inside commit `84c3c937`
while implementing release profile gating. That commit contained no portal-acceptance evidence
for Ovorion. The public catalog, compatibility matrix and security roadmap all continued to
state `E2E_PENDING / IMPLEMENTED_NOT_E2E` or `EXPERIMENTAL`.

The bundled profile has therefore been corrected to `EXPERIMENTAL`. Its activation remains
`ENABLED`, which means the QA registry can continue controlled testing, while the existing
release policy excludes the sensitive profile because it lacks `VERIFIED_E2E` evidence. No
origin, endpoint, algorithm, adapter, callback, capability or transport was changed.

A new cross-catalog regression gate now checks every bound public entry: profile status
`VERIFIED_E2E` must be equivalent to the exact metadata pair
`E2E_VERIFIED / VERIFIED_E2E`. Contractual, experimental and browse-only profiles cannot carry
that public E2E pair. The relevant trust, catalog UI and signing-confirmation fixtures were
updated to use QA policy when exercising Ovorion and to display its real `EXPERIMENTAL` status.
The profile catalog version was incremented from 8 to 9.

Fresh verification:

- RED: four focused tests failed against the incorrect Ovorion promotion;
- GREEN focused profile/catalog/trust/signing tests: PASS;
- `testDebugUnitTest`: 450 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 450 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: PASS;
- Debug APK SHA-256:
  `2075adeea1d97627ee04564644e73c0fefd73b78b1409c5b735d8cc26176e225`;
- QA APK SHA-256:
  `190115079eba9c942db9e1fa3a20b4119eac445fef9406c90c4254729cc5fc7f`;
- QA AndroidTest APK SHA-256:
  `8a67d0ca4c32590022de4cf9728a09e32cac501ba85ff62cb71a2021dd4e250f`.

This milestone corrects metadata and release eligibility only. It does not run or claim a new
portal E2E flow.

## Milestone P08B — UniZAR login VERIFIED_E2E — 2026-07-30

The real `unizar-tramitador` authentication flow was executed on a physical
Android device using the exact QA profile and a manually controlled personal
certificate. The application completed the observed 20-byte precalculated
challenge flow with `SHA1withRSA`, detached `CAdES`, exact
`precalculatedHashAlgorithm=SHA1`, exact `serverUrl`, PRE, local RSA signing,
POST and the AutoScript callback. The portal accepted the result and opened its
authenticated `Buzón Electrónico` area with the `Mis Gestiones` block.

The acceptance build used profile version 1 and QA APK SHA-256
`190115079eba9c942db9e1fa3a20b4119eac445fef9406c90c4254729cc5fc7f`.
The subsequent profile promotion is metadata-only: version 2,
`VERIFIED_E2E / ENABLED`, profile catalog version 10 and public catalog
`E2E_VERIFIED / VERIFIED_E2E`. Origins, endpoint, capabilities, algorithm,
format, challenge constraints, properties, callback and transport are unchanged.

The evidence is limited to authentication. Storage/Retrieve, co-sign,
counter-sign, document signing and administrative submission remain blocked.
No procedure was created, modified or submitted. Authenticated screenshots are
not committed because they contain identifying data. No password, PKCS#12,
private key, certificate, signature, challenge, cookie, session field or case
content was retained.

TDD evidence before promotion:

- focused profile/catalog/UI tests failed against the previous
  `VERIFIED_CONTRACT / QA_ONLY` metadata;
- the public-catalog generator test failed with `E2E_PENDING` instead of the
  required `E2E_VERIFIED`;
- after the exact metadata promotion, all focused tests and reproducibility
  checks passed.

Final verification after the reproducible catalog regeneration:

- `testDebugUnitTest`: 452 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 452 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 75 tests, 0 failures/errors, 1 skipped;
- committed public catalog is byte-for-byte reproducible from the inventory;
- QA APK `zipalign -c -p -v 4`: PASS;
- QA APK Signature Scheme v2: verified, one signer;
- QA manifest: `debuggable=true` (expected QA), `allowBackup=false`,
  `usesCleartextTraffic=false`;
- forbidden canary scan: no certificate identity, password, PKCS#12, private-key,
  `firmaB64` or `certificadoB64` markers;
- Debug APK SHA-256:
  `c0d79cb55f6d28db69ef92b8734c11331a6076f3855cd429103c96198fecf34b`;
- QA APK SHA-256:
  `28373cb7cccf9a8a80347ff06e36192feab29474578a566d9021ef1384b36c61`;
- QA AndroidTest APK SHA-256:
  `8a67d0ca4c32590022de4cf9728a09e32cac501ba85ff62cb71a2021dd4e250f`.
- `pm install -r` of the promoted QA APK: `Success`;
- local QA APK and installed `base.apk` SHA-256 matched exactly;
- encrypted certificate-unlock cache remained `101` bytes with mode `600`;
- force-stop and cold launch restored the certificate without a password prompt;
- QA smoke `OPEN` and `INSPECT` returned profile `unizar-tramitador`, adapter
  `unizar-autoscript-triphase-cades-v1`, support `VERIFIED_E2E` and
  `OPEN_REQUESTED / WEBVIEW_ACTIVE`.

## Milestone P07C — RedSARA safe E2E blocker revalidated — 2026-07-30

The promoted UniZAR QA build was used to reopen the exact `reg-age-redsara`
profile on a physical Android device. Smoke resolution returned profile
`reg-age-redsara`, adapter `local-xades-detached-v1` and
`IMPLEMENTED_NOT_E2E / WEBVIEW_ACTIVE`.

Read-only inspection of the public portal established that both `Nuevo registro`
and `Mis registros` lead to `/es/login`, where the only visible authentication
action is Cl@ve. The contractual XAdES operation is not authentication: the
portal invokes it over an application-summary XML and continues with
`saveXMLAutoSign`. Consequently, a real acceptance test requires Cl@ve and a
real authorized administrative case.

The test stopped before Cl@ve and before creating, modifying, signing or saving
any registration. Profile version 1 remains `VERIFIED_CONTRACT / QA_ONLY`; the
public catalog remains `E2E_PENDING / IMPLEMENTED_NOT_E2E`. No origin, tuple,
XAdES adapter, capability or release policy changed.

TDD/catalog evidence:

- RED: the generator test rejected the stale 2026-07-18 limitations;
- the canonical inventory now records the live Cl@ve/administrative blocker and
  review date 2026-07-30;
- the generated public resource must remain byte-for-byte reproducible;
- no credential, certificate, XML, signature, cookie, form value or case ID was
  retained.

Fresh verification:

- `testDebugUnitTest`: 452 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 452 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`, `assembleQa`: PASS;
- Python catalog/tool tests: 75 tests, 0 failures/errors, 1 skipped;
- public catalog generator reproducibility: PASS;
- QA APK SHA-256:
  `ceae0d202796cc8788011aa880fbf6a7aabdca34789caa340f0eae12dcd573cc`.

## Milestone F05 — state-driven secure window — 2026-07-30

The previous `MainActivity` policy enabled `FLAG_SECURE` only while the
certificate password field was visible. After a successful unlock it cleared
the flag, exposing certificate identity, native catalog, authenticated WebView
content and native signing UI to screenshots or screen recording.

A pure `SensitiveWindowStatePolicy` now drives the window flag:

- `LoadingReference` and `NoCertificate` are unprotected only while signing is
  `Idle`;
- `Locked`, `Unlocking` and `Unlocked` are always protected;
- any non-idle signing state is protected as a fail-safe even if certificate
  state is inconsistent;
- the Compose effect still clears the flag on Activity disposal;
- `ProtocolProbeActivity` and first-run no-certificate visual tests are not
  changed.

TDD evidence:

- RED: focused unit compilation failed because the state policy did not exist;
- GREEN: forced `SensitiveWindowProtectionTest` run completed 3 tests with 0
  failures/errors; the policy covers loading/no-certificate, password,
  unlocking, unlocked, signing and failed states;
- `CertificateSetupFlowTest` now requires the real window flag to remain set
  after unlock, background/resume and Activity recreation, and while manually
  locked.

Fresh global verification:

- `testDebugUnitTest`: 453 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 453 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 75 tests, 0 failures/errors, 1 skipped;
- APK alignment: Debug, QA and QA AndroidTest PASS;
- QA APK Signature Scheme v2: verified, one signer;
- forbidden exact canary scan: PASS;
- Debug APK SHA-256:
  `da984cf742ea8106091d7a1575ddf85f22eafa09207d15f89d5e8fe09376f97a`;
- QA APK SHA-256:
  `fe303b10658a8fcf3698e00d42e5714e4d7b42ba28208c8beb196da505963199`;
- QA AndroidTest APK SHA-256:
  `3f516205ac62d96fd42de924a7a817b1f9a4723a8a8f878bdc412ff6c133e4d2`.

Physical-device verification:

- `pm install -r`: Success;
- installed `base.apk` hash exactly matched the QA APK;
- encrypted unlock cache remained 101 bytes, mode `600`;
- force-stop/cold launch restored the certificate without a password prompt;
- `dumpsys window` reported `SECURE` on the restored certificate screen;
- UniZAR smoke returned `VERIFIED_E2E / OPEN_REQUESTED / WEBVIEW_ACTIVE` and the
  same MainActivity window still reported `SECURE`.

No certificate screen or authenticated portal screenshot was created or
committed during this gate.

## Milestone F08 — profile-scoped cookies and site data — 2026-07-30

The previous browser action combined certificate-session closure with
`CookieManager.removeAllCookies`, `WebStorage.deleteAllData`, cache/history and
form-data deletion. The historical cookie bridge also accepted a global Junta
origin allowlist instead of one active profile.

The implementation now has three separate boundaries:

- `ProfileCookieBridge` and the compatibility `WebViewCookieBridge` require one
  `SiteProfile` and accept only its exact declared network endpoint URLs;
- `SiteDataCleaner.clearOrigin` deletes WebStorage only for the current HTTPS
  origin. With `GET_COOKIE_INFO`, it creates expired same-host cookies from only
  name/path/domain/Secure metadata and never copies values. Unsupported,
  parent-domain or malformed metadata returns the limited result without global
  fallback;
- `clearAllConfirmed` is the only global WebView-data path and is reachable from
  a separately named and confirmed UI command. `Cerrar sesión` now only closes
  the portal and locks the certificate.

`WebViewProfileCapabilities` records provider package/version plus
`MULTI_PROFILE`, `GET_COOKIE_INFO`, `WEB_MESSAGE_LISTENER` and
`DOCUMENT_START_SCRIPT`. No trust decision depends on these booleans and the app
does not call `WebViewCompat.setProfile` in this milestone.

TDD evidence:

- RED core: the capability, cleaner and profile bridge types/method were absent;
- GREEN core: new isolation/cleanup tests and the rewritten compatibility bridge
  tests passed together;
- RED UI: `BrowserLayout` lacked distinct current-site/global callbacks;
- GREEN UI: Compose tests prove three menu actions and independent confirmations;
  a source regression forbids direct global deletion from `BrowserScreen`.

Fresh verification:

- `testDebugUnitTest`: 464 tests, 0 failures, 0 errors, 0 skipped;
- `testQaUnitTest`: 464 tests, 0 failures, 0 errors, 0 skipped;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 75 tests, 0 failures/errors, 1 skipped;
- APK alignment: Debug, QA and QA AndroidTest PASS;
- QA APK Signature Scheme v2: verified, one signer;
- QA manifest: `debuggable=true` (expected QA), `allowBackup=false`,
  `usesCleartextTraffic=false`;
- exact forbidden-canary scan: PASS;
- Debug APK SHA-256:
  `190a56b25d9f625a6a1b6a39ee513855388122f49be7357915bbf3187f5b9db9`;
- QA APK SHA-256:
  `40f03d634b5053b0b79a217b88edf65ca32a3c57c5b36d0371ab968f9bc558b7`;
- QA AndroidTest APK SHA-256:
  `ceafc6b65c513b1e5e6fb5ed728bd376c7617557144e3c5581088dce782bf68f`.

Physical-device capability gate:

- target/test installation: Success; installed `base.apk` matched the QA hash;
- encrypted unlock cache remained 101 bytes, mode `600`;
- one named instrumentation class returned `OK (1 test)`;
- provider `com.google.android.webview` version `150.0.7871.181` reported all
  four measured capabilities as true;
- `MainActivity` was never resumed and the target process did not remain alive;
- no URL, cookie, certificate or portal content was emitted.

## Milestone F17 — public IPv6 DNS-result policy — 2026-07-30

Android previously rejected every `Inet6Address`, so IPv6-only endpoints and
valid DNS64 results failed before connect. The Go QA relay allowed global IPv6
more broadly, including addresses outside `2000::/3`, and normalized mapped IPv4
before classification. The two network boundaries therefore had different
semantics.

F-17 introduces equivalent reviewed policy:

- policy revision is pinned to the IANA IPv6 Special-Purpose Address Space
  snapshot dated 2025-10-09;
- ordinary IPv6 must be in `2000::/3` and outside the closed special-purpose
  table;
- scoped and IPv4-mapped addresses fail closed;
- well-known NAT64 `64:ff9b::/96` is allowed only when its embedded IPv4 passes
  the complete public IPv4 deny-policy;
- profile URLs still require canonical DNS hostnames and never accept IP
  literals;
- OkHttp keeps the original endpoint hostname and pins DNS plus the actual
  connected address;
- the Go relay rejects unsafe mixed DNS sets, dials a bracketed IPv6 literal and
  verifies that `RemoteAddr` equals the selected address.

TDD evidence:

- Android RED: the new policy type did not exist;
- Android GREEN: `PublicIpAddressPolicyTest` 7/7 and
  `ProfileHttpTransportTest` 11/11 passed; the wildcard regression also ran the
  18 direct-first transport tests;
- second RED: three reviewed special IPv4 ranges passed directly and through
  NAT64; adding them to the shared IPv4 policy returned GREEN;
- Go RED: the pinned registry revision/new classifier were absent;
- Go GREEN: classifier, mapped/zoned rejection, deterministic selection,
  bracketed IPv6 literal and exact-peer verification passed.

Fresh global verification:

- Debug unit: 473 tests, 0 failures/errors/skips;
- QA unit: 473 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Go `test ./... -count=1` and `go vet ./...`: PASS;
- Python catalog/tool tests: 75, 0 failures/errors, 1 environmental skip;
- APK alignment: Debug, QA and QA AndroidTest PASS;
- QA APK Signature Scheme v2: verified, one signer;
- forbidden exact canary scan: PASS;
- Debug APK SHA-256:
  `c4ded880e4310d21e1818a5878424e091a9ef1863626069d9f3ebfd37d4afec6`;
- QA APK SHA-256:
  `46788b0c65380aab91ff02bccde2d5f4dafe931320bf58fe4e7e645e5772c013`;
- QA AndroidTest APK SHA-256:
  `7182651ac0926cf65f4bcf0a6cd067b819f5a512a92d1a2e54c20f8f21a21acf`.

The previously deferred device classifier gate was completed during the F-13
installation. `PublicIpAddressPolicyInstrumentedTest` returned `OK (1 test)` on
the physical Android implementation with the reviewed revision `2025-10-09`:
ordinary global IPv6 and NAT64 with a public embedded IPv4 were accepted, while
NAT64 with a non-public embedded IPv4 was rejected. This is a classifier test,
not a live IPv6 route or portal E2E claim.

## Milestone F13 — process-scoped Client TLS preference lifecycle — 2026-07-30

The previous Client TLS flow invoked `WebView.clearClientCertPreferences`
asynchronously but could create or recreate a WebView before a reliable process-
wide completion barrier. A missing callback or Activity recreation could lose the
failure state, and late callbacks had no shared generation owner.

F-13 adds one process-scoped boundary:

- `JuntaFirmaApplication` owns `ClientCertPreferenceCoordinator`;
- the coordinator publishes `IDLE`, `CLEARING` and sticky `FAILED` state;
- the platform static API exists only in `AndroidClientCertPreferenceClearer`;
- every portal `AndroidView` is suppressed during `CLEARING` or `FAILED`;
- timeout is exactly three seconds and exception/timeout remain fail-closed;
- callbacks are generation-bound; superseded or late callbacks cannot activate a
  grant or clear a newer failure;
- a Client TLS grant is activated only after `CLEARED` plus exact profile,
  navigation epoch and TTL revalidation;
- background, Activity disposal, renderer death, certificate lock and profile
  switch detach local callbacks and request process cleanup;
- only a later successful clear returns the process to `IDLE`;
- the coordinator stores no certificate, private key, WebView, URL, cookie or
  signed payload.

TDD and regression evidence:

- RED: coordinator and process-owned WebView gate were absent;
- barrier tests: 7/7;
- coordinator tests: 7/7, including synchronous callback, timeout, exception,
  retry, supersession, listener cancellation and stale callback;
- Client TLS authorizer/request-handler/dedicated-WebView tests: 16/16;
- browser security source regressions: 11/11;
- QA AndroidTest compilation: PASS.

Fresh global verification:

- `testDebugUnitTest`: 489 tests, 0 failures/errors/skips;
- `testQaUnitTest`: 489 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 75, 0 failures/errors, 1 environmental skip;
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- APK alignment: Debug, QA and QA AndroidTest PASS;
- APK Signature Scheme v2: verified, one signer for all three artifacts;
- QA manifest: `debuggable=true` (expected QA), `allowBackup=false`,
  `usesCleartextTraffic=false`, not `testOnly`;
- exact forbidden-canary scan: PASS;
- Debug APK SHA-256:
  `9fb8c65c11f446ea4bce2d87c140e02a034748656f12e9a68c3649507aec87fe`;
- QA APK SHA-256:
  `8547b3e45cd27636b6b716059fbc216e7cc5cff93c3fc2dca69149f18dad3477`;
- QA AndroidTest APK SHA-256:
  `73bfc781ee2e3eabf275dcdaca811efc398c0735fdbcbf29493e4367b68ea618`.

Physical-device verification:

- target and test APK installation: `Success`;
- installed `base.apk` exactly matched the QA hash;
- `ClientCertPreferenceCoordinatorInstrumentedTest`: `OK (1 test)` using the
  real Android preference-clear callback without opening a WebView or portal;
- the deferred F-17 classifier instrumentation also returned `OK (1 test)`;
- encrypted 24-hour unlock cache remained 101 bytes, mode `600`;
- force-stop/cold launch restored the unlocked certificate without a password
  prompt and the MainActivity window retained `FLAG_SECURE`;
- the test package and staging files were removed and ChatGPT was returned to the
  foreground.

This milestone hardens the already observed Carné Joven Client TLS lifecycle. It
does not verify or enable any additional Client TLS portal; that remains F-03.

## Milestone F09/F10 — monotonic TTL and replay hardening — 2026-07-30

The previous signing/reply lifetimes used civil wall-clock values and replay IDs
were bounded only by capacity. A system-clock jump could change authorization
windows, while a long-lived process could eventually fill a permanent replay set.
The JavaScript shim also contained a non-cryptographic fallback for identifiers.

F-09/F-10 introduces one consistent boundary:

- `MonotonicSecurityTime` uses process monotonic nanoseconds for every security
  lifetime decision;
- request observation, pending confirmation, active PRE/local/POST operation and
  MiniApplet reply all share the same two-minute window;
- civil `Instant` remains only display/context metadata;
- `BoundedReplayLedger` retains terminal IDs for five minutes, prunes at the exact
  boundary before capacity checks and fails closed on monotonic rollback;
- pending signing capacity remains 1024 IDs and reply capacity 64 IDs, now without
  permanent denial of service after retention expires;
- the shim uses Web Crypto UUID generation only and contains no `Math.random()`;
  missing Web Crypto stops bridge forwarding fail-closed.

Hostile/regression evidence:

- exact monotonic TTL boundary and rollback;
- civil clock jumps forward/backward without changing the security window;
- replay retention/pruning and capacity recovery;
- two concurrent confirmations with only one PRE/local/POST owner;
- concurrent success/failure with exactly one terminal delivery;
- stale/replayed callbacks and changed origin/navigation remain rejected;
- source regression prohibits civil-clock expiry and weak JS identifiers.

Fresh global verification:

- `testDebugUnitTest`: 499 tests, 0 failures/errors/skips;
- `testQaUnitTest`: 499 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 75, 0 failures/errors, 1 environmental skip;
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- APK alignment and APK Signature Scheme v2: PASS for Debug, QA and AndroidTest;
- QA manifest hardening and exact forbidden-canary scan: PASS;
- Debug APK SHA-256:
  `51c425a45e8b8fee3a384a9c8098771f0896eb456c78eae9bcfdf810170c0824`;
- QA APK SHA-256:
  `0258378038d703979239c8701e1e8d2ce68ecabc7de5699b68cbccbef1e5ceec`;
- QA AndroidTest APK SHA-256:
  `4cc4be8ae9ca7d3300f444d7a40c4dd5d83a4005f9b53080ef09521ab02a2904`.

Physical-device acceptance:

- target/test APK installation returned `Success`;
- installed `base.apk` exactly matched the QA hash;
- encrypted unlock cache remained 101 bytes, mode `600`;
- cold launch restored the certificate without password and retained
  `FLAG_SECURE`;
- F-13 Client TLS callback regression and F-17 IPv6 classifier each returned
  `OK (1 test)`;
- the authorized user then completed a fresh Oficina Virtual certificate login
  with this exact build and reported correct portal opening and correct UI/menu
  behavior;
- scope remains login CAdES only: no document signing or administrative
  submission is claimed;
- the test package and F-09 staging files were removed after acceptance.

No screenshot, password, PKCS#12, certificate body, signature, cookie or personal
identifier was retained in the repository.

## Milestone F14 — CI and supply-chain gate — 2026-07-31

F-14 adds repository-enforced build and dependency controls without changing the
runtime portal, signing or direct-only release policy:

- `.github/workflows/ci.yml` runs separate Android, Python and Go jobs with
  read-only permissions, immutable third-party action SHAs, timeouts and
  concurrency cancellation;
- `.github/workflows/security.yml` scans complete Git history with pinned
  Gitleaks 8.30.1 and scans the explicit Python/Go manifests with pinned
  OSV-Scanner 2.3.8;
- Dependabot covers Gradle, Go modules and GitHub Actions weekly;
- Gradle 9.4.1 distribution checksum is pinned and the wrapper JAR was regenerated
  for the same version. The previous JAR was official but did not match the
  configured Gradle version;
- Gradle dependency verification records SHA-256 metadata/artifacts and contains
  no wildcard trust rule;
- Android artifact scripts verify alignment, signature scheme v2, exactly one
  signer, hardened manifest values and forbidden canaries;
- release without private signing inputs must fail and leave no release APK;
- Go is pinned to 1.26.5; Linux CI retains the race detector gate.

TDD evidence:

- RED: policy tests identified a missing Gradle distribution checksum, a setup-go
  cache path for nonexistent `go.sum`, an unsafe `yes | sdkmanager` pipeline under
  `pipefail`, mismatched wrapper JAR version, recursive OSV scope and Go 1.24;
- GREEN: 10/10 CI policy tests pass after exact, minimal corrections;
- action tag commits were resolved against each official action repository and
  matched every pinned SHA;
- official Gradle distribution/wrapper and Gitleaks archive checksums matched.

Fresh local verification:

- Python: 85 tests, 0 failures/errors, 1 expected environmental skip;
- Debug unit: 499 tests, 0 failures/errors/skips;
- QA unit: 499 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`,
  `assembleQaAndroidTest`: PASS;
- APK alignment, v2 signature, exactly one signer, manifest hardening and exact
  forbidden-canary scan: PASS;
- release-signing fail-closed gate: PASS; no release APK remained;
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- `govulncheck` 1.6.0: no reachable Go vulnerabilities found;
- OSV explicit scans of `tools/requirements.txt` and `ws024-relay/go.mod`: exit 0,
  no vulnerable packages reported;
- Gitleaks history scan under Debian/proot: 166 commits, 3,992,451 bytes, zero
  findings. Native Android execution was blocked by seccomp `faccessat2`; the same
  checksum-verified ARM64 binary completed in Debian/proot;
- workflow/Dependabot YAML parsing and both CI shell scripts: PASS.

Artifact SHA-256 values remain identical to the preceding F-09/F-10 build:

- Debug: `51c425a45e8b8fee3a384a9c8098771f0896eb456c78eae9bcfdf810170c0824`;
- QA: `0258378038d703979239c8701e1e8d2ce68ecabc7de5699b68cbccbef1e5ceec`;
- QA AndroidTest:
  `4cc4be8ae9ca7d3300f444d7a40c4dd5d83a4005f9b53080ef09521ab02a2904`.

Limitations are explicit:

- local `go test -race` is not supported on Android/arm64 and is not marked PASS;
  the required Linux CI job provides that gate;
- OSV does not scan `gradle/verification-metadata.xml` as a runtime lockfile. It is
  an integrity ledger containing build/test tooling. Gradle dependency
  verification and Dependabot remain active, but they do not prove that the full
  Gradle graph is free of known vulnerabilities. A dedicated reviewed Gradle SCA
  remains future hardening.
