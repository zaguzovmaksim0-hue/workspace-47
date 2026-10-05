# Historial de cambios

## 0.2.11

Actualización en desarrollo; consulta el resultado de CI del commit antes de instalar su APK.

- Nombre visible unificado como **Firma Mobile**, tanto dentro de la aplicación como bajo el icono.
- Versión visible sin el sufijo `optimized`; se conservan el código de versión y el identificador del paquete.
- La acción de bloqueo se conserva en el menú del certificado y se retira de los botones principales.
- Detección de región: reutilización de ubicaciones recientes válidas, un reintento acotado del servicio de geocodificación y mensajes de error diferenciados; la selección manual prevalece sobre resultados automáticos tardíos.
- Descripción e instrucciones principales del proyecto en español, con README adicional en inglés.
- Correcciones de transporte y ejecución de firma por lotes, bloqueo inmediato del certificado y revocación de caché fuera del hilo de interfaz.
- El cierre de sesión bloquea primero el certificado; la preparación automática de servicios Junta no elimina las cookies temporales de otros sitios.

La compatibilidad de los portales continúa limitada a las operaciones verificadas. Este registro no sustituye las evidencias del commit ni acredita presentación de trámites reales.
