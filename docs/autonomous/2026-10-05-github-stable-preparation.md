# Preparación de distribución GitHub — 5 de octubre de 2026

Base: `569814d84e4c0e1e191cb1d5475127c9a3c93ce1`.

El mantenedor eligió GitHub y un canal estable, solicitó conservar las capacidades
existentes y aceptó mantener una indicación honesta de compatibilidad no confirmada.
No se convierten registros `VERIFIED_CONTRACT`/`EXPERIMENTAL` en `VERIFIED_E2E`.

## Implementación

- Avisos exactos de 118 AAR/JAR del bloqueo actual, indexados por componente y hash.
- Textos adicionales versionados de PDFBox Android v2.0.27.0, SLF4J v_1.7.36,
  licencias OFL de fuentes y Apache/BSD; los avisos originales de dependencias
  se conservan sin traducción ni sustitución de autoría.
- Política de privacidad en español y lector local accesible desde Información legal.
- Párrafos de tamaño acotado y lista virtualizada para avisos largos; sin WebView,
  navegación remota ni ejecución del contenido de licencias.
- `ALLOW_UNVERIFIED_PROFILES` separado de los diagnósticos QA. Perfiles desactivados
  siguen desactivados; los controles de origen, confirmación y claves no se relajan.
- CI compila, prueba y analiza también release con R8. Su firma desechable de CI
  se etiqueta como no distribuible y se sustituye localmente antes de publicar.
- Verificación de identidad de los documentos de licencia empaquetados y del
  inventario frente a las dependencias resueltas.

## Firma y límites

La firma de producción se conserva fuera del repositorio. No se exportan claves
privadas ni contraseñas a CI o GitHub. La publicación requiere verificar firma,
hash, alineación y contenidos del APK final, además del SHA exacto de CI.

No se ejecutan E2E, no se usan documentos o certificados reales para las pruebas
y no se garantiza aceptación universal de portales. La designación del canal no
sustituye esa evidencia. El cambio de firma de instalaciones previas necesita una
migración separada, sin borrar datos ni desinstalar automáticamente.
