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
