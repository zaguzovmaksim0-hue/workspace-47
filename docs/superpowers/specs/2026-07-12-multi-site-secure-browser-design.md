# Junta Firma Mobile — diseño del navegador seguro multi-sitio

Fecha: 2026-07-12

Estado: diseño aprobado por la instrucción de aprobación automática del usuario

Entorno de aceptación: Termux/aarch64 y POCO F6 Pro con Android 16/API 36

## 1. Resultado del estudio previo

El diseño parte del commit `95e068b` (`feat: add secure Junta WebView flow`) en
la rama `feature/research-shell`. Los commits inmediatamente anteriores separan
la política de navegación/origin (`a159a5f`) y el flujo seguro de certificado
(`9183b43`). La suite actual tiene 91 unit tests verdes; compila los Android
tests, pasa lint y genera APK debug/release instalables mediante Gradle Wrapper
en Termux.

La implementación existente ya contiene:

- `JuntaOriginPolicy`, con seis hosts HTTPS exactos y sin wildcard;
- `JuntaNavigationPolicy`, `JuntaWebViewClient` y `JuntaWebChromeClient`;
- `TrustedJuntaWebView`, con JavaScript/DOM storage habilitados, mixed content
  y file access deshabilitados, Safe Browsing y third-party cookies bloqueadas;
- `WebMessageBridge`, `WebMessageProtocol`, `WebMessageRouter` y un script de
  document-start restringidos a origins Junta;
- `WebViewCookieBridge` y `WebViewStateHolder`;
- `BrowserScreen` Compose, todavía específico de Junta;
- `AfirmaUriParser`, certificado SAF/PKCS#12 y `CertificateSession` en memoria;
- tests unitarios e instrumentación compilable para estos límites.

La implementación todavía no contiene `SigningCoordinator`, motor CAdES,
cliente tri-phase ni una interfaz ejecutable de protocolo de firma. La firma
Junta no se considera lista: falta observar la rama runtime, implementar el
contrato confirmado y completar el E2E real.

El dispositivo usa `com.google.android.webview` 149.0.7827.160. El proyecto usa
AndroidX WebKit 1.16.0, cuya API incluye `MULTI_PROFILE`, `ProfileStore`,
`Profile.getCookieManager()` y `WebViewCompat.setProfile()`. No existe todavía
una medición ejecutada de `WebViewFeature.isFeatureSupported(MULTI_PROFILE)` en
el proceso de la app. Por tanto, este diseño no afirma aislamiento físico de
cookies y toma el perfil WebView predeterminado como baseline conservador.
Aunque el feature resulte disponible, no se cambiará el perfil del WebView
Junta antes de repetir su SSO/E2E: `setProfile()` debe ejecutarse antes de toda
navegación y cambiarlo exige crear otro WebView.

## 2. Objetivo y orden vinculante

El producto será un mini-navegador HTTPS limitado, no un clon de Chrome:

1. preservar el flujo Junta actual y sus tests;
2. completar primero el E2E real de Junta;
3. extraer límites genéricos sin reescribir certificado ni criptografía;
4. permitir navegación HTTPS browse-only, dirección, home, favoritos e
   historial seguro;
5. representar Junta como el primer `SiteProfile` de firma;
6. añadir como máximo un perfil de firma nuevo por ciclo de investigación,
   tests y E2E independiente.

Todo HTTPS que supere la política de URL puede abrirse. La mera disponibilidad
de TLS, la presencia en favoritos o la pertenencia a una administración no
habilitan firma. Un sitio sin perfil recibe WebView normal, sin bridge de firma,
sin acceso a `CertificateSession` y sin export de cookies al cliente nativo.

## 3. Enfoques considerados

### 3.1. Fachada compatible e introducción incremental — seleccionado

Se introducen `BrowserUrlPolicy`, `SiteProfileRegistry` y un controlador de
confianza junto a las clases existentes. `JuntaOriginPolicy` permanece como
fachada compatible que delega en `JuntaSiteProfile`; sus constantes y tests no
se debilitan. Los consumidores migran uno a uno. Junta se completa y valida
antes de activar navegación multi-sitio en producción.

Ventajas: cambios revisables, rollback por tarea, compatibilidad con DataStore,
tests Junta intactos y ninguna reescritura del certificado. Coste: durante la
migración conviven una fachada Junta y APIs genéricas de forma deliberada.

### 3.2. Sustitución total inmediata — rechazada

Reemplazar en un solo cambio `JuntaOriginPolicy`, clients, bridge, cookies,
estado y UI produciría una superficie de regresión demasiado grande antes del
E2E. También haría difícil atribuir un fallo del portal a la firma o al nuevo
navegador.

### 3.3. Un WebView/Profile físico por sitio desde el inicio — pospuesto

AndroidX ofrece perfiles independientes cuando `MULTI_PROFILE` está soportado,
pero el perfil debe asignarse antes de usar el WebView. Cambiar entre un origin
Junta y un redirect externo implicaría recrear WebView, coordinar historiales y
volver a validar SSO. La primera versión multi-sitio mantiene un solo perfil
físico y aplica aislamiento lógico estricto. Un experimento posterior podrá
adoptar perfiles físicos solo si el test runtime es verde y no rompe el E2E
Junta. No se publicitará aislamiento físico antes de esa evidencia.

## 4. Límites y componentes

### 4.1. URL y origin

`browser/BrowserUrlPolicy.kt` será el único parser de entradas de la barra y de
navegaciones top-level ordinarias. Producirá un `NormalizedHttpsUrl` o un error
cerrado. `network/TrustedOrigin.kt` conservará el modelo de origin exacto que
ahora vive junto a `JuntaOriginPolicy`; moverlo de archivo no cambia su paquete
ni su API.

Los resultados puros son:

```kotlin
data class NormalizedHttpsUrl(
    val serialized: String,
    val origin: TrustedOrigin,
    val path: String,
    val query: String?,
    val fragment: String?,
)

enum class BrowserUrlError {
    EMPTY,
    TOO_LONG,
    CONTROL_CHARACTER,
    UNSUPPORTED_SCHEME,
    USERINFO_NOT_ALLOWED,
    HOST_MISSING,
    HOST_NOT_CANONICAL,
    IP_LITERAL_NOT_ALLOWED,
    MALFORMED,
}

sealed interface BrowserUrlResult {
    data class Valid(val url: NormalizedHttpsUrl) : BrowserUrlResult
    data class Blocked(val error: BrowserUrlError) : BrowserUrlResult
}
```

Reglas de URL ordinaria:

- máximo 8.192 caracteres;
- se rechazan controles U+0000–U+001F y U+007F antes de normalizar;
- solo se eliminan espacios U+0020 iniciales/finales;
- un input sin esquema se acepta solo si tiene forma de hostname y recibe
  `https://`;
- solo `https` es válido; no existe búsqueda automática;
- se rechazan userinfo, host vacío, trailing dot, malformed percent encoding,
  IPv4/IPv6 literal, localhost y hostname no ASCII introducido directamente;
- `IDN.toASCII(..., USE_STD3_ASCII_RULES)` debe producir exactamente un host
  ASCII canónico; un A-label `xn--` puede abrirse solo browse-only y se muestra
  completo, pero nunca coincide con un trusted origin salvo literal exacto del
  perfil;
- un puerto HTTPS explícito es válido para browse-only; cada Site Profile fija
  por separado sus puertos permitidos;
- query y fragment pueden existir solo en la navegación transitoria. Nunca se
  guardan en favoritos, recientes o fallback de estado.

Errores de entrada muestran `Introduce una dirección web completa y segura.` y
no generan ninguna petición de red.

### 4.2. Site Profile

El paquete nuevo `site/` contiene los modelos de perfil. `SigningProtocolId` se
define una sola vez en `signing/SigningModels.kt` y el perfil lo importa; no hay
un segundo tipo o conversión por String:

```kotlin
@JvmInline value class SiteProfileId(val value: String)

enum class SiteSupportLevel {
    FULLY_VERIFIED,
    EXPERIMENTAL,
    BROWSE_ONLY,
}

enum class SigningRequestScheme {
    AFIRMA,
    INTENT_AFIRMA,
    WEB_MESSAGE,
}

data class SiteProfile(
    val id: SiteProfileId,
    val displayName: String,
    val version: Int,
    val homeUrl: NormalizedHttpsUrl,
    val topLevelOrigins: Set<TrustedOrigin>,
    val loginOrigins: Set<TrustedOrigin>,
    val bridgeOrigins: Set<TrustedOrigin>,
    val callbackOrigins: Set<TrustedOrigin>,
    val networkOrigins: Set<TrustedOrigin>,
    val requestSchemes: Set<SigningRequestScheme>,
    val signingProtocols: Set<SigningProtocolId>,
    val cookiePolicy: ProfileCookiePolicy,
    val confirmationPolicy: ConfirmationPolicy,
    val supportLevel: SiteSupportLevel,
)
```

`SiteProfileRegistry` es inmutable durante el proceso y se crea desde código
versionado. No hay botón ni almacenamiento que convierta un host arbitrario en
trusted. El matching usa scheme+host ASCII+puerto exactos, nunca suffixes ni
wildcards. Los conjuntos de login, bridge, callback y network son distintos:
pertenecer a uno no concede automáticamente los demás permisos.

Las políticas auxiliares y el registro tienen estas formas:

```kotlin
enum class OriginPurpose {
    TOP_LEVEL,
    LOGIN,
    BRIDGE,
    CALLBACK,
    NATIVE_NETWORK,
}

interface OriginPolicy {
    fun canonicalHttpsOrigin(url: NormalizedHttpsUrl): TrustedOrigin
    fun allows(profile: SiteProfile, origin: TrustedOrigin, purpose: OriginPurpose): Boolean
}

data class ProfileCookiePolicy(
    val nativeReadOrigins: Set<TrustedOrigin>,
    val nativeWriteOrigins: Set<TrustedOrigin>,
)

data class ConfirmationPolicy(
    val requireExplicitAction: Boolean,
    val showExperimentalWarning: Boolean,
)

interface SiteProfileRegistry {
    val profiles: List<SiteProfile>
    fun profileForTopLevel(origin: TrustedOrigin): SiteProfile?
    fun profile(id: SiteProfileId): SiteProfile?
    fun bridgeOriginRules(): Set<String>
}
```

Todos los profiles exigen `version >= 1`, ID ASCII estable y home URL contenida
en `topLevelOrigins`. Ningún top-level origin puede pertenecer a dos profiles.
Los origins de cookies son subconjuntos de `networkOrigins`. Un profile
`BROWSE_ONLY` tiene vacíos bridge/callback/network/requestSchemes/protocols y no
puede activar `CertificateSession`. Todo profile de firma requiere
`requireExplicitAction=true`; `EXPERIMENTAL` requiere además
`showExperimentalWarning=true`. El registro falla al construirse si una de
estas invariantes no se cumple.

`JuntaSiteProfile` conserva el start URL y los seis hosts actuales. Mientras no
exista E2E completo tendrá `EXPERIMENTAL`; el código y UI no pueden marcarlo
`FULLY_VERIFIED`. La promoción requiere evidencia de portal aceptando el
resultado, actualización de `protocol-observations.md`, regresiones y un commit
explícito. Aun siendo experimental, Junta es el único perfil con trabajo de
firma prioritario; los accesos oficiales restantes empiezan browse-only.

Para la primera migración, los seis origins actuales permanecen en
topLevel/login/bridge/callback/network exactamente como hoy, con HTTPS:443. Es
una copia compatible, no una nueva concesión. Tras el capture runtime se podrán
reducir conjuntos en una nueva versión del profile; nunca se ampliarán por
inferencia. `JuntaOriginPolicy.allowedHosts` y `webMessageOriginRules` siguen
exponiendo los mismos seis literales durante esa reducción gradual.

Los niveles significan hechos comprobados, no intención:

- `FULLY_VERIFIED`: apertura, login, reconocimiento, firma, callback y
  aceptación final del servidor han pasado un E2E real en el profile/version
  publicado;
- `EXPERIMENTAL`: el sitio/origin es oficial y una parte del protocolo está
  observada, pero falta al menos uno de esos gates; cada firma muestra warning;
- `BROWSE_ONLY`: solo navegación HTTPS; no hay bridge, adapter ni cookies
  nativas de firma.

`JuntaOriginPolicy` delegará en ese perfil y conservará `START_URL`,
`allowedHosts`, `webMessageOriginRules`, `isAllowed` y `originFor`, de modo que
los tests y llamadas existentes sigan compilando sin relajar expectativas.

### 4.3. Estado de confianza

`BrowserTrustController` mantiene un solo estado top-level:

```kotlin
sealed interface BrowserTrustState {
    data object Home : BrowserTrustState
    data class BrowseOnly(val url: NormalizedHttpsUrl) : BrowserTrustState
    data class Trusted(
        val profile: SiteProfile,
        val origin: TrustedOrigin,
        val navigationId: String,
    ) : BrowserTrustState
    data class Blocked(val reason: BrowserUrlError) : BrowserTrustState
}
```

Cada navegación top-level crea un `navigationId` aleatorio de sesión. El estado
se reevalúa en entrada manual, link, redirect, `onPageStarted`,
`onPageCommitVisible`, history Back/Forward y restore. Un iframe nunca cambia el
estado top-level.

Al salir de trusted hacia browse-only, antes de permitir la carga se cancela el
pending sign request, se incrementa la generación de navegación y se cierra el
acceso al signing layer. La UI muestra `Estás saliendo de un sitio de confianza.
La firma se desactivará.`. Una comprobación posterior en page-start/commit
corrige redirects que no pasaron por el callback previo. Volver a un trusted
origin crea un navigationId nuevo; nunca revive una solicitud anterior.

### 4.4. Navegación WebView

`BrowserNavigationPolicy` permite top-level HTTPS normalizado dentro del mismo
WebView, tanto con perfil como browse-only. Bloquea HTTP, file, content, data,
javascript, blob top-level, custom schemes, URI con credentials, malformed y
oversized.

`afirma:`, `intent:` y market/Play nunca se envían a Package Manager. Solo un
request compatible con el perfil top-level activo puede llegar al adapter. Un
intent con package, component, browser fallback arbitrario o esquema no
reconocido termina en error cerrado. `JuntaNavigationPolicy` permanece como
fachada de compatibilidad y conserva sus tests actuales, aunque la UI termine
usando la política genérica.

Popups y ventanas nuevas continúan bloqueados en la primera versión. No se crea
un segundo WebView oculto para resolver `target=_blank`. Un enlace que necesite
otra ventana debe ofrecer una URL HTTPS normal que el usuario pueda abrir o
copiar. Esto preserva el hardening actual y evita tabs fuera del modelo de
confianza.

### 4.5. Bridge origin-restricted

No se usa `addJavascriptInterface`. `WebMessageBridge` registra reglas solo
para la unión exacta de `bridgeOrigins` de perfiles compilados; inicialmente
solo Junta. Esto permite que un redirect hacia un trusted origin tenga el
objeto document-start disponible sin inyectarlo en origins desconocidos.

Cada mensaje exige además:

- source origin exacto y top-level origin del mismo perfil;
- `isMainFrame == true`;
- navigationId/generación activos;
- tipo JSON cerrado, versión, UUID y límite de tamaño;
- ausencia de duplicados críticos y comandos desconocidos;
- adapter registrado y permitido por el perfil;
- estado no cancelado ni consumido.

Un iframe Junta dentro de una página desconocida puede recibir técnicamente el
objeto restringido a su propio origin, pero `isMainFrame` y el estado top-level
lo rechazan antes de tocar `CertificateSession`. En un origin desconocido el
objeto no se inyecta. Cerrar el attachment elimina listener y script handler;
el router sigue siendo la defensa autoritativa ante referencias JavaScript que
una página antigua hubiera conservado.

Los tipos permitidos son capabilities query, sign request, sign cancel, sign
result y closed sign error. Capabilities no expone certificado, cookies ni
detalles de clave. El resultado contiene solo datos que el adapter del perfil
autoriza para el callback exacto.

Para el parser AutoFirma inicial se conservan operaciones `sign`, `selectcert`
y `websocket` ya reconocidas, pero solo `sign` puede avanzar cuando el adapter
Junta lo habilite. Los límites iniciales son 1.048.576 caracteres de URI, 64
parámetros, nombre ASCII de 64 caracteres, valor encoded individual de 786.432
caracteres, `dat` decodificado de 524.288 bytes y un único valor para cada campo
crítico. Se admiten como máximo tres URLs de protocolo, una por
`serverurl`/`rtservlet`/`stservlet`, y todas pasan origin policy. Algoritmo y
formato se validan contra el profile: Junta parte con `SHA1withRSA` legacy y
`SHA256withRSA`, ambos RSA+CAdES; ningún valor nuevo se habilita por fallback.
Una necesidad runtime mayor exige fixture, cambio versionado de profile y test,
no un límite abierto.

### 4.6. Protocolo y criptografía

`signing/SigningProtocolAdapter.kt` separa protocolo, sesión de certificado y
motor criptográfico:

```kotlin
interface SigningProtocolAdapter {
    val id: SigningProtocolId
    fun recognize(input: InterceptedSigningInput, profile: SiteProfile): Boolean
    fun normalize(input: InterceptedSigningInput, context: SigningContext): AdapterParseResult
    suspend fun prepare(request: NormalizedSignRequest): PreSignResult
    suspend fun complete(
        request: NormalizedSignRequest,
        localSignature: LocalSignature,
    ): ProtocolCompletionResult
}
```

El adapter nunca obtiene `PrivateKey`. `SigningCoordinator` solicita a
`LocalSignatureEngine` firmar bytes normalizados usando una identity todavía
desbloqueada. El engine no conoce WebView, cookies, callbacks ni SiteProfile.
`CertificateSession.identityForSigning()` permanece internal y solo el límite
de firma puede accederlo.

No se crean adapters vacíos. El primero será el adapter que exija el contrato
Junta observado. Reutilizará el parser `AfirmaUriParser`; tri-phase y callback
se implementarán solo con fixtures derivados de observación segura. Fire,
storage, websocket local y otros protocolos no se añaden por anticipación.

### 4.7. Confirmación de firma

Cada request normalizado crea un snapshot in-memory single-use con:

- requestId y fecha de expiración;
- profile ID+version y support level;
- top-level origin y navigationId;
- algoritmo, formato y descripción segura;
- identidad/certificate summary seleccionada;
- huella interna del payload para detectar sustitución, nunca persistida ni
  añadida a diagnóstico/historial.

La hoja muestra dominio exacto, perfil, soporte, operación, formato, algoritmo,
titular y descripción segura. `EXPERIMENTAL` añade warning visible. Solo el
botón `Firmar` transita a ejecución. Antes de usar la clave se vuelven a validar
origin, profile/version, navigationId, expiry, request fingerprint e identidad
desbloqueada. Cualquier cambio cancela. `Cancelar`, Back, Reload, navegación,
lock, background o process death eliminan el pending request.

### 4.8. Cookies y datos WebView

El WebView usa cookies first-party y bloquea third-party cookies. Mientras no
se demuestre aislamiento físico, `CookieManager` se documenta como almacén
global de los WebViews de la app. La seguridad de firma no depende de que ese
almacén sea físicamente separado:

- `ProfileCookieBridge` solo lee/escribe la URL exacta incluida en
  `cookiePolicy` y `networkOrigins` del perfil activo;
- no existe API para que browse-only solicite cookies al native layer;
- cada redirect nativo vuelve a validar perfil/origin;
- cookies nunca se loguean, persisten en DataStore ni cruzan adapters;
- salir de trusted cancela el uso nativo de cookies, aunque WebView conserve su
  sesión normal por reglas estándar de dominio.

`Limpiar datos de este sitio` elimina WebStorage del origin y, si
`GET_COOKIE_INFO` está soportado, enumera cookies del URL exacto y las expira
sin leerlas en logs. Si el provider no permite borrado exacto, la UI lo declara
y ofrece el borrado global separado. `Eliminar todos los datos locales` y
`Cerrar sesión` requieren confirmación y pueden usar `removeAllCookies`.
Nunca se borra todo al elegir la acción por sitio.

El runtime QA registra los booleanos `MULTI_PROFILE` y `GET_COOKIE_INFO`, nombre
y versión del provider, sin cookies ni URLs sensibles. Un resultado true no
cambia automáticamente la arquitectura: adoptar profiles físicos requiere un
design/commit posterior y repetir Junta SSO/E2E.

### 4.9. Historial, favoritos y saved state

El DataStore nuevo se llama `browser_data`; no se renombra ni migra
`certificate_reference`. Guarda como máximo 50 recientes y 100 favoritos. Cada
entrada contiene URL HTTPS normalizada sin query/fragment, host ASCII completo,
título saneado de hasta 120 caracteres y timestamp. No contiene forms,
password, P12, certificate, cookies, callback, token, signing URI, Base64,
documento ni digest de firma.

Antes de llamar `WebView.saveState`, `WebViewStateHolder` inspecciona
`copyBackForwardList()`. Solo guarda el Bundle completo si todas las entradas
son HTTPS, no tienen query/fragment y pasan la política de historial. En caso
contrario no serializa el history de WebView y conserva únicamente un fallback
normalizado sin query/fragment. Una navegación `afirma:`/`intent:` se intercepta
antes de entrar al history. Un pending signing request prohíbe guardar el
Bundle completo.

## 5. Experiencia de usuario

### 5.1. Entrada y home

Se conserva el flujo de certificado y se añade `Navegar sin certificado`; no
es obligatorio desbloquear una identity para browse-only. El home es Compose,
no HTML local, por lo que no exige habilitar file/content access.

Contiene:

- tarjeta prioritaria Junta que conserva el start URL existente;
- campo `Abrir otro sitio`;
- favoritos y recientes saneados;
- accesos browse-only verificados a sedes/categorías oficiales.

Las fuentes oficiales, revisadas el 2026-07-12, son:

| Acceso | URL inicial browse-only | Fuente de titularidad/portal |
|---|---|---|
| Junta de Andalucía | `https://www.juntadeandalucia.es/servicios/sede.html` | [Sede Electrónica General](https://www.juntadeandalucia.es/servicios/sede.html) |
| Administración General del Estado | `https://sede.administracion.gob.es/` | [Identificación de la Sede PAG](https://sede.administracion.gob.es/PAG_Sede/LaSedePAG/IdentificacionDeLaSede.html?idioma=es) |
| Seguridad Social | `https://sede.seg-social.gob.es/` | [Preguntas generales de la Sede](https://sede.seg-social.gob.es/wps/wcm/connect/sede/sede_contenidos/sede/inicio/preguntas%2Bfrecuentes/preguntas%2Bgenerales) |
| SEPE | `https://sede.sepe.gob.es/` | [Aviso legal de la Sede](https://sede.sepe.gob.es/portalSede/es/informacion/aviso-Legal-de-la-sede-electronica) |
| Agencia Tributaria | `https://sede.agenciatributaria.gob.es/` | [Aviso legal y dominios AEAT](https://sede.agenciatributaria.gob.es/Sede/condiciones-uso-sede-electronica/aviso-legal/aviso-legal.html) |
| DGT | `https://sede.dgt.gob.es/` | [Sede Electrónica DGT](https://sede.dgt.gob.es/) |
| Sedes municipales | `https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_Ayuntamientos.html` | [Directorio oficial de ayuntamientos](https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_Ayuntamientos.html) |

Las siete URLs respondieron HTTP 200 el 2026-07-12 después de seguir redirects
HTTPS. Esta comprobación autoriza solo el shortcut browse-only; la navegación
runtime vuelve a validar cada redirect y no hereda confianza de firma.

Ser acceso oficial o shortcut no lo convierte en signing profile. Todos salvo
el start URL Junta se presentan inicialmente como browse-only.

### 5.2. Browser chrome

La barra superior incluye Back, Forward, Reload/Stop, Home, dirección editable,
texto de confianza, menú e indicador de progreso. El host se muestra completo,
sin elisión del extremo derecho. El color es secundario a estas etiquetas:

- Junta FULLY_VERIFIED: `Sitio de confianza` / `Firma disponible`;
- perfil EXPERIMENTAL: `Sitio de confianza` / `Firma experimental`;
- browse-only/unknown: `Sitio normal` / `Certificado desactivado`;
- input bloqueado: `Dirección bloqueada`.

El address bar conserva el URL completo solo en memoria mientras se muestra. No
lo envía a un buscador y no persiste query/fragment. Paste pasa por exactamente
la misma normalización que typing.

El menú contiene abrir el HTTPS actual en navegador externo, favorito,
información/status, limpiar datos del sitio, cambiar/bloquear certificado,
cerrar sesión y borrar todos los browser data con confirmación independiente.
Abrir externamente cancela cualquier pending sign y nunca transfiere callbacks.

## 6. Trust boundaries y amenazas

```text
Entrada/clipboard no confiable
        │ BrowserUrlPolicy
        ▼
BrowserTrustController ───► WebView top-level no confiable
        │ perfil exacto                  │ iframes/redirects no confiables
        ▼                                ▼
SiteProfileRegistry ─────► WebMessage router (origin + main-frame + nav-id)
        │                                │ normalized request only
        ▼                                ▼
ProfileCookiePolicy              SigningCoordinator + confirmación
        │ cookie exacta                   │
        ▼                                ▼
HTTPS endpoints del perfil       CertificateSession → crypto core
```

Activos: P12, password, PrivateKey, identity, signing payload, firma, certificado
completo, cookies/SSO, pending callback, historial con tokens y evidencia de
support level.

Controles no negociables:

- ningún WebView/JavaScript recibe P12, password, key, file path, cookies,
  comandos, stack trace ni `PrivateKey.encoded`;
- trusted significa origin exacto de un perfil versionado, no certificado TLS;
- unknown HTTPS nunca alcanza signing/network-cookie APIs;
- bridge y router validan origin de forma independiente;
- cambio de top-level origin invalida pending work antes de firma;
- release sigue non-debuggable y WebView debugging depende de
  `BuildConfig.DEBUG`;
- mixed content, universal file access, SSL proceed y Play fallback permanecen
  prohibidos;
- los logs aceptan solo metadata tipada y nunca URL completa con query.

Riesgo residual documentado: JavaScript comprometido en un trusted origin puede
solicitar una operación permitida, pero no puede saltarse la confirmación ni
cambiar payload/origin después de ella. Un dispositivo root con control del
proceso queda fuera de la garantía de secreto en uso.

## 7. Errores y fail-closed

Los límites producen enums cerrados. La barra distingue input inválido de error
de red. Bridge/adapters reciben códigos como `ORIGIN_NOT_ALLOWED`,
`PROFILE_NOT_ACTIVE`, `REQUEST_EXPIRED`, `NAVIGATION_CHANGED`,
`CERTIFICATE_LOCKED`, `UNSUPPORTED_PROTOCOL` y `USER_CANCELLED`, sin detalles
internos. Un fallo al detectar perfil, parsear intent, validar callback, obtener
cookie o restaurar estado nunca degrada a firma genérica ni abre app externa.

## 8. Estrategia de migración y commits

La migración es secuencial y cada paso conserva suite completa verde:

1. terminar observación/signing/E2E Junta sobre los límites actuales;
2. introducir URL policy pura;
3. introducir models/registry y Junta profile con fachada compatible;
4. introducir state machine de confianza y navegación browse-only;
5. endurecer saved state e historial;
6. añadir address/home/UI y almacenamiento seguro;
7. ligar lifecycle del bridge y cookies al perfil;
8. ejecutar regresión Junta, runtime multi-site y release gates.

No se mezcla el design commit con production code. Cada tarea TDD tiene commit
propio y review gate. Un fallo E2E Junta detiene la activación multi-site, pero
no autoriza una reescritura amplia ni una declaración de compatibilidad.

## 9. Criterios de aceptación del diseño

- El package sigue siendo `dev.junta.firmamobile` y el DataStore de certificado
  conserva nombre/keys.
- Junta mantiene start URL, seis exact origins y tests actuales.
- Junta es el primer perfil prioritario y solo se promueve a FULLY_VERIFIED por
  E2E real aceptado por el portal.
- Cualquier HTTPS seguro puede abrirse browse-only; no HTTPS se bloquea.
- Unknown no tiene bridge, certificate ni native cookies.
- Dirección, trust status, home, history/favorites y menú no persisten secretos.
- Afirma/intent/Google Play nunca salen a Package Manager.
- Toda firma exige confirmación y revalidación del snapshot.
- El aislamiento físico de WebView se describe solo si una prueba runtime lo
  demuestra; el aislamiento lógico permanece obligatorio en cualquier caso.
- Unit, instrumentation, Termux builds, install gate, Junta E2E y release audit
  deben pasar antes de declarar el producto listo.
