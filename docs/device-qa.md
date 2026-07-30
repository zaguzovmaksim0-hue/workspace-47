# Device QA

Fecha: 2026-07-14
Dispositivo: `24069PC21G`
API: 36
Commit bajo prueba: `86d644c76036eecc9cfda8617e11f31770f379d4`

## Transporte

`connectedDebugAndroidTest` no pudo crear el ADB bridge porque
`$ANDROID_HOME/platform-tools/adb` es un ejecutable Linux x86-64 en este host
Termux aarch64. El `adb` nativo de Termux funciona, pero no tenía un dispositivo
conectado. Este fallo se clasifica como ambiental, no como fallo de un test.

Se usó el shell local autorizado de Shizuku (`rish`, uid shell) para ejecutar
el runner Android directamente. No se modificó el SDK global para ocultar el
problema.

## Integridad de APK instalados

Antes del run se copiaron los APK instalados a un rollback local y se comparó
su SHA-256 con la clean build:

- target instalado/build: `5519e6af1a3889b606f64ef9e4db5f8c6da870887d42a22373c809ab28e7c277`;
- test instalado/build: `d08e1ae0a7a4bc4874cde312e393cdb1f7d50f7844d7e516e91811b2feeb41ef`.

Ambos pares eran idénticos, por lo que no fue necesaria una reinstalación.

## Instrumentation

Comando Android-shell:

```text
am instrument -w dev.junta.firmamobile.test/androidx.test.runner.AndroidJUnitRunner
```

Resultado: `OK (21 tests)` en aproximadamente 50 segundos.

Clases ejecutadas:

- `AppLaunchTest`;
- `CertificatePickerIntentTest`;
- `CertificateSetupFlowTest`;
- `SigningConfirmationInstrumentedTest`;
- `VisualReferenceInstrumentedTest`;
- `JuntaWebViewClientInstrumentedTest`;
- `ProtocolProbeInstrumentedTest`;
- `TrustedJuntaWebViewInstrumentedTest`;
- `WebMessageBridgeInstrumentedTest`;
- `JuntaTriPhaseCodecInstrumentedTest`.

El run usa fixtures sintéticas. No se abrió un portal oficial, no se introdujo
un password real, no se firmó un documento y no se obtuvo evidencia E2E.

## Estado

- Deterministic instrumentation: `PASSED`.
- Real Junta E2E: `NOT_RUN`.
- Capturas/logs con identidad o payload: ninguno guardado.

## Actualización 2026-07-29 — Oficina Virtual aceptada en dispositivo físico

Dispositivo: POCO F6 Pro (`24069PC21G`), Android API 36.

Se ejecutó manualmente la autenticación real de
`https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs`. El portal
aceptó el resultado y abrió el área interna de trámites pendientes. La captura
original no se conserva en Git porque contiene identidad del certificado.

Build que produjo la aceptación:

- commit `26230abac82c791901f6c45e6dfb9b02ff62547b`;
- QA APK SHA-256
  `6c14b2d95187b89261973a221d391f0ea469d43149e9a3bf3e1358355ca69779`.

Build posterior con el estado visible `VERIFIED_E2E`:

- commit de promoción `b3f1817c36324394a1816befc172340d6f5cd180`;
- QA APK e installed `base.apk` SHA-256
  `ba82c501c4e1e4d9843dc263648d4b051ea2d9bbbbefd6f7ff451ab197b30e34`;
- instalación `pm install -r`: `Success`;
- `firstInstallTime` preservado: `2026-07-11 20:55:37`;
- direct-only, sin relay tuple ni credencial QA.

Estado actualizado:

- Deterministic unit/lint/build: `PASSED`;
- Real Junta Oficina Virtual authentication E2E: `PASSED`;
- Scope: login CAdES observado únicamente;
- capturas o logs sensibles incorporados al repositorio: ninguno.

## Actualización 2026-07-30 — F-05 secure-window state policy

Se instaló la QA APK SHA-256
`fe303b10658a8fcf3698e00d42e5714e4d7b42ba28208c8beb196da505963199`
mediante `pm install -r`; el `base.apk` instalado coincidió byte-for-byte. El
cache cifrado de desbloqueo permaneció en 101 bytes, modo `600`.

Tras `force-stop` y cold launch, el certificado se restauró sin contraseña. El
window dump de `MainActivity` mostró `fl=... SECURE ...`. Después de abrir el
profile exacto UniZAR mediante el QA smoke hook y confirmar
`VERIFIED_E2E / WEBVIEW_ACTIVE`, el mismo window continuó mostrando `SECURE`.
No se tomó screenshot del certificado ni del WebView autenticado.

El gate no modifica `ProtocolProbeActivity`: la Activity debug aislada continúa
sin protección de lifetime para permitir fixtures sanitizados. La pantalla
inicial `LoadingReference/NoCertificate + Idle` también permanece sin flag; una
vez que existe estado Locked, Unlocking, Unlocked o signing no-idle, la ventana
queda protegida.

## Actualización 2026-07-30 — F-08 profile-scoped WebView data

Se instalaron mediante `pm install -r -t`, sin iniciar `MainActivity`:

- QA APK SHA-256:
  `40f03d634b5053b0b79a217b88edf65ca32a3c57c5b36d0371ab968f9bc558b7`;
- QA AndroidTest APK SHA-256:
  `ceafc6b65c513b1e5e6fb5ed728bd376c7617557144e3c5581088dce782bf68f`.

Los hashes se conservaron al copiar a almacenamiento compartido,
`/data/local/tmp` y el `base.apk` instalado. El cache cifrado de desbloqueo
permaneció en 101 bytes, modo `600`.

Se ejecutó únicamente:

```text
am instrument -w -r -e class dev.junta.firmamobile.browser.WebViewCapabilitiesInstrumentedTest dev.junta.firmamobile.test/androidx.test.runner.AndroidJUnitRunner
```

Resultado: `OK (1 test)`. Observación permitida:

- provider package: `com.google.android.webview`;
- provider version: `150.0.7871.181`;
- `MULTI_PROFILE=true`;
- `GET_COOKIE_INFO=true`;
- `WEB_MESSAGE_LISTENER=true`;
- `DOCUMENT_START_SCRIPT=true`.

`topResumedActivity` antes y después siguió siendo ChatGPT; el proceso target no
quedó activo. La app no llamó `WebViewCompat.setProfile`: el milestone mide la
capacidad, pero mantiene un único perfil físico de WebView. No se registraron
URLs, nombres/valores de cookies, certificado ni contenido de portal.

## Actualización 2026-07-30 — F-17 IPv6 classifier

La incidencia ambiental inicial quedó superada durante la instalación F-13. En
el dispositivo físico se ejecutó únicamente
`PublicIpAddressPolicyInstrumentedTest`: `OK (1 test)`. Publicó la revisión IANA
`2025-10-09` y tres booleanos sanitizados: IPv6 global ordinario y NAT64 con IPv4
público aceptados; NAT64 con IPv4 no público rechazado. Esto verifica el
clasificador Android, no una ruta IPv6/DNS64 real ni E2E de portal.

## Actualización 2026-07-30 — F-09/F-10 monotonic TTL y replay

Se instaló mediante `pm install -r -t` la QA APK SHA-256
`0258378038d703979239c8701e1e8d2ce68ecabc7de5699b68cbccbef1e5ceec`.
El `base.apk` instalado coincidió byte-for-byte; el cache cifrado permaneció en
101 bytes, modo `600`, y el cold launch restauró el certificado sin contraseña
con `FLAG_SECURE` activo.

Antes del E2E se repitieron dos regressions físicas sanitizadas:

- `ClientCertPreferenceCoordinatorInstrumentedTest`: `OK (1 test)`;
- `PublicIpAddressPolicyInstrumentedTest`: `OK (1 test)`.

El usuario autorizado abrió después Junta de Andalucía — Oficina Virtual,
confirmó una firma nueva y comunicó que el portal abrió correctamente y que todo
el interfaz, incluido el menú legacy reparado, funcionó. El alcance probado sigue
siendo solo autenticación CAdES; no se firmó un documento ni se presentó un
trámite. No se guardaron screenshots, certificado, firma, cookie, password ni
identificadores personales.

Tras la aceptación se desinstaló `dev.junta.firmamobile.test` y se eliminaron los
APK/XML de staging F-09/F-10. La aplicación target y su cache permanecieron.
