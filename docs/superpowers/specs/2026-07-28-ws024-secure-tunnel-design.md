# Diseño: túnel TLS integrado para `ws024`

**Fecha:** 2026-07-28  
**Estado:** diseño aprobado para revisión escrita  
**Rama:** `design/ws024-secure-tunnel-20260728`

## 1. Problema confirmado

En determinadas redes, la aplicación puede cargar correctamente la Oficina Virtual en
`ws072.juntadeandalucia.es`, pero no puede establecer una conexión TCP/TLS con
`ws024.juntadeandalucia.es:443`, que aloja el servicio tri-phase de AutoFirma.

La observación en dispositivo separó el fallo de la criptografía y del bridge:

1. el portal entregó una petición MiniApplet válida;
2. la aplicación la normalizó y mostró la confirmación;
3. el codec tri-phase decodificó la petición;
4. el fallo ocurrió durante la conexión PRE, antes de recibir respuesta HTTP;
5. no se ejecutó la firma RSA local, no se envió POST y no se publicó callback.

El objetivo es que una persona usuaria no tenga que instalar Orbot, configurar una VPN,
seleccionar un proxy ni conocer detalles de red.

## 2. Objetivo

Añadir a Junta Firma un fallback de red automático y estrechamente limitado para las
operaciones tri-phase cuyo destino contractual sea `ws024.juntadeandalucia.es:443`.

La aplicación intentará primero la ruta directa. Solo cuando pueda demostrar que el
fallo ocurrió antes de enviar bytes HTTP al upstream podrá repetir la misma petición por
un túnel seguro administrado por el proyecto.

El túnel será invisible para el usuario y no se utilizará para WebView, navegación,
otros portales ni destinos arbitrarios.

## 3. No objetivos

Este trabajo no crea:

- una VPN general;
- un proxy HTTP abierto;
- un navegador remoto;
- un relay para cualquier host o puerto;
- un mecanismo para eludir errores HTTP, errores de sesión o rechazos de protocolo;
- soporte nuevo para Storage, Retrieve, cofirma, contrafirma o firma documental;
- un fallback para Carné Joven, Aragón SIRAW u otros perfiles;
- una forma de ocultar la aplicación frente al servidor oficial.

Tampoco se desactiva la validación TLS, no se aceptan certificados no confiables y no se
sustituye el endpoint oficial por un servidor que procese la firma.

## 4. Arquitectura elegida

### 4.1. Doble capa TLS

La conexión de fallback tendrá dos capas independientes:

1. **TLS exterior:** aplicación → relay del proyecto. Protege la autenticación del
   túnel y evita que la red local vea el protocolo de control.
2. **TLS interior:** aplicación → `ws024.juntadeandalucia.es`. Viaja como flujo opaco
   dentro del túnel. Mantiene SNI, cadena de confianza y verificación de hostname del
   servidor oficial.

El relay puede observar metadatos inevitables —IP de origen, hora, duración y volumen
aproximado—, pero no puede leer el certificado de usuario, la sesión tri-phase, los datos
PRE/POST ni la firma final.

### 4.2. Protocolo del relay

El relay expondrá un único servicio TLS en un dominio administrado por el proyecto. La
aplicación enviará, dentro del TLS exterior, un CONNECT HTTP/1.1 autenticado cuya
authority deberá ser exactamente `ws024.juntadeandalucia.es:443`. La versión del
protocolo de túnel viajará en una cabecera fija. Cualquier otro método, authority, puerto
o versión será rechazado antes de abrir un socket upstream.

Después de autorizar la solicitud:

1. el relay resolverá `ws024.juntadeandalucia.es` mediante su propia red;
2. rechazará direcciones privadas, loopback, link-local, multicast y rangos especiales;
3. abrirá un socket TCP únicamente al puerto 443;
4. devolverá `200 Connection Established`;
5. copiará bytes de forma bidireccional sin interpretarlos;
6. aplicará límites de inactividad, duración, concurrencia y volumen;
7. cerrará ambas direcciones ante timeout, error o exceso de límites.

No habrá parámetros de destino, redirecciones, reintentos del lado servidor ni caché.

### 4.3. Cliente Android

El cliente añadirá una abstracción de ruta al transporte tri-phase:

- `DIRECT`: transporte actual con resolución DNS aprobada, sin proxy, cookies,
  redirects, autenticadores ni retry automático;
- `SECURE_TUNNEL`: socket exterior TLS al relay, CONNECT fijo y posteriormente TLS
  interior a `ws024` con la misma verificación de hostname usada en la ruta directa.

La integración se limitará al `ProfileHttpTransport` de los perfiles cuyo endpoint
exacto sea uno de los servicios tri-phase aprobados en `ws024`. El resto del tráfico
mantendrá el transporte existente.

La implementación preferida es un `SocketFactory`/socket conectado especializado que
entregue a OkHttp un canal ya establecido a través del relay. OkHttp seguirá creando la
capa TLS interior y procesando HTTP; así no se duplica el codec de HTTP ni la validación
de respuestas.

## 5. Selección de ruta y seguridad de reintento

### 5.1. Regla direct-first

La primera operación en una red nueva utilizará la ruta directa. El fallback solo se
intentará cuando el transporte pueda clasificar el fallo como anterior al envío de la
petición HTTP:

- DNS no resuelto;
- conexión TCP no establecida;
- handshake TLS directo no completado;
- socket cerrado antes de empezar a escribir el body.

### 5.2. Casos en los que no se permite fallback automático

No se repetirá automáticamente cuando:

- comenzó la escritura del request body;
- el body se escribió y se perdió la respuesta;
- hubo timeout de lectura;
- el servidor respondió con cualquier código HTTP;
- se recibió HTML, content type inválido o respuesta demasiado grande;
- falló el parseo PRE/POST;
- cambió la navegación, el perfil, el certificado o la huella del payload;
- expiró la sesión;
- falló la firma local o la entrega del callback.

Esta separación evita duplicar un POST cuya recepción por el servidor sea incierta.

### 5.3. Clasificación de fallo

El resultado de red dejará de usar un único `NETWORK_ERROR` para todos los casos. Se
introducirá una fase segura y sin datos sensibles, por ejemplo:

- `DNS_BEFORE_CONNECT`;
- `TCP_BEFORE_WRITE`;
- `TLS_BEFORE_WRITE`;
- `WRITE_STARTED`;
- `READ_AFTER_WRITE`.

Solo las tres primeras serán elegibles para fallback.

### 5.4. Preferencia temporal por red

Cuando la ruta directa falle de forma segura y el túnel funcione, la aplicación podrá
preferir el túnel durante diez minutos para el identificador efímero de la red Android
activa. La preferencia será exclusivamente en memoria:

- no se guardará SSID, BSSID ni dirección IP;
- no sobrevivirá al reinicio del proceso;
- se eliminará al cambiar de `Network`;
- una comprobación directa posterior satisfactoria restablecerá la ruta normal.

Esto evita añadir quince segundos de espera a cada PRE y POST en una red conocida como
incompatible.

## 6. Autenticación y control de abuso

Un secreto estático dentro del APK no se considera secreto y no será el mecanismo de
producción.

### 6.1. QA

La variante QA podrá usar una credencial de desarrollo inyectada desde configuración
local o CI. La credencial:

- no se almacenará en Git;
- no aparecerá en recursos, BuildConfig público, logs ni informes;
- podrá revocarse sin publicar una nueva APK;
- solo habilitará el relay de prueba.

### 6.2. Producción

La activación en release requerirá credenciales de corta duración emitidas por el
backend tras validar la integridad de la aplicación. El diseño de producción asumirá
Play Integrity cuando la distribución sea mediante Google Play:

1. la aplicación solicita un token de integridad para un nonce del backend;
2. el backend valida package name, certificado de firma, frescura y verdict mínimo;
3. emite una credencial de túnel de corta duración;
4. la credencial queda vinculada al nonce y a una instalación pseudónima;
5. el relay aplica rate limits y rechaza replay o expiración.

Las builds instaladas fuera del canal de producción conservarán ruta directa y no
recibirán credenciales de producción. Si se decide una distribución sin Google Play,
la autenticación del relay necesitará un diseño separado basado en clave hardware y
registro del dispositivo; no se sustituirá por un token global embebido.

## 7. Backend

La implementación recomendada del relay es un servicio pequeño en Go por su soporte
directo de sockets, TLS, timeouts y copia bidireccional con límites claros.

Componentes:

- `TunnelListener`: termina TLS exterior y valida método/ruta;
- `CredentialVerifier`: valida la credencial QA o el token corto de producción;
- `FixedUpstreamDialer`: solo resuelve y conecta a `ws024:443`;
- `BidirectionalPump`: copia el flujo opaco con límites y half-close controlado;
- `RateLimiter`: limita por credencial e IP de forma conservadora;
- `SafeAudit`: registra únicamente código de resultado, duración redondeada y buckets
  de bytes, sin payload, cabeceras de autorización ni identificadores oficiales;
- `HealthEndpoint`: comprueba el proceso del relay, sin iniciar operaciones de firma.

El servicio deberá ejecutarse fuera de la red que bloquea `ws024`, con dominio propio,
TLS válido, salida TCP a `ws024:443` y una política explícita de retención de logs.

## 8. Integración con perfiles

El endpoint contractual oficial seguirá siendo `ws024`; los JSON de perfiles no se
reescribirán al dominio del relay. La ruta es una decisión del transporte, no una
modificación del protocolo MiniApplet.

Inicialmente el fallback se permitirá únicamente para:

- `junta-ofvirtual` → MiniApplet 1.5;
- el perfil Junta ya existente que usa MiniApplet 1.4, después de verificar que comparte
  las mismas garantías de retry y callback.

Cada perfil tendrá una bandera compilada de elegibilidad para túnel. La presencia del
host `ws024` por sí sola no habilitará automáticamente el fallback.

## 9. Errores y experiencia de usuario

La UI no mostrará términos como proxy, SOCKS, CONNECT o Tor.

Estados previstos:

- durante el fallback: `Conectando de forma segura con el servicio de firma…`;
- direct y tunnel fallan antes de escribir: `No se pudo conectar con el servicio de
  firma de la Junta. Inténtalo de nuevo más tarde.`;
- fallo incierto después de escribir: mensaje que evita reintento automático y pide
  reiniciar la operación desde el portal;
- credencial de túnel no disponible: la aplicación conserva la ruta directa y muestra
  una indisponibilidad, sin degradar TLS ni usar un proxy público.

Los códigos internos distinguirán, al menos:

- `DIRECT_CONNECT_UNAVAILABLE`;
- `TUNNEL_AUTH_UNAVAILABLE`;
- `TUNNEL_CONNECT_UNAVAILABLE`;
- `UPSTREAM_CONNECT_UNAVAILABLE`;
- `NETWORK_RESULT_UNCERTAIN`.

Ningún mensaje incluirá URL con path/query, certificado, nombre de persona, session ID,
challenge ni firma.

## 10. Observabilidad y privacidad

### Aplicación

La traza QA podrá registrar únicamente:

- ruta elegida;
- fase de conexión;
- código de fallo;
- duración por bucket;
- resultado PRE/POST/callback.

No registrará cuerpos, cookies, certificados, firmas, tokens, nombres de titulares ni
URLs completas.

### Relay

El relay no tendrá acceso al TLS interior. Los logs de aplicación se limitarán a:

- versión del protocolo del túnel;
- resultado de autenticación sin token;
- resultado de conexión al upstream;
- duración redondeada;
- bucket de bytes;
- código de cierre.

La IP de origen solo se usará para rate limiting y controles operativos, con retención
mínima documentada. También se revisarán los logs automáticos del proveedor de hosting.

## 11. Límites iniciales

Los valores exactos se configurarán en servidor y tendrán tests, con estos máximos de
diseño:

- un único upstream: `ws024.juntadeandalucia.es:443`;
- handshake exterior: 10 segundos;
- conexión upstream: 10 segundos;
- inactividad: 30 segundos;
- duración total: 90 segundos;
- máximo de bytes por dirección: 4 MiB;
- concurrencia por credencial: 2;
- sin reintento dentro del relay;
- sin persistencia del flujo.

Los límites de request/response HTTP existentes en Android seguirán siendo más
restrictivos cuando corresponda.

## 12. Pruebas

### 12.1. Android unitarias

- clasificación exacta de fallos antes y después de escribir;
- fallback solo en fases seguras;
- ausencia de fallback ante timeout de lectura, HTTP error o parse error;
- un único envío lógico del PRE y del POST;
- aislamiento por perfil y endpoint;
- route cache efímera por `Network`;
- cierre y borrado de buffers ante cancelación;
- mensajes y logs sanitizados;
- validación interior de hostname `ws024`.

### 12.2. Backend unitarias e integración

- rechazo de métodos, rutas, hosts y puertos no permitidos;
- rechazo de credencial ausente, expirada, repetida o inválida;
- bloqueo de DNS privado/especial;
- conexión exclusiva a `ws024:443`;
- límites de bytes, tiempo, concurrencia e inactividad;
- copia bidireccional y half-close;
- cero logging de authorization y payload;
- comportamiento fail-closed ante errores DNS/TCP/TLS.

### 12.3. Integración local

Un upstream TLS sintético representará `ws024`. Se probarán:

- direct success sin tocar el relay;
- direct connect failure + tunnel success;
- direct write-started failure sin fallback;
- tunnel authentication failure;
- relay conectado a upstream incorrecto y rechazo por TLS interior;
- cancelación de Activity y expiración de petición.

### 12.4. Dispositivo real

En QA y sobre la red que bloquea `ws024`:

1. la ruta directa falla antes de escribir;
2. el fallback se activa automáticamente;
3. PRE llega al servicio oficial;
4. la firma local se ejecuta una sola vez;
5. POST llega al servicio oficial;
6. el callback vuelve al portal;
7. la Oficina Virtual acepta el certificado e inicia sesión;
8. no aparecen datos sensibles en logs del teléfono ni del relay.

El perfil no se promoverá a `VERIFIED_E2E` hasta completar esta secuencia.

## 13. Despliegue gradual

1. **Diseño y contratos:** interfaces Android, protocolo relay y threat model.
2. **Relay local:** servidor y upstream sintético, sin Internet pública.
3. **Cliente QA:** fallback detrás de flag compilada, credencial de desarrollo.
4. **Servidor QA externo:** dominio y TLS válidos fuera de la red bloqueada.
5. **E2E en dispositivo:** PRE → firma → POST → callback → login.
6. **Hardening:** rate limits, rotación, métricas, revisión de privacidad y carga.
7. **Producción:** Play Integrity, credenciales cortas y activación release.

Cada fase tendrá su propio commit y gate. La activación release no se incluirá en el
primer milestone.

## 14. Rollback

El cliente conservará siempre la ruta directa. El fallback estará controlado por una
política compilada que enumera perfiles y destinos exactos. El backend podrá desactivar
el servicio dejando de emitir o aceptar credenciales, pero ninguna respuesta remota
podrá añadir hosts, puertos, perfiles ni operaciones.

Ante incidente:

- se revocan credenciales;
- se desactiva el endpoint del relay;
- la aplicación vuelve a direct-only;
- no se modifica el perfil oficial ni el bridge MiniApplet;
- no se necesita borrar certificados del usuario.

## 15. Criterios de aceptación del primer milestone

El primer milestone se considera completo cuando:

- existe un relay local que solo permite el upstream fijo;
- el cliente Android puede crear TLS interior validado sobre el túnel;
- direct-first y fallback seguro están cubiertos por tests;
- nunca se reintenta después de empezar a escribir el request;
- debug y QA pasan unit tests, lint y build;
- los tests demuestran que el relay no ve ni registra el contenido interior;
- el fallback sigue desactivado en release;
- la documentación identifica claramente que un E2E real requiere un servidor externo
  administrado por el proyecto.

## 16. Riesgos residuales

- El hosting y la red del relay ven IP, horarios y volumen aproximado.
- Un servidor comprometido puede cortar o retrasar tráfico, aunque no puede falsificar
  `ws024` sin superar la validación TLS interior.
- Play Integrity introduce dependencia del canal de Google Play para producción.
- El túnel no resuelve una caída real de `ws024`; solo una ruta local bloqueada.
- Una respuesta perdida después de escribir no puede repetirse automáticamente sin
  riesgo de duplicación.
- El proyecto asume costes y operación continuada de un backend, aunque el usuario final
  no instala ni configura componentes adicionales.
