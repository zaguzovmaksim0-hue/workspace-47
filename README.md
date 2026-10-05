# Firma Mobile

Español | [English](README.en.md)

**Proyecto independiente y no oficial.** Firma Mobile no pertenece a la Junta de Andalucía ni a ninguna otra administración pública. No es la aplicación oficial AutoFirma y no implica patrocinio, certificación ni respaldo de los organismos cuyos servicios aparecen en el catálogo.

Aplicación Android para acceder a servicios públicos españoles y utilizar certificados digitales en operaciones de identificación y firma electrónica compatibles. Integra un navegador, gestión local de certificados PKCS#12 y adaptadores para protocolos y portales concretos.

El proyecto es experimental. La compatibilidad se verifica por portal y operación: que un sitio aparezca en el catálogo no garantiza que admita identificación, firma o presentación de documentos desde la aplicación.

## Empezar

- [Instalación y primeros pasos](docs/instalacion.md)
- [Cambios de la versión 0.2.11](CHANGELOG.md)
- [Documentación técnica](docs/README.md)
- [Política de seguridad](SECURITY.md)
- [Cómo contribuir](CONTRIBUTING.md)

El nombre visible es **Firma Mobile** y la versión de uso diario es **0.2.11**. El identificador Android se mantiene como `dev.junta.firmamobile` para conservar la continuidad de las actualizaciones. El tipo de compilación interno `optimized` no forma parte del nombre visible de la versión.

## Certificados y privacidad

El certificado y su contraseña se procesan localmente. La aplicación puede conservar el desbloqueo hasta 24 horas mediante una caché cifrada; la opción de bloqueo del menú del certificado permite finalizarlo antes. Bloquear no elimina el archivo ni revoca el certificado ante su emisor, y no garantiza cerrar una sesión ya abierta en un portal.

Cada operación de firma requiere confirmación. No deben incorporarse al repositorio certificados reales, contraseñas, cookies, documentos privados ni capturas autenticadas. Las pruebas emplean datos sintéticos; consulta [las reglas de las muestras de prueba](docs/test-fixtures.md).

## Compatibilidad y límites

Los perfiles y sus permisos son explícitos. Navegar por una dirección HTTPS no concede automáticamente permiso para utilizar el certificado. La configuración de un proveedor WebAuthn tampoco demuestra que ese proveedor autorice todas las operaciones.

- [Inventario y evidencias](docs/compatibility/)
- [Observaciones de protocolos](docs/protocol-observations.md)
- [Evidencias históricas de operaciones concretas](docs/e2e/)
- [Catálogo generado](app/src/main/res/raw/public_portal_catalog_v1.json)

El catálogo se genera con `tools/generate_public_portal_catalog.py` a partir de fuentes revisadas del repositorio; el generador no consulta sitios externos. El modo de uso diario incluye perfiles experimentales y no equivale a una distribución pública certificada.

## Desarrollo y comprobaciones

Utiliza el Gradle Wrapper del repositorio y Java 17. GitHub Actions es el entorno principal para comprobar el **SHA exacto** de cada candidato: pruebas Android Debug, QA y Optimized, lint, APK, Python, Go y análisis de seguridad. La instrumentación con emulador requiere activación manual explícita. Las pruebas locales acotadas no sustituyen ese ciclo.

```bash
./gradlew testOptimizedUnitTest
./gradlew lintOptimized
./gradlew assembleOptimized
```

Las compilaciones `release` necesitan una configuración de firma privada y nunca deben recurrir automáticamente a la clave de depuración. Consulta [firma de distribución](docs/release-signing.md), [verificación en GitHub Actions](docs/agents/github-actions-verification.md) y [notas de Termux](docs/building-on-termux.md).

Los cambios se preparan en ramas y se integran mediante pull request después de las comprobaciones aplicables. `main` sigue siendo la rama estable; los documentos históricos conservan sus fechas y no sustituyen la verificación del candidato actual.

## Licencia y procedencia

El código del proyecto utiliza la [licencia MIT](LICENSE). Los componentes de terceros conservan sus propias licencias y avisos: [NOTICE](NOTICE), [procedencia](docs/provenance.md), [selección de licencia](docs/license-selection.md) y [auditoría de dependencias](docs/licenses/runtime-dependency-audit.md).

Las marcas, nombres y dominios de terceros se mencionan únicamente para explicar la interoperabilidad. Las obligaciones de redistribución de cada APK/AAB se comprueban por separado; publicar el código no certifica automáticamente cualquier binario.

El [registro histórico de publicación](docs/oss-publication-status.md) conserva la aceptación de metadatos de autoría y las declaraciones de procedencia del mantenedor. La documentación técnica e histórica puede mantenerse en inglés; las instrucciones principales para usuarios se ofrecen en español.
