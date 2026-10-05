# Instalación y primeros pasos

## Antes de instalar

Firma Mobile es un proyecto experimental e independiente, no la aplicación oficial AutoFirma. Utiliza únicamente un APK proporcionado por el mantenedor y comprobado para el commit correspondiente. No existe en este documento una promesa de disponibilidad en Google Play ni de una publicación binaria estable.

Para actualizar una instalación existente, el paquete y la firma deben coincidir. No desinstales la aplicación ni borres sus datos si quieres conservar la configuración. Si Android rechaza la actualización por una firma diferente, detente y comprueba el origen del APK; no desactives las protecciones del sistema.

## Uso básico

1. Abre **Firma Mobile**. Puedes consultar el catálogo sin importar un certificado.
2. Selecciona tu región desde el catálogo; puedes cambiarla más adelante. La detección por ubicación es opcional y requiere permiso. Si falla, elige la región manualmente.
3. Cuando necesites identificarte o firmar, selecciona tu archivo `.p12` o `.pfx` e introduce su contraseña. La aplicación no emite certificados.
4. Abre el servicio y revisa la operación antes de confirmarla. Una firma generada localmente no demuestra por sí sola que el portal haya aceptado un trámite.
5. Para finalizar el desbloqueo antes de que expire, utiliza la opción de bloqueo del menú del certificado. El próximo uso requerirá de nuevo la contraseña.

Bloquear el certificado no elimina el archivo, no lo revoca ante su emisor y no garantiza cerrar las sesiones del servidor. Usa el cierre de sesión del propio portal cuando corresponda.

## Variantes y actualizaciones

La versión visible de uso diario es **0.2.11**. `optimized` es una denominación interna de compilación; incluye perfiles experimentales, utiliza transporte directo y no habilita los controles de depuración. El paquete permanece `dev.junta.firmamobile`.

Una compilación de desarrollo no debe confundirse con una distribución pública `release`, que exige su propia configuración de firma y revisión de obligaciones de terceros. Consulta [la política de seguridad](../SECURITY.md) y [la configuración de firma](release-signing.md).
