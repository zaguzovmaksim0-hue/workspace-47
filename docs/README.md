# Índice de documentación

## Para usuarios

- [Instalación y primeros pasos](instalacion.md).
- [Cambios de versión](../CHANGELOG.md).
- [Descripción del proyecto](../README.md).

## Desarrollo actual

- [Ciclo de trabajo del repositorio](../CONTEXT.md): main estable, ramas acotadas y verificación del commit exacto.
- [Guía de contribución](../CONTRIBUTING.md).
- [Política de verificación](agents/github-actions-verification.md): pruebas, lint y compilaciones en GitHub Actions; el emulador se ejecuta en la programación nocturna de la rama predeterminada o mediante activación manual explícita, no en PR/push ordinarios.
- [Plan de pruebas](test-plan.md) y [muestras sintéticas](test-fixtures.md).
- [Configuración de firma](release-signing.md) y [claves y firma del APK diario](daily-apk-signing.md): huellas, dónde está la clave, verificación, migración y rotación.
- [Modelo de seguridad](../SECURITY.md) y [procedencia](provenance.md).

## Producto y evidencias

- [Especificación](spec.md).
- [Inventario de compatibilidad](compatibility/all-spanish-public-portals-inventory.md).
- [Observaciones de protocolos](protocol-observations.md).
- `compatibility/` contiene evidencias de descubrimiento y compatibilidad.
- `e2e/` conserva evidencias históricas de operaciones acotadas, no una garantía de compatibilidad universal actual.

## Registros históricos

- [Archivo acotado de registros de agentes](archive/agent-records/README.md): copias íntegras con manifiesto SHA-256 y enlaces desde las rutas anteriores.

`autonomous/`, `superpowers/` y los informes antiguos conservan sus fechas y evidencias por commit. No indican que deban retomarse ramas obsoletas. La documentación técnica e histórica puede permanecer en inglés; se conservan sus rutas para no romper enlaces.

El [flujo con certificados reales](../.github/workflows/real-e2e.yml) se reactivó el 5 de noviembre de 2026 con autorización expresa del operador. Solo se lanza a mano desde `main`, dentro del entorno protegido `real-e2e` con aprobación del operador, y el certificado y su contraseña existen únicamente como secretos de ese entorno. Recorre las fichas del catálogo en emuladores desechables, firma solo desafíos de autenticación de perfiles revisados, cancela cualquier otra firma en la confirmación y publica únicamente informes saneados. Las pruebas de política inspeccionan esos límites.

El flujo y el paquete del perfilador Kai para macOS, ajenos a este proyecto, se eliminaron del árbol actual; permanecen recuperables en el historial de Git.

## Organización de las copias de trabajo

Utiliza una copia limpia que siga la rama actual prevista. Antes de reorganizar copias antiguas, conserva sus commits, diferencias binarias y archivos fuente no seguidos. Compara cualquier archivo antiguo modificado con el código actual antes de integrarlo; no apliques cambios obsoletos a los flujos de firma sin revisión.

- [Revisión de distribución pública y bloqueos (5 de octubre de 2026)](public-distribution-review-2026-10-05.md)

- [Política de privacidad](privacidad.md)
