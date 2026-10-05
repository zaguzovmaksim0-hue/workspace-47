# Política de seguridad

Firma Mobile utiliza certificados digitales y operaciones de firma electrónica. Trata los informes de seguridad como sensibles, incluso cuando sólo afecten a una variante de desarrollo.

## Alcance

La revisión cubre el código activo del repositorio: certificados y estado de firma, navegación WebView y puente JavaScript, validación de solicitudes, operaciones criptográficas, relay QA bajo `ws024-relay/` y separación de capacidades entre variantes.

No se ha declarado una matriz estable de soporte de versiones binarias. Las afirmaciones de seguridad se limitan al commit, variante y operación comprobados.

## Comunicar una vulnerabilidad

No incluyas secretos ni información personal en una incidencia pública. Utiliza el canal privado de informes de seguridad de GitHub si está disponible. Si no aparece, contacta primero con el mantenedor mediante GitHub con una descripción mínima y no sensible para acordar un intercambio privado antes de enviar detalles de explotación.

Incluye: commit/versión, variante, componente, reproducción mínima con datos sintéticos, comportamiento esperado y observado, impacto, alcance en `release`/`qa`/`debug` y posibles medidas correctoras.

## Sistemas de terceros

La presencia de un portal en el catálogo o código **no autoriza a escanearlo, ejecutar fuzzing, explotar fallos, eludir controles ni realizar pruebas destructivas o autenticadas**. Trabaja únicamente sobre código e infraestructura propios, muestras locales, observación pública normal o sistemas con autorización explícita para la prueba.

Los posibles fallos de un servicio público ajeno deben comunicarse mediante el canal del operador de ese servicio. No continúes las pruebas a través de este proyecto sin permiso.

## Credenciales y evidencias

Nunca publiques ni adjuntes certificados reales, claves privadas, archivos `.jks`/`.keystore`, credenciales del relay, contraseñas, tokens, cookies, identificadores de sesión, desafíos de autenticación, OTP/SMS, claves API, documentos privados, capturas autenticadas, registros de claves TLS ni tráfico descifrado.

La muestra PKCS#12 de instrumentación es deliberadamente sintética. Su clave es pública y no debe utilizarse operativamente; consulta [su documentación](docs/test-fixtures.md).

## Separación del relay QA y la distribución

El relay es una herramienta de investigación explícita, no un proxy general. Deben conservarse estas condiciones:

- `release` utiliza transporte directo y una política de túnel vacía;
- el túnel QA requiere habilitación explícita y está limitado por variante;
- el upstream del relay permanece fijo y revisado;
- hosts operativos, credenciales, claves TLS y configuración privada no se incorporan a Git;
- los registros sólo contienen metadatos saneados.

Todo cambio que debilite esas condiciones requiere revisión específica antes de integrarse.

## Divulgación coordinada

Concede un plazo razonable para reproducir y corregir una vulnerabilidad válida antes de publicar detalles de explotación. Esta petición no limita la investigación autorizada ni concede autorización para probar servicios públicos de terceros.
