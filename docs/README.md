# Índice de documentación

## Para usuarios

- [Instalación y primeros pasos](instalacion.md).
- [Cambios de versión](../CHANGELOG.md).
- [Descripción del proyecto](../README.md).

## Desarrollo actual

- [Ciclo de trabajo del repositorio](../CONTEXT.md): main estable, ramas acotadas y verificación del commit exacto.
- [Guía de contribución](../CONTRIBUTING.md).
- [Política de verificación](agents/github-actions-verification.md): pruebas, lint y compilaciones en GitHub Actions; el emulador requiere activación manual explícita.
- [Plan de pruebas](test-plan.md) y [muestras sintéticas](test-fixtures.md).
- [Configuración de firma](release-signing.md).
- [Modelo de seguridad](../SECURITY.md) y [procedencia](provenance.md).

## Producto y evidencias

- [Especificación](spec.md).
- [Inventario de compatibilidad](compatibility/all-spanish-public-portals-inventory.md).
- [Observaciones de protocolos](protocol-observations.md).
- `compatibility/` contiene evidencias de descubrimiento y compatibilidad.
- `e2e/` conserva evidencias históricas de operaciones acotadas, no una garantía de compatibilidad universal actual.

## Registros históricos

`autonomous/`, `superpowers/` y los informes antiguos conservan sus fechas y evidencias por commit. No indican que deban retomarse ramas obsoletas. La documentación técnica e histórica puede permanecer en inglés; se conservan sus rutas para no romper enlaces.

El flujo con certificados reales está [archivado](archive/workflows/real-e2e.yml.disabled) como documento inerte. Sus credenciales de GitHub y su historial de ejecuciones se retiraron en la limpieza autorizada. No debe reactivarse ni recibir credenciales sin nueva autorización. Las pruebas de política siguen inspeccionando sus límites históricos.

El flujo y el paquete del perfilador Kai para macOS, ajenos a este proyecto, se eliminaron del árbol actual; permanecen recuperables en el historial de Git.

## Organización de las copias de trabajo

Utiliza una copia limpia que siga la rama actual prevista. Antes de reorganizar copias antiguas, conserva sus commits, diferencias binarias y archivos fuente no seguidos. Compara cualquier archivo antiguo modificado con el código actual antes de integrarlo; no apliques cambios obsoletos a los flujos de firma sin revisión.
