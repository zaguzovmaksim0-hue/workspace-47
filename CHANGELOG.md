# Historial de cambios

Cada versión es una actualización en desarrollo: consulta el resultado de CI del commit antes de instalar su APK. Los APK del canal diario se firman con la clave persistente `CN=Firma Mobile Daily APK` (SHA-256 `f1bcad77c90bab402ac9671b0ac6c034cc1d916d4561f65e480740021e5d6971`); consulta [la firma del APK y la migración desde otras firmas](docs/instalacion.md#firma-del-apk-y-actualizaciones).

## 0.2.22 (24) — 2026-11-10

- Catálogo: las sedes con un perfil activo y revisado se abren directamente en «Modo específico del portal». Las direcciones sin perfil y la navegación universal mantienen su modo durante toda la sesión.
- HTTPS: se admiten los puertos públicos 8443 y 4443 y el paso seguro desde HTTP. El transporte nativo puede completar cadenas con intermediarios incluidos y revisados, conservando la validación de confianza y del nombre del servidor.
- Firma PDF: límites explícitos de 8 MiB de entrada y 10 MiB de salida, con buffers y memoria temporal acotados. Las pruebas sintéticas no sustituyen la medición de memoria en el teléfono.
- Solicitudes web: los iframe necesitan un origen y un propietario comprobados; las ventanas emergentes pasan por confirmación. Los protocolos externos no implementados muestran motivos concretos, sin atribuirles un perfil de confianza.
- Compatibilidad: entrada de Soria corregida, transporte presign/postsign de Lugo con diagnóstico de estado HTTP sin datos sensibles y conservación de parámetros repetidos en ventanas emergentes. La devolución autenticada de Funciona sigue pendiente de evidencia del protocolo.
- Mantenimiento: estado común de operaciones y separación del bloque visual de avisos/carga del navegador; actualización verificada a Gradle 9.8.2 conservando el bootstrap de Termux. CI separa las comprobaciones Android prolongadas y mantiene sus controles obligatorios.
- Versión y distribución: fuente única 0.2.22 / código 24 y mínimo de compilación actualizado. El APK de uso diario requiere el proceso de firma persistente y CI/Security satisfactorios sobre el commit exacto.
- Alcance de comprobación: los cambios se verifican con pruebas locales y CI. No se han ejecutado E2E reales de autenticación o firma en portales; no se declara compatibilidad universal.

## 0.2.21 (23) — 2026-11-04

- AutoFirma en navegación general: si una sede del catálogo con modo específico revisado envía una solicitud que el motor universal no puede atender (por ejemplo, el acceso con certificado de la Oficina Virtual de la Junta de Andalucía, cuyo MiniApplet no indica dónde devolver la firma), el aviso ofrece «Modo específico del portal» y lo abre desde su página de entrada con el perfil del catálogo. El aviso muestra además el código técnico del rechazo.

## 0.2.20 (22) — 2026-11-03

- Nuevo diseño «F», inspirado en un aparato físico: cuerpo gris cálido, teclas con relieve que se hunden al pulsarlas, LEDs de estado y una pantalla oscura con cifras. Se retira la fuente Bebas Neue: la interfaz usa la tipografía del sistema.
- Inicio: el certificado es una tarjeta con chip que entra en un lector; la pantalla muestra los días restantes, la fecha de caducidad y una escala de validez. Tecla naranja principal, teclas redondas «firmar», «verificar», «diario» y «avisos», y tecla «cambiar» para elegir otro certificado.
- Catálogo: tecla de volver, contador de servicios en una pantalla pequeña, selector de región, buscador empotrado con tecla «Pegar» y filas con LED de verificación y tecla de favorito; leyenda «verificado / sin verificar».
- Nuevo icono de la aplicación: una tecla naranja con «F» y un LED verde, con capa monocroma para los iconos temáticos.

## 0.2.19 (21) — 2026-11-03

- Nuevo diseño «Mosaico», más colorido: cabeceras con degradado y estrellas de azulejo, saludo según la hora del día y fondo con manchas de color que sigue el tema claro u oscuro.
- Inicio: el certificado aparece como una tarjeta de identidad (listo, bloqueado o desbloqueando) con barra de validez y días restantes; bloquear, elegir otro u olvidar pasan al menú «⋯» y las herramientas se muestran en mosaico.
- Catálogo: la región es un chip dentro de la cabecera, el buscador flota sobre ella, las secciones tienen color e iniciales, toda la tarjeta abre el sitio y la estrella de favorito se anima.
- Nuevo icono de la aplicación con capa monocroma para los iconos temáticos, y barras del sistema que siguen el tema.

## 0.2.18 (20) — 2026-11-02

- Diseño renovado en toda la aplicación: nueva paleta clara y oscura, esquinas redondeadas, tipografía más legible e iconos propios.
- Inicio reorganizado: tarjeta del certificado, acceso al navegador y secciones «Herramientas» y «Ajustes e información».
- Catálogo más limpio: tarjetas con una sola acción, insignia solo para sedes verificadas y selector de región simplificado.
- Información legal con navegación clara y versión visible; se retiran de la interfaz el informe de compatibilidad y el estado técnico de WebAuthn.
- Verificación sin conexión con el resultado destacado y las notas plegadas en «Más información»; diario de comprobaciones más legible, con exportación «Exportar diario (JSON)».
- Navegador: menú con iconos unificados, franja del certificado y aviso de navegación general más discretos; buscador del catálogo sin etiqueta flotante.

## 0.2.17 (19) — 2026-11-02

- AutoFirma: `expPolicy` ampliado para las firmas locales CAdES y PAdES (#564).

## 0.2.16 (18) — 2026-11-02

- Navegador: precalentamiento de la sesión de IFEF antes de abrir enlaces directos en frío, tolerante a su redirección y a los cambios de navegación (#561).

## 0.2.15 (17) — 2026-11-01

- Estilo visual unificado del catálogo y la pantalla de inicio (#559).
- La comprobación de documento en blanco se vincula a la navegación actual (#558).

## 0.2.14 (16) — 2026-11-01

- AutoFirma universal en sitios HTTPS públicos y correcciones para IFEF (#556).
- La 0.2.13 (15) no llegó a `main`: el código de versión pasó de 14 a 16.

## 0.2.12 (14) — 2026-10-30

- Catálogo con búsqueda primero y campo de dirección inteligente (#552), filtro de secciones verificadas (#504) y fecha de la última verificación de cada portal (#413).
- AutoFirma: relay para servlets AutoScript bloqueados por CSP (#548–#551), filtros de certificado `nonexpired`, `authcert` y cualificado (#547) y relleno del último fragmento del resultado DES (#545).
- Certificados: catálogo de certificados guardados (#455, #463, #465, #466, #503), elección de entrada en archivos PKCS#12 con varias claves (#479, #480), identidades EC P-256/P-384 para firmar archivos locales, con rechazo seguro en portales (#474, #476, #477), e importador verificado a AndroidKeyStore/StrongBox (#443).
- Verificación local de firmas PDF existentes (#451), XAdES `.xsig` (#539) y CMS con exportación SAF (#399, #422); vista previa local del PDF en el consentimiento PAdES (#411).
- Red: cadenas de servidor completadas con intermedios incluidos, como FNMT ACCOMP (#546).
- Recursos en inglés, catalán, gallego y euskera (#387) y más textos localizados (#410, #471, #505).
- Perfiles de portal con orígenes y contratos TLS fijados, y catálogo de perfiles versionado (esquema v2) empaquetado como recurso comprobado (#509–#531, #533, #540, #544).
- APK diario firmado con una clave persistente y fijada, solo después de CI y seguridad verdes en `main`.

## 0.2.11 (13)

- Nombre visible unificado como **Firma Mobile**, tanto dentro de la aplicación como bajo el icono.
- Versión visible sin el sufijo `optimized`; se conservan el código de versión y el identificador del paquete.
- La acción de bloqueo se conserva en el menú del certificado y se retira de los botones principales.
- Detección de región: reutilización de ubicaciones recientes válidas, un reintento acotado del servicio de geocodificación y mensajes de error diferenciados; la selección manual prevalece sobre resultados automáticos tardíos.
- Descripción e instrucciones principales del proyecto en español, con README adicional en inglés.
- Correcciones de transporte y ejecución de firma por lotes, bloqueo inmediato del certificado y revocación de caché fuera del hilo de interfaz.
- El cierre de sesión bloquea primero el certificado; la preparación automática de servicios Junta no elimina las cookies temporales de otros sitios.

La compatibilidad de los portales continúa limitada a las operaciones verificadas. Este registro no sustituye las evidencias del commit ni acredita presentación de trámites reales.