# Diseño del cliente universal seguro para sedes públicas españolas

Fecha: 2026-07-15

Estado: diseño previo a producción; bloqueado por el gate manual de regresión
Junta descrito en la sección 2

Ámbito: evolución incremental de `dev.junta.firmamobile` sobre la rama
`feature/research-shell`, sin proyecto nuevo, cambio de package, rebranding ni
reescritura del contorno de certificado.

Este documento sustituye, cuando exista conflicto, al diseño multi-sitio del
2026-07-12. Los diseños anteriores continúan explicando el origen de las
clases Junta, pero no autorizan perfiles de firma nuevos.

## 1. Resultado y límites

El producto será un cliente HTTPS limitado con tres separaciones obligatorias:

1. un motor de contratos oficiales AutoFirma;
2. un catálogo local y estricto de perfiles concretos;
3. navegación `BROWSE_ONLY` para cualquier HTTPS desconocido que supere la
   política general de URL.

Un sitio no obtiene confianza por terminar en `.es`, pertenecer a una
administración, usar un certificado TLS válido ni cargar AutoScript. Solo una
coincidencia exacta con un profile habilitado puede activar una capability.

Quedan fuera de esta fase:

- Cl@ve Firma y cualquier firma en nube;
- servicios remotos FNMT no documentados para integración;
- DNIe NFC, smart cards, tokens hardware y HSM;
- servidor WebSocket localhost emulado en Android;
- catálogo remoto, aunque esté servido por HTTPS;
- wildcard de origin o endpoint;
- navegador general equivalente a Chrome;
- cambio de launcher, nombre o identidad visual.

La matriz de evidencia vinculante es
`docs/compatibility/spanish-government-signing-matrix.md`. Un status de
compatibilidad informa lo probado; no habilita código por sí mismo.

## 2. Baseline y gate de no regresión

El baseline documental es el commit `8862284` y conserva el contorno actual:

- origin iniciador Junta:
  `https://www.juntadeandalucia.es`;
- endpoint tri-phase exacto:
  `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService`;
- operación `MiniApplet.sign`;
- CAdES explícito;
- SHA1withRSA marcado como compatibilidad heredada;
- callback legado de éxito con firma/certificado y error con tipo/mensaje;
- confirmación nativa antes de firmar;
- PRE, PKCS#1 local y POST sin exportar la clave privada.

Las suites automatizadas y la instrumentación del dispositivo están verdes,
pero el portal todavía no ha aceptado un E2E real de esta build exacta. Por
tanto:

- Junta sigue `EXPERIMENTAL`;
- no se cambia comportamiento production de navegación, bridge, firma,
  certificado, cookies ni red hasta completar el gate manual;
- sí se permiten investigación read-only, documentos, fixtures redactadas y
  planes;
- un fallo del E2E se diagnostica y corrige primero en el contorno Junta; no se
  oculta mediante la arquitectura genérica.

El gate exige selección SAF y contraseña dentro de la app, confirmación manual
y aceptación del portal. Contraseña, payload, certificado, firma, challenge y
cookies no se capturan ni se introducen por shell/ADB/UIAutomator.

## 3. Fronteras de seguridad

Los activos protegidos son el PKCS#12, contraseña, `PrivateKey`, cadena de
certificados, payload, PRE/POST, callback, cookies, autenticación TLS cliente y
estado de correlación. Los adversarios incluidos son:

- página HTTPS desconocida o portal oficial todavía no perfilado;
- iframe hostil dentro de una página permitida;
- redirect a otro origin o endpoint;
- URI `afirma:`/`intent:` manipulada;
- parámetro de página que intenta sustituir el endpoint;
- respuesta de red malformada, enorme, HTML de login o redirect;
- replay después de reload, Back, cambio de profile o recreación de proceso;
- carrera entre dos invocaciones o dos `ClientCertRequest`;
- catálogo corrupto o con campos/protocol IDs desconocidos;
- JavaScript que intenta elegir el request ID o inyectar código de callback.

Invariantes:

1. El código nativo es autoridad de profile, origin top-level, epoch,
   request ID, TTL y estado terminal.
2. Una capability no se hereda entre origins, profiles, operaciones ni
   adapters.
3. Todo estado sensible es efímero, de propietario único y se borra en cada
   transición terminal.
4. Toda ambigüedad falla cerrada; no existe fallback a otro algoritmo,
   formato, endpoint, callback o transporte.
5. Ningún adapter recibe una API para obtener o serializar `PrivateKey`.
6. El motor nativo nunca evalúa código callback proporcionado por la página.

## 4. Vista de componentes

```text
URL / navegación top-level
        |
        v
BrowserUrlPolicy --> SiteProfileRegistry --> BrowserTrustController
                                              |      |       |
                                   BROWSE_ONLY |      |       | BLOCKED/EXTERNAL
                                              v      v       v
                                       BridgeGate  ClientAuthGate
                                              |      |
                                              v      v
                                  SigningCoordinator  ClientCertCoordinator
                                   |       |       |          |
                              adapter  local key  network   exact request
                                   |      boundary   policy  proceed/cancel
                                   v
                             typed result channel
```

`CertificateSession` y `LocalSignatureEngine` permanecen como frontera de
clave. WebView, adapters, catálogo y UI solo reciben resumen de certificado o
handles efímeros controlados por coordinadores.

## 5. Catálogo local y SiteProfileRegistry

### 5.1. Formato y versionado

El catálogo inicial será un recurso bundled, por ejemplo
`app/src/main/res/raw/site_profiles_v1.json`, protegido por la firma del APK.
No se consulta configuración remota. El parser usa las primitivas JSON ya
presentes en el proyecto y aplica validación estricta; no se añade una
dependencia mientras no exista una necesidad demostrada.

El envelope contiene únicamente:

```kotlin
data class SiteProfileCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val profiles: List<SiteProfile>,
)
```

Solo se acepta `schemaVersion == 1`. Todo campo desconocido, duplicado, tipo
incorrecto, enum desconocido, profile ID repetido o protocol ID no registrado
invalida el catálogo completo. La app arranca en modo seguro sin profiles de
firma y muestra un error local; nunca intenta «salvar» entradas válidas de un
catálogo ambiguo.

Un test compara la lista cerrada de keys en cada nivel. Release no contiene
URL de actualización, downloader ni override desde DataStore, intent o query.

### 5.2. Modelo de profile

El modelo conceptual es:

```kotlin
data class SiteProfile(
    val profileId: ProfileId,
    val profileVersion: Int,
    val displayName: String,
    val compatibilityStatus: CompatibilityStatus,
    val activation: ProfileActivation,
    val initiatorOrigins: Set<ExactOrigin>,
    val redirectOrigins: Set<ExactOrigin>,
    val trustedBrowseOrigins: Set<ExactOrigin>,
    val endpoints: Map<EndpointId, ProfileEndpoint>,
    val operationPolicies: Map<ProtocolOperation, OperationPolicy>,
    val capabilities: Set<Capability>,
    val clientAuthPolicy: ClientAuthPolicy?,
    val certificateRules: CertificateFilterRules,
    val evidence: List<EvidenceReference>,
)
```

`CompatibilityStatus` usa exactamente `VERIFIED_E2E`,
`VERIFIED_CONTRACT`, `EXPERIMENTAL`, `BROWSE_ONLY`, `UNSUPPORTED`.
`ProfileActivation` es independiente: `DISABLED`, `QA_ONLY`, `ENABLED`.
Así, un JS `VERIFIED_CONTRACT` nuevo sigue sin acceso al certificado hasta un
commit explícito de activación y sus gates. `QA_ONLY` se compila únicamente en
debug/QA y nunca activa bridge, firma o client-auth en release; un artifact
test inspecciona el catálogo release. El ID estable del profile Junta sigue
siendo `junta-andalucia`, igual que en el código production actual.

Validación cerrada:

- `profileId` ASCII estable, máximo 64, sin significado de dominio;
- `profileVersion >= 1` y aumenta ante cualquier cambio de confianza;
- origins serializados como `https://host[:port]`, sin path, query, fragment,
  userinfo, wildcard, IP literal, localhost ni trailing dot;
- host IDNA convertido y comparado en ASCII canónico; un A-label no obtiene
  confianza salvo coincidencia literal catalogada;
- puerto omitido equivale únicamente a 443; otro puerto requiere valor exacto
  y evidencia específica;
- `initiatorOrigins` pueden iniciar solo operaciones declaradas;
- `redirectOrigins` permiten navegación únicamente como transición del
  profile activo; abiertos directamente se resuelven como `BROWSE_ONLY` y
  nunca heredan bridge/certificado;
- cada endpoint fija purpose, origin, path, puerto, método, content-types,
  tamaño y política de redirect; no se construye desde parámetros de página;
- IDs de endpoint son únicos dentro del catálogo y toda referencia debe
  resolver al mismo profile;
- callback, operación, packaging, modo, formato, algoritmo y properties deben
  ser compatibles con el adapter registrado;
- `LEGACY_SHA1` es obligatorio si aparece SHA-1;
- `CLIENT_TLS_AUTH` exige `clientAuthPolicy`; una policy sin capability también
  es inválida;
- perfiles `BROWSE_ONLY` o `UNSUPPORTED` no pueden declarar adapter, endpoint
  sensible ni capability de certificado/firma;
- los sets de roles de origin son disjuntos dentro de un profile y ningún
  origin/autoridad puede aparecer en dos profiles; cualquier solapamiento
  invalida todo el catálogo. Un futuro SSO compartido exige un tipo browse-only
  explícito y una revisión separada, no una excepción implícita.

Las referencias de evidencia contienen URL pública y fecha de revisión, nunca
payload, query efímera ni datos personales.

### 5.3. Endpoints tipados

```kotlin
enum class EndpointPurpose { TRIPHASE, STORAGE, RETRIEVE, PORTAL_RESULT }

data class ProfileEndpoint(
    val endpointId: EndpointId,
    val purpose: EndpointPurpose,
    val url: ExactHttpsUrl,
    val method: HttpMethod,
    val requestContentTypes: Set<String>,
    val responseContentTypes: Set<String>,
    val maxRequestBytes: Int,
    val maxResponseBytes: Int,
    val redirects: RedirectPolicy = RedirectPolicy.DENY,
)
```

`EndpointId` es ASCII cerrado, único y sin semántica de URL. Un request solo
referencia un endpoint por ese ID tipado. `serverUrl`, servlet
URL o callback URL recibidos de una página deben coincidir byte a byte tras
normalización con el endpoint del profile; no lo sustituyen.

### 5.4. Policy de operación y extraProperties

Formato/algoritmo no son sets amplios aplicables a cualquier operación. Cada
operación tiene una policy completa:

```kotlin
sealed interface OperationPolicy {
    val inputAdapterId: ProtocolInputAdapterId
    val callbackContractId: CallbackContractId
    val capabilities: Set<Capability>
}

data class SignOperationPolicy(
    override val inputAdapterId: ProtocolInputAdapterId,
    override val callbackContractId: CallbackContractId,
    override val capabilities: Set<Capability>,
    val format: SigningFormat,
    val packaging: SignaturePackaging, // ATTACHED o DETACHED
    val mode: SignatureMode,            // IMPLICIT o EXPLICIT
    val algorithm: SigningAlgorithm,
    val endpointIds: Set<EndpointId>,
    val extraProperties: ExtraPropertyPolicy,
) : OperationPolicy

data class CertificateSelectionOperationPolicy(
    override val inputAdapterId: ProtocolInputAdapterId,
    override val callbackContractId: CallbackContractId,
    override val capabilities: Set<Capability>,
    val certificateRules: CertificateFilterRules,
    val allowedPageFilterSyntax: Set<CertificateFilterSyntax>,
) : OperationPolicy

data class ExtraPropertyPolicy(
    val fixed: Map<PropertyKey, TypedPropertyValue>,
    val allowed: Map<PropertyKey, PropertyRule>,
    val rejectUnknown: Boolean = true,
)
```

El parser nunca reenvía un mapa `extraProperties` procedente de la página. Las
keys fijas se reconstruyen desde el profile; una key permitida tiene tipo,
longitud, enum/rango y normalización cerrados; cualquier key desconocida,
duplicada o con valor distinto del fijo rechaza todo el request. En particular
`serverUrl`, `documentId`, mode, format, policy y filtros no pueden ampliar el
profile. Antes de generalizar Junta se añade un test de regresión que demuestra
qué properties exactas necesita; el comportamiento actual de reenviar
properties restantes no se hereda al motor común.

La policy de selección tampoco acepta filtros libres: solo sintaxis catalogada
y tipada, siempre intersectada con `certificateRules`. Un filtro de página no
puede ampliar certificados, cambiar operación ni convertirse en firma. Si no
puede parsearse exactamente, se rechaza la selección.

## 6. Modelo de confianza y capabilities

### 6.1. Trust modes

| Modo | Significado | Acceso sensible |
| --- | --- | --- |
| `TRUSTED_SIGNING` | Origin iniciador exacto, profile activo y operación/adapters permitidos | Solo tras request válido y confirmación |
| `TRUSTED_CLIENT_AUTH` | Primer handshake de un WebView one-shot vacío hacia una autoridad exacta | Solo para un `ClientCertRequest` confirmado |
| `TRUSTED_BROWSE` | Redirect/origin auxiliar exacto de un profile | Ninguno |
| `BROWSE_ONLY` | HTTPS válido sin profile de confianza | Ninguno |
| `EXTERNAL_ONLY` | HTTPS válido que la policy decide no embeber | Ninguno; solo intent HTTPS explícito |
| `BLOCKED` | URL/esquema/estado inválido | Ninguno |

El trust mode describe la navegación actual, no el status de marketing. La UI
muestra ambos por separado.

`BrowserTrustController` resuelve siempre desde el top-level nativo. Un iframe
no cambia profile ni mode. Cada cambio de profile, salida de un origin
iniciador, reload, Back/Forward, nuevo documento, restore o recreación:

- cancela y borra pending signing request;
- cierra request correlation y reply channel;
- borra callback/certificate-selection/tri-phase state;
- pone a cero payloads y firmas temporales;
- invalida la decisión/petición client-auth;
- incrementa el epoch nativo;
- entra en el barrier asíncrono de `WebView.clearClientCertPreferences` cuando
  cambia el profile, se bloquea la identidad o se borra la sesión web; ninguna
  navegación nueva comienza hasta completarlo.

Las cookies del WebView nunca se exportan a un adapter o a HTTPS nativo por
defecto. El cambio de profile no promete aislamiento físico del cookie jar;
se mantiene la separación lógica y se ofrece borrado de sesión. Cualquier
futuro forwarding de cookie exige evidencia, campo de catálogo y revisión de
seguridad separados.

### 6.2. Capabilities

La lista cerrada inicial es:

```text
SIGN
SELECT_CERTIFICATE
CLIENT_TLS_AUTH
TRIPHASE
STORAGE_RETRIEVE
AUTOFIRMA_URI
MINIAPPLET_CALLBACK
LOCAL_CADES
LOCAL_PADES
LOCAL_XADES
COSIGN
COUNTERSIGN
LEGACY_SHA1
```

La existencia de código upstream no habilita una capability. `LOCAL_XADES`,
`COSIGN` y `COUNTERSIGN` pueden existir como valores del schema y permanecer
sin ningún profile activo. Una solicitud no soportada devuelve un error
cerrado `UNSUPPORTED_CAPABILITY`; nunca cambia a otro modo.

## 7. Protocol adapters y operaciones cerradas

Los adapters normalizan contratos; no deciden confianza, no muestran UI y no
poseen la clave. `SIGN`, `SELECT_CERTIFICATE` y `CLIENT_TLS_AUTH` son modelos y
coordinadores distintos. El input común es sealed:

```kotlin
sealed interface NormalizedProtocolRequest {
    val context: ProtocolRequestContext
    val operation: ProtocolOperation
}

data class NormalizedSignRequest(/* payload owned, policy y context */) :
    NormalizedProtocolRequest

data class CertificateSelectionRequest(
    override val context: ProtocolRequestContext,
    val filterPolicy: CertificateFilterRules,
    val callbackContractId: CallbackContractId,
) : NormalizedProtocolRequest {
    override val operation = ProtocolOperation.SELECT_CERTIFICATE
}

sealed interface ProtocolOperationResult
data class SigningResult(/* owned signature/result */) : ProtocolOperationResult
data class SelectedCertificateResult(/* owned public certificate DER */) :
    ProtocolOperationResult

interface ProtocolInputAdapter {
    val id: ProtocolInputAdapterId
    fun recognize(input: InterceptedProtocolInput, profile: SiteProfile): Boolean
    fun normalize(
        input: InterceptedProtocolInput,
        context: ProtocolRequestContext,
        profile: SiteProfile,
    ): AdapterParseResult
}

interface MiniAppletCallbackAdapter {
    val contractId: CallbackContractId
    fun encodeSuccess(result: ProtocolOperationResult): TypedBridgeResult
    fun encodeCancel(): TypedBridgeResult
    fun encodeError(error: SigningError): TypedBridgeResult
}

interface TriPhaseExecutionAdapter {
    suspend fun prepare(request: NormalizedSignRequest, chain: CertificateChain): PreSignResult
    suspend fun complete(
        request: NormalizedSignRequest,
        preSign: PreSignResult,
        pkcs1: LocalSignature,
    ): ProtocolCompletionResult
}

interface LocalSigningAdapter {
    fun createPlan(request: NormalizedSignRequest): LocalSigningPlan
    fun encodeResult(plan: LocalSigningPlan, signature: LocalSignature): SigningResult
}

interface AutoFirmaUriAdapter {
    fun parse(uri: String, profile: SiteProfile): AdapterParseResult
}

interface ClientCertificateAuthAdapter {
    fun validate(
        request: ClientAuthRequestMetadata,
        intent: ArmedClientAuthIntent,
        profile: SiteProfile,
        certificate: CertificateAuthMetadata,
    ): ClientAuthValidation
}

interface SigningResultEncoder {
    fun encode(result: SigningResult, contract: CallbackContractId): TypedBridgeResult
}

interface CertificateSelectionResultEncoder {
    fun encode(
        result: SelectedCertificateResult,
        contract: CallbackContractId,
    ): TypedBridgeResult
}
```

El nombre actual `SigningProtocolAdapter` se conserva temporalmente como
fachada compatible del ejecutor PRE/POST Junta. No se redefine como parser: se
adapta de forma explícita a `TriPhaseExecutionAdapter` y se elimina solo en un
commit posterior con tests de API.

`SigningCoordinator` conserva la propiedad de estado, confirmación, acceso a
`CertificateSession`, `LocalSignatureEngine`, TTL y transición terminal. El
adapter tri-phase recibe cadena pública y bytes bajo ownership controlado; el
PKCS#1 se produce solo en el motor local. Ninguna interfaz pública contiene
`PrivateKey`, password, URI P12 o `CertificateSession`.

`CertificateSelectionCoordinator` posee otro state machine:

```text
RECEIVED -> AWAITING_DISCLOSURE_CONSENT -> CERTIFICATE_SELECTED
    \                    \                         \
     +--------------------+------------------------->
             SUCCESS | CANCELLED | ERROR | EXPIRED | INVALIDATED
```

Solo devuelve el certificado público Base64 mediante el callback exacto de un
`selectcert`; no llama al motor de firma, no obtiene `PrivateKey` y no convierte
la selección en login TLS. Está ligado al mismo profile/version/origin/epoch,
TTL y single-use que firma. La UI indica `Selección de certificado` y que se
compartirá el certificado público con el origin. Cambio de navegación/profile,
replay o recreación lo invalida y borra su callback state.

`CertificateAuthMetadata` se calcula dentro de la frontera de certificado a
partir de la cadena pública y contiene solo algoritmo/tamaño de clave, vigencia,
key-usage, EKU cerrados, hashes SHA-256 de principals DER de la cadena y
longitud de cadena. No expone DN, serial, DER completo, alias ni material
privado. Es suficiente para la policy client-auth; el coordinator obtiene el
`PrivateKey` únicamente en el scope final de `proceed()`.

La implementación Junta actual se envuelve como primer adapter/profile sin
cambiar start URL, confirmación, copy visible, request body, callbacks ni
resultado externo. Las clases `JuntaOriginPolicy`, `JuntaNavigationPolicy`,
`JuntaWebViewClient` y `TrustedJuntaWebView` permanecen como fachadas mientras
sus consumidores migran uno a uno.

## 8. Bridge universal y correlación nativa

### 8.1. Canal

Se mantiene AndroidX WebKit y se prohíbe `addJavascriptInterface`.
`addDocumentStartJavaScript` y `addWebMessageListener` solo se registran para
los origins iniciadores exactos del profile activo. El callback nativo exige
`isMainFrame == true` y compara `sourceOrigin` con el top-level confirmado.

El ABI v1 tiene cuatro envelopes con keys exactas:

```json
{"v":1,"type":"REQUEST","callHandle":7,"operation":"SIGN","args":{}}
{"v":1,"type":"ACK","callHandle":7,"requestId":"<native-uuid>","ttlMs":120000}
{"v":1,"type":"CANCEL","callHandle":7,"requestId":"<native-uuid>"}
{"v":1,"type":"RESULT","callHandle":7,"requestId":"<native-uuid>","status":"SUCCESS|CANCELLED|ERROR","value":{}}
```

`args` y `value` tienen schemas cerrados por operación; `value` de error usa
solo un error code cerrado. `callHandle` es un entero monotónico por documento
que indexa referencias success/error guardadas en la clausura del shim. No es
un nonce ni autoridad de seguridad. El shim guarda las referencias antes de
`REQUEST`, pero no permite invocarlas hasta recibir `ACK`. Nativo valida el
request, genera `requestId`, almacena la tupla exacta
`(replyProxy, callHandle, requestId, profile, origin, epoch)` y responde ACK.
`CANCEL` y `RESULT` deben coincidir con toda la tupla; mensajes pre-ACK,
duplicados, reordenados o de otro proxy se rechazan y consumen el estado.

Se rechazan campos duplicados o desconocidos, tipos implícitos, strings de
control y mensajes por encima del límite. La respuesta usa el
`JavaScriptReplyProxy` asociado al REQUEST. No se usa `evaluateJavascript` para
ejecutar callbacks. El shim invoca únicamente la referencia original ya
guardada; un RESULT terminal borra handle, referencias y request ID. `pagehide`
puede enviar CANCEL como optimización, pero la invalidación nativa no depende
de que JavaScript llegue a ejecutarlo.

El ciclo de registro también es fail-closed. Al cambiar de profile, incluido
profile a `none`, el controlador primero invalida estado, retira el handler de
document-start y `removeWebMessageListener` en el hilo UI y destruye el WebView
si no puede demostrar la retirada. Un WebView nuevo registra únicamente los
origins iniciadores exactos del profile antes de su primer `loadUrl`. No se
reutiliza un listener con la unión de todos los profiles. `WebViewStateHolder`
y `MainActivity` no restauran un Bundle opaco de un documento trusted o con
flow activo: conservan como máximo URL top-level sanitizada y profile ID, y la
carga restaurada recibe process session/epoch nuevos.

### 8.2. Request ID y navigation epoch

JavaScript no genera request IDs de seguridad. `SecureRequestIdGenerator`
obtiene 128 bits de `SecureRandom`, fija la forma UUID y devuelve error si el
provider no puede producir entropía. No existe `Math.random()` ni fallback
temporal, incremental o basado en URL.

El contexto contiene:

```kotlin
data class NativeNavigationEpoch(
    val processSessionId: ProcessSessionId,
    val counter: Long,
)

data class SigningContext(
    val requestId: UUID,
    val profileId: ProfileId,
    val profileVersion: Int,
    val origin: ExactOrigin,
    val epoch: NativeNavigationEpoch,
    val createdAtElapsedNanos: Long,
    val deadlineElapsedNanos: Long,
)
```

`processSessionId` también usa `SecureRandom` y nunca se persiste. El contador
es monotónico dentro del proceso. Una navegación solicitada por UI invalida el
epoch antes de `loadUrl/reload/goBack/goForward`; `onPageStarted` cubre
redirects o cargas que no pasaron por el controlador; `onPageCommitVisible`
confirma el documento. Restore siempre crea sesión/epoch nuevos y no restaura
requests.

Expiry y ventanas de estado usan únicamente tiempo monotónico
(`SystemClock.elapsedRealtimeNanos` mediante una abstracción inyectable). La
hora UTC se usa solo en diagnóstico redondeado. Cambiar el reloj civil no
prolonga un request. El tiempo nunca correlaciona una llamada con otra: solo
sirve para expirar una correlación ya exacta.

### 8.3. Estado de un request

```text
RECEIVED -> AWAITING_CONFIRMATION -> PREPARING -> LOCAL_SIGNING -> COMPLETING
    \              \                    \              \             \
     +--------------+--------------------+--------------+-------------->
                  SUCCESS | CANCELLED | ERROR | EXPIRED | INVALIDATED
```

Solo una transición terminal gana mediante compare-and-set. Cada request es
single-use, está ligado a profile/version/origin/epoch, expira en un máximo de
dos minutos y conserva fingerprint del payload. Cualquier mismatch consume e
invalida la entrada; no queda disponible para reintento ambiguo.

Antes de cada PRE, firma local, POST y callback se repiten profile, origin,
epoch, TTL, fingerprint, identity session y capability. Navegar, recargar,
Back, cambio de profile, bloqueo de certificado, process death o segundo
request incompatible cancelan el primero y ponen a cero las copias sensibles.

Un `afirma:`/`intent:` top-level no solicitado se bloquea. Solo
`AutoFirmaUriAdapter` puede reconocer una URI main-frame, decodificarla sin
lanzar Package Manager y convertirla en request del profile activo. Package,
component, chooser, market y browser fallback arbitrario se rechazan.

## 9. Flujos criptográficos y de red

### 9.1. Tri-phase

El flujo es PRE remoto, PKCS#1 local y POST remoto. El endpoint se selecciona
por ID del catálogo; un `serverUrl` de la página solo puede confirmar el valor
exacto. Se fijan method, MIME, charset, codec, tamaño y número de firmas.

El transport genérico no puede heredar el DNS TOCTOU actual. La policy resuelve
y valida todas las direcciones una vez y entrega un `ResolvedEndpointSet`
inmutable al executor. El executor no puede hacer otra resolución. La
implementación production usa un cliente que admita DNS inyectado y devuelva
exclusivamente ese set (o un socket endpoint-bound equivalente), mientras
conserva el hostname original para SNI y hostname verification de plataforma.
No se sustituye el hostname TLS por una IP ni se instala trust manager,
hostname verifier o CA propia.

Antes de elegir/añadir ese cliente se revisan su documentación oficial,
versión y superficie de dependencia. Un contract test con DNS mutable debe
demostrar que la conexión solo intenta las direcciones previamente aprobadas;
si el stack no permite probarlo, el segundo profile de red queda bloqueado.

`EndpointScopedTransport` recibe `EndpointId` y deriva URL, method,
request/response MIME, charset/codec, headers permitidos, tamaños, timeouts y
redirect policy exclusivamente del catálogo. Deniega redirects, direcciones
privadas/reservadas, HTML de login y headers inesperados. No envía `Cookie`,
`Authorization`, `Referer` ni headers WebView salvo contrato futuro explícito
y revisado.

Cada execution adapter declara un `ProtocolResultVerifier`. La respuesta final
se valida sintáctica y semánticamente y, si devuelve contenedor/firma, se
verifica localmente contra payload y certificado antes del callback. Si el
contrato devuelve un identificador opaco, el adapter debe probar su retrieval
y binding según fuente oficial. Un `OK` remoto por sí solo no se convierte en
éxito silencioso; un profile sin verifier suficiente no se habilita.

### 9.2. Firma local

`LOCAL_CADES` y `LOCAL_PADES` usan planes distintos. Un profile fija attached
o detached, algoritmo, modo/policy y límites; una opción de la página no puede
ampliarlos. XAdES/FacturaE no se implementan localmente por analogía: la fuente
oficial Android los enruta a tri-phase.

El documento vive en memoria o descriptor con ownership y límite. No se
persiste en cache, saved state, informe o logs. Un output local destinado a
subida se entrega solo al request origin/epoch exacto.

### 9.3. Storage/Retrieve

Es una capability independiente. Storage y Retrieve deben aparecer como par
exacto del mismo profile, con sus límites y codec. Los identificadores son
secretos efímeros: no se registran, no entran en historial y se invalidan al
cambiar epoch. No se sigue redirect ni se acepta endpoint proporcionado por la
página que no coincida con catálogo.

## 10. Autenticación TLS con certificado

Firma de documento y `ClientCertRequest` son flows y consentimientos
distintos.

### 10.1. Policy

```kotlin
data class ClientAuthPolicy(
    val authorities: Set<ExactTlsAuthority>,
    val allowedKeyTypes: Set<ClientKeyType>,
    val allowedIssuerDerSha256: Set<Sha256>?,
    val allowEmptyServerIssuerList: Boolean,
    val certificateRules: CertificateFilterRules,
    val purposeLabel: String,
)
```

El default de `allowEmptyServerIssuerList` es `false`. Las reglas exigen
vigencia, clave compatible y uso/EKU apropiado cuando esas extensiones están
presentes. Los principals enviados por el servidor se comparan por encoding
DER/hash, no por substring de DN.

### 10.2. Ambigüedad de frame y WebView one-shot

`ClientCertRequest` expone host/port/key types/principals, pero no frame, URL ni
path. Un armado sobre un WebView que ya muestra contenido no puede distinguir
una petición top-level de un iframe/subresource de la misma autoridad. Por
tanto, el armado por sí solo no es una defensa y ese caso falla cerrado.

`CLIENT_TLS_AUTH` solo puede habilitarse si el portal demuestra que el challenge
ocurre en el primer handshake TLS de una navegación top-level. El flow usa un
WebView nuevo, dedicado y one-shot:

1. esperar el barrier de limpieza de preferencias client-cert;
2. crear un WebView sin documento previo, bridge ni restore state;
3. el usuario pulsa `Entrar con certificado` y nativo crea un
   `ArmedClientAuthIntent` single-use, TTL corto, profile, epoch y URL exacta;
4. registrar esa única navegación main-frame antes del primer `loadUrl`;
5. aceptar solo un callback antes de que exista response/document-start; los
   redirects previos deben estar enumerados y conservar autoridad permitida;
6. exigir match exacto de host/port/key types/principals/metadata del
   certificado;
7. una segunda petición, contenido/document-start previo, callback tardío,
   navegación adicional, carrera o mismatch ejecuta `ignore()`, destruye el
   WebView y cierra el flow.

Un marker de document-start sin datos sensibles prueba el límite en tests. Si
el challenge aparece después de que el portal haya podido crear subresources,
si WebView lo satisface sin callback o si la secuencia no es reproducible, la
capability de ese profile queda `UNSUPPORTED`. Tras `proceed` válido, el mismo
WebView limpio puede convertirse en la vista activa del profile y el armado se
consume. WebView puede reutilizar la decisión sin callback para el mismo
host/puerto; por ello la sesión autenticada queda confinada a esa autoridad y
profile, no a un path. Salir de la autoridad/profile, background/lock o cerrar
sesión destruye el WebView y espera el barrier de limpieza. La UI explica esa
reutilización acotada. Si el portal exige un scope menor que host/puerto, queda
`UNSUPPORTED`. Esta regla no afirma observar cada handshake ni identificar
frames que la API no expone.

### 10.3. Consentimiento y resolución

La UI muestra organización, dominio/puerto, resumen del certificado y el texto
`Inicio de sesión con certificado; no es una firma de documento`. Tras
confirmación, el coordinator valida `CertificateAuthMetadata` y recupera clave
y cadena de la sesión únicamente dentro del scope de
`request.proceed(privateKey, chain)` de esa instancia exacta. No guarda la
instancia ni la identidad en el adapter.

- rechazo técnico/no armado: `ignore()` para no cachear una decisión amplia;
- cancelación explícita del usuario en una petición válida: `cancel()`;
- confirmación válida: `proceed()` una sola vez;
- salida de profile con diálogo pendiente: `ignore()` y limpieza;
- cambio de profile, bloqueo de identidad o borrado de sesión:
  barrier de `WebView.clearClientCertPreferences`.

`clearClientCertPreferences` es asíncrono. `ClientCertPreferenceBarrier`
mantiene el navegador en `CLEARING`, bloquea creación/carga de otro WebView y
solo pasa a `READY` dentro del callback UI. Profile switch, background/lock de
identidad y borrado de sesión esperan ese barrier. Timeout, excepción o
destrucción de Activity dejan client-auth y navegación WebView bloqueados
(`EXTERNAL_ONLY`) hasta que una limpieza posterior o un proceso nuevo complete
el barrier; nunca se continúa suponiendo que el cache se limpió. Los tests
fuerzan callback tardío, ausencia de callback y navegación concurrente.

No se reutiliza la decisión en otro origin. No se loguean principals completos,
issuer DN, serial, alias, URI P12 ni material de clave. El informe solo indica
profile, autoridad redactada, resultado cerrado y timestamp redondeado.

## 11. Navegación desconocida y UX

Una URL HTTPS válida sin profile se abre `BROWSE_ONLY` si la política permite
embeberla. El bridge no se registra, los URI AutoFirma se bloquean, el cliente
nativo no lee cookies y cualquier `ClientCertRequest` se ignora.

La UI conserva la estética actual y muestra siempre:

- display name del profile o `Sitio sin perfil`;
- status de compatibilidad y trust mode con textos distintos;
- dominio ASCII completo;
- tipo de acción: entrada, firma o selección de certificado;
- formato/algoritmo antes de confirmar;
- advertencia destacada para SHA-1;
- acción para abrir el HTTPS validado en navegador externo.

El mensaje exacto para un sitio desconocido es:

> Este sitio todavía no tiene un perfil de firma verificado. Puede navegar,
> pero el certificado y la firma están bloqueados.

`VERIFIED_CONTRACT` se presenta como `Contrato documentado; pendiente de
validación real`, nunca como soporte verificado. Solo `VERIFIED_E2E` puede
mostrar `Validado con el portal`, junto con fecha/versión del profile.

El reporte de compatibilidad excluye query, fragment, cookies, payload,
certificado, callbacks opacos y datos personales.

## 12. Migración incremental y perfiles candidatos

No hay sustitución big-bang:

1. completar gate Junta sin cambios production;
2. añadir modelos/catálogo puros y representar Junta sin cambiar consumidores;
3. introducir trust controller con fachadas Junta;
4. activar browse-only desconocido sin exponer capabilities;
5. separar input/execution adapters y cerrar extraProperties;
6. eliminar DNS TOCTOU y añadir result verifiers antes de otro profile;
7. endurecer request ID/epoch y después el ABI reply channel en commits
   separados, manteniendo el E2E;
8. implementar `SELECT_CERTIFICATE` como flow propio;
9. añadir el framework ClientCert one-shot y su barrier sin profile confiable
   si todavía falta
   evidencia exacta;
10. incorporar un profile por familia y commit, con tests y E2E independientes.

Orden de investigación, no promesa de activación:

- **ancla:** Junta Ovorion;
- **segundo tri-phase CAdES:** Universidad de Zaragoza, tras redactar fixture,
  confirmar el contrato móvil y ejecutar E2E;
- **MiniApplet/Storage alternativo:** Gobierno de Aragón;
- **callback/formato distinto:** REG/RedSARA o ACCEDA, solo después de resolver
  su transporte móvil y soportar XAdES/PAdES conforme a evidencia;
- **TLS cliente:** AEAT, después de capturar metadata exacta de
  `ClientCertRequest` sin autenticarse automáticamente;
- **firma local PAdES:** Comunidad de Madrid, después de confirmar el formato
  aceptado al adjuntar el PDF.

Ninguno entra `ENABLED` por estar en esta lista. Cada grupo requiere commit
separado, profile version nuevo, fixture redactada, security review, tests de
contrato y E2E real. Un perfil se degrada a `DISABLED` si cambia su JS,
endpoint, certificado TLS o contrato y no puede revalidarse.

## 13. Persistencia, privacidad y diagnóstico

Se puede persistir únicamente:

- versión del catálogo bundled;
- profile/favorito seleccionado sin query ni fragment;
- URI SAF ya autorizada según la política actual;
- preferencias UI no sensibles.

No se persisten request/epoch, callback, password, `PrivateKey`, cadena DER,
payload, PRE/PK1/POST, resultado, Storage ID, client-auth request/decision,
query de navegación ni WebView saved state que contenga un flow pendiente.

`SanitizedLogger` acepta eventos cerrados, no mapas libres. Hosts se registran
solo si proceden del catálogo; paths se sustituyen por ID de endpoint. Los
errores externos se mapean a códigos internos y no copian bodies o mensajes
que puedan contener datos.

## 14. Validación y criterios de aceptación

### 14.1. Tests automatizados

Una suite contractual parametrizada enumera directamente cada entrada del
catálogo y cada adapter registrado. El test falla si aparece una entrada sin
su matriz completa o un adapter sin profile/test explícito. Por adapter y
profile verifica:

- schema/unknown fields/protocol ID/duplicados;
- exact origin, puerto, IDNA, redirect y endpoint override;
- parser, operación, formato, packaging, algoritmo, modo, extraProperties y
  callback;
- request ID seguro, epoch, single-use, TTL, replay y doble terminal;
- iframe, subframe message, origin spoof y reload/Back/restore;
- cancel/error y limpieza observable de copias sensibles;
- network timeout, DNS privado, redirect, MIME, tamaño, HTML y respuesta
  malformada;
- filtrado y sesión de certificado;
- process recreation.

Para `SELECT_CERTIFICATE`: consentimiento de disclosure, filtro, callback de
un solo certificado, cancel/error, ausencia de firma/clave privada, replay y
limpieza por navegación.

Para ClientCert:

- petición sin armado;
- WebView usado previamente, iframe/subresource hostil y callback posterior al
  marker document-start;
- host/port/key types/principals incorrectos;
- redirect permitido y no permitido;
- segunda petición/race/replay;
- confirmación/cancel/ignore exactamente una vez;
- barrier asíncrono de clear preferences, timeout y navegación concurrente;
- muerte de proceso con diálogo pendiente.

Fixtures de portales se reducen al mínimo y eliminan documento, cookie,
challenge, identificador, certificado y respuesta completa.

### 14.2. Gates globales por milestone

1. focused tests;
2. suite unit completa;
3. instrumentation en el POCO F6 Pro;
4. lint debug/release;
5. clean debug/release build;
6. firma y alignment APK;
7. manifest/DEX: no probe/debugging en release;
8. scans de credenciales y patrones inseguros;
9. diff completo y `git diff --check`;
10. commit local limpio, sin push/merge/publicación.

Los diez pasos se ejecutan tras **cada** milestone production, aunque el diff
parezca no tocar WebView o criptografía. Un test de artefacto exige que ningún
profile `QA_ONLY` esté activo en release.

Regresiones obligatorias:

- E2E Junta sin cambio externo;
- unknown HTTPS siempre browse-only;
- un profile no hereda confianza de otro;
- endpoint no sustituible por la página;
- iframe no abre firma ni selección; TLS cliente solo puede ocurrir en el
  WebView one-shot anterior a todo documento, o queda unsupported;
- callback de otro epoch rechazado;
- password sigue `CharArray` y se limpia;
- clave privada nunca se exporta;
- release sin debug probe, WebView debugging, cleartext ni backup;
- scan de credenciales limpio.

### 14.3. Promoción de status

Unit/instrumentation nunca producen `VERIFIED_E2E`. La promoción exige portal
real, certificado elegido manualmente, contraseña manual, confirmación manual,
resultado aceptado y evidencia anonimizada. Si el trámite requiere decisión,
documento personal o efecto administrativo, la ejecución se detiene en el
punto exacto y el usuario completa la acción.

## 15. Rollback y riesgos residuales

Cada milestone conserva una fachada y un commit autónomo. Rollback consiste en
revertir solo el commit del milestone; el catálogo puede desactivar un profile
sin eliminar el motor. Nunca se usa reset destructivo sobre cambios del
usuario.

Riesgos que requieren evidencia antes de implementación:

- el contorno Junta aún no tiene aceptación E2E de la build baseline;
- `ClientCertRequest` no aporta frame/path; solo el primer handshake de un
  WebView one-shot vacío puede superar el gate, y cualquier portal que no
  cumpla esa secuencia permanece sin `CLIENT_TLS_AUTH`;
- WebView no garantiza aislamiento físico de cookies entre profiles;
- REG/ACCEDA publican contratos desktop/local que no prueban un transporte
  móvil interno compatible con esta app;
- Aragón/UniZar usan SHA-1 heredado y requieren advertencia más revisión de
  aceptación actual;
- el release baseline está firmado con certificado debug y el tooling Termux
  no verificó aún el gate moderno exacto de páginas de 16 KiB;
- los hooks de probe inertes siguen en el recurso JS main y deben salir por
  completo del artefacto release durante el milestone de bridge.

Ninguno de estos riesgos se resuelve ampliando allowlists o relajando TLS.
