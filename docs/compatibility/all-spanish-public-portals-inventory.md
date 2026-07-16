# Inventario evolutivo de portales públicos españoles

- Fecha del snapshot: 2026-07-16
- `inventory_schema_version`: `2`
- `snapshot_id`: `2026-07-16-seed-2`
- Fecha de corte de la matriz de firma de origen: 2026-07-15

Este documento es un **censo de descubrimiento evolutivo**, no una afirmación
matemáticamente demostrable de que se hayan enumerado todos los sitios,
subdominios o trámites públicos de España. En este inventario, «todos» significa
todos los registros descubiertos dentro de las fuentes enumeradoras y olas de
revisión que consten como completadas en el snapshot.

El inventario separa tres hechos que no son equivalentes:

1. que una institución pública exista;
2. que se haya verificado una superficie web pública oficial;
3. que exista un contrato de autenticación o firma compatible y validado.

Una entrada aquí no crea un `SiteProfile`, no amplía una allowlist, no activa
bridge, certificado, clave privada, autenticación TLS cliente ni firma. La
matriz `docs/compatibility/spanish-government-signing-matrix.md` aporta la
evidencia técnica del seed, pero este inventario usa una taxonomía propia de
ocho estados. El mapeo es explícito y no promociona ninguna superficie.

## 1. Unidad censal, alcance y exclusiones

La unidad censal es una **superficie pública**: un origin HTTPS exacto y una
entrada pública estable con una función identificable para la ciudadanía. Una
institución puede tener varias superficies si usa origins o fronteras de
confianza distintas. Por ejemplo, la sede general de la Comunidad de Madrid y
el frontend `gestiona2` son dos registros.

Se incluyen:

- sedes electrónicas;
- portales generales o sectoriales de servicios;
- frontends públicos de trámites;
- portales públicos de autenticación o firma;
- sedes de universidades públicas españolas.

No se cuentan como superficie independiente:

- un endpoint técnico sin entrada ciudadana, como PRE/POST,
  Storage/Retrieve o un callback;
- un redirect auxiliar o un CDN;
- Cl@ve, VALIDe u otra dependencia compartida cuando solo aparece enlazada;
- intranets, áreas autenticadas no observables sin credenciales o APIs
  privadas;
- entidades privadas, universidades privadas, portales europeos o de otros
  países;
- un resultado de buscador sin confirmación en una fuente oficial.

Los endpoints técnicos permanecen en la matriz de compatibilidad y podrán
tener un inventario de dependencias separado. No se mezclan con el denominador
de portales ciudadanos.

## 2. Esquema de registro v1

| Campo | Obligatorio | Regla v1 |
| --- | --- | --- |
| `inventory_id` | Sí | ID ASCII estable `ES-PUB-NNNN`; nunca se reutiliza. |
| `surface_key` | Sí | Slug estable de la superficie, independiente del dominio. |
| `administrative_level` | Sí | Uno de `ESTATAL`, `AUTONOMICO`, `PROVINCIAL`, `MUNICIPAL`, `UNIVERSIDAD_PUBLICA`. |
| `institution_name` | Sí | Nombre publicado por la institución o el enumerador oficial. |
| `surface_name` | Sí | Nombre visible del portal, sede o frontend. |
| `surface_type` | Sí | Uno de `SEDE`, `PORTAL_SERVICIO`, `FRONTEND_TRAMITE`, `PORTAL_AUTENTICACION`. |
| `origin` | Sí | `https://host[:port]` canónico, sin path, query, fragment, userinfo, wildcard ni trailing dot. Puerto omitido significa solo 443. |
| `entry_url` | Sí | URL pública estable. Solo admite query si es un selector público no efímero y no secreto. |
| `discovery_state` | Sí | Estado de mantenimiento definido en §3.1. |
| `autonomous_community` | Sí | Comunidad o ciudad autónoma, o `NO_APLICA` para una superficie estatal. |
| `province_or_municipality` | Sí | Provincia o municipio acreditado, o `NO_APLICA` cuando el ámbito no es local/provincial. |
| `official_site` | Sí | URL oficial verificada de la superficie; no se infiere una homepage corporativa distinta. |
| `e_sede` | Sí | URL de sede electrónica acreditada o `NO_VERIFICADO`. |
| `procedure_page` | Sí | Página pública de procedimiento/trámite acreditada o `NO_VERIFICADO`; no se sustituye por una FAQ. |
| `certificate_required` | Sí | Uno de `SI`, `NO`, `CONDICIONAL`, `NO_VERIFICADO`; describe solo el flujo documentado. |
| `signature_required` | Sí | Uno de `SI`, `NO`, `CONDICIONAL`, `NO_VERIFICADO`; autenticarse con certificado no implica firmar. |
| `js_client` | Sí | Cliente JavaScript exacto (`AutoScript`, `MiniApplet`) o `NO_VERIFICADO`. |
| `protocol_family` | Sí | Familia delimitada por evidencia o `NO_VERIFICADO`; una marca de producto no basta. |
| `signature_format` | Sí | Formato y modo acreditados o `NO_VERIFICADO`; un documento PDF no prueba PAdES. |
| `endpoint` | Sí | Endpoint servidor exacto, lista cerrada o `NO_VERIFICADO`; una invocación local no se inventa como URL. |
| `inventory_status` | Sí | Uno y solo uno de los ocho estados definidos en §3.2. |
| `operation_summary` | Sí | Operación acreditada o `NO_VERIFICADO`; no se infiere por semejanza. |
| `protocol_evidence` | Sí | Contrato exacto, mención documental delimitada o `NO_VERIFICADO`. |
| `client_tls_auth` | Sí | Uno de los valores de §3.3. Una mención a certificado no prueba TLS cliente. |
| `evidence_ids` | Sí | Al menos una fuente primaria registrada en §6. |
| `reason` | Sí | Razón concreta para todo estado sin E2E, dato no confirmado o protocolo no soportado. |
| `reviewed_at` | Sí | Fecha UTC `YYYY-MM-DD` de la última revisión material. |
| `next_gate` | Sí | Próxima evidencia necesaria; nunca una promesa de compatibilidad. |
| `notes` | No | Solo metadatos no sensibles y limitaciones reproducibles. |

Reglas de integridad:

- `inventory_id` es único; la pareja `(origin, surface_key)` también es única.
- Dos superficies del mismo origin solo se separan cuando tienen una entrada y
  una frontera funcional distintas; no se duplican por idioma o path cosmético.
- Un redirect cross-origin estable crea un candidato separado antes de recibir
  cualquier estado revisado.
- Cada registro de §7 declara individualmente todos los campos obligatorios;
  los valores globales no sustituyen campos omitidos.
- Todo dato desconocido se escribe `NO_VERIFICADO`; no se completa desde otro
  portal que use la misma plataforma o marca AutoFirma.
- Una fuente oficial de existencia no prueba que la URL sea una sede; una sede
  no prueba firma; un JavaScript estático no prueba aceptación E2E.
- Un cambio de origin, contrato, algoritmo, endpoint o propietario genera una
  revisión nueva, no una edición silenciosa del hecho histórico.
- Ningún consumidor debe transformar automáticamente este inventario en el
  catálogo de confianza de la aplicación.

## 3. Leyenda y estados

### 3.1. Metadato de mantenimiento `discovery_state`

Esta dimensión registra el ciclo de vida del censo y es ortogonal al único
`inventory_status` de §3.2; no expresa compatibilidad:

| Estado | Significado |
| --- | --- |
| `CANDIDATE` | Aparece en una fuente enumeradora oficial; todavía no se ha confirmado una entrada HTTPS de la institución. |
| `DISCOVERED` | Se verificaron propietario y origin, pero falta revisar operación, fuentes de ayuda y límites. |
| `REVIEWED` | Entrada pública, propietario, origin, evidencia mínima y clasificación fueron revisados y registrados. No implica compatibilidad. |
| `RECHECK_REQUIRED` | Un registro antes revisado cambió, contradice otra fuente o no pudo revalidarse de forma segura. Conserva su compatibilidad anterior sin promoción. |
| `RETIRED` | Una fuente oficial confirma retirada o sustitución. El registro se conserva para trazabilidad y sale del conjunto activo. |

### 3.2. Estado del inventario

Esta taxonomía es propia del inventario. No es idéntica a la taxonomía
histórica de la matriz ni constituye una escala automática:

| Estado | Significado operativo |
| --- | --- |
| `VERIFIED_E2E` | Un flujo real, seguro y delimitado fue aceptado por el portal y existe evidencia sanitizada de esa aceptación. No exige participación manual si la automatización preserva consentimiento, límites y prueba de resultado. |
| `IMPLEMENTED_NOT_E2E` | Existe implementación delimitada y evidencia portal-specific, pero no se ha probado que el portal acepte el resultado del flujo real. |
| `VERIFIED_CONTRACT` | Fuentes oficiales prueban un contrato técnico estático; no prueban implementación ni aceptación del portal. |
| `REQUIRES_AUTHENTICATED_RESEARCH` | La evidencia pública se agotó y el siguiente hecho técnico solo puede investigarse de forma autenticada y controlada. No autoriza credenciales ni soporte. |
| `BROWSE_ONLY` | La superficie oficial puede inventariarse/navegarse, pero no hay contrato suficiente para exponer certificado, bridge, client-auth o firma. |
| `UNSUPPORTED_PROTOCOL` | Evidencia oficial demuestra que el flujo/protocolo observado no puede soportarse en el contorno definido; la razón debe constar por registro. |
| `INACCESSIBLE` | No se puede alcanzar o revalidar de forma segura y repetible la superficie oficial; no se hacen afirmaciones técnicas mientras persista. |
| `DEPRECATED` | Una fuente oficial confirma retirada o sustitución del flujo; se conserva únicamente para trazabilidad. |

No hay ningún `VERIFIED_E2E` en este snapshot. Tests locales, hashes de JS y
revisión documental nunca producen ese estado. La implementación Junta se
mapea a `IMPLEMENTED_NOT_E2E`; los dos flujos móviles que la matriz marcaba
como no soportados se mapean a `UNSUPPORTED_PROTOCOL`, con razón específica en
cada ficha. No se asigna `REQUIRES_AUTHENTICATED_RESEARCH` sin demostrar antes
que la investigación pública segura se agotó.

### 3.3. Autenticación TLS cliente

| Valor | Significado |
| --- | --- |
| `VERIFIED` | Se observó y delimitó una petición TLS cliente exacta con host, puerto, key types, principals, consentimiento y resultado. |
| `NO_EN_CONTORNO_OBSERVADO` | La operación revisada no incluyó client-auth; no afirma que todo el portal carezca de él. |
| `NO_VERIFICADO` | No hay evidencia suficiente para afirmar presencia ni ausencia. |
| `NO_SOPORTABLE_TLS` | El flujo TLS se investigó y no puede exponerse con las garantías del producto. |

## 4. Contabilidad de cobertura del snapshot

El denominador nacional todavía es desconocido. Por tanto, no se publica un
porcentaje «de España». El único porcentaje cerrado de este milestone es la
cobertura del seed heredado de la matriz: 20 superficies descubiertas y
revisadas. Los diez enumeradores están registrados como provenance, pero sus
colas completas siguen pendientes; no se cuentan como ingeridos por haber
abierto su página índice.

| Métrica | Resultado |
| --- | ---: |
| Registros seed esperados desde la matriz | 20 |
| Registros seed inventariados | 20/20 (100 % del seed, no del país) |
| Origins primarios distintos | 20 |
| Fuentes enumeradoras oficiales registradas | 10 |
| Colas enumeradoras ingeridas de extremo a extremo | 0/10 |
| Colas enumeradoras pendientes de ingestión | 10/10 |
| Fuentes oficiales portal-specific registradas | 39 |
| Fuentes oficiales totales registradas | 49 |
| Entradas `VERIFIED_E2E` | 0 |
| Evidencia exacta de `ClientCertRequest` | 0 |

Por nivel administrativo:

| Nivel | Registros |
| --- | ---: |
| `ESTATAL` | 10 |
| `AUTONOMICO` | 4 |
| `PROVINCIAL` | 1 |
| `MUNICIPAL` | 2 |
| `UNIVERSIDAD_PUBLICA` | 3 |
| **Total** | **20** |

Por estado del inventario:

| Estado | Registros |
| --- | ---: |
| `VERIFIED_E2E` | 0 |
| `IMPLEMENTED_NOT_E2E` | 1 |
| `VERIFIED_CONTRACT` | 4 |
| `REQUIRES_AUTHENTICATED_RESEARCH` | 0 |
| `BROWSE_ONLY` | 13 |
| `UNSUPPORTED_PROTOCOL` | 2 |
| `INACCESSIBLE` | 0 |
| `DEPRECATED` | 0 |
| **Total** | **20** |

Por mantenimiento del inventario:

| Estado | Registros |
| --- | ---: |
| `REVIEWED` | 19 |
| `RECHECK_REQUIRED` | 1 |
| `CANDIDATE`, `DISCOVERED`, `RETIRED` | 0 |
| **Total** | **20** |

## 5. Método de descubrimiento reproducible

### 5.1. Enumeración institucional

Cada ola comienza con un snapshot fechado de enumeradores oficiales:

1. El Punto de Acceso General lista categorías de portales públicos [D01],
   ministerios de la AGE [D02], comunidades y ciudades autónomas [D03] y
   entidades locales [D04]. Sus índices de ayuntamientos [D05] y diputaciones
   [D06] sirven como colas territoriales, no como prueba de un protocolo.
2. SIA se usa como catálogo oficial de procedimientos y servicios [D07]. Una
   ficha SIA puede descubrir un frontend de trámite, pero su URL debe
   confirmarse en la institución responsable.
3. DIR3 se usa para identidad y deduplicación de unidades, organismos y
   oficinas [D08]. DIR3 no garantiza que una unidad tenga portal propio.
4. INVENTE amplía la cola a entidades del sector público institucional estatal,
   autonómico y local [D09]. Existencia jurídica y portal web se verifican por
   separado.
5. RUCT identifica universidades y permite filtrar las públicas [D10]. La sede
   electrónica se confirma después en el dominio oficial de cada universidad.

Un motor de búsqueda puede ayudar a localizar una página dentro de un dominio
ya conocido, pero el resultado o snippet nunca se registra como evidencia.

### 5.2. Verificación de propietario y superficie

Para cada candidato:

1. confirmar el nombre y nivel en un enumerador oficial;
2. seguir únicamente enlaces públicos o navegación oficial hasta la entrada;
3. resolver redirects con límite cerrado y registrar cada cambio de origin;
4. normalizar el origin y crear un `surface_key` estable;
5. comprobar que la página identifica a la institución o que una página
   oficial de esta enlaza la superficie;
6. separar sede general, portal sectorial y frontend de trámite cuando sus
   origins o riesgos difieran;
7. clasificar inicialmente sin certificado, bridge ni firma.

Las comprobaciones automatizadas usan primero `HEAD` y, si el servidor no lo
admite, un `GET` público sin autenticación, cookies persistentes ni envío de
formularios. Se limita la frecuencia, redirects y tamaño. Los cuerpos se
descartan al terminar la comprobación.

### 5.3. Investigación de contrato

Solo se inspeccionan páginas públicas, ayuda oficial, manuales oficiales,
JavaScript servido por el portal y repositorios oficiales fijados a commit.
Para JS se puede registrar URL, fecha, tamaño y hash; no se almacenan payloads
de sesión. Una mención a AutoFirma, @firma, Cl@ve, certificado, P12/PFX o DNIe
mantiene el portal `BROWSE_ONLY` mientras no revele un ABI exacto.

Clasificación del inventario:

- `VERIFIED_CONTRACT` requiere operación, origin, protocolo y parámetros
  relevantes demostrados por fuente oficial;
- `IMPLEMENTED_NOT_E2E` requiere además una implementación delimitada, pero no
  se confunde con aceptación;
- `VERIFIED_E2E` exige un flujo seguro y delimitado en el portal real,
  respuesta aceptada y evidencia sanitizada; la intervención manual no es un
  requisito del estado;
- `REQUIRES_AUTHENTICATED_RESEARCH` solo se usa después de documentar por qué
  ninguna comprobación pública segura puede resolver el gap;
- cualquier ambigüedad conserva el estado anterior o baja a una capacidad más
  restrictiva.

### 5.4. Delta, deduplicación y revalidación

Cada ola produce:

- fecha y versión de cada enumerador;
- candidatos nuevos, desaparecidos y con URL cambiada;
- registros añadidos, fusionados, separados o marcados para revalidación;
- conteos por fuente, nivel, `discovery_state` e `inventory_status`;
- lista explícita de enumeradores o territorios pendientes.

Un HTTP 4xx/5xx persistente, TLS no validable, redirect cross-origin nuevo,
cambio de hash en JS contractual o contradicción documental cambia
`discovery_state` a `RECHECK_REQUIRED`. No se borra el registro ni se sustituye
la evidencia por una inferencia. Todo contrato se revalida antes de
implementarlo y antes de una release que dependa de él.

## 6. Registro de fuentes primarias

### 6.1. Enumeradores para futuras olas

| ID | Propietario | Cobertura y uso |
| --- | --- | --- |
| [D01] | Punto de Acceso General | Índice raíz de portales públicos estatales, autonómicos, locales, judiciales y otros organismos. |
| [D02] | Punto de Acceso General | Ministerios y portales de la AGE. |
| [D03] | Punto de Acceso General | Las 17 comunidades autónomas y Ceuta/Melilla. |
| [D04] | Punto de Acceso General | Índice de entidades locales. |
| [D05] | Punto de Acceso General | Cola territorial de ayuntamientos. |
| [D06] | Punto de Acceso General | Cola de diputaciones provinciales; remite a cabildos/consejos donde corresponda. |
| [D07] | Punto de Acceso General / SIA | Catálogo oficial de procedimientos y servicios. |
| [D08] | Secretaría General de Administración Digital | DIR3: unidades, organismos y oficinas; identidad/deduplicación. |
| [D09] | IGAE | INVENTE: entidades del sector público estatal, autonómico y local. |
| [D10] | Ministerio de Ciencia, Innovación y Universidades | RUCT: universidades oficiales; se filtran solo las públicas. |

Ningún enumerador anterior es por sí solo un catálogo completo de origins.
Se cruzan porque cada uno cubre una dimensión distinta.

### 6.2. Evidencia del seed

Los IDs se mantienen alineados con la matriz para que una revalidación no
requiera traducción manual.

| Ficha | Institución o superficie | Fuentes oficiales | Tipo de evidencia |
| --- | --- | --- | --- |
| `P01` | PAG / enlace al REG-AGE | [P01][P01B] | Entrada y sistemas de firma aceptados; sin ABI. |
| `P02` | AEAT | [P02][P02A][P02B] | Certificado en navegador/Android y fase documental de firma; sin `ClientCertRequest` exacto. |
| `P03` | Seguridad Social / Import@ss | [P03][P03A] | Ayuda oficial; AutoFirma móvil rechazada en la Sede y métodos de acceso Import@ss. |
| `P04` | SEPE | [P04] | FAQ oficial de AutoFirma de escritorio. |
| `P05` | DGT | [P05] | Verificación de equipo, certificado y mención de AutoFirma. |
| `P06` | Sede Judicial / Ministerio de Justicia | [P06][P06A][P06B][P06C] | Ayuda, guía PDF y trámite oficial; ABI no publicado. |
| `P07` | Junta de Andalucía / Ovorion | [P07] | Entrada pública portal-specific; contrato detallado en la matriz y observaciones locales redactadas. |
| `P08` | Comunidad de Madrid / gestiona2 | [P08][P08A][P08B] | Guía local-PDF y frontend que bloquea móvil. |
| `P09` | Diputación de Valladolid | [P09][P09A] | Certificados admitidos y explicación conceptual de firma. |
| `P10` | Ayuntamiento de Sevilla | [P10][P10A] | Requisito de certificado vigente y AutoFirma. |
| `P11` | Ayuntamiento de Madrid | [P11] | Procedimiento oficial previamente inspeccionado; revalidación automatizada pendiente. |
| `P12` | Universidad de Granada | [P12][P12A] | Pantalla AutoFirma y requisitos; transporte exacto no probado. |
| `P13` | Universidad de Sevilla | [P13] | Requisitos de autenticación/firma de escritorio. |
| `P14` | REG/RedSARA | [P14][P14A][P14B][P14C][P14D] | Entrada, manual y JS público con contrato estático. |
| `P15` | ACCEDA | [P15][P15A][P15B] | Entrada y helper/AutoScript públicos; uso runtime del helper no observado. |
| `P16` | Gobierno de Aragón / SIRAW | [P16][P16B][P16C] | Entrada y JS público con MiniApplet y Storage/Retrieve. |
| `P17` | Universidad de Zaragoza | [P17][P17A][P17B] | Entrada e integration JS con firma de challenge y tri-phase móvil. |

### 6.3. Disponibilidad en este snapshot

La comprobación pública del 2026-07-16 obtuvo HTTP 200 mediante `GET` directo
en 46 de las 49 URLs registradas, después de un único retry de P07. Las tres
excepciones se conservan con la limitación exacta:

- D09 cerró la conexión del cliente CLI, aunque una lectura HTTPS
  independiente recuperó la página oficial de INVENTE;
- D10 no pudo validar la cadena CA con el almacén local de `curl`, aunque una
  lectura HTTPS independiente recuperó la página oficial de RUCT; no se usó
  `--insecure` ni se debilitó TLS;
- P11 respondió HTTP 403 al cliente CLI. Una lectura web independiente mostró
  la página oficial, pero el registro permanece `RECHECK_REQUIRED` porque la
  comprobación automatizada no es repetible sin cambiar de transporte.

No se siguió ninguna ruta autenticada ni se intentó eludir esas respuestas.

## 7. Registros seed

Los registros se publican como un único bloque YAML para que cada campo sea
explícito y el snapshot pueda validarse de forma mecánica. `official_site`
designa la URL oficial verificada de esta superficie, no una homepage
institucional inferida. `procedure_page` queda `NO_VERIFICADO` cuando las
fuentes solo aportan ayuda o requisitos.

```yaml
records:
  - inventory_id: "ES-PUB-0001"
    surface_key: "age-pag-reg"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Administración General del Estado"
    surface_name: "Punto de Acceso General / Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://sede.administracion.gob.es"
    official_site: "https://sede.administracion.gob.es/"
    e_sede: "https://sede.administracion.gob.es/"
    entry_url: "https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1"
    procedure_page: "https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Identificación y presentación con certificado/DNIe o Cl@ve."
    protocol_evidence: "La fuente acredita sistemas de firma aceptados, pero no ABI ni transporte portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P01", "P01B"]
    reason: "ABI, formato, endpoint y TLS cliente no verificados; solo se autoriza navegación."
    reviewed_at: "2026-07-15"
    next_gate: "Capturar un trámite y transporte exactos sin credenciales."

  - inventory_id: "ES-PUB-0002"
    surface_key: "age-reg-redsara"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Administración General del Estado"
    surface_name: "Registro Electrónico General / RedSARA"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://reg.redsara.es"
    official_site: "https://reg.redsara.es/es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://reg.redsara.es/es/"
    procedure_page: "https://reg.redsara.es/es/"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_LOCAL"
    signature_format: "XAdES Detached"
    signature_algorithm: "SHA512withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_CONTRACT"
    operation_summary: "Firma del XML de resumen antes de guardarlo en el expediente."
    protocol_evidence: "AutoScript.sign estático; el wrapper consume signatureB64 y llama a saveXMLAutoSign."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P14", "P14B", "P14C", "P14D"]
    reason: "Contrato JS estático probado; endpoint servidor runtime, implementación y aceptación E2E no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Fixture sanitizada, adapter limitado y E2E propio."

  - inventory_id: "ES-PUB-0003"
    surface_key: "age-acceda"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Administración General del Estado"
    surface_name: "Plataforma ACCEDA"
    surface_type: "SEDE"
    origin: "https://sede.administracionespublicas.gob.es"
    official_site: "https://sede.administracionespublicas.gob.es/"
    e_sede: "https://sede.administracionespublicas.gob.es/"
    entry_url: "https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_LOCAL"
    signature_format: "PAdES (format=PAdES Detached); rama genérica XAdES Detached"
    signature_algorithm: "SHA1withRSA en doSignSolicitud; rama genérica NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_CONTRACT"
    operation_summary: "Helper estático para firmar una solicitud; rama genérica gobernada por formulario."
    protocol_evidence: "AutoScript.sign y callbacks publicados por el origin; uso runtime en un procedimiento concreto no observado."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P15", "P15A", "P15B"]
    reason: "Contrato estático probado, pero faltan procedimiento exacto, endpoint servidor, implementación y aceptación E2E."
    reviewed_at: "2026-07-15"
    next_gate: "Demostrar invocación portal-specific en un procedimiento y después E2E."

  - inventory_id: "ES-PUB-0004"
    surface_key: "aeat-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal de Administración Tributaria"
    surface_name: "Sede electrónica de la AEAT"
    surface_type: "SEDE"
    origin: "https://sede.agenciatributaria.gob.es"
    official_site: "https://sede.agenciatributaria.gob.es/"
    e_sede: "https://sede.agenciatributaria.gob.es/"
    entry_url: "https://sede.agenciatributaria.gob.es/Sede/certificado-dni-electronico.html"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Autenticación con certificado instalado; algunos servicios incluyen una fase de firma."
    protocol_evidence: "La documentación Android no distingue un ClientCertRequest de otro mecanismo del portal."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P02", "P02A", "P02B"]
    reason: "Host, puerto, key types, issuers, ABI, formato y endpoint no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Observar una petición TLS exacta sin continuar con el certificado."

  - inventory_id: "ES-PUB-0005"
    surface_key: "seguridad-social-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Nacional de la Seguridad Social"
    surface_name: "Sede Electrónica de la Seguridad Social"
    surface_type: "SEDE"
    origin: "https://sede.seg-social.gob.es"
    official_site: "https://sede.seg-social.gob.es/"
    e_sede: "https://sede.seg-social.gob.es/"
    entry_url: "https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RequisitosTecnicos/requisitos%2Bde%2Bfirma%2Belectronica/autofirma?changeLanguage=es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "UNSUPPORTED_PROTOCOL"
    operation_summary: "Firma de trámites de la Sede mediante AutoFirma."
    protocol_evidence: "La fuente oficial documenta AutoFirma de escritorio y declara que esa firma no funciona en móvil."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P03"]
    reason: "El flujo AutoFirma documentado por la propia Sede no funciona en dispositivos móviles."
    reviewed_at: "2026-07-15"
    next_gate: "Mantener bloqueado hasta un cambio oficial y una investigación nueva."

  - inventory_id: "ES-PUB-0006"
    surface_key: "tgss-importass"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Tesorería General de la Seguridad Social"
    surface_name: "Import@ss"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://portal.seg-social.gob.es"
    official_site: "https://portal.seg-social.gob.es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://portal.seg-social.gob.es/wps/portal/importass/importass/ayuda"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Acceso mediante SMS, Cl@ve, certificado electrónico o DNIe."
    protocol_evidence: "La ayuda no publica ABI de firma ni handshake TLS cliente exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P03A"]
    reason: "Firma, transporte de certificado, formato y endpoint no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Identificar la entrada exacta de certificado y su handshake público."

  - inventory_id: "ES-PUB-0007"
    surface_key: "sepe-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Servicio Público de Empleo Estatal"
    surface_name: "Sede electrónica del SEPE"
    surface_type: "SEDE"
    origin: "https://sede.sepe.gob.es"
    official_site: "https://sede.sepe.gob.es/"
    e_sede: "https://sede.sepe.gob.es/"
    entry_url: "https://sede.sepe.gob.es/portalSede/firma-electronica/preguntas-frecuentes/autofirma"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Firma con AutoFirma después de identificarse mediante certificado."
    protocol_evidence: "La FAQ acredita AutoFirma, pero no versión, ABI, callback ni endpoint."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P04"]
    reason: "El requisito de AutoFirma no autoriza un bridge sin contrato técnico exacto."
    reviewed_at: "2026-07-15"
    next_gate: "Inspeccionar un procedimiento público y su JavaScript vigente."

  - inventory_id: "ES-PUB-0008"
    surface_key: "dgt-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Tráfico"
    surface_name: "Sede electrónica de la DGT"
    surface_type: "SEDE"
    origin: "https://sede.dgt.gob.es"
    official_site: "https://sede.dgt.gob.es/"
    e_sede: "https://sede.dgt.gob.es/"
    entry_url: "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/index.html"
    procedure_page: "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/index.html"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Identificación con certificado y firma con AutoFirma en servicios que la requieren."
    protocol_evidence: "La fuente acredita requisitos generales, no un contrato portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P05"]
    reason: "JS cliente, ABI, formato, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Localizar un procedimiento concreto y su contrato técnico."

  - inventory_id: "ES-PUB-0009"
    surface_key: "justicia-sede-judicial"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Administración de Justicia"
    surface_name: "Sede Judicial Electrónica"
    surface_type: "SEDE"
    origin: "https://sedejudicial.justicia.es"
    official_site: "https://sedejudicial.justicia.es/"
    e_sede: "https://sedejudicial.justicia.es/"
    entry_url: "https://sedejudicial.justicia.es/firma-y-certificados-electronicos-admitidos"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Acceso con Cl@ve/certificado y firma de escritos o documentos PDF con AutoFirma."
    protocol_evidence: "PDF es el documento de la guía; no prueba el formato criptográfico de la firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P06", "P06A"]
    reason: "Procedimiento exacto, ABI, formato criptográfico, endpoint y callback no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Separar autenticación, firma local y entrega al portal."

  - inventory_id: "ES-PUB-0010"
    surface_key: "mjusticia-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Justicia"
    surface_name: "Sede electrónica del Ministerio de Justicia"
    surface_type: "SEDE"
    origin: "https://sede.mjusticia.gob.es"
    official_site: "https://sede.mjusticia.gob.es/"
    e_sede: "https://sede.mjusticia.gob.es/"
    entry_url: "https://sede.mjusticia.gob.es/tramites/organos-gobierno"
    procedure_page: "https://sede.mjusticia.gob.es/tramites/organos-gobierno"
    certificate_required: "NO_VERIFICADO"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Firma local con AutoFirma en determinados trámites."
    protocol_evidence: "La fuente limita AutoFirma a determinados trámites, pero no publica ABI ni endpoint."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P06B", "P06C"]
    reason: "Requisito exacto de certificado, formato, algoritmo, endpoint y callback no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Capturar un procedimiento explícitamente compatible."

  - inventory_id: "ES-PUB-0011"
    surface_key: "junta-andalucia-ovorion"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Andalucía"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Andalucía"
    surface_name: "Ovorion"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://www.juntadeandalucia.es"
    official_site: "https://www.juntadeandalucia.es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
    procedure_page: "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet"
    protocol_family: "MINIAPPLET_TRIFASICA_PRE_PKCS1_POST"
    signature_format: "CAdES / EXPLICIT"
    signature_algorithm: "SHA1withRSA (LEGACY_SHA1 portal-specific)"
    endpoint: "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma de autenticación mediante MiniApplet.sign."
    protocol_evidence: "Contrato tri-phase y callbacks legacy delimitados; adapter y tests internos existentes."
    client_tls_auth: "NO_EN_CONTORNO_OBSERVADO"
    evidence_ids: ["P07"]
    reason: "La entrega técnica está implementada, pero no existe evidencia sanitizada de aceptación del portal."
    reviewed_at: "2026-07-15"
    next_gate: "Probar aceptación segura del portal y conservar solo evidencia sanitizada."

  - inventory_id: "ES-PUB-0012"
    surface_key: "comunidad-madrid-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Comunidad de Madrid"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comunidad de Madrid"
    surface_name: "Sede electrónica de la Comunidad de Madrid"
    surface_type: "SEDE"
    origin: "https://sede.comunidad.madrid"
    official_site: "https://sede.comunidad.madrid/"
    e_sede: "https://sede.comunidad.madrid/"
    entry_url: "https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid"
    procedure_page: "https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Descargar PDF, firmarlo localmente con AutoFirma y adjuntarlo al registro."
    protocol_evidence: "La guía acredita el proceso documental, no el formato criptográfico ni el contrato de carga."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P08", "P08A"]
    reason: "PAdES es solo candidato; formato aceptado, JS cliente y endpoint de upload no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Verificar el formato aceptado y el contrato de carga."

  - inventory_id: "ES-PUB-0013"
    surface_key: "comunidad-madrid-gestiona2"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Comunidad de Madrid"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comunidad de Madrid"
    surface_name: "gestiona2"
    surface_type: "FRONTEND_TRAMITE"
    origin: "https://gestiona2.comunidad.madrid"
    official_site: "https://gestiona2.comunidad.madrid/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://gestiona2.comunidad.madrid/gpse_solicitud/accesos.jsf?numref=2094"
    procedure_page: "https://gestiona2.comunidad.madrid/gpse_solicitud/accesos.jsf?numref=2094"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "UNSUPPORTED_PROTOCOL"
    operation_summary: "Acceso con certificado y firma AutoFirma del trámite observado."
    protocol_evidence: "El frontend público presenta una exclusión explícita de móvil/tableta."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P08B"]
    reason: "El propio frontend declara que la solicitud no puede realizarse desde móvil o tableta."
    reviewed_at: "2026-07-15"
    next_gate: "Mantener bloqueado; revalidar solo ante cambio oficial."

  - inventory_id: "ES-PUB-0014"
    surface_key: "aragon-siraw"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Aragón"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Aragón"
    surface_name: "SIRAW"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://aplicaciones.aragon.es"
    official_site: "https://aplicaciones.aragon.es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw"
    procedure_page: "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet"
    protocol_family: "MINIAPPLET_CON_STORAGE_RETRIEVE"
    signature_format: "CAdES / explicit"
    signature_algorithm: "SHA1withRSA; precalculatedHashAlgorithm=SHA1"
    endpoint:
      - "https://aplicaciones.aragon.es/siraw/resources/js-signature-storage/StorageService"
      - "https://aplicaciones.aragon.es/siraw/resources/js-signature-retriever/RetrieveService"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_CONTRACT"
    operation_summary: "Firma de token de acceso y hash precalculado mediante MiniApplet.sign."
    protocol_evidence: "La integración pública configura MiniApplet y los dos servlets exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P16", "P16B", "P16C"]
    reason: "Contrato JS estático probado; implementación y aceptación E2E no verificadas."
    reviewed_at: "2026-07-15"
    next_gate: "Fixture sanitizada, advertencia legacy, adapter limitado y E2E propio."

  - inventory_id: "ES-PUB-0015"
    surface_key: "diputacion-valladolid-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "Valladolid (provincia)"
    institution_name: "Diputación Provincial de Valladolid"
    surface_name: "Sede electrónica de la Diputación de Valladolid"
    surface_type: "SEDE"
    origin: "https://www.sede.diputaciondevalladolid.es"
    official_site: "https://www.sede.diputaciondevalladolid.es/"
    e_sede: "https://www.sede.diputaciondevalladolid.es/"
    entry_url: "https://www.sede.diputaciondevalladolid.es/requisitos-tecnicos"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Identificación/firma con DNIe y certificados admitidos por @firma."
    protocol_evidence: "La validación por @firma y una explicación de multifirma no prueban el transporte de un trámite."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P09", "P09A"]
    reason: "Procedimiento, JS cliente, protocolo, formato, endpoint, cofirma y contrafirma no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Identificar una operación real sin habilitar multifirma."

  - inventory_id: "ES-PUB-0016"
    surface_key: "sevilla-sede"
    administrative_level: "MUNICIPAL"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "Sevilla (municipio)"
    institution_name: "Ayuntamiento de Sevilla"
    surface_name: "Sede electrónica del Ayuntamiento de Sevilla"
    surface_type: "SEDE"
    origin: "https://sede.sevilla.org"
    official_site: "https://sede.sevilla.org/"
    e_sede: "https://sede.sevilla.org/"
    entry_url: "https://sede.sevilla.org/opencms/system/modules/sede/contents/faq/Presentacion_Clave"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Presentación con certificado vigente y AutoFirma."
    protocol_evidence: "Las FAQ acreditan requisitos, no ABI ni endpoint portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P10", "P10A"]
    reason: "Página de procedimiento, JS cliente, formato, algoritmo y endpoint no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Inspeccionar el JS de una presentación pública concreta."

  - inventory_id: "ES-PUB-0017"
    surface_key: "madrid-sede"
    administrative_level: "MUNICIPAL"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "Madrid (municipio)"
    institution_name: "Ayuntamiento de Madrid"
    surface_name: "Sede electrónica del Ayuntamiento de Madrid"
    surface_type: "SEDE"
    origin: "https://sede.madrid.es"
    official_site: "https://sede.madrid.es/"
    e_sede: "https://sede.madrid.es/"
    entry_url: "https://sede.madrid.es/portal/site/tramites/menuitem.62876cb64654a55e2dbd7003a8a409a0/?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD"
    procedure_page: "https://sede.madrid.es/portal/site/tramites/menuitem.62876cb64654a55e2dbd7003a8a409a0/?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "RECHECK_REQUIRED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Identificación/tramitación con certificado cuando el procedimiento lo admite."
    protocol_evidence: "La entrada oficial fue inspeccionada previamente; el cliente CLI recibió 403 en la revalidación."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P11"]
    reason: "Firma y contrato técnico no verificados; la lectura web existe, pero la comprobación CLI no es repetible."
    reviewed_at: "2026-07-15"
    next_gate: "Revalidar la entrada oficial sin sortear controles del portal."

  - inventory_id: "ES-PUB-0018"
    surface_key: "ugr-sede"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "NO_VERIFICADO"
    institution_name: "Universidad de Granada"
    surface_name: "Sede electrónica de la Universidad de Granada"
    surface_type: "SEDE"
    origin: "https://sede.ugr.es"
    official_site: "https://sede.ugr.es/"
    e_sede: "https://sede.ugr.es/"
    entry_url: "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"
    procedure_page: "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"
    certificate_required: "SI"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "AutoFirma muestra certificados disponibles para acceder o firmar."
    protocol_evidence: "No se verificó si la página usa selectcert, sign u otro transporte."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P12", "P12A"]
    reason: "JS cliente, operación exacta, protocolo, callback, formato y endpoint no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Revisar JS público y ejecutar un flujo no destructivo."

  - inventory_id: "ES-PUB-0019"
    surface_key: "us-sede"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "NO_VERIFICADO"
    institution_name: "Universidad de Sevilla"
    surface_name: "Sede electrónica de la Universidad de Sevilla"
    surface_type: "SEDE"
    origin: "https://sede.us.es"
    official_site: "https://sede.us.es/"
    e_sede: "https://sede.us.es/"
    entry_url: "https://sede.us.es/opencms/system/modules/sede/contents/pages/requisitosTecnicos"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Autenticación y firma mediante AutoFirma de escritorio."
    protocol_evidence: "Los requisitos no publican ABI, formato, callback ni endpoint."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P13"]
    reason: "Página de procedimiento y contrato técnico portal-specific no verificados."
    reviewed_at: "2026-07-15"
    next_gate: "Identificar la entrada y el contrato de un procedimiento."

  - inventory_id: "ES-PUB-0020"
    surface_key: "unizar-tramitador"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "NO_VERIFICADO"
    institution_name: "Universidad de Zaragoza"
    surface_name: "Tramitador ciudadano"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://tramita.unizar.es"
    official_site: "https://tramita.unizar.es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent"
    procedure_page: "https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_TRIFASICA_MOVIL_CON_STORAGE_RETRIEVE"
    signature_format: "CAdES sobre hash precalculado"
    signature_algorithm: "SHA1withRSA; precalculatedHashAlgorithm=SHA1"
    endpoint:
      - "https://tramita.unizar.es/afirma-server-triphase-signer-2.7.3/SignatureService"
      - "https://tramita.unizar.es/afirma-signature-storage/StorageService"
      - "https://tramita.unizar.es/afirma-signature-retriever/RetrieveService"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_CONTRACT"
    operation_summary: "Firma de challenge de sesión precalculado; tri-phase en móvil."
    protocol_evidence: "Integration JS y AutoScript públicos fijan formato, algoritmo, serverUrl y Storage/Retrieve."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P17", "P17A", "P17B"]
    reason: "Contrato JS estático probado; no existe implementación habilitada ni aceptación E2E."
    reviewed_at: "2026-07-15"
    next_gate: "Fixture sanitizada, advertencia legacy, adapter limitado y E2E propio."
```

## 8. Relación con el catálogo de producto

Este archivo es documentación de investigación, no configuración ejecutable:

- no se empaqueta ni se descarga como catálogo remoto;
- no asigna `ProfileActivation`;
- no concede origins de inicio, redirect o browse confiable;
- no concede endpoint, adapter, capability ni filtro de certificado;
- no convierte `VERIFIED_CONTRACT` en soporte real;
- no modifica el único profile de firma existente, `junta-andalucia`, que
  continúa gobernado por su catálogo de producto sin promoción E2E.

Un origin inventariado sin profile activo sigue el comportamiento de sitio
desconocido: navegación HTTPS `BROWSE_ONLY`, bridge ausente, URI AutoFirma
bloqueado, cookies no leídas por nativo y `ClientCertRequest` ignorado.

## 9. Privacidad y seguridad de la evidencia

Se pueden conservar: institución, nivel, origin, path público estable,
content-type, estado HTTP, fecha, operación cerrada, nombre de método, tamaño y
hash de JS público.

No se conservan: cookies, `Authorization`, query secrets, fragments sensibles,
payloads, documentos, challenges, identificadores de sesión o expediente,
datos personales, certificados, seriales, issuer DN completos, URI de P12/PFX,
contraseñas, firmas ni claves. Si una entrada pública genera un challenge o
token efímero, solo se comprueban nombres de campos y metadatos permitidos; el
valor y el cuerpo se descartan. No se archivan HAR completos.

La investigación nunca envía formularios, inicia expedientes, firma, elige
certificado, pulsa confirmaciones ni intenta eludir un 403, CAPTCHA, WAF o
control de acceso.

## 10. Gaps y próximas olas

El snapshot cubre todo el seed de la matriz, pero conserva gaps nacionales
grandes y explícitos:

1. No se ha materializado todavía el denominador de ministerios, organismos y
   entidades de INVENTE/DIR3; solo hay diez superficies estatales seed.
2. De las 17 comunidades autónomas y Ceuta/Melilla solo hay superficies de
   Andalucía, Aragón y Madrid.
3. Solo hay una diputación y dos ayuntamientos; faltan las colas de
   diputaciones, cabildos, consejos insulares y miles de entidades locales.
4. RUCT aún no se ha convertido en una cola cerrada de universidades públicas;
   el seed contiene tres.
5. SIA no se ha recorrido para descubrir frontends distintos del portal
   institucional.
6. No existe inventario separado de proveedores compartidos, plataformas
   multi-tenant, SSO, Storage/Retrieve o endpoints tri-phase.
7. No hay evidencia E2E ni un `ClientCertRequest` exacto para ningún registro.
8. Variantes lingüísticas, dominios históricos y redirects no se cuentan hasta
   completar una ola de deduplicación.
9. El Ayuntamiento de Madrid permanece `RECHECK_REQUIRED` por la limitación de
   revalidación ya documentada.

Orden de expansión recomendado:

1. AGE y sector público estatal: [D02], [D07], [D08], [D09].
2. Comunidades y ciudades autónomas: [D03], con una ola por territorio.
3. Diputaciones/cabildos/consejos y ayuntamientos: [D04], [D05], [D06],
   contrastados con DIR3.
4. Universidades públicas: [D10], confirmando cada sede en el dominio
   institucional.
5. Dependencias técnicas y familias multi-tenant, en un inventario separado
   antes de cualquier propuesta de profile.

## 11. Definiciones de fuentes

### Enumeradores

[D01]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas.html
[D02]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/AGE-Ministerios.html
[D03]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_CCAA.html
[D04]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL.html
[D05]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_Ayuntamientos.html
[D06]: https://administracion.gob.es/pag_Home/es/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_Diputaciones.html
[D07]: https://administracion.gob.es/pag_Home/espanaAdmon/SIA.html
[D08]: https://administracionelectronica.gob.es/ctt/dir3
[D09]: https://www.igae.pap.hacienda.gob.es/sitios/igae/es-ES/BasesDatos/invente/paginas/inicio.aspx
[D10]: https://www.ciencia.gob.es/Universidades/RUCT.html

### Evidencia portal-specific

[P01]: https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1
[P01B]: https://sede.administracion.gob.es/PAG_Sede/LaSedePAG/SistemasFirmaAceptados.html?hc=1
[P02]: https://sede.agenciatributaria.gob.es/Sede/certificado-dni-electronico.html
[P02A]: https://sede.agenciatributaria.gob.es/Sede/ayuda/consultas-informaticas/firma-digital-sistema-clave-pin-tecnica/certificados-electronicos-dispositivos-moviles/android-cuestiones-generales-uso-certificados.html
[P02B]: https://sede.agenciatributaria.gob.es/Sede/ayuda/consultas-informaticas/otros-servicios-ayuda-tecnica/documentos-pendientes-firma.html
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
