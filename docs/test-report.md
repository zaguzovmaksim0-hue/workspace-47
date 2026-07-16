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
