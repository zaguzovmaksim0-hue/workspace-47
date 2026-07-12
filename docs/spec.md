# Junta Firma Mobile — especificación del producto

Fecha de la especificación: 2026-07-11
Estado: diseño aprobado; la implementación y el E2E aún no están validados

## 1. Objetivo

Junta Firma Mobile es un cliente Android nativo, no oficial y de uso personal,
para completar dentro de una sola aplicación la autenticación y la firma
electrónica del portal objetivo de la Junta de Andalucía.

La aplicación:

- se llama **Junta Firma Mobile**;
- usa el application ID `dev.junta.firmamobile`;
- muestra de forma visible `Cliente no oficial para uso personal`;
- no usa código, recursos, package IDs ni APK modificados de AutoFirma;
- no abre AutoFirma externa, Google Play ni un servidor WSS local;
- implementa únicamente el subconjunto de interoperabilidad que demuestre usar
  el portal objetivo.

URL inicial:

`https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs`

## 2. Plataforma y construcción

- Kotlin.
- Un módulo Android `app`.
- AndroidX y Material 3.
- `minSdk = 26`.
- `compileSdk = 36` y `targetSdk = 36`, correspondientes al Android 16 del
  dispositivo objetivo. Las versiones se verificarán contra documentación
  oficial antes de fijarlas en el catálogo de versiones.
- Gradle Wrapper incluido; construcción por CLI sin Android Studio.
- JDK compatible con la versión de Android Gradle Plugin seleccionada.
- APK debug para investigación y APK release no depurable.
- Sin dependencia obligatoria de Google Play Services.
- `android:allowBackup="false"` y reglas explícitas de exclusión de backup y
  device transfer.

Dispositivo primario verificado durante el diseño:

- Xiaomi/POCO, modelo reportado `24069PC21G`;
- Android 16, API 36;
- ABI `arm64-v8a`;
- HyperOS/MIUI;
- Termux nativo aarch64 como entorno de construcción.

## 3. Alcance funcional

### 3.1 Primer inicio y certificado

En el primer inicio se presenta una configuración breve, no el portal:

1. `Certificado digital` con selector SAF para `.p12`/`.pfx`.
2. Solicitud de contraseña en un control que no la transforma en `String`.
3. Validación completa del almacén PKCS#12.
4. Resumen con titular, fecha de expiración y emisor.
5. `Continuar` abre el WebView; `Elegir otro` descarta la selección.

El URI SAF y su permiso persistente se pueden conservar. El archivo no se
copia por defecto. No se conserva la contraseña ni una representación binaria
exportada de la clave privada. Tras una muerte de proceso o reinicio, la
identidad queda bloqueada y una nueva firma exige contraseña.

### 3.2 Navegación

La pantalla principal contiene:

- barra superior con atrás, recargar, título y menú;
- `TrustedJuntaWebView`;
- estado inferior del certificado;
- menú con cambio/bloqueo de certificado, borrado de sesión web, retorno a la
  página inicial, diagnóstico y acerca de.

La navegación normal usa `webView.canGoBack()`. El estado del WebView se guarda
y restaura en recreaciones de Activity. Los errores de red producen una vista
de error recuperable. Un error TLS siempre se cancela.

### 3.3 Confirmación de firma

Cada solicitud de firma crea un `PendingSignRequest` único y muestra una hoja
modal con sitio, certificado, formato y algoritmo. No existe firma automática.

Para `SHA1withRSA`, que ya se observó en la página pública, la hoja muestra una
advertencia clara de compatibilidad heredada. El algoritmo solo se habilita
para el origin y contrato exactos documentados; nunca se convierte en valor
predeterminado general.

Estados visibles:

- solicitud pendiente;
- `Firmando… No cierres la aplicación.`;
- `Firma completada`;
- error con código interno, reintento seguro y acceso a diagnóstico.

Un `requestId` se consume una sola vez. Cancelar, navegar fuera, recargar,
bloquear el certificado o completar la firma elimina la solicitud pendiente.

## 4. Arquitectura

### 4.1 Capas

1. **UI/state**: Compose Material 3, flujo de configuración, navegador,
   confirmación, progreso y diagnóstico.
2. **Browser boundary**: configuración segura de WebView, allowlist, navegación,
   bridge por mensajes y shim de compatibilidad instalado al inicio del
   documento.
3. **AutoFirma compatibility**: parser de URI, request router, almacenamiento
   efímero de solicitudes y contrato observado de `MiniApplet`.
4. **Certificate boundary**: SAF, carga PKCS#12, validación, resumen y sesión de
   clave solo en memoria.
5. **Signing**: coordinación, firma JCA, CAdES mediante biblioteca revisada y
   cliente tri-phase limitado al contrato confirmado.
6. **Network/session**: cliente HTTPS, validación SSRF, aislamiento de cookies
   por host y detección de sesión expirada.
7. **Security/diagnostics**: datos sensibles, almacenamiento opcional cifrado,
   Android Keystore y logger sanitizado.

Cada capa expone interfaces pequeñas para permitir pruebas sin WebView ni red
real. El dominio no depende de Activity, Compose ni objetos JavaScript.

### 4.2 Paquetes previstos

```text
dev.junta.firmamobile
├── MainActivity.kt
├── afirma/
├── browser/
├── certificate/
├── model/
├── network/
├── security/
├── signing/
└── ui/
```

Los nombres concretos solicitados en el encargo se mantendrán salvo que una
prueba demuestre que una responsabilidad debe dividirse; no se eliminará ningún
límite de seguridad por simplificación.

## 5. WebView y límites de origen

Origins HTTPS iniciales permitidos, sin wildcard:

- `https://www.juntadeandalucia.es`
- `https://sede.juntadeandalucia.es`
- `https://ssoweb.juntadeandalucia.es`
- `https://pfirma.juntadeandalucia.es`
- `https://ws024.juntadeandalucia.es`
- `https://ws050.juntadeandalucia.es`

Un nuevo host solo entra tras verificar propiedad oficial, registrar evidencia
en `docs/protocol-observations.md` y añadir una prueba.

Configuración obligatoria:

- JavaScript y DOM storage habilitados;
- cookies habilitadas;
- Safe Browsing inicializado cuando la implementación del sistema lo soporte;
- mixed content, cleartext y accesos de `file://` a red deshabilitados;
- debugging de WebView solo en debug;
- ventanas arbitrarias y popups rechazados;
- selector de archivos mediante SAF;
- SSL errors cancelados sin excepción.

`shouldOverrideUrlLoading` devuelve `false` para HTTPS permitido. Los enlaces
HTTP/HTTPS externos se envían al navegador del sistema después de validarlos.
`afirma://` se enruta internamente. `market://`, enlaces de Play a AutoFirma y
fallbacks equivalentes se bloquean, registrando
`PLAY_STORE_FALLBACK_INTERCEPTED`. `intent://` se analiza con `Intent.parseUri`:
solo un payload interno `afirma://` o un destino HTTP/HTTPS validado puede
continuar; intents de paquete, componente o esquema no reconocido se rechazan.

## 6. Bridge y shim de MiniApplet

No se usa `addJavascriptInterface`.

El bridge usa AndroidX WebKit:

- `WebViewCompat.addDocumentStartJavaScript` instala el shim antes del JS de la
  página;
- `WebViewCompat.addWebMessageListener` acepta mensajes solo de origins HTTPS
  explícitos;
- los mensajes son JSON versionado, con tamaño máximo, `requestId`, tipo y
  origin verificado tanto en JavaScript como en Kotlin.

El shim observa la asignación global de `MiniApplet` y envuelve únicamente los
métodos confirmados (`cargarMiniApplet`, `sign` y, si se observa,
`selectCertificate`). Conserva en una clausura JavaScript las funciones success
y error entregadas por la propia página. Android nunca recibe ni inventa el
nombre del callback. El resultado vuelve por mensaje al `requestId` activo y el
shim llama a la función original guardada.

La primera build de investigación registra únicamente nombres, longitudes y
hashes truncados de parámetros. Hasta confirmar el contrato runtime, el bridge
no envía una firma ni llama a endpoints tri-phase.

La correlación de investigación es cerrada y explícita: una rama con el mismo
UUID se marca `REQUEST_ID`; una navegación nativa top-level solo puede
asociarse mientras existe una única llamada `SIGN` abierta en el mismo
documento/origin y durante un máximo de 250 ms (`ACTIVE_CALL_WINDOW`). Nunca se
asocia una navegación anterior a una llamada posterior. Cada documento usa un
UUID efímero, una generación nativa y un origin HTTPS exacto; replay, TTL,
overflow, duplicate keys, rama/end desconocido o cambio ambiguo dejan la
correlación fail-closed hasta el documento siguiente, sin eviction. La rama
solo se publica tras su `MINIAPPLET_CALL_END`; un fallo posterior muestra el
marcador cerrado `PROTOCOL_CORRELATION_REJECTED`. El shim
común contiene hooks de observación inertes, pero el listener, recorder y
Activity que los consumen existen solo en debug. Ninguna capa registra el URI,
el UUID de request/documento ni payloads.

## 7. Certificados

`Pkcs12Loader` usa `KeyStore.getInstance("PKCS12")` y exige exactamente una
identidad seleccionable con `PrivateKeyEntry` y `X509Certificate`.

Validaciones:

- lectura completa dentro de un límite de tamaño;
- contraseña correcta;
- certificado vigente mediante `checkValidity`;
- `digitalSignature` permitido si existe `keyUsage`;
- cadena X.509 legible y ordenada;
- algoritmo RSA para el alcance inicial;
- correspondencia clave/certificado mediante firma y verificación de un nonce
  aleatorio, sin exportar la clave privada.

La contraseña se recibe como `CharArray`, se copia solo cuando una API lo exige
y todas las copias se rellenan con cero en `finally`. `UnlockedIdentity` nunca
expone bytes de clave. La sesión opcional de diez minutos, si se habilita,
reside exclusivamente en memoria y se invalida por temporizador, bloqueo
manual, periodo de background, presión de memoria o muerte de proceso.

## 8. Firma y tri-phase

La criptografía primitiva se delega a JCA y a una biblioteca CMS/CAdES mantenida
y compatible con Android. No se implementan RSA, ASN.1 ni CMS manualmente y no
se registra un provider global salvo evidencia de necesidad.

Alcance confirmado para iniciar la implementación:

- formato CAdES;
- RSA;
- `SHA1withRSA` únicamente como compatibilidad explícita del flujo de login
  observado, con advertencia;
- `SHA256withRSA` soportado por pruebas y habilitado cuando el request lo pida;
- `SHA512withRSA`, DES y otros formatos permanecen rechazados hasta una
  observación real y una prueba específica.

El flujo tri-phase se habilita solo cuando el capture runtime confirme request,
content types, encoding, pre-sign, post-sign y entrega final. Cada URL se valida
contra SSRF antes de usarla. Status HTTP 200 no implica éxito: se valida tipo,
tamaño y estructura de respuesta.

XML se procesa con DOCTYPE, external entities y DTD externos desactivados,
secure processing activado y límites estrictos de entrada.

## 9. Cookies y sesión

WebView y cliente nativo comparten una sesión lógica sin crear una cookie jar
global permisiva:

- antes de cada request, se toma el header de CookieManager únicamente para la
  URL HTTPS exacta;
- no se reenvía a un host distinto ni se registra;
- `Set-Cookie` se devuelve a CookieManager para la URL de origen y se llama
  `flush()`;
- cada redirect se vuelve a validar antes de seguirlo.

401/403, redirect a login o HTML de autenticación donde se esperaba el formato
de protocolo producen `SESSION_EXPIRED`.

## 10. Red y SSRF

`networkSecurityConfig` deshabilita cleartext y confía solo en CA del sistema.
No hay pinning sin estrategia de rotación, trust-all, CA de usuario, hostname
verifier permisivo ni bypass SSL.

Todo callback/server URL exige:

- esquema `https`;
- host exacto permitido;
- puerto 443 o puerto documentado explícitamente;
- ausencia de userinfo;
- nombre DNS, no localhost ni dirección IP privada/reservada;
- resolución y redirects revalidados para impedir DNS rebinding y escape de
  allowlist;
- límite de redirects, timeouts y límite de cuerpo.

## 11. Diagnóstico y privacidad

`SanitizedLogger` existe en debug y en release con distinto nivel, pero nunca
acepta secretos. Campos permitidos: evento, timestamp, host, operación,
algoritmo, formato, status, longitud, primeros ocho hex de SHA-256 y código de
error.

Campos prohibidos: contraseña, P12, clave, documento, `dat`, firma,
certificado completo, cookies/tokens y claves legacy. El export produce texto
sanitizado desde almacenamiento privado; permite copiar, exportar y borrar.

## 12. Errores

El modelo cerrado de errores incluye:

`CERT_FILE_UNREADABLE`, `CERT_PASSWORD_INVALID`, `CERT_NO_PRIVATE_KEY`,
`CERT_EXPIRED`, `AFIRMA_URI_INVALID`, `AFIRMA_OPERATION_UNSUPPORTED`,
`ORIGIN_NOT_ALLOWED`, `CALLBACK_URL_REJECTED`, `SESSION_EXPIRED`,
`PREFIRMA_HTTP_ERROR`, `PREFIRMA_FORMAT_INVALID`,
`LOCAL_SIGNATURE_FAILED`, `POSTFIRMA_HTTP_ERROR`,
`RESULT_DELIVERY_FAILED`, `USER_CANCELLED` y
`PLAY_STORE_FALLBACK_INTERCEPTED`.

Los detalles internos no se muestran a la página ni al usuario si contienen
datos sensibles.

## 13. Criterio de finalización

La construcción, los unit tests y una instalación exitosa son condiciones
necesarias, no prueba de finalización. El producto solo se declarará listo tras:

1. pasar todos los casos de `docs/test-plan.md`;
2. instalar y ejecutar en Android 16;
3. completar con interacción humana segura el E2E real usando un certificado
   de prueba autorizado;
4. comprobar que el portal acepta el resultado y continúa;
5. auditar logcat sin secretos;
6. verificar APK release con `apksigner` y `zipalign`;
7. entregar fuentes, APK, SHA-256, fingerprint del certificado de firma y un
   informe con limitaciones reales.

Si falta el certificado, una sesión del portal o una interacción de usuario,
el informe dirá exactamente qué tramo no se validó y la aplicación no se
marcará como terminada.
