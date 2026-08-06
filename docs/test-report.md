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

## Milestone F15B — catalog-generation deduplication — 2026-07-31

F-15B removes the remaining manually maintained copies from the profile/public
catalog pipeline without changing portal trust, activation, protocol or E2E
scope:

- `config/site_profiles_v1.json` is now the sole committed profile data source;
  its SHA-256 is unchanged from the former raw resource:
  `a45cf2bbfe13d3492a963d0b8866c676ec13e5e95fac99e0cf2e0eeac568dc4c`;
- Gradle emits that exact JSON as `BuildConfig.SITE_PROFILE_CATALOG_JSON` and
  `BuiltInSiteProfiles` parses it; the handwritten Kotlin JSON and Android raw
  profile resource are removed;
- the public inventory now contains 182 records and 210 registered sources
  (198 portal-specific plus 12 enumerators);
- Oficina Virtual and Educación convocatoria 46 are inventory records
  `ES-PUB-0181` and `ES-PUB-0182`, not Python supplemental objects;
- the generator has no `PROFILE_BINDINGS` or `_supplemental_entries()` and maps
  all seven profiles by exact equality of profile `startUrl` and inventory
  `entry_url`;
- malformed/unexpected profile roots, duplicate profile IDs/start URLs, missing
  matches, multiple matches and profile/surface collisions fail closed.

TDD and semantic evidence:

- RED focused tests failed because the two-argument generator API, canonical
  profile source and generated BuildConfig field did not exist;
- GREEN focused generator tests: 7/7;
- the committed public catalog is byte-for-byte reproducible from the two local
  canonical sources;
- semantic comparison against the preceding 182-entry catalog found zero added
  or removed portals and zero unexpected field changes. Only
  `junta-andalucia-ofvirtual` and `educacion-convocatoria-46` changed
  `inventoryId` from `null` to their stable IDs; `sourceRevision` changed as
  required by the inventory update;
- profile/catalog JVM regression tests passed and all seven exact bindings remain
  covered by the existing consistency gates.

Fresh final verification on the completed content:

- Debug unit: 499 tests, 0 failures/errors/skips;
- QA unit: 499 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 91 tests, 0 failures/errors, 1 environmental skip
  (`hardlinks unavailable`);
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- pinned `govulncheck` 1.6.0: no vulnerabilities found;
- Android artifact verification: alignment, v2 signature, exactly one signer,
  QA manifest hardening and exact forbidden-canary scan PASS;
- release-signing fail-closed gate: PASS; no release APK remained;
- Debug APK SHA-256:
  `cce4e9c36668bb62520c9f7ccfa7cffbda5626b84230e56e9bc5deb9dd5573e7`;
- QA APK SHA-256:
  `d57ccc3850c8f44d4f01f5d578c5c0a9013c7310d98c40faa39cc1fc1f8ace6d`;
- QA AndroidTest APK SHA-256:
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`.

Test-stability limitation:

- two earlier combined clean invocations observed the existing bounded DNS
  executor test immediately after its saturation test returning transient
  `NETWORK_ERROR` for a blocked address. F-15B changes no network source. The
  exact test, full Debug suite, full QA suite and final combined current-content
  gate all passed on rerun. This is recorded as residual order-sensitive test
  teardown work; no runtime network defect or portal regression is claimed.

No APK was installed, no physical-device instrumentation was executed and no
portal, certificate, authentication or signature flow was opened for F-15B.


## Deterministic DNS executor unit-test isolation — 2026-07-31

The residual order-sensitive failure recorded after F-15B was caused by JVM
tests sharing the process-wide bounded DNS executor, not by the public-address
classifier or a portal regression. With a zero-core `ThreadPoolExecutor` and a
`SynchronousQueue`, caller/Future completion can precede a worker returning to
the handoff queue. A rapid following test submission may therefore be rejected
and correctly map to fail-closed `NETWORK_ERROR`.

The first implementation isolated only the saturation test. Its focused test,
three Debug repeats, three QA repeats, and full suites initially passed. The
mandatory fresh pre-commit run then failed QA:

- test: `everyRepresentativeNonGlobalDnsRangeIsRejectedBeforeConnect`;
- address: `2001:10::1`;
- expected: `PRIVATE_ADDRESS`;
- actual: `NETWORK_ERROR`.

That second RED disproved the saturation-only diagnosis. The final design is
broader but remains test-scoped:

- `HttpsProfileHttpTransport` accepts an internal `ExecutorService`, defaulting
  at runtime to the unchanged process-wide `DNS_EXECUTOR`;
- `DirectTestExecutorService` executes synchronous JVM resolver tasks inline and
  owns no worker thread;
- timeout and cancellation subcases each own a separate bounded executor and
  require bounded termination;
- saturation owns an identically configured bounded executor and terminates it;
- all 18 JVM-test constructions of `HttpsProfileHttpTransport` explicitly supply
  a test-owned executor, including the secure-tunnel harnesses.

Production invariants remain exact: 0 core workers, 2 maximum workers,
30-second keep-alive, `SynchronousQueue`, daemon threads, `AbortPolicy`,
core-thread timeout, and fail-closed `NETWORK_ERROR` on rejected submission. No
retry, queue, fallback, DNS timeout, URL policy, public-IP policy, peer pinning,
or HTTP behavior changed.

TDD and stability evidence:

- RED 1: focused compilation failed with
  `No parameter with name 'dnsExecutor' found`;
- GREEN 1: injected-executor test passed after the minimal seam;
- RED 2: the fresh QA failure above exposed remaining process-executor use;
- GREEN 2: exact combined Debug/QA focused command PASS;
- additional final focused repetitions: Debug 5/5, QA 5/5;
- explicit source audit: 18/18 JVM-test transport constructors specify
  `dnsExecutor`.

Fresh final verification on the corrected content:

- Debug unit: 500 tests, 0 failures/errors/skips;
- QA unit: 500 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- `verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`: PASS;
- Python catalog/tool tests: 91 tests, 0 failures/errors, 1 environmental skip
  (`hardlinks unavailable`);
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- Android artifact verification: alignment, v2 signature, exactly one signer,
  QA manifest hardening and exact forbidden-canary scan PASS;
- release-signing fail-closed gate: PASS; no release APK remained;
- Debug APK SHA-256:
  `dbddc5a31a719fa59ff6a5d7ec1a7199f4fe916982f07399327e3869c0754758`;
- QA APK SHA-256:
  `6132831e16ddd807c2ac7ec4ddea3a6d63ab5045ce6f89d6365157a493300944`;
- QA AndroidTest APK SHA-256:
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`.

`govulncheck` was not installed in the current Termux environment and was not
rerun for this Android/test-only change. Relay source and module files did not
change; Go test/vet/build were rerun. Local Go race instrumentation remains
unsupported on Android/arm64 and is provided by Linux CI.

No APK was installed, no physical-device instrumentation was executed and no
portal, certificate, authentication or signature flow was opened.


## Android runtime dependency SCA — 2026-07-31

This milestone closes the reviewed Gradle runtime-SCA gap without changing any
application dependency version or runtime behavior:

- strict dependency locking is activated only for
  `debugRuntimeClasspath`, `qaRuntimeClasspath` and
  `releaseRuntimeClasspath`;
- `app/gradle.lockfile` contains 140 external Maven component rows plus Gradle's
  canonical trailing `empty=` sentinel;
- lock SHA-256:
  `286bcc684775520851aa5de6a4bb01fa172a72ca87dae2dc73e671fc76afa64d`;
- no unit-test, Android-test, lint, buildscript or plugin configuration is
  included in the runtime claim;
- `verifyRuntimeDependencyLocks` materializes artifact views and is run before
  OSV in the security workflow;
- `scripts/ci/update-android-runtime-lock.sh` accepts only the exact generated
  `empty=incomingCatalogForLibs0` settings sentinel, removes it and rejects any
  other root lock state.

TDD and hostile evidence:

- RED 1: four policy tests failed for missing lockfile, missing
  `LockMode.STRICT`, missing verification task and missing Android OSV input;
- GREEN 1: the four focused tests passed after the minimal scoped lock/workflow
  implementation;
- RED 2: a temporary `0.0.0-stale-lock` mutation unexpectedly passed while the
  task only read `resolutionResult`; this result was rejected;
- GREEN 2: after materializing artifact views, the same mutation failed on
  `debugRuntimeClasspath` with `Dependency version enforced by Dependency
  Locking`, and the trap restored the original lock with no diff;
- RED/GREEN maintenance: direct `--write-locks` generated a non-runtime
  settings lock; updater policy tests failed before the script existed, then
  passed and a second update reproduced the identical lock SHA-256;
- staged review found that the first cleanup trap would delete unexpected
  settings-lock evidence after failure. A new policy test failed on that trap;
  the final updater removes only exact known content and preserves unknown drift.

Pinned vulnerability scan:

- official OSV-Scanner release: `v2.3.8`, Linux ARM64;
- downloaded binary matched the publisher's `osv-scanner_SHA256SUMS` entry:
  `8158b18edd2d03b1a30d905ca91b032bc62262167be8f206c27114f08823e27c`;
- native Termux execution was blocked before scanning by Android seccomp on
  `faccessat2` and is not marked PASS;
- the same verified binary ran in `ws024-gate-debian` proot and found 140 Android
  packages, one Python package and one Go package;
- result: `No issues found`, exit 0.

Fresh final verification:

- Debug unit: 500 tests, 0 failures/errors/skips;
- QA unit: 500 tests, 0 failures/errors/skips;
- Python: 94 tests, 0 failures/errors, 1 environmental skip
  (`hardlinks unavailable`);
- strict runtime lock, `verifyResolvedCoreVersion` and
  `verifyPortableAapt2Configuration`: PASS;
- `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`,
  `assembleQaAndroidTest`: PASS; 143/143 fresh Gradle tasks executed;
- Android artifact verification: alignment, APK Signature Scheme v2, exactly one
  signer, QA manifest hardening and forbidden-canary scan PASS;
- release-signing fail-closed gate: PASS; no release APK remained;
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- Debug APK SHA-256:
  `7a93dddcccc90b339e33df55f6cac8a24ad26acfe4b8ced7c6ed6707dee62233`;
- QA APK SHA-256:
  `7c99595546f9fa8cb0e6bd77832531c648ffa06d62081309d244b5bad840abcd`;
- QA AndroidTest APK SHA-256:
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`.

Claim boundaries remain explicit: locking fixes exact versions; dependency
verification authenticates downloaded files; OSV reports currently known
vulnerabilities. No portal, WebView, certificate, authentication, signing,
physical-device or APK-install operation was performed.

## Milestone F-03 — AEAT exact Client TLS QA profile — 2026-07-31

F-03 adds one additional Client TLS contract without broadening release trust:

- profile `aeat-mis-datos-censales`, version 1,
  `VERIFIED_CONTRACT / QA_ONLY`;
- exact source:
  `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`;
- exact queryless target:
  `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`;
- explicit transition mode `DIRECT_FROM_SOURCE`; Carné Joven retains the
  separate `REDIRECT_AFTER_SOURCE` contract;
- legacy callbacks, subframes, wrong profile/source, suffix host, non-443,
  alternate or encoded path, fragment, any query and an empty `?` fail closed;
- direct grants are one-shot per exact profile/source/target/epoch tuple, and a
  hostile navigation cannot reset the consumed marker;
- AEAT permits RSA/EC, requires a non-empty acceptable-issuer list and retains
  the existing epoch, TTL, validity, keyUsage and EKU checks;
- release resolves neither the profile nor its source/request origins.

TDD evidence:

- parser/model RED: explicit transition mode absent; GREEN after the minimal
  enum/parser contract;
- direct-authorizer RED: exact AEAT transition unsupported; GREEN after mode
  dispatch and queryless exact matching;
- hostile replay RED: a rejected navigation could reset the consumed direct
  grant; GREEN after separating pending-state cleanup from lifecycle invalidation;
- profile/catalog RED: AEAT was unbound metadata; GREEN with one QA-only profile
  and exact inventory binding;
- request-handler regression: matching RSA + issuer proceeds once; empty issuer
  fails closed and clears Client TLS preferences.

Fresh complete verification:

- runtime lock, core version and portable AAPT2 gates: PASS;
- Debug JVM: 509 tests, 0 failures/errors/skips;
- QA JVM: 509 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Gradle: `BUILD SUCCESSFUL`, 143/143 tasks executed;
- Android artifact verification: PASS;
- release-signing fail-closed verification: PASS;
- Python: 94 tests, 0 failures/errors, 1 environmental skip
  (`hardlinks unavailable`);
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- Debug APK SHA-256:
  `7b956e3369ea9efa133e0bd8a4b8ce49a00e4b2c3282eec8c1ce619db3902b35`;
- QA APK SHA-256:
  `ca5b351656cb41904f3774ed2a84ac002041d9babdf5fe877d6191d04d6befe2`;
- QA AndroidTest APK SHA-256:
  `0030fc08655511c51ec284f87775dc0a231e80ebdc1832f3ae0d9a5cf5c365f4`.

Physical-device gate:

- `pm install -r -t`: `Success`; local/staging/installed hashes matched the QA
  hash above;
- protected catalog smoke resolved `aeat-sede` exactly but returned
  `profileResolvedOnly=1`, `webViewActive=0`, with zero failures;
- MainActivity showed only allowlisted locked-state markers
  `Contraseña del certificado`, `Desbloquear certificado`, `Elegir otro` and
  `Olvidar certificado`;
- the password was not read, copied, logged or automated;
- WebView `ClientCertRequest`, native confirmation and accepted AEAT login were
  not reached.

The physical outcome is recorded in
`docs/e2e/2026-07-31-aeat-client-tls-blocked.md`. The profile remains
`VERIFIED_CONTRACT / QA_ONLY`; the public entry remains
`E2E_PENDING / IMPLEMENTED_NOT_E2E`. No signing, tax modification, payment or
administrative submission was attempted.


### F-03 physical continuation after manual unlock — 2026-08-01

The user manually unlocked the existing certificate on the installed QA build;
the password was not supplied to, read by, logged by or automated by the agent.

Sanitized observations:

- unlocked UI state confirmed by `Bloquear certificado`, `Elegir otro` and
  `Olvidar certificado` markers;
- protected `aeat-sede` smoke: `total=1`, `webViewActive=1`,
  `profileResolvedOnly=0`, `catalogOnly=0`, `failures=0`;
- with `Junta Firma` restored to foreground, the exact public label
  `Mis datos censales` was observed in the WebView.

The source-to-target click was not executed. Android Control Bridge repeatedly
brought its own Activity to foreground; the first exact-click attempt therefore
inspected the wrong application and was rejected. After force-stopping that
service UI and restoring `Junta Firma`, the next system UI dump was empty. No
coordinate guess or blind click was used.

Therefore the following gates remain unproven: WebView `ClientCertRequest`,
native certificate confirmation and accepted authenticated read-only AEAT
landing. The status remains `VERIFIED_CONTRACT / QA_ONLY` and
`E2E_PENDING / IMPLEMENTED_NOT_E2E`.

Continuation evidence:
`docs/e2e/2026-08-01-aeat-client-tls-partial.md`.


## Autonomous G1-01 — QA WebView debugging boundary — 2026-08-04

Reproduction showed that `TrustedJuntaWebView` used
`setWebContentsDebuggingEnabled(BuildConfig.DEBUG)` while the generated QA
BuildConfig had both `BUILD_TYPE="qa"` and `DEBUG=true`. The acceptance QA
variant therefore inherited WebView remote-debugging capability from the broad
debuggable flag.

The remediation adds the explicit build field
`ENABLE_WEBVIEW_CONTENTS_DEBUGGING`: default false, Debug true, QA false and
Release false. `TrustedJuntaWebView` uses only this field. QA remains debuggable
for its existing controlled diagnostics; network, origin, TLS, profile, signing
and certificate policies are unchanged.

TDD sequence:

- RED: focused Python policy test failed because the explicit boundary was absent;
- an intermediate loose-regex GREEN was rejected during diff review because it
  had allowed an invalid configuration (Debug both false/true, QA without an
  explicit false);
- the test was tightened to isolate each Gradle build block and observed RED on
  that malformed state;
- GREEN: the corrected policy passed the focused test.

Fresh verification:

- Debug JVM: 509 tests, 0 failures/errors/skips;
- QA JVM: 509 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS, 0 errors and 27 warnings per variant;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- combined Gradle gate: `BUILD SUCCESSFUL`, 140 actionable tasks;
- generated BuildConfig: Debug WebView debugging `true`, QA `false`;
- Python: 95 tests, 0 failures/errors, 1 environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./...`: PASS;
- Android artifact verification: PASS;
- release signing without private inputs: expected fail-closed PASS; no release
  APK was produced;
- Debug APK SHA-256:
  `2f45274f105faac67c5cedd3272278cad1a7b77bae592730fecea3786da7b4c4`;
- QA APK SHA-256:
  `d326174c55a470f5a857574342ebad9dd0e6a68e82768f95c061e895d4749e62`;
- QA AndroidTest APK SHA-256:
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

No APK installation, app launch, device control, portal navigation, certificate
operation, credential use, signing, upload, payment or submission occurred.


## Autonomous G1-02 — network failure-detail visibility — 2026-08-04

The previous public `ProfileHttpResult.Failure` data class exposed the internal
`ProfileHttpFailureDetail` type and suppressed Kotlin
`EXPOSED_PARAMETER_TYPE`/`EXPOSED_PROPERTY_TYPE` diagnostics. The internal detail
contains direct/tunnel fallback phase and HTTP-write state; production signing
consumers use the public failure `code`.

A synthetic Kotlin 2.3 fixture rejected the tempting data-class + internal-primary-
constructor shape because generated `copy()` retains broader visibility. The final
shape is an ordinary class with an internal primary constructor/internal `detail`,
while preserving public `Failure(ProfileHttpFailure)` and public `code`.

TDD sequence and compile evidence:

- RED: source/API policy test rejected the existing suppression/data-class shape;
- GREEN: policy test passed after only the visibility/class-shape change;
- `compileDebugKotlin` and `compileQaKotlin`: PASS, no `EXPOSED_*` or copy-
  visibility compiler diagnostics;
- focused `ProfileHttpTransportTest`, `DirectFirstProfileHttpTransportTest` and
  `TriPhaseExecutionAdapterTest`: PASS (`BUILD SUCCESSFUL`).

Fresh complete verification:

- Debug JVM: 509 tests, 0 failures/errors/skips;
- QA JVM: 509 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS, 0 errors / 27 warnings per variant;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- combined Android gate: `BUILD SUCCESSFUL`, 140 actionable tasks;
- Python: 96 tests, 0 failures/errors, 1 environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./...`: PASS;
- Android artifact verification: PASS;
- release signing without private inputs: expected fail-closed PASS;
- Debug APK SHA-256:
  `a28a116087eead23c183bd54f4fabbc6e8c3d449a740c3f5fac6595c5bdab7fe`;
- QA APK SHA-256:
  `e6e51ec7a92f072e806310937db1968016d04793ff8269344ea3ffaa2811dc0c`;
- QA AndroidTest APK SHA-256:
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

The standalone Termux `kotlinc` fixture emitted an environmental Jansi native-
library diagnostic (`libc.so.6` not found for bundled Jansi), but the selected
ordinary-class fixture returned exit 0 with no visibility/compiler warning. The
actual Gradle/Kotlin compilation is the authoritative repository gate.

No APK installation, launch, device control, portal request, certificate use,
credential, signature, upload, payment or submission occurred.


## Autonomous G2-01 — release-registry invariant evidence repair — 2026-08-04

The release runtime policy itself was already generic and fail-closed, but the old
`releaseRejectsSensitiveEnabledProfileWithoutVerifiedE2eEvidence` regression test was
a false-positive proof: it downgraded the first `VERIFIED_E2E` JSON occurrence
(`unizar-tramitador`) while asserting that the unrelated `junta-andalucia` profile
was absent from release.

The replacement test is catalog-driven. It verifies every sensitive non-E2E profile
is absent from release and, one profile at a time, downgrades every current
`ENABLED / VERIFIED_E2E` sensitive profile to `VERIFIED_CONTRACT`; release must then
exclude that exact profile while QA keeps it with the downgraded status. No runtime
source or profile data changed.

Verification evidence:

- focused final invariant test: Debug PASS and QA PASS;
- complete final JVM rerun after the catalog-wide assertion: Debug 509/509 and QA
  509/509, zero failures/errors/skips;
- complete cross-stack gate on the same unchanged production tree before the final
  test-only strengthening: toolchain pins PASS; Debug 509/509 and QA 509/509;
  `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` PASS;
- Android artifact verification PASS; release signing without private inputs rejected
  fail-closed as required;
- Python: 96 tests, 0 failures/errors, 1 environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
- APK SHA-256 unchanged from G1-02: Debug
  `a28a116087eead23c183bd54f4fabbc6e8c3d449a740c3f5fac6595c5bdab7fe`, QA
  `e6e51ec7a92f072e806310937db1968016d04793ff8269344ea3ffaa2811dc0c`,
  QA AndroidTest
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

Go race instrumentation remains an external Linux CI gate; it was not claimed on
Termux. No application/device/portal/credential/certificate/signing action occurred.


## Autonomous G2-02 — QA diagnostic journal clear boundary — 2026-08-04

The authoritative test plan says logger `clear` removes the journal, but QA also
persists sanitized records in `filesDir/qa-navigation.log`. The initial integration
test proved the mismatch: after `logger.clear()` the in-memory export was empty while
the file remained non-empty, failing at the final persisted-file assertion.

The fix keeps `SanitizedLogSink` SAM-compatible by adding only a default no-op
`clear()`. `SanitizedLogger.clear()` delegates best-effort to the sink, the QA file
sink truncates its app-private journal, and the QA composite propagates clear. The
release/non-QA sink remains no-op; event schemas, allowlists, hashes and capacities
are unchanged. Logcat erasure is not claimed.

Verification evidence:

- RED: focused Debug regression failed at persisted-file emptiness after clear;
- focused GREEN: complete `ApplicationSanitizedLoggerFactoryTest` PASS in Debug and
  QA;
- full Debug JVM: 510 tests, 0 failures/errors/skips;
- full QA JVM: 510 tests, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS, 0 errors / 27 warnings per variant;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS;
- Python: 96 tests, 0 failures/errors, 1 environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
- generated relay binary removed;
- Debug APK SHA-256:
  `079506fc28ee108c37b2a5bb929bfe5214dda767284fe8c9dac04e8e811adbec`;
- QA APK SHA-256:
  `c253e07b0cb94321e31769dc96dc1fd7f142f8a907884ecc7617254d0cb53e85`;
- QA AndroidTest APK SHA-256:
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

Go race instrumentation remains an external supported-Linux CI gate. No APK
installation/launch, device control, portal request, credential/certificate use,
real signing, upload, payment or administrative submission occurred.


## Autonomous G3-01 — CAdES capture backing-buffer zeroization — 2026-08-04

The CAdES `CapturingContentSigner` used `output.toByteArray().fill(0)` before
`reset()`. A standalone JVM subclass probe confirmed that the actual
`ByteArrayOutputStream.buf` still contained the canary (`retained=true`) after this
sequence. The new source-policy test observed RED against that exact implementation.

An initial fix that zeroed `buf` from an overridden stream `close()` was not accepted:
focused Debug CAdES tests produced two failures because BouncyCastle closes the
supplied stream during generation, causing the signed-attributes capture to be empty
before `signedBytes()`. The corrected implementation uses an explicit `clear()`
method and invokes it only from `CapturingContentSigner.close()`, matching existing
repository sensitive-stream patterns.

Final verification evidence:

- source-policy regression: PASS;
- corrected focused CAdES/LocalSignature tests: Debug PASS and QA PASS;
- toolchain pins: PASS;
- full Debug JVM: 510 tests, 0 failures/errors/skips;
- full QA JVM: 510 tests, 0 failures/errors/skips;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- final non-lint Android invocation: `BUILD SUCCESSFUL`, 127 actionable tasks;
- `lintDebug`, `lintQa`: PASS, 0 errors / 27 warnings per variant; separate lint
  invocation `BUILD SUCCESSFUL`;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS;
- Python: 97 tests, 0 failures/errors, 1 environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
- generated relay binary removed;
- Debug APK SHA-256:
  `f8d819a0de57e40ad7e1575a2c44ff8577d9b70a55ff5b53942a2fd3d2f1227e`;
- QA APK SHA-256:
  `96331ee7bddd782981a5b4900e906e27887ddc0dfd28698e62c17c38cbdb7f1b`;
- QA AndroidTest APK SHA-256:
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

One earlier all-in-one verification job reached its external 1800-second wrapper
timeout while lint analysis was still active, after unit and assemble tasks had run.
This was not treated as PASS or as a product failure. Lint was rerun to successful
completion separately, then pins/unit/assemble were rerun in a separate successful
invocation on the final tree.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Go race
instrumentation remains an external supported-Linux CI gate.

## Autonomous G4-01 — XAdES byte-stream backing-buffer zeroization — 2026-08-04

`XadesDetachedCodec.serialize()` and `canonicalize()` previously used ordinary
`ByteArrayOutputStream` and returned `toByteArray()` copies. A standalone JVM probe
showed that `close()` left the original XML canary in the protected backing buffer
while the returned copy also contained it. The focused source-policy test was added
first and failed on the old stream patterns.

The production change is limited to those two helpers and one private clearing
stream. Each helper gets the intentional output copy and clears the app-owned backing
buffer in `finally`; `clear()` zeros protected `buf` then resets it. XAdES algorithms,
canonicalization identifiers, output ownership, certificate chain and runtime
security policy are unchanged.

Verification evidence:

- RED: source-policy regression rejected the ordinary XAdES stream patterns;
- focused policy GREEN: 1/1 PASS;
- forced focused XAdES Debug+QA rerun: PASS, `BUILD SUCCESSFUL`, 60/60 tasks executed;
- toolchain pin checks: PASS;
- full Debug JVM: 510 tests, 0 failures/errors/skips;
- full QA JVM: 510 tests, 0 failures/errors/skips;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS; non-lint Android gate
  `BUILD SUCCESSFUL`, 127 actionable tasks;
- `lintDebug`, `lintQa`: PASS, 0 errors / 27 warnings per variant; separate lint gate
  `BUILD SUCCESSFUL`, 55 actionable tasks;
- Python: 98 tests, 0 failures/errors, 1 environmental hardlink skip;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
- generated relay binary removed;
- Debug APK SHA-256:
  `6a6b6e72006048ea9191de2b4b509cda21bb9f60b226386afa54ea872e753139`;
- QA APK SHA-256:
  `20740737b0e977e263192367de217f8f03262f59e4ba972e2a233da08b5e8810`;
- QA AndroidTest APK SHA-256:
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.

One guarded production-mutation command found the exact planned XAdES source diff
already present before its write step and stopped on an old-source assertion. A
follow-up process and hash stability check found no active mutator and no unrelated
source diff; the origin of that transient/in-flight write was not established. The
TDD RED had already been observed against the old source before this state appeared.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Go race
instrumentation remains an external supported-Linux CI gate.


## Autonomous G4-02 — persisted certificate-unlock threat-model reconciliation — 2026-08-04

The runtime already implements a bounded encrypted unlock-recovery cache: after a
successful manual PKCS#12 password entry, the password may remain for at most the
original 24-hour window as authenticated AES-256-GCM ciphertext in
`noBackupFilesDir`, protected by an Android Keystore AES key. The feature does not
persist the PKCS#12 bytes or private-key object. The existing threat model still
claimed lifecycle/process death locked the identity, despite explicit runtime tests
and P07C evidence for valid-cache restoration.

This milestone reconciles only `docs/threat-model.md` and a documentation-policy
regression. T5 now records original-expiry semantics, clearing conditions, process
death and memory-pressure recovery, API 28+ unlocked-device Keystore behavior and
residual app-privilege risk. No runtime or build behavior changed.

Verification evidence:

- RED: focused documentation-policy regression failed on the stale threat model;
- GREEN: the same focused policy test passed after reconciliation;
- Python: 99 tests, 0 failures/errors, 1 environmental hardlink skip;
- focused lifecycle Debug+QA: the three named `CertificateSession`/
  `CertificateViewModel` tests passed with `--no-daemon --rerun-tasks`;
  `BUILD SUCCESSFUL`, 60 actionable tasks, 60 executed;
- two earlier retry jobs are intentionally not counted as product verification:
  their `--tests` placement broadened Debug discovery while multiple Gradle jobs
  overlapped, and they failed during test-class execution. Duplicate jobs were
  removed and the exact task-scoped command then passed.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Go race
remains an external supported-Linux CI gate; AEAT F-03 remains manual.

## Autonomous G6-01 — public inventory deadline cleanup — 2026-08-04

A fresh complete Python run exposed one order/timing-sensitive failure in
`DeadlineTest.test_one_blocking_read_is_cancelled_at_the_wall_clock_deadline`.
The suite result was 99 tests, one failure and one environmental hardlink skip.
Five immediate focused reruns passed, so the result was not hidden as a stable
PASS. A deterministic probe established the exact control-flow defect: after the
I/O worker started, an already expired deadline caused `_remaining_seconds()` to
raise before the configured timeout cleanup callback could run.

The new deterministic test observed RED with `cleanup_called=False`. The minimal
production change introduces a private best-effort timeout-cleanup helper and
uses it both for post-start deadline-calculation failure and the existing live-
worker timeout path. Cleanup exceptions remain suppressed and the original
`InventoryError` classification is retained. No timeout is extended and no
network, TLS, DNS, redirect, origin, inventory or portal trust rule changes.

Fresh verification:

- deterministic regression: PASS after observed RED;
- complete `DeadlineTest`: 3 tests, zero failures/errors/skips;
- new plus original blocking deadline regressions: 10/10 sequential repetitions;
- complete Python discovery: 100 tests, zero failures/errors, one environmental
  skip (`hardlinks unavailable`);
- `python -m py_compile` for production and test modules: PASS;
- `git diff --check`: PASS.

The concurrent uncommitted G5-01 Android/WebView milestone was not modified by
this Python-only remediation. No APK, device, portal, certificate, credential,
signature, upload, payment or submission action occurred.

## Autonomous G5-01 — stale WebView callback ownership lease — 2026-08-04

The normal and dedicated Client TLS WebView clients did not prove that a callback's
`WebView` was still the active browser instance. Focused tests first observed RED at
compile time because the ownership dependency did not exist. The implementation now
injects an identity predicate from `BrowserScreen`, consumes stale navigation and
suppresses stale state/native/error/recovery callbacks while preserving platform
fail-closed rejection and Client TLS cleanup.

Verification evidence:

- focused new stale-callback regression, Debug: PASS;
- complete normal/Client-TLS client tests plus existing renderer regression, Debug
  and QA: PASS;
- fresh full Debug JVM: 513 tests, zero failures/errors/skips;
- fresh full QA JVM: 513 tests, zero failures/errors/skips;
- full unit invocation: `BUILD SUCCESSFUL`, 60 actionable tasks, all executed;
- `lintDebug`, `lintQa`: PASS, zero errors / 27 warnings per variant;
  `BUILD SUCCESSFUL`, 55/55 tasks;
- `verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`,
  `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
  `BUILD SUCCESSFUL`, 110/110 tasks;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS; no release APK
  remained;
- final Python discovery after G6-01: 100 tests, zero failures/errors, one
  environmental hardlink skip;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
  generated relay binary removed;
- complete-diff exact-scope, whitespace, sensitive-content and unsafe WebView/TLS/
  backup scans: PASS.

APK SHA-256:

- Debug: `ee01227e286ab371a24d326a1a414f822e7e975b80892c6e2266ba866aaf3365`;
- QA: `d4eb3e09b4430e3a6a0007064577943195a1d8c9bfa02335aa33ab0ec9820dae`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Physical
AEAT F-03 and Go race remain external gates.

## Autonomous G6-02 — browser data-clear completion lease — 2026-08-04

A delayed `clearAllConfirmed()` completion could execute after profile disposal,
update the replacement profile's UI state and reload the obsolete profile URL on the
new active WebView. The regression was pinned before production mutation by a source-
policy RED and a behavioral compile-time RED.

The implementation adds an atomic one-shot completion lease. Each confirmed request
is bound to its initiating WebView, later requests supersede earlier ones, disposal
invalidates outstanding ownership, and reload requires exact identity with the active
`webViewRef`. The already-started global deletion semantics are unchanged.

Verification evidence:

- source-policy RED: `job_20260804_181217_616624bb`, expected failure, 30/30 tasks;
- behavioral RED: `job_20260804_181902_691aaf9e`, expected missing-type compile
  failure, 28/28 tasks;
- minimum GREEN: `job_20260804_182201_7546f3be`, PASS, 30/30 tasks;
- focused Debug+QA lease/browser/security/cleaner gate:
  `job_20260804_182900_2034c491`, PASS, 60/60 tasks;
- full Android: `job_20260804_184100_327d7ebe`, pins PASS, Debug 517/517 and QA
  517/517 with zero failures/errors/skips, three assemblies PASS, 127/127 tasks;
- `lintDebug`, `lintQa`: PASS, 0 errors / 27 warnings per variant, 55/55 tasks;
- Python: 100 tests, zero failures/errors, one environmental hardlink skip;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS; no release APK;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
  generated relay binary removed;
- complete-diff whitespace, exact-scope, sensitive-content, personal-data and unsafe
  WebView/TLS/backup scans: PASS. The first scan wrapper had an operator shell-quoting
  error before content scanning and was replaced by a successful simplified rerun.

APK SHA-256:

- Debug: `e02c14c9383b480a7ca9792136737e0e1b71932ae7b8bd517459d76eab43702f`;
- QA: `e14387a60d88127762ba552d7b34dcd39384cc6f36757da21dae0488d13c2742`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Physical
AEAT F-03 and Go race remain external gates.

## Autonomous G7-01 — WebMessageBridge compatibility-error ownership — 2026-08-04

A WebMessageBridge listener/document-start attachment failure was posted through its
initiating WebView but could set `compatibilityError` after that WebView had been
released and replaced. A source-policy regression was added before production mutation
to require exact active-WebView ownership inside the attachment-failure runnable.

The minimum implementation keeps the existing WebView-posted delivery and adds
`webViewRef.get() === webView` immediately before compatibility-state mutation. It
does not alter bridge attachment or any trust policy.

Verification evidence:

- RED: `job_20260804_192114_5aab8616`, expected assertion failure at
  `BrowserSecurityRegressionTest.kt:280`, 30/30 tasks;
- focused GREEN: `job_20260804_192637_31b56bc1`, PASS, 30/30 tasks;
- focused Debug+QA BrowserSecurityRegression/BrowserScreen:
  `job_20260804_193202_5d9e8290`, PASS, 60/60 tasks;
- full Android: `job_20260804_193946_0b04588e`, pins PASS, Debug 518/518 and QA
  518/518 with zero failures/errors/skips, three assemblies PASS, 127/127 tasks;
- lint: `job_20260804_195110_a0e5e68a`, PASS, 0 errors / 27 warnings per variant,
  55/55 tasks;
- Python: 100 tests, zero failures/errors, one environmental hardlink skip;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS; no release APK;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
  generated relay binary removed;
- complete-diff whitespace, exact-scope, sensitive-content, personal-data and unsafe
  WebView/TLS/backup scans: PASS.

APK SHA-256:

- Debug: `6c97ea151ffe4bfc8c1a0b53ac6657f03760a880d78e62dbec2284da72f7edc2`;
- QA: `875b38927595c7f4b153d79f33e09395825ffeee38c1829e2d0333bcc85c233a`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK installation/launch, device control, portal request, credential/certificate
use, real signing, upload, payment or administrative submission occurred. Physical
AEAT F-03 and Go race remain external gates.

## Autonomous G7-02 — certificate unlock invalidation race — 2026-08-05

Two independent deterministic regressions established the race before production
mutation. `CertificateUnlockCacheTest.clearDuringBlockingWriteCannotResurrectUnlockRecord`
failed in `job_20260804_203419_2ce1ec18` because an in-flight physical write completed
after `clear()`, returned success and recreated the persisted record.
`CertificateViewModelTest.sessionUnlockIsNotPublishedBeforeCacheCommitCompletes` was
freshly reconfirmed RED in `job_20260804_215510_e95834c9`; its XML reported
`expected null, but was <UnlockedIdentity>` while the cache store was still suspended.

The cache now captures an atomic invalidation generation before IO, advances the
generation at the start of every clear, and rejects/clears a successful late write if
the generation changed. The ViewModel now awaits cache persistence and checks
cancellation before publishing `CertificateSession`; session unlock and the matching
`Unlocked` UI update have no suspension between them.

Verification evidence:

- exact dual-regression GREEN: `job_20260804_220006_94a6661d`, `BUILD SUCCESSFUL`,
  30/30 tasks executed;
- relevant cache/ViewModel/session Debug+QA gate:
  `job_20260804_220546_1789baad`, `BUILD SUCCESSFUL`, 60/60 tasks executed;
- full Android: `job_20260804_221205_041a15fa`, pin checks PASS, Debug 520/520 and
  QA 520/520 with zero failures/errors/skips, Debug/QA/QA-AndroidTest assembly PASS,
  `BUILD SUCCESSFUL`, 127/127 tasks executed;
- forced `lintDebug`/`lintQa`: `job_20260805_103210_50f11051`, `BUILD SUCCESSFUL`,
  55/55 tasks, 0 errors / 27 warnings per variant;
- Python discovery: 100 tests, zero failures/errors, one environmental hardlink skip;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS; release APK count
  zero;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS;
  generated relay binary removed;
- pre-evidence `git diff --check`, exact-scope, high-confidence secret and unsafe
  WebView/TLS/backup scans: PASS.

APK SHA-256:

- Debug: `b2d414f4a74eb3f42dbf4cb6c63a4403e82a3e199b5b4fcd2d3c111a62345547`;
- QA: `833081836caf0feb5060f9daee90ce4a0ee00646fb136006c8181aba1d1a376e`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The threat-model semantics did not change: explicit manual lock/session clear was
already specified to eliminate the persisted record. This milestone makes that
existing contract hold under an in-flight blocking store. No APK installation,
application launch, device control, portal request, credential/certificate use, real
signing, upload, payment or administrative submission occurred. Go race remains an
external supported-Linux CI gate and AEAT F-03 remains manual.

## Autonomous G8-01 — cancelled certificate-selection URI permission — 2026-08-05

`CertificateRepository.select()` obtains a persistable SAF read permission before writing its
`StoredCertificateReference`. A new deterministic store test suspended `write()` before any
reference mutation, cancelled the selection, and required the newly acquired URI permission to
be released. RED job `job_20260805_105940_21cd09b2` failed exactly at that assertion: expected
the cancelled URI in `released`, but the list was empty; tests=1, failures=1, errors=0.

The minimum production repair adds the existing `releaseQuietly(uri)` rollback to the
`CancellationException` branch when the URI differs from the previously persisted URI, then
rethrows the same cancellation. It does not convert cancellation into a storage error and does
not release a same-URI permission that was already owned by the previous reference.

Verification evidence:

- exact GREEN: `job_20260805_110609_9d68b59d`, `BUILD SUCCESSFUL`, 30/30 tasks;
- complete `CertificateRepositoryTest` Debug+QA: `job_20260805_111233_b8e16ac4`, exit 0;
- full Android: `job_20260805_112149_c6983e7c`, pins PASS, Debug 521/521 and QA 521/521 with
  zero failures/errors/skips, Debug/QA/QA-AndroidTest assembly PASS;
- forced lint: `job_20260805_113412_665bf0a2`, `BUILD SUCCESSFUL`, 55/55 tasks,
  0 errors / 27 warnings per variant;
- Python: 100 tests, zero failures/errors, one environmental hardlink skip;
- Android artifact verification: PASS;
- release without private signing inputs: expected fail-closed PASS; release APK count zero;
- Go `test ./... -count=1`, `go vet ./...`, `go build ./cmd/ws024-relay`: PASS; relay binary
  removed;
- pre-evidence exact-scope, `git diff --check`, whitespace, high-confidence secret,
  personal-data and unsafe WebView/TLS/backup scans: PASS.

APK SHA-256:

- Debug: `6ceca12ed1254d6627c89406875bb57669c2ac64ae8b4852b4352cda7ed673d7`;
- QA: `0e4789a79f4d0d4849825605f768dc677a1e7d844bdce449d6e952ed5d2b9096`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK installation/launch, device control, portal request, credential/certificate material use,
real signing, upload, payment or administrative submission occurred. The threat-model wording
is unchanged because this milestone tightens cleanup within the existing certificate-document
permission/reference lifecycle rather than introducing a new asset or trust boundary.

## Autonomous G8-02 — cancelled unlock stale reference-summary write — 2026-08-05

A deterministic repository regression blocked a valid synthetic PKCS#12 stream after loading had
started, cancelled the unlock, then released the blocking read. Its reference store records
`write()` immediately without suspension. On unchanged production, RED job
`job_20260805_115511_14d81020` failed because a stale reference-summary write was observed after
cancellation (tests=1, failures=1, errors=0).

The minimum repair adds `currentCoroutineContext().ensureActive()` after blocking certificate
loading returns and before the successful-result summary persistence branch. This guarantees that
an already-cancelled old unlock cannot initiate that subsequent write while preserving the
original cancellation and all non-cancelled behavior.

Verification evidence:

- exact GREEN: `job_20260805_120103_a7e93b2b`, PASS;
- complete `CertificateRepositoryTest` Debug+QA: `job_20260805_120546_d5ea1fd2`, PASS;
- full Android: `job_20260805_121301_ef67a622`, pin checks PASS, Debug 522/522 and QA 522/522
  with zero failures/errors/skips, Debug/QA/QA-AndroidTest assembly PASS, 127/127 tasks;
- forced lint: `job_20260805_123048_ba6c0459`, `BUILD SUCCESSFUL`, 55/55 tasks,
  0 errors / 27 warnings per variant;
- Python: `job_20260805_123629_7317943c`, 100 tests, zero failures/errors, one environmental
  hardlink skip;
- Android artifacts: `job_20260805_123802_8de46e94`, PASS;
- release without private signing inputs: `job_20260805_124233_27c083ee`, expected fail-closed
  PASS; release APK count zero;
- Go test/vet/build: `job_20260805_123929_9870b5cf`, PASS; generated relay binary removed;
- exact-scope, `git diff --check`, high-confidence secret, personal/certificate-literal and unsafe
  WebView/TLS/backup added-line scans: PASS.

APK SHA-256:

- Debug: `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`;
- QA: `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

No APK installation/launch, device control, portal request, credential/certificate material use,
real signing, upload, payment or administrative submission occurred. Threat-model wording is
unchanged because the remediation closes a cancellation ordering gap inside the existing selected
certificate-reference lifecycle rather than creating or broadening a trust boundary.

## Autonomous G9-01 — autonomous branch CI push coverage — 2026-08-05

The repository contract requires each completed autonomous milestone to be pushed to the
`agent/workspace-47-autonomous-20260803` branch. Before this milestone, both GitHub Actions
workflows accepted pushes only from `main` and `feature/**`; therefore an autonomous branch push
could exist remotely without triggering either the ordinary CI workflow or the security workflow.

A policy regression was added first and run against unchanged workflows. RED job
`job_20260805_132116_bf00a316` failed exactly because `.github/workflows/ci.yml` did not contain
the `agent/**` push branch entry. The minimum workflow change adds `agent/**` to the existing
explicit branch list in both workflows. The regression now requires `main`, `feature/**` and
`agent/**` in each file.

Verification evidence:

- exact GREEN plus complete CI policy and Python discovery:
  `job_20260805_132135_fe5674af`, policy 19/19 and Python 101 tests, zero failures/errors, one
  environmental hardlink skip;
- full Android: `job_20260805_132209_da78308f`, pin/AAPT2 checks PASS, Debug 522/522 and QA
  522/522 with zero failures/errors/skips, Debug/QA/QA-AndroidTest assemblies PASS, 127/127 tasks;
- forced lint: `job_20260805_133103_ecb4c60e`, 55/55 tasks, zero errors and 27 warnings per
  variant;
- Go: `job_20260805_132223_437dc850`, `go test ./... -count=1`, `go vet ./...` and relay build
  PASS; generated binary removed;
- Android artifacts: `job_20260805_133111_c4ee32c9`, alignment/signature/manifest/canary checks
  PASS;
- release without private signing inputs: `job_20260805_133814_d06bfb4e`, expected fail-closed
  PASS, exit 0, release APK count zero;
- pre-evidence scope/security policy scan: `job_20260805_132521_00581e82`, exact five-file scope,
  no high-confidence secret or personal identifier, no write-all permission,
  `pull_request_target` or enabled checkout credential persistence, and all action uses still
  pinned to the existing 40-character SHAs.

APK SHA-256:

- Debug: `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`;
- QA: `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The workflow change does not modify permissions, jobs, schedules, commands, action pins,
dependencies, credentials or release policy. Local testing validates policy syntax and the gate
commands; an actual GitHub-hosted run is not claimed unless separately observed after the branch
push. No APK installation/launch, device control, portal request, credential/certificate use,
real signing, upload, payment or administrative submission occurred. Physical AEAT F-03 and Go
race on supported Linux remain external gates.
## Autonomous G10-01 — browser notice assertive live region — 2026-08-05

`BrowserNoticeBanner` can appear dynamically when browser or portal loading fails. Before this
milestone, the tagged banner exposed visible descendants but no `SemanticsProperties.LiveRegion`,
so the UI carried no Compose instruction to announce the newly appearing error while focus remained
elsewhere.

TDD evidence:

- RED `job_20260805_191209_05c712cb`: one focused Debug test failed after 30/30 executed tasks; the
  XML assertion explicitly showed the `browser_notice` node lacked expected
  `LiveRegion = 'Assertive'`;
- minimum implementation: one `LiveRegionMode.Assertive` semantics property on the existing banner
  `Surface`, with no focus request, copy, layout or action change;
- exact GREEN `job_20260805_191610_b601f0fe`: PASS, 30/30 tasks;
- focused Debug+QA `job_20260805_191930_64b43357`: two tests per variant, zero
  failures/errors/skips, 60/60 tasks.

Full verification:

- Android `job_20260805_192433_0a9882e0`: pins/AAPT2 PASS; Debug 523/523 and QA 523/523 with zero
  failures/errors/skips; Debug/QA/QA-AndroidTest assemblies PASS; 127/127 tasks;
- lint `job_20260805_193250_fbcb35e0`: 55/55 tasks, zero errors and 27 warnings per variant;
- Python `job_20260805_192440_7b0b9c8e`: 101 tests PASS, one environmental hardlink skip;
- Go `job_20260805_192506_91c193e3`: test/vet/build PASS; generated relay binary removed;
- artifacts `job_20260805_193317_e2ccbe58`: alignment/signature/manifest/canary checks PASS;
- release `job_20260805_193923_b5015fe3`: expected private-signing rejection PASS and release APK
  count zero.

APK SHA-256:

- Debug: `340114fc16b6603bb972d9f409fa4f0d3b4aa1a0eeb8ec0a177ffbea530788f9`;
- QA: `d951d33a6f616242348a16a3ff3ae9017165a480253cffd8848a8e4bd4cc8061`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

Automated tests validate the semantics tree; they do not prove physical TalkBack timing, speech
interruption behavior or real-device visual correctness. Those remain manual acceptance gates. No
APK installation/launch, device control, portal request, credential/certificate use, real signing,
upload, payment or administrative submission occurred. Threat-model wording is unchanged because
this accessibility change creates no application trust boundary.

## Autonomous G11-01 — WebMessage bridge release ownership — 2026-08-05

The normal WebView bridge attachment previously had no exact WebView owner at the
`AndroidView.onRelease` boundary. The released WebView was destroyed without invoking
`WebMessageBridgeAttachment.close()`, so temporary AndroidView removal/recreation could
retain its listener, document-start script and pending MiniApplet replies and then
replace the only raw attachment reference.

TDD evidence:

- lease RED `job_20260805_195612_9b1c5899`: expected test-compilation failure on the
  absent `BrowserOwnedResourceLease`, 28/28 tasks;
- integration RED read `job_20260805_201142_349e55bb`: Browser security suite 15 tests,
  one failure, zero errors/skips, exact missing exact-owner lease assertion;
- minimum implementation: atomic owner/resource lease plus exact bind/release/current/
  close wiring in `BrowserScreen`; `onRelease` releases before WebView destruction;
- focused Debug GREEN `job_20260805_202100_6c1c3977`: 16/16 selected tests, 30/30
  tasks;
- focused Debug+QA `job_20260805_202539_f1cc3955`: 16/16 selected tests per variant,
  60/60 tasks.

Full verification:

- Android `job_20260805_203513_76ad0a12`: resolved-core, portable-AAPT2 and runtime
  dependency-lock checks PASS; Debug 525/525 and QA 525/525, zero failures/errors/
  skips; Debug/QA/QA-AndroidTest assemblies PASS; 128/128 tasks;
- lint `job_20260805_204136_e5c97e7b`: 55/55 tasks, zero errors and 27 warnings per
  variant;
- Python/Go `job_20260805_202651_47c08720`: Python 101 tests PASS with one
  environmental hardlink skip; Go test/vet/build PASS;
- artifacts `job_20260805_203536_e7dd25ed`: alignment/signature/manifest/canary
  checks PASS;
- release `job_20260805_203636_635bafd7`: expected private-signing rejection PASS;
  release APK count zero;
- cleanup/state `job_20260805_204226_28765586`: generated relay binary removed and
  release APK count confirmed zero;
- exact-scope, `git diff --check`, sensitive-content and unsafe WebView/TLS scans:
  `job_20260805_204243_51a14b98`, PASS.

APK SHA-256:

- Debug: `6bf8e4722fe865b1137a7a4498bc824b83e4413ca9b9dd4c8c8e64414703e195`;
- QA: `3a263176016595ec449bbaab3ee352c7a674bf79c48f5d9f0e954efa06aa8f37`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

Automated evidence validates owner ordering and source integration, not physical
WebView/device behavior. No APK installation/launch, device control, portal request,
credential/certificate use, real signing, upload, payment or administrative submission
occurred. Physical AEAT F-03, physical TalkBack/visual validation and Go race on
supported Linux remain external gates. Threat-model wording is unchanged.

## Autonomous G12-01 — stale WebView network-diagnostic ownership — 2026-08-05

A stale/released `JuntaWebViewClient` could still append sanitized main-frame request
metadata because `shouldInterceptRequest()` did not use the exact active-WebView
predicate already protecting its navigation/lifecycle/UI callbacks. Network handling
was not intercepted or changed; the defect was diagnostic provenance/lifetime.

TDD evidence:

- RED `job_20260805_205546_7e6ca54a`: 30/30 tasks executed and the single new test
  failed; XML read `job_20260805_205837_885abec9` showed one test, one failure, zero
  errors/skips and the stale `NETWORK_REQUEST` record;
- minimum implementation: one `if (!isCurrentWebView(view)) return null` guard before
  the existing logging block;
- exact Debug GREEN `job_20260805_205906_3890a3e5`: 30/30 tasks PASS;
- focused Debug+QA `job_20260805_210208_77f0117c`: complete
  `JuntaWebViewClientTest` 18/18 per variant, 60/60 tasks PASS.

Full verification:

- Android `job_20260805_210652_14457a72`: resolved-core, portable-AAPT2 and runtime
  dependency-lock checks PASS; Debug/QA/QA-AndroidTest assemblies PASS; 128/128 tasks;
- aggregate XML `job_20260805_211450_91c4e83d`: Debug 526/526 and QA 526/526 JVM
  tests, zero failures/errors/skips;
- lint `job_20260805_211457_1604b9df`: 55/55 tasks PASS;
  `job_20260805_212114_570c9a57`: zero errors and unchanged 27 warnings per variant;
- Python/Go `job_20260805_210659_0143787d`: Python 101 tests PASS with one
  environmental hardlink skip; Go test/vet/build PASS;
- artifacts `job_20260805_211505_15af8337`: alignment/signature/manifest/canary
  checks PASS;
- release `job_20260805_212127_371468b7`: expected private-signing rejection PASS;
- cleanup `job_20260805_212329_ecd65e0a`: relay binary absent and release APK count
  zero. The preceding whitelist assertion `job_20260805_212254_962eeacb` failed before
  mutation only because the known `go build` output had not been listed; diagnostic
  `job_20260805_212313_afba868a` confirmed the untracked ARM64 ELF origin;
- exact-scope, whitespace, sensitive-material and unsafe WebView/TLS scans:
  `job_20260805_212358_bb60a813`, PASS.

APK SHA-256:

- Debug: `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`;
- QA: `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The regression proves stale diagnostic suppression and the existing positive control
proves active logging remains enabled. It does not constitute physical WebView/device
validation. No APK installation/launch, device control, portal request, credential or
certificate use, real signing, upload, payment or administrative submission occurred.
Physical AEAT F-03, physical TalkBack/visual behavior and supported-Linux Go race remain
external gates. Threat-model wording is unchanged.

## Autonomous G12-02 — Python Dependabot update monitoring — 2026-08-05

The security workflow already scans `tools/requirements.txt` with OSV, but Dependabot
version-update configuration covered only Gradle, Go modules and GitHub Actions.
Current GitHub Dependabot documentation supports `pip`/`requirements.txt`; the Python
manifest therefore lacked the update-discovery control used by the other explicit
package ecosystems.

TDD evidence:

- RED `job_20260805_213934_d8a7096a`: one policy test, one failure, exactly `0 != 1`
  for the required `package-ecosystem: "pip"`; Dependabot config unchanged at RED;
- minimum fix: one `pip` block at `/tools`, weekly Monday, PR limit 5; no version
  update and no existing ecosystem block changed;
- GREEN `job_20260805_213957_df23d7c9`: exact regression PASS and complete
  `tools.tests.test_ci_policy` 19/19 PASS.

Fresh full verification:

- Android `job_20260805_214008_05cba7fd`: resolved-core, portable AAPT2, runtime
  dependency locks, Debug/QA/QA-AndroidTest assemblies, 128/128 tasks PASS;
- XML/hash read `job_20260805_214720_ad44529f`: Debug 526/526 and QA 526/526, zero
  failures/errors/skips; requirements worktree/hash equals HEAD;
- Python/Go `job_20260805_214014_3f1f0af3`: Python 101 PASS with one environmental
  hardlink skip; Go test/vet/build PASS;
- lint `job_20260805_214728_d0d3ec61`: 55/55 tasks PASS;
  `job_20260805_215335_c01cf437`: zero errors / 27 warnings per variant;
- artifacts `job_20260805_214736_54d890ad`: PASS;
- release `job_20260805_215344_817e8e2b`: expected private-signing rejection PASS;
- cleanup `job_20260805_215505_a917c8de`: relay binary absent, release APK count zero;
- exact pre-evidence scope/YAML/whitespace/sensitive/dependency/workflow policy scan
  `job_20260805_215529_fe814d2d`: PASS.

APK SHA-256 (unchanged from G12-01):

- Debug: `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`;
- QA: `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`;
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.

The automated evidence proves repository configuration and policy shape only. It does
not prove that GitHub Dependabot executed or opened a PR from this autonomous branch.
No dependency was upgraded. No APK/device/portal/credential/certificate/real-signing/
upload/payment/submission action occurred. Threat-model wording is unchanged.

## G13-02 — browser notice live-region severity — 2026-08-06

`BrowserNoticeBanner` previously used an assertive live region for every browser
notice, including non-error Client TLS preparation and successful browser-data clear
status. The regression tests were added before production mutation. RED
`job_20260805_222337_0803500c` failed exactly on the absent explicit live-region API
and absent state policy helper. The minimum change keeps the banner default
assertive, assigns `Polite` only to `CLEARING` and exact site/global clear success,
and keeps failures, warnings, navigation blocks and browser/compatibility errors
assertive.

Focused GREEN `job_20260805_222533_f589b871` passed both variants; XML
`job_20260805_222740_d7eee693`: 11 tests per variant, zero failures/errors/skips.
Fresh final split gates after connector diagnostics:

- dependency/toolchain: `job_20260805_224216_debaec44`, 3/3 tasks PASS;
- full JVM: `job_20260805_224421_3aee3897` PASS; XML
  `job_20260805_224616_c849f08f`: Debug 528/528 and QA 528/528, zero
  failures/errors/skips;
- lint/build: `job_20260805_224514_53d85d71`, 124 tasks PASS; lint is 0 errors / 27
  warnings for both Debug and QA; Debug, QA and QA AndroidTest assemblies PASS;
- Python/Go: `job_20260805_224626_7301ac9b`, Python 101 PASS with one environmental
  hardlink skip; Go test/vet/build PASS;
- Android artifacts: `job_20260805_224646_40ff453a` PASS;
- release without private signing inputs: `job_20260805_224658_b2416ba2` PASS
  fail-closed; zero release APK retained;
- generated relay executable removed by `job_20260805_224803_03dcec46` after
  `job_20260805_224754_055676e0` identified it as the expected ARM64 Go build output.

Two earlier monolithic Android-gate requests lost the connector response with HTTP
502 while Gradle wrappers continued. Their unknown request-level result is not used
as verification evidence; the observed split commands above supply the pass evidence.
No APK was installed or launched and no device, portal, credential, certificate,
real-signing, upload, payment or submission action occurred.

APK SHA-256:

- Debug: `cd499662a3fafc00f5b9370b5deaf604393611b0071b36487e47fba7aa13c2ae`
- QA: `c9732852c88117ab09b49f786bf2adc8f03c2144174534a7ee100ec6c84be098`
- QA AndroidTest: `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`

Automated evidence covers Compose semantics, not physical TalkBack announcement
timing/interruption or real-device visual correctness; those remain manual gates.


## G14-02 — Client TLS issuer-filter hardening — 2026-08-06

- RED `job_20260805_230810_b70c1333` / XML confirmation
  `job_20260805_231028_8c556d8a`: Debug and QA each ran 7
  `ClientAuthRequestHandlerTest` cases with exactly one expected failure,
  `aeatLeafSubjectIsNotAcceptedAsAnIssuer` (`expected:<0> but was:<1>`); production
  source was unchanged.
- Focused GREEN `job_20260805_231054_95780151`: 7/7 Debug and 7/7 QA. Adjacent
  Client TLS/browser/profile regression `job_20260805_231253_bce0565d`: 55/55 per
  variant, zero failures/errors/skips.
- Runtime dependency lock, core-version and portable AAPT2 verification
  `job_20260805_231523_b4599725`: PASS.
- Fresh full JVM `--rerun-tasks` `job_20260805_232254_97d24413`: all 60 tasks
  executed; Debug 529/529 and QA 529/529, zero failures/errors/skips. Earlier
  `job_20260805_232044_edd244d7` is excluded from pass evidence because overlapping
  connector retries produced broad Gradle test-executor class-execution failures; the
  isolated rerun above is the replacement evidence.
- Lint `job_20260805_233550_b6edf4ed`: PASS, 0 errors / 27 warnings for Debug and
  QA. Duplicate connector-retry lint jobs `job_20260805_232800_8d0a4b2b` and
  `job_20260805_233008_31f68385` reached their 300-second infrastructure timeout and
  are not counted.
- Build `job_20260805_233933_8242422b`: `assembleDebug`, `assembleQa`,
  `assembleQaAndroidTest` PASS. APK SHA-256: Debug
  `a31bb8cdfdb05af38a26c3ec32bddf5415e6991d00453553e61f54bb01f32fa9`; QA
  `53dd0a15d69fc59a0fa70dde0032005ddf2f6425c9758d745e31d60b8e71f6e9`; QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Python/Go `job_20260805_234055_b7910c71`: Python 101 tests PASS with one
  environmental hardlink skip; `go test ./... -count=1`, `go vet ./...`, and relay
  build PASS. Generated relay executable removed afterwards.
- Android artifact/release `job_20260805_234127_1ff5af35`: artifact checks PASS;
  release without private signing inputs rejected fail-closed as required; release APK
  count zero.
- No APK was installed or launched and no device-control, authenticated portal,
  credential/certificate-private-material, real-signature, upload, payment or
  administrative-submission action occurred.


## G14-03 — persisted unlock stale-restore invalidation — 2026-08-06

- RED `job_20260805_235400_7d3f1417`: exact stale-restore test ran once in Debug and
  once in QA; both failed as intended with `expected null, but was
  CachedCertificateUnlock`. Production source was unchanged at RED.
- Focused GREEN `job_20260805_235700_940d2cca`: `CertificateUnlockCacheTest` 9/9
  Debug and 9/9 QA, zero failures/errors/skips.
- Adjacent regression/dependency `job_20260805_235934_6f90c772`: cache, session,
  PKCS#12 loader and CertificateViewModel suites 45/45 per variant; runtime dependency
  locks, resolved-core version and portable AAPT2 gates PASS.
- Fresh full JVM `job_20260806_000346_09dda276`: all 60 tasks executed; Debug 530/530
  and QA 530/530, zero failures/errors/skips. The known internal
  `ProfileHttpCallPhaseTracker.connectStart` parameter-name warning remains; no new
  compile/test warning was introduced by this milestone.
- Lint/build `job_20260806_000750_feb3c236`: lint PASS at 0 errors / 27 warnings per
  variant; `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` PASS. APK SHA-256:
  Debug `b771e02dacc454a0f83c0e6049d73de09e0a231dd318a48469b5a2a8545e7daf`; QA
  `c717d9c212566c372331a68365c9b75006af92f2f3f503c37d3a66651896e660`; QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Python/Go/artifact/release `job_20260806_001426_21a0f58a`: Python 101 PASS with one
  environmental hardlink skip; Go test/vet/build PASS; Android artifact verification
  PASS; release without private signing inputs rejected fail-closed; release APK count
  zero. Generated relay executable removed by `job_20260806_001604_23dbe0ac`.
- No APK installation/launch, device control, authenticated portal interaction, private
  certificate/credential use, real signature, upload, payment or administrative submission
  occurred.

## G14-04 — complete Android backup/D2D domain exclusion — 2026-08-06

- RED `job_20260806_002638_74b167aa`: the new parser-based policy test failed exactly
  because `backup_rules.xml` contained only `root` and lacked `file`, `database`,
  `sharedpref`, `external`, `device_root`, `device_file`, `device_database` and
  `device_sharedpref`; both production XML resources were unchanged at RED.
- Focused GREEN `job_20260806_002711_d709b2b3`: exact regression PASS and complete
  `CiPolicyTest` 20/20 PASS.
- Dependency/toolchain + fresh full JVM `job_20260806_002722_41fae726`: runtime locks,
  resolved-core and portable-AAPT2 PASS; all 60 JVM tasks executed; Debug 530/530 and QA
  530/530, zero failures/errors/skips.
- Lint/build `job_20260806_003314_e0f0f679`: lint 0 errors / 27 warnings per variant;
  `assembleDebug`, `assembleQa`, `assembleQaAndroidTest` PASS. APK SHA-256: Debug
  `2885b12708dd3e25beebf04fed55a76c945d09fc59ceca78821312b3c86ef40a`, QA
  `f1252cbaefcb16063e1006558c4b03c630fe4b401f9a7a9723b52444d92a0842`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Python/Go/artifact/release `job_20260806_003806_d8526dbe`: Python 101 PASS with one
  environmental hardlink skip; Go test/vet/build PASS; Android artifact verification
  PASS; release without private signing inputs rejected fail-closed; release APK count
  zero. Generated relay SHA-256
  `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed
  before final staging.
- This is backup/transfer policy evidence only. No APK installation/launch, device
  control, portal interaction, credential/private-certificate use, real signing, upload,
  payment or administrative submission occurred. Physical AEAT F-03, real-device
  TalkBack/visual validation and supported-Linux Go race remain external gates.

## G15-01 — Client TLS monotonic grant TTL — 2026-08-06

- RED `job_20260806_180534_6ae3e8d9`: civil-clock rollback regressions failed as intended in the Client TLS authorizer and request handler before production change.
- Focused/dependency `job_20260806_194539_847585e9`: runtime locks, resolved-core, portable-AAPT2 and Debug/QA authorizer/request-handler/WebView-client suites PASS.
- Full JVM `job_20260806_195244_56763d36`: 60 tasks executed; Debug 532/532 and QA 532/532, zero failures/errors/skips. Only previously known compile/deprecation warnings observed.
- Build `job_20260806_194307_a1adf27b`: Debug, QA, QA AndroidTest PASS. Lint `job_20260806_200521_58fbcc2f`: 0 errors / 27 warnings for Debug and QA.
- Python/Go `job_20260806_201618_656cbb46`: Python 102 PASS with one environmental hardlink skip; Go test/vet/build PASS. The wrapper then exited 126 solely because Termux lacks `/usr/bin/env` for unchanged CI-script shebangs. Explicit Termux-bash artifact verification `job_20260806_201734_39e57c20` PASS; release-signing fail-closed itself passed in `job_20260806_201817_7ccfec9b`; `job_20260806_202036_f8686d7a` confirmed zero release APKs.
- APK SHA-256: Debug `cb361265a636712ed584d6235ee0a877b7268b486074558874d32cb6dc841dc4`; QA `f97b5f4ac075ab70729b77d365b67d7698c53ef0cf66be176196078f6577f5fb`; QA AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`. Generated relay SHA-256 `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6` was removed.
- Local `govulncheck`, `osv-scanner` and `gitleaks` executables were unavailable; pinned scanner/workflow policy is covered by passing CI-policy tests, but no local vulnerability-scan execution is claimed.
- No APK launch/install, device control, authenticated portal use, credentials/private certificate material, real signature, upload, payment or submission occurred. Physical AEAT F-03, real-device TalkBack/visual and supported-Linux Go race remain external gates.

## G17-01 — browser identity button-role semantics — 2026-08-06

- Initial RED `job_20260806_204953_52915fe0`: 5 component tests, one expected role
  failure; the optional interactive node measured 69 px, so no size/layout fix was
  justified. Narrowed RED `job_20260806_205704_31ea5f2a` / parser
  `job_20260806_210524_6ad34925` failed only on missing `Role.Button` while `OnClick`
  remained present.
- Minimum implementation adds `role = Role.Button` only to the non-null
  `onIdentityClick` branch; passive/default identity remains role-free and non-clickable.
- Focused GREEN `job_20260806_210559_522f6433`: 5/5 Debug and 5/5 QA. Full
  dependency/toolchain/JVM `job_20260806_210837_45835074`: 63 tasks; XML
  `job_20260806_211559_f3df9f5b`: 534/534 per variant, zero failures/errors/skips.
- Lint/build `job_20260806_211608_f9af0293`: 124 tasks PASS; parsed lint
  `job_20260806_212122_622207d7`: 0 errors / 27 warnings per variant. APK SHA-256:
  Debug `16a334f13900d06559dbf56e8736976255712e2d5345cbc2fc6b2bff800d309a`; QA
  `e96e72c3902e4d6c9d8d7eeb04aacf48c760d2fd65a4b799ea58621ba1192230`; QA
  AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
- Python/Go/artifact/release `job_20260806_212150_46b3db2d`: Python 102 PASS with one
  environmental hardlink skip; Go test/vet/build, Android artifact verification and
  release fail-closed PASS. Final focused/CiPolicy `job_20260806_212752_eee24aa7` PASS.
- Post-commit call-site audit `job_20260806_213403_11ab2535` plus history/blame
  `job_20260806_213437_188b61c4` confirmed the exact limitation: production
  `BrowserLayout` never supplies `onIdentityClick`, sets `editingContent = null`, and
  already tests that the toolbar identity cannot open a manual URL editor. Thus G17-01
  is dormant internal API semantics hardening, not a current runtime accessibility
  change. Commit `1e6b7a611635476185ca819d7d2641580a3d5c91` is remotely verified.
- No APK/device/portal/credential/private-certificate/real-signing/upload/payment/
  submission action occurred.
## G18-01 — dormant manual-URL surface removal — 2026-08-06

- Symbol audit `job_20260806_214058_9ef9999b` found the live browser read-only but
  main-source still contained an unused `BrowserAddressBar` (`BasicTextField` plus
  arbitrary `onSubmit`) and unused `onIdentityClick` / `editingContent` slots.
- RED `job_20260806_214334_dbb2fc34` / parser `job_20260806_214501_7a424a95`: one
  source-policy test, one expected failure, zero errors/skips, exactly on retained
  dormant manual URL editor; production source unchanged.
- Minimum removal deletes only the dead editor, its editor-only strings/imports/tag and
  dormant slots. `BrowserAddressPresentation.hostOf` and actual read-only browser chrome
  remain. Negative `browser_address_field` UI assertions remain test-local.
- Focused GREEN `job_20260806_214656_e8869677` / `job_20260806_215018_c60f84a6`:
  browser security 16/16, chrome 3/3 and screen 8/8 = 27/27 per variant, zero
  failures/errors/skips; existing no-manual-editor regression is still present.
- Dependency/toolchain/full JVM `job_20260806_215028_a94e7588`: 63/63 tasks;
  `job_20260806_215812_66037d60`: Debug 533/533 and QA 533/533, zero
  failures/errors/skips. Known `ProfileHttpCallPhaseTracker` parameter-name and legacy
  WebView-test deprecation warnings remain.
- Lint/build `job_20260806_215827_fe79aaec`: 124 tasks PASS and three APK assemblies;
  parser `job_20260806_220447_5a1738c0`: 0 errors / 26 warnings per variant. APK
  SHA-256: Debug `39fded02c7dcd0280ace68ec02083615dabb774e79786685e56c3b4912d143c3`,
  QA `b20a394f812b7d7718c0724508a17c7c513b8cd97b183df25e1a6072a7048705`, QA
  AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
- Python/Go/artifact/release `job_20260806_220500_65771d83`: Python 102 PASS with one
  environmental hardlink skip; Go test/vet/build, Android artifact and release
  fail-closed checks PASS. `job_20260806_220857_877aaf8f`: relay absent and release APK
  count zero. Pre-evidence structural/scope/security scan
  `job_20260806_220924_691a63aa` PASS.
- This is structural attack-surface reduction, not evidence of a previously reachable
  arbitrary-navigation exploit. No APK/device/portal/credential/private-certificate/
  real-signing/upload/payment/submission action occurred.

## G19-01 — Afirma main-frame native-delivery boundary — 2026-08-06

- RED `job_20260806_222113_c06f171b` / parser
  `job_20260806_222241_725e79f7`: three Debug regressions with two expected failures;
  valid direct/embedded Afirma subframe routing emitted two `afirma:sign` callbacks and
  the deprecated String callback emitted one, while the modern main-frame positive
  control passed before production mutation.
- Minimum fix gates only `NavigationDecision.HandleAfirma` delivery on
  `isModernMainFrame`. Subframe/legacy frame ambiguity is consumed and reported as
  `UNTRUSTED_AFIRMA_ORIGIN`; modern main-frame direct and embedded-Afirma behavior is
  preserved. No generic navigation, WebMessage, Client TLS, certificate/signing,
  profile/release or dependency behavior changed.
- Focused GREEN `job_20260806_222530_6d61bc8c` / parser
  `job_20260806_222921_23a53387`: 40/40 Debug and 40/40 QA, zero
  failures/errors/skips.
- Dependency/toolchain/full JVM `job_20260806_222931_bb0d28b8`: runtime locks,
  resolved-core and portable-AAPT2 PASS; 63/63 rerun tasks. XML aggregation
  `job_20260806_223615_f97d9406`: Debug 535/535 and QA 535/535, zero
  failures/errors/skips.
- Lint/build `job_20260806_223624_b4579b73`: 124 tasks PASS including
  `lintDebug`, `lintQa`, `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`;
  parser `job_20260806_224214_259200f6`: 0 errors / 26 warnings per variant. APK
  SHA-256: Debug `16589a5492c7b689a7492791d3fe22a71dbb69873b46db27f2305750553fb1e2`, QA
  `f4c8f34765debdfdcb4bfe73712819939996e8ee791b71e8da7ea84679088df8`, QA
  AndroidTest `08ed3f916acb55c5586a52a93dfdb2c2c66c7832b385b9f74a1d7182d9cba449`.
- Python/Go/artifact/release `job_20260806_224224_6882554f`: Python 102 PASS with
  one environmental hardlink skip; Go test/vet/build, Android artifact verification
  and release-signing fail-closed PASS. Pre-evidence exact-scope/diff/sensitive/
  unsafe-pattern scan `job_20260806_224602_80996800` PASS.
- Claim is limited to automated WebView callback frame ownership. No physical portal
  E2E, automatic-signature exploit, APK/device execution, credential/private-certificate
  use, real signing, upload, payment or submission is claimed.

## G20-01 — external-browser main-frame native-delivery boundary — 2026-08-07

- RED `job_20260806_231912_7675aebf`: two Debug regressions, two expected failures,
  zero errors/skips. Direct external HTTPS from subframe plus legacy callback produced
  `[external:example.org, external:example.org]`; validated `intent:` HTTPS browser
  fallback from a subframe produced `[external:example.org]`. Its modern main-frame
  positive control passed before the negative assertion.
- Minimum fix adds typed `UNTRUSTED_EXTERNAL_NAVIGATION` and gates only
  `NavigationDecision.OpenExternal` native delivery on `isModernMainFrame`.
  Non-main/legacy paths are consumed with sanitized diagnostics and no application
  callbacks; modern main-frame direct/fallback handoff remains. No policy decision,
  allowlist, profile/release, Client TLS, WebMessage, Afirma/signing or dependency
  behavior changed.
- Focused GREEN `job_20260806_232243_54481b9e`: 42/42 Debug and 42/42 QA, zero
  failures/errors/skips.
- Runtime-lock/core/AAPT2 + full JVM `job_20260806_232842_a997f44f`: 63/63 tasks;
  Debug 537/537 and QA 537/537, zero failures/errors/skips.
- Lint/build `job_20260806_233635_8c200dbd`: 124/124 tasks; 0 errors / 26 warnings
  per variant; Debug, QA and QA AndroidTest assemblies PASS. APK SHA-256: Debug
  `eac33d40a71eb3a01d4af8be4dc48ef504e2617a72c3fda891f759b59c4b5b8b`, QA
  `8b451a495c43bce6ed3bbc934986c092564f4d784e4d20afc037c84a269579c9`, QA
  AndroidTest `fcb913bd40aca5802141bdfecd5c92701f86e0499eade634e64b6a487fc41664`.
- Python/Go/artifact/release `job_20260806_234613_6d1f3271`: Python 102 PASS with one
  environmental hardlink skip; Go test/vet/build, Android artifact verification and
  release fail-closed PASS; generated relay removed; release APK count zero.
  Pre-evidence scope/whitespace/sensitive/unsafe-pattern review
  `job_20260806_234851_95328bfb` PASS.
- Claim is limited to automated WebView frame ownership before native external-browser
  handoff. No APK/device execution, physical portal E2E, credential/private-certificate
  use, real signing, upload, payment or submission is claimed.
