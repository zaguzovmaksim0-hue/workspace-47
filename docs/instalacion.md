# Instalación y primeros pasos

## Antes de instalar

Firma Mobile es un proyecto experimental e independiente, no la aplicación oficial AutoFirma. Utiliza únicamente un APK proporcionado por el mantenedor y comprobado para el commit correspondiente. No existe en este documento una promesa de disponibilidad en Google Play ni de una publicación binaria estable.

Para actualizar una instalación existente, el paquete y la firma deben coincidir. No desinstales la aplicación ni borres sus datos si quieres conservar la configuración. Si Android rechaza la actualización por una firma diferente, detente y comprueba el origen del APK; no desactives las protecciones del sistema. Consulta [Firma del APK y actualizaciones](#firma-del-apk-y-actualizaciones).

## Uso básico

1. Abre **Firma Mobile**. Puedes consultar el catálogo sin importar un certificado.
2. Selecciona tu región desde el catálogo; puedes cambiarla más adelante. La detección por ubicación es opcional y requiere permiso. Si falla, elige la región manualmente.
3. Cuando necesites identificarte o firmar, selecciona tu archivo `.p12` o `.pfx` e introduce su contraseña. La aplicación no emite certificados.
4. Abre el servicio y revisa la operación antes de confirmarla. Una firma generada localmente no demuestra por sí sola que el portal haya aceptado un trámite.
5. Para finalizar el desbloqueo antes de que expire, utiliza la opción de bloqueo del menú del certificado. El próximo uso requerirá de nuevo la contraseña.

Bloquear el certificado no elimina el archivo, no lo revoca ante su emisor y no garantiza cerrar las sesiones del servidor. Usa el cierre de sesión del propio portal cuando corresponda.

## Elegir el modo de navegación

Una pulsación normal en el catálogo utiliza el perfil específico del portal cuando está activo y validado para la compilación instalada. Si no existe un perfil disponible, la sede se abre en modo universal. Una dirección introducida manualmente siempre abre una sesión universal, incluso si coincide con una sede conocida.

Para usar el modo universal de forma explícita en una sede con perfil, mantén pulsada su tarjeta y selecciona **Abrir en modo universal (sin perfil)**. Cada apertura crea una sesión con un modo fijo: seguir enlaces o recibir un error no cambia ese modo ni concede permisos de otro perfil. El modo universal solicita por separado la autorización que necesite para utilizar el certificado.

Las ventanas solicitadas sin una pulsación directa requieren una confirmación nativa antes de abrirse. La solicitud caduca a los 15 segundos y se cancela si cambias de página o pasas la aplicación a segundo plano. La nueva ventana usa el modo universal; abrirla no autoriza el uso del certificado ni copia los permisos del perfil de la página anterior.

## Variantes y actualizaciones

La versión visible de uso diario es **0.2.17**. `optimized` es una denominación interna de compilación; incluye perfiles experimentales, utiliza transporte directo y no habilita los controles de depuración. El paquete permanece `dev.junta.firmamobile`.

El APK para este canal se obtiene con **Actions → Signed daily APK → Run workflow → main**, después de que CI y seguridad estén verdes para ese commit. El artefacto `firmamobile-daily-<commit>` contiene `firmamobile-daily.apk`, su suma SHA-256 (`SHA256SUMS`), el commit (`COMMIT.txt`) y el informe público del certificado (`daily-certificate.txt`); GitHub lo conserva 14 días. La firma permanente se verifica contra `config/signing/daily-cert-sha256.txt`. Los APK `optimized` de CI sin este paso siguen siendo artefactos de prueba con firma debug, no actualizaciones de este canal.

Una compilación de desarrollo no debe confundirse con una distribución pública `release`, que exige su propia configuración de firma y revisión de obligaciones de terceros. Consulta [la política de seguridad](../SECURITY.md) y [la configuración de firma](release-signing.md).

## Firma del APK y actualizaciones

Todos los APK del canal diario se firman con la misma clave persistente:

- certificado: `CN=Firma Mobile Daily APK`;
- SHA-256: `f1bcad77c90bab402ac9671b0ac6c034cc1d916d4561f65e480740021e5d6971`;
- SHA-1: `3ebac06c7bf4a5f1cadccb205335f1f0ee830e0b`.

Antes de instalar, comprueba la suma y la huella con `apksigner` (incluido en el Android SDK; en Termux, `pkg install apksigner`):

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs firmamobile-daily.apk | grep 'SHA-256'
```

Todas las líneas `SHA-256 digest` deben mostrar exactamente la huella anterior. Si no coincide, no instales el APK.

### «El paquete entra en conflicto con un paquete existente»

Si Android muestra ese aviso (o uno similar), o `adb`/`pm` devuelven `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, la aplicación instalada está firmada con otra clave: por ejemplo, la antigua clave local `CN=Firma Mobile, OU=Release` (SHA-256 `b5d187690ee2a17d8c0812ac24139ee480280f780118ce2aefb6121ff05550dd`) o una clave de depuración. Android no permite actualizarla y la aplicación no admite copias de seguridad del sistema, así que sus datos no pueden trasladarse. Migración, una sola vez:

1. Guarda fuera de la aplicación el archivo `.p12`/`.pfx` de tu certificado y su contraseña.
2. Descarga el APK diario y comprueba su suma y su huella.
3. Desinstala Firma Mobile (Ajustes → Aplicaciones → Firma Mobile → Desinstalar). Se borran los certificados importados, las referencias guardadas, la región y el resto de datos locales.
4. Instala `firmamobile-daily.apk`, ábrelo e importa de nuevo el certificado.

A partir de ahí, cada APK diario más reciente se instala como actualización normal, sin perder datos. Android sigue rechazando un APK con un `versionCode` inferior al instalado.

La clave privada no está en el repositorio ni en los artefactos. Dónde se guarda, cómo restaurarla en GitHub y cómo rotarla se explica en [la referencia de firma del APK](daily-apk-signing.md) (en inglés).