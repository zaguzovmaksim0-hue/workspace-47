# Matriz de compatibilidad de firma de las administraciones públicas españolas

Fecha de corte: 2026-07-29

Estado del documento: investigación de contratos previa a cambios de
producción. Esta matriz no convierte por sí sola ningún sitio en confiable.

## 1. Alcance, método y significado de los estados

La investigación usa únicamente fuentes primarias: código oficial de Cliente
@firma y firma-android fijado a commits concretos, documentación de Android y
páginas o manuales publicados por cada administración. La aparición de la
palabra AutoFirma, de un certificado o de la plataforma @firma en una página
no demuestra el origin, endpoint, codec ni callback que usa un trámite.

Los estados significan:

- `VERIFIED_E2E`: el portal aceptó una operación real, con selección,
  contraseña y confirmación manuales, y se conservó solo evidencia
  anonimizada.
- `VERIFIED_CONTRACT`: el contrato técnico genérico o portal-specific está
  probado por fuente oficial. Un contrato genérico no demuestra que un portal
  lo use; uno portal-specific tampoco implica implementación ni E2E.
- `EXPERIMENTAL`: hay evidencia específica del portal y una implementación
  delimitada, pero falta aceptación E2E real o una parte del contrato runtime.
- `BROWSE_ONLY`: existe una entrada oficial, pero no hay contrato suficiente
  para exponer certificado, clave privada, bridge, autenticación TLS cliente o
  transporte de firma.
- `UNSUPPORTED`: la operación está fuera del alcance o una fuente oficial
  confirma que el flujo móvil no es compatible.

P19 (Carné Joven Europeo de Andalucía) cuenta con verificación `VERIFIED_E2E`
delimitada a `CLIENT_TLS_AUTH` en dispositivo físico (2026-07-21, commit
dc3c231). P16 (Aragón SIRAW) cuenta con `VERIFIED_E2E` limitado al login CAdES
aceptado por el portal el 2026-07-28. P07B (`junta-ofvirtual`, MiniApplet 1.5)
cuenta con `VERIFIED_E2E` limitado al login CAdES aceptado por Oficina Virtual
en dispositivo físico el 2026-07-29. P17 (Universidad de Zaragoza) cuenta con
`VERIFIED_E2E` limitado al login CAdES aceptado por el portal el 2026-07-30.

Además de los contratos genéricos, REG/RedSARA y ACCEDA publican JavaScript
suficiente para `VERIFIED_CONTRACT`; REG/RedSARA dispone de profile/adapter
limitado, pero todavía no de aceptación E2E. ACCEDA no está implementada.
Aragón SIRAW, Oficina Virtual y UniZAR están habilitados solo para sus logins
observados; Storage/Retrieve, firma documental y presentación administrativa
permanecen fuera de la evidencia. La integración
histórica Ovorion MiniApplet 1.4 permanece `EXPERIMENTAL` y no hereda el estado
del profile separado MiniApplet 1.5.

## 2. Contratos oficiales del motor común

| Familia o interacción | Contrato probado por fuente oficial | Decisión para el cliente | Estado |
| --- | --- | --- | --- |
| `MiniApplet.sign` legado | `sign(data, algorithm, format, extraParams, success, error)`; el callback legado de éxito recibe firma y certificado, y el de error tipo y mensaje. [C3] | Solo se habilita con profile, origin, operación, formato, algoritmo y callback exactos. | `VERIFIED_CONTRACT` |
| `AutoScript.sign` actual | El API oficial conserva seis argumentos; el éxito actual admite firma, certificado e información adicional, y el error excepción, mensaje y código. [C1][C2] | Se modelan contratos de callback versionados; no se adivina la aridad. | `VERIFIED_CONTRACT` |
| `selectCertificate` | AutoScript devuelve el certificado Base64 por callback; el protocolo Android declara `selectcert`. [C2][C4] | Capability independiente de `SIGN`; nunca entrega clave privada. | `VERIFIED_CONTRACT` |
| `coSign` / `counterSign` | Existen en el motor y en el protocolo oficial. [C4][C5] | Deshabilitados hasta que un portal concreto aporte evidencia y E2E. | `UNSUPPORTED` |
| `afirma://` | El cliente Android oficial declara operaciones `sign`, `cosign`, `countersign`, `batch`, `selectcert` y `save`. [C4] | Se parsea internamente solo para un adapter permitido; nunca se delega de forma genérica a otra app. | `VERIFIED_CONTRACT` |
| `intent://` | AutoScript construye el wrapper Android de una invocación AutoFirma; no es una familia criptográfica distinta. [C6] | Se acepta únicamente si decodifica a un request permitido del profile activo, sin package/component/fallback arbitrario. | `VERIFIED_CONTRACT` |
| WebSocket/WSS localhost | AutoScript contiene selección, bootstrap y handshake del servicio local; el launcher de escritorio expone operaciones adicionales. [C7][C8] | No se implementa servidor loopback móvil. El cliente Android oficial usa otro transporte y no justifica abrir localhost. | `UNSUPPORTED` |
| Tri-phase PRE/POST | El cliente oficial ejecuta PRE, PKCS#1 local y POST; el servidor oficial define el servicio y los preprocesadores admitidos. [C11][C12][C18] | Adapter cerrado por endpoint exacto, formato, algoritmo, modo, tamaños, MIME y codec; la clave privada nunca sale del dispositivo. | `VERIFIED_CONTRACT` |
| StorageService / RetrieveService | Los servlets oficiales definen almacenamiento, recuperación y comprobación mediante identificadores. [C9][C10] | Deshabilitados salvo evidencia específica y allowlist exacta de ambos endpoints; no se reutilizan cookies por defecto. | `VERIFIED_CONTRACT` |
| Firma local Android | firma-android incorpora firmadores CAdES/PAdES; enruta XAdES y FacturaE a tri-phase. [C13][C14] | `LOCAL_CADES` y `LOCAL_PADES` son capabilities separadas. XAdES/FacturaE no se anuncian como locales. | `VERIFIED_CONTRACT` |
| Modos y algoritmos | Cliente @firma define firmas implícitas/explícitas y algoritmos RSA, incluido SHA-256; SHA-1 queda como compatibilidad heredada. [C19][C20] | SHA-256 es la rama normal. `LEGACY_SHA1` exige profile exacto y advertencia; no existe fallback algorítmico. | `VERIFIED_CONTRACT` |
| Autenticación TLS con certificado | Android entrega una petición distinta mediante `ClientCertRequest`, con host, puerto, tipos de clave e issuers; `KeyChain` permite elegir una identidad. `proceed()` y `cancel()` pueden recordarse para el host/puerto, `ignore()` no, y las preferencias se limpian explícitamente. [C15][C16] | Adapter y consentimiento separados de firma; cada resolución se limita a la petición exacta y el cambio de profile limpia preferencias. | `VERIFIED_CONTRACT` |

El repositorio oficial de Cliente @firma identifica MiniApplet como tecnología
obsoleta y remite a AutoScript; por eso la compatibilidad MiniApplet es una
rama heredada y no el API general del producto. [C0]

## 3. Resumen de portales investigados

| Ámbito | Organización / origin exacto investigado | Operación acreditada | TLS cliente | Estado del producto |
| --- | --- | --- | --- | --- |
| AGE / PAG | Punto de Acceso General — `https://sede.administracion.gob.es` | Acceso al catálogo y al Registro General | No verificado | `BROWSE_ONLY` |
| AGE / RedSARA | Registro Electrónico General — `https://reg.redsara.es` | Firma de XML de resumen mediante AutoScript | No verificado | `VERIFIED_CONTRACT`; profile/adapter implementados, sin E2E |
| AGE / ACCEDA | Sede Administraciones Públicas — `https://sede.administracionespublicas.gob.es` | Firma PAdES de solicitud; rama genérica XAdES | No verificado | `VERIFIED_CONTRACT` estático; no implementado/E2E |
| Estatal | AEAT — `https://sede.agenciatributaria.gob.es` → `https://www1.agenciatributaria.gob.es` | Acceso de solo lectura a `Mis datos censales` mediante Client TLS exacto | `CertificateRequest` TLS observado; callback WebView y aceptación aún no E2E | `VERIFIED_CONTRACT / QA_ONLY`; release no lo incluye |
| Estatal | Sede Seguridad Social — `https://sede.seg-social.gob.es` | Firma con AutoFirma | No verificado | `UNSUPPORTED` en móvil para ese flujo |
| Estatal | Import@ss — `https://portal.seg-social.gob.es` | Identificación con certificado/DNIe, Cl@ve o SMS | No verificado | `BROWSE_ONLY` |
| Estatal | SEPE — `https://sede.sepe.gob.es` | Firma con AutoFirma tras identificación con certificado | No verificado | `BROWSE_ONLY` |
| Estatal | DGT — `https://sede.dgt.gob.es` | Identificación con certificado y firma con AutoFirma en trámites que la requieren | No verificado | `BROWSE_ONLY` |
| Justicia | Sede Judicial — `https://sedejudicial.justicia.es` | Acceso con certificado/Cl@ve y firma de escritos con AutoFirma | No verificado | `BROWSE_ONLY` |
| Ministerio | Sede Ministerio de Justicia — `https://sede.mjusticia.gob.es` | Firma local con AutoFirma en determinados trámites | No verificado | `BROWSE_ONLY` |
| Comunidad autónoma | Junta de Andalucía — `https://www.juntadeandalucia.es` | `MiniApplet.sign` para autenticación, tri-phase CAdES | No en el contorno observado | `EXPERIMENTAL` |
| Comunidad autónoma | Junta de Andalucía — Oficina Virtual — `https://ws072.juntadeandalucia.es` | Login mediante `MiniApplet.sign` 1.5 y tri-phase CAdES contra endpoint WS024 exacto | No | `VERIFIED_E2E` limitado al login aceptado por el portal el 2026-07-29; release direct-only; firma documental no verificada |
| Comunidad autónoma | IAJ / Carné Joven Andalucía — `https://ws104.juntadeandalucia.es` | Entrada con certificado mediante facade TLS exacta en `ws235` | Sí, `CertificateRequest` verificado | `VERIFIED_E2E` (solamente `CLIENT_TLS_AUTH`; verificado en dispositivo físico 2026-07-21 tras dc3c231; Zona privada y Solicitar Carné Joven autenticados; firma/AutoFirma posterior no E2E) |
| Comunidad autónoma | Comunidad de Madrid — `https://sede.comunidad.madrid` | Descargar PDF, firmarlo localmente con AutoFirma y adjuntarlo al registro | No verificado | `BROWSE_ONLY` |
| Comunidad autónoma | Comunidad de Madrid / gestiona2 — `https://gestiona2.comunidad.madrid` | Acceso con certificado y firma AutoFirma del trámite observado | No verificado | `UNSUPPORTED` en móvil para ese flujo |
| Comunidad autónoma | Gobierno de Aragón — `https://aplicaciones.aragon.es` | Login mediante `MiniApplet.sign` CAdES; Storage/Retrieve y firma documental separados | No verificado | `VERIFIED_E2E` limitado al login CAdES aceptado el 2026-07-28; ramas restantes bloqueadas |
| Diputación | Diputación de Valladolid — `https://www.sede.diputaciondevalladolid.es` | Identificación/firma con certificados admitidos por @firma | No verificado | `BROWSE_ONLY` |
| Ayuntamiento | Ayuntamiento de Sevilla — `https://sede.sevilla.org` | Presentación con certificado actual y AutoFirma | No verificado | `BROWSE_ONLY` |
| Ayuntamiento | Ayuntamiento de Madrid — `https://sede.madrid.es` | Trámites con certificado en navegador/móvil según el procedimiento | No verificado | `BROWSE_ONLY` |
| Universidad pública | Universidad de Granada — `https://sede.ugr.es` | Apertura de AutoFirma y elección de certificado para acceder/firmar | No verificado | `BROWSE_ONLY` |
| Universidad pública | Universidad de Sevilla — `https://sede.us.es` | Autenticación y firma con AutoFirma de escritorio | No verificado | `BROWSE_ONLY` |
| Universidad pública | Universidad de Zaragoza — `https://tramita.unizar.es` | Firma de challenge de sesión CAdES; tri-phase en móvil | No verificado | `VERIFIED_E2E` limitado al login CAdES aceptado el 2026-07-30; Storage/Retrieve y firma documental bloqueados |

## 4. Fichas de evidencia por portal

`No verificado` significa exactamente que el dato no se publica en la fuente
consultada o no se observó de forma segura. No se completa por semejanza con
otro portal.

### P01 — Punto de Acceso General / enlace al REG-AGE

- **Organización:** Administración General del Estado, Registro Electrónico
  General.
- **Origin oficial investigado:** `https://sede.administracion.gob.es`.
- **Entrada:** [Registro Electrónico General de la AGE][P01].
- **Operación:** identificación y presentación de solicitud, escrito o
  comunicación con certificado/DNIe o Cl@ve.
- **Protocolo:** no verificado para móvil; la validación por @firma no prueba
  el transporte del portal. [P01B]
- **Signing endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.
- **Motivo:** la fuente acredita el requisito de identidad/firma, no un ABI
  AutoFirma ni un endpoint que pueda incluirse de forma segura en un profile.

### P02 — Agencia Estatal de Administración Tributaria

- **Organización:** Agencia Tributaria (AEAT).
- **Source exacto:** [Mi área personal][P02C],
  `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`.
- **Target exacto:** [Mis datos censales][P02D],
  `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`.
- **Operación acotada:** autenticación Client TLS y acceso de solo lectura a
  `Mis datos censales`; no incluye modificación censal, presentación, pago ni
  firma. La documentación general de certificado móvil permanece como contexto
  [P02][P02A]; las ramas de firma separadas no forman parte del profile [P02B].
- **Evidencia runtime:** un handshake TLS 1.2 sin certificado recibió
  `CertificateRequest` con lista de issuers no vacía; sin certificado, el
  servidor terminó en la página de error 403. Esto prueba el contrato TLS del
  endpoint, no el callback Android ni la aceptación del certificado.
- **Contrato implementado:** profile `aeat-mis-datos-censales`, transición
  `DIRECT_FROM_SOURCE`, source/host/443/path exactos, sin query ni fragment,
  key algorithms RSA/EC, issuer obligatorio y TTL de 15 segundos.
- **Signing endpoint / formato / algoritmo / callback:** no verificados y fuera
  de alcance.
- **Estado:** `VERIFIED_CONTRACT / QA_ONLY`; el release no contiene este trust
  profile.
- **Gate restante:** observar `onReceivedClientCertRequest` en WebView y que el
  portal acepte el acceso de solo lectura en dispositivo físico. Solo entonces
  puede evaluarse `VERIFIED_E2E / ENABLED`.

### P03 — Seguridad Social e Import@ss

- **Organización:** Instituto Nacional de la Seguridad Social / Tesorería
  General de la Seguridad Social.
- **Origins oficiales investigados:** `https://sede.seg-social.gob.es` y
  `https://portal.seg-social.gob.es`.
- **Entradas:** [requisitos de AutoFirma de la Sede][P03] y [ayuda de
  Import@ss][P03A].
- **Operaciones:** firma de trámites en la Sede; identificación en Import@ss
  mediante SMS, Cl@ve, certificado electrónico o DNIe.
- **Protocolo:** la Sede documenta AutoFirma de escritorio y declara que esa
  firma no funciona en dispositivos móviles. Import@ss no publica en la fuente
  consultada un ABI de firma ni un handshake TLS exacto.
- **Signing endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estados:** Sede/AutoFirma móvil `UNSUPPORTED`; Import@ss `BROWSE_ONLY`.

### P04 — Servicio Público de Empleo Estatal

- **Organización:** SEPE.
- **Origin oficial investigado:** `https://sede.sepe.gob.es`.
- **Entrada:** [preguntas frecuentes de AutoFirma][P04].
- **Operación:** firmar con AutoFirma después de identificarse mediante
  certificado.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.
- **Motivo:** el requisito de instalar AutoFirma no identifica la versión del
  protocolo ni autoriza un bridge.

### P05 — Dirección General de Tráfico

- **Organización:** DGT.
- **Origin oficial investigado:** `https://sede.dgt.gob.es`.
- **Entrada:** [verificación de equipos, firmas y certificados][P05].
- **Operación:** identificación mediante certificado y firma con AutoFirma en
  los servicios que requieren firma.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P06 — Sede Judicial y Ministerio de Justicia

- **Organizaciones:** Sede Judicial Electrónica y Ministerio de Justicia.
- **Origins oficiales investigados:** `https://sedejudicial.justicia.es` y
  `https://sede.mjusticia.gob.es`.
- **Entradas:** [firma y certificados admitidos][P06], [guía de presentación
  de escritos][P06A], [preguntas frecuentes del Ministerio][P06B] y [trámite
  ministerial que exige AutoFirma][P06C].
- **Operaciones:** autenticación con Cl@ve/certificado y firma de escritos o
  documentos PDF con AutoFirma; el Ministerio indica que solo determinados
  trámites permiten AutoFirma.
- **Protocolo:** AutoFirma acreditada, pero ABI, endpoint y callback concretos
  no verificados. Que la guía entregue un documento PDF no demuestra CAdES,
  PAdES ni el modo criptográfico devuelto al portal.
- **Signing endpoint / algoritmo / callback:** no verificados.
- **Formato:** PDF como documento según la guía; formato criptográfico de la
  firma no verificado.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P07 — Junta de Andalucía (ancla de regresión)

- **Organización:** Junta de Andalucía, Ovorion.
- **Origin top-level habilitado en el contorno actual:**
  `https://www.juntadeandalucia.es`.
- **Entrada:** [login público con certificado][P07].
- **Operación:** `MiniApplet.sign` para autenticación.
- **Protocolo observado:** página MiniApplet heredada; dos capturas tempranas
  observaron la rama Android `intent://`. La implementación interna ejecuta el
  contrato tri-phase PRE/PKCS#1/POST sin abrir AutoFirma externa. El probe
  endurecido posterior prefirió falsos negativos y no reprodujo de nuevo la
  correlación causal; véase `docs/protocol-observations.md`.
- **Signing endpoint exacto:**
  `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService`.
- **Formato / modo:** `CAdES` / `EXPLICIT`.
- **Algoritmo:** `SHA1withRSA`, exclusivamente como `LEGACY_SHA1` del contrato
  observado; no es un valor general.
- **Callback:** éxito legado
  `saveSignatureAuthCallback(signatureB64, certificateB64)`; error
  `showLogCallback(errorType, errorMessage)`. El nativo no ejecuta nombres de
  callback: el shim conserva referencias de función tipadas.
- **TLS client auth:** no forma parte del contorno observado; no se habilita.
- **Estado:** `EXPERIMENTAL`.
- **Motivo:** existen evidencia pública, adapter y tests, pero falta aceptación
  E2E real del portal con confirmación manual.

Los redirects/origins Junta ya codificados (`sede`, `ssoweb`, `pfirma`,
`ws024`, `ws050`) no se promueven automáticamente a origins iniciadores de
firma. El endpoint `ws024` anterior es el único destino tri-phase actual.

### P07B — Junta de Andalucía, Oficina Virtual (`junta-ofvirtual`)

- **Organización:** Junta de Andalucía, Oficina Virtual.
- **Origin iniciador exacto:** `https://ws072.juntadeandalucia.es`.
- **Entrada:**
  `https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs`.
- **Operación:** acceso con certificado mediante `MiniApplet.sign`.
- **Signing endpoint exacto:**
  `https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService`.
- **Contrato:** `POST`, request
  `application/x-www-form-urlencoded; charset=UTF-8`, response `text/plain`,
  redirects denegados y límites de 2 MiB por request/response.
- **Formato / packaging / modo:** `CAdES` / `DETACHED` / `EXPLICIT`.
- **Algoritmo:** `SHA1withRSA`, exclusivamente bajo capability
  `LEGACY_SHA1` del profile exacto.
- **TLS client auth:** no forma parte de este profile.
- **Estado actual:** profile version 2, `VERIFIED_E2E / ENABLED`.
- **Evidencia E2E 2026-07-29:** en un POCO F6 Pro, la build QA direct-only
  completó PRE, firma local, POST, callback y submit; Oficina Virtual aceptó la
  autenticación y abrió el área interna de trámites pendientes.
- **Causa del fallo anterior:** un redirect HTTP heredado era bloqueado y
  `onStop()` bloqueaba la identidad/destruía el WebView. Los commits `6538e1a`
  y `26230ab` corrigen ambas transiciones sin ampliar origin ni contrato.
- **Transporte:** el E2E aceptado fue directo. El tunnel permanece aislado,
  QA-only y deshabilitado; release es direct-only y no contiene credential ni
  relay tuple.
- **Limitación:** verificación limitada al login CAdES observado. No demuestra
  Storage/Retrieve, firma documental, cofirma, contrafirma, presentación de
  solicitudes ni todas las funciones del portal.
- **Informe sanitizado:**
  `docs/e2e/2026-07-29-junta-ofvirtual-auth-success.md`.

### P08 — Comunidad de Madrid

- **Organización:** Comunidad de Madrid.
- **Origins oficiales investigados:** `https://sede.comunidad.madrid` y, como
  flujo distinto, `https://gestiona2.comunidad.madrid`.
- **Entradas:** [guía oficial de tramitación][P08], [Registro Electrónico
  General][P08A] y [entrada técnica de un trámite][P08B].
- **Operación:** la guía general permite descargar el PDF de la solicitud,
  firmarlo localmente con AutoFirma y adjuntarlo al registro. El trámite
  `gestiona2` observado solicita certificado y AutoFirma, pero su propio
  JavaScript muestra `No se puede realizar la solicitud desde dispositivo
  móvil o tableta`.
- **Protocolo:** candidato a `LOCAL_PADES`, pero el formato criptográfico y el
  contrato de carga no se han capturado. El segundo flujo tampoco publica ABI.
- **Signing endpoint / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estados:** guía general/local PDF `BROWSE_ONLY`; flujo `gestiona2`
  `UNSUPPORTED` en móvil.

### P09 — Diputación Provincial de Valladolid

- **Organización:** Diputación de Valladolid.
- **Origin oficial investigado:**
  `https://www.sede.diputaciondevalladolid.es`.
- **Entrada:** [requisitos técnicos][P09].
- **Operación:** identificación/firma con DNIe y certificados admitidos por
  @firma.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.
- **Nota:** que sus preguntas frecuentes describan cofirma o contrafirma no
  demuestra que un trámite del portal las invoque; `COSIGN` y `COUNTERSIGN`
  permanecen deshabilitadas. [P09A]

### P10 — Ayuntamiento de Sevilla

- **Organización:** Ayuntamiento de Sevilla.
- **Origin oficial investigado:** `https://sede.sevilla.org`.
- **Entradas:** [presentación y Cl@ve Firma][P10] y [ayuda de errores de
  firma][P10A].
- **Operación:** presentación con certificado vigente y AutoFirma.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P11 — Ayuntamiento de Madrid

- **Organización:** Ayuntamiento de Madrid.
- **Origin oficial investigado:** `https://sede.madrid.es`.
- **Entrada:** [procedimiento oficial con canales y sistemas de
  identificación][P11].
- **Operación:** identificación/tramitación con certificado cuando el
  procedimiento lo admite.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P12 — Universidad de Granada

- **Organización:** Universidad de Granada.
- **Origin oficial investigado:** `https://sede.ugr.es`.
- **Entradas:** [paso «Abriendo AutoFirma»][P12] y [requisitos
  técnicos][P12A].
- **Operación:** AutoFirma muestra certificados disponibles para elegir y
  permite acceder/firmar; se admiten contenedores P12/PFX según los requisitos.
- **Protocolo:** evidencia funcional compatible con selección de certificado,
  pero no se ha verificado si usa `selectcert`, `sign`, otro transporte o qué
  callback consume la página.
- **Signing endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P13 — Universidad de Sevilla

- **Organización:** Universidad de Sevilla.
- **Origin oficial investigado:** `https://sede.us.es`.
- **Entrada:** [requisitos técnicos][P13].
- **Operación:** autenticación y firma mediante AutoFirma en sistemas de
  escritorio declarados.
- **Protocolo / endpoint / formato / algoritmo / callback:** no verificados.
- **TLS client auth:** no verificado.
- **Estado:** `BROWSE_ONLY`.

### P14 — REG/RedSARA

- **Organización:** Registro Electrónico General de la AGE / RedSARA.
- **Origin oficial investigado:** `https://reg.redsara.es`.
- **Entradas:** [Registro Electrónico General][P14], [preguntas
  frecuentes][P14A] y [manual oficial][P14B].
- **Operación:** el [bundle lazy][P14C] cargado por la ruta `nuevo-registro`
  firma el XML de resumen antes de guardarlo en el expediente.
- **Protocolo:** `AutoScript.sign`; el [AutoScript público][P14D] incluye
  invocación `afirma://` y bootstrap WSS localhost de escritorio, además del
  wrapper `intent://` Android. El bundle del trámite pasa directamente el XML
  al API.
- **Signing endpoint:** invocación local AutoFirma; la ruta posterior de
  `saveXMLAutoSign` se resuelve en el mapa runtime del API y no se añade como
  endpoint confiable sin valor exacto.
- **Formato / modo:** `XAdES Detached`; el modo adicional no se publica en la
  llamada.
- **Algoritmo:** `SHA512withRSA`.
- **Callback:** éxito consume el primer argumento `signatureB64`, lo guarda
  como `xmlSummarySigned` y después llama a `saveXMLAutoSign`; error cierra el
  flujo como fallo. El certificado no se consume en ese wrapper.
- **TLS client auth:** no verificado; la evidencia de firma no demuestra ni
  descarta un handshake distinto.
- **Comprobación física 2026-07-30:** `Nuevo registro` y `Mis registros`
  conducen a `/es/login` y requieren Cl@ve. La firma XAdES observada pertenece
  al XML de resumen preparado dentro de una solicitud y precede a
  `saveXMLAutoSign`; no existe un gate público no destructivo que pruebe su
  aceptación sin avanzar hacia una actuación administrativa.
- **Estado:** `VERIFIED_CONTRACT / QA_ONLY`; profile exacto y adapter XAdES local
  implementados, sin aceptación E2E del portal. La evidencia del blocker está en
  `docs/e2e/2026-07-30-redsara-e2e-blocked.md`.
- **Evidencia reproducible:** el `main` actual referencia el chunk lazy
  `chunk-64DWZJJG.js`, cuyo SHA-256 revalidado el 2026-07-18 es
  `980d6d49f4d2c660d3f0375fdcd50dcb8743e866403213f759a1c83dcd5382d9`.
  `scripts-IIDJLUBL.js` fue revalidado con SHA-256
  `bd8c89df046876ef6e746f129457bfa37cf2fe45dab9098bcbc86c66d32eb2fe`.
  Un nombre/hash nuevo obliga a revalidar el contrato.

### P15 — Sede Administraciones Públicas / ACCEDA

- **Organización:** plataforma ACCEDA de la Administración General del Estado.
- **Origin oficial investigado:**
  `https://sede.administracionespublicas.gob.es`.
- **Entrada:** [acceso con certificado][P15].
- **Operación:** el helper estático `doSignSolicitud` define la firma de una
  solicitud; `doSign` ofrece una rama genérica gobernada por el formulario. No
  se observó el uso runtime de ese helper en un procedimiento concreto.
- **Protocolo:** `AutoScript.sign` de la [integración ACCEDA][P15A] y el
  [AutoScript][P15B] publicados por el mismo origin; la biblioteca contiene
  `afirma://` y WSS localhost. No se observó configuración portal-specific de
  Storage/Retrieve ni tri-phase.
- **Signing endpoint:** invocación local AutoFirma; endpoint servidor no
  verificado.
- **Formato / modo:** `PAdES`, con extra property
  `format=PAdES Detached` y política `FirmaAGE` para `doSignSolicitud`; la rama
  genérica añade `XAdES Detached` cuando el formulario selecciona XAdES.
- **Algoritmo:** `SHA1withRSA` en `doSignSolicitud`, exclusivamente candidato
  `LEGACY_SHA1`; la rama genérica toma el algoritmo del campo del formulario y
  no se habilita sin una allowlist más estrecha.
- **Callback:**
  `showSignResultCallback(signatureB64, certificateB64, extraData)` deposita la
  firma en `firma_formularioweb`; el error recibe tipo y mensaje.
- **TLS client auth:** no verificado; Cl@ve/certificado de entrada no prueba
  `ClientCertRequest`.
- **Estado:** `VERIFIED_CONTRACT` estático; no implementado y sin E2E.

### P16 — Gobierno de Aragón

- **Organización:** Gobierno de Aragón, SIRAW.
- **Origin oficial investigado:** `https://aplicaciones.aragon.es`.
- **Entrada:** [login público][P16].
- **Operación:** `MiniApplet.sign` de token de acceso y de hash precalculado.
- **Protocolo:** MiniApplet heredado. La [integración de la página][P16B] llama
  a `setServlets` y después a `sign`; el [MiniApplet cargado][P16C] contiene
  `afirma://`, wrapper `intent://` Android y transportes AutoFirma oficiales.
- **Signing endpoints exactos:**
  `https://aplicaciones.aragon.es/siraw/resources/js-signature-storage/StorageService`
  y
  `https://aplicaciones.aragon.es/siraw/resources/js-signature-retriever/RetrieveService`.
- **Formato / modo:** `CAdES` / `explicit`; el flujo de hash declara
  `precalculatedHashAlgorithm=SHA1`.
- **Algoritmo:** `SHA1withRSA`, solo candidato `LEGACY_SHA1`.
- **Callback:** éxito recibe firma y certificado, guarda solo la firma en un
  campo controlado y provoca el submit; error recibe tipo y mensaje.
- **TLS client auth:** no verificado; la integración de firma de token/hash no
  demuestra ni descarta otro flow.
- **Implementación 2026-07-28:** profile `aragon-siraw` `QA_ONLY`, origin y
  entrada exactos, bridge MiniApplet y adapter CAdES local limitados al login
  público. El adapter exige challenge de 20 bytes, `SHA1withRSA`, `CAdES`
  detached y propiedades exactas `mode=explicit` + `filter=nonexpired`; valida
  el CMS/CAdES y rechaza manipulación antes de devolverlo.
- **Límite:** Storage/Retrieve y la rama documental con hash precalculado
  permanecen deshabilitados. Su presencia en el JS no concede endpoint ni
  capability runtime.
- **E2E 2026-07-28:** el portal real aceptó la firma CAdES de autenticación y
  continuó al área interna observada. La confirmación fue manual y el resultado
  se documentó sin conservar certificado, firma, cookies ni credenciales.
- **Estado:** `VERIFIED_E2E` exclusivamente para el login CAdES observado.
  Storage/Retrieve, firma documental y cualquier presentación administrativa
  permanecen fuera del alcance.

### P17 — Universidad de Zaragoza

- **Organización:** Universidad de Zaragoza, Tramitador.
- **Origin oficial investigado:** `https://tramita.unizar.es`.
- **Entrada:** [acceso ciudadano][P17].
- **Operación:** la autenticación firma un challenge de sesión precalculado;
  el mismo integration JS contiene operaciones documentales adicionales que
  no se habilitan para el profile de autenticación.
- **Protocolo:** el [integration JS][P17A] usa `AutoScript.sign`; en móvil añade
  un `serverUrl` tri-phase. La página carga [AutoScript][P17B] y configura
  también Storage/Retrieve. La existencia de ramas `coSign` y `counterSign` en
  el integration JS no prueba que esta entrada las use.
- **Signing endpoints exactos:**
  `https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService`,
  `https://tramita.unizar.es/afirma-signature-storage/StorageService` y
  `https://tramita.unizar.es/afirma-signature-retriever/RetrieveService`.
- **Formato / algoritmo:** los campos públicos de la entrada observada fijan
  `CAdES` y `SHA1withRSA`; las propiedades declaran
  `precalculatedHashAlgorithm=SHA1`. Es una candidata `LEGACY_SHA1`, no un
  default general.
- **Callback:** éxito recibe `(firma, certificado)`, conserva el certificado
  público para coherencia dentro del flujo y entrega la firma al formulario;
  error recibe `(tipo, mensaje)`.
- **TLS client auth:** no verificado; el flow observado es firma de challenge,
  no una captura de autenticación TLS.
- **E2E 2026-07-30:** la aplicación completó PRE, firma RSA local, POST y
  callback; el portal aceptó la autenticación y abrió el `Buzón Electrónico`
  interno con el bloque `Mis Gestiones`. La ejecución terminó sin iniciar ni
  modificar un trámite administrativo.
- **Estado:** profile version 2, `VERIFIED_E2E / ENABLED`, exclusivamente para
  el login CAdES observado. El profile solo admite el origin exacto, el
  challenge precalculado de 20 bytes, `CAdES`, `SHA1withRSA`, las dos
  propiedades observadas y el endpoint `SignatureService` exacto. No habilita
  `afirma://`, Storage/Retrieve, co-sign, counter-sign ni firma documental.
- **Privacidad de la comprobación:** el challenge efímero, certificado, firma,
  cookies y datos personales no se conservaron. Las capturas originales no se
  incorporan al repositorio. La evidencia sanitizada está en
  `docs/e2e/2026-07-30-unizar-auth-success.md`.

### P19 — Carné Joven Europeo de Andalucía

- **Organización:** Instituto Andaluz de la Juventud.
- **Entrada:** [aplicación oficial][P19B], enlazada desde el
  [procedimiento 24721][P19].
- **Contrato de acceso:** el [CallAuthenticationServlet][P19C] redirige a
  `https://ws235.juntadeandalucia.es/authenticationFacade` con
  `action=validateCert`, `appId=IAJ.CARNETJOVEN`, callback exacto a ws104 y dos
  identificadores efímeros no retenidos. El handshake TLS 1.2 de la
  [facade][P19D] emite `CertificateRequest` sin lista de CA.
- **Profile:** `CLIENT_TLS_AUTH` solamente. La facade compartida requiere grant
  one-shot ligado al profile, transición top-level exacta, navigation epoch y
  TTL. Navegación directa a ws235 queda `BROWSE_ONLY` y no entrega certificado.
- **Firma posterior:** la documentación menciona AutoFirma, pero el runtime
  tuple, payload, algoritmo, endpoint y callback están detrás del login; no se
  habilita `SIGN`, MiniApplet ni `AFIRMA_URI` por semejanza.
- **Estado:** `VERIFIED_E2E` para `CLIENT_TLS_AUTH` (verificado en dispositivo físico 2026-07-21 tras dc3c231; tanto la Zona privada como el flujo Solicitar Carné Joven alcanzaron confirmación nativa y autenticación exitosa en portal). La firma, AutoFirma, presentación jurídica o solicitud completada posterior no se afirman como E2E.

## 5. Decisiones derivadas para el catálogo

1. El catálogo conserva el ID production inicial `junta-andalucia`, todavía
   `EXPERIMENTAL`, junto a los perfiles contractuales más recientes.
2. Las fichas `BROWSE_ONLY` no contienen endpoints, callbacks ni capabilities
   de certificado. Pueden aportar accesos HTTPS visibles, nunca confianza.
3. REG permanece como entrada QA limitada por contrato y no se anuncia como
   aceptada por el portal: Cl@ve y una solicitud administrativa real son
   precondiciones del XAdES observado. ACCEDA permanece como candidato estático.
   Aragón SIRAW y UniZAR están habilitados solo para sus logins CAdES verificados
   E2E; Storage/Retrieve, firma documental y presentación continúan bloqueados.
4. AEAT es candidata a la primera investigación de `CLIENT_TLS_AUTH`, pero no
   entra como profile confiable hasta observar host, puerto, key types, issuer
   constraints, frame/origin y resultado real.
5. Comunidad de Madrid es candidata a `LOCAL_PADES`; primero debe verificarse
   el formato de firma que acepta el registro y el contrato de subida.
6. Universidad de Granada es candidata a un contrato alternativo de selección
   de certificado; hace falta inspeccionar el JavaScript público y ejecutar un
   flujo no destructivo antes de escribir el adapter.
7. No se habilitan `COSIGN`, `COUNTERSIGN`, `LOCAL_XADES`, FacturaE,
   Storage/Retrieve ni WSS por mera presencia en Cliente @firma.
8. Cl@ve Firma, firma remota FNMT, DNIe NFC, smart cards, HSM y APIs cerradas
   siguen fuera del alcance.

## 6. Privacidad de la evidencia

Esta matriz y el futuro informe de compatibilidad solo pueden almacenar:
origin exacto, path público cuando sea necesario, operación cerrada, nombres
de métodos/content-types, tamaños, hashes cortos no reversibles y estado. No se
guardan cookies, cabeceras de autorización, query secrets, payloads de
documentos, respuestas completas con identificadores, datos personales,
certificados, seriales, issuer DN completos, URI P12, contraseñas, firmas ni
claves privadas.

Limitación de disponibilidad: la fuente P11 devolvió HTTP 403 al cliente
automatizado durante la revalidación del 2026-07-15. Se mantiene como entrada
oficial previamente inspeccionada, pero no soporta ninguna afirmación técnica
de protocolo y por eso continúa `BROWSE_ONLY`.

## 7. Fuentes primarias

### Contratos de Cliente @firma, firma-android y Android

[C0]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/README.md#L96-L120
[C1]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js#L689-L715
[C2]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js#L2871-L2907
[C3]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/miniapplet_JA.js#L520-L670
[C4]: https://github.com/ctt-gob-es/firma-android/blob/6f6554bd5beb4f2ecb403da1979a7eb07e4a2c0c/afirma-ui-android/app/src/main/AndroidManifest.xml#L183-L256
[C5]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-simple/src/main/java/es/gob/afirma/standalone/protocol/ProtocolInvocationLauncher.java#L211-L336
[C6]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js#L4992-L5020
[C7]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js#L1061-L1127
[C8]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-ui-miniapplet-deploy/src/main/webapp/js/autoscript.js#L2453-L2639
[C9]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-signature-storage/src/main/java/es/gob/afirma/signfolder/server/proxy/StorageService.java#L30-L140
[C10]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-signature-retriever/src/main/java/es/gob/afirma/signfolder/server/proxy/RetrieveService.java#L27-L99
[C11]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-crypto-xadestri-client/src/main/java/es/gob/afirma/signers/xadestri/client/AOXAdESTriPhaseSigner.java#L405-L597
[C12]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-server-triphase-signer/src/main/java/es/gob/afirma/triphase/server/SignatureService.java#L73-L99
[C13]: https://github.com/ctt-gob-es/firma-android/blob/6f6554bd5beb4f2ecb403da1979a7eb07e4a2c0c/afirma-ui-android/app/build.gradle#L67-L80
[C14]: https://github.com/ctt-gob-es/firma-android/blob/6f6554bd5beb4f2ecb403da1979a7eb07e4a2c0c/afirma-ui-android/app/src/main/java/es/gob/afirma/android/crypto/SignTask.java#L194-L216
[C15]: https://developer.android.com/reference/android/webkit/ClientCertRequest
[C16]: https://developer.android.com/reference/android/security/KeyChain
[C18]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-server-triphase-signer-core/src/main/java/es/gob/afirma/triphase/signer/processors/PreProcessorFactory.java#L15-L46
[C19]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-core/src/main/java/es/gob/afirma/core/signers/AOSignConstants.java#L59-L75
[C20]: https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-core/src/main/java/es/gob/afirma/core/misc/protocol/UrlParametersToSign.java#L63-L78

### Portales

[P01]: https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1
[P01B]: https://sede.administracion.gob.es/PAG_Sede/LaSedePAG/SistemasFirmaAceptados.html?hc=1
[P02]: https://sede.agenciatributaria.gob.es/Sede/certificado-dni-electronico.html
[P02A]: https://sede.agenciatributaria.gob.es/Sede/ayuda/consultas-informaticas/firma-digital-sistema-clave-pin-tecnica/certificados-electronicos-dispositivos-moviles/android-cuestiones-generales-uso-certificados.html
[P02B]: https://sede.agenciatributaria.gob.es/Sede/ayuda/consultas-informaticas/otros-servicios-ayuda-tecnica/documentos-pendientes-firma.html
[P02C]: https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html
[P02D]: https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso
[P03]: https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RequisitosTecnicos/requisitos%2Bde%2Bfirma%2Belectronica/autofirma?changeLanguage=es
[P03A]: https://portal.seg-social.gob.es/wps/portal/importass/importass/ayuda
[P04]: https://sede.sepe.gob.es/portalSede/firma-electronica/preguntas-frecuentes/autofirma
[P05]: https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/index.html
[P06]: https://sedejudicial.justicia.es/firma-y-certificados-electronicos-admitidos
[P06A]: https://sedejudicial.justicia.es/documents/20142/72138908/202408_Escrito%2Biniciador%2Bde%2Bjurisdicci%C3%B3n%2Bvoluntaria_ciudadan%C3%ADa_V3.pdf/72c096fe-0e01-2293-fb9a-c9862dca89f0?t=1727245303785
[P06B]: https://sede.mjusticia.gob.es/informacion-ayuda/preguntas-frecuentes
[P06C]: https://sede.mjusticia.gob.es/tramites/organos-gobierno
[P07]: https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs
[P08]: https://sede.comunidad.madrid/guia-tramitacion/realizo-solicitud
[P08A]: https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid
[P08B]: https://gestiona2.comunidad.madrid/gpse_solicitud/accesos.jsf?numref=2094
[P09]: https://www.sede.diputaciondevalladolid.es/requisitos-tecnicos
[P09A]: https://www.sede.diputaciondevalladolid.es/preguntas-frecuentes
[P10]: https://sede.sevilla.org/opencms/system/modules/sede/contents/faq/Presentacion_Clave
[P10A]: https://sede.sevilla.org/opencms/system/modules/sede/contents/faq/Error_firma
[P11]: https://sede.madrid.es/portal/site/tramites/menuitem.62876cb64654a55e2dbd7003a8a409a0/?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD
[P12]: https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp
[P12A]: https://sede.ugr.es/portal/requisitos/index.html
[P13]: https://sede.us.es/opencms/system/modules/sede/contents/pages/requisitosTecnicos
[P14]: https://reg.redsara.es/es/
[P14A]: https://reg.redsara.es/preguntas-frecuentes
[P14B]: https://reg.redsara.es/es/media/es/REG-ManualUsuario.pdf
[P14C]: https://reg.redsara.es/es/chunk-64DWZJJG.js
[P14D]: https://reg.redsara.es/es/scripts-IIDJLUBL.js
[P15]: https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES
[P15A]: https://sede.administracionespublicas.gob.es/js/afirma/afirma_funciones.js
[P15B]: https://sede.administracionespublicas.gob.es/js/afirma/autoscript.js
[P16]: https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw
[P16B]: https://aplicaciones.aragon.es/siraw/javax.faces.resource/js/afirma.js.xhtml
[P16C]: https://aplicaciones.aragon.es/siraw/resources/js/miniapplet.js
[P17]: https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent
[P17A]: https://tramita.unizar.es/tramitador/js/implementaciones/implementacionIFirma_ES.js
[P17B]: https://tramita.unizar.es/tramitador/js/miniAppletFirma/autoscript.js
[P19]: https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html
[P19B]: https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp
[P19C]: https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet
[P19D]: https://ws235.juntadeandalucia.es/authenticationFacade
