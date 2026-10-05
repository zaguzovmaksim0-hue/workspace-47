Política de privacidad de Firma Mobile
Actualización: 5 de octubre de 2026

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

5. Componentes del dispositivo y de terceros
La aplicación usa Android WebView y bibliotecas de terceros, incluidas integraciones de identidad/credenciales de Google donde se utilizan. Su funcionamiento puede implicar servicios del dispositivo o del proveedor; no debe interpretarse esta política como una promesa de que el dispositivo o los portales nunca se comuniquen con terceros. Los componentes y avisos se enumeran en «Licencias y componentes».

Esta distribución no incluye un servidor propio del proyecto para almacenar tus certificados o documentos ni un servicio propio de publicidad o analítica. Los sitios visitados pueden tener sus propios servicios y cookies. El permiso de biometría puede proceder de bibliotecas de autenticación; la aplicación no recibe la plantilla biométrica que conserva Android.

6. Seguridad y eliminación
La configuración de producción desactiva copias de seguridad de datos de la aplicación y tráfico HTTP en claro. Se aplican controles de origen y se evita registrar secretos en los registros de la aplicación. Ninguna medida elimina todo riesgo: mantén Android actualizado y no uses un dispositivo comprometido para certificados sensibles.

Puedes bloquear u olvidar el certificado desde la aplicación, limpiar datos del sitio mediante las opciones disponibles y revocar permisos en Android. Android permite borrar los datos locales de la aplicación o desinstalarla; esto puede eliminar de forma irreversible sus preferencias. Los archivos que hayas guardado fuera del almacenamiento privado requieren eliminación independiente.

7. Contacto y cambios
La información actual del proyecto se publica en:
https://github.com/zaguzovmaksim0-hue/workspace-47

Puedes plantear consultas generales sobre esta política en:
https://github.com/zaguzovmaksim0-hue/workspace-47/issues

Las incidencias son públicas: no incluyas datos personales, certificados, contraseñas, documentos, cookies ni capturas autenticadas. Para comunicar un problema de seguridad consulta primero SECURITY.md en el repositorio. No envíes datos sensibles mediante una incidencia pública.

Los cambios que afecten al tratamiento de datos deben reflejarse en una nueva versión de esta política. La política no sustituye la información ni los consentimientos que deba presentar un portal o servicio externo.
