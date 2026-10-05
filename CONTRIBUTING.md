# Cómo contribuir

Firma Mobile es un proyecto independiente de identificación y firma electrónica para Android. Las contribuciones deben conservar la privacidad, las comprobaciones de seguridad que rechazan operaciones no autorizadas y la trazabilidad del código.

## Antes de modificar el código

- Lee los documentos y las evidencias pertinentes de `docs/`.
- No amplíes orígenes de confianza, destinos de red, callbacks, algoritmos o permisos QA por suposición.
- Añade pruebas de regresión y comprueba el SHA exacto del candidato en GitHub Actions.
- Separa hechos verificados de hipótesis. No eleves el nivel de compatibilidad sin evidencia.
- Conserva `main` estable e integra los cambios mediante pull request.

Las pruebas, lint y compilaciones pertinentes se describen en [la política de verificación](docs/agents/github-actions-verification.md). La firma `release` requiere configuración privada; no añadas una alternativa con una clave de depuración. No declares superada una comprobación que no se haya ejecutado sobre el candidato.

## Datos privados y muestras

No publiques certificados personales `.p12`/`.pfx`, almacenes de claves, claves TLS, credenciales del relay, contraseñas, tokens, cookies, OTP, capturas HAR/pcap autenticadas, documentos privados ni imágenes con datos de cuentas o certificados. Usa muestras sintéticas; la muestra pública del repositorio se documenta en [test-fixtures](docs/test-fixtures.md).

Una muestra binaria nueva necesita procedencia verificable, identidad no operativa, contraseña pública de prueba cuando corresponda y documentación antes de su revisión. Siempre que sea posible, genera el material de prueba durante el test.

## Servicios públicos y autorización

La interoperabilidad no autoriza a investigar la seguridad de sistemas ajenos. Limita las comprobaciones a muestras locales, infraestructura propia, uso público normal y acotado, o sistemas para los que tengas permiso explícito que cubra la prueba.

No explores endpoints arbitrarios, eludas autenticación, ejecutes fuzzing contra administraciones ni presentes trámites como prueba. Antes de una modificación, pago o presentación real, debe existir autorización específica para esa operación. Conserva únicamente los metadatos saneados necesarios para describir el protocolo.

## Procedencia y marcas

Todo código, recurso o conjunto de datos copiado, adaptado o generado debe tener un origen trazable. Para material ajeno, registra proyecto, componente exacto, licencia y versión, atribución requerida y tipo de reutilización. Que un archivo sea público no significa que tenga licencia de reutilización.

Actualiza [la procedencia](docs/provenance.md) al añadir código derivado de terceros, fuentes, imágenes, iconos, bibliotecas incorporadas o catálogos de nuevas familias de fuentes. Conserva sus avisos y licencias.

No añadas sellos, logotipos ni afirmaciones que impliquen respaldo, certificación o afiliación con una administración, universidad u otro servicio sin permiso documentado. Los nombres y dominios pueden citarse descriptivamente para explicar la interoperabilidad.

## Evidencias y pull requests

Detectar AutoFirma en una página no demuestra compatibilidad. Una firma local correcta no demuestra aceptación del portal. Un acceso autenticado sólo acredita el flujo concreto observado, no todos los trámites.

Cada PR debe explicar el problema, el límite de confianza afectado, las pruebas del commit, el impacto en privacidad y procedencia, y las limitaciones pendientes. Un PR que exige divulgar credenciales reales o evidencias sensibles no está listo para revisión.
