Política de privacidad de Firma Mobile
Actualización: 24 de octubre de 2026

Proyecto: Firma Mobile. Mantenedor: Maksim Zaguzov.
Aplicación independiente y no oficial para Android. Esta política describe la aplicación publicada por este proyecto, no las políticas de los portales visitados ni las de Android, Google o el proveedor de archivos elegido.

1. Certificados y firma
El usuario selecciona un archivo PKCS#12 mediante el selector de Android. La aplicación conserva una referencia al archivo y datos de presentación del certificado (titular, emisor y vigencia) en su almacenamiento privado. El archivo PKCS#12, su clave privada y su contraseña no se suben a un servidor del proyecto mediante el flujo de importación y firma.

Al identificarse o firmar, la operación puede transmitir al portal elegido el certificado público, una firma, los datos o huellas del documento y los parámetros necesarios para esa operación. El certificado público puede contener datos identificativos. La aplicación pide confirmación para firmar y restringe los destinos mediante perfiles. Las operaciones dependen del portal y no tienen una garantía universal de aceptación.

2. Desbloqueo local
El material necesario para reutilizar el certificado puede mantenerse en memoria y en una caché local cifrada de hasta 24 horas. La opción «Bloquear certificado» termina el desbloqueo local. No revoca el certificado ante su emisor ni garantiza cerrar una sesión que un portal ya haya establecido.

«Olvidar certificado» elimina la referencia guardada por la aplicación y su desbloqueo; no borra el archivo original de tu proveedor de archivos. No compartas un archivo PKCS#12 ni su contraseña en informes de errores.

3. Navegador, portales y documentos
Los sitios elegidos reciben las peticiones de navegación, dirección IP y los datos que decidas proporcionarles. WebView puede conservar cookies y otros datos de navegación. Los portales aplican sus propias políticas y plazos. Los archivos descargados o guardados mediante Android permanecen en el destino elegido hasta que los elimines allí.

El borrado local de datos de un sitio no cancela trámites enviados, revoca firmas, elimina documentos remotos ni garantiza invalidar todas las sesiones de un organismo. Una operación ya enviada puede haber llegado al destinatario aunque se produzca un error de red; comprueba el justificante antes de repetirla.

4. Región y ubicación
La selección manual de región está disponible. La detección automática se usa cuando la solicitas y requiere el permiso de ubicación aproximada. Puede utilizar una posición reciente y el geocodificador de Android para convertir coordenadas en región. El proveedor de geocodificación del dispositivo puede procesar la consulta conforme a sus propios términos. Firma Mobile no necesita seguimiento continuo en segundo plano para esta función. Puedes denegar el permiso y seleccionar la región manualmente.

5. Avisos de caducidad del certificado
Los avisos están desactivados por defecto y solo se activan si los habilitas en «Ajustes de recordatorios». En Android 13 o posterior requieren el permiso de notificaciones. Se calculan en el dispositivo a partir de la fecha de caducidad del certificado guardado y se muestran 30 y 7 días naturales antes de esa fecha. La notificación solo indica los días restantes, sin titular ni otros datos del certificado, y se marca como privada para la pantalla de bloqueo.

Para programarlos, la aplicación usa la alarma de Android y vuelve a calcular el siguiente aviso tras reiniciar el dispositivo, actualizar la aplicación o cambiar la hora o la zona horaria. En su almacenamiento privado guarda si los avisos están activados, una huella SHA-256 calculada a partir de la referencia y la fecha de caducidad del certificado, y qué avisos ya se mostraron. No se envía nada a un servidor del proyecto. Puedes desactivarlos en la aplicación o retirar el permiso de notificaciones en Android.

6. Verificación de firmas en PDF
La verificación de firmas de un PDF se realiza en el dispositivo y sin conexión. Eliges el archivo con el selector de Android; la aplicación lo lee para comprobar la firma y no lo sube a ningún servidor ni lo guarda en su almacenamiento. El resultado describe la comprobación técnica local y no sustituye la validación de un prestador de servicios de confianza ni del organismo destinatario.

7. Clave en el almacén seguro de Android
Esta función se activa por etapas y solo se aplica cuando está disponible en tu versión y decides usarla. Con tu confirmación, la aplicación copia la clave privada del archivo PKCS#12 al almacén de claves de Android: a StrongBox si el dispositivo lo admite (Android 12 o posterior) o, solo si lo aceptas expresamente, al entorno seguro del dispositivo. No se admite una ubicación únicamente por software. La aplicación puede usar la clave para firmar, pero no puede leerla ni exportarla. Se sigue protegiendo con el desbloqueo de la aplicación, no con una confirmación del sistema en cada firma.

La clave no se incluye en copias de seguridad ni se puede trasladar a otro dispositivo. Desinstalar la aplicación, borrar sus datos o restablecer el teléfono la elimina de forma irreversible, por lo que debes conservar el archivo PKCS#12 original en un lugar seguro. La aplicación guarda en su almacenamiento privado un alias aleatorio, la ubicación confirmada, la huella SHA-256 del certificado y su nombre visible. «Olvidar certificado» elimina esa referencia y la clave del almacén de Android; nunca borra tu archivo original.

8. Solicitudes de firma desde otras aplicaciones o enlaces
Esta función se activa por etapas y solo se aplica cuando está disponible en tu versión. Otras aplicaciones o páginas web pueden abrir Firma Mobile con un enlace afirma://. La aplicación no puede verificar quién envía la solicitud y la muestra como de origen desconocido, con un aviso. Nada se firma ni se envía sin tu confirmación expresa: antes de confirmar ves la operación, el tamaño y la huella SHA-256 de los datos y la dirección de destino sin parámetros. La confirmación sirve solo para esa solicitud y caduca a los dos minutos.

Si confirmas, la aplicación envía por HTTPS a la dirección indicada en la solicitud el certificado público, la firma y los datos necesarios para la operación. Se rechazan las solicitudes que intentan omitir la confirmación u obtener los datos de forma indirecta, y las que llegan mientras otra operación de firma está en curso. Comprueba que esperabas la solicitud antes de confirmarla.

9. Componentes del dispositivo y de terceros
La aplicación usa Android WebView y bibliotecas de terceros, incluidos Google Play services mediante AndroidX Credentials (`androidx.credentials:credentials-play-services-auth`) para integraciones de identidad/credenciales. Su funcionamiento puede implicar servicios del dispositivo o del proveedor; no debe interpretarse esta política como una promesa de que el dispositivo o los portales nunca se comuniquen con terceros. Los componentes y avisos se enumeran en «Licencias y componentes».

Esta distribución no incluye un servidor propio del proyecto para almacenar tus certificados o documentos ni un servicio propio de publicidad o analítica. Los sitios visitados pueden tener sus propios servicios y cookies. El permiso de biometría puede proceder de bibliotecas de autenticación; la aplicación no recibe la plantilla biométrica que conserva Android.

10. Seguridad y eliminación
La configuración de producción desactiva copias de seguridad de datos de la aplicación y tráfico HTTP en claro. Se aplican controles de origen y se evita registrar secretos en los registros de la aplicación. Ninguna medida elimina todo riesgo: mantén Android actualizado y no uses un dispositivo comprometido para certificados sensibles.

Puedes bloquear u olvidar el certificado y desactivar los avisos de caducidad desde la aplicación, limpiar datos del sitio mediante las opciones disponibles y revocar permisos en Android. Android permite borrar los datos locales de la aplicación o desinstalarla; esto puede eliminar de forma irreversible sus preferencias. Los archivos que hayas guardado fuera del almacenamiento privado requieren eliminación independiente.

11. Contacto y cambios
La información actual del proyecto se publica en:
https://github.com/zaguzovmaksim0-hue/workspace-47

Puedes plantear consultas generales sobre esta política en:
https://github.com/zaguzovmaksim0-hue/workspace-47/issues

Las incidencias son públicas: no incluyas datos personales, certificados, contraseñas, documentos, cookies ni capturas autenticadas. Para comunicar un problema de seguridad consulta primero SECURITY.md en el repositorio. No envíes datos sensibles mediante una incidencia pública.

Los cambios que afecten al tratamiento de datos deben reflejarse en una nueva versión de esta política. La política no sustituye la información ni los consentimientos que deba presentar un portal o servicio externo.
