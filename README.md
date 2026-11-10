# Firma Mobile

Español | [English](README.en.md)

**Proyecto independiente y no oficial.** Firma Mobile no pertenece a la Junta de Andalucía ni a ninguna otra administración pública. No es la aplicación oficial AutoFirma y no implica patrocinio, certificación ni respaldo de los organismos cuyos servicios aparecen en el catálogo.

Aplicación Android para acceder a servicios públicos españoles y utilizar certificados digitales en operaciones de identificación y firma electrónica compatibles. Integra un navegador, gestión local de certificados PKCS#12 y adaptadores para protocolos y portales concretos.

El proyecto es experimental. La compatibilidad se verifica por portal y operación: que un sitio aparezca en el catálogo no garantiza que admita identificación, firma o presentación de documentos desde la aplicación.

## Empezar

- [Instalación y primeros pasos](docs/instalacion.md)
- [Firma del APK y actualizaciones](docs/instalacion.md#firma-del-apk-y-actualizaciones)
- [Cambios de la versión 0.2.17](CHANGELOG.md)
- [Documentación técnica](docs/README.md)
- [Política de seguridad](SECURITY.md)
- [Política de privacidad](docs/privacidad.md)
- [Cómo contribuir](CONTRIBUTING.md)

El nombre visible es **Firma Mobile** y la versión de uso diario es **0.2.17**. El identificador Android se mantiene como `dev.junta.firmamobile` para conservar la continuidad de las actualizaciones. El tipo de compilación interno `optimized` no forma parte del nombre visible de la versión.

## APK diario y clave de firma

El APK de uso diario se genera en GitHub Actions con **Signed daily APK** sobre `main`, solo cuando CI y seguridad están verdes para ese commit, y siempre se firma con la misma clave persistente:

- certificado `CN=Firma Mobile Daily APK`;
- SHA-256 `f1bcad77c90bab402ac9671b0ac6c034cc1d916d4561f65e480740021e5d6971`, fijado en `config/signing/daily-cert-sha256.txt`.

La clave privada no está en el repositorio ni en los artefactos: la custodia el mantenedor y GitHub guarda una copia como secretos del entorno `daily-signing`, que no se pueden descargar. Si Android indica que el paquete «entra en conflicto con un paquete existente», la aplicación instalada se firmó con otra clave (por ejemplo, la antigua `CN=Firma Mobile, OU=Release`): hay que desinstalarla una vez, lo que borra sus datos, e importar de nuevo el certificado. Consulta [los pasos de instalación y migración](docs/instalacion.md#firma-del-apk-y-actualizaciones) y [la referencia de claves, verificación y rotación](docs/daily-apk-signing.md).

## Abrir un portal

Al tocar una sede del catálogo, se abre directamente en su modo específico si tiene un perfil activo y una vinculación válida para esta compilación. Las demás sedes y las direcciones escritas manualmente se abren en modo universal, sin adoptar los permisos de ningún perfil.

El modo queda fijado durante esa sesión. Para abrir una sede del catálogo en modo universal de forma explícita, mantén pulsada su tarjeta y selecciona **Abrir en modo universal (sin perfil)**. Esto inicia una sesión nueva; los errores de firma y los cambios de página no cambian el modo automáticamente. Un perfil activo no equivale a una prueba E2E de todas sus operaciones: identificación, firma y devolución del resultado se verifican por separado.

## Certificados y privacidad

El certificado y su contraseña se procesan localmente. En cada desbloqueo manual se eligen 15 minutos (por defecto), 1 hora o 24 horas. En API 30+ la aplicación puede conservarlo mediante una caché cifrada sin renovar el vencimiento al restaurarlo; la opción de bloqueo del menú del certificado permite finalizarlo antes. Bloquear no elimina el archivo ni revoca el certificado ante su emisor, y no garantiza cerrar una sesión ya abierta en un portal.
La recuperación guardada requiere confirmación nativa biométrica/PIN por operación; Android 8–10 conserva solo la sesión en memoria. Aceptación física pendiente.

Cada operación de firma requiere confirmación. No deben incorporarse al repositorio certificados reales, contraseñas, cookies, documentos privados ni capturas autenticadas. Las pruebas emplean datos sintéticos; consulta [las reglas de las muestras de prueba](docs/test-fixtures.md).

## Compatibilidad y límites

Los perfiles y sus permisos son explícitos. Navegar por una dirección HTTPS no concede automáticamente permiso para utilizar el certificado. La configuración de un proveedor WebAuthn tampoco demuestra que ese proveedor autorice todas las operaciones.

- [Inventario y evidencias](docs/compatibility/)
- [Observaciones de protocolos](docs/protocol-observations.md)
- [Evidencias históricas de operaciones concretas](docs/e2e/)
- [Catálogo generado](app/src/main/res/raw/public_portal_catalog_v1.json)

El catálogo se genera con `tools/generate_public_portal_catalog.py` a partir de fuentes revisadas del repositorio; el generador no consulta sitios externos. El modo de uso diario incluye perfiles experimentales y no equivale a una distribución pública certificada.

## Desarrollo y comprobaciones

Utiliza el Gradle Wrapper del repositorio: Java 17 para el runtime de Gradle y el nivel de código Java; las tareas de pruebas unitarias del host seleccionan un toolchain Java 21, según `app/build.gradle.kts`. GitHub Actions es el entorno principal para comprobar el **SHA exacto** de cada candidato: pruebas Android Debug, QA y Optimized, lint, APK, Python, Go y análisis de seguridad. La instrumentación con emulador se ejecuta en el nightly de la rama predeterminada o mediante activación manual explícita, no en los PR/push ordinarios. Las pruebas locales acotadas no sustituyen ese ciclo.

```bash
./gradlew testOptimizedUnitTest
./gradlew lintOptimized
./gradlew assembleOptimized
```

Los APK `debug`, `qa` y `optimized` que genera Gradle, también en CI, llevan una firma de depuración desechable y solo sirven para pruebas; el canal diario vuelve a firmar el APK `optimized` comprobado con la clave persistente. Las compilaciones `release` necesitan una configuración de firma privada y nunca deben recurrir automáticamente a la clave de depuración. Consulta [firma de distribución](docs/release-signing.md), [verificación en GitHub Actions](docs/agents/github-actions-verification.md) y [notas de Termux](docs/building-on-termux.md).

Los cambios se preparan en ramas y se integran mediante pull request después de las comprobaciones aplicables. `main` sigue siendo la rama estable; los documentos históricos conservan sus fechas y no sustituyen la verificación del candidato actual.

## Licencia y procedencia

El código del proyecto utiliza la [licencia Apache-2.0](LICENSE). Los componentes de terceros conservan sus propias licencias y avisos: [NOTICE](NOTICE), [procedencia](docs/provenance.md), [selección de licencia](docs/license-selection.md) y [auditoría de dependencias](docs/licenses/runtime-dependency-audit.md).

Las marcas, nombres y dominios de terceros se mencionan únicamente para explicar la interoperabilidad. Las obligaciones de redistribución de cada APK/AAB se comprueban por separado; publicar el código no certifica automáticamente cualquier binario.

El [registro histórico de publicación](docs/oss-publication-status.md) conserva la aceptación de metadatos de autoría y las declaraciones de procedencia del mantenedor. La documentación técnica e histórica puede mantenerse en inglés; las instrucciones principales para usuarios se ofrecen en español.
