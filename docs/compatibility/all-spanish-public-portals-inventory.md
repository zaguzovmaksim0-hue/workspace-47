# Inventario evolutivo de portales públicos españoles

- Fecha del snapshot: 2026-07-31
- `inventory_schema_version`: `2`
- `snapshot_id`: `2026-07-31-age-d11-ccaa-d03-insular-d12-diputaciones-d06-profile-catalog-dedup-4`
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

## 2. Esquema de registro v2

| Campo | Obligatorio | Regla v2 |
| --- | --- | --- |
| `inventory_id` | Sí | ID ASCII estable `ES-PUB-NNNN`; nunca se reutiliza. |
| `surface_key` | Sí | Slug estable de la superficie, independiente del dominio. |
| `administrative_level` | Sí | Uno de `ESTATAL`, `AUTONOMICO`, `PROVINCIAL`, `INSULAR`, `MUNICIPAL`, `UNIVERSIDAD_PUBLICA`, `OTRA_INSTITUCION_PUBLICA`. |
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
| `signature_algorithm` | Sí | Algoritmo exacto acreditado o `NO_VERIFICADO`; no se hereda de otro portal. |
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

P19 (Carné Joven Europeo de Andalucía) cuenta con verificación E2E delimitada a CLIENT_TLS_AUTH en dispositivo físico (2026-07-21, commit dc3c231). P16 (Aragón SIRAW), P20 (Oficina Virtual) y P17 (UniZAR) cuentan con verificación E2E delimitada a sus logins CAdES observados el 2026-07-28, 2026-07-29 y 2026-07-30, respectivamente. Tests locales, hashes de JS y
revisión documental nunca producen por sí solos ese estado. La implementación Junta Ovorion se
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

> **Fuente operativa de verdad para el estado actual:** los registros YAML de este inventario y el catálogo generado. Para evitar copiar cifras históricas entre chats, ejecutar `python tools/report_public_portal_coverage.py`; la suite de tests comprueba que las tablas resumidas de esta sección coincidan con los registros reales.

El denominador nacional todavía es desconocido. Por tanto, no se publica un
porcentaje «de España». Este snapshot conserva las 20 superficies del seed e
ingiere de extremo a extremo el directorio oficial de sedes AGE [D11]: 79
entradas únicas, siete ya presentes por exact origin y 72 superficies nuevas.
También materializa la lista cerrada D03 de las 17 comunidades autónomas y
Ceuta/Melilla: las 19 referencias territoriales se resolvieron mediante
fuentes HTTPS oficiales y produjeron 24 superficies nuevas. Seis son fronteras
funcionales adicionales con origin o función propios; no son duplicados de la
sede principal. También ingiere de extremo a extremo D12: sus 11 cabildos y
consells se resolvieron en 22 superficies insulares, separando en cada caso el
portal institucional de la sede electrónica por su origin y frontera funcional
acreditados. Finalmente, materializa las 41 etiquetas provinciales de D06 con
una superficie primaria por etiqueta: Valladolid ya estaba presente por exact
origin y las otras 40 crean registros nuevos. Las superficies provinciales
secundarias quedan diferidas. D05 sigue capturado pero pendiente de ingestión.

| Métrica | Resultado |
| --- | ---: |
| Registros seed esperados desde la matriz | 20 |
| Registros seed inventariados | 20/20 (100 % del seed, no del país) |
| Entradas únicas materializadas desde D11 | 79/79 del snapshot 2026-07-16 |
| Entradas D11 ya presentes por exact origin | 7 |
| Registros nuevos creados desde D11 | 72 |
| Territorios D03 materializados | 19/19 del snapshot 2026-07-16 |
| Registros nuevos creados desde D03 | 24 |
| Instituciones D12 materializadas | 11/11 del snapshot 2026-07-16 |
| Registros nuevos creados desde D12 | 22 |
| Entradas D06 materializadas | 41/41 del snapshot 2026-07-16 |
| Entradas D06 ya presentes por exact origin | 1 |
| Registros nuevos creados desde D06 | 40 |
| Registros totales del snapshot | 183 |
| Origins primarios distintos | 180 |
| Fuentes enumeradoras oficiales registradas | 12 |
| Colas enumeradoras ingeridas de extremo a extremo | 4/12 |
| Colas enumeradoras pendientes de ingestión | 8/12 |
| Fuentes oficiales portal-specific registradas | 245 |
| Fuentes oficiales totales registradas | 257 |
| Entradas `VERIFIED_E2E` | 4 |
| Entradas `IMPLEMENTED_NOT_E2E` | 111 |
| Entradas implementadas (`VERIFIED_E2E` + `IMPLEMENTED_NOT_E2E`) | 115 |
| Entradas restantes fuera de ambos estados | 68 |
| Evidencia exacta de `ClientCertRequest` | 4 |

Por nivel administrativo:

| Nivel | Registros |
| --- | ---: |
| `ESTATAL` | 82 |
| `AUTONOMICO` | 32 |
| `PROVINCIAL` | 41 |
| `INSULAR` | 22 |
| `MUNICIPAL` | 2 |
| `UNIVERSIDAD_PUBLICA` | 4 |
| `OTRA_INSTITUCION_PUBLICA` | 0 |
| **Total** | **183** |

Por estado del inventario:

| Estado | Registros |
| --- | ---: |
| `VERIFIED_E2E` | 4 |
| `IMPLEMENTED_NOT_E2E` | 111 |
| `VERIFIED_CONTRACT` | 1 |
| `REQUIRES_AUTHENTICATED_RESEARCH` | 0 |
| `BROWSE_ONLY` | 61 |
| `UNSUPPORTED_PROTOCOL` | 2 |
| `INACCESSIBLE` | 4 |
| `DEPRECATED` | 0 |
| **Total** | **183** |

Por mantenimiento del inventario:

| Estado | Registros |
| --- | ---: |
| `REVIEWED` | 160 |
| `RECHECK_REQUIRED` | 5 |
| `DISCOVERED` | 18 |
| `CANDIDATE`, `RETIRED` | 0 |
| **Total** | **183** |

## 5. Método de descubrimiento reproducible

### 5.1. Enumeración institucional

Cada ola comienza con un snapshot fechado de enumeradores oficiales:

1. El Punto de Acceso General lista categorías de portales públicos [D01],
   ministerios de la AGE [D02], comunidades y ciudades autónomas [D03] y
   entidades locales [D04]. Sus índices de ayuntamientos [D05], diputaciones
   [D06] y cabildos/consells [D12] sirven como colas territoriales, no como
   prueba de un protocolo.
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
6. El directorio de sedes AGE [D11] aporta una relación revisada de institución
   y enlace. En este snapshot se materializó completo sin abrir los destinos.

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

Las comprobaciones automatizadas usan `GET` público sin autenticación, cookies
persistentes ni envío de formularios. Se limita la frecuencia, redirects y
tamaño. Los cuerpos se descartan al terminar la comprobación.

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

### 6.1. Enumeradores

| ID | Propietario | Cobertura y uso |
| --- | --- | --- |
| [D01] | Punto de Acceso General | Índice raíz de portales públicos estatales, autonómicos, locales, judiciales y otros organismos. |
| [D02] | Punto de Acceso General | Ministerios y portales de la AGE. |
| [D03] | Punto de Acceso General | Las 17 comunidades autónomas y Ceuta/Melilla; cola cerrada materializada en este snapshot, sin abrir destinos. |
| [D04] | Punto de Acceso General | Índice de entidades locales. |
| [D05] | Punto de Acceso General | Cola territorial de ayuntamientos. |
| [D06] | Punto de Acceso General | Cola cerrada de 41 diputaciones provinciales, materializada en este snapshot con una superficie primaria por etiqueta. |
| [D07] | Punto de Acceso General / SIA | Catálogo oficial de procedimientos y servicios. |
| [D08] | Secretaría General de Administración Digital | DIR3: unidades, organismos y oficinas; identidad/deduplicación. |
| [D09] | IGAE | INVENTE: entidades del sector público estatal, autonómico y local. |
| [D10] | Ministerio de Ciencia, Innovación y Universidades | RUCT: universidades oficiales; se filtran solo las públicas. |
| [D11] | Punto de Acceso General / Sede PAG | Directorio oficial de sedes electrónicas de la AGE; acredita nombre y enlace publicados, no disponibilidad, procedimiento ni contrato técnico. |
| [D12] | Punto de Acceso General | Cola independiente de cabildos y consells insulares. |

Ningún enumerador anterior es por sí solo un catálogo completo de origins.
Se cruzan porque cada uno cubre una dimensión distinta.

### 6.2. Evidencia del seed

Los IDs se mantienen alineados con la matriz para que una revalidación no
requiera traducción manual.

| Ficha | Institución o superficie | Fuentes oficiales | Tipo de evidencia |
| --- | --- | --- | --- |
| `P01` | PAG / enlace al REG-AGE | [P01][P01B] | Ficha oficial PAG y enlace público exacto de Acceso al Registro Electrónico General hacia REG-AGE; el ABI pertenece al perfil RedSARA ya verificado. |
| `P02` | AEAT | [P02][P02A][P02B] | Certificado en navegador/Android y fase documental de firma; sin `ClientCertRequest` exacto. |
| `P03` | Seguridad Social / Import@ss | [P03][P03A] | Ayuda oficial; AutoFirma móvil rechazada en la Sede y métodos de acceso Import@ss. |
| `P04` | SEPE | [P04] | FAQ oficial de AutoFirma de escritorio. |
| `P05` | DGT | [P05][DGT-JS-MAIN-2026-08-09][DGT-JS-CONSTANTES-2026-08-09][DGT-JS-MINIAPPLET-2026-08-09] | Entrada y scripts oficiales con llamada MiniApplet CAdES exacta; sin endpoint de resultado. |
| `P06` | Sede Judicial / Ministerio de Justicia | [P06][P06A][P06B][P06C] | Ayuda, guía PDF y trámite oficial; ABI no publicado. |
| `P07` | Junta de Andalucía / Ovorion | [P07] | Entrada pública portal-specific; contrato detallado en la matriz y observaciones locales redactadas. |
| `P08` | Comunidad de Madrid / gestiona2 | [P08][P08A][P08B] | Guía local-PDF y frontend que bloquea móvil. |
| `P09` | Diputación de Valladolid | [P09][P09A] | Certificados admitidos y explicación conceptual de firma. |
| `P10` | Ayuntamiento de Sevilla | [P10][P10A] | Requisito de certificado vigente y AutoFirma. |
| `P11` | Ayuntamiento de Madrid | [P11] | Procedimiento oficial previamente inspeccionado; revalidación automatizada pendiente. |
| `P12` | Universidad de Granada | [P12][P12A] | Pantalla AutoFirma y requisitos; transporte exacto no probado. |
| `P13` | Universidad de Sevilla | [P13][P13A] | Requisitos técnicos y ficha pública ISG_01 que delega exactamente a REG-AGE. |
| `P14` | REG/RedSARA | [P14][P14A][P14B][P14C][P14D] | Entrada, manual y JS público con contrato estático. |
| `P15` | ACCEDA | [P15][P15A][P15B] | Entrada y helper/AutoScript públicos; uso runtime del helper no observado. |
| `P16` | Gobierno de Aragón / SIRAW | [P16][P16B][P16C] | Entrada y JS público con MiniApplet y Storage/Retrieve. |
| `P17` | Universidad de Zaragoza | [P17][P17A][P17B] | Entrada e integration JS con firma de challenge y tri-phase móvil; login CAdES aceptado E2E en dispositivo físico el 2026-07-30. |
| `P18` | Comunidad de Madrid / Cuenta Digital — Carné Joven 53F1 | [P18][P18A][P18B][P18C][P18D][P18E][P18F] | Ficha oficial, métodos de identificación/firma, entrada 53F1 y cadena JS de lookup/redirect autenticado; sin contrato de presentación. |
| `P19` | IAJ / Carné Joven Europeo de Andalucía | [P19][P19A][P19B][P19C][P19D] | Autenticación CLIENT_TLS_AUTH verificada E2E en dispositivo físico (2026-07-21, commit dc3c231); Zona privada y Solicitar Carné Joven alcanzaron entrada nativa autenticada; firma posterior no E2E. |
| `P20` | Junta de Andalucía / Oficina Virtual | [LIVE-JUNTA-OFVIRTUAL-2026-07-22][E2E-JUNTA-OFVIRTUAL-2026-07-29] | Entrada oficial y autenticación CAdES aceptada E2E el 2026-07-29; alcance limitado al login observado. |
| `P21` | Ministerio de Educación / convocatoria 46 | [LIVE-EDUCACION-ENTRY-2026-07-22] | Entrada oficial revisada; transporte downstream de certificado y callback no verificados. |

D11 se añadió además como provenance a los siete registros seed cuyo exact
origin coincide con el directorio. Esta relación de existencia no modifica ni
promociona su evidencia técnica portal-specific.

La ola autonómica usa los IDs `A01` a `A19` en el mismo orden cerrado de D03.
Sus 55 fuentes portal-specific acreditan propietario, entrada HTTPS y, cuando
se declara, una mención delimitada a certificado o firma. `A15` corresponde a
Madrid y no crea definiciones nuevas: sus dos superficies ya conservan la
evidencia P08 portal-specific. D03 no se añadió a los cuatro registros
autonómicos preexistentes porque sus enlaces territoriales no coinciden con la
frontera exacta de Ovorion, SIRAW, la sede de Madrid o gestiona2.

La ola insular usa las familias `I01` a `I11` en el orden de D12. Cada familia
separa una fuente `A` para el portal institucional y una fuente `B` para la
sede electrónica; cada ID define una sola URL. D12 se conserva como provenance
de los portales institucionales y el
[snapshot JSONL](snapshots/pag-insular-2026-07-16.jsonl) fija la cola cerrada
capturada. Los 22 origins se acreditan además con sus 22 fuentes HTTPS
portal-specific; D12 no acredita por sí solo disponibilidad, certificado,
firma ni contrato técnico.

La ola provincial usa las familias `DP01` a `DP41` en el orden exacto de D06.
Sus 69 fuentes portal-specific definen una sola URL por ID: `A` acredita la
superficie primaria y `B`, cuando existe, una evidencia oficial adicional
delimitada. D06 aporta provenance a las 41 etiquetas; Valladolid conserva
`ES-PUB-0015` por exact origin y las otras 40 crean registros nuevos. Las sedes
o portales secundarios observados quedan expresamente fuera de esta ola.

### 6.3. Disponibilidad en este snapshot

La comprobación pública del seed obtuvo HTTP 200 mediante `GET` directo en 46
de sus 49 URLs, después de un único retry de P07. D11 se materializó después
mediante un `GET` HTTPS público con presupuesto de cero redirects, HTTP 200 y
baseline revisado de 22 ministerios, 81 fichas, 84 anchors y 79 entradas
únicas. La fuente D03 se materializó mediante otro único `GET` HTTPS público,
sin redirects ni apertura de destinos, y cumplió el baseline cerrado de 19
territorios, tres referencias HTTPS y dieciséis referencias HTTP heredadas.
Estas últimas se conservaron como componentes no ejecutables; las superficies
de §7.3 proceden de 55 fuentes HTTPS portal-specific revisadas por separado.
El cociente histórico de disponibilidad del primer conjunto sigue siendo
47/50; no se publica un cociente agregado para las 212 fuentes porque las
olas usaron transportes y alcances distintos. Las tres excepciones del seed se
conservan con la limitación exacta:

El resultado D11 sanitizado y determinista queda fijado en el
[snapshot JSONL](snapshots/age-sede-directory-2026-07-16.jsonl). Incluye solo
metadatos públicos, hashes/tamaños de la fuente y URLs ya sanitizadas; no
incluye el cuerpo HTML ni valores sessionizados.

El resultado D03 equivalente queda fijado en el
[snapshot JSONL](snapshots/ccaa-directory-2026-07-16.jsonl). No contiene cuerpos
HTML ni ejecuta las referencias territoriales. D03 solo acredita la cola
territorial; cada origin HTTPS de §7.3 exige además su evidencia portal-specific
y no se añadió como provenance a las cuatro superficies autonómicas preexistentes.

El resultado D12 queda fijado en el
[snapshot JSONL](snapshots/pag-insular-2026-07-16.jsonl), ya incluido en el
commit `abfac7516bf9dd7788b15e6e46328c8914af30e6`. Sus 11 referencias se
resolvieron mediante fuentes HTTPS oficiales sin solicitar los enlaces HTTP
heredados ni sintetizar equivalentes HTTPS. La revisión mantuvo formularios,
autenticación, firma, cookies y rutas privadas fuera de alcance. Las sedes de
El Hierro, La Gomera y Lanzarote se conservan como
`INACCESSIBLE`/`RECHECK_REQUIRED`: el cliente de revisión detectó un bucle de
redirección al abrir sus entradas oficiales y no siguió el ciclo.

El resultado D06 queda fijado en el
[snapshot JSONL](snapshots/pag-diputaciones-2026-07-16.jsonl). Sus 41 etiquetas
se resolvieron mediante evidencia HTTPS oficial sin solicitar las referencias
HTTP heredadas ni sintetizar cambios de esquema. Valladolid coincidió con el
origin existente; 40 origins son nuevos. Soria se conserva como
`INACCESSIBLE`/`RECHECK_REQUIRED` porque la revalidación normal encontró un
certificado TLS expirado el 2026-06-21; no se usó `--insecure`. Formularios,
autenticación, firma, cookies y rutas privadas quedaron fuera de alcance.

El snapshot [D05](snapshots/pag-municipal-queues-2026-07-16.jsonl) también está
capturado y versionado, pero permanece pendiente de resolución HTTPS e
ingestión. Las superficies provinciales secundarias descubiertas durante D06
quedan para una ola posterior y no alteran el cierre primario 41/41.

- D09 cerró la conexión del cliente CLI, aunque una lectura HTTPS
  independiente recuperó la página oficial de INVENTE;
- D10 no pudo validar la cadena CA con el almacén local de `curl`, aunque una
  lectura HTTPS independiente recuperó la página oficial de RUCT; no se usó
  `--insecure` ni se debilitó TLS;
- P11 respondió HTTP 403 al cliente CLI. Una lectura web independiente mostró
  la página oficial, pero el registro permanece `RECHECK_REQUIRED` porque la
  comprobación automatizada no es repetible sin cambiar de transporte.

No se siguió ninguna ruta autenticada ni se intentó eludir esas respuestas.

## 7. Registros materializados

Los registros se publican en bloques YAML por ola para que cada campo sea
explícito y el snapshot pueda validarse de forma mecánica. Todos los bloques
forman un único conjunto lógico. `official_site` designa la URL oficial
verificada de esta superficie, no una homepage institucional inferida.
`procedure_page` queda `NO_VERIFICADO` cuando las fuentes solo aportan ayuda o
requisitos.

### 7.1. Seed heredado de la matriz

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
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede PAG delega el acceso al Registro Electrónico General mediante un enlace público exacto a REG-AGE."
    protocol_evidence: "La ficha oficial PAG enlaza directamente Acceso al Registro Electrónico General con el mismo launch URL https://reg.redsara.es/es/ ya cubierto por el perfil reg-age-redsara; no se infiere un contrato de firma propio de PAG."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P01", "P01B", "P14", "D11"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL oficial; se conserva la ficha PAG como entry URL y no se realizó E2E físico desde ella."
    reviewed_at: "2026-08-13"
    next_gate: "E2E físico seguro desde la ficha PAG hasta REG-AGE sin realizar una presentación administrativa real."

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
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma del XML de resumen antes de guardarlo en el expediente."
    protocol_evidence: "AutoScript.sign estático; el wrapper consume signatureB64 y llama a saveXMLAutoSign."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P14", "P14B", "P14C", "P14D", "LIVE-REDSARA-2026-07-30"]
    reason: "Revalidación física 2026-07-30: Nuevo registro y Mis registros exigen Cl@ve; XAdES solo aparece tras preparar una solicitud, sin E2E público seguro antes de una actuación administrativa."
    reviewed_at: "2026-07-30"
    next_gate: "Caso administrativo real autorizado: Cl@ve, preparación de solicitud y aceptación XAdES por RedSARA; no usar datos ficticios ni automatizar la presentación."

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
    evidence_ids: ["P15", "P15A", "P15B", "D11"]
    reason: "Contrato estático probado, pero faltan procedimiento exacto, endpoint servidor, implementación y aceptación E2E."
    reviewed_at: "2026-07-15"
    next_gate: "Demostrar invocación portal-specific en un procedimiento y después E2E."

  - inventory_id: "ES-PUB-0004"
    surface_key: "aeat-sede"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal de Administración Tributaria"
    surface_name: "AEAT — Mi área personal / Mis datos censales"
    surface_type: "SEDE"
    origin: "https://sede.agenciatributaria.gob.es"
    official_site: "https://sede.agenciatributaria.gob.es/"
    e_sede: "https://sede.agenciatributaria.gob.es/"
    entry_url: "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
    procedure_page: "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CLIENT_TLS"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Autenticación Client TLS para el acceso de solo lectura a Mis datos censales."
    protocol_evidence: "La transición exacta desde Mi área personal al endpoint MdcAcceso solicita certificado de cliente en TLS 1.2; sin certificado termina en error 403."
    client_tls_auth: "SI"
    evidence_ids: ["P02", "P02A", "P02C", "P02D", "D11"]
    reason: "Contrato Client TLS exacto implementado solo en QA; faltan callback WebView y aceptación E2E del portal. No se afirma firma ni presentación administrativa."
    reviewed_at: "2026-07-31"
    next_gate: "Confirmar ClientCertRequest y autenticación de solo lectura en dispositivo físico; mantener QA_ONLY si cualquier gate falla."

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
    evidence_ids: ["P03", "D11"]
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
    surface_name: "SEPE — Registro electrónico común"
    surface_type: "SEDE"
    origin: "https://sede.sepe.gob.es"
    official_site: "https://sede.sepe.gob.es/"
    e_sede: "https://sede.sepe.gob.es/"
    entry_url: "https://sede.sepe.gob.es/portalSede/registro-electronico.html"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.sepe.gob.es/portalSede/registro-electronico.html"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede electrónica del SEPE publica el Registro Electrónico Común de la Administración General del Estado como vía externa de registro electrónico."
    protocol_evidence: "La página first-party https://sede.sepe.gob.es/portalSede/registro-electronico.html enlaza explícitamente «Registro electrónico común» a https://rec.redsara.es/; la raíz REC vigente redirige con locale español exactamente a https://reg.redsara.es/es/, startUrl canónico ya cubierto por el perfil reg-age-redsara. Workspace-47 reutiliza únicamente ese startUrl y no atribuye a sede.sepe.gob.es un ABI de firma, constantes AutoFirma ni confianza criptográfica REG-AGE."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P04", "D11", "SEPE-REG-2026-08-19", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación oficial explícita del SEPE al Registro Electrónico Común y resolución pública actual al startUrl exacto; no se amplía trust al origin SEPE y falta E2E físico de la transición."
    reviewed_at: "2026-08-19"
    next_gate: "Validar físicamente la transición SEPE → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La capacidad implementada es solo la delegación exacta al REG-AGE. Los flujos propios del SEPE tras Cl@ve/certificado y su firma AutoFirma permanecen fuera de este contrato: no se infieren algoritmo, formato, payload, callback, endpoint ni client-TLS."

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
    entry_url: "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/"
    procedure_page: "https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "MiniApplet"
    protocol_family: "MINIAPPLET_LOCAL_CADES"
    signature_format: "CAdES / DETACHED / EXPLICIT"
    signature_algorithm: "SHA1withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Verificación de equipo mediante MiniApplet.sign con payload fijo."
    protocol_evidence: "El JavaScript oficial vigente fija MiniApplet.sign con SHA1withRSA, CAdES, filter=nonexpired:, callback y payload fijo Cadena a firmar; no incluye endpoint de resultado."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P05", "DGT-JS-MAIN-2026-08-09", "DGT-JS-CONSTANTES-2026-08-09", "DGT-JS-MINIAPPLET-2026-08-09"]
    reason: "La integración local está implementada solo para QA y el contrato CAdES fijo; no se ha observado aceptación E2E, presentación administrativa ni endpoint portal-specific."
    reviewed_at: "2026-08-09"
    next_gate: "Realizar una prueba física manual delimitada al contrato fijo y verificar aceptación sin enviar datos administrativos; mantener QA_ONLY hasta evidencia E2E sanitizada."

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
    entry_url: "https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75"
    procedure_page: "https://sede.mjusticia.gob.es/tramites/organos-gobierno"
    certificate_required: "NO_VERIFICADO"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "MJUSTICIA_SEDE2_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA-only desde la Sede del Ministerio al inicio oficial de Modificaciones estatutarias de fundaciones; autenticación y firma no implementadas."
    protocol_evidence: "La página oficial actual enlaza 'Tramitación On-line con CL@VE con Certificado Digital' a sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75, que redirige en el mismo origin a /login/index/idp/75. La página de login ofrece Cl@ve y describe certificado/AutoFirma, pero el branch de certificado no está presente en el DOM; el módulo first-party conserva código XAdES Detached implícito sin publicar el wrapper accAfirma ni algoritmo."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P06B", "P06C", "D11", "MJUSTICIA-IDP75-LAUNCH-2026-08-19"]
    reason: "Implementación QA-only limitada al launch exacto observado; no se exponen certificado, client TLS, firma, formato, algoritmo, endpoint ni callback."
    reviewed_at: "2026-08-19"
    next_gate: "Para ampliar capacidades, autenticar de forma controlada y avanzar hasta el primer estado pre-sign activo, deteniéndose antes de firma privada y presentación final."

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
    surface_name: "Registro Electrónico General de la Comunidad de Madrid"
    surface_type: "SEDE"
    origin: "https://gestiona.comunidad.madrid"
    official_site: "https://sede.comunidad.madrid/"
    e_sede: "https://sede.comunidad.madrid/"
    entry_url: "https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm"
    procedure_page: "https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "MADRID_EREG_MULTIPART_ROUTER"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidorProcesa.icm"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede oficial delega el Registro Electrónico General al selector público exacto de gestiona.comunidad.madrid; Workspace-47 habilita solo navegación QA a ese inicio, sin automatizar upload, autenticación ni firma."
    protocol_evidence: "La página oficial vigente publica «Acceder» hacia https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm. Ese launch responde 200 y su first-party HTML expone un formulario multipart POST a InicioDistribuidorProcesa.icm con campo de fichero `files`, `ajax=1`, `nombrefichero` y respuesta interpretada mediante VP_ERROR/VP_FICHERO/VP_PROCEDIMIENTO/VP_URL_REDIRECCION/VP_MOTIVO_ERROR. El input no publica `accept`, y una sonda técnica inofensiva no permitió demostrar formato aceptado; por ello formato/algoritmo/ABI de firma permanecen NO_VERIFICADO y el perfil no expone capacidades sensibles."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P08", "P08A"]
    reason: "Perfil QA-only de navegación al selector público exacto del Registro Electrónico General. El router multipart está observado, pero no se automatiza ni se expone como endpoint de firma; el formato aceptado y el contrato criptográfico siguen sin verificar, y no se realizó presentación E2E."
    reviewed_at: "2026-08-19"
    next_gate: "Con un modelo oficial no personal y sin presentar el registro, verificar qué formatos de solicitud reconoce InicioDistribuidorProcesa.icm y capturar solo la transición pre-auth/pre-sign resultante; mantener SIGN y client-auth bloqueados hasta prueba independiente."

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
    inventory_status: "VERIFIED_E2E"
    operation_summary: "Firma de token de acceso y hash precalculado mediante MiniApplet.sign."
    protocol_evidence: "La integración pública configura MiniApplet y los dos servlets exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P16", "P16B", "P16C"]
    reason: "El portal real aceptó la firma CAdES de autenticación y abrió la sesión interna; verificación limitada al login observado."
    reviewed_at: "2026-07-28"
    next_gate: "Mantener Storage/Retrieve y firma documental bloqueados hasta evidencia E2E separada."

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
    entry_url: "https://www.sede.diputaciondevalladolid.es/tgauth/login"
    procedure_page: "https://www.sede.diputaciondevalladolid.es/tramites-disponibles/12S203/"
    certificate_required: "SI"
    signature_required: "CONDICIONAL"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado digital mediante TLS cliente en el endpoint exacto :21460; firma documental bloqueada."
    protocol_evidence: "La entrada pública /c/portal/cert-login redirige exactamente a :21460; el handshake TLS solicita certificado cliente y sin certificado retorna a /errors/no-certificate."
    client_tls_auth: "SI"
    evidence_ids: ["P09", "P09A", "D06", "DP38A", "DP38B", "VALLADOLID-PROCEDURE-2026-08-13", "VALLADOLID-LOGIN-2026-08-13", "VALLADOLID-CERT-REDIRECT-2026-08-13", "VALLADOLID-CLIENT-TLS-2026-08-13"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA para host, ruta y puerto exactos; sin verificación E2E. La firma documental, cofirma, contrafirma y presentación jurídica permanecen bloqueadas."
    reviewed_at: "2026-08-13"
    next_gate: "Verificar E2E el login TLS cliente en dispositivo físico sin realizar firma documental ni presentación administrativa antes de cualquier promoción release."

  - inventory_id: "ES-PUB-0016"
    surface_key: "sevilla-sede"
    administrative_level: "MUNICIPAL"
    autonomous_community: "NO_VERIFICADO"
    province_or_municipality: "Sevilla (municipio)"
    institution_name: "Ayuntamiento de Sevilla"
    surface_name: "Sede electrónica del Ayuntamiento de Sevilla"
    surface_type: "SEDE"
    origin: "https://www.sevilla.org"
    official_site: "https://sede.sevilla.org/"
    e_sede: "https://sede.sevilla.org/"
    entry_url: "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
    procedure_page: "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_LOCAL_XADES"
    signature_format: "XAdES Enveloping"
    signature_algorithm: "SHA1withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a ATSE mediante AutoScript.sign sobre un reto efímero de 40 caracteres."
    protocol_evidence: "La cadena oficial Sede → Oficina Virtual ATSE y la entrada pública fijan AutoScript.sign(Base64(reto), SHA1withRSA, XAdES, null, callback); el reto se genera en runtime y no se codifica en la app."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P10", "P10A", "P10B", "P10C", "P10D"]
    reason: "Contrato de acceso con certificado implementado solo en QA; no se realizó E2E físico, authenticate/POST ni uso de Storage/Retrieve."
    reviewed_at: "2026-08-11"
    next_gate: "Validar físicamente callback/login sin presentar trámites; mantener QA_ONLY hasta evidencia E2E sanitizada."

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
    signature_required: "SI"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_MINIAPPLET_LOCAL_CADES"
    signature_format: "CAdES Detached"
    signature_algorithm: "SHA1withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado mediante AutoScript.sign y CAdES detached."
    protocol_evidence: "El evidence packet fija AutoScript.sign(texto,'SHA1withRSA','CAdES','',ok,error), callback ok(signatureB64,certificateB64) y configuración first-party Storage/Retrieve sin endpoint de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P12", "P12A"]
    reason: "Contrato AutoScript/MiniApplet CAdES SHA1 implementado solo en QA; no se realizó E2E ni se enviaron solicitudes a StorageService/RetrieveService; esas URLs son solo configuración de almacenamiento/recuperación y no un endpoint de firma."
    reviewed_at: "2026-08-09"
    next_gate: "Validar callback y login en un E2E autorizado; mantener QA_ONLY y no enviar Storage/Retrieve sin evidencia separada."

  - inventory_id: "ES-PUB-0019"
    surface_key: "us-sede"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "Andalucía"
    province_or_municipality: "Sevilla"
    institution_name: "Universidad de Sevilla"
    surface_name: "Sede electrónica de la Universidad de Sevilla"
    surface_type: "SEDE"
    origin: "https://sede.us.es"
    official_site: "https://sede.us.es/"
    e_sede: "https://sede.us.es/"
    entry_url: "https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La sede US delega esta presentación al Registro Electrónico de la AGE mediante un enlace público exacto a REG-AGE."
    protocol_evidence: "La ficha oficial ISG_01 indica que el trámite se realiza en el Registro Electrónico de la AGE y que «Iniciar trámite» conduce exactamente a https://reg.redsara.es/es/; el contrato de firma procede del perfil REG-AGE ya verificado, no de un ABI nuevo de la US."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P13", "P13A", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL; no se realizó E2E físico desde la ficha US ni se infirió un contrato de firma propio de la US."
    reviewed_at: "2026-08-09"
    next_gate: "Validar físicamente la transición US → REG-AGE y el flujo de firma antes de cualquier VERIFIED_E2E; mantener QA_ONLY."

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
    inventory_status: "VERIFIED_E2E"
    operation_summary: "Firma de challenge de sesión precalculado; tri-phase en móvil."
    protocol_evidence: "Integration JS y AutoScript públicos fijan formato, algoritmo, serverUrl y Storage/Retrieve."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P17", "P17A", "P17B", "E2E-UNIZAR-2026-07-30"]
    reason: "El portal real aceptó la firma CAdES de autenticación y abrió el buzón electrónico; verificación limitada al login observado."
    reviewed_at: "2026-07-30"
    next_gate: "Mantener bloqueadas Storage/Retrieve, cofirma, contrafirma, firma documental y presentación administrativa hasta evidencia separada."
```

### 7.2. Directorio oficial de sedes AGE [D11]

Esta ola materializa solo la relación institución-enlace publicada por D11.
No se visitaron las sedes enlazadas y todos los campos técnicos desconocidos
permanecen explícitamente sin verificar.

```yaml
records:
  - inventory_id: "ES-PUB-0021"
    surface_key: "age-administrador-de-infraestructuras-ferroviarias-adif"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Administrador de Infraestructuras Ferroviarias (ADIF)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.adif.gob.es"
    official_site: "https://sede.adif.gob.es/"
    e_sede: "https://sede.adif.gob.es/"
    entry_url: "https://sede.adif.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Transportes y Movilidad Sostenible."

  - inventory_id: "ES-PUB-0022"
    surface_key: "age-agencia-espanola-de-cooperacion-internacional-para-el-desarrollo-aecid"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Española de Cooperación Internacional para el Desarrollo (AECID)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.aecid.gob.es"
    official_site: "https://www.aecid.gob.es/"
    e_sede: "https://www.aecid.gob.es/"
    entry_url: "https://www.aecid.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Asuntos Exteriores, Unión Europea y Cooperación."

  - inventory_id: "ES-PUB-0023"
    surface_key: "age-agencia-espanola-de-medicamentos-y-productos-sanitarios-aemps"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Española de Medicamentos y Productos Sanitarios (AEMPS)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.aemps.gob.es"
    official_site: "https://sede.aemps.gob.es/"
    e_sede: "https://sede.aemps.gob.es/"
    entry_url: "https://sede.aemps.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.aemps.gob.es/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede de AEMPS ofrece públicamente una entrada al Registro Electrónico General y delega esa actuación al servicio REG-AGE."
    protocol_evidence: "La portada oficial AEMPS contiene un enlace público con texto «Registro», title «Abre en una pestaña nueva: portal del registro electrónico general» y href https://reg.redsara.es/. Workspace-47 reutiliza únicamente el startUrl canónico ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara; no se atribuye a sede.aemps.gob.es un ABI de firma propio ni se amplían sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "AEMPS-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la sede AEMPS delega públicamente en el Registro Electrónico General y se lanza solo el startUrl canónico exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición AEMPS → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio de Sanidad."

  - inventory_id: "ES-PUB-0024"
    surface_key: "age-agencia-estatal-de-meteorologia-aemet"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal de Meteorología (AEMET)"
    surface_name: "AEMET — Solicitud certificados y datos / Sede electrónica"
    surface_type: "SEDE"
    origin: "https://sede.aemet.gob.es"
    official_site: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
    e_sede: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
    entry_url: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
    procedure_page: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/solicitudes"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoFirma MiniApplet"
    protocol_family: "AEMET_SEDE_CERTIFICATE_LOGIN_PUBLIC_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada desde la entrada oficial estable de la Sede AEMET hacia el flujo público 'Solicitud certificados y datos'; el login por certificado/AutoFirma se registra como frontera observada, no como capacidad criptográfica implementada."
    protocol_evidence: "La entrada oficial AEMET publica 'Solicitud certificados y datos' y 'Nueva Solicitud'. En una sesión pública normal, 'Usuarios en general' abre https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/formularioSolicitud?tipoSolicitud=L1, que exige identificación previa y ofrece usuario/contraseña o DNI-e/certificado digital. La ruta pública https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso muestra 'Acceso con certificado digital', declara necesaria la aplicación AUTOFIRMA, carga miniapplet.js y firma.js y contiene un POST same-origin a /AEMET/es/GestionPeticiones/ssoLogin con campos ocultos signature/errorMessage. No se ejecutó el POST, no se invocó firma privada y no se verificaron payload, algoritmo, formato, callback ni endpoint de firma. Los deep links dependen de sesión; por ello el startUrl implementado permanece en la entrada oficial estable."
    client_tls_auth: "NO_EN_CONTORNO_OBSERVADO"
    evidence_ids: ["D11", "AEMET-SEDE-2026-08-23", "AEMET-PROCEDURE-2026-08-23", "AEMET-NEW-SOLICITUD-2026-08-23", "AEMET-L1-2026-08-23", "AEMET-SSO-2026-08-23"]
    reason: "Perfil nuevo QA_ONLY limitado a navegación same-origin desde la entrada oficial estable. Los mecanismos CERTIFICATE_ACCESS/ELECTRONIC_SIGNATURE/AUTOFIRMA/MINIAPPLET reflejan únicamente la frontera pública observada; el perfil mantiene capabilities vacío y no implementa SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH, carga documental ni presentación final. Falta E2E físico."
    reviewed_at: "2026-08-23"
    next_gate: "Validar físicamente la navegación QA desde la entrada oficial hasta la frontera de identificación; ampliar autenticación o firma solo con un contrato AEMET-specific exacto y una gate independiente."
    notes: "No se enviaron usuario/contraseña, certificado, signature, documentos ni formularios; no se realizó firma, presentación final ni pago. certificateRules del perfil son metadatos estructurales inertes porque capabilities está vacío."

  - inventory_id: "ES-PUB-0025"
    surface_key: "age-agencia-estatal-de-seguridad-aerea-aesa"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal de Seguridad Aérea (AESA)"
    surface_name: "Sede electrónica / Solicitud general"
    surface_type: "SEDE"
    origin: "https://sede.seguridadaerea.gob.es"
    official_site: "https://sede.seguridadaerea.gob.es/sede-aesa/"
    e_sede: "https://sede.seguridadaerea.gob.es/sede-aesa/"
    entry_url: "https://sede.seguridadaerea.gob.es/sede-aesa/catalogo-de-procedimientos/solicitud-general"
    procedure_page: "https://sede.seguridadaerea.gob.es/sede-aesa/catalogo-de-procedimientos/solicitud-general"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "AutoFirma"
    protocol_family: "AESA_SOLICITUD_GENERAL_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Lanzamiento público QA de la Solicitud general de AESA (SIA 203553); la Sede documenta acceso autenticado y firma electrónica, pero la implementación no ejecuta autenticación ni firma."
    protocol_evidence: "La ficha oficial de Solicitud general enlaza el inicio online y declara presentación telemática autenticada. La ayuda oficial de identificación/firma admite DNIe, certificados soportados por @firma y exige AutoFirma para DNIe/certificados. El inicio online confirma certificado digital/DNIe/Cl@ve, pero no publica un ABI exacto de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "AESA-SEDE-2026-08-23", "AESA-SOLGEN-2026-08-23", "AESA-ID-FIRMA-2026-08-23"]
    reason: "Perfil QA_ONLY limitado al lanzamiento público exacto de Solicitud general, sin capabilities sensibles. `CERTIFICATE_ACCESS`, `ELECTRONIC_SIGNATURE` y `AUTOFIRMA` son mecanismos documentados por AESA; no se infieren CLIENT_TLS_AUTH, formato, algoritmo, endpoint, callback ni presentación final."
    reviewed_at: "2026-08-23"
    next_gate: "Observar de forma controlada el contrato runtime exacto de autenticación/firma antes de añadir CLIENT_TLS_AUTH o SIGN; mantener QA_ONLY hasta E2E separado."
    notes: "La Sede devuelve HTTP 200 en la landing, ficha de Solicitud general, ayuda de identificación/firma e inicio online revisados desde Pipupa Stable. El trámite online exacto es /oficina/tramites/altaSolicitud.do?codArea=SOLGEN&id=4."

  - inventory_id: "ES-PUB-0026"
    surface_key: "age-agencia-estatal-del-boletin-oficial-del-estado-boe"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal del Boletín Oficial del Estado (BOE)"
    surface_name: "Sede electrónica / información pública y trámites administrativos"
    surface_type: "SEDE"
    origin: "https://www.boe.es"
    official_site: "https://www.boe.es/"
    e_sede: "https://www.boe.es/informacion/index.php"
    entry_url: "https://www.boe.es/informacion/index.php"
    procedure_page: "https://www.boe.es/informacion/index.php"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "BOE_SEDE_PUBLIC_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada exclusivamente a la página pública estable «La Sede Electrónica» de BOE.es. Los trámites administrativos que saltan a extranet.boe.es, la autenticación y cualquier firma quedan fuera del contrato implementado."
    protocol_evidence: "La página first-party https://www.boe.es/informacion/index.php se identifica como «La Sede Electrónica», publica normativa de creación de la sede/registro y enumera Trámites Administrativos. Las acciones Anuncios, Quejas y sugerencias y ARDE/CSV delegan a https://extranet.boe.es, que constituye una frontera/origin distinta y no se añade al perfil. La página de sistemas de firma declara que la sede admite certificados electrónicos reconocidos o cualificados, pero no acredita para este entry un ABI, formato, algoritmo, packaging, callback ni endpoint de firma. El CSP permite afirma://*, lo que tampoco se promueve por sí solo a capacidad AutoFirma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "BOE-SEDE-2026-08-23"]
    reason: "Perfil nuevo VERIFIED_CONTRACT/QA_ONLY limitado al entry público exacto https://www.boe.es/informacion/index.php y sin capacidades sensibles. extranet.boe.es permanece fuera de trustedBrowseOrigins; firma, certificado operativo y presentación requieren un contrato separado."
    reviewed_at: "2026-08-23"
    next_gate: "Validar físicamente la navegación QA a la página pública de la Sede. Cualquier soporte de extranet.boe.es, autenticación o firma requiere inventario/contrato técnico separado con evidencia exacta."
    notes: "Investigación pública no autenticada únicamente. Se observaron enlaces first-party de Sede, páginas de sistemas de firma y la separación de origen hacia extranet.boe.es. No se abrió sesión autenticada, no se seleccionó certificado, no se llamó firma, no se cargó documento ni se realizó presentación/pago."

  - inventory_id: "ES-PUB-0027"
    surface_key: "age-autoridad-independiente-de-responsabilidad-fiscal-airef"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Autoridad Independiente de Responsabilidad Fiscal (AIReF)"
    surface_name: "AIReF — Instancia General"
    surface_type: "SEDE"
    origin: "https://sede.airef.es"
    official_site: "https://sede.airef.es/"
    e_sede: "https://sede.airef.es/"
    entry_url: "https://sede.airef.es/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01"
    procedure_page: "https://sede.airef.es/catalogo-de-tramites-es/instancia-general-es/"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet AutoScript"
    protocol_family: "AUTOSCRIPT_XADES_CLIENT_TLS_AUTH"
    signature_format: "XAdES Enveloping"
    signature_algorithm: "SHA1withRSA"
    endpoint: "https://sede.airef.es/invesiteRE/action/solicitud/sign"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Autenticación con certificado vía Cl@ve y firma local de la Instancia General AIReF mediante MiniApplet.sign sobre un payload dinámico de 32 bytes."
    protocol_evidence: "Runtime autenticado controlado: Cl@ve eIdentifier solicita certificado TLS cliente en pasarela-ident.clave.gob.es y alcanza /invesiteRE/action/solicitud/view. La página protegida carga miniapplet.js SHA-256 420dc2dfe483232e775e7f3a1a5704158ce13f7df959dce2c80468383463a11c y llama MiniApplet.sign(payloadBase64 dinámico de 32 bytes, SHA1withRSA, XAdES, null, firmaExito, firmaError). firmaExito copia firma y certificado Base64 y prepara POST a /invesiteRE/action/solicitud/sign. No se invocó MiniApplet.sign ni se envió ese POST."
    client_tls_auth: "SI"
    evidence_ids: ["D11", "AIREF-PUBLIC-2026-08-18", "AIREF-AUTH-2026-08-18", "AIREF-SIGNING-2026-08-18"]
    reason: "Contrato de autenticación y firma exacto implementado fail-closed solo en QA. El runtime controlado llegó hasta la vista previa sin ejecutar firma criptográfica ni presentación final; E2E de firma queda pendiente."
    reviewed_at: "2026-08-18"
    next_gate: "Validar en QA Android el flujo completo hasta callback de firma con credencial de prueba/autorizada y detenerse antes de cualquier presentación administrativa; promover a release solo con evidencia E2E separada."
    notes: "SIA 216016. El payload de firma y el id de borrador son dinámicos y no se fijan como constantes. Se creó un borrador intermedio sin firma durante la inspección controlada; no se eliminó ni se registró porque las acciones destructivas y la presentación final quedan fuera de §3.1."

  - inventory_id: "ES-PUB-0028"
    surface_key: "age-biblioteca-nacional-de-espana"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Biblioteca Nacional de España"
    surface_name: "Quejas y sugerencias — Biblioteca Nacional de España"
    surface_type: "SEDE"
    origin: "https://sede.bne.gob.es"
    official_site: "https://sede.bne.gob.es/"
    e_sede: "https://sede.bne.gob.es/"
    entry_url: "https://sede.bne.gob.es/es/tramites/quejas-sugerencias"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.bne.gob.es/es/tramites/quejas-sugerencias"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede BNE ofrece el Registro Electrónico General como vía electrónica para presentar quejas o sugerencias dirigidas a la Biblioteca Nacional de España."
    protocol_evidence: "La ficha oficial Quejas y sugerencias de la BNE ofrece explícitamente el Registro Electrónico General y su enlace público conduce exactamente a https://reg.redsara.es/es/; el contrato de firma pertenece al perfil REG-AGE ya cubierto y no se atribuye un ABI propio a sede.bne.gob.es."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "BNE-REG-2026-08-16", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL oficial; se conserva la página BNE como entry URL, no se amplía trust al origin BNE y falta E2E físico de la transición."
    reviewed_at: "2026-08-16"
    next_gate: "Validar físicamente la transición BNE → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio de Cultura."

  - inventory_id: "ES-PUB-0029"
    surface_key: "age-centro-de-investigaciones-energeticas-medioambientales-y-tecnologicas-ciemat"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Centro de Investigaciones Energéticas, Medioambientales y Tecnológicas (CIEMAT)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.ciemat.gob.es"
    official_site: "https://sede.ciemat.gob.es/"
    e_sede: "https://sede.ciemat.gob.es/"
    entry_url: "https://sede.ciemat.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Ciencia, Innovación y Universidades. La URL sessionizada de la fuente se descartó; solo se conserva el origin exacto."

  - inventory_id: "ES-PUB-0030"
    surface_key: "age-centro-para-el-desarrollo-tecnologico-industrial-cdti"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Centro para el Desarrollo Tecnológico Industrial (CDTI)"
    surface_name: "Validar certificado digital — CDTI"
    surface_type: "SEDE"
    origin: "https://sede.cdti.gob.es"
    official_site: "https://sede.cdti.gob.es/"
    e_sede: "https://sede.cdti.gob.es/"
    entry_url: "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
    procedure_page: "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_LOCAL_XADES_ENVELOPING"
    signature_format: "XAdES Enveloping"
    signature_algorithm: "SHA512withRSA; la rama macOS pública usa SHA256withRSA y queda fuera del perfil Android"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Validación pública de certificado: la página carga AutoScript, firma un reto dinámico CertExp con XAdES Enveloping y reenvía firma y certificado en campos ocultos."
    protocol_evidence: "La página pública ejecuta AutoScript.sign(dataB64, SHA512withRSA, XAdES Enveloping, filters=nonexpired, SignatureOKFunction, SignatureErrorFunction) fuera de macOS. Tres GET independientes del 2026-08-16 mostraron tokens Base64 sin padding distintos con forma estable CertExp + 32 hex minúsculas + 24 alfanuméricos minúsculos; el bridge valida la forma literal antes de decodificar; el perfil restaura el único padding = y valida que los bytes decodificados puedan proceder de esa forma exacta, tolerando únicamente los bits Base64 finales no significativos observados; el callback permanece exacto."
    client_tls_auth: "NO"
    evidence_ids: ["D11", "CDTI-CERT-2026-08-16"]
    reason: "Contrato pre-auth completo y acotado implementado en QA con XAdES Enveloping local; sin ampliar confianza fuera de la URL exacta y sin promover release antes de E2E físico."
    reviewed_at: "2026-08-16"
    next_gate: "E2E físico seguro de la validación de certificado en Android, sin presentar solicitud administrativa."
    notes: "El perfil implementa únicamente la rama no-macOS observada: SHA512withRSA. La rama macOS SHA256withRSA permanece fail-closed."

  - inventory_id: "ES-PUB-0031"
    surface_key: "age-comisionado-para-el-mercado-de-tabacos-cmt"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comisionado para el Mercado de Tabacos (CMT)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.cmt.gob.es"
    official_site: "https://sede.cmt.gob.es/"
    e_sede: "https://sede.cmt.gob.es/"
    entry_url: "https://sede.cmt.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Hacienda."

  - inventory_id: "ES-PUB-0032"
    surface_key: "age-comision-nacional-de-los-mercados-y-la-competencia-cnmc"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comisión Nacional de los Mercados y la Competencia (CNMC)"
    surface_name: "Remisión de solicitudes, escritos y comunicaciones — Sede electrónica CNMC"
    surface_type: "SEDE"
    origin: "https://sede.cnmc.gob.es"
    official_site: "https://sede.cnmc.gob.es/"
    e_sede: "https://sede.cnmc.gob.es/"
    entry_url: "https://sede.cnmc.gob.es/tramites/general/remision-de-solicitudes-escritos-y-comunicaciones"
    procedure_page: "https://sede.cnmc.gob.es/tramites/general/remision-de-solicitudes-escritos-y-comunicaciones"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CNMC_PUBLIC_PROCEDURE_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada únicamente a la página pública vigente del trámite general «Remisión de solicitudes, escritos y comunicaciones». Los launches de autenticación/formulario en tramites.cnmc.gob.es y tramitesclave.cnmc.gob.es, la firma y la presentación final quedan fuera del contrato implementado."
    protocol_evidence: "La página first-party del trámite devuelve HTTP 200, figura Activo y «Trámites online con certificado digital», y publica dos accesos externos: https://tramitesclave.cnmc.gob.es/formulario/21 (Cl@ve) y https://tramites.cnmc.gob.es/formulario/21 (certificado electrónico). Las instrucciones indican que los datos del firmante se obtienen del certificado usado para firmar electrónicamente, pero la página pública no acredita para este entry un ABI, formato, algoritmo, packaging, callback ni endpoint de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CNMC-GENERAL-2026-08-23"]
    reason: "Perfil nuevo VERIFIED_CONTRACT/QA_ONLY limitado al procedure page first-party exacto en sede.cnmc.gob.es y sin capacidades sensibles. tramites.cnmc.gob.es y tramitesclave.cnmc.gob.es permanecen fuera del navigation trust; autenticación y firma requieren contratos separados."
    reviewed_at: "2026-08-23"
    next_gate: "Validar físicamente la navegación QA al procedure page público. Cualquier soporte de launch autenticado, certificado o firma requiere contrato técnico separado con evidencia exacta."
    notes: "Investigación pública no autenticada únicamente. No se inició Cl@ve, no se proporcionó certificado, no se rellenó formulario, no se cargaron documentos, no se firmó ni se presentó solicitud."

  - inventory_id: "ES-PUB-0033"
    surface_key: "age-comision-nacional-del-mercado-de-valores-cnmv"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comisión Nacional del Mercado de Valores (CNMV)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.cnmv.gob.es"
    official_site: "https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx"
    e_sede: "https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx"
    entry_url: "https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CNMV_PUBLIC_SEDE_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA delimitada a la Sede pública oficial; certificados y plataforma de firma constan como evidencia descriptiva, sin exponer autenticación, firma ni presentación final."
    protocol_evidence: "La landing first-party devuelve HTTP 200 y documenta certificados electrónicos válidos, presentación por Zona abierta con certificado y una plataforma de firma que remite a la aplicación oficial; no publica un ABI/algoritmo/formato/callback de firma suficiente para exponer SIGN."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CNMV-SEDE-2026-08-23"]
    reason: "La Sede first-party está revalidada y permite un contrato QA-only de navegación pública. Las referencias a certificado/AutoFirma son descriptivas; autenticación, selección de certificado, firma y presentación final permanecen fuera del contrato implementado."
    reviewed_at: "2026-08-23"
    next_gate: "Validar físicamente la navegación QA. Cualquier soporte de autenticación o firma requiere evidencia técnica exacta separada."
    notes: "Ministerio(s) enumerador(es): Ministerio de Economía, Comercio y Empresa. Investigación pública no autenticada; no se seleccionó certificado, no se firmó ni se presentó documentación."

  - inventory_id: "ES-PUB-0034"
    surface_key: "age-consejo-de-seguridad-nuclear-csn"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Consejo de Seguridad Nuclear (CSN)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.csn.gob.es"
    official_site: "https://sede.csn.gob.es/"
    e_sede: "https://sede.csn.gob.es/"
    entry_url: "https://sede.csn.gob.es/"
    procedure_page: "https://sede.csn.gob.es/Sede20/identificacion?tipoacceso=3"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Entrada pública de la Sede CSN y selector de acceso por certificado digital/DNIe, Cl@ve o usuario/contraseña; no se atribuye contrato de firma."
    protocol_evidence: "La página oficial de identificación publica certificado digital/DNIe, Cl@ve y usuario/contraseña, y declara certificados de clave pública no revocados soportados por @firma; no publica un ABI de firma, algoritmo, formato ni endpoint de presentación."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CSN-SEDE-2026-08-23", "CSN-IDENT-2026-08-23"]
    reason: "Perfil QA-only limitado a navegación pública de la Sede CSN. La evidencia acredita opciones de identificación, pero no CLIENT_TLS_AUTH ni un contrato de firma ejecutable; esas capacidades permanecen deshabilitadas."
    reviewed_at: "2026-08-23"
    next_gate: "Runtime autenticado controlado para observar la transición exacta de certificado/Cl@ve y, si existe, detenerse antes de cualquier firma o presentación final."
    notes: "Ministerio(s) enumerador(es): Ministerio de Industria y Turismo; Ministerio para la Transición Ecológica y el Reto Demográfico."

  - inventory_id: "ES-PUB-0035"
    surface_key: "age-consejo-de-transparencia-y-buen-gobierno-ctbg"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Consejo de Transparencia y Buen Gobierno (CTBG)"
    surface_name: "Consejo de Transparencia y Buen Gobierno (CTBG) — Solicitud de Información"
    surface_type: "SEDE"
    origin: "https://sede.consejodetransparencia.gob.es"
    official_site: "https://sede.consejodetransparencia.gob.es/"
    e_sede: "https://sede.consejodetransparencia.gob.es/"
    entry_url: "https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6"
    procedure_page: "https://sede.consejodetransparencia.gob.es/catalog/t/01b4b72b-7f21-4d7c-9576-e1d7871624a6"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CTBG_ESPUBLICO_CLAVE_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Solicitud de Información: perfil QA-only para la entrada electrónica exacta y la navegación observada hasta la frontera de identificación Cl@ve/certificado."
    protocol_evidence: "La Sede vigente publica el catálogo en /dossier y la Solicitud de Información en /catalog/t/01b4b72b-7f21-4d7c-9576-e1d7871624a6. El control Iniciar tramitación electrónica abre exactamente /catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6 y, en Chromium público, alcanza la página Identificación electrónica. Esa página ofrece Cl@ve/certificado y contiene un formulario POST a https://pasarela.clave.gob.es/Proxy2/ServiceProvider con campos SAMLRequest y RelayState. No se conservaron sus valores ni se envió el formulario. No se atribuye contrato de firma específico del procedimiento."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CTBG-DOSSIER-2026-08-23", "CTBG-SOLINFO-DETAIL-2026-08-23", "CTBG-SOLINFO-LAUNCH-2026-08-23", "CTBG-CLAVE-2026-08-23"]
    reason: "Perfil QA-only limitado al launch exacto de Solicitud de Información y al primer origen Cl@ve observado. El certificado es una alternativa de identificación; no se expone SIGN, CLIENT_TLS_AUTH, endpoint, formato ni algoritmo; sin E2E."
    reviewed_at: "2026-08-23"
    next_gate: "Con identidad autorizada, observar el estado autenticado de Solicitud de Información; detenerse antes de cualquier firma criptográfica, registro final o pago."
    notes: "Ministerio(s) enumerador(es): Ministerio para la Transformación Digital y de la Función Pública."

  - inventory_id: "ES-PUB-0036"
    surface_key: "age-consejo-superior-de-deportes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Consejo Superior de Deportes"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.csd.gob.es"
    official_site: "https://sede.csd.gob.es/"
    e_sede: "https://sede.csd.gob.es/"
    entry_url: "https://sede.csd.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Educación, Formación Profesional y Deportes."

  - inventory_id: "ES-PUB-0037"
    surface_key: "age-consejo-superior-de-investigaciones-cientificas-csic"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Consejo Superior de Investigaciones Científicas (CSIC)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.csic.gob.es"
    official_site: "https://sede.csic.gob.es/"
    e_sede: "https://sede.csic.gob.es/"
    entry_url: "https://sede.csic.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Ciencia, Innovación y Universidades."

  - inventory_id: "ES-PUB-0038"
    surface_key: "age-cuerpo-nacional-de-policia"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Cuerpo Nacional de Policía"
    surface_name: "Sede electrónica de la Policía Nacional — Solicitud genérica"
    surface_type: "SEDE"
    origin: "https://sede.policia.gob.es"
    official_site: "https://sede.policia.gob.es/"
    e_sede: "https://sede.policia.gob.es/"
    entry_url: "https://sede.policia.gob.es/"
    procedure_page: "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / customsign.js"
    protocol_family: "AUTOSCRIPT_LOCAL_XADES"
    signature_format: "XAdES detached"
    signature_algorithm: "SHA1withRSA"
    endpoint: "Sin endpoint trifásico: customsign.js invoca AutoScript.sign localmente con SHA1withRSA, XAdES y filtros DNIe/no expirado, devolviendo signatureB64 al formulario."
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma de solicitud genérica en la Sede de la Policía Nacional mediante AutoScript.sign en formato XAdES Detached con SHA1withRSA."
    protocol_evidence: "solicitudGenerica.xhtml y customsign.js cargan AutoScript y llaman AutoScript.sign(dataB64, 'SHA1withRSA', 'XAdES', extraProperties, callback) con filtros de certificado DNIe/nonexpired y keyusage.nonrepudiation."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "POLICIA-SEDE-2026-08-15", "POLICIA-SOLICITUD-2026-08-15"]
    reason: "Contrato de firma XAdES Detached implementado solo en QA; no se realizó E2E físico/manual, autenticación real ni presentación administrativa. El perfil no promueve release ni VERIFIED_E2E."
    reviewed_at: "2026-08-15"
    next_gate: "E2E físico/manual autorizado sobre el flujo real; no promover release ni VERIFIED_E2E sin evidencia separada."
    notes: "Ministerio(s) enumerador(es): Ministerio del Interior."

  - inventory_id: "ES-PUB-0039"
    surface_key: "age-direccion-general-de-fondos-europeos"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Fondos Europeos"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sedefondoscomunitarios.gob.es"
    official_site: "https://sedefondoscomunitarios.gob.es/"
    e_sede: "https://sedefondoscomunitarios.gob.es/"
    entry_url: "https://sedefondoscomunitarios.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Hacienda."

  - inventory_id: "ES-PUB-0040"
    surface_key: "age-direccion-general-de-la-guardia-civil"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de la Guardia Civil"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.guardiacivil.gob.es"
    official_site: "https://sede.guardiacivil.gob.es/"
    e_sede: "https://sede.guardiacivil.gob.es/"
    entry_url: "https://sede.guardiacivil.gob.es/"
    procedure_page: "https://sede.guardiacivil.gob.es/procedimientos/index/language/es_ES"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AUTOFIRMA"
    protocol_family: "GUARDIA_CIVIL_CLAVE_AUTOFIRMA_PUBLIC_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Entrada pública de la Sede de la Guardia Civil y catálogo de procedimientos; identificación mediante Cl@ve con DNIe/certificado/Cl@ve móvil o permanente, y firma de solicitudes cuando el procedimiento lo exige."
    protocol_evidence: "La instrucción oficial vigente de 11-05-2026 para inscripción exige autenticación mediante Cl@ve (DNIe, certificado electrónico, Cl@ve móvil o permanente) y envío del formulario firmado. La documentación oficial de requisitos de la Sede describe firma básica, firma con certificado y AutoFirma para certificado local; no acredita aquí ABI, algoritmo, formato ni endpoint exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "GC-SEDE-2026-08-23", "GC-PROCEDURES-2026-08-23", "GC-INSTRUCCION-2026-05-11"]
    reason: "Perfil QA-only limitado a navegación pública y metadata observada. La evidencia acredita Cl@ve/certificado y firma con AutoFirma en determinados flujos, pero no se expone SIGN ni CLIENT_TLS_AUTH sin contrato runtime exacto."
    reviewed_at: "2026-08-23"
    next_gate: "Progresión autenticada controlada hasta pre-firma para observar la invocación AutoFirma, algoritmo, formato y callback exactos, deteniéndose antes de firma criptográfica o presentación final."
    notes: "Ministerio(s) enumerador(es): Ministerio del Interior."

  - inventory_id: "ES-PUB-0041"
    surface_key: "age-direccion-general-de-ordenacion-del-juego"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Ordenación del Juego"
    surface_name: "Sede electrónica / navegación pública de procedimientos"
    surface_type: "SEDE"
    origin: "https://sede.ordenacionjuego.gob.es"
    official_site: "https://sede.ordenacionjuego.gob.es/"
    e_sede: "https://sede.ordenacionjuego.gob.es/"
    entry_url: "https://sede.ordenacionjuego.gob.es/"
    procedure_page: "https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DGOJ_PUBLIC_NAVIGATION_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA-only por la Sede pública de la DGOJ y su índice público de procedimientos y servicios."
    protocol_evidence: "La Sede first-party y el índice público de procedimientos responden sin autenticación. Las páginas oficiales de sistemas de firma documentan @firma, Cl@ve Firma, AutoFirma y DNIeRemote, pero no acreditan para una operación exacta un ABI, formato, algoritmo, endpoint o callback; por ello el runtime queda limitado a navegación pública del origin DGOJ sin capacidades sensibles."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DGOJ-PUBLIC-2026-08-24"]
    reason: "Perfil QA-only de navegación pública limitado al origin exacto sede.ordenacionjuego.gob.es; no se infiere SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH ni contrato criptográfico a partir de documentación descriptiva."
    reviewed_at: "2026-08-24"
    next_gate: "Seleccionar un procedimiento público concreto y verificar su contrato técnico exacto antes de exponer cualquier capacidad de firma o autenticación."
    notes: "Investigación pública no autenticada y de solo lectura; no se ejecutaron login, selección de certificado, firma, pago, carga ni presentación."

  - inventory_id: "ES-PUB-0042"
    surface_key: "age-direccion-general-de-seguros-y-fondos-de-pensiones"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Seguros y Fondos de Pensiones"
    surface_name: "Sede electrónica / inicio público oficial"
    surface_type: "SEDE"
    origin: "https://www.sededgsfp.gob.es"
    official_site: "https://www.sededgsfp.gob.es/"
    e_sede: "https://www.sededgsfp.gob.es/"
    entry_url: "https://www.sededgsfp.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DGSFP_PUBLIC_SEDE_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA delimitada al inicio público first-party de la Sede DGSFP; autenticación, certificado, Cl@ve, AutoFirma y tramitación permanecen fuera del contrato implementado."
    protocol_evidence: "La raíz first-party redirige same-origin a /es/Paginas/inicio.aspx y responde HTTP 200. Los bundles públicos de la propia Sede contienen UI/servicios para certificado, Cl@ve, procedimientos, notificaciones y AutoFirma/TestFirma, pero no acreditan un signer ABI, formato, algoritmo, callback ni aceptación de presentación exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DGSFP-SEDE-2026-08-24"]
    reason: "La entrada pública first-party y su origin están revalidados y permiten solo un contrato QA-only de navegación. Las referencias de certificado/Cl@ve/AutoFirma en bundles no se promocionan a SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH ni AFIRMA_URI sin contrato técnico exacto y E2E físico."
    reviewed_at: "2026-08-24"
    next_gate: "Validar navegación QA al inicio público. Cualquier soporte de autenticación, certificado, Cl@ve, AutoFirma o presentación requiere una revisión separada del contrato técnico exacto y prueba física E2E."
    notes: "Ministerio(s) enumerador(es): Ministerio de Economía, Comercio y Empresa. Investigación pública no autenticada: no se seleccionó certificado, no se inició sesión, no se firmó, no se cargó documentación y no se realizó presentación administrativa. Cookies/request IDs SharePoint no se conservaron."

  - inventory_id: "ES-PUB-0043"
    surface_key: "age-direccion-general-del-catastro"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General del Catastro"
    surface_name: "Dirección General del Catastro — Otras solicitudes y escritos genéricos"
    surface_type: "SEDE"
    origin: "https://www.sedecatastro.gob.es"
    official_site: "https://www.sedecatastro.gob.es/"
    e_sede: "https://www.sedecatastro.gob.es/"
    entry_url: "https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22"
    procedure_page: "https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CATASTRO_CLAVE_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Perfil QA-only para otras solicitudes, escritos por discrepancias y documentos genéricos, limitado al launch público exacto y a la frontera de identificación observada."
    protocol_evidence: "La Sede vigente publica la categoría de trámites en https://www.sedecatastro.gob.es/Accesos/SECAccTramites.aspx. La entrada exacta Dest=22 muestra el trámite Presentar otras solicitudes, escritos por discrepancias con la descripción catastral y documentos genéricos. El control Ir al formulario realiza un POST same-origin y alcanza https://www.sedecatastro.gob.es/Accesos/SECAccDNI.aspx?Dest=22, donde se ofrecen certificado digital o Cl@ve. La opción Cl@ve estable apunta a https://www.sedecatastro.gob.es/Accesos/SECAccPIN.aspx?Dest=22&texp=REGI; esa página publica el flujo Cl@ve y un POST boundary a https://pasarela.clave.gob.es/Proxy2/ResponseRedirect. No se conservaron valores de ASP.NET state, SAML ni cookies, y no se envió autenticación."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CATASTRO-HOME-2026-08-24", "CATASTRO-TRAMITES-2026-08-24", "CATASTRO-DEST22-2026-08-24", "CATASTRO-DNI22-2026-08-24", "CATASTRO-PIN22-2026-08-24", "CATASTRO-CLAVE-2026-08-24"]
    reason: "Perfil QA-only de navegación. La alternativa de certificado acredita acceso posible, no client TLS ni firma. No se exponen SIGN, CLIENT_TLS_AUTH, endpoint, formato ni algoritmo; sin E2E."
    reviewed_at: "2026-08-24"
    next_gate: "Con identidad autorizada, observar el primer estado autenticado del formulario Dest=22; detenerse antes de cualquier firma, registro final, presentación administrativa o pago."
    notes: "Ministerio(s) enumerador(es): Ministerio de Hacienda."

  - inventory_id: "ES-PUB-0044"
    surface_key: "age-enaire-entidad-publica-empresarial"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "ENAIRE, Entidad Pública Empresarial"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://enaire.sede.gob.es"
    official_site: "https://enaire.sede.gob.es/"
    e_sede: "https://enaire.sede.gob.es/"
    entry_url: "https://enaire.sede.gob.es/"
    procedure_page: "https://enaire.sede.gob.es/procedimientos"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AUTOFIRMA"
    protocol_family: "ENAIRE_CLAVE_AUTOFIRMA_PUBLIC_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Entrada pública de la Sede ENAIRE y catálogo de procedimientos; acceso a expedientes mediante Cl@ve y firma electrónica con certificado cuando el trámite la requiere."
    protocol_evidence: "La Sede pública vigente expone catálogo de procedimientos y área privada mediante Cl@ve. La página oficial Requisitos indica expresamente que para firmar electrónicamente con certificado es necesario AutoFirma y que para iniciar/acceder a expedientes se usa Cl@ve (DNI-e, certificado electrónico, Cl@ve PIN o Permanente). No se publica aquí ABI, algoritmo, formato ni endpoint exactos de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "ENAIRE-SEDE-2026-08-24", "ENAIRE-PROCEDURES-2026-08-24", "ENAIRE-REQ-2026-08-24", "ENAIRE-VALIDACION-2026-08-24"]
    reason: "Perfil QA-only limitado a navegación pública y metadata observada. Se acredita Cl@ve/certificado y requisito de AutoFirma para firma electrónica con certificado, pero no se expone SIGN ni CLIENT_TLS_AUTH sin contrato runtime exacto."
    reviewed_at: "2026-08-24"
    next_gate: "Progresión autenticada controlada hasta pre-firma para observar invocación AutoFirma, algoritmo, formato y callback exactos, deteniéndose antes de firma criptográfica o presentación final."
    notes: "Ministerio(s) enumerador(es): Ministerio de Transportes y Movilidad Sostenible."

  - inventory_id: "ES-PUB-0045"
    surface_key: "age-entidad-publica-empresarial-de-suelo-sepes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Entidad Pública Empresarial de Suelo (SEPES)"
    surface_name: "Quejas y reclamaciones — SEPES / Sede Transportes"
    surface_type: "SEDE"
    origin: "https://sede.transportes.gob.es"
    official_site: "https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones"
    e_sede: "https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones"
    entry_url: "https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones"
    procedure_page: "https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones"
    certificate_required: "NO_VERIFICADO"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "SEPES_TRANSPORTES_PUBLIC_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA exacta a la ficha pública vigente de Quejas y reclamaciones de SEPES dentro de la Sede del Ministerio de Transportes; autenticación, firma y presentación quedan fuera del contrato implementado."
    protocol_evidence: "La Sede Transportes devuelve HTTP 200/TLS válido para la ficha first-party «Quejas y reclamaciones» identificada expresamente como Entidad Pública Empresarial de Suelo (SEPES), y publica Iniciar trámite hacia /Procedimiento/?procedureKey=7601. La normativa vigente de la misma Sede conserva el convenio de incorporación de SEPES a la Sede y Registro electrónicos del Ministerio. El launch 7601 redirige actualmente a /Procedimiento/Auth?procedureKey=7601 y termina HTTP 500; por ello no se promociona ningún contrato de autenticación o firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "SEPES-TRANSPORTES-2026-08-24", "SEPES-TRANSPORTES-NORMATIVA-2026-08-24"]
    reason: "Existe una ruta oficial first-party vigente y TLS-válida ligada directamente a SEPES, suficiente para navegación QA. El launch procedureKey=7601 termina actualmente HTTP 500, así que no se modela autenticación o firma. El perfil Transportes existente id=7002 corresponde a otro procedimiento y su contrato SHA1/XAdES no se reutiliza; el origin heredado www.sepes.es mantiene un certificado caducado y no se usa ni se sortea."
    reviewed_at: "2026-08-24"
    next_gate: "Validar físicamente la navegación QA a la ficha SEPES. Revalidar procedureKey=7601 cuando deje de devolver 500 antes de modelar autenticación, certificado, firma o presentación."
    notes: "Ministerio(s) enumerador(es): Ministerio de Transportes y Movilidad Sostenible. La compatibilidad añadida es únicamente de navegación pública. El soporte de dos perfiles QA sobre sede.transportes.gob.es se mantiene fail-closed: la resolución global solo desambigua por startUrl exacto y los flujos con perfil activo usan resolución profile-scoped. No se seleccionó certificado, no se firmó, no se cargó documentación y no se presentó trámite."

  - inventory_id: "ES-PUB-0046"
    surface_key: "age-fondo-de-garantia-salarial-fogasa"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Fondo de Garantía Salarial (FOGASA)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.mites.gob.es"
    official_site: "https://www.mites.gob.es/fogasa/default.html"
    e_sede: "https://www.mites.gob.es/fogasa/default.html"
    entry_url: "https://www.mites.gob.es/fogasa/default.html"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Trabajo y Economía Social."

  - inventory_id: "ES-PUB-0047"
    surface_key: "age-fondo-espanol-de-garantia-agraria-o-a-fega"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Fondo Español de Garantía Agraria O.A. (FEGA)"
    surface_name: "FEGA — Solicitud al FEGA"
    surface_type: "SEDE"
    origin: "https://www3.sede.fega.gob.es"
    official_site: "https://www.sede.fega.gob.es/"
    e_sede: "https://www.sede.fega.gob.es/"
    entry_url: "https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02"
    procedure_page: "https://www.sede.fega.gob.es/content/solicitud-al-fega"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "FEGA_CLAVE_PUBLIC_REGISTRY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Perfil QA-only para Solicitud al FEGA, limitado al launch público OFVSG02 y a la frontera Cl@ve observada."
    protocol_evidence: "La Sede FEGA vigente publica Solicitud al FEGA y su Acceso en línea en https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02. La landing del Registro Electrónico enumera identificación por DNIe/certificado, Cl@ve PIN y Cl@ve permanente, y carga autoscript.js, pero también exige disponer previamente de un PDF completado y firmado; por ello no se infiere un ABI de firma del portal. El control público Continuar realiza POST same-origin a https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/registroAsientos.action y en Chromium alcanza https://pasarela.clave.gob.es/Proxy2/ServiceProvider. No se conservaron valores SAML/RelayState, cookies ni credenciales y no se envió autenticación."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "FEGA-HOME-2026-08-24", "FEGA-PROCEDURES-2026-08-24", "FEGA-SOLICITUD-2026-08-24", "FEGA-OFVSG02-2026-08-24", "FEGA-REGPOST-2026-08-24", "FEGA-CLAVE-2026-08-24"]
    reason: "Perfil QA-only de navegación. El acceso admite certificado o Cl@ve, pero no se ha probado client TLS ni un contrato de firma del portal; autoscript.js por sí solo no se promueve a SIGN. Sin endpoint, formato, algoritmo ni E2E."
    reviewed_at: "2026-08-24"
    next_gate: "Con identidad autorizada, observar el primer estado autenticado del Registro Electrónico OFVSG02; detenerse antes de cualquier firma con clave privada o presentación/registro final."
    notes: "Ministerio(s) enumerador(es): Ministerio de Agricultura, Pesca y Alimentación."

  - inventory_id: "ES-PUB-0048"
    surface_key: "age-fabrica-nacional-de-moneda-y-timbre-real-casa-de-la-moneda-fnmt-rcm"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Fábrica Nacional de Moneda y Timbre-Real Casa de la Moneda (FNMT-RCM)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.sede.fnmt.gob.es"
    official_site: "https://www.sede.fnmt.gob.es/"
    e_sede: "https://www.sede.fnmt.gob.es/"
    entry_url: "https://www.sede.fnmt.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Hacienda."

  - inventory_id: "ES-PUB-0049"
    surface_key: "age-instituto-cervantes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Cervantes"
    surface_name: "Sede electrónica / acceso al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://cervantes.sede.gob.es"
    official_site: "https://cervantes.sede.gob.es/"
    e_sede: "https://cervantes.sede.gob.es/"
    entry_url: "https://cervantes.sede.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://cervantes.sede.gob.es/servicio?id=Registro-Electrónico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Instituto Cervantes publica un servicio específico de acceso al Registro Electrónico General de la Administración General del Estado (REG-AGE) para escritos sin procedimiento electrónico o formulario normalizado específico."
    protocol_evidence: "La página first-party https://cervantes.sede.gob.es/servicio?id=Registro-Electrónico-General identifica expresamente el Registro Electrónico General de la AGE como REG-AGE y publica «Acceso al Registro Electrónico» hacia https://reg.redsara.es/. En una sesión Chromium pública no autenticada el enlace abrió el REG actual; la raíz REG respondió 302 por negociación de idioma y con Accept-Language español redirigió exactamente a https://reg.redsara.es/es/, startUrl canónico ya cubierto por reg-age-redsara. Workspace-47 reutiliza únicamente ese startUrl; no atribuye a cervantes.sede.gob.es un ABI de firma propio ni amplía sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CERVANTES-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede Cervantes delega explícitamente en REG-AGE y la raíz oficial publicada negocia idioma hasta el startUrl español exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Cervantes → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Deep public research incluyó Chromium/Playwright, red pública y mapa de scripts AC2. Los scripts genéricos exponen rutas AutoFirma de trámites autenticados, pero no se usan para inferir constantes de firma Cervantes ni para ampliar trust; no se invocaron POST administrativos, login, certificado, firma, carga ni presentación."

  - inventory_id: "ES-PUB-0050"
    surface_key: "age-instituto-de-astrofisica-de-canarias-iac"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto de Astrofísica de Canarias (IAC)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://iac.sede.gob.es"
    official_site: "https://iac.sede.gob.es/"
    e_sede: "https://iac.sede.gob.es/"
    entry_url: "https://iac.sede.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Ciencia, Innovación y Universidades."

  - inventory_id: "ES-PUB-0051"
    surface_key: "age-instituto-de-contabilidad-y-auditoria-de-cuentas-icac"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto de Contabilidad y Auditoría de Cuentas (ICAC)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://icac.sede.gob.es"
    official_site: "https://icac.sede.gob.es/"
    e_sede: "https://icac.sede.gob.es/"
    entry_url: "https://icac.sede.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Economía, Comercio y Empresa."

  - inventory_id: "ES-PUB-0052"
    surface_key: "age-instituto-de-credito-oficial-ico"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto de Crédito Oficial (ICO)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sedeico.gob.es"
    official_site: "https://sedeico.gob.es/web/sedeico"
    e_sede: "https://sedeico.gob.es/web/sedeico"
    entry_url: "https://sedeico.gob.es/web/sedeico"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Economía, Comercio y Empresa."

  - inventory_id: "ES-PUB-0053"
    surface_key: "age-instituto-de-mayores-y-servicios-sociales-imserso"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto de Mayores y Servicios Sociales (IMSERSO)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.imserso.gob.es"
    official_site: "https://sede.imserso.gob.es/"
    e_sede: "https://sede.imserso.gob.es/"
    entry_url: "https://sede.imserso.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Derechos Sociales, Consumo y Agenda 2030."

  - inventory_id: "ES-PUB-0054"
    surface_key: "age-instituto-de-salud-carlos-iii"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto de Salud Carlos III"
    surface_name: "Sede electrónica / selección de certificado"
    surface_type: "SEDE"
    origin: "https://sede.isciii.gob.es"
    official_site: "https://sede.isciii.gob.es/"
    e_sede: "https://sede.isciii.gob.es/"
    entry_url: "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null"
    procedure_page: "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null"
    certificate_required: "SI"
    signature_required: "NO"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_LOCAL"
    signature_format: "NO_APLICA"
    signature_algorithm: "NO_APLICA"
    endpoint: "http://dtomcat7.isciiides.es:8080/afirma-server-triphase-signer/SignatureService"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Selección local de certificado para continuar el formulario genérico de la sede."
    protocol_evidence: "AutoScript.selectCertificate(params, success, error); success recibe certB64."
    client_tls_auth: "NO"
    evidence_ids: ["D11", "ISCIII-SELECTCERT-2026-08-15"]
    reason: "Contrato público preautenticación implementado en QA: selección local y devolución de certB64; el serverUrl HTTP observado se valida como literal y no se usa como endpoint de red. Falta E2E físico seguro."
    reviewed_at: "2026-08-15"
    next_gate: "Validar en dispositivo que el callback recibe certB64 y que la navegación posterior coincide con el formulario observado, sin efectuar presentación administrativa."
    notes: "Evidencia first-party 2026-08-15: cargaApplet.jsp, autoscript.js y constantes.js; sin POST, autenticación, firma ni bypass TLS durante la investigación."

  - inventory_id: "ES-PUB-0055"
    surface_key: "age-instituto-nacional-de-administracion-publica-inap"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Nacional de Administración Pública (INAP)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.inap.gob.es"
    official_site: "https://sede.inap.gob.es/"
    e_sede: "https://sede.inap.gob.es/"
    entry_url: "https://sede.inap.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.inap.gob.es/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del INAP ofrece públicamente el Registro Electrónico General como vía de registro electrónico y delega esa actuación al servicio REG-AGE."
    protocol_evidence: "La portada oficial del INAP contiene tres enlaces públicos a https://reg.redsara.es/; uno está rotulado exactamente «Acceso al Registro Electrónico General». Workspace-47 reutiliza únicamente el startUrl canónico ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara, sin atribuir a sede.inap.gob.es un ABI de firma propio ni ampliar sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "INAP-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la sede INAP delega públicamente en REG-AGE y se lanza solo el startUrl canónico exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición INAP → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio para la Transformación Digital y de la Función Pública."

  - inventory_id: "ES-PUB-0056"
    surface_key: "age-instituto-nacional-de-estadistica-ine"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Nacional de Estadística (INE)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.ine.gob.es"
    official_site: "https://sede.ine.gob.es/"
    e_sede: "https://sede.ine.gob.es/"
    entry_url: "https://sede.ine.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Economía, Comercio y Empresa."

  - inventory_id: "ES-PUB-0057"
    surface_key: "age-instituto-para-la-transicion-justa-itj"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto para la Transición Justa (ITJ)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.transicionjusta.gob.es"
    official_site: "https://sede.transicionjusta.gob.es/"
    e_sede: "https://sede.transicionjusta.gob.es/"
    entry_url: "https://sede.transicionjusta.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio para la Transición Ecológica y el Reto Demográfico."

  - inventory_id: "ES-PUB-0058"
    surface_key: "age-instituto-social-de-las-fuerzas-armadas-isfas"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Social de las Fuerzas Armadas (ISFAS)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.isfas.gob.es"
    official_site: "https://sede.isfas.gob.es/"
    e_sede: "https://sede.isfas.gob.es/"
    entry_url: "https://sede.isfas.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Defensa."

  - inventory_id: "ES-PUB-0059"
    surface_key: "age-ministerio-de-agricultura-pesca-y-alimentacion"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Agricultura, Pesca y Alimentación"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.mapa.gob.es"
    official_site: "https://sede.mapa.gob.es/portal/site/seMAPA"
    e_sede: "https://sede.mapa.gob.es/portal/site/seMAPA"
    entry_url: "https://sede.mapa.gob.es/portal/site/seMAPA"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.mapa.gob.es/portal/site/seMAPA"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del MAPA enlaza públicamente con el Registro Electrónico General; Workspace-47 representa esa salida como una delegación acotada al perfil REG-AGE existente."
    protocol_evidence: "La portada oficial de la Sede MAPA incluye en su pie un enlace HTTPS directo a https://reg.redsara.es/ mediante el recurso reg_footer.png. Workspace-47 no asume un ABI de firma propio del MAPA ni un redirect de ese root: el alias usa únicamente el startUrl canónico ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara, sin ampliar orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MAPA-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara basado en el enlace público de la Sede MAPA al REG; faltan validación E2E física de la transición y cualquier atribución de contrato de firma específico del MAPA."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición MAPA → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio de Agricultura, Pesca y Alimentación."

  - inventory_id: "ES-PUB-0060"
    surface_key: "age-ministerio-de-asuntos-exteriores-union-europea-y-cooperacion"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Asuntos Exteriores, Unión Europea y Cooperación"
    surface_name: "Baja del Registro de Matrícula Consular — vía REG desde España"
    surface_type: "SEDE"
    origin: "https://www.exteriores.gob.es"
    official_site: "https://www.exteriores.gob.es/"
    e_sede: "https://sede.maec.gob.es/"
    entry_url: "https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La página consular oficial permite a quien ya se encuentre en España solicitar la baja del Registro de Matrícula mediante el Registro Electrónico General."
    protocol_evidence: "La página oficial de Servicios Consulares indica literalmente que, cuando el solicitante se encuentre ya en España, puede solicitar la baja mediante Registro Electrónico en https://reg.redsara.es/es/; el launch URL coincide exactamente con el startUrl del perfil reg-age-redsara y no se infiere un ABI de firma propio de Exteriores."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MAEC-REG-2026-08-16", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL oficial; se conserva la página consular de Exteriores como entry URL, no se amplía la confianza de firma al origen www.exteriores.gob.es y no se realizó E2E físico."
    reviewed_at: "2026-08-16"
    next_gate: "E2E físico seguro Exteriores → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "La implementación cubre únicamente la vía REG publicada para solicitantes que ya se encuentren en España; no modela otros canales consulares ni un contrato criptográfico propio de Exteriores."

  - inventory_id: "ES-PUB-0061"
    surface_key: "age-ministerio-de-ciencia-innovacion-y-universidades"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Ciencia, Innovación y Universidades"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://ciencia.sede.gob.es"
    official_site: "https://ciencia.sede.gob.es/"
    e_sede: "https://ciencia.sede.gob.es/"
    entry_url: "https://ciencia.sede.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio de Ciencia, Innovación y Universidades."

  - inventory_id: "ES-PUB-0062"
    surface_key: "age-ministerio-de-cultura"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Cultura"
    surface_name: "Registro Electrónico General — Ministerio de Cultura"
    surface_type: "SEDE"
    origin: "https://cultura.sede.gob.es"
    official_site: "https://cultura.sede.gob.es/"
    e_sede: "https://cultura.sede.gob.es/"
    entry_url: "https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio de Cultura publica el Registro Electrónico General de la AGE como vía para presentar solicitudes, escritos y comunicaciones sin procedimiento o formulario normalizado específico."
    protocol_evidence: "La página oficial «Registro Electrónico General» identifica explícitamente el servicio como REG-AGE y enlaza públicamente a https://reg.redsara.es/; Workspace-47 conserva esa página ministerial como entry y usa únicamente el startUrl canónico español ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara, sin atribuir un ABI de firma propio a cultura.sede.gob.es."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "CULTURA-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación oficial explícita en REG-AGE; el launch queda limitado al startUrl exacto del perfil existente, no se amplía trust al origin Cultura y falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Cultura → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La evidencia acredita únicamente la delegación al REG-AGE; no se infiere certificado, firma, AutoFirma, endpoint ni contrato criptográfico propio de la Sede de Cultura."

  - inventory_id: "ES-PUB-0063"
    surface_key: "age-ministerio-de-defensa"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Defensa"
    surface_name: "Registro Electrónico General AGE — Ministerio de Defensa"
    surface_type: "SEDE"
    origin: "https://sede.defensa.gob.es"
    official_site: "https://sede.defensa.gob.es/"
    e_sede: "https://sede.defensa.gob.es/"
    entry_url: "https://sede.defensa.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.defensa.gob.es/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede Electrónica Central del Ministerio de Defensa ofrece públicamente el Registro Electrónico General AGE como servicio externo de registro electrónico."
    protocol_evidence: "La portada oficial de la Sede de Defensa publica varias entradas rotuladas «Registro Electrónico General AGE» cuyo destino es https://rec.redsara.es/; ese endpoint público responde con redirección HTTP 301 a https://reg.redsara.es/, servicio REG-AGE ya cubierto por el perfil existente. Workspace-47 usa únicamente el startUrl canónico español ya revisado https://reg.redsara.es/es/ y no atribuye un ABI de firma propio a sede.defensa.gob.es."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DEFENSA-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación oficial explícita al Registro Electrónico General AGE y redirección pública del legacy endpoint REC al servicio REG; el launch queda limitado al startUrl exacto del perfil existente, no se amplía trust al origin Defensa y falta E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Defensa → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La Sede de Defensa también publica procedimientos propios con autenticación/certificado; esos contratos quedan fuera de esta implementación. Solo se cubre la delegación externa REG-AGE."

  - inventory_id: "ES-PUB-0064"
    surface_key: "age-ministerio-de-derechos-sociales-consumo-y-agenda-2030"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Derechos Sociales, Consumo y Agenda 2030"
    surface_name: "V Certamen Artístico Amigos de los Animales — vía REG alternativa"
    surface_type: "SEDE"
    origin: "https://www.dsca.gob.es"
    official_site: "https://www.dsca.gob.es/"
    e_sede: "https://dsca.sede.gob.es/"
    entry_url: "https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La convocatoria 2026 del Ministerio ofrece, además del formulario propio, una vía alternativa explícita por Registro Electrónico General dirigida a la Dirección General de Derechos de los Animales."
    protocol_evidence: "La página oficial vigente del V Certamen indica plazo 13-07-2026 a 10-10-2026 y enlaza literalmente la vía alternativa REG a https://reg.redsara.es/es/; el launch URL coincide exactamente con el startUrl del perfil reg-age-redsara, sin inferir un ABI de firma propio de DSCA."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DSCA-REG-2026-08-16", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL oficial; se conserva la página ministerial como entry URL, no se amplía la confianza de firma al origen www.dsca.gob.es y no se realizó E2E físico."
    reviewed_at: "2026-08-16"
    next_gate: "E2E físico seguro DSCA → REG-AGE sin completar ni presentar una candidatura administrativa real; mantener release fail-closed hasta entonces."
    notes: "La implementación cubre únicamente la vía alternativa REG publicada por el Ministerio para esta convocatoria, no el formulario propio dsca.sede.gob.es/procedimiento/portada?idProc=155723."

  - inventory_id: "ES-PUB-0065"
    surface_key: "age-ministerio-de-economia-comercio-y-empresa"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Economía, Comercio y Empresa"
    surface_name: "Instancia Genérica — Ministerio de Economía, Comercio y Empresa"
    surface_type: "SEDE"
    origin: "https://serviciosede.mineco.gob.es"
    official_site: "https://sede.mineco.gob.es/"
    e_sede: "https://sede.mineco.gob.es/"
    entry_url: "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG"
    procedure_page: "https://sede.mineco.gob.es/es/servicios-comunes"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "MiniApplet / AutoFirma"
    protocol_family: "MINIAPPLET_LOCAL_PADES"
    signature_format: "PAdES"
    signature_algorithm: "SHA512withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Rama local Firmar y Enviar: firma PAdES del PDF generado por la Instancia Genérica mediante MiniApplet/AutoFirma; Cl@veFirma queda fuera del contrato implementado."
    protocol_evidence: "Controlled authenticated observation 2026-08-17: /FB/solicitud/firma.aspx invokes MiniApplet.sign(unsignedPdfBase64, SHA512withRSA, PAdES, filters=signingCert:;nonexpired: + expPolicy=FirmaAGE + signatureSubFilter=ETSI.CAdES.detached, showResultCallback, showErrorCallback). Current official AutoFirma source maps FirmaAGE PAdES policy 1.9 to OID 2.16.724.1.3.1.1.2.1.9, SHA-1 policy hash G7roucf600+f03r/o0bAOQ6WAs0= and the official policy qualifier URL."
    client_tls_auth: "SI"
    evidence_ids: ["D11"]
    reason: "Contrato exacto de la rama local Firmar y Enviar implementado en QA y pendiente de E2E físico; la autenticación eIdentifier usa certificado TLS por separado y Cl@veFirma no se infiere ni se implementa."
    reviewed_at: "2026-08-17"
    next_gate: "E2E controlado de la rama local sin presentación administrativa; validar callback signedPdf y abortar antes del postback final de registro."
    notes: "La autenticación con certificado Cl@ve/eIdentifier y la firma del escrito son contratos distintos. El perfil implementa solo SIGN local PAdES; los orígenes Cl@ve quedan limitados a navegación de confianza y no se declara una operación CLIENT_TLS_AUTH en el perfil."
  - inventory_id: "ES-PUB-0066"
    surface_key: "age-ministerio-de-educacion-formacion-profesional-y-deportes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Educación, Formación Profesional y Deportes"
    surface_name: "Proceso selectivo Liceo Cervantes Roma 2026 — vía REG"
    surface_type: "SEDE"
    origin: "https://www.educacionfpydeportes.gob.es"
    official_site: "https://www.educacionfpydeportes.gob.es/"
    e_sede: "https://sede.educacion.gob.es/portada.html"
    entry_url: "https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La ficha oficial del proceso selectivo 2026 documenta que la presentación telemática se realiza a través del Registro Electrónico General de la AGE; el plazo de solicitudes de esa convocatoria ya está finalizado."
    protocol_evidence: "La página oficial del Ministerio publica literalmente https://reg.redsara.es/es/ como vía de presentación de la solicitud. Ese launch URL coincide exactamente con el startUrl del perfil reg-age-redsara; no se infiere un ABI de firma propio de Educación ni se afirma que la convocatoria siga abierta."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "EDU-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL publicado; se conserva la ficha ministerial como entry URL, no se amplía la confianza de firma al origen www.educacionfpydeportes.gob.es y no se realizó E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro Educación → REG-AGE sobre una futura convocatoria abierta que publique el mismo destino exacto, sin completar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "La convocatoria concreta usada como evidencia cerró el 13-04-2026 y publicó resolución definitiva el 17-07-2026; esta implementación acredita el contrato de delegación REG, no disponibilidad temporal del proceso."

  - inventory_id: "ES-PUB-0067"
    surface_key: "age-ministerio-de-igualdad"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Igualdad"
    surface_name: "Sede electrónica / acceso al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://igualdad.sede.gob.es"
    official_site: "https://igualdad.sede.gob.es/"
    e_sede: "https://igualdad.sede.gob.es/"
    entry_url: "https://igualdad.sede.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://igualdad.sede.gob.es/servicio?id=Registro-Electrónico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio de Igualdad publica un servicio específico de Registro Electrónico General y delega el acceso al REG-AGE."
    protocol_evidence: "La página pública oficial /servicio?id=Registro-Electrónico-General identifica expresamente el Registro Electrónico General de la AGE (REG-AGE), muestra «Acceso al Registro Electrónico General» y enlaza https://reg.redsara.es/. Workspace-47 reutiliza únicamente el startUrl canónico ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara; no atribuye a igualdad.sede.gob.es un ABI de firma ni amplía sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "IGUALDAD-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede de Igualdad publica una delegación explícita al REG-AGE y el catálogo lanza solo el startUrl canónico exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Igualdad → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La evidencia usada es el servicio público REG-AGE de la propia Sede; no se deriva compatibilidad del dominio *.sede.gob.es ni del AC2/AutoFirma compartido."

  - inventory_id: "ES-PUB-0068"
    surface_key: "age-ministerio-de-inclusion-seguridad-social-y-migraciones"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Inclusión, Seguridad Social y Migraciones"
    surface_name: "Sede electrónica / acceso al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://sede.inclusion.gob.es"
    official_site: "https://sede.inclusion.gob.es/"
    e_sede: "https://sede.inclusion.gob.es/"
    entry_url: "https://sede.inclusion.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.inclusion.gob.es/registroelectronico"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio de Inclusión publica un acceso al Registro Electrónico de la Administración General del Estado; su enlace legacy REC migra mediante redirección HTTP al actual origin REG-AGE."
    protocol_evidence: "La página first-party https://sede.inclusion.gob.es/registroelectronico indica expresamente que permite acceder al Registro Electrónico de la Administración General del Estado y publica https://rec.redsara.es/registro/action/are/acceso.do; ese URL responde 301 hacia https://reg.redsara.es/. El PAG vigente identifica ese servicio como Registro Electrónico General (REG-AGE) y publica «Acceso al REG» sobre el mismo origin. Workspace-47 lanza únicamente el startUrl canónico ya cubierto https://reg.redsara.es/es/ del perfil reg-age-redsara; no atribuye a sede.inclusion.gob.es un ABI de firma ni amplía sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "INCLUSION-REG-2026-08-17", "PAG-REG-AGE-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede de Inclusión delega explícitamente al registro AGE y la ruta legacy publicada migra al origin REG-AGE actual; el catálogo usa solo el startUrl canónico exacto del perfil existente. Falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Inclusión → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "No se deriva compatibilidad del dominio sede.inclusion.gob.es ni de una mención genérica a firma; la promoción se limita a la delegación pública REG-AGE y conserva el origin institucional fuera del signing trust."

  - inventory_id: "ES-PUB-0069"
    surface_key: "age-ministerio-de-industria-y-turismo"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Industria y Turismo"
    surface_name: "Sede electrónica / acceso al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://sede.minetur.gob.es"
    official_site: "https://sede.minetur.gob.es/"
    e_sede: "https://sede.minetur.gob.es/"
    entry_url: "https://sede.minetur.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.minetur.gob.es/es-es/procedimientoselectronicos/Paginas/consulta_registro.aspx"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio de Industria y Turismo publica una vía específica al Registro Electrónico General de la Administración General del Estado para solicitudes, escritos y comunicaciones sin aplicación específica."
    protocol_evidence: "La página first-party https://sede.minetur.gob.es/es-es/procedimientoselectronicos/Paginas/consulta_registro.aspx contiene el apartado «Acceso al Registro Electrónico General de la Administración General del Estado» y publica https://rec.redsara.es/registro/action/are/acceso.do; ese URL responde 301 hacia https://reg.redsara.es/. El PAG vigente identifica el servicio como Registro Electrónico General (REG-AGE), mientras que el startUrl canónico existente https://reg.redsara.es/es/ responde como «REG - Registro Electrónico General». Workspace-47 reutiliza únicamente ese startUrl del perfil reg-age-redsara; no atribuye a sede.minetur.gob.es ni sede.serviciosmin.gob.es un ABI de firma ni amplía sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "INDUSTRIA-REG-2026-08-17", "PAG-REG-AGE-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede de Industria delega explícitamente en el Registro Electrónico General de la AGE y la ruta legacy publicada migra al origin REG actual; el catálogo lanza solo el startUrl canónico exacto del perfil existente. Falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Industria → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La entrada institucional https://sede.minetur.gob.es/ redirige públicamente a la Sede vigente en sede.serviciosmin.gob.es; esto se conserva como metadata de navegación y no amplía signing trust. La promoción no se deriva de AutoFirma genérica ni del dominio."

  - inventory_id: "ES-PUB-0070"
    surface_key: "age-ministerio-de-juventud-e-infancia"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Juventud e Infancia"
    surface_name: "Formulario genérico — vía Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://juventudeinfancia.sede.gob.es"
    official_site: "https://juventudeinfancia.sede.gob.es/"
    e_sede: "https://juventudeinfancia.sede.gob.es/"
    entry_url: "https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede publica un formulario genérico para solicitudes, escritos y comunicaciones sin procedimiento normalizado y remite expresamente al Registro Electrónico General (REG-AGE)."
    protocol_evidence: "La página pública oficial «Registro Electrónico General» describe REG-AGE y publica el enlace https://reg.redsara.es/. En un contexto público sin autenticación con locale es-ES, ese launch redirige exactamente a https://reg.redsara.es/es/, que coincide con el startUrl del perfil existente reg-age-redsara. Solo se reutiliza ese launch/profile; no se atribuye a juventudeinfancia.sede.gob.es ningún ABI criptográfico ni origen de confianza de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "JUVENTUD-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita a REG-AGE y resolución exacta del launch español al startUrl existente; falta E2E físico y no se amplía la confianza criptográfica al origen institucional."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Juventud e Infancia → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio de Juventud e Infancia."

  - inventory_id: "ES-PUB-0071"
    surface_key: "age-ministerio-de-la-presidencia-justicia-y-relaciones-con-las-cortes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de la Presidencia, Justicia y Relaciones con las Cortes"
    surface_name: "Registro Electrónico General — vía REG-AGE"
    surface_type: "SEDE"
    origin: "https://mpr.sede.gob.es"
    official_site: "https://mpr.sede.gob.es/"
    e_sede: "https://mpr.sede.gob.es/"
    entry_url: "https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede oficial publica el Registro Electrónico General como vía para presentar solicitudes, escritos y comunicaciones que no dispongan de procedimiento electrónico o formulario normalizado específico."
    protocol_evidence: "La página pública oficial «Registro Electrónico General» identifica expresamente el Registro Electrónico General de la Administración General del Estado (REG-AGE) y publica el enlace https://reg.redsara.es/. En un contexto público sin autenticación con locale es-ES, ese launch resuelve exactamente a https://reg.redsara.es/es/, que coincide con el startUrl del perfil existente reg-age-redsara. Solo se reutiliza ese launch/profile; no se atribuye a mpr.sede.gob.es ningún ABI criptográfico ni origen de confianza de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MPR-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita a REG-AGE y resolución exacta del launch español al startUrl existente; falta E2E físico y no se amplía la confianza criptográfica al origen institucional."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición MPR → REG-AGE sin completar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "Ministerio(s) enumerador(es): Ministerio de la Presidencia, Justicia y Relaciones con las Cortes."

  - inventory_id: "ES-PUB-0072"
    surface_key: "age-ministerio-de-politica-territorial-y-memoria-democratica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Política Territorial y Memoria Democrática"
    surface_name: "Registro Electrónico General — acceso directo REG-AGE"
    surface_type: "SEDE"
    origin: "https://mptmd.sede.gob.es"
    official_site: "https://mptmd.sede.gob.es/"
    e_sede: "https://mptmd.sede.gob.es/"
    entry_url: "https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede oficial publica el Registro Electrónico General de la AGE para solicitudes, escritos y comunicaciones sin procedimiento específico y ofrece un acceso directo al REG; la propia página indica DNIe/certificado digital y firma de la solicitud al enviarla."
    protocol_evidence: "La página pública oficial «Registro Electrónico General» identifica expresamente el Registro Electrónico General de la AGE y publica «ACCESO DIRECTO AL REGISTRO» hacia https://reg.redsara.es/. En un contexto público sin autenticación con locale es-ES, la cadena observada fue https://reg.redsara.es/ (302) → https://reg.redsara.es/es/ (200), que coincide exactamente con el startUrl del perfil existente reg-age-redsara. Solo se reutiliza ese launch/profile; no se atribuye a mptmd.sede.gob.es ningún ABI criptográfico, algoritmo, endpoint ni origen de confianza de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MPTMD-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita y cadena de redirect exacta al startUrl existente; falta E2E físico y no se amplía la confianza criptográfica al origen institucional."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición MPTMD → REG-AGE sin autenticarse, firmar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "La evidencia institucional describe requisitos de certificado/firma del servicio, pero Workspace-47 no infiere formato, algoritmo, endpoint ni capacidades criptográficas propias de mptmd.sede.gob.es."

  - inventory_id: "ES-PUB-0073"
    surface_key: "age-ministerio-de-sanidad"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Sanidad"
    surface_name: "Sede electrónica / acceso con certificado al registro SIGEM"
    surface_type: "SEDE"
    origin: "https://sede.mscbs.gob.es"
    official_site: "https://sede.mscbs.gob.es/"
    e_sede: "https://sede.mscbs.gob.es/"
    entry_url: "https://sede.mscbs.gob.es/"
    procedure_page: "https://sede.mscbs.gob.es/registroElectronico/formularios.htm"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&ENTIDAD_ID=000&LANG=es&COUNTRY=ES"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado mediante TLS cliente al trámite público TRAM_TARDESCONPLAN; no incluye firma documental ni presentación administrativa."
    protocol_evidence: "El índice SIGEM público marca TRAM_TARDESCONPLAN activo; form_gen.js construye el endpoint exacto de certificado. Una petición GET sin certificado provoca HelloRequest y CertificateRequest por renegociación TLS 1.2 con lista de emisores no vacía, y termina en HTTP 403."
    client_tls_auth: "SI"
    evidence_ids: ["D11", "P20", "P21", "P22", "P23"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA para source, host, ruta y query exactos. El flujo depende de renegociación TLS 1.2 y queda E2E pendiente; no se afirma firma, envío ni aceptación administrativa."
    reviewed_at: "2026-08-14"
    next_gate: "Confirmar onReceivedClientCertRequest y acceso autenticado en WebView físico sin firmar ni presentar trámites antes de cualquier promoción release."

  - inventory_id: "ES-PUB-0074"
    surface_key: "age-ministerio-de-trabajo-y-economia-social"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Trabajo y Economía Social"
    surface_name: "Sede electrónica — acceso con certificado"
    surface_type: "SEDE"
    origin: "https://sede.mites.gob.es"
    official_site: "https://sede.mites.gob.es/"
    e_sede: "https://sede.mites.gob.es/"
    entry_url: "https://sede.mites.gob.es/"
    procedure_page: "https://sede.mites.gob.es/inicio/detalleProcedimiento/38"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_LOCAL_CADES_IMPLICIT"
    signature_format: "CAdES / IMPLICIT"
    signature_algorithm: "SHA512withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Sede MITES mediante firma local CAdES de un challenge aleatorio de 10 letras ASCII minúsculas."
    protocol_evidence: "La API pública del procedimiento 38 acredita Quejas y Sugerencias activo y exige identificación y firma digital. El bundle público /auth genera exactamente 10 letras minúsculas, carga AutoFirma y llama AutoScript.sign(challenge, SHA512withRSA, CAdES, mode=implicit + filters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:). El bridge queda limitado a /auth, al origin MITES y a esa tupla exacta; no se implementa ni se atribuye el flujo PAdES posterior de presentación."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MITES-CERT-2026-08-17"]
    reason: "Contrato público de acceso con certificado implementado en QA con validación fail-closed de página, origin, challenge, algoritmo, formato y propiedades; falta aceptación E2E física y no se realizaron login, firma real ni presentación administrativa."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro limitado al login con certificado en /auth; no continuar a formularios, firma PAdES ni presentación administrativa."
    notes: "El script AutoFirma se sirve desde expinterweb.mites.gob.es, pero ese origin no recibe confianza de navegación ni firma; el perfil mantiene como único initiator origin https://sede.mites.gob.es."

  - inventory_id: "ES-PUB-0075"
    surface_key: "age-ministerio-de-transportes-y-movilidad-sostenible"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Transportes y Movilidad Sostenible"
    surface_name: "Sede electrónica — Quejas y Sugerencias"
    surface_type: "SEDE"
    origin: "https://sede.transportes.gob.es"
    official_site: "https://sede.transportes.gob.es/"
    e_sede: "https://sede.transportes.gob.es/"
    entry_url: "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002"
    procedure_page: "https://sede.transportes.gob.es/proc-servicios-comunes/presentacion-quejas-sugerencias-ambito-ministerio-transportes-movilidad-sostenible"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet / AutoFirma"
    protocol_family: "MINIAPPLET_LOCAL_XADES_ENVELOPED"
    signature_format: "XAdES / ENVELOPED"
    signature_algorithm: "SHA1withRSA (LEGACY_SHA1 portal-specific)"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Autenticación con certificado para Quejas y Sugerencias mediante firma local XAdES Enveloped del challenge XML público de la Sede."
    protocol_evidence: "La ficha oficial vigente enlaza exactamente /MFOM.genericprocedure.web/?id=7002. Ese launch redirige, manteniendo sesión pública, a /MFOM.genericprocedure.web/Autenticacion.aspx. La página pública construye SignParams con signatureFormat=XAdES, FILTER_AUTHENTICATION e idToSign=tag1; CIM 3.0.1 transforma los defaults en SHA1withRSA, XAdES Enveloped, includeOnlySigningCertificate=true, nodeToSign=tag1, applySystemDate=false, filtro digitalSignature/nonexpired y sticky=true, y llama MiniApplet.sign. Tres GET públicos independientes confirmaron solo la forma estable de un challenge XML de 113 bytes con tag1_timestamp; no se persistieron valores efímeros."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "TRANSPORTES-QYS-2026-08-17"]
    reason: "Contrato de autenticación por firma implementado en QA con comprobación fail-closed de origin, página, challenge, algoritmo, formato y propiedades. Falta aceptación E2E física; no se realizó autenticación, POST, firma real ni presentación administrativa."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro limitado a la autenticación inicial del procedimiento 7002; no completar ni presentar el trámite."
    notes: "El URL histórico https://sede.mitma.gob.es/sede_electronica/lang_castellano/ redirige actualmente a https://sede.transportes.gob.es/. Los Storage/Retrieve de fire.transportes.gob.es aparecen en CIM_Constants.js pero no se activan como endpoints/trust del perfil ni se llamaron durante la investigación."

  - inventory_id: "ES-PUB-0076"
    surface_key: "age-ministerio-de-vivienda-y-agenda-urbana"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Vivienda y Agenda Urbana"
    surface_name: "Sede electrónica — Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://mivau.sede.gob.es"
    official_site: "https://mivau.sede.gob.es/"
    e_sede: "https://mivau.sede.gob.es/"
    entry_url: "https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio de Vivienda y Agenda Urbana ofrece públicamente el Registro Electrónico General como vía de registro y delega esa actuación al servicio REG-AGE."
    protocol_evidence: "La página oficial https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General contiene un enlace público rotulado «Registro Electrónico» con href https://reg.redsara.es/. En una sesión Chromium 149 nueva y off-the-record, y de forma independiente con HTTP Accept-Language español, ese root público negocia idioma mediante 302 a https://reg.redsara.es/es/, exactamente el startUrl del perfil existente reg-age-redsara. Workspace-47 reutiliza únicamente ese startUrl canónico; no se atribuye a mivau.sede.gob.es un ABI de firma propio ni se amplían sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MIVAU-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita desde la Sede MIVAU y redirect acotado del root REG a su startUrl español canónico; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición MIVAU → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "El browser pass se realizó en contexto Chromium off-the-record sin credenciales, certificado, firma, POST ni submission. El enlace institucional publica el root locale-negotiated https://reg.redsara.es/; para el alias se usa exclusivamente https://reg.redsara.es/es/, ya cubierto por el perfil reg-age-redsara."

  - inventory_id: "ES-PUB-0077"
    surface_key: "age-ministerio-del-interior"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio del Interior"
    surface_name: "Formulario de propósito general — vía REG-AGE"
    surface_type: "SEDE"
    origin: "https://sede.interior.gob.es"
    official_site: "https://sede.interior.gob.es/portal/sede"
    e_sede: "https://sede.interior.gob.es/portal/sede"
    entry_url: "https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede actual del Ministerio del Interior publica el «Formulario de propósito general» para escritos sin formulario específico y ofrece un botón público de acceso que delega en el registro estatal."
    protocol_evidence: "La página pública first-party https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL publica el botón «Acceso» con destino exacto https://rec.redsara.es/registro/action/are/acceso.do. En la revisión actual esa URL responde 301 a https://reg.redsara.es/, que responde 302 a https://reg.redsara.es/es/ y finaliza HTTP 200; el destino final coincide exactamente con el startUrl del perfil existente reg-age-redsara. Solo se reutiliza ese launch/profile; no se atribuye a sede.interior.gob.es ningún ABI criptográfico, algoritmo, formato, endpoint ni origen de confianza de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "INTERIOR-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación first-party explícita y cadena de redirect acotada que termina exactamente en el startUrl existente; falta E2E físico y no se amplía la confianza criptográfica al origen institucional."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Interior → REC legacy → REG-AGE sin autenticarse, seleccionar certificado, firmar ni presentar un escrito real; mantener release fail-closed hasta entonces."
    notes: "El enlace histórico https://sede.mir.gob.es/opencms/export/sites/default/es/inicio/ redirige actualmente a la nueva Sede https://sede.interior.gob.es/portal/sede. La página general de sistemas de firma menciona certificados X.509 y @firma, pero esos datos no se usan para inferir el contrato de firma de este alias."

  - inventory_id: "ES-PUB-0078"
    surface_key: "age-ministerio-para-la-transformacion-digital-y-de-la-funcion-publica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio para la Transformación Digital y de la Función Pública"
    surface_name: "Sede electrónica — fallback al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://digital.sede.gob.es"
    official_site: "https://digital.sede.gob.es/"
    e_sede: "https://digital.sede.gob.es/"
    entry_url: "https://digital.sede.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Ministerio indica que, cuando un trámite de su competencia no dispone de procedimiento electrónico específico, la solicitud, escrito o comunicación puede presentarse a través del Registro Electrónico General (REG)."
    protocol_evidence: "La página inicial oficial digital.sede.gob.es publica el enlace exacto https://reg.redsara.es/ bajo el texto Registro Electrónico General (REG); un contexto Chromium público nuevo con Accept-Language español observó el redirect documental HTTP 302 desde ese root a https://reg.redsara.es/es/, exactamente el startUrl de reg-age-redsara."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DIGITAL-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita y redirect acotado al startUrl canónico; la sede ministerial se conserva como entry URL, no se añade digital.sede.gob.es a la confianza criptográfica REG-AGE y no se realizó E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Sede Digital → REG-AGE para un caso sin procedimiento específico sin realizar una presentación administrativa real; mantener release fail-closed hasta entonces."
    notes: "La evidencia de AutoFirma encontrada en JavaScript genérico ACCEDA2 de la sede no se usa para inferir algoritmo, formato, endpoint ni ABI propio del Ministerio; esos campos permanecen NO_VERIFICADO."

  - inventory_id: "ES-PUB-0079"
    surface_key: "age-ministerio-para-la-transicion-ecologica-y-el-reto-demografico"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio para la Transición Ecológica y el Reto Demográfico"
    surface_name: "Información pública DPMT Murcia — vía REG"
    surface_type: "SEDE"
    origin: "https://www.miteco.gob.es"
    official_site: "https://www.miteco.gob.es/"
    e_sede: "https://sede.miteco.gob.es/portal/site/seMITECO"
    entry_url: "https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La página oficial de información pública de Costas permite remitir documentación y observaciones y publica, para quien disponga de certificado o DNIe, la vía del Registro Electrónico General de la AGE."
    protocol_evidence: "La página oficial de MITECO publica literalmente https://reg.redsara.es/es/ como destino del Registro General Electrónico de la AGE para usuarios con certificado o DNIe; el launch URL coincide exactamente con el startUrl de reg-age-redsara. El plazo concreto usado como evidencia finalizó el 27-07-2026, por lo que se acredita la delegación técnica y no disponibilidad actual del trámite."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "MITECO-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por igualdad exacta del launch URL publicado; se conserva la página ministerial como entry URL, no se amplía la confianza de firma al origen www.miteco.gob.es y no se realizó E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro MITECO → REG-AGE sobre una futura fase abierta que publique el mismo destino exacto, sin completar ni presentar documentación administrativa real; mantener release fail-closed hasta entonces."
    notes: "La información pública concreta usada como evidencia admitió documentación del 30-06-2026 al 27-07-2026; esta implementación registra únicamente la delegación exacta a REG-AGE y no un ABI criptográfico propio de MITECO."

  - inventory_id: "ES-PUB-0080"
    surface_key: "age-museo-nacional-centro-de-arte-reina-sofia"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Museo Nacional Centro de Arte Reina Sofía"
    surface_name: "Sede electrónica / acceso al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://museoreinasofia.sede.gob.es"
    official_site: "https://museoreinasofia.sede.gob.es/"
    e_sede: "https://museoreinasofia.sede.gob.es/"
    entry_url: "https://museoreinasofia.sede.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://museoreinasofia.sede.gob.es/servicio?id=Registro-Electrónico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Museo Reina Sofía publica un servicio específico de acceso al Registro Electrónico General de la Administración General del Estado (REG-AGE) para solicitudes, escritos y comunicaciones sin procedimiento electrónico o formulario normalizado específico."
    protocol_evidence: "La página first-party https://museoreinasofia.sede.gob.es/servicio?id=Registro-Electrónico-General identifica expresamente el Registro Electrónico General de la AGE como REG-AGE y publica «Acceso al Registro Electrónico» hacia https://reg.redsara.es/. En Chromium público no autenticado el enlace abrió el REG actual; la raíz REG respondió 302 por negociación de idioma y con Accept-Language español redirigió exactamente a https://reg.redsara.es/es/, startUrl canónico ya cubierto por reg-age-redsara. Se reutiliza únicamente ese startUrl; no se atribuye a museoreinasofia.sede.gob.es un ABI de firma ni se amplía su trust."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "REINA-SOFIA-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede del Museo delega explícitamente en REG-AGE y la raíz oficial publicada negocia idioma hasta el startUrl español exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Reina Sofía → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "Deep public research incluyó Chromium/Playwright, red pública, runtime globals/handlers y mapa completo de scripts AC2 cargados. Los scripts genéricos contienen rutas AutoFirma de trámites autenticados, pero no se usan para inferir constantes de firma del Museo ni para ampliar trust; no se invocaron login, certificado, firma, carga, pago ni presentación administrativa."

  - inventory_id: "ES-PUB-0081"
    surface_key: "age-mutualidad-general-judicial-mugeju"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Mutualidad General Judicial (MUGEJU)"
    surface_name: "Remisión de documentación / Mutualnet"
    surface_type: "SEDE"
    origin: "https://sedemugeju.gob.es"
    official_site: "https://sedemugeju.gob.es/"
    e_sede: "https://sedemugeju.gob.es/"
    entry_url: "https://sedemugeju.gob.es/remisiondocumentacion"
    procedure_page: "https://sedemugeju.gob.es/remisiondocumentacion"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "MiniApplet AutoScript"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "CAdEStri"
    signature_algorithm: "SHA512withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la remisión de documentación mediante Cl@ve eIdentifier; el runtime protegido observa firma AutoScript/MiniApplet CAdES trifásica, pero el perfil QA implementa únicamente la transición CLIENT_TLS_AUTH."
    protocol_evidence: "Revalidación oficial 2026-08-19: /remisiondocumentacion redirige a /mutualnet3/servlet/AccesoServlet?operation=REM y /mutualnet3/clave/ControladorClaveCiudadanoServlet?operation=REM. Runtime autenticado controlado 2026-08-18: Cl@ve usa https://pasarela.clave.gob.es/Proxy2/ServiceProvider como fuente y https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen como petición TLS cliente; después alcanza /mutualnet3/faces/protected/tramites/tramite.xhtml. La página protegida carga AutoScript/MiniApplet y llama MiniApplet.sign(fileName, SHA512withRSA, CAdEStri, mode=implicit + expPolicy=FirmaAGE + serverUrl=signatureServiceUrl). El valor dinámico signatureServiceUrl no se observó por ausencia de expediente habilitado; por ello SIGN no se habilita ni se adivina endpoint."
    client_tls_auth: "SI"
    evidence_ids: ["D11", "MUGEJU-PUBLIC-2026-08-19", "MUGEJU-AUTH-2026-08-18", "MUGEJU-SIGNING-2026-08-18"]
    reason: "Perfil QA_ONLY limitado al CLIENT_TLS_AUTH exacto MUGEJU → Cl@ve observado. La firma posterior está acreditada solo como metadata runtime, pero permanece fuera de capabilities hasta acotar signatureServiceUrl; E2E físico pendiente."
    reviewed_at: "2026-08-19"
    next_gate: "Validar en QA Android la transición MUGEJU → Cl@ve con certificado autorizado. Si aparece un expediente habilitado, capturar signatureServiceUrl en pre-sign y abortar antes de la firma privada; solo entonces evaluar una capability SIGN separada."
    notes: "No se ejecutó firma criptográfica, registro, presentación, pago ni envío final. No se persisten identificadores personales, cookies, SAML, credenciales ni material de certificado."

  - inventory_id: "ES-PUB-0082"
    surface_key: "age-oficina-espanola-de-patentes-y-marcas"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Oficina Española de Patentes y Marcas"
    surface_name: "Solicitud de propósito general / ProtegeO"
    surface_type: "SEDE"
    origin: "https://sede.oepm.gob.es"
    official_site: "https://sede.oepm.gob.es/"
    e_sede: "https://sede.oepm.gob.es/"
    entry_url: "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM"
    procedure_page: "https://sede.oepm.gob.es/eSede/es/tramites-comunes/solicitud-electronica-de-proposito-general-remitida-a-la-oepm-/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "OEPM_PROTEGEO_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada exclusivamente al inicio público de ProtegeO para la Solicitud electrónica de propósito general remitida a la OEPM; el flujo POST posterior, autenticación y firma quedan fuera del contrato implementado."
    protocol_evidence: "La página first-party actual de la OEPM publica exactamente https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM. Chromium público no autenticado abrió ProtegeO v1.71.1. El runtime pre-POST no expuso AutoScript, MiniApplet, ClienteFirma, AppletFirma ni constantes de algoritmo/formato; el segundo Aceptar intenta POST al mismo /ProtegeOWeb/inicio con tres campos hidden vacíos, transición que se observó y abortó antes de enviarse."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "OEPM-PROTEGEO-2026-08-17"]
    reason: "Perfil nuevo QA_ONLY limitado al launch público exacto de ProtegeO y sin capacidades SIGN, SELECT_CERTIFICATE o CLIENT_TLS_AUTH. La tarjeta current first-party indica acceso con Cl@ve y «No requiere certificado electrónico», mientras el texto desplegable conserva instrucciones legacy de Java Applet/certificado; por esa contradicción no se modelan constantes de firma ni certificado. Falta E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la navegación QA hasta ProtegeO; cualquier ampliación al POST posterior, autenticación o firma requiere evidencia separada y autorización correspondiente."
    notes: "Deep public research agotó eSede, ProtegeO, recursos JS, Chromium/network/runtime y trigger tracing no destructivo. El form.submit del segundo Aceptar fue instrumentado y abortado: no se envió POST, no hubo login, certificado, firma, carga, pago ni presentación administrativa. certificateRules del perfil son metadatos estructurales inertes porque capabilities está vacío."

  - inventory_id: "ES-PUB-0083"
    surface_key: "age-portal-de-la-transparencia"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Portal de la Transparencia"
    surface_name: "Sede electrónica — Derecho de acceso a la información pública"
    surface_type: "SEDE"
    origin: "https://transparencia.sede.gob.es"
    official_site: "https://transparencia.sede.gob.es/"
    e_sede: "https://transparencia.sede.gob.es/"
    entry_url: "https://transparencia.sede.gob.es/procedimiento/portada?idProc=133628&idAmb=101524"
    procedure_page: "https://transparencia.sede.gob.es/procedimiento/ambitos?idProc=133628"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "MiniApplet / AutoScript / AutoFirma"
    protocol_family: "MINIAPPLET_LOCAL_PADES"
    signature_format: "PAdES"
    signature_algorithm: "SHA512withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma PAdES local con certificado en la solicitud de derecho de acceso; el perfil QA queda limitado al contrato AutoScript exacto observado tras autenticación controlada."
    protocol_evidence: "La superficie pública vigente conduce por Cl@ve al formulario protegido. En runtime autenticado autorizado, el paso de firma carga ac2-autofirmaFunctions.js (SHA-256 84da2c1d58d81c090d7e6e227b14b3fb37eec1ae83fd94ded8eada09b2dece96 el 2026-08-18), que ejecuta AutoScript.sign sobre el PDF generado con SHA512withRSA, PAdES y los parámetros exactos filters=nonexpired:true; + headless=true; el callback devuelve signatureB64, certificateB64 y extraData. No se ejecutó ninguna firma ni registro final."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "Contrato first-party de firma completo probado antes de la firma mediante runtime autenticado controlado; implementación QA fail-closed, pendiente de E2E físico seguro."
    reviewed_at: "2026-08-18"
    next_gate: "E2E físico seguro en Android del flujo de firma con certificado, abortando antes de cualquier presentación o registro final."
    notes: "La Sede vigente usa el origin transparencia.sede.gob.es (el origin sede.transparencia.gob.es del registro previo estaba obsoleto). La autenticación controlada por certificado ocurrió en la frontera compartida de Cl@ve y no se promociona como client-TLS del portal. El portal ofrece firma básica no criptográfica o firma con certificado; el perfil implementa exclusivamente la segunda. El bridge exige perfil activo, origin HTTPS exacto, página /procedimiento/firma con idProc=133628, idAmb=101524 e idBorr server-issued, además del tuple SHA512withRSA + PAdES + filters=nonexpired:true;\nheadless=true."

  - inventory_id: "ES-PUB-0084"
    surface_key: "age-portal-funciona"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Portal Funciona"
    surface_name: "Sede Funciona / inicio público"
    surface_type: "SEDE"
    origin: "https://sede.funciona.gob.es"
    official_site: "https://sede.funciona.gob.es/public/servicios"
    e_sede: "https://sede.funciona.gob.es/public/servicios"
    entry_url: "https://sede.funciona.gob.es/es/home"
    procedure_page: "https://sede.funciona.gob.es/es/home"
    certificate_required: "CONDICIONAL"
    signature_required: "NO_VERIFICADO"
    js_client: "OIDC_PKCE"
    protocol_family: "OIDC_PKCE_AUTENTICA_SAML_CLIENT_TLS_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada solo al home público de Funciona. El acceso autenticado observado usa OIDC Authorization Code + PKCE hacia Autentica/SAML y alcanza una frontera TLS que solicita certificado cliente; esa cadena y la firma FNC posterior quedan fuera del contrato implementado."
    protocol_evidence: "Chromium público abrió https://sede.funciona.gob.es/es/home y mostró «Acceder con Autentica». El click público produjo OIDC auth en auth-api.redsara.es con client_id fe66bc25-ec04-41e4-8202-809dbded381a, response_type=code, code_challenge_method=S256 y acr_values=loa:2; después broker Autentica/SAML con appId=5524. La página final de Autentica exige certificado digital o eDNI y un handshake TLS 1.2 independiente con autentica.redsara.es observó CertificateRequest. El runtime público de /es/home no expuso AutoScript, MiniApplet ni AutoFirma globals."
    client_tls_auth: "SI"
    evidence_ids: ["D11", "FUNCIONA-PUBLIC-2026-08-17"]
    reason: "Perfil nuevo VERIFIED_CONTRACT/QA_ONLY con entry_url exacto https://sede.funciona.gob.es/es/home; el URL del directorio /public/servicios se conserva como official_site/e_sede, sin SIGN, SELECT_CERTIFICATE, CLIENT_TLS_AUTH ni AFIRMA_URI capabilities. La cadena auth real es multi-hop Funciona → OIDC/Keycloak → Autentica/SAML → client-certificate y no se amplía por analogía; auth-api.redsara.es y autentica.redsara.es permanecen fuera de navigation trust. FNC signing visible en bundle protegido tampoco se modela porque no se acreditaron algoritmo, formato, packaging ni callback actuales. Falta E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la navegación QA al home público. Cualquier soporte de login Autentica/client TLS o firma FNC requiere contrato separado que delimite la cadena multi-hop y sus callbacks sin usar datos reales."
    notes: "deep_public_research=PASS en BROWSER_PUBLIC_RUNTIME: SPA/loaded chunks, OIDC metadata, red de redirects, runtime globals, ruta CSV pública y TLS CertificateRequest agotados. No se introdujeron credenciales, no se proporcionó certificado, no se llamó token/userinfo/secure backend/FNC POST, no hubo firma, carga, pago ni presentación administrativa. certificateRules del perfil son metadatos estructurales inertes porque capabilities está vacío."

  - inventory_id: "ES-PUB-0085"
    surface_key: "age-puertos-del-estado"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Puertos del Estado"
    surface_name: "Registro Electrónico General — acceso directo REG-AGE"
    surface_type: "SEDE"
    origin: "https://puertos.sede.gob.es"
    official_site: "https://puertos.sede.gob.es/"
    e_sede: "https://puertos.sede.gob.es/"
    entry_url: "https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede de Puertos del Estado ofrece públicamente su servicio Registro Electrónico General y delega esa actuación al REG-AGE."
    protocol_evidence: "La página oficial de Puertos del Estado identifica expresamente el Registro Electrónico General de la AGE (REG-AGE) y publica «Acceso al Registro Electrónico» con href https://reg.redsara.es/; una sesión Chromium pública actual confirmó la redirección GET exacta a https://reg.redsara.es/es/. Workspace-47 reutiliza únicamente el startUrl canónico del perfil reg-age-redsara, sin atribuir a puertos.sede.gob.es un ABI de firma propio ni ampliar sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "PUERTOS-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la Sede Puertos del Estado delega públicamente en REG-AGE y se lanza solo el startUrl canónico exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Puertos del Estado → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "El antiguo enlace del directorio https://sede.puertos.gob.es/Paginas/Contenido.aspx redirige actualmente a https://puertos.sede.gob.es/. Ministerio(s) enumerador(es): Ministerio de Transportes y Movilidad Sostenible."

  - inventory_id: "ES-PUB-0086"
    surface_key: "age-red-es"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Red.es"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.red.gob.es"
    official_site: "https://sede.red.gob.es/"
    e_sede: "https://sede.red.gob.es/"
    entry_url: "https://sede.red.gob.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "DISCOVERED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11"]
    reason: "El directorio oficial acredita institución y enlace, pero no procedimiento, certificado, firma, disponibilidad ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar landing pública, procedimiento y contrato técnico exactos."
    notes: "Ministerio(s) enumerador(es): Ministerio para la Transformación Digital y de la Función Pública."

  - inventory_id: "ES-PUB-0087"
    surface_key: "age-secretaria-de-estado-de-comercio"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Secretaría de Estado de Comercio"
    surface_name: "Excepciones al comercio internacional de servicios — vía REG"
    surface_type: "SEDE"
    origin: "https://sede.mineco.gob.es"
    official_site: "https://comercio.gob.es/"
    e_sede: "https://sede.mineco.gob.es/"
    entry_url: "https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "El catálogo público de Comercio mantiene el procedimiento SIA 3057517 y, mientras se habilita su procedimiento administrativo específico, indica que la solicitud puede presentarse por el Registro Electrónico General de la AGE."
    protocol_evidence: "El portal oficial comercio.gob.es enlaza la relación vigente de procedimientos de Comercio en sede.mineco.gob.es. El detalle público SIA 3057517, cargado desde la lista pública SedeProcedures, publica literalmente https://reg.redsara.es como enlace del Registro Electrónico General; un contexto Chromium X11 público nuevo observó el mismo aviso y un segundo contexto nuevo resolvió el root REG mediante HTTP 302 a https://reg.redsara.es/es/, exactamente el startUrl de reg-age-redsara."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "COMERCIO-SURFACE-2026-08-17", "COMERCIO-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública explícita y destino exacto; se conserva el procedimiento público de Comercio como entry URL, no se amplía la confianza criptográfica REG-AGE a sede.mineco.gob.es y no se realizó E2E físico."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición del procedimiento SIA 3057517 hacia REG-AGE sin completar ni presentar una solicitud administrativa real; mantener release fail-closed hasta entonces."
    notes: "La antigua URL D11 https://sede.comercio.gob.es/ redirige actualmente a la Sede de Industria y Turismo; por ello la superficie se reancla en la relación de procedimientos de Comercio que el portal oficial comercio.gob.es publica en sede.mineco.gob.es. No se infieren certificado, firma, formato, algoritmo ni endpoint propios de Comercio."

  - inventory_id: "ES-PUB-0088"
    surface_key: "age-sede-electronica-central-del-ministerio"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Hacienda"
    surface_name: "Sede Electrónica Central del Ministerio de Hacienda"
    surface_type: "SEDE"
    origin: "https://sede.hacienda.gob.es"
    official_site: "https://sede.hacienda.gob.es/"
    e_sede: "https://sede.hacienda.gob.es/"
    entry_url: "https://sede.hacienda.gob.es/"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://sede.hacienda.gob.es/es-es/paginas/informacion"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede Electrónica Central del Ministerio de Hacienda declara que el Ministerio utiliza el Registro Electrónico General de la AGE para solicitudes, escritos y comunicaciones de su ámbito; Workspace-47 representa esa vía como alias acotado al perfil REG-AGE existente."
    protocol_evidence: "La página first-party de Información de la Sede Hacienda declara literalmente que el Ministerio utiliza el REG-AGE y enlaza la página oficial del servicio en la Sede PAG. La página PAG identifica el servicio como Registro Electrónico General de la AGE y publica «Acceso al Registro Electrónico General» hacia https://reg.redsara.es/. Chromium confirmó esa cadena pública y que el startUrl canónico español existente https://reg.redsara.es/es/ responde como «REG - Registro Electrónico General». El alias usa únicamente ese startUrl canónico; no se atribuye a sede.hacienda.gob.es ningún ABI de firma ni se amplían orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "HACIENDA-REG-2026-08-17", "HACIENDA-PAG-REG-AGE-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara basado en una delegación first-party explícita del Ministerio de Hacienda al REG-AGE y en el launch oficial de la Sede PAG. No se infieren certificados, algoritmo, formato, callback, endpoint ni client-TLS específicos de Hacienda; falta validación E2E física de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Sede Hacienda → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La Sede central también agrega procedimientos específicos de organismos con contratos propios; este alias cubre únicamente la vía REG-AGE declarada por el propio Ministerio y no hereda ni amplía esos contratos."

  - inventory_id: "ES-PUB-0089"
    surface_key: "age-sede-electronica-de-la-s-e-de-digitalizacion-e-inteligencia-artificial-y-s-e-de-telecomunica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Sede Electrónica de la S.E. de Digitalización e Inteligencia Artificial y S.E. de Telecomunicaciones e Infraestructuras Digitales del Ministerio de Transformación Digital"
    surface_name: "Sede SEDIA/SETID migrada — fallback al Registro Electrónico General"
    surface_type: "SEDE"
    origin: "https://sedediatid.digital.gob.es"
    official_site: "https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"
    e_sede: "https://digital.sede.gob.es/"
    entry_url: "https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://digital.sede.gob.es/servicio?id=Procedimientos-electr%C3%B3nicos-disponibles-en-la-Sede-Electr%C3%B3nica"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La antigua entrada SEDIA/SETID migra al portal actual del Ministerio; su sede electrónica asociada publica REG como vía para solicitudes, escritos o comunicaciones de competencia ministerial cuando no existe un procedimiento electrónico específico habilitado."
    protocol_evidence: "La URL histórica del directorio redirige al portal actual digital.gob.es, que enlaza la sede electrónica asociada digital.sede.gob.es. La página oficial de procedimientos electrónicos de esa sede indica expresamente que, si un trámite de competencia del Ministerio no dispone de procedimiento electrónico habilitado, puede presentarse por el Registro Electrónico General (REG) y enlaza https://reg.redsara.es/. Un navegador público fresco resolvió ese destino 302 a https://reg.redsara.es/es/, exactamente el startUrl del perfil reg-age-redsara. No se atribuye a digital.gob.es, digital.sede.gob.es ni sedediatid.digital.gob.es un ABI de firma propio ni se amplían orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "DIGITAL-SEDE-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación oficial explícita y resolución pública exacta al startUrl canónico; se conserva la entrada histórica SEDIA/SETID como entry URL, no se copian constantes criptográficas de REG-AGE y falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición desde la superficie SEDIA/SETID migrada hasta REG-AGE sin completar ni presentar una actuación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "La URL histórica sedediatid.digital.gob.es responde 301 hacia el portal actual del Ministerio. La sede asociada actual identifica por separado procedimientos de la Secretaría de Estado de Digitalización e Inteligencia Artificial y de la Secretaría de Estado de Telecomunicaciones e Infraestructuras Digitales."

  - inventory_id: "ES-PUB-0090"
    surface_key: "age-sede-electronica-de-los-tribunales-economico-administrativos-tea"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Sede electrónica de los Tribunales Económico-Administrativos (TEA)"
    surface_name: "TEA / Alegaciones con certificado"
    surface_type: "SEDE"
    origin: "https://sede.tea.hacienda.gob.es"
    official_site: "https://sede.tea.hacienda.gob.es/"
    e_sede: "https://sede.tea.hacienda.gob.es/"
    entry_url: "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html"
    procedure_page: "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado TLS cliente a la entrada pública de Alegaciones TEA; no incluye firma documental ni presentación administrativa."
    protocol_evidence: "La página pública de Alegaciones enlaza exactamente a SedeTRAM?tram=0 en www1.tea.hacienda.gob.es. El handshake TLS 1.2 de ese host envía CertificateRequest, una lista no vacía de autoridades certificadoras y tipos RSA/ECDSA; sin certificado el flujo termina en la página oficial de error 403."
    client_tls_auth: "SI"
    evidence_ids: ["D11", "P24", "P25"]
    reason: "CLIENT_TLS_AUTH TLS 1.2 implementado solo en QA para la fuente, host, ruta y query tram=0 exactos. E2E físico pendiente; no se afirma firma electrónica, envío de alegaciones ni aceptación administrativa."
    reviewed_at: "2026-08-14"
    next_gate: "Confirmar onReceivedClientCertRequest y acceso autenticado en WebView físico sin firmar ni presentar alegaciones antes de cualquier promoción release."
    notes: "Ministerio(s) enumerador(es): Ministerio de Hacienda. El perfil no cubre Otras solicitudes (tram=2) ni otros trámites TEA."

  - inventory_id: "ES-PUB-0091"
    surface_key: "age-tesoro-publico"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Tesoro Público"
    surface_name: "Adhesión al Código de Buenas Prácticas — vía Registro Electrónico Común"
    surface_type: "SEDE"
    origin: "https://www.tesoropublico.gob.es"
    official_site: "https://www.tesoropublico.gob.es/"
    e_sede: "https://www.tesoropublico.gob.es/"
    entry_url: "https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede del Tesoro indica que la comunicación de adhesión al Código de Buenas Prácticas para deudores hipotecarios en riesgo de vulnerabilidad se presenta por el Registro Electrónico Común y publica su acceso directo."
    protocol_evidence: "La página pública actual del Tesoro publica dos enlaces exactos a https://rec.redsara.es/registro/action/are/acceso.do para este trámite. En una sesión Chromium pública y aislada, ese URL respondió con 301 a https://reg.redsara.es/ y, con contexto de navegador español, el root REG respondió con 302 a https://reg.redsara.es/es/, que coincide exactamente con el startUrl del perfil existente reg-age-redsara. La página del Tesoro menciona certificado electrónico/DNIe y AutoFirma, pero no se infieren de ello algoritmo, formato, endpoint ni ABI de firma propios del Tesoro."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "TESORO-REC-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara por delegación pública exacta y cadena de redirección acotada; la página del Tesoro permanece como entry URL y no se amplía la confianza criptográfica REG-AGE al origen www.tesoropublico.gob.es. E2E físico no realizado."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición Tesoro → REC/REG-AGE sin completar ni presentar una comunicación administrativa real; mantener release fail-closed hasta entonces."
    notes: "La implementación cubre únicamente la vía REC/REG publicada para esta adhesión. Otros servicios del Tesoro (pagos, FCT, SECAD, compra-venta, sandbox, creadores de mercado) tienen superficies distintas y no se incorporan ni se infieren en este perfil."

  - inventory_id: "ES-PUB-0092"
    surface_key: "age-universidad-nacional-de-educacion-a-distancia-uned"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Universidad Nacional de Educación a Distancia (UNED)"
    surface_name: "Registro Electrónico — Sede electrónica de la UNED"
    surface_type: "SEDE"
    origin: "https://uned.sede.gob.es"
    official_site: "https://uned.sede.gob.es/"
    e_sede: "https://uned.sede.gob.es/"
    entry_url: "https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    launch_url: "https://reg.redsara.es/es/"
    procedure_page: "https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_REG_AGE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La nueva Sede electrónica de la UNED publica el Registro Electrónico General de la AGE (REG-AGE) para solicitudes, escritos y comunicaciones sin formulario normalizado propio."
    protocol_evidence: "La antigua sede.uned.es anuncia que desde el 1-11-2025 los procedimientos se realizan en https://uned.sede.gob.es y enlaza su Registro Electrónico. La ficha vigente de la nueva Sede identifica expresamente el servicio como REG-AGE y publica «Acceso al Registro Electrónico» con href https://reg.redsara.es/; fresh Chromium confirma el redirect público 302 de ese root al startUrl canónico https://reg.redsara.es/es/ del perfil reg-age-redsara. No se atribuye a UNED un ABI de firma propio ni se amplían sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D11", "UNED-REG-2026-08-17", "P14"]
    reason: "Alias QA-only al perfil existente reg-age-redsara: la nueva Sede UNED delega explícitamente el Registro Electrónico General en REG-AGE y Workspace-47 lanza únicamente el startUrl canónico exacto del perfil existente; falta E2E físico de la transición."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la transición UNED → REG-AGE sin realizar una presentación administrativa real; mantener QA_ONLY hasta entonces."
    notes: "El directorio AGE de 2026-07-16 apuntaba a la sede legacy https://sede.uned.es/; esa propia sede publica el cambio a https://uned.sede.gob.es desde el 1-11-2025. Ministerio(s) enumerador(es): Ministerio de Ciencia, Innovación y Universidades."

```

### 7.3. Comunidades y ciudades autónomas [D03]

Esta ola conserva las cuatro superficies autonómicas del seed sin modificar su
evidencia ni su estado. D03 enumera territorios, no contratos ni origins HTTPS
de sede. Por ello, cada registro nuevo combina D03 con evidencia oficial
portal-specific y permanece `BROWSE_ONLY`.

```yaml
records:
  - inventory_id: "ES-PUB-0093"
    surface_key: "junta-andalucia-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Andalucía"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Andalucía"
    surface_name: "Sede Electrónica General — Presentación electrónica general"
    surface_type: "SEDE"
    origin: "https://veaja.cloud.juntadeandalucia.es"
    official_site: "https://www.juntadeandalucia.es/servicios/sede.html"
    e_sede: "https://www.juntadeandalucia.es/servicios/sede.html"
    entry_url: "https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA"
    procedure_page: "https://www.juntadeandalucia.es/servicios/sede/tramites.html"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "AUTOSCRIPT_AUTOFIRMA"
    protocol_family: "VEA_AUTOSCRIPT_DYNAMIC"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede Electrónica General dirige la Presentación electrónica general al procedimiento PEG_VEA de Ventanilla Electrónica; Workspace-47 habilita únicamente la navegación QA al inicio público exacto, manteniendo autenticación y firma bloqueadas."
    protocol_evidence: "La Sede enlaza públicamente PEG mediante ws094/ws050, que actualmente convergen en https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA. La página pública ofrece Certificado Electrónico y Cl@ve. El bundle vigente carga AutoScript/AutoFirma y prepara firma explícita sobre hashes, pero algoritmo, formato, hashAlgorithm y filtro cualificado final proceden de datos de borrador protegidos tras autenticación; por ello el perfil no expone SIGN, SELECT_CERTIFICATE, AFIRMA_URI ni client-auth."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A01A", "A01B", "JUNTA-VEA-PEG-2026-08-17", "JUNTA-VEA-RUNTIME-2026-08-17"]
    reason: "Perfil QA-only de navegación al PEG_VEA público exacto. La firma es obligatoria en la entrega y AutoScript es observable, pero el contrato sensible exacto depende de estado autenticado/borrador; no se copian constantes de OVORION, Oficina Virtual ni otros portales Junta."
    reviewed_at: "2026-08-17"
    next_gate: "Validar físicamente la navegación Sede → PEG_VEA y, solo con autorización separada, reabrir el contrato de autenticación/firma para observar algorithm/format/hashAlgorithm del borrador sin efectuar una presentación administrativa."
    notes: "El perfil confía únicamente en veaja.cloud.juntadeandalucia.es para navegación. api-veaja.cloud.juntadeandalucia.es y los flujos de certificado/Cl@ve quedan fuera del trust del perfil."

  - inventory_id: "ES-PUB-0094"
    surface_key: "aragon-tramites-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Aragón"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Aragón"
    surface_name: "Trámites del Gobierno de Aragón"
    surface_type: "SEDE"
    origin: "https://www.aragon.es"
    official_site: "https://www.aragon.es/tramites"
    e_sede: "https://www.aragon.es/tramites"
    entry_url: "https://www.aragon.es/tramites"
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
    operation_summary: "Tramitación con medios de identificación y firma según el procedimiento."
    protocol_evidence: "La ayuda oficial describe identificación y firma electrónica, pero no publica ABI ni transporte."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A02A", "A02B"]
    reason: "La mención general de firma no acredita cliente JS, formato, callback, endpoint ni TLS cliente."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un trámite concreto y su JavaScript vigente sin enviar formulario."

  - inventory_id: "ES-PUB-0095"
    surface_key: "asturias-miprincipado-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Principado de Asturias"
    province_or_municipality: "NO_APLICA"
    institution_name: "Principado de Asturias"
    surface_name: "MiPrincipado — Solicitud Genérica"
    surface_type: "SEDE"
    origin: "https://miprincipado.asturias.es"
    official_site: "https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica"
    e_sede: "https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica"
    entry_url: "https://miprincipado.asturias.es/-/dboid-6269000102616541907573?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites"
    procedure_page: "https://miprincipado.asturias.es/-/dboid-6269000102616541907573?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Solicitud Genérica protegida de MiPrincipado: soporte QA limitado a la autenticación con certificado mediante la frontera mTLS exacta de Cl@ve; la firma documental y la presentación final quedan fuera del contrato implementado."
    protocol_evidence: "El POST oficial sytInitForm hacia https://tramita.asturias.es/sta/Relec/STARhssoManager encadena RHSSO OIDC con client_id=sitemiprincipado y broker samlClaveV2 hasta https://pasarela.clave.gob.es/Proxy2/ServiceProvider. La opción DNIe / Certificado electrónico realiza POST exacto a https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen:443; un acceso controlado con el certificado autorizado completó mTLS y devolvió la Solicitud Genérica protegida. El handshake TLS 1.2 del endpoint envía CertificateRequest sin nombres de CA y anuncia RSA/ECDSA; el certificado usado con éxito es RSA con Digital Signature. La pantalla autenticada muestra 1. Rellenar formulario, 2. Firmar, 3. Descargar justificante y POST /sta/Relec/TramitaSign, pero el avance normal exige Órgano, Unidad Administrativa, Detalle de la solicitud y modo de notificación antes de alcanzar el signer."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A03A", "A03C", "ASTURIAS-CLAVE-AUTH-2026-08-19", "ASTURIAS-SOLICITUD-GENERICA-2026-08-19"]
    reason: "IMPLEMENTED_NOT_E2E: perfil QA fail-closed limitado a la cadena MiPrincipado/STA/RHSSO/Cl@ve observada y al salto exacto https://pasarela.clave.gob.es/Proxy2/ServiceProvider -> https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen:443. La firma sigue NO_VERIFICADO: no se inventaron Órgano, Unidad Administrativa, Detalle de la solicitud ni preferencia de notificación, no se ejecutó operación de clave privada y no hubo presentación/registro final."
    reviewed_at: "2026-08-19"
    next_gate: "Con datos administrativos reales del operador, avanzar por onSave hasta la página de firma e instrumentar el signer para capturar formato, algoritmo, payload y callback, abortando antes de cualquier operación de clave privada o presentación final."

  - inventory_id: "ES-PUB-0096"
    surface_key: "asturias-sede-tramite-autofirma"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Principado de Asturias"
    province_or_municipality: "NO_APLICA"
    institution_name: "Principado de Asturias"
    surface_name: "Frontend de trámite de la Sede de Asturias"
    surface_type: "FRONTEND_TRAMITE"
    origin: "https://sede.asturias.es"
    official_site: "https://sede.asturias.es/ast/-/dboid-6269000011903512107573"
    e_sede: "https://sede.asturias.es/ast/-/dboid-6269000011903512107573"
    entry_url: "https://sede.asturias.es/ast/-/dboid-6269000011903512107573"
    procedure_page: "https://sede.asturias.es/ast/-/dboid-6269000011903512107573"
    certificate_required: "NO_VERIFICADO"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "ASTURIAS_SEDE_MIPRINCIPADO_REDIRECT_NAVIGATION"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La URL oficial de Sede redirige a la ficha vigente del mismo trámite en miPrincipado; Workspace-47 habilita solo esa navegación QA y mantiene fuera de confianza el POST posterior a tramita.asturias.es y cualquier firma."
    protocol_evidence: "El 2026-08-19 el entry exacto https://sede.asturias.es/ast/-/dboid-6269000011903512107573 respondió 301 a https://miprincipado.asturias.es/ast/-/dboid-6269000011903512107573, que respondió 200 y publicó el mismo trámite CERT0046T01. La ficha contiene un POST público a https://tramita.asturias.es/sta/Relec/STARhssoManager y un requisito técnico de firma, pero no se ejecutó ese POST ni se atribuye al trámite el MiniApplet de la utilidad separada de comprobación de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A03B", "ASTURIAS-0096-REDIRECT-2026-08-19"]
    reason: "Perfil QA-only de navegación: confía en sede.asturias.es como iniciador y únicamente en miprincipado.asturias.es como redirect observado. tramita.asturias.es, Cl@ve, selección de certificado, AutoFirma/MiniApplet, algoritmo, formato, callback y endpoint permanecen fuera del contrato; no hay E2E físico."
    reviewed_at: "2026-08-19"
    next_gate: "Validar físicamente la navegación Sede → miPrincipado; una reapertura autenticada puede observar el POST de STARhssoManager y el estado pre-firma, deteniéndose antes de firma criptográfica o presentación final."

    notes: "El redirect conserva exactamente el dboid. El host tramita.asturias.es y el helper histórico www30.asturias.es/Esign2 no se añaden a los orígenes de confianza de este perfil."

  - inventory_id: "ES-PUB-0097"
    surface_key: "caib-seu-electronica"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Illes Balears"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de las Illes Balears"
    surface_name: "Seu Electrònica de les Illes Balears"
    surface_type: "SEDE"
    origin: "https://www.caib.es"
    official_site: "https://www.caib.es/seucaib/ca/"
    e_sede: "https://www.caib.es/seucaib/ca/"
    entry_url: "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros="
    procedure_page: "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros="
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "MiniApplet 1.6.5"
    protocol_family: "MINIAPPLET_XML_BATCH_TRIFASICO_PORTAFIB_PADES"
    signature_format: "PAdES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "Runtime requestPlugin: https://intranet.caib.es/portafibback/public/signmodule/requestPlugin/{token}/-1/{BatchPresigner,BatchPostsigner}; POST query xml/certs/tridata"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Instancia genérica protegida entrega a MiniApplet.signBatch un único lote PAdES SHA256withRSA mediante PortaFIB; soporte QA limitado al contrato exacto observado y sin envío final."
    protocol_evidence: "Controlled-auth autorizado alcanzó la pantalla PortaFIB previa a la firma: MiniApplet.signBatch(batchB64, BatchPresigner, BatchPostsigner, extraProperties, showResultCallback, showErrorCallback); el lote XML contiene stoponerror=false, SHA256withRSA, PAdES, datasource request-scoped, SignatureId ligado a token y SignSaverFile. El wire PRE/PK1/POST se contrastó con el cliente AutoFirma oficial; no se ejecutó la firma real."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A04A", "A04B"]
    reason: "IMPLEMENTED_NOT_E2E: bridge y adaptador QA aceptan únicamente el origin intranet.caib.es, ruta requestPlugin exacta, un lote PAdES observado y parámetros exactos; autenticación completa dentro de la app, firma criptográfica y presentación administrativa siguen sin E2E y no se ejecutaron en este pass."
    reviewed_at: "2026-08-18"
    next_gate: "E2E solo en entorno de prueba autorizado, con identidad/certificado de prueba y sin presentación administrativa; validar primero el retorno del callback antes de cualquier submit."

  - inventory_id: "ES-PUB-0098"
    surface_key: "caib-registre-electronic"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Illes Balears"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de las Illes Balears"
    surface_name: "Registre Electrònic"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://apps.caib.es"
    official_site: "https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/"
    e_sede: "https://www.caib.es/seucaib/ca/"
    entry_url: "https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/"
    launch_url: "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros="
    procedure_page: "https://www.caib.es/seucaib/es/200/personas/tramites/tramite/4213695"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_CAIB_INSTANCIA_GENERICA"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "El Registre Electrònic autonómico delega la solicitud genérica al mismo asistente SiStra2 de Instància genèrica ya cubierto por el perfil CAIB PortaFIB."
    protocol_evidence: "La entrada oficial vigente del Registre Electrònic enlaza la solicitud genérica a la ficha CAIB 4213695; la variante oficial en castellano de esa misma ficha publica exactamente el launch URL https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=false&idTramiteCatalogo=4213963&parametros=, que coincide byte por byte con el startUrl del perfil caib-portafib ya verificado. El alias no atribuye un ABI propio a apps.caib.es ni amplía sus orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A04A", "A04B", "A04C"]
    reason: "Alias QA-only al perfil existente caib-portafib por la cadena oficial vigente hacia el launch URL exacto de la Instància genèrica; se conserva apps.caib.es como entry URL, no se añade a los orígenes de confianza del perfil y falta E2E físico de la transición desde el Registre Electrònic."
    reviewed_at: "2026-08-19"
    next_gate: "Validar físicamente la transición Registre Electrònic → Instància genèrica y el callback de firma en QA con identidad autorizada, deteniéndose antes de la firma criptográfica y de cualquier registro final."
    notes: "Cadena revalidada 2026-08-19: Registre Electrònic → ficha 4213695 (SIA 2307649) → CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR / idTramiteCatalogo=4213963."

  - inventory_id: "ES-PUB-0099"
    surface_key: "canarias-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Canarias"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Canarias"
    surface_name: "Sede electrónica del Gobierno de Canarias"
    surface_type: "SEDE"
    origin: "https://sede.gobiernodecanarias.org"
    official_site: "https://sede.gobiernodecanarias.org/sede/la_sede"
    e_sede: "https://sede.gobiernodecanarias.org/sede/la_sede"
    entry_url: "https://sede.gobiernodecanarias.org/sede/la_sede"
    procedure_page: "https://sede.gobiernodecanarias.org/sede/tramites/6861"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_MINIAPPLET_LOCAL_CADES"
    signature_format: "CAdES Detached"
    signature_algorithm: "SHA1withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado mediante AutoScript: reto UTC efímero firmado localmente como CAdES detached antes del POST de autenticación."
    protocol_evidence: "El flujo público GET /sede/tramitador/creacion/tramites/6861 redirige 303 a /sede/identificacionmenu; la rama pública GET /sede/identificacion carga sfest.base.js y construye AutoScript.sign(Base64(Date.toUTCString()), SHA1withRSA, CAdES, extraProperties). Runtime Chromium confirmó CAdES Detached, serverUrl /platino/servlet_afirma/SignatureService, referencesDigestMethod SHA-512 y el filtro exacto nonexpired/signingCert/issuer.rfc2254; no se ejecutaron firma, selección de certificado ni POST."
    client_tls_auth: "NO_EN_CONTORNO_OBSERVADO"
    evidence_ids: ["D03", "A05A", "A05B"]
    reason: "Contrato público exacto implementado en perfil QA_ONLY fail-closed; falta E2E físico con certificado real y por ello no se afirma autenticación completada ni presentación administrativa."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro del acceso con certificado en /sede/identificacion, sin presentación administrativa; mantener QA_ONLY hasta evidencia separada."

  - inventory_id: "ES-PUB-0100"
    surface_key: "cantabria-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Cantabria"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Cantabria"
    surface_name: "Sede electrónica del Gobierno de Cantabria"
    surface_type: "SEDE"
    origin: "https://sede.cantabria.es"
    official_site: "https://sede.cantabria.es/sede/"
    e_sede: "https://sede.cantabria.es/sede/"
    entry_url: "https://sede.cantabria.es/sede/"
    launch_url: "https://rec.cantabria.es/rec/bienvenida.htm"
    procedure_page: "https://sede.cantabria.es/sede/catalogo-de-tramites/tramite/emision-de-certificados-de-los-datos-que-consten-en-los-registros-de-asociaciones/2645"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_CANTABRIA_REC"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Sede de Cantabria delega el acceso público al Registro Electrónico Común mediante un enlace exacto al REC ya implementado en QA."
    protocol_evidence: "La portada oficial de la Sede enlaza exactamente https://rec.cantabria.es/rec/bienvenida.htm; ese launch URL coincide byte a byte con startUrl del perfil cantabria-rec-cert-login ya IMPLEMENTED_NOT_E2E. No se infiere un ABI nuevo para sede.cantabria.es."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A06A", "A06B", "A06C", "A06D", "A06E"]
    reason: "Alias QA-only al perfil existente cantabria-rec-cert-login por igualdad exacta del launch URL oficial; se conserva la Sede como entry URL, no se atribuye al origen sede.cantabria.es el ABI del REC y falta E2E físico."
    reviewed_at: "2026-08-16"
    next_gate: "E2E físico seguro desde la Sede hasta el REC sin autenticación real ni presentación administrativa; mantener QA_ONLY hasta evidencia separada."

  - inventory_id: "ES-PUB-0101"
    surface_key: "cantabria-registro-electronico-comun"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Cantabria"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Cantabria"
    surface_name: "Registro Electrónico Común"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://rec.cantabria.es"
    official_site: "https://rec.cantabria.es/rec/bienvenida.htm"
    e_sede: "https://sede.cantabria.es/sede/"
    entry_url: "https://rec.cantabria.es/rec/bienvenida.htm"
    procedure_page: "https://rec.cantabria.es/rec/bienvenida.htm"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoFirma / MiniApplet"
    protocol_family: "AUTOFIRMA_MINIAPPLET_LOCAL_CADES"
    signature_format: "CAdES / DETACHED / IMPLICIT"
    signature_algorithm: "SHA512withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al REC mediante MiniApplet.sign sobre un reto efímero de 40 caracteres hexadecimales."
    protocol_evidence: "La página pública y sus scripts first-party fijan SHA512withRSA, CAdES, mode=implicit, filters vacío y callback MiniApplet; el reto se entrega en runtime y no se codifica en la app."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A06E", "CANTABRIA-REC-AUTOFIRMA-2026-08-09", "CANTABRIA-REC-AFIRMA-CLIENTE-2026-08-09", "CANTABRIA-REC-MINIAPPLET-2026-08-09"]
    reason: "Contrato de acceso con certificado implementado solo en QA; no se realizó E2E, autenticación posterior ni envío del formulario del portal."
    reviewed_at: "2026-08-09"
    next_gate: "Validar físicamente el callback/login sin presentar trámites; mantener QA_ONLY hasta evidencia E2E sanitizada."

  - inventory_id: "ES-PUB-0102"
    surface_key: "castilla-leon-tramita"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Castilla y León"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Castilla y León"
    surface_name: "Sugerencias y quejas de la ciudadanía — QUJU"
    surface_type: "SEDE"
    origin: "https://presidencia.jcyl.es"
    official_site: "https://www.tramitacastillayleon.jcyl.es/"
    e_sede: "https://www.tramitacastillayleon.jcyl.es/"
    entry_url: "https://presidencia.jcyl.es/QUJU?O=1"
    procedure_page: "https://www.tramitacastillayleon.jcyl.es/web/jcyl/AdministracionElectronica/es/Plantilla100Detalle/1251181050732/Tramite/1277466706825/Tramite"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "JCYL_QUJU_PUBLIC_FORM_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada únicamente al formulario público QUJU exacto. El POST intermedio autorizado se aceptó en Chromium y terminó en /QUJU/Successfull sin cargar AutoScript/MiniApplet ni exponer un ABI de firma; no se implementa firma ni registro electrónico."
    protocol_evidence: "La ficha oficial vigente «Sugerencias y quejas de la ciudadanía» (IAPA 50 / SIA 1812980) enlaza al launcher first-party /Comun/Home/Formulario/QUJU, que publica «Acceder a la solicitud» hacia https://presidencia.jcyl.es/QUJU?O=1. En un perfil Chromium aislado, el formulario válido transmitió el POST permitido y alcanzó /QUJU/Successfull; la página resultante no expuso AutoScript, MiniApplet, JCYLfirma, iframes ni scripts de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A07A", "A07B", "JCYL-QUJU-PROC-2026-08-19", "JCYL-QUJU-RUNTIME-2026-08-19"]
    reason: "Perfil nuevo VERIFIED_CONTRACT/QA_ONLY limitado a la navegación pública exacta de QUJU, con capabilities vacío. El POST autorizado demuestra el límite operativo actual, pero no acredita ni implementa firma, selección de certificado, client TLS, algoritmo, formato, callback ni registro electrónico; esos campos permanecen NO_VERIFICADO y falta E2E físico."
    reviewed_at: "2026-08-19"
    next_gate: "Validar físicamente la navegación QA al formulario QUJU exacto. Cualquier soporte de firma/registro requiere una ruta first-party que exponga de forma independiente el ABI de firma antes de la operación criptográfica y del envío final."

  - inventory_id: "ES-PUB-0103"
    surface_key: "castilla-la-mancha-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Comunidades de Castilla-La Mancha"
    surface_name: "JCCM — Registro Electrónico / Solicitud Genérica"
    surface_type: "SEDE"
    origin: "https://registrounicociudadanos.jccm.es"
    official_site: "https://www.jccm.es/"
    e_sede: "https://www.jccm.es/"
    entry_url: "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"
    procedure_page: "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_MINIAPPLET_LOCAL_XADES_CLIENT_TLS_AUTH"
    signature_format: "XAdES Detached / IMPLICIT en la rama con certificado; Cl@ve usa la rama sin firma criptográfica local"
    signature_algorithm: "SHA512withRSA en la rama con certificado"
    endpoint: "LOCAL_AUTOFIRMA; el POST final AltaRegGenericaAction.do?accion=Guardar no se ejecutó"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso por certificado o Cl@ve y firma XAdES del XML de resumen de la Solicitud Genérica en la rama con certificado."
    protocol_evidence: "Runtime controlado 2026-08-19: el SAML de Cl@ve volvió a JCCM y abrió accesoclvd.do con formulario AltaReg. La página protegida construye getXmlForm(), codifica el XML en Base64 y su rama con certificado invoca MiniApplet.sign(xmlBase64, SHA512withRSA, XADES, format=XAdES Detached + mode=implicit, firma_success, firma_error); firma_success copia firma/certificado y solo después prepara AltaRegGenericaAction.do?accion=Guardar. La rama Cl@ve llama firmarFormClave, copia el XML sin firma criptográfica y prepara el mismo Guardar. No se ejecutó MiniApplet.sign sobre la solicitud ni Guardar. El reto público ABCDEF con el mismo tuple sigue siendo únicamente autenticación y no es evidencia del payload final."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A08A", "A08B", "A08C", "JCCM-REG-GENERICA-AUTH-2026-08-19", "JCCM-REG-GENERICA-SIGNER-2026-08-19"]
    reason: "Contrato exacto de acceso y firma implementado fail-closed solo en QA. Se verificó el retorno autenticado y el signer first-party protegido, pero se detuvo antes de firma privada de la solicitud y antes del POST Guardar/registro final; por tanto no hay E2E de presentación."
    reviewed_at: "2026-08-19"
    next_gate: "Validar en QA Android el callback de firma con credencial autorizada y detenerse antes de AltaRegGenericaAction.do?accion=Guardar; promover a release solo con evidencia E2E separada."
    notes: "El perfil ES-PUB-0103 es independiente de ES-PUB-0183. No reutiliza el CAdES SHA1 probe antiguo ni REG-AGE; los parámetros XAdES SHA512 provienen del JavaScript first-party de la página protegida actual."

  - inventory_id: "ES-PUB-0104"
    surface_key: "catalunya-seu-electronica"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Cataluña"
    province_or_municipality: "NO_APLICA"
    institution_name: "Generalitat de Catalunya"
    surface_name: "Seu electrònica de la Generalitat de Catalunya"
    surface_type: "SEDE"
    origin: "https://web.gencat.cat"
    official_site: "https://web.gencat.cat/ca/seu-electronica"
    e_sede: "https://web.gencat.cat/ca/seu-electronica"
    entry_url: "https://web.gencat.cat/ca/seu-electronica"
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
    operation_summary: "Relación digital mediante sistemas de identificación y firma según el servicio."
    protocol_evidence: "El catálogo oficial acredita sistemas admitidos, no el contrato de un trámite concreto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A09A", "A09B", "A09D"]
    reason: "Operación, cliente JS, formato, algoritmo, callback y endpoint no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar un trámite firmado y observar su transporte público."

  - inventory_id: "ES-PUB-0105"
    surface_key: "catalunya-tramits-peticio-generica"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Cataluña"
    province_or_municipality: "NO_APLICA"
    institution_name: "Generalitat de Catalunya"
    surface_name: "Tràmits gencat — Petició genèrica"
    surface_type: "FRONTEND_TRAMITE"
    origin: "https://tramits.gencat.cat"
    official_site: "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?category=72461610-a82c-11e3-a972-000c29052e2c"
    e_sede: "https://web.gencat.cat/ca/seu-electronica"
    entry_url: "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?category=72461610-a82c-11e3-a972-000c29052e2c"
    procedure_page: "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?category=72461610-a82c-11e3-a972-000c29052e2c"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Petició genèrica mediante la ruta Cl@ve/eIdentifier; la firma documental posterior no está implementada."
    protocol_evidence: "El flujo protegido ING001HTM2 alcanza desde https://pasarela.clave.gob.es/Proxy2/ServiceProvider la petición TLS cliente exacta https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen. La autenticación con certificado fue aceptada por eIdentifier, pero el retorno federado termina después en HTTP 500 de GSIT; no se observó ABI de firma."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A09B", "A09C", "A09D", "CATALUNYA-PETICIO-CLIENTTLS-2026-08-19"]
    reason: "Implementación QA-only limitada a CLIENT_TLS_AUTH. La revalidación controlada 2026-08-19 volvió a terminar en GSIT j_acegi_security_check HTTP 500 después de autenticar; firma, formato, algoritmo, callback y aceptación E2E permanecen no verificados."
    reviewed_at: "2026-08-19"
    next_gate: "Revalidar el retorno GSIT; solo si abre una sesión protegida, avanzar hasta pre-sign para observar el ABI sin ejecutar firma privada ni presentación final."

  - inventory_id: "ES-PUB-0106"
    surface_key: "ceuta-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Ciudad Autónoma de Ceuta"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ciudad Autónoma de Ceuta"
    surface_name: "Sede electrónica de Ceuta"
    surface_type: "SEDE"
    origin: "https://sede.ceuta.es"
    official_site: "https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info"
    e_sede: "https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info"
    entry_url: "https://sede.ceuta.es/controlador/controlador?modulo=tramites&funcion=applet&tramite=ANI"
    procedure_page: "https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "CEUTA_AUTHENTICATED_FORM_BOUNDARY"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA al trámite ANI exacto y frontera autenticada frmAlta; no se implementa firma ni presentación."
    protocol_evidence: "Autenticación controlada devuelve a /controlador/controlador con #frmAlta POST, modulo=carpeta y cmd=entrada-prepara-add; el intento intermedio controlado terminó en Error 500 sin exponer AutoScript/MiniApplet/AutoFirma ni ABI de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A10A", "A10B", "A10C"]
    reason: "Contrato limitado a navegación QA y frontera de formulario autenticado. Firma, signer ABI, endpoint de firma y presentación final permanecen NO_VERIFICADO."
    reviewed_at: "2026-08-19"
    next_gate: "Repetir entrada-prepara-add cuando el portal deje de devolver Error 500 y observar la siguiente frontera; detenerse antes de firma privada o presentación final."

  - inventory_id: "ES-PUB-0107"
    surface_key: "melilla-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Ciudad Autónoma de Melilla"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ciudad Autónoma de Melilla"
    surface_name: "Sede electrónica de Melilla"
    surface_type: "SEDE"
    origin: "https://sede.melilla.es"
    official_site: "https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO"
    e_sede: "https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_TITULARSEDE"
    entry_url: "https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999"
    procedure_page: "https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_STA_BATCH_TRIFASICO"
    signature_format: "CAdES / PAdES / XAdES (perfil móvil limitado a CAdES detached)"
    signature_algorithm: "SHA256withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma por lotes AutoScript/STA del trámite público exacto, con perfil móvil QA limitado a CAdES detached."
    protocol_evidence: "El trámite exacto carga AutoScript y sta-autofirma-lote.js; el helper fija SHA256withRSA, CAdES por defecto y ramas PAdES/XAdES, con pre/post de lote delimitados por evidencia pública."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A11A", "A11B", "A11C", "A11D", "A11E", "A11F"]
    reason: "Contrato AutoScript/STA implementado solo en QA; sin aceptación física/E2E del portal real, sin envío administrativo y sin afirmar endpoint dinámico como URL estática."
    reviewed_at: "2026-08-11"
    next_gate: "E2E físico/manual del trámite exacto con consentimiento; no promover release ni VERIFIED_E2E sin evidencia separada."

  - inventory_id: "ES-PUB-0108"
    surface_key: "gva-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Comunitat Valenciana"
    province_or_municipality: "NO_APLICA"
    institution_name: "Generalitat Valenciana"
    surface_name: "Generalitat Valenciana — acceso con certificado al trámite 15602"
    surface_type: "SEDE"
    origin: "https://www.tramita.gva.es"
    official_site: "https://sede.gva.es/es/"
    e_sede: "https://sede.gva.es/es/"
    entry_url: "https://www.tramita.gva.es/ctt-att-atr/asistente/iniciarTramite.html?tramite=DGM_GEN&version=4&idioma=es&idProcGuc=15602&idSubfaseGuc=SOLICITUD&idCatGuc=PR"
    procedure_page: "https://sede.gva.es/es/detall-tramit?id_proc=15602"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al flujo autenticado del trámite 15602 mediante el servicio Client TLS de la Generalitat Valenciana; la firma y presentación posteriores quedan fuera del contrato implementado."
    protocol_evidence: "La ficha pública enlaza DGM_GEN v4. El login público deriva el acceso por certificado a ptt-clave.gva.es y de ahí a ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html, conservando el mismo idSesion y añadiendo idioma=es. TLS 1.2 en ese host emite CertificateRequest con tipos RSA sign y ECDSA sign; no se proporcionó certificado."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A12C", "A12D", "GVA-DGM15602-2026-08-18", "GVA-CLIENTTLS-2026-08-18"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA con source/target exactos, idSesion enlazado, idioma=es, host/path/port cerrados y TTL local acotado; sin E2E. No se infieren algoritmo, formato, endpoint ni constantes de la firma/presentación posterior."
    reviewed_at: "2026-08-18"
    next_gate: "Validar E2E por separado el acceso con certificado; mantener firma y presentación bloqueadas hasta evidencia autenticada/autorizada independiente."
    notes: "Investigación pública no autenticada 2026-08-18: Chromium real, inventario de red, JS de login, cadena de redirección sanitizada y handshake TLS sin certificado; no hubo POST, selección de identidad, autenticación, firma ni presentación."

  - inventory_id: "ES-PUB-0109"
    surface_key: "extremadura-tramites"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Extremadura"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Extremadura"
    surface_name: "Trámites de la Junta de Extremadura"
    surface_type: "SEDE"
    origin: "https://tramites.juntaex.es"
    official_site: "https://tramites.juntaex.es/"
    e_sede: "https://tramites.juntaex.es/"
    entry_url: "https://tramites.juntaex.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / STAAutofirmaLote"
    protocol_family: "AUTOSCRIPT_STA_BATCH_TRIFASICO"
    signature_format: "CAdES / PAdES / XAdES (perfil móvil QA limitado a CAdES detached)"
    signature_algorithm: "SHA256withRSA"
    endpoint: "URLs runtime bajo /sta/AutofirmaLote/{presign,postsign,getdata}; valores concretos suministrados por backend"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma por lotes AutoScript/STA con perfil móvil QA limitado al contrato CAdES observado."
    protocol_evidence: "La página pública de Registro General carga AutoScript, sta-autofirma-lote.js y webAppsFwk.js; firmarLote usa SHA256withRSA/CAdES/sign/stopOnError=false y devuelve el resultado mediante PRESENTAR_FIRMA."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A13A", "A13B", "A13E", "A13F", "A13G", "A13H"]
    reason: "Contrato STA batch implementado solo en QA; E2E físico/manual y aceptación por un trámite real siguen pendientes. Las URLs presign/postsign/getdata son efímeras y no se fijan como endpoints estáticos."
    reviewed_at: "2026-08-13"
    next_gate: "E2E físico/manual autorizado; no promover release ni VERIFIED_E2E sin evidencia separada."

  - inventory_id: "ES-PUB-0110"
    surface_key: "extremadura-sede-anterior"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Extremadura"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Extremadura"
    surface_name: "Sede electrónica anterior de la Junta de Extremadura"
    surface_type: "SEDE"
    origin: "https://sede.juntaex.es"
    official_site: "https://sede.juntaex.es/SEDE/"
    e_sede: "https://sede.juntaex.es/SEDE/"
    entry_url: "https://sede.juntaex.es/SEDE/"
    launch_url: "https://tramites.juntaex.es/"
    procedure_page: "https://sede.juntaex.es/SEDE/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_EXTREMADURA_TRAMITES"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La sede anterior permanece pública y delega expresamente la tramitación electrónica en Trámites de la Junta de Extremadura, cuyo launch exacto ya dispone de un perfil QA-only independiente."
    protocol_evidence: "La portada oficial sede.juntaex.es/SEDE/ enlaza su banner «Tramita» a https://tramites.juntaex.es y su «Registro Electrónico General» al STA actual bajo tramites.juntaex.es. El alias conserva la sede anterior como entry URL y reutiliza únicamente el startUrl canónico https://tramites.juntaex.es/ del perfil extremadura-tramites; no se atribuye un ABI de firma propio a sede.juntaex.es ni se amplían orígenes de confianza."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A13C", "EXT-SEDE-ALIAS-2026-08-18", "A13A", "A13B", "A13E", "A13F", "A13G", "A13H"]
    reason: "Alias QA-only al perfil existente extremadura-tramites por delegación oficial actual de la sede anterior al portal tramites.juntaex.es; faltan E2E físico de la transición y cualquier atribución de contrato criptográfico específico a la sede anterior."
    reviewed_at: "2026-08-18"
    next_gate: "Validar físicamente la transición sede.juntaex.es → tramites.juntaex.es sin autenticarse, firmar ni presentar una actuación administrativa; mantener QA_ONLY hasta entonces."

  - inventory_id: "ES-PUB-0111"
    surface_key: "extremadura-portal-tributario"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Extremadura"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Extremadura"
    surface_name: "Portal Tributario de la Junta de Extremadura"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://portaltributario.juntaex.es"
    official_site: "https://portaltributario.juntaex.es/PortalTributario/web/guest/requisitos-tecnicos"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://pattex.juntaex.es/PATTEX/externos.jsf?info=060~user~pass~SEDE_ALTA~https://pattex.juntaex.es~codigo"
    procedure_page: "https://pattex.juntaex.es/PATTEX/accesoCertificadoSEDE.jsf"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://pattex.juntaex.es/PATTEX/accesoCertificadoSEDE.jsf"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a PATTEX mediante renegociación TLS cliente en el salto exacto externos.jsf → accesoCertificadoSEDE.jsf; la firma documental y la presentación posterior quedan fuera del contrato implementado."
    protocol_evidence: "El launch PATTEX exacto redirige por HTTP 302 a /PATTEX/accesoCertificadoSEDE.jsf. En TLS 1.2, la petición a esa ruta provoca HelloRequest y renegociación con CertificateRequest; el servidor anuncia rsa_sign y ecdsa_sign y una lista certificate_authorities vacía. El runtime autenticado controlado del 2026-08-18 confirmó que presentar el certificado elimina el fallo de validación y que cookie-only no basta."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A13D", "PATTEX-AUTH-RUNTIME-2026-08-18", "PATTEX-TLS-RENEGOTIATION-2026-08-19"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA para source/target PATTEX exactos, host/path/port cerrados y TTL local acotado; E2E Android pendiente. La firma documental — signer ABI, formato, algoritmo y callbacks — y la presentación permanecen NO_VERIFICADO."
    reviewed_at: "2026-08-19"
    next_gate: "Validar E2E Android del acceso PATTEX con certificado; instrumentar por separado cualquier pre-sign/signing runtime y detenerse antes de firma criptográfica y presentación final."

  - inventory_id: "ES-PUB-0112"
    surface_key: "galicia-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Galicia"
    province_or_municipality: "NO_APLICA"
    institution_name: "Xunta de Galicia"
    surface_name: "Sede electrónica de la Xunta de Galicia"
    surface_type: "SEDE"
    origin: "https://sede.xunta.gal"
    official_site: "https://sede.xunta.gal/a-sede/identificacion-e-titularidade"
    e_sede: "https://sede.xunta.gal/a-sede/identificacion-e-titularidade"
    entry_url: "https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica"
    procedure_page: "https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet / AutoScript / AutoFirma / @firma"
    protocol_family: "MINIAPPLET_TRIPHASE"
    signature_format: "PAdES"
    signature_algorithm: "SHA1withRSA (LEGACY_SHA1 portal-specific)"
    endpoint: "https://sede.xunta.gal/presenta/sinatura/SignatureService"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Solicitud genérica PR004A: autenticación TLS con certificado y firma PAdES tri-phase de la solicitud principal; el perfil QA implementa solo el contrato de firma principal y selección de certificado observado."
    protocol_evidence: "Los assets first-party vigentes de Presenta 2026 fuerzan PAdEStri + SHA1withRSA para la solicitud principal, MiniApplet.selectCertificate(filters=nonexpired) y SignatureService en /presenta/sinatura/. El runtime controlado autenticado confirmó client-TLS y cargó PR004A sin firmar ni presentar. El wire PAdES tri-phase se contrastó con clienteafirma fe60ef3fdbae3c491e97c262a2179e2787b85776."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A14A", "A14B", "A14C", "LIVE-XUNTA-PR004A-2026-08-18"]
    reason: "Contrato exacto de firma principal implementado fail-closed en QA; XAdES de anexos, parámetros visuales y E2E Android mTLS→firma quedan fuera hasta prueba segura específica. La presentación/registro no se ejecutó."
    reviewed_at: "2026-08-18"
    next_gate: "E2E físico seguro en Android del acceso mTLS, selección de certificado y firma principal PR004A, deteniéndose antes de presentar o registrar."
    notes: "El bridge exige la página exacta /presenta/novo/PR004A_2025_1, data literal doc, SHA1withRSA, PAdEStri, endpoint SignatureService y el allowlist de propiedades observado. XAdEStri de anexos y propiedades visuales fallan cerrado. El modelo actual no combina clientAuthPolicy con operaciones SIGN; por ello el soporte permanece IMPLEMENTED_NOT_E2E."

  - inventory_id: "ES-PUB-0113"
    surface_key: "murcia-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Región de Murcia"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comunidad Autónoma de la Región de Murcia"
    surface_name: "Sede electrónica de la CARM"
    surface_type: "SEDE"
    origin: "https://sede.carm.es"
    official_site: "https://sede.carm.es/web/pagina?IDCONTENIDO=40291&IDTIPO=100"
    e_sede: "https://sede.carm.es/web/pagina?IDCONTENIDO=40291&IDTIPO=100"
    entry_url: "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288"
    procedure_page: "https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "CARM_PASE_CONCLAVE_BROWSE_AUTH_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "El procedimiento 385 expone una aportación de documentos protegida; soporte QA limitado a la navegación exacta Sede CARM → PASE → ConCl@ve, sin capacidades nativas de certificado o firma."
    protocol_evidence: "Runtime Chromium first-party revalidado 2026-08-19: /presentador/inicio/385/DI155 cruzó el WAF Radware por el flujo normal del navegador y redirigió a https://pase.carm.es/pase/login con service de retorno a F.TRAMITE?tipdoc=DI155&proc=385. PASE ofreció APP Cl@ve, certificado electrónico cualificado, eIDAS y Cl@ve Permanente; el formulario de certificado hizo POST exacto a https://conclave.carm.es/TokenServlet y alcanzó una página Cl@ve. Los valores session-scoped no se guardaron. No se observó ni autorizó ABI de firma, selección nativa de certificado ni TLS cliente."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A16A", "A16B", "A16C"]
    reason: "NEW_PROFILE QA_ONLY de navegación: sede.carm.es es TRUSTED_BROWSE y validate.perfdrive.com, pase.carm.es y conclave.carm.es quedan solo como redirects BROWSE_ONLY. capabilities=[], sin bridge, clientAuthPolicy, endpoint de firma ni aceptación E2E."
    reviewed_at: "2026-08-19"
    next_gate: "Validar en WebView QA la cadena exacta WAF → PASE → ConCl@ve; con identidad de prueba/autorizada, investigar después el post-auth hasta el límite pre-sign sin firmar ni registrar."
    notes: "Alcance implementado: subflujo DI155 (aportación de documentos) del procedimiento 385. La Solicitud Genérica PAECARM F.SOLICITUD?proc=385 también se alcanzó en Chromium, pero no se rellenó ni se continuó por requerir datos administrativos."


  - inventory_id: "ES-PUB-0114"
    surface_key: "navarra-sede-registro-general"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Comunidad Foral de Navarra"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de Navarra"
    surface_name: "Sede electrónica y Registro General Electrónico de Navarra"
    surface_type: "SEDE"
    origin: "https://www.navarra.es"
    official_site: "https://www.navarra.es/es/tramites/titularidad-de-la-sede-electronica"
    e_sede: "https://www.navarra.es/es/tramites/titularidad-de-la-sede-electronica"
    entry_url: "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico"
    procedure_page: "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://ateka.navarra.es/ateka/Certificate/login"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al Registro General Electrónico mediante ATEKA; la firma y presentación final quedan fuera del contrato implementado."
    protocol_evidence: "El flujo oficial enlaza www.navarra.es → administracionelectronica.navarra.es/RGE2 → ateka.navarra.es/ateka/router?ReturnUrl=TOKEN → /ateka/Certificate/login?returnUrl=TOKEN. Dos sesiones públicas confirmaron que el valor efímero cambia entre sesiones y coincide exactamente source-target dentro de cada sesión. Chromium NetLog observó SSL_CLIENT_CERT_REQUESTED/URL_REQUEST_DELEGATE_CERTIFICATE_REQUESTED en /Certificate/login; el runtime autenticado controlado alcanzó RGE. En EnviarSinFirma.aspx, Firmar y enviar intentó un POST WebForms ordinario y no invocó FirmarXML/AutoScript/MiniApplet; el POST final se bloqueó antes de salir."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A17A", "A17B", "A17C", "NAVARRA-RGE-ENTRY-2026-08-18", "NAVARRA-ATEKA-CLIENTCERT-2026-08-18", "NAVARRA-RGE-AUTH-RUNTIME-2026-08-18"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA con host, path y token efímero ReturnUrl→returnUrl enlazados de forma exacta; E2E pendiente. No se infieren formato, algoritmo ni cliente de firma y no se implementa el POST final de presentación."
    reviewed_at: "2026-08-18"
    next_gate: "E2E físico Android del acceso ATEKA con certificado y retorno a RGE, deteniéndose antes del POST final; verificar por separado cualquier contrato de firma antes de implementarlo."

  - inventory_id: "ES-PUB-0115"
    surface_key: "euskadi-sede-electronica"
    administrative_level: "AUTONOMICO"
    autonomous_community: "País Vasco"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno Vasco"
    surface_name: "Sede electrónica de Euskadi"
    surface_type: "SEDE"
    origin: "https://www.euskadi.eus"
    official_site: "https://www.euskadi.eus/sede-electronica/"
    e_sede: "https://www.euskadi.eus/sede-electronica/"
    entry_url: "https://www.euskadi.eus/sede-electronica/"
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
    operation_summary: "Tramitación mediante medios de identificación y firma admitidos."
    protocol_evidence: "La ayuda general de firma no acredita que un formato o contrato se aplique a toda la sede."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A18A", "A18B", "A18C"]
    reason: "Las menciones documentales, incluida XAdES en escenarios delimitados, no prueban el flujo de esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar un procedimiento firmado y verificar su contrato específico."

  - inventory_id: "ES-PUB-0116"
    surface_key: "la-rioja-oficina-electronica"
    administrative_level: "AUTONOMICO"
    autonomous_community: "La Rioja"
    province_or_municipality: "NO_APLICA"
    institution_name: "Gobierno de La Rioja"
    surface_name: "Oficina electrónica del Gobierno de La Rioja"
    surface_type: "SEDE"
    origin: "https://web.larioja.org"
    official_site: "https://web.larioja.org/oficina-electronica/"
    e_sede: "https://web.larioja.org/oficina-electronica/"
    entry_url: "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697"
    procedure_page: "https://web.larioja.org/oficina-electronica/tramite?n=24697"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso del trámite 24697 mediante CAS; la vía de certificado usa client TLS en /clientcertSSL/login. La firma documental posterior permanece sin contrato verificado."
    protocol_evidence: "El flujo actual delega en ias1.larioja.org, CAS OFIVIR y /clientcertSSL/login: sin certificado responde 401/TLS CertificateRequest y el runtime autenticado retorna a /oficinavirtual/presentacion. No se infiere ABI de firma."
    client_tls_auth: "SI"
    evidence_ids: ["D03", "A19A", "A19B", "A19C"]
    reason: "Contrato CLIENT_TLS_AUTH acotado implementado solo en QA, sin E2E; cliente JS, formato, algoritmo, callback y endpoint de firma documental siguen deliberadamente NO_VERIFICADO."
    reviewed_at: "2026-08-18"
    next_gate: "Observar el contrato exacto de firma documental tras autenticación, abortando antes de ejecutar la firma o presentación final."

```

### 7.4. Cabildos y consells insulares [D12]

La cola cerrada D12 se materializa con dos superficies por institución:
primero el portal institucional `PORTAL_SERVICIO` y después la sede `SEDE`.
La separación se conserva porque cada par tiene origins y fronteras
funcionales distintos. Los portales incorporan D12 como provenance; todos los
URL fields se sostienen además en una definición I portal-specific de una sola
URL. Ninguna mención a certificado de servidor se interpreta como requisito de
certificado ciudadano, firma o TLS cliente.

```yaml
records:
  - inventory_id: "ES-PUB-0117"
    surface_key: "menorca-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular de Menorca"
    surface_name: "Portal institucional del Consell Insular de Menorca — Sol·licitud genèrica"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.cime.es"
    official_site: "https://www.cime.es/"
    e_sede: "https://seuelectronica.cime.es/"
    entry_url: "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262"
    procedure_page: "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AUTOFIRMA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://www.carpetaciutadana.org/cime/Login/LoginCert.aspx"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Sol·licitud genèrica del Consell Insular de Menorca mediante TLS cliente; la firma AutoFirma y el envío administrativo posteriores quedan fuera del contrato implementado."
    protocol_evidence: "El portal institucional enlaza la Carpeta Ciutadana actual; la Sol·licitud genèrica publica Tramitar, exige certificado para firmar y enviar y nombra AutoFirma. El flujo real pasa por Login.aspx y LoginCert.aspx conservando el parámetro efímero URL; sin certificado LoginCert devuelve 403, mientras el runtime autenticado con certificado autorizado alcanza formsol.aspx. No se observó ABI de firma antes del formulario protegido."
    client_tls_auth: "SI"
    evidence_ids: ["D12", "I01A", "I01B", "MENORCA-GENERIC-2026-08-18", "MENORCA-CLIENT-TLS-2026-08-18"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA para el origin, source, target y parámetro URL enlazado exactos; E2E Android pendiente. AutoFirma está documentado por el portal, pero formato, algoritmo, payload, callback y envío final no se infieren ni se implementan."
    reviewed_at: "2026-08-18"
    next_gate: "Verificar E2E únicamente la autenticación TLS cliente en WebView físico; mantener la firma documental y la presentación final bloqueadas hasta evidencia independiente."

  - inventory_id: "ES-PUB-0118"
    surface_key: "menorca-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular de Menorca"
    surface_name: "Seu electrònica del Consell Insular de Menorca"
    surface_type: "SEDE"
    origin: "https://seuelectronica.cime.es"
    official_site: "https://seuelectronica.cime.es/"
    e_sede: "https://seuelectronica.cime.es/"
    entry_url: "https://seuelectronica.cime.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Acceso público a trámites, carpeta o servicios administrativos de la sede electrónica."
    protocol_evidence: "La fuente acredita la sede y sus servicios públicos, no un requisito exacto de certificado o firma ni un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I01B"]
    reason: "Cliente JS, familia de protocolo, formato, algoritmo, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0119"
    surface_key: "mallorca-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell de Mallorca"
    surface_name: "Portal institucional del Consell de Mallorca"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.conselldemallorca.es"
    official_site: "https://www.conselldemallorca.es/"
    e_sede: "https://seu.conselldemallorca.net/"
    entry_url: "https://www.conselldemallorca.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I02A", "I02B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0120"
    surface_key: "mallorca-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell de Mallorca"
    surface_name: "Seu electrònica del Consell de Mallorca"
    surface_type: "SEDE"
    origin: "https://cim.secimallorca.net"
    official_site: "https://seu.conselldemallorca.net/"
    e_sede: "https://seu.conselldemallorca.net/"
    entry_url: "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"
    procedure_page: "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://identificacionssl.sedipualba.es/"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al Registre Electrònic del Consell de Mallorca mediante el servidor SSL de identificación compartido de SEDIPUALB@; la firma documental posterior permanece fuera del contrato implementado."
    protocol_evidence: "La sede oficial enlaza el Registre Electrònic del Consell de Mallorca. Su flujo público SEDIPUALB@ exige certificado digital y construye exactamente la transición desde /segex/identificacion_opciones.aspx?idtoken=TOKEN&idioma=ca hacia https://identificacionssl.sedipualba.es/?idtoken=TOKEN&idioma=ca&entidad=07700, enlazando el mismo idtoken. El trámite declara AutoFirma para la firma posterior, cuyo formato, algoritmo y ABI no se infieren."
    client_tls_auth: "SI"
    evidence_ids: ["I02B", "MALLORCA-REGISTRE-2026-08-18", "MALLORCA-SSL-IDENT-2026-08-18"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA con host, path, entidad, idioma e idtoken source-target enlazado de forma exacta; sin E2E. No se infiere el algoritmo ni el formato de la firma documental posterior."
    reviewed_at: "2026-08-18"
    next_gate: "Verificación E2E separada del acceso con certificado y del paso de firma; mantener firma/presentación bloqueadas hasta evidencia independiente."

  - inventory_id: "ES-PUB-0121"
    surface_key: "eivissa-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular d’Eivissa"
    surface_name: "Portal institucional del Consell Insular d’Eivissa"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.conselldeivissa.es"
    official_site: "https://www.conselldeivissa.es/"
    e_sede: "https://seu.conselldeivissa.es/"
    entry_url: "https://www.conselldeivissa.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I03A", "I03B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0122"
    surface_key: "eivissa-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular d’Eivissa"
    surface_name: "Sede electrónica del Consell Insular d’Eivissa"
    surface_type: "SEDE"
    origin: "https://seu.conselldeivissa.es"
    official_site: "https://seu.conselldeivissa.es/"
    e_sede: "https://seu.conselldeivissa.es/"
    entry_url: "https://seu.conselldeivissa.es/"
    procedure_page: "https://seu.conselldeivissa.es/sta/CarpetaPublic/Public?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269002703260065905043"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / AutoFirma"
    protocol_family: "AUTOSCRIPT_LOCAL_CADES_IMPLICIT"
    signature_format: "CAdES / DETACHED / IMPLICIT"
    signature_algorithm: "SHA256withRSA"
    endpoint: "Sin endpoint de firma estático: /sta/reg/autofirma.js descarga el payload con AutofirmaDownload y sube el resultado mediante AutofirmaUpload; Storage/Retrieve son auxiliares AutoFirma."
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma local CAdES de Instancia General tras autenticación con certificado, sin ejecutar presentación administrativa."
    protocol_evidence: "La Instancia General vigente (PID 6269002703260065905043) devuelve authentication.autofirma=true tras autenticación controlada. Summary llama window.signFiles; /sta/reg/autofirma.js fija en Android SHA256withRSA, CAdES, headless=true, filter=encodedcert:<cert>;filter=nonexpired:, mode=implicit y MIME opcional, usando AutofirmaDownload/Upload same-origin."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I03B", "EIVISSA-INSTANCIA-GENERAL-2026-08-18", "EIVISSA-REG-AUTOFIRMA-2026-08-18", "EIVISSA-CONTROLLED-AUTH-2026-08-18"]
    reason: "IMPLEMENTED_NOT_E2E: perfil QA limitado al origin/PID y al contrato Android CAdES SHA256 implícito demostrado; autenticación con certificado validada, pero no se realizó firma real, AutofirmaUpload, presentación, pago ni aceptación E2E."
    reviewed_at: "2026-08-18"
    next_gate: "Validar en dispositivo físico una firma segura sin presentación y comprobar aceptación del resultado antes de cualquier promoción E2E."

  - inventory_id: "ES-PUB-0123"
    surface_key: "formentera-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular de Formentera"
    surface_name: "Portal institucional del Consell Insular de Formentera"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.consellinsulardeformentera.cat"
    official_site: "https://www.consellinsulardeformentera.cat/"
    e_sede: "https://ovac.conselldeformentera.cat/"
    entry_url: "https://www.consellinsulardeformentera.cat/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I04A", "I04B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0124"
    surface_key: "formentera-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Illes Balears"
    province_or_municipality: "Illes Balears"
    institution_name: "Consell Insular de Formentera"
    surface_name: "Sede electrónica / OVAC del Consell Insular de Formentera"
    surface_type: "SEDE"
    origin: "https://ovac.conselldeformentera.cat"
    official_site: "https://ovac.conselldeformentera.cat/"
    e_sede: "https://ovac.conselldeformentera.cat/"
    entry_url: "https://ovac.conselldeformentera.cat/"
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
    operation_summary: "Acceso público a trámites, carpeta o servicios administrativos de la sede electrónica."
    protocol_evidence: "La entrada oficial menciona certificados o sistemas de firma de forma condicional, sin publicar ABI, formato, algoritmo ni endpoint exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I04B"]
    reason: "Cliente JS, familia de protocolo, formato, algoritmo, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0125"
    surface_key: "el-hierro-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de El Hierro"
    surface_name: "Portal institucional del Cabildo Insular de El Hierro"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.elhierro.es"
    official_site: "https://www.elhierro.es/es"
    e_sede: "https://elhierro.sedelectronica.es/info.0"
    entry_url: "https://www.elhierro.es/es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I05A", "I05B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0126"
    surface_key: "el-hierro-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de El Hierro"
    surface_name: "Sede electrónica del Cabildo Insular de El Hierro"
    surface_type: "SEDE"
    origin: "https://elhierro.sedelectronica.es"
    official_site: "https://elhierro.sedelectronica.es/info.0"
    e_sede: "https://elhierro.sedelectronica.es/info.0"
    entry_url: "https://elhierro.sedelectronica.es/info.0"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "RECHECK_REQUIRED"
    inventory_status: "INACCESSIBLE"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I05B"]
    reason: "El transporte de revisión informó 400 Redirect loop detected al abrir https://elhierro.sedelectronica.es/info.0; no se siguió el ciclo ni se hicieron afirmaciones técnicas."
    reviewed_at: "2026-07-16"
    next_gate: "Revalidar la misma entrada HTTPS con un presupuesto cerrado de redirecciones y confirmar una respuesta estable antes de revisar operaciones."
    notes: "La existencia y titularidad proceden de la fuente oficial I05B; la indisponibilidad corresponde al transporte de revisión de este snapshot."

  - inventory_id: "ES-PUB-0127"
    surface_key: "tenerife-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de Tenerife"
    surface_name: "Portal institucional del Cabildo Insular de Tenerife"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.tenerife.es"
    official_site: "https://www.tenerife.es/"
    e_sede: "https://sede.tenerife.es/"
    entry_url: "https://www.tenerife.es/"
    launch_url: "https://sede.tenerife.es/"
    procedure_page: "https://sede.tenerife.es/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_TENERIFE_SEDE"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "El portal institucional delega la tramitación electrónica en la Sede oficial https://sede.tenerife.es/, cuyo launch exacto ya dispone de perfil QA-only independiente."
    protocol_evidence: "La información oficial del Cabildo ubica expresamente la Sede electrónica en sede.tenerife.es; el alias conserva www.tenerife.es como entry URL y solo resuelve el launch exacto del perfil tenerife-sede-electronica, sin habilitar capacidades en el origin institucional."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I06A", "I06B"]
    reason: "Alias QA-only al perfil existente tenerife-sede-electronica por delegación oficial exacta a https://sede.tenerife.es/; no se hereda confianza al origin institucional y falta E2E físico desde la entrada www.tenerife.es."
    reviewed_at: "2026-08-16"
    next_gate: "Validar físicamente que la entrada institucional conduce a la Sede exacta y que el perfil QA-only conserva sus límites, sin autenticarse, firmar ni presentar una actuación administrativa."

  - inventory_id: "ES-PUB-0128"
    surface_key: "tenerife-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de Tenerife"
    surface_name: "Sede electrónica del Cabildo Insular de Tenerife"
    surface_type: "SEDE"
    origin: "https://sede.tenerife.es"
    official_site: "https://sede.tenerife.es/"
    e_sede: "https://sede.tenerife.es/"
    entry_url: "https://sede.tenerife.es/"
    procedure_page: "SPA pública de la sede; el componente app-autofirma firma el documentoSolicitud descargado por la tramitación."
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / AutoFirma / AutoFirmaService (Angular)"
    protocol_family: "AUTOSCRIPT_CADES_LOCAL"
    signature_format: "CAdES detached"
    signature_algorithm: "SHA512withRSA"
    endpoint: "Sin endpoint de firma: AutoScript.sign recibe en Base64 el documentoSolicitud descargado y devuelve signatureB64 al backend."
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma local AutoFirma de la solicitud descargada, limitada en QA al contrato SHA512withRSA/CAdES/mode=explicit observado."
    protocol_evidence: "El bundle público 76.81426d6ba0b90ca6.js define app-autofirma/AutoFirmaService: descarga documentoSolicitud, lo codifica Base64, fija CAdES + SHA512withRSA + mode=explicit, llama AutoScript.sign y entrega signatureB64 mediante presentCertificateSignature."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I06B", "I06C"]
    reason: "Contrato de firma local implementado solo en QA; no se ha realizado E2E físico/manual, autenticación real ni presentación administrativa. El perfil no promueve release ni VERIFIED_E2E."
    reviewed_at: "2026-08-14"
    next_gate: "E2E físico/manual autorizado sobre el flujo real; no promover release ni VERIFIED_E2E sin evidencia separada."

  - inventory_id: "ES-PUB-0129"
    surface_key: "la-palma-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de La Palma"
    surface_name: "Portal institucional del Cabildo Insular de La Palma"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.cabildodelapalma.es"
    official_site: "https://www.cabildodelapalma.es/"
    e_sede: "https://sedeelectronica.cabildodelapalma.es/"
    entry_url: "https://www.cabildodelapalma.es/"
    launch_url: "https://sedeelectronica.cabildodelapalma.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "DELEGACION_SEDE_LA_PALMA"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "El portal institucional delega la administración electrónica mediante un enlace exacto a la Sede de La Palma ya implementada en QA."
    protocol_evidence: "La portada oficial publica exactamente https://sedeelectronica.cabildodelapalma.es/; ese launch URL coincide byte a byte con startUrl del perfil la-palma-sede-electronica ya IMPLEMENTED_NOT_E2E. No se infiere un ABI nuevo para www.cabildodelapalma.es."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I07A", "I07B"]
    reason: "Alias QA-only al perfil existente la-palma-sede-electronica por igualdad exacta del launch URL oficial; se conserva el portal institucional como entry URL, no se hereda trust al origin institucional y falta E2E físico."
    reviewed_at: "2026-08-16"
    next_gate: "E2E físico seguro desde el portal institucional hasta la Sede sin realizar una presentación administrativa; mantener QA_ONLY hasta evidencia separada."

  - inventory_id: "ES-PUB-0130"
    surface_key: "la-palma-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de La Palma"
    surface_name: "Sede electrónica del Cabildo Insular de La Palma"
    surface_type: "SEDE"
    origin: "https://sedeelectronica.cabildodelapalma.es"
    official_site: "https://sedeelectronica.cabildodelapalma.es/"
    e_sede: "https://sedeelectronica.cabildodelapalma.es/"
    entry_url: "https://sedeelectronica.cabildodelapalma.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / STAAutofirmaLote"
    protocol_family: "AUTOSCRIPT_STA_BATCH_TRIFASICO"
    signature_format: "CAdES / PAdES / XAdES (perfil móvil QA limitado a CAdES detached)"
    signature_algorithm: "SHA256withRSA"
    endpoint: "URLs runtime bajo /sta/AutofirmaLote/{presign,postsign,getdata}; valores concretos suministrados por backend"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma por lotes AutoScript/STA con perfil móvil QA limitado al contrato CAdES observado."
    protocol_evidence: "La sede pública carga AutoScript, sta-autofirma-lote.js y webAppsFwk.js; los tres recursos son byte-idénticos a los ya validados en Extremadura. firmarLote declara SHA256withRSA/CAdES/sign y usa el contrato STA batch trifásico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I07B", "I07C", "I07D", "I07E", "I07F"]
    reason: "Contrato STA batch implementado solo en QA; E2E físico/manual y aceptación por un trámite real siguen pendientes. Las URLs presign/postsign/getdata son efímeras y no se fijan como endpoints estáticos."
    reviewed_at: "2026-08-13"
    next_gate: "E2E físico/manual autorizado; no promover release ni VERIFIED_E2E sin evidencia separada."

  - inventory_id: "ES-PUB-0131"
    surface_key: "la-gomera-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de La Gomera"
    surface_name: "Portal institucional del Cabildo Insular de La Gomera"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.lagomera.es"
    official_site: "https://www.lagomera.es/"
    e_sede: "https://lagomera.sedelectronica.es/info.0"
    entry_url: "https://www.lagomera.es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I08A", "I08B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0132"
    surface_key: "la-gomera-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Santa Cruz de Tenerife"
    institution_name: "Cabildo Insular de La Gomera"
    surface_name: "Sede electrónica del Cabildo Insular de La Gomera"
    surface_type: "SEDE"
    origin: "https://lagomera.sedelectronica.es"
    official_site: "https://lagomera.sedelectronica.es/info.0"
    e_sede: "https://lagomera.sedelectronica.es/info.0"
    entry_url: "https://lagomera.sedelectronica.es/info.0"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "RECHECK_REQUIRED"
    inventory_status: "INACCESSIBLE"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I08B"]
    reason: "El transporte de revisión informó 400 Redirect loop detected al abrir https://lagomera.sedelectronica.es/info.0; no se siguió el ciclo ni se hicieron afirmaciones técnicas."
    reviewed_at: "2026-07-16"
    next_gate: "Revalidar la misma entrada HTTPS con un presupuesto cerrado de redirecciones y confirmar una respuesta estable antes de revisar operaciones."
    notes: "La existencia y titularidad proceden de la fuente oficial I08B; la indisponibilidad corresponde al transporte de revisión de este snapshot."

  - inventory_id: "ES-PUB-0133"
    surface_key: "fuerteventura-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Fuerteventura"
    surface_name: "Portal institucional del Cabildo Insular de Fuerteventura"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.cabildofuer.es"
    official_site: "https://www.cabildofuer.es/cabildo/"
    e_sede: "https://sede.cabildofuer.es/eAdmin/Sede.do"
    entry_url: "https://www.cabildofuer.es/cabildo/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I09A", "I09B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0134"
    surface_key: "fuerteventura-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Fuerteventura"
    surface_name: "Sede electrónica del Cabildo Insular de Fuerteventura"
    surface_type: "SEDE"
    origin: "https://sede.cabildofuer.es"
    official_site: "https://sede.cabildofuer.es/eAdmin/Sede.do"
    e_sede: "https://sede.cabildofuer.es/eAdmin/Sede.do"
    entry_url: "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
    procedure_page: "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet / AutoFirma"
    protocol_family: "MINIAPPLET_LOCAL_PADES"
    signature_format: "PAdES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    profile_id: "fuerteventura-sede-electronica"
    operation_summary: "Firma PAdES local del PDF de solicitud generado por la Sede, limitada al contrato exacto observado en pre-sign."
    protocol_evidence: "Controlled authenticated observation 2026-08-18: tras Cl@ve/AFIRMA, un bootstrap de contacto sin datos inventados dejó idTercero vacío pero avanzó al registro; action=firmar serializa la solicitud y verYfirmar&modo=cert expone un PDF Base64 y llama exactamente MiniApplet.sign(dataB64, SHA256withRSA, PAdES, parámetros visuales fijos, successCallback, errorCallback). El callback de éxito llenaría firmaElectronica/certificado y POSTearía Registrar.do?action=registrar; esa firma y ese POST final no se ejecutaron."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I09B", "I09C", "I09D", "I09E"]
    reason: "Contrato pre-sign exacto implementado solo en QA como PAdES local; no se ejecutaron la firma criptográfica real ni Registrar.do?action=registrar, por lo que E2E permanece pendiente."
    reviewed_at: "2026-08-18"
    next_gate: "E2E físico seguro limitado a comprobar aceptación de la firma PAdES; no efectuar presentación/registro final fuera de una autorización específica."
    notes: "El bootstrap observado no persistió un idTercero; los datos de contacto no se inventaron. Los endpoints firmaMovil observados son Storage/Retrieve auxiliares y no se modelan como endpoint de firma."

  - inventory_id: "ES-PUB-0135"
    surface_key: "lanzarote-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Lanzarote"
    surface_name: "Portal institucional del Cabildo Insular de Lanzarote"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.cabildodelanzarote.com"
    official_site: "https://www.cabildodelanzarote.com/"
    e_sede: "https://cabildodelanzarote.sedelectronica.es/info.0"
    entry_url: "https://www.cabildodelanzarote.com/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I10A", "I10B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0136"
    surface_key: "lanzarote-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Lanzarote"
    surface_name: "Sede electrónica del Cabildo Insular de Lanzarote"
    surface_type: "SEDE"
    origin: "https://cabildodelanzarote.sedelectronica.es"
    official_site: "https://cabildodelanzarote.sedelectronica.es/info.0"
    e_sede: "https://cabildodelanzarote.sedelectronica.es/info.0"
    entry_url: "https://cabildodelanzarote.sedelectronica.es/info.0"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "RECHECK_REQUIRED"
    inventory_status: "INACCESSIBLE"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["I10B"]
    reason: "El transporte de revisión informó 400 Redirect loop detected al abrir https://cabildodelanzarote.sedelectronica.es/info.0; no se siguió el ciclo ni se hicieron afirmaciones técnicas."
    reviewed_at: "2026-07-16"
    next_gate: "Revalidar la misma entrada HTTPS con un presupuesto cerrado de redirecciones y confirmar una respuesta estable antes de revisar operaciones."
    notes: "La existencia y titularidad proceden de la fuente oficial I10B; la indisponibilidad corresponde al transporte de revisión de este snapshot."

  - inventory_id: "ES-PUB-0137"
    surface_key: "gran-canaria-portal-institucional"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Gran Canaria"
    surface_name: "Portal institucional del Cabildo Insular de Gran Canaria"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://cabildo.grancanaria.com"
    official_site: "https://cabildo.grancanaria.com/"
    e_sede: "https://sede.grancanaria.com/"
    entry_url: "https://cabildo.grancanaria.com/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública de información institucional y acceso diferenciado a la sede electrónica."
    protocol_evidence: "La fuente acredita la entrada institucional y su enlace separado a la sede, no un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D12", "I11A", "I11B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

  - inventory_id: "ES-PUB-0138"
    surface_key: "gran-canaria-sede-electronica"
    administrative_level: "INSULAR"
    autonomous_community: "Canarias"
    province_or_municipality: "Las Palmas"
    institution_name: "Cabildo Insular de Gran Canaria"
    surface_name: "Sede electrónica del Cabildo Insular de Gran Canaria"
    surface_type: "SEDE"
    origin: "https://sede.grancanaria.com"
    official_site: "https://sede.grancanaria.com/"
    e_sede: "https://sede.grancanaria.com/"
    entry_url: "https://sede.grancanaria.com/sede-privado/instancia-general?inicio"
    procedure_page: "https://sede.grancanaria.com/informacion-instancia"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet / AutoFirma"
    protocol_family: "MINIAPPLET_LOCAL_PADES"
    signature_format: "PAdES"
    signature_algorithm: "SHA512withRSA"
    endpoint: "LOCAL_AUTOFIRMA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Firma PAdES local de solicitud; el perfil QA reproduce únicamente el contrato MiniApplet exacto publicado por la información pública de la Sede."
    protocol_evidence: "La página pública informacion-instancia publica SHA512withRSA y los endpoints AutoFirma; su recurso JSF AFIRMA/operaciones.js (SHA-256 6d1b19186f95f704a68e1a9ea87af87f678d597ebde7f33fbd1ad4fe7ac470cd el 2026-08-17) ejecuta MiniApplet.sign(dataB64, algoritmoFirma, PAdES, headless=true + filters=nonexpired:, success, error). La rama qualified depende de un número de serie no establecido por la evidencia pública y queda fuera del perfil."
    client_tls_auth: "NO"
    evidence_ids: ["I11B"]
    reason: "Contrato first-party pre-auth completo implementado en QA mediante PAdES local exacto; no se promueve a VERIFIED_E2E sin prueba física segura."
    reviewed_at: "2026-08-17"
    next_gate: "E2E físico seguro en Android del flujo de firma, sin presentar ni registrar una solicitud administrativa."
    notes: "La entrada pública del perfil es instancia-general?inicio. Durante la firma el bridge exige perfil activo, origen HTTPS exacto de sede.grancanaria.com y el tuple SHA512withRSA + PAdES + headless=true\nfilters=nonexpired:. No implementa la rama qualified con número de serie ni generaliza PAdES a otros perfiles."
```


### 7.5. Diputaciones provinciales [D06]

La cola cerrada D06 se materializa con una única superficie primaria por cada
una de sus 41 etiquetas, en el orden exacto del snapshot. Valladolid coincide
por exact origin con `ES-PUB-0015`; por ello conserva ese registro y recibe
solo provenance y evidencia provincial adicionales. Las otras 40 etiquetas
crean `ES-PUB-0139` a `ES-PUB-0178`. Los origins secundarios descubiertos
durante la revisión se conservan únicamente como evidencia y quedan diferidos
para una ola provincial secundaria; no crean registros en este snapshot.

```yaml
records:
  - inventory_id: "ES-PUB-0139"
    surface_key: "diputacion-alicante-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Comunidad Valenciana"
    province_or_municipality: "Alicante (provincia)"
    institution_name: "Diputación Provincial de Alicante"
    surface_name: "Solicitud General — Sede electrónica de Diputación de Alicante"
    surface_type: "SEDE"
    origin: "https://diputacionalicante.sedelectronica.es"
    official_site: "https://www.diputacionalicante.es"
    e_sede: "https://diputacionalicante.sedelectronica.es/"
    entry_url: "https://diputacionalicante.sedelectronica.es/catalog/tw/66192629-8b04-4cf8-a121-e2cb86cd45cb"
    procedure_page: "https://diputacionalicante.sedelectronica.es/catalog/t/66192629-8b04-4cf8-a121-e2cb86cd45cb"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "ALICANTE_SEDE_SOLICITUD_GENERAL_PUBLIC_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA integrada exclusivamente al inicio exacto de la Solicitud General vigente de la Sede electrónica de la Diputación de Alicante; autenticación, formulario, documentos, firma y presentación final quedan fuera del contrato implementado."
    protocol_evidence: "La Diputación delega actualmente en diputacionalicante.sedelectronica.es y la Solicitud General SIA 2407578 abre exactamente /catalog/tw/66192629-8b04-4cf8-a121-e2cb86cd45cb. El runtime autenticado alcanzó el wizard Identificación -> Formulario -> Documentos -> Firmar -> Acuse de recibo y avanzó de Identificación a Formulario mediante el POST Wicket intermedio permitido por RUNBOOK v2.4. El Formulario exige Email y Móvil antes de continuar. No se observó ni se infiere ABI de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP01A", "DP01B"]
    reason: "Perfil nuevo QA_ONLY limitado al launch exacto de la Solicitud General y sin capacidades SIGN, SELECT_CERTIFICATE o CLIENT_TLS_AUTH. La autenticación con certificado vía Cl@ve fue observada, pero no se modela como client-TLS propio de Alicante; el ABI de firma posterior permanece NO_VERIFICADO. Falta E2E físico."
    reviewed_at: "2026-08-18"
    next_gate: "Validar físicamente la navegación QA al inicio exacto de Solicitud General; ampliar autenticación o firma solo con un contrato específico independiente."
    notes: "RUNBOOK v2.4 permitió progresión administrativa acotada: Identificación se completó y Formulario devolvió como únicos requisitos adicionales observados Email y Móvil. No se inventaron datos de contacto, no se cargaron documentos, no se inicializó firma, no se realizó firma criptográfica, presentación final ni pago. certificateRules del perfil son metadatos estructurales inertes porque capabilities está vacío."

  - inventory_id: "ES-PUB-0140"
    surface_key: "diputacion-alava-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "País Vasco"
    province_or_municipality: "Álava (provincia)"
    institution_name: "Diputación Foral de Álava"
    surface_name: "Registro Electrónico Común — Diputación Foral de Álava"
    surface_type: "SEDE"
    origin: "https://egoitza.araba.eus"
    official_site: "https://web.araba.eus/es/home"
    e_sede: "https://egoitza.araba.eus/es/inicio"
    entry_url: "https://egoitza.araba.eus/izapidetu/at/01/es/0000301"
    procedure_page: "https://egoitza.araba.eus/es/inicio/tramites/fitxa/registro-electronico-comun"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "ALAVA_EGOITZA_REGISTRO_COMUN_QA_LAUNCH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Navegación QA al asistente vigente del Registro Electrónico Común de la Diputación Foral de Álava. El acceso autenticado con certificado alcanza el flujo protegido; la firma documental y la presentación final quedan fuera del contrato implementado."
    protocol_evidence: "El asistente vigente 0000301 fue revalidado el 2026-08-18: tras autenticación controlada alcanza /para-quien. El frontend first-party mantiene pasos de contacto, expediente, solicitud, adjuntos y Firmar y enviar; la inicialización de firma devuelve dinámicamente urlInicioFirma. No se infiere formato, algoritmo ni ABI del firmante."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP02A"]
    reason: "Perfil QA_ONLY limitado al inicio exacto del Registro Electrónico Común. La autenticación por certificado fue observada, pero no se declara CLIENT_TLS_AUTH propio del perfil ni capacidad SIGN. El signer downstream es dinámico; formato, algoritmo, callback y firma física siguen NO_VERIFICADO/E2E pendiente."
    reviewed_at: "2026-08-18"
    next_gate: "Validar físicamente el launch QA; ampliar el contrato de pre-firma o firma solo con evidencia exacta del signer dinámico, deteniéndose antes de firma criptográfica y presentación final."

  - inventory_id: "ES-PUB-0141"
    surface_key: "diputacion-albacete-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Albacete (provincia)"
    institution_name: "Diputación Provincial de Albacete"
    surface_name: "Sede electrónica de Diputación Provincial de Albacete"
    surface_type: "SEDE"
    origin: "https://sede.dipualba.es"
    official_site: "https://web.dipualba.es/"
    e_sede: "https://sede.dipualba.es/"
    entry_url: "https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567"
    procedure_page: "https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://identificacionssl.sedipualba.es/"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al trámite Registro Electrónico/Presentación Instancia General mediante el servidor SSL de identificación compartido de SEDIPUALBA; la firma documental posterior permanece fuera del contrato implementado."
    protocol_evidence: "La sede pública deriva el acceso al SEGEX de identificación y la página de opciones construye exactamente https://identificacionssl.sedipualba.es/?idtoken=TOKEN&idioma=es&entidad=02000, enlazando el mismo idtoken efímero de la fuente."
    client_tls_auth: "SI"
    evidence_ids: ["D06", "DP03A", "DP03B", "ALBACETE-INSTANCIA-2026-08-18", "ALBACETE-SSL-IDENT-2026-08-18"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA con host, path, entidad, idioma e idtoken source-target enlazado de forma exacta; sin E2E. No se infiere el algoritmo ni el formato de la firma documental posterior."
    reviewed_at: "2026-08-18"
    next_gate: "Verificación E2E separada del acceso con certificado y del paso de firma; mantener firma/presentación bloqueadas hasta evidencia independiente."

  - inventory_id: "ES-PUB-0142"
    surface_key: "diputacion-almeria-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Almería (provincia)"
    institution_name: "Diputación Provincial de Almería"
    surface_name: "Portal oficial de Diputación Provincial de Almería"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipalme.org"
    official_site: "https://www.dipalme.org"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipalme.org"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP04A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0143"
    surface_key: "diputacion-avila-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Ávila (provincia)"
    institution_name: "Diputación Provincial de Ávila"
    surface_name: "Diputación Provincial de Ávila — Instancia General"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://diputacionavila.sedelectronica.es"
    official_site: "https://www.diputacionavila.es"
    e_sede: "https://diputacionavila.sedelectronica.es/"
    entry_url: "https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5"
    procedure_page: "https://diputacionavila.sedelectronica.es/catalog/t/5161fa8d-970e-4b48-a506-b2ac34ceafe5"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Instancia General (SIA 1786719): perfil QA-only para la entrada telemática exacta y la navegación Cl@ve observada; el paso Firmar usa un submit Wicket probado hasta el límite pre-red."
    protocol_evidence: "Runtime autenticado llegó a 4. Firmar. El control llama wicketSubmitFormById con el componente viewFolderAdmissible:confirm. En v2.4 se activó el handler con intercepción previa a red: POST a la raíz, application/x-www-form-urlencoded, con id4c_hf_0 y viewFolderAdmissible:confirm; el POST fue abortado antes de alcanzar el servidor. Formato, algoritmo, signer y resultado post-firma siguen NO_VERIFICADO."
    client_tls_auth: "CONDICIONAL"
    evidence_ids: ["D06", "DP05A", "DP05B", "AVILA-INSTANCIA-2026-08-18", "AVILA-FIRMAR-2026-08-18"]
    reason: "Implementación limitada a lanzamiento/navegación QA del trámite exacto. No implementa ni afirma firma criptográfica: el request Firmar solo se observó y abortó antes de red; formato, algoritmo, callback y resultado permanecen NO_VERIFICADO."
    reviewed_at: "2026-08-18"
    next_gate: "Observar una sesión de pre-firma posterior al POST solo si puede garantizarse aborto antes de cualquier operación criptográfica; nunca completar firma ni registro final."

  - inventory_id: "ES-PUB-0144"
    surface_key: "diputacion-badajoz-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Extremadura"
    province_or_municipality: "Badajoz (provincia)"
    institution_name: "Diputación Provincial de Badajoz"
    surface_name: "Portal oficial de Diputación Provincial de Badajoz"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dip-badajoz.es"
    official_site: "https://www.dip-badajoz.es"
    e_sede: "https://sede.dip-badajoz.es"
    entry_url: "https://sede.dip-badajoz.es"
    procedure_page: "https://sede.dip-badajoz.es/sede/tramitacionElectronica.do?asu_mod_cod=67&asu_cod=68&asunto=68&aplcorreo=4&ent_id=10&idioma=1"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MINIAPPLET"
    protocol_family: "MINIAPPLET"
    signature_format: "CADES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "NO_APLICA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Instancia General de la Diputación de Badajoz mediante firma local CAdES-detached SHA256withRSA; la firma documental posterior queda fuera del contrato implementado."
    protocol_evidence: "La portada oficial enlaza la Sede; el catálogo vigente 2026 expone Instancia General. El login público invoca firmar(formLogin.shaLogin.value, errorText, '', 'TEXTO', 0, pulsarFirmarIdentificateCallback, pulsarFirmarIdentificateCallbackError, true), que en firmaDigital.js SHA-256 9e3dced47cdf634d120c4783b22ae0f9e00be3d42fad13429de38f5ef5921483 resuelve MiniApplet.sign con CAdES SHA256withRSA y extraProperties policy=FirmaAGE, headless=true, filters=nonexpired:true;authCert:true."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP06A", "DP06B", "BADAJOZ-INSTANCIA-2026-08-18", "BADAJOZ-LOGIN-2026-08-18"]
    reason: "Contrato público de login con certificado implementado en QA y limitado a autenticación: CAdES-detached SHA256withRSA sobre shaLogin con parámetros exactos y callback a firmaLogin. No se atribuye este tuple a la firma documental posterior; sin E2E."
    reviewed_at: "2026-08-18"
    next_gate: "Verificar E2E del acceso con certificado y, por separado, observar tras autenticación el contrato de firma documental de Instancia General sin ejecutar firma ni presentación."


  - inventory_id: "ES-PUB-0145"
    surface_key: "diputacion-barcelona-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Cataluña"
    province_or_municipality: "Barcelona (provincia)"
    institution_name: "Diputació de Barcelona"
    surface_name: "Diputació de Barcelona — Solicitud genérica 2057"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://seuelectronica.diba.cat"
    official_site: "https://www.diba.cat/es/"
    e_sede: "https://seuelectronica.diba.cat/es/"
    entry_url: "https://seuelectronica.diba.cat/es/sol%C2%B7licitud-gen%C3%A8rica"
    procedure_page: "https://seuelectronica.diba.cat/es/sol%C2%B7licitud-gen%C3%A8rica"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "VÀLid"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Solicitud genérica 2057 con identificación VÀLid; el perfil implementa únicamente la entrada exacta y la navegación autenticada previa a firma."
    protocol_evidence: "El runtime vigente de 2057 redirige desde tramits.diba.cat a valid.aoc.cat; la opción de certificado continúa a cert.valid.aoc.cat, cuyo TLS 1.2 emite CertificateRequest para RSA/ECDSA. El contrato de firma posterior a altaPeticio sigue NO_VERIFICADO."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DIBA-2057-2026-08-18", "DIBA-VALID-2026-08-18"]
    reason: "Perfil QA-only limitado a la entrada exacta de Solicitud genérica 2057 y sus orígenes de identificación observados; no afirma ABI, formato, algoritmo, endpoint ni aceptación E2E de firma/presentación."
    reviewed_at: "2026-08-18"
    next_gate: "Con una credencial VÀLid disponible, ejecutar el altaPeticio intermedio autorizado y observar el estado pre-firma; detenerse antes de firma criptográfica y registro final."

  - inventory_id: "ES-PUB-0146"
    surface_key: "diputacion-burgos-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Burgos (provincia)"
    institution_name: "Diputación Provincial de Burgos"
    surface_name: "Portal oficial de Diputación Provincial de Burgos"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://burgos.es"
    official_site: "https://burgos.es"
    e_sede: "https://sede.diputaciondeburgos.es"
    entry_url: "https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO"
    procedure_page: "https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / STAAutofirmaLote"
    protocol_family: "AUTOSCRIPT_STA_BATCH_TRIFASICO"
    signature_format: "CAdES / PAdES / XAdES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "URLs runtime bajo /sta/AutofirmaLote/{presign,postsign,getdata}; valores concretos suministrados por backend"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La Instancia Genérica pública del Registro electrónico exige certificado reconocido y firma electrónica y ofrece AutoFirma; el soporte queda limitado al origin exacto registro.diputaciondeburgos.es."
    protocol_evidence: "El Registro público carga AutoScript, sta-autofirma-lote.js y webAppsFwk.js byte-idénticos al seam STA ya validado; firmarLote devuelve PRESENTAR_FIRMA y el helper fija SHA256withRSA/CAdES/sign. El servlet público confirma /{op}/{operacionId}."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP08A", "DP08B", "DP08C", "DP08D", "DP08E", "DP08F", "DP08G", "DP08H"]
    reason: "Implementación QA limitada a https://registro.diputaciondeburgos.es y al contrato STA observado; no se realizó autenticación, certificado real, firma real ni E2E administrativo."
    reviewed_at: "2026-08-16"
    next_gate: "Mantener QA_ONLY hasta una verificación E2E autorizada con el flujo real; no inferir soporte para burgos.es, sede.diputaciondeburgos.es u otros hosts."

  - inventory_id: "ES-PUB-0147"
    surface_key: "diputacion-caceres-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Extremadura"
    province_or_municipality: "Cáceres (provincia)"
    institution_name: "Diputación Provincial de Cáceres"
    surface_name: "Portal oficial de Diputación Provincial de Cáceres"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dip-caceres.es"
    official_site: "https://www.dip-caceres.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dip-caceres.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP09A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0148"
    surface_key: "diputacion-cadiz-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Cádiz (provincia)"
    institution_name: "Diputación Provincial de Cádiz"
    surface_name: "Portal oficial de Diputación Provincial de Cádiz"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipucadiz.es"
    official_site: "https://www.dipucadiz.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipucadiz.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública del portal institucional; la evidencia de certificado/firma corresponde a una superficie secundaria diferida."
    protocol_evidence: "La fuente secundaria acredita otra superficie oficial de la institución; no prueba requisitos ni contrato técnico para este origin."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP10A", "DP10B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0149"
    surface_key: "diputacion-castellon-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Comunidad Valenciana"
    province_or_municipality: "Castellón (provincia)"
    institution_name: "Diputació de Castelló"
    surface_name: "Portal oficial de Diputació de Castelló"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipcas.es"
    official_site: "https://www.dipcas.es/es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipcas.es/es/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP11A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0150"
    surface_key: "diputacion-ciudad-real-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Ciudad Real (provincia)"
    institution_name: "Diputación Provincial de Ciudad Real"
    surface_name: "Portal oficial de Diputación Provincial de Ciudad Real"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipucr.es"
    official_site: "https://www.dipucr.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipucr.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP12A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0151"
    surface_key: "diputacion-cordoba-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Córdoba (provincia)"
    institution_name: "Diputación Provincial de Córdoba"
    surface_name: "Portal oficial de Diputación Provincial de Córdoba"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipucordoba.es"
    official_site: "https://www.dipucordoba.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipucordoba.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP13A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0152"
    surface_key: "diputacion-a-coruna-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Galicia"
    province_or_municipality: "A Coruña (provincia)"
    institution_name: "Deputación da Coruña"
    surface_name: "Portal oficial de Deputación da Coruña"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dacoruna.gal"
    official_site: "https://www.dacoruna.gal/portada"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dacoruna.gal/portada"
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
    operation_summary: "Un recurso tributario oficial documenta certificado y firma en ese flujo limitado; no se generaliza al portal."
    protocol_evidence: "La evidencia se limita al recurso tributario documentado y no publica contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP14A", "DP14B"]
    reason: "Certificado y firma son condicionales solo para el flujo citado; procedimiento general y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0153"
    surface_key: "diputacion-cuenca-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Cuenca (provincia)"
    institution_name: "Diputación Provincial de Cuenca"
    surface_name: "Portal oficial de Diputación Provincial de Cuenca"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipucuenca.es"
    official_site: "https://www.dipucuenca.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipucuenca.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública del portal institucional; la evidencia de certificado/firma corresponde a una superficie secundaria diferida."
    protocol_evidence: "La fuente secundaria acredita otra superficie oficial de la institución; no prueba requisitos ni contrato técnico para este origin."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP15A", "DP15B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0154"
    surface_key: "diputacion-girona-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Cataluña"
    province_or_municipality: "Girona (provincia)"
    institution_name: "Diputació de Girona"
    surface_name: "Portal oficial de Diputació de Girona"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.ddgi.cat"
    official_site: "https://www.ddgi.cat/web/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.ddgi.cat/web/"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta pública del portal institucional; la evidencia de certificado/firma corresponde a una superficie secundaria diferida."
    protocol_evidence: "La fuente secundaria acredita otra superficie oficial de la institución; no prueba requisitos ni contrato técnico para este origin."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP16A", "DP16B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0155"
    surface_key: "diputacion-granada-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Granada (provincia)"
    institution_name: "Diputación Provincial de Granada"
    surface_name: "Portal oficial de Diputación Provincial de Granada"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipgra.es"
    official_site: "https://www.dipgra.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipgra.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "NO_VERIFICADO"
    protocol_evidence: "NO_VERIFICADO"
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP17A"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0156"
    surface_key: "diputacion-guadalajara-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Guadalajara (provincia)"
    institution_name: "Diputación Provincial de Guadalajara"
    surface_name: "Sede electrónica de Diputación Provincial de Guadalajara"
    surface_type: "SEDE"
    origin: "https://dguadalajara.sedelectronica.es"
    official_site: "https://dguadalajara.sedelectronica.es"
    e_sede: "https://dguadalajara.sedelectronica.es"
    entry_url: "https://dguadalajara.sedelectronica.es"
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
    operation_summary: "La sede documenta identificación condicionada con certificado; no acredita firma obligatoria en un trámite concreto."
    protocol_evidence: "La evidencia delimita identificación electrónica, no un contrato de firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP18A", "DP18B"]
    reason: "La identificación con certificado no permite inferir firma, formato, algoritmo, endpoint, cliente JS ni TLS cliente."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0157"
    surface_key: "diputacion-gipuzkoa-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "País Vasco"
    province_or_municipality: "Gipuzkoa (provincia)"
    institution_name: "Diputación Foral de Gipuzkoa"
    surface_name: "Sede electrónica de Diputación Foral de Gipuzkoa"
    surface_type: "SEDE"
    origin: "https://egoitza.gipuzkoa.eus"
    official_site: "https://egoitza.gipuzkoa.eus/es/"
    e_sede: "https://egoitza.gipuzkoa.eus"
    entry_url: "https://egoitza.gipuzkoa.eus/es/"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP19A", "DP19B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0158"
    surface_key: "diputacion-huelva-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Huelva (provincia)"
    institution_name: "Diputación Provincial de Huelva"
    surface_name: "Portal oficial de Diputación Provincial de Huelva"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.diphuelva.es"
    official_site: "https://www.diphuelva.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.diphuelva.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "El portal oficial enlaza verificación de firma; no acredita que un trámite exija certificado o firma."
    protocol_evidence: "La referencia a verificación de firma no prueba un requisito de operación ni un contrato técnico."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP20A"]
    reason: "No se generaliza el enlace de verificación a certificado o firma obligatorios; seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0159"
    surface_key: "diputacion-huesca-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Aragón"
    province_or_municipality: "Huesca (provincia)"
    institution_name: "Diputación Provincial de Huesca"
    surface_name: "Portal oficial de Diputación Provincial de Huesca"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dphuesca.es"
    official_site: "https://www.dphuesca.es"
    e_sede: "https://ovc24.dphuesca.es"
    entry_url: "https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME"
    procedure_page: "https://ovc24.dphuesca.es/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=OVC_PORTAFIRMAS"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript / STAAutofirmaLote"
    protocol_family: "AUTOSCRIPT_STA_BATCH_TRIFASICO"
    signature_format: "CAdES / PAdES / XAdES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "URLs runtime bajo /sta/AutofirmaLote/{presign,postsign,getdata}; valores concretos suministrados por backend"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La OVC de la Diputación de Huesca expone Portafirmas y un contrato STA AutoFirma por lotes; el soporte queda limitado al origin exacto de la OVC."
    protocol_evidence: "La OVC pública carga AutoScript, sta-autofirma-lote.js y webAppsFwk.js byte-idénticos al seam STA ya validado; firmarLote usa SHA256withRSA/CAdES/sign y devuelve PRESENTAR_FIRMA. El servlet público confirma la gramática /{op}/{operacionId}."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP21A", "DP21B", "DP21C", "DP21D", "DP21E", "DP21F", "DP21G", "DP21H", "DP21I"]
    reason: "Implementación QA limitada a https://ovc24.dphuesca.es y al contrato STA observado; no se realizó autenticación, certificado real, firma real ni E2E administrativo."
    reviewed_at: "2026-08-16"
    next_gate: "Mantener QA_ONLY hasta una verificación E2E autorizada con el flujo real; no inferir soporte para otros hosts de la Diputación."

  - inventory_id: "ES-PUB-0160"
    surface_key: "diputacion-jaen-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Jaén (provincia)"
    institution_name: "Diputación Provincial de Jaén"
    surface_name: "Sede electrónica de Diputación Provincial de Jaén"
    surface_type: "SEDE"
    origin: "https://sede.dipujaen.es"
    official_site: "https://sede.dipujaen.es"
    e_sede: "https://sede.dipujaen.es"
    entry_url: "https://sede.dipujaen.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP22A", "DP22B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0161"
    surface_key: "diputacion-leon-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "León (provincia)"
    institution_name: "Diputación Provincial de León"
    surface_name: "Sede electrónica de Diputación Provincial de León"
    surface_type: "SEDE"
    origin: "https://sede.dipuleon.es"
    official_site: "https://sede.dipuleon.es"
    e_sede: "https://sede.dipuleon.es"
    entry_url: "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270"
    procedure_page: "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://identificacionssl.sedipualba.es/"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado al trámite Instancia General mediante el servidor SSL de identificación compartido de SEDIPUALBA; la firma documental posterior permanece fuera del contrato implementado."
    protocol_evidence: "La página pública de opciones construye exactamente https://identificacionssl.sedipualba.es/?idtoken=TOKEN&idioma=es&entidad=24000 y la navegación usa el mismo idtoken de la sesión pública. Una petición GET sin certificado provoca renegociación TLS 1.2 y CertificateRequest antes de devolver la respuesta; no se proporcionó certificado."
    client_tls_auth: "SI"
    evidence_ids: ["D06", "DP23A", "LEON-INSTANCIA-2026-08-16", "LEON-SSL-IDENT-2026-08-16"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA con host, path, entidad, idioma e idtoken source-target enlazado de forma exacta; sin E2E. No se infiere el algoritmo ni el formato de la firma documental posterior."
    reviewed_at: "2026-08-16"
    next_gate: "Verificación E2E separada del acceso con certificado y del paso de firma; mantener firma/presentación bloqueadas hasta evidencia independiente."

  - inventory_id: "ES-PUB-0162"
    surface_key: "diputacion-lleida-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Cataluña"
    province_or_municipality: "Lleida (provincia)"
    institution_name: "Diputació de Lleida"
    surface_name: "Sede electrónica de Diputació de Lleida"
    surface_type: "SEDE"
    origin: "https://seu.diputaciolleida.cat"
    official_site: "https://seu.diputaciolleida.cat"
    e_sede: "https://seu.diputaciolleida.cat"
    entry_url: "https://seu.diputaciolleida.cat"
    procedure_page: "https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MINIAPPLET"
    protocol_family: "MINIAPPLET"
    signature_format: "CADES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "NO_APLICA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Diputació de Lleida mediante firma local CAdES-detached SHA256withRSA."
    protocol_evidence: "firmar(formLogin.shaLogin.value, errorText, '', 'TEXTO', 0, pulsarFirmarIdentificateCallback, pulsarFirmarIdentificateCallbackError, true) invoca MiniApplet.sign con CAdES SHA256withRSA y extraProperties policy=FirmaAGE, headless=true, filters=nonexpired:true;authCert:true."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP24A", "DP24B", "LLEIDA-LOGIN-2026-08-16"]
    reason: "Contrato público de login con certificado implementado en QA: firma local CAdES-detached SHA256withRSA sobre el shaLogin del formulario con extraProperties exactas (policy=FirmaAGE, headless=true, filters=nonexpired:true;authCert:true) y retorno de resFirma al callback. Sin pruebas E2E en dispositivo físico."
    reviewed_at: "2026-07-16"
    next_gate: "Verificar E2E en dispositivo físico que el callback completa la autenticación en la Sede electrónica de la Diputació de Lleida sin emitir transacciones no deseadas."

  - inventory_id: "ES-PUB-0163"
    surface_key: "diputacion-lugo-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Galicia"
    province_or_municipality: "Lugo (provincia)"
    institution_name: "Deputación de Lugo"
    surface_name: "Sede electrónica de Deputación de Lugo"
    surface_type: "SEDE"
    origin: "https://sede.deputacionlugo.org"
    official_site: "https://sede.deputacionlugo.org"
    e_sede: "https://sede.deputacionlugo.org"
    entry_url: "https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp"
    procedure_page: "https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "AutoScript 1.8.0 / clientSigner"
    protocol_family: "AUTOSCRIPT_XML_BATCH_TRIFASICO_PREHASH"
    signature_format: "CAdES"
    signature_algorithm: "SHA256withRSA"
    endpoint: "Runtime multi-node: /opencms/clientsigner/{BatchPresigner,BatchPostsigner}/service/{JSESSIONID}; query POST xml/certs/tridata"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "La página pública de acceso con certificado construye un lote CAdES explícito sobre hash SHA-256 y lo entrega a AutoScript.signBatch; soporte limitado al acceso con certificado, origin exacto y un único elemento observado."
    protocol_evidence: "autenticacion.jsp publica authenticate con SHA256withRSA/CAdES/hashToSign; clientSigner.js publica signBatch, BatchPresigner/BatchPostsigner multi-node, XML signbatch, precalculatedHashAlgorithm=SHA-256 y callback signresult. El wire PRE/PK1 se contrastó con el cliente AutoFirma oficial."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP25A", "DP25B", "DP25C", "DP25D", "DP25E", "DP25F"]
    reason: "IMPLEMENTED_NOT_E2E: contrato QA limitado al acceso público con certificado, un lote CAdES explícito de un hash SHA-256 y endpoints multi-node exactos; no se realizó autenticación, firma real, recuperación de firma ni envío administrativo."
    reviewed_at: "2026-08-16"
    next_gate: "E2E solo con entorno de prueba autorizado y credenciales/certificado de prueba; no usar identidad real ni producir efectos administrativos."

  - inventory_id: "ES-PUB-0164"
    surface_key: "diputacion-malaga-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Málaga (provincia)"
    institution_name: "Diputación Provincial de Málaga"
    surface_name: "Sede electrónica de Diputación Provincial de Málaga"
    surface_type: "SEDE"
    origin: "https://sede.malaga.es"
    official_site: "https://sede.malaga.es"
    e_sede: "https://sede.malaga.es"
    entry_url: "https://sede.malaga.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP26A", "DP26B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0165"
    surface_key: "diputacion-ourense-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Galicia"
    province_or_municipality: "Ourense (provincia)"
    institution_name: "Deputación de Ourense"
    surface_name: "Sede electrónica de Deputación de Ourense"
    surface_type: "SEDE"
    origin: "https://sede.depourense.es"
    official_site: "https://sede.depourense.es"
    e_sede: "https://sede.depourense.es"
    entry_url: "https://sede.depourense.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP27A", "DP27B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0166"
    surface_key: "diputacion-palencia-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Palencia (provincia)"
    institution_name: "Diputación Provincial de Palencia"
    surface_name: "Sede electrónica de Diputación Provincial de Palencia"
    surface_type: "SEDE"
    origin: "https://sede.diputaciondepalencia.es"
    official_site: "https://sede.diputaciondepalencia.es"
    e_sede: "https://sede.diputaciondepalencia.es"
    entry_url: "https://sede.diputaciondepalencia.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP28A", "DP28B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0167"
    surface_key: "diputacion-pontevedra-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Galicia"
    province_or_municipality: "Pontevedra (provincia)"
    institution_name: "Deputación de Pontevedra"
    surface_name: "Sede electrónica de Deputación de Pontevedra"
    surface_type: "SEDE"
    origin: "https://sede.depo.gal"
    official_site: "https://sede.depo.gal"
    e_sede: "https://sede.depo.gal"
    entry_url: "https://sede.depo.gal"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP29A", "DP29B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0168"
    surface_key: "diputacion-salamanca-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Salamanca (provincia)"
    institution_name: "Diputación de Salamanca"
    surface_name: "Sede electrónica de Diputación de Salamanca"
    surface_type: "SEDE"
    origin: "https://sede.diputaciondesalamanca.gob.es"
    official_site: "https://sede.diputaciondesalamanca.gob.es"
    e_sede: "https://sede.diputaciondesalamanca.gob.es"
    entry_url: "https://sede.diputaciondesalamanca.gob.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP30A", "DP30B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0169"
    surface_key: "diputacion-segovia-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Segovia (provincia)"
    institution_name: "Diputación de Segovia"
    surface_name: "Sede electrónica de Diputación de Segovia"
    surface_type: "SEDE"
    origin: "https://sede.dipsegovia.es"
    official_site: "https://sede.dipsegovia.es"
    e_sede: "https://sede.dipsegovia.es"
    entry_url: "https://sede.dipsegovia.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP31A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0170"
    surface_key: "diputacion-sevilla-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Andalucía"
    province_or_municipality: "Sevilla (provincia)"
    institution_name: "Diputación de Sevilla"
    surface_name: "Sede electrónica de Diputación de Sevilla"
    surface_type: "SEDE"
    origin: "https://sedeelectronicadipusevilla.es"
    official_site: "https://sedeelectronicadipusevilla.es"
    e_sede: "https://sedeelectronicadipusevilla.es"
    entry_url: "https://sedeelectronicadipusevilla.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP32A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0171"
    surface_key: "diputacion-soria-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Soria (provincia)"
    institution_name: "Diputación de Soria"
    surface_name: "Sede electrónica de Diputación de Soria"
    surface_type: "SEDE"
    origin: "https://sede.dipsoria.es"
    official_site: "https://sede.dipsoria.es"
    e_sede: "https://sede.dipsoria.es"
    entry_url: "https://sede.dipsoria.es"
    procedure_page: "NO_VERIFICADO"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "RECHECK_REQUIRED"
    inventory_status: "INACCESSIBLE"
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP33A", "DP33B"]
    reason: "La revalidación HTTPS directa falla por certificado TLS de validez normal expirado el 2026-06-21; la evidencia documental se conserva sin afirmar accesibilidad actual ni contrato técnico."
    reviewed_at: "2026-07-16"
    next_gate: "Revalidar la cadena TLS normal sin --insecure; después revisar un procedimiento vigente y su contrato exacto."

  - inventory_id: "ES-PUB-0172"
    surface_key: "diputacion-tarragona-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Cataluña"
    province_or_municipality: "Tarragona (provincia)"
    institution_name: "Diputació de Tarragona"
    surface_name: "Sede electrónica de Diputació de Tarragona"
    surface_type: "SEDE"
    origin: "https://seuelectronica.dipta.cat"
    official_site: "https://seuelectronica.dipta.cat"
    e_sede: "https://seuelectronica.dipta.cat"
    entry_url: "https://seuelectronica.dipta.cat"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP34A", "DP34B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0173"
    surface_key: "diputacion-teruel-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Aragón"
    province_or_municipality: "Teruel (provincia)"
    institution_name: "Diputación Provincial de Teruel"
    surface_name: "Sede electrónica de Diputación Provincial de Teruel"
    surface_type: "SEDE"
    origin: "https://dpteruel.sedelectronica.es"
    official_site: "https://dpteruel.sedelectronica.es"
    e_sede: "https://dpteruel.sedelectronica.es"
    entry_url: "https://dpteruel.sedelectronica.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP35A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0174"
    surface_key: "diputacion-toledo-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Toledo (provincia)"
    institution_name: "Diputación Provincial de Toledo"
    surface_name: "Sede electrónica de Diputación Provincial de Toledo"
    surface_type: "SEDE"
    origin: "https://diputacion.toledo.gob.es"
    official_site: "https://diputacion.toledo.gob.es"
    e_sede: "https://diputacion.toledo.gob.es"
    entry_url: "https://diputacion.toledo.gob.es/SIGEM_RegistroTelematicoWeb/realizarSolicitudRegistro.do?tramiteId=TRAM_31"
    procedure_page: "https://diputacion.toledo.gob.es/procedimientos/1"
    certificate_required: "SI"
    signature_required: "CONDICIONAL"
    js_client: "NO_APLICA"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://diputacion.toledo.gob.es:843/SIGEM_AutenticacionWeb/validacionCertificado.do"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Solicitud de propósito general mediante TLS cliente SIGEM en el endpoint exacto :843; firma y presentación permanecen bloqueadas."
    protocol_evidence: "La Sede enlaza de forma explícita la Solicitud de propósito general con certificado; el flujo público deriva a seleccionEntidad.do y su JavaScript navega al endpoint exacto :843. El handshake TLS solicita certificado cliente con lista de CA y, sin certificado, devuelve un error controlado de certificado de usuario."
    client_tls_auth: "SI"
    evidence_ids: ["D06", "DP36A", "DP36B", "TOLEDO-PROCEDURES-2026-08-13", "TOLEDO-REGISTRY-START-2026-08-13", "TOLEDO-CERT-REDIRECT-2026-08-13", "TOLEDO-CLIENT-TLS-2026-08-13"]
    reason: "CLIENT_TLS_AUTH implementado solo en QA para el source, host, ruta y puerto exactos; sin E2E. La firma documental y la presentación administrativa posteriores no se implementan ni se infieren."
    reviewed_at: "2026-08-13"
    next_gate: "Verificar E2E únicamente el acceso TLS cliente en dispositivo físico autorizado, sin firmar ni presentar una solicitud administrativa, antes de cualquier promoción release."

  - inventory_id: "ES-PUB-0175"
    surface_key: "diputacion-valencia-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Comunidad Valenciana"
    province_or_municipality: "Valencia (provincia)"
    institution_name: "Diputació de València"
    surface_name: "Diputació de València — Portafirmas con certificado"
    surface_type: "SEDE"
    origin: "https://portafirmas.dival.es"
    official_site: "https://www.sede.dival.es"
    e_sede: "https://www.sede.dival.es"
    entry_url: "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml"
    procedure_page: "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml"
    certificate_required: "SI"
    signature_required: "NO"
    js_client: "AutoScript"
    protocol_family: "AUTOSCRIPT_LOCAL"
    signature_format: "NO_APLICA"
    signature_algorithm: "NO_APLICA"
    endpoint: "NO_APLICA"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Selección local de certificado para el acceso al Portafirmas de la Diputació de València."
    protocol_evidence: "AutoScript.selectCertificate(\"filters=keyusage.nonrepudiation:true;nonexpired:true\\nheadless=true\", exitoCallback, errorCallback); exitoCallback recibe certB64."
    client_tls_auth: "NO"
    evidence_ids: ["D06", "DP37A", "DP37B", "VALENCIA-SELECTCERT-2026-08-15"]
    reason: "Contrato público preautenticación implementado en QA: selección local y devolución de certB64 al callback exitoCallback; las llamadas comentadas de firma CAdES/PAdES y el posterior POST JSF permanecen bloqueados. Falta E2E físico seguro."
    reviewed_at: "2026-08-15"
    next_gate: "Validar en dispositivo que el callback recibe certB64 y que la entrega no realiza envíos ni firmas involuntarias, sin efectuar presentación administrativa."
    notes: "Evidencia first-party 2026-08-15: login.xhtml, autoscript.js, filtros.js y util.js; sin POST, autenticación, firma ni bypass TLS durante la investigación."

  - inventory_id: "ES-PUB-0176"
    surface_key: "diputacion-bizkaia-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "País Vasco"
    province_or_municipality: "Bizkaia (provincia)"
    institution_name: "Diputación Foral de Bizkaia"
    surface_name: "Sede electrónica de Diputación Foral de Bizkaia"
    surface_type: "SEDE"
    origin: "https://www.ebizkaia.eus"
    official_site: "https://www.ebizkaia.eus"
    e_sede: "https://www.ebizkaia.eus"
    entry_url: "https://www.ebizkaia.eus"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP39A", "DP39B"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0177"
    surface_key: "diputacion-zamora-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla y León"
    province_or_municipality: "Zamora (provincia)"
    institution_name: "Diputación Provincial de Zamora"
    surface_name: "Sede electrónica de Diputación Provincial de Zamora"
    surface_type: "SEDE"
    origin: "https://diputaciondezamora.sedelectronica.es"
    official_site: "https://diputaciondezamora.sedelectronica.es"
    e_sede: "https://diputaciondezamora.sedelectronica.es"
    entry_url: "https://diputaciondezamora.sedelectronica.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP40A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0178"
    surface_key: "diputacion-zaragoza-sede"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Aragón"
    province_or_municipality: "Zaragoza (provincia)"
    institution_name: "Diputación Provincial de Zaragoza"
    surface_name: "Sede electrónica de Diputación Provincial de Zaragoza"
    surface_type: "SEDE"
    origin: "https://dpz.sedelectronica.es"
    official_site: "https://dpz.sedelectronica.es"
    e_sede: "https://dpz.sedelectronica.es"
    entry_url: "https://dpz.sedelectronica.es"
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
    operation_summary: "La evidencia oficial documenta uso condicionado de certificado y firma electrónica; no se generaliza a todos los trámites."
    protocol_evidence: "La mención portal-specific es documental y delimitada; no publica contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D06", "DP41A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."
```

### 7.6. Superficie sectorial autonómica — Carné Joven Madrid

Este registro delimita la entrada oficial 53F1 de Cuenta Digital. La ficha
publica la acción «Firmar y enviar» y permite identificación sin certificado;
la SPA pública solo acredita lookup y redirect autenticado. No se infieren
cliente, protocolo, formato, algoritmo ni endpoint de presentación.

```yaml
records:
  - inventory_id: "ES-PUB-0179"
    surface_key: "comunidad-madrid-cuenta-digital-carne-joven"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Comunidad de Madrid"
    province_or_municipality: "NO_APLICA"
    institution_name: "Comunidad de Madrid"
    surface_name: "Cuenta Digital — Carné Joven 53F1"
    surface_type: "FRONTEND_TRAMITE"
    origin: "https://digital.comunidad.madrid"
    official_site: "https://digital.comunidad.madrid/ext/53F1"
    e_sede: "https://sede.comunidad.madrid/"
    entry_url: "https://digital.comunidad.madrid/ext/53F1"
    procedure_page: "https://sede.comunidad.madrid/autorizaciones-licencias-permisos-carnes/carne-joven"
    certificate_required: "CONDICIONAL"
    signature_required: "SI"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Solicitud electrónica y obtención de la versión digital del Carné Joven de la Comunidad de Madrid mediante Cuenta Digital."
    protocol_evidence: "P18C-P18F acreditan una SPA requireAuth, lookup tramites/{id} y redirect mediante data.url_tramitacion; no acreditan el contrato de firma o presentación de 53F1."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["P18", "P18A", "P18B", "P18C", "P18D", "P18E", "P18F"]
    reason: "La ficha acredita «Firmar y enviar» y certificado/DNIe solo como opción; el lookup autenticado de 53F1 no revela el endpoint de presentación, payload, callback, formato ni algoritmo. La cadena JS revisada solo acredita lookup/redirect."
    reviewed_at: "2026-07-16"
    next_gate: "Observar de forma controlada el flujo autenticado 53F1 y conservar evidencia sanitizada de endpoint, payload y callback exactos antes de evaluar cualquier promoción."
```

### 7.7. Carné Joven Europeo de Andalucía — autenticación TLS

El contrato verificado se limita al acceso con certificado. La facade ws235 es
compartida, por lo que el profile no confía en el host de forma aislada: exige
la transición top-level exacta desde `CallAuthenticationServlet`, valida los
parámetros fijos de Carné Joven y concede una autorización one-shot con TTL.

```yaml
records:
  - inventory_id: "ES-PUB-0180"
    surface_key: "junta-andalucia-carne-joven"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Andalucía"
    province_or_municipality: "NO_APLICA"
    institution_name: "Instituto Andaluz de la Juventud"
    surface_name: "Carné Joven Europeo de Andalucía"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://ws104.juntadeandalucia.es"
    official_site: "https://ws101.juntadeandalucia.es/portalcj/"
    e_sede: "https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html"
    entry_url: "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
    procedure_page: "https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "No aplica; el acceso verificado usa selección de certificado mediante TLS de cliente"
    protocol_family: "TLS_CLIENT_CERTIFICATE_AUTHENTICATION"
    signature_format: "NO_APLICA_AL_LOGIN; firma posterior NO_VERIFICADO"
    signature_algorithm: "Negociado por TLS; firma posterior NO_VERIFICADO"
    endpoint: "https://ws235.juntadeandalucia.es/authenticationFacade"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_E2E"
    operation_summary: "Entrada con certificado mediante facade TLS compartida y retorno exacto a ws104."
    protocol_evidence: "Autenticación CLIENT_TLS_AUTH verificada E2E en dispositivo físico (2026-07-21, commit dc3c231); Zona privada y Solicitar Carné Joven alcanzaron confirmación nativa y autenticación exitosa."
    client_tls_auth: "VERIFIED_E2E"
    evidence_ids: ["P19", "P19A", "P19B", "P19C", "P19D"]
    reason: "CLIENT_TLS_AUTH verificado E2E en dispositivo físico para Zona privada y Solicitar Carné Joven; la firma, AutoFirma, presentación jurídica o solicitud completada posterior no se afirman como E2E."
    reviewed_at: "2026-07-21"
    next_gate: "Research autenticado post-login deteniéndose antes de cualquier presentación jurídica."
```


### 7.8. Junta de Andalucía — Oficina Virtual

La ficha materializa el mismo flujo de login ya delimitado por el profile
`junta-ofvirtual`. La aceptación E2E se limita a la autenticación CAdES y no
amplía el alcance a firma documental, presentación jurídica ni otros trámites.

```yaml
records:
  - inventory_id: "ES-PUB-0181"
    surface_key: "junta-andalucia-ofvirtual"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Andalucía"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Andalucía"
    surface_name: "Junta de Andalucía — Oficina Virtual"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://ws072.juntadeandalucia.es"
    official_site: "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
    procedure_page: "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "MiniApplet / @firma"
    protocol_family: "MINIAPPLET_TRIPHASE"
    signature_format: "CAdES"
    signature_algorithm: "SHA1withRSA"
    endpoint: "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService"
    discovery_state: "REVIEWED"
    inventory_status: "VERIFIED_E2E"
    operation_summary: "Acceso con certificado a la Oficina Virtual"
    protocol_evidence: "MiniApplet.sign 1.5, PRE/POST tri-phase, callback y submit aceptados por el login real; evidencia sanitizada en P20."
    client_tls_auth: "NO_EN_CONTORNO_OBSERVADO"
    evidence_ids: ["LIVE-JUNTA-OFVIRTUAL-2026-07-22", "E2E-JUNTA-OFVIRTUAL-2026-07-29"]
    reason: "El portal real aceptó la firma CAdES de autenticación y abrió la sesión interna; verificación limitada al login observado."
    reviewed_at: "2026-07-29"
    next_gate: "No ampliar el alcance sin evidencia portal-specific y consentimiento para una operación distinta del login."
```

### 7.9. Ministerio de Educación — convocatoria 46

La entrada oficial conserva navegación exacta y un contrato QA-only de
`CLIENT_TLS_AUTH` para el salto Cl@ve/eIdentifier observado. El profile no
concede firma porque no existe evidencia suficiente del ABI downstream,
callback ni contrato de presentación.

```yaml
records:
  - inventory_id: "ES-PUB-0182"
    surface_key: "educacion-convocatoria-46"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Educación, Formación Profesional y Deportes"
    surface_name: "Ministerio de Educación — Convocatoria 46"
    surface_type: "FRONTEND_TRAMITE"
    origin: "https://sede.educacion.gob.es"
    official_site: "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46"
    e_sede: "https://sede.educacion.gob.es/"
    entry_url: "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46"
    procedure_page: "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46"
    certificate_required: "SI"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "CLIENT_TLS_AUTH"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Acceso con certificado a la Convocatoria 46 mediante la pasarela Cl@ve/eIdentifier; la firma documental posterior no está implementada."
    protocol_evidence: "La entrada oficial POSTea a claveEduPeticion.form; la pasarela genera ServiceProvider, el selector AFIRMA progresa por ServiceRedirect y entrega una forma POST exacta a pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen. El host TLS 1.2 emite CertificateRequest sin lista de CA; no se observó ABI de firma."
    client_tls_auth: "SI"
    evidence_ids: ["LIVE-EDUCACION-ENTRY-2026-07-22", "EDUCACION-CONV46-CLIENTTLS-2026-08-19"]
    reason: "Implementación QA-only limitada a CLIENT_TLS_AUTH; firma, formato, algoritmo, callback y aceptación E2E permanecen no verificados."
    reviewed_at: "2026-08-19"
    next_gate: "Si se requiere ampliar SIGN, autenticar de forma controlada y avanzar hasta el primer pre-sign observable, deteniéndose antes de la firma privada y la presentación final."
  - inventory_id: "ES-PUB-0183"
    surface_key: "castilla-la-mancha-certificate-login-probe"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Comunidades de Castilla-La Mancha"
    surface_name: "JCCM — acceso público con certificado"
    surface_type: "PORTAL_AUTENTICACION"
    origin: "https://ventanillaelectronica.jccm.es"
    official_site: "https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml"
    e_sede: "https://www.jccm.es/"
    entry_url: "https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml"
    procedure_page: "https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml"
    certificate_required: "SI"
    signature_required: "SI"
    js_client: "AutoScript / MiniApplet"
    protocol_family: "AUTOSCRIPT_MINIAPPLET_LOCAL_CADES"
    signature_format: "CAdES"
    signature_algorithm: "SHA1withRSA"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "IMPLEMENTED_NOT_E2E"
    operation_summary: "Validación pública de acceso con certificado mediante AutoScript/MiniApplet.sign sobre payload fijo ABCDE."
    protocol_evidence: "La página pública y su MiniApplet first-party fijan AutoScript/MiniApplet.sign con SHA1withRSA, CAdES, propiedades null y payload Base64 QUJDREU= (ABCDE); no se realizó FORMPROC.submit ni autenticación o sesión."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["G38-JCCM-CERTIFICATE-LOGIN-2026-08-09"]
    reason: "Contrato AutoScript/MiniApplet CAdES SHA1 implementado solo para QA; E2E pendiente y sin FORMPROC.submit, presentación administrativa, autenticación ni sesión."
    reviewed_at: "2026-08-09"
    next_gate: "Realizar una prueba física autorizada del login y callback; mantener QA_ONLY mientras E2E pendiente y no ejecutar FORMPROC.submit ni realizar presentación administrativa."

```

## 8. Relación con el catálogo de producto

Este archivo es documentación de investigación, no configuración ejecutable:

- no se empaqueta ni se descarga como catálogo remoto;
- no asigna `ProfileActivation`;
- no concede origins de inicio, redirect o browse confiable;
- no concede endpoint, adapter, capability ni filtro de certificado;
- no convierte `VERIFIED_CONTRACT` en soporte real;
- no concede por sí mismo profiles o capabilities; el catálogo de producto
  versionado sigue siendo la única fuente runtime de confianza.

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

1. El directorio AGE D11 ya está materializado; faltan DIR3, SIA, INVENTE y la
   resolución de superficies estatales adicionales o no incluidas por D11.
2. La cola territorial D03 ya está materializada para las 17 comunidades y
   Ceuta/Melilla, pero sus organismos dependientes, portales sectoriales y
   contratos de firma siguen requiriendo olas específicas. La relación entre
   las dos sedes observadas de Extremadura permanece sin resolver.
3. D12 ya está materializado para los 11 cabildos y consells y D06 para sus 41
   etiquetas provinciales primarias. D05 sigue capturado pero pendiente de
   resolución e ingestión; también quedan diferidas las superficies
   provinciales secundarias y miles de entidades locales.
4. RUCT aún no se ha convertido en una cola cerrada de universidades públicas;
   el seed contiene tres.
5. SIA no se ha recorrido para descubrir frontends distintos del portal
   institucional.
6. No existe inventario separado de proveedores compartidos, plataformas
   multi-tenant, SSO, Storage/Retrieve o endpoints tri-phase.
7. Hay cuatro entradas con evidencia E2E delimitada y dos evidencias exactas de `ClientCertRequest`; ningún otro registro hereda esos resultados.
8. Las variantes lingüísticas no crean registros; los dominios históricos y
   redirects solo se separan cuando existe una frontera funcional acreditada.
   Los candidatos INAGA de Aragón, el checker técnico de Castilla y León, el
   portal de convenios de Castilla-La Mancha y un trámite archivado de CAIB no
   se promovieron a superficies actuales sin una entrada ciudadana vigente.
9. El Ayuntamiento de Madrid permanece `RECHECK_REQUIRED` por la limitación de
   revalidación ya documentada.

Orden de expansión recomendado:

1. AGE y sector público estatal: D11 completado; continuar con [D02], [D07],
   [D08] y [D09].
2. Comunidades y ciudades autónomas: D03 completado; continuar por familias de
   protocolo y organismos dependientes, conservando perfiles separados por
   origin y frontera funcional.
3. Diputaciones y ayuntamientos: D06 primario completado; continuar su ola
   secundaria y [D04]/[D05], cuyo snapshot municipal aún no está ingerido, y
   contrastar con DIR3. D12 ya está completado para cabildos y consells.
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
[D06]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_Diputaciones.html
[D07]: https://administracion.gob.es/pag_Home/espanaAdmon/SIA.html
[D08]: https://administracionelectronica.gob.es/ctt/dir3
[D09]: https://www.igae.pap.hacienda.gob.es/sitios/igae/es-ES/BasesDatos/invente/paginas/inicio.aspx
[D10]: https://www.ciencia.gob.es/Universidades/RUCT.html
[D11]: https://sede.administracion.gob.es/sedes-electronicas
[CTBG-DOSSIER-2026-08-23]: https://sede.consejodetransparencia.gob.es/dossier
[CTBG-SOLINFO-DETAIL-2026-08-23]: https://sede.consejodetransparencia.gob.es/catalog/t/01b4b72b-7f21-4d7c-9576-e1d7871624a6
[CTBG-SOLINFO-LAUNCH-2026-08-23]: https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6
[CTBG-CLAVE-2026-08-23]: https://pasarela.clave.gob.es/Proxy2/ServiceProvider
[D12]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_CabildosConsejos.html

### Evidencia portal-specific

[CATASTRO-HOME-2026-08-24]: https://www.sedecatastro.gob.es/
[CATASTRO-TRAMITES-2026-08-24]: https://www.sedecatastro.gob.es/Accesos/SECAccTramites.aspx
[CATASTRO-DEST22-2026-08-24]: https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22
[CATASTRO-DNI22-2026-08-24]: https://www.sedecatastro.gob.es/Accesos/SECAccDNI.aspx?Dest=22
[CATASTRO-PIN22-2026-08-24]: https://www.sedecatastro.gob.es/Accesos/SECAccPIN.aspx?Dest=22&texp=REGI
[CATASTRO-CLAVE-2026-08-24]: https://pasarela.clave.gob.es/Proxy2/ResponseRedirect
[FEGA-HOME-2026-08-24]: https://www.sede.fega.gob.es/
[FEGA-PROCEDURES-2026-08-24]: https://www.sede.fega.gob.es/procedimientos-y-servicios
[FEGA-SOLICITUD-2026-08-24]: https://www.sede.fega.gob.es/content/solicitud-al-fega
[FEGA-OFVSG02-2026-08-24]: https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02
[FEGA-REGPOST-2026-08-24]: https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/registroAsientos.action
[FEGA-CLAVE-2026-08-24]: https://pasarela.clave.gob.es/Proxy2/ServiceProvider
[AEMET-SEDE-2026-08-23]: https://www.aemet.es/es/sede_electronica
[AEMET-PROCEDURE-2026-08-23]: https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/solicitudes
[AEMET-NEW-SOLICITUD-2026-08-23]: https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/nuevaSolicitud
[AEMET-L1-2026-08-23]: https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/formularioSolicitud?tipoSolicitud=L1
[AEMET-SSO-2026-08-23]: https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso
[COMERCIO-SURFACE-2026-08-17]: https://comercio.gob.es/
[COMERCIO-REG-2026-08-17]: https://sede.mineco.gob.es/es/procedimientos-y-servicios-electronicos/areas-tematicas/comercio/detalle-procedimiento?val=3057517
[DIGITAL-SEDE-REG-2026-08-17]: https://digital.sede.gob.es/servicio?id=Procedimientos-electr%C3%B3nicos-disponibles-en-la-Sede-Electr%C3%B3nica
[HACIENDA-REG-2026-08-17]: https://sede.hacienda.gob.es/es-es/paginas/informacion
[HACIENDA-PAG-REG-AGE-2026-08-17]: https://sede.administracion.gob.es/servicios-electronicos/registro-electronico-general-age
[TESORO-REC-2026-08-17]: https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de
[UNED-REG-2026-08-17]: https://uned.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[PUERTOS-REG-2026-08-17]: https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[DSCA-REG-2026-08-16]: https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje
[POLICIA-SEDE-2026-08-15]: https://sede.policia.gob.es/
[POLICIA-SOLICITUD-2026-08-15]: https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml

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
[P05]: https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/
[DGT-JS-MAIN-2026-08-09]: https://sede.dgt.gob.es/export/system/modules/es.trafico.dgt.sedeV5/resources/js/padi/main.js
[DGT-JS-CONSTANTES-2026-08-09]: https://sede.dgt.gob.es/export/system/modules/es.trafico.dgt.sedeV5/resources/js/padi/constantes.js
[DGT-JS-MINIAPPLET-2026-08-09]: https://sede.dgt.gob.es/export/system/modules/es.trafico.dgt.sedeV5/resources/js/padi/miniapplet.js
[P06]: https://sedejudicial.justicia.es/firma-y-certificados-electronicos-admitidos
[P06A]: https://sedejudicial.justicia.es/documents/20142/72138908/202408_Escrito%2Biniciador%2Bde%2Bjurisdicci%C3%B3n%2Bvoluntaria_ciudadan%C3%ADa_V3.pdf/72c096fe-0e01-2293-fb9a-c9862dca89f0?t=1727245303785
[P06B]: https://sede.mjusticia.gob.es/informacion-ayuda/preguntas-frecuentes
[P06C]: https://sede.mjusticia.gob.es/tramites/organos-gobierno
[MJUSTICIA-IDP75-LAUNCH-2026-08-19]: https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75
[P07]: https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs
[P08]: https://sede.comunidad.madrid/guia-tramitacion/realizo-solicitud
[P08A]: https://sede.comunidad.madrid/registro-electronico-general-comunidad-madrid
[P08B]: https://gestiona2.comunidad.madrid/gpse_solicitud/accesos.jsf?numref=2094
[P09]: https://www.sede.diputaciondevalladolid.es/requisitos-tecnicos
[P09A]: https://www.sede.diputaciondevalladolid.es/preguntas-frecuentes
[VALLADOLID-PROCEDURE-2026-08-13]: https://www.sede.diputaciondevalladolid.es/tramites-disponibles/12S203/
[VALLADOLID-LOGIN-2026-08-13]: https://www.sede.diputaciondevalladolid.es/tgauth/login
[VALLADOLID-CERT-REDIRECT-2026-08-13]: https://www.sede.diputaciondevalladolid.es/c/portal/cert-login
[VALLADOLID-CLIENT-TLS-2026-08-13]: https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login
[P10]: https://sede.sevilla.org/opencms/system/modules/sede/contents/faq/Presentacion_Clave
[P10A]: https://sede.sevilla.org/opencms/system/modules/sede/contents/faq/Error_firma
[P10B]: https://sede.sevilla.org/opencms/system/modules/sede/contents/footer/mapa_web
[P10C]: https://www.sevilla.org/ovweb/
[P10D]: https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente
[P11]: https://sede.madrid.es/portal/site/tramites/menuitem.62876cb64654a55e2dbd7003a8a409a0/?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD
[P12]: https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp
[P12A]: https://sede.ugr.es/portal/requisitos/index.html
[P13]: https://sede.us.es/opencms/system/modules/sede/contents/pages/requisitosTecnicos
[P13A]: https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01
[BNE-REG-2026-08-16]: https://sede.bne.gob.es/es/tramites/quejas-sugerencias
[OEPM-PROTEGEO-2026-08-17]: https://sede.oepm.gob.es/eSede/es/tramites-comunes/solicitud-electronica-de-proposito-general-remitida-a-la-oepm-/
[FUNCIONA-PUBLIC-2026-08-17]: https://sede.funciona.gob.es/es/home
[BOE-SEDE-2026-08-23]: https://www.boe.es/informacion/index.php
[CERVANTES-REG-2026-08-17]: https://cervantes.sede.gob.es/servicio?id=Registro-Electrónico-General
[REINA-SOFIA-REG-2026-08-17]: https://museoreinasofia.sede.gob.es/servicio?id=Registro-Electrónico-General
[DGOJ-PUBLIC-2026-08-24]: https://sede.ordenacionjuego.gob.es/es/firma
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
[P18]: https://sede.comunidad.madrid/autorizaciones-licencias-permisos-carnes/carne-joven
[P18A]: https://carnejovenmadrid.com/ventajas/carne-joven-digital
[P18B]: https://digital.comunidad.madrid/ext/53F1
[P18C]: https://gestiona.comunidad.madrid/mova_configuraciones_mova3/cudc_webapp_cuentadigital2/v3/app-config.json
[P18D]: https://gestiona.comunidad.madrid/cudc_mf_procedures/3.8.4/remoteEntry.js
[P18E]: https://gestiona.comunidad.madrid/cudc_mf_procedures/3.8.4/2395.e31f7440c3b55405.js
[P18F]: https://gestiona.comunidad.madrid/cudc_mf_procedures/3.8.4/9536.885f46529d1fae07.js
[P19]: https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html
[P20]: https://sede.mscbs.gob.es/registroElectronico/formularios.htm
[P21]: https://sede.mscbs.gob.es/SIGEM_RegistroTelematicoWeb/indiceForm
[P22]: https://sede.mscbs.gob.es/diseno/js/form_gen.js
[P23]: https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&ENTIDAD_ID=000&LANG=es&COUNTRY=ES
[P24]: https://sede.tea.hacienda.gob.es/TEA/alegaciones.html
[P25]: https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0
[P19A]: https://ws101.juntadeandalucia.es/portalcj/
[P19B]: https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp
[P19C]: https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet
[P19D]: https://ws235.juntadeandalucia.es/authenticationFacade
[LIVE-JUNTA-OFVIRTUAL-2026-07-22]: https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs
[E2E-JUNTA-OFVIRTUAL-2026-07-29]: ../e2e/2026-07-29-junta-ofvirtual-auth-success.md
[LIVE-EDUCACION-ENTRY-2026-07-22]: https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46
[EDUCACION-CONV46-CLIENTTLS-2026-08-19]: https://www.educacion.gob.es/claveedu/claveEduPeticion.form

### Evidencia de comunidades y ciudades autónomas

[A01A]: https://www.juntadeandalucia.es/servicios/sede/sobre-sede/titularidad.html
[A01B]: https://www.juntadeandalucia.es/servicios/sede
[JUNTA-VEA-PEG-2026-08-17]: https://www.juntadeandalucia.es/servicios/sede/tramites.html
[JUNTA-VEA-RUNTIME-2026-08-17]: https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA
[A02A]: https://www.aragon.es/tramites
[A02B]: https://www.aragon.es/tramites/identificacion-y-firma-electronica
[A03A]: https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica
[A03B]: https://sede.asturias.es/ast/-/dboid-6269000011903512107573
[A03C]: https://miprincipado.asturias.es/sobre-miprincipado/sistemas-de-identificacion
[A04A]: https://caib.es/seucaib/ca/fichainformativa/3392758
[A04B]: https://www.caib.es/seucaib/ca/
[A04C]: https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/
[A05A]: https://sede.gobiernodecanarias.org/sede/la_sede
[A05B]: https://sede.gobiernodecanarias.org/sede/la_sede/requisitos_tecnicos
[A06A]: https://sede.cantabria.es/sede/informacion/identificacion-de-la-sede
[A06B]: https://sede.cantabria.es/sede/
[A06C]: https://sede.cantabria.es/sede/catalogo-de-tramites/tramite/emision-de-certificados-de-los-datos-que-consten-en-los-registros-de-asociaciones/2645
[A06D]: https://sede.cantabria.es/sede/informacion/sistemas-de-e-firma-admitidos
[A06E]: https://rec.cantabria.es/rec/bienvenida.htm
[CANTABRIA-REC-AUTOFIRMA-2026-08-09]: https://rec.cantabria.es/rec/js/autoFirma.js
[CANTABRIA-REC-AFIRMA-CLIENTE-2026-08-09]: https://clientefirma.cantabria.es/clientefirma/js/autofirma/afirmaClienteMiniapplet.js
[CANTABRIA-REC-MINIAPPLET-2026-08-09]: https://clientefirma.cantabria.es/clientefirma/js/autofirma/miniapplet.js
[A07A]: https://www.tramitacastillayleon.jcyl.es/
[A07B]: https://www.tramitacastillayleon.jcyl.es/web/es/ayuda-sobre-administracion-electronica/requisitos-tecnicos.html
[JCYL-QUJU-PROC-2026-08-19]: https://www.tramitacastillayleon.jcyl.es/web/jcyl/AdministracionElectronica/es/Plantilla100Detalle/1251181050732/Tramite/1277466706825/Tramite
[JCYL-QUJU-RUNTIME-2026-08-19]: https://presidencia.jcyl.es/QUJU?O=1
[A08A]: https://www.jccm.es/
[A08B]: https://www.jccm.es/web/la-sede/sistemas-de-identificacion-y-firma
[A08C]: https://www.jccm.es/tramites/1001243
[A09A]: https://web.gencat.cat/ca/seu-electronica/informacio/sobre-la-seu
[A09B]: https://web.gencat.cat/ca/seu-electronica
[A09C]: https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?category=72461610-a82c-11e3-a972-000c29052e2c
[A09D]: https://web.gencat.cat/ca/seu-electronica/relacio-digital/cataleg-sistemes-identificacio-i-signatura-electronica
[A10A]: https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info
[A10B]: https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI
[A10C]: https://sede.ceuta.es/controlador/controlador?cmd=requisitos&modulo=info
[A11A]: https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_TITULARSEDE
[A11B]: https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO
[A11C]: https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_FIRMA
[A11D]: https://sede.melilla.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999
[A11E]: https://sede.melilla.es/sta/resources/js/sta-autofirma-lote.js
[A11F]: https://sede.melilla.es/sta/resources/js/autoscript.js
[A12A]: https://sede.gva.es/es/normativa-reguladora
[A12B]: https://sede.gva.es/es/
[A12C]: https://sede.gva.es/es/detall-tramit?id_proc=15602
[A12D]: https://sede.gva.es/es/sistemes-d-identificacio-i-signatura-acceptats
[A13A]: https://tramites.juntaex.es/
[A13B]: https://tramites.juntaex.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_FIRMA
[A13C]: https://sede.juntaex.es/SEDE/
[EXT-SEDE-ALIAS-2026-08-18]: ../autonomous/2026-08-18-extremadura-sede-anterior-alias.md
[A13D]: https://portaltributario.juntaex.es/PortalTributario/web/guest/requisitos-tecnicos
[A13E]: https://tramites.juntaex.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_REGGENERAL_INFO
[A13F]: https://tramites.juntaex.es/sta/resources/js/sta-autofirma-lote.js
[A13G]: https://tramites.juntaex.es/sta/resources/js/autoscript.js
[A13H]: https://tramites.juntaex.es/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.2
[A14A]: https://sede.xunta.gal/a-sede/identificacion-e-titularidade
[A14B]: https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica
[A14C]: https://sede.xunta.gal/a-sede/sistemas-de-identificacion-e-sinatura
[A16A]: https://sede.carm.es/web/pagina?IDCONTENIDO=40291&IDTIPO=100
[A16B]: https://sede.carm.es/web/pagina?IDCONTENIDO=385&IDTIPO=240&RASTRO=c%24m40293%2C62654%2C40288
[A16C]: https://sede.carm.es/web/pagina?IDCONTENIDO=56864&IDTIPO=100&RASTRO=c%24m40248
[A17A]: https://www.navarra.es/es/tramites/titularidad-de-la-sede-electronica
[A17B]: https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico
[A17C]: https://www.navarra.es/es/tramites/ayuda-para-tramitar-por-internet/firmar-documentos
[A18A]: https://www.euskadi.eus/sede-electronica/
[A18B]: https://www.euskadi.eus/medios-de-identificacion-electronica-admitidos/web01-sede/eu/
[A18C]: https://www.euskadi.eus/faqs/firma-electronica-preguntas-mas-frecuentes/web01-tramite/es/
[A19A]: https://www.larioja.org/
[A19B]: https://web.larioja.org/oficina-electronica/
[A19C]: https://web.larioja.org/oficina-electronica/tramite?n=24697

### Evidencia de cabildos y consells insulares

[I01A]: https://www.cime.es/
[I01B]: https://seuelectronica.cime.es/
[I02A]: https://www.conselldemallorca.es/
[I02B]: https://seu.conselldemallorca.net/
[I03A]: https://www.conselldeivissa.es/
[I03B]: https://seu.conselldeivissa.es/
[I04A]: https://www.consellinsulardeformentera.cat/
[I04B]: https://ovac.conselldeformentera.cat/
[I05A]: https://www.elhierro.es/es
[I05B]: https://elhierro.sedelectronica.es/info.0
[I06A]: https://www.tenerife.es/
[I06B]: https://sede.tenerife.es/
[I06C]: https://sede.tenerife.es/76.81426d6ba0b90ca6.js
[I07A]: https://www.cabildodelapalma.es/
[I07B]: https://sedeelectronica.cabildodelapalma.es/
[I07C]: https://sedeelectronica.cabildodelapalma.es/sta/resources/js/sta-autofirma-lote.js
[I07D]: https://sedeelectronica.cabildodelapalma.es/sta/resources/js/autoscript.js
[I07E]: https://sedeelectronica.cabildodelapalma.es/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.3
[I07F]: https://sedeelectronica.cabildodelapalma.es/sta/CarpetaPublic/Login?APP_CODE=STA&PAGE_CODE=PTS2_HOME
[I08A]: https://www.lagomera.es/
[I08B]: https://lagomera.sedelectronica.es/info.0
[I09A]: https://www.cabildofuer.es/cabildo/
[I09B]: https://sede.cabildofuer.es/eAdmin/Sede.do
[I09C]: https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1
[I09D]: https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert
[I09E]: https://sede.cabildofuer.es/eAdmin/js/miniapplet.js
[I10A]: https://www.cabildodelanzarote.com/
[I10B]: https://cabildodelanzarote.sedelectronica.es/info.0
[I11A]: https://cabildo.grancanaria.com/
[I11B]: https://sede.grancanaria.com/

### Evidencia de diputaciones provinciales

Cada familia `DP01` a `DP41` sigue el orden cerrado de D06. El sufijo `A`
define exactamente la URL de la superficie primaria; `B`, cuando existe,
define una única URL oficial adicional para propietario, certificado o firma.
D06 se conserva como provenance de cada registro, pero no acredita por sí solo
availability, certificado, firma ni contrato técnico.

[DP01A]: https://www.diputacionalicante.es
[DP01B]: https://sede.diputacionalicante.es/
[DP02A]: https://web.araba.eus/es/home
[DP03A]: https://www.dipualba.es
[DP03B]: https://sede.dipualba.es/transparencia/Home/Details/20
[ALBACETE-INSTANCIA-2026-08-18]: https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567
[ALBACETE-SSL-IDENT-2026-08-18]: https://sede.dipualba.es/segex/identificacion_opciones.aspx
[DP04A]: https://www.dipalme.org
[DP05A]: https://www.diputacionavila.es
[DP05B]: https://diputacionavila.sedelectronica.es/
[AVILA-INSTANCIA-2026-08-18]: https://diputacionavila.sedelectronica.es/catalog/t/5161fa8d-970e-4b48-a506-b2ac34ceafe5
[AVILA-FIRMAR-2026-08-18]: https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5
[DP06A]: https://www.dip-badajoz.es
[DP06B]: https://sede.dip-badajoz.es/
[DP07A]: https://www.diba.cat/es/
[DP07B]: https://seuelectronica.diba.cat/es/suport-a-la-tramitaci%C3%B3
[DIBA-VALID-2026-08-18]: https://valid.aoc.cat/o/oauth2/auth
[DIBA-2057-2026-08-18]: https://seuelectronica.diba.cat/es/sol%C2%B7licitud-gen%C3%A8rica
[DP08A]: https://burgos.es
[DP08B]: https://sede.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=CATALOGO
[DP08C]: https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO
[DP08D]: https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_FAQS2
[DP08E]: https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_FIRMA
[DP08F]: https://registro.diputaciondeburgos.es/sta/resources/js/sta-autofirma-lote.js
[DP08G]: https://registro.diputaciondeburgos.es/sta/resources/js/autoscript.js
[DP08H]: https://registro.diputaciondeburgos.es/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.3
[DP09A]: https://www.dip-caceres.es
[DP10A]: https://www.dipucadiz.es
[DP10B]: https://sede.dipucadiz.es/web/sede/inicio
[DP11A]: https://www.dipcas.es/es/
[DP12A]: https://www.dipucr.es
[DP13A]: https://www.dipucordoba.es
[DP14A]: https://www.dacoruna.gal/portada
[DP14B]: https://www.dacoruna.gal/servizos-tributarios/preguntas-frecuentes/recursos/
[DP15A]: https://www.dipucuenca.es
[DP15B]: https://sede.dipucuenca.es/aviso-legal.aspx?entidad=16000
[DP16A]: https://www.ddgi.cat/web/
[DP16B]: https://seu.ddgi.cat/web/nivell/658/s-1/sistemes-de-signatura-electronica
[DP17A]: https://www.dipgra.es
[DP18A]: https://dguadalajara.sedelectronica.es
[DP18B]: https://www.dguadalajara.es/web/guest/sede-electronica
[DP19A]: https://egoitza.gipuzkoa.eus/es/
[DP19B]: https://egoitza.gipuzkoa.eus/es/identificacion-y-autenticacion/certificado-electronico-cualificado
[DP20A]: https://www.diphuelva.es
[DP21A]: https://www.dphuesca.es
[DP21B]: https://diputaciondehuesca.transparencialocal.gob.es/es_ES/media/49636
[DP21C]: https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME
[DP21D]: https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_FAQS2
[DP21E]: https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_REQUISITOS
[DP21F]: https://ovc24.dphuesca.es/sta/resources/js/sta-autofirma-lote.js
[DP21G]: https://ovc24.dphuesca.es/sta/resources/js/autoscript.js
[DP21H]: https://ovc24.dphuesca.es/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.2
[DP21I]: https://ovc24.dphuesca.es/sta/AutofirmaLote
[DP22A]: https://sede.dipujaen.es
[DP22B]: https://sede.dipujaen.es/CertificadoElectronico
[DP23A]: https://sede.dipuleon.es
[DP24A]: https://seu.diputaciolleida.cat
[DP24B]: https://seu.diputaciolleida.cat/portal/contenedor.do?det_cod=49&ent_id=1&idioma=2
[DP25A]: https://sede.deputacionlugo.org
[DP25B]: https://sede.deputacionlugo.org/opencms/system/modules/sede/contents/footer/requisitos
[DP25C]: https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/secciones/autenticacion/autenticacion.jsp
[DP25D]: https://sede.deputacionlugo.org/opencms/common-js/clientSigner.js
[DP25E]: https://sede.deputacionlugo.org/opencms/system/modules/sede/contents/faq/acceso_sede
[DP25F]: https://sede.deputacionlugo.org/opencms/system/modules/sede/contents/faq/instalar_autofirma
[DP26A]: https://sede.malaga.es
[DP26B]: https://sede.malaga.es/politica-de-firma-electronica/
[DP27A]: https://sede.depourense.es
[DP27B]: https://sede.depourense.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_FIRMASELEC
[DP28A]: https://sede.diputaciondepalencia.es
[DP28B]: https://sede.diputaciondepalencia.es/siac/Tramites/CertificadosElectronicosAdmitidos.aspx
[DP29A]: https://sede.depo.gal
[DP29B]: https://sede.depo.gal/web/public/dynamic/description/esignature/
[DP30A]: https://sede.diputaciondesalamanca.gob.es
[DP30B]: https://sede.diputaciondesalamanca.gob.es/opencms/system/modules/gsede/elements/contenido/requisitos.jsp
[DP31A]: https://sede.dipsegovia.es
[DP32A]: https://sedeelectronicadipusevilla.es
[DP33A]: https://sede.dipsoria.es
[DP33B]: https://www.dipsoria.es/
[DP34A]: https://seuelectronica.dipta.cat
[DP34B]: https://seuelectronica.dipta.cat/normativa
[DP35A]: https://dpteruel.sedelectronica.es
[DP36A]: https://diputacion.toledo.gob.es
[DP36B]: https://diputacion.toledo.gob.es/sede/2
[TOLEDO-PROCEDURES-2026-08-13]: https://diputacion.toledo.gob.es/procedimientos/1
[TOLEDO-REGISTRY-START-2026-08-13]: https://diputacion.toledo.gob.es/SIGEM_RegistroTelematicoWeb/realizarSolicitudRegistro.do?tramiteId=TRAM_31
[TOLEDO-CERT-REDIRECT-2026-08-13]: https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31&SESION_ID=&ENTIDAD_ID=&LANG=&COUNTRY=
[TOLEDO-CLIENT-TLS-2026-08-13]: https://diputacion.toledo.gob.es:843/SIGEM_AutenticacionWeb/validacionCertificado.do
[DP37A]: https://www.sede.dival.es
[DP37B]: https://www.sede.dival.es/opencms/opencms/sede/paginas/index.jsp?opcion=detalle&agrupacion=DatosInstitucionales&servicio=ListaSistemasFirmaElectronica
[DP38A]: https://www.sede.diputaciondevalladolid.es/
[DP38B]: https://www.sede.diputaciondevalladolid.es/web/guest/requisitos-tecnicos
[DP39A]: https://www.ebizkaia.eus
[DP39B]: https://www.ebizkaia.eus/es/medios-de-identificacion
[DP40A]: https://diputaciondezamora.sedelectronica.es
[DP41A]: https://dpz.sedelectronica.es
[MENORCA-GENERIC-2026-08-18]: https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262
[MENORCA-CLIENT-TLS-2026-08-18]: https://www.carpetaciutadana.org/cime/Login/LoginCert.aspx
[EDU-REG-2026-08-17]: https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html
[CDTI-CERT-2026-08-16]: https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx
[MITECO-REG-2026-08-17]: https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html
[DIGITAL-REG-2026-08-17]: https://digital.sede.gob.es/
[MAEC-REG-2026-08-16]: https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula
[GVA-DGM15602-2026-08-18]: https://sede.gva.es/es/detall-tramit?id_proc=15602
[GVA-CLIENTTLS-2026-08-18]: https://ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html
[SEPE-REG-2026-08-19]: https://sede.sepe.gob.es/portalSede/registro-electronico.html
[CULTURA-REG-2026-08-17]: https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[JUVENTUD-REG-2026-08-17]: https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[IGUALDAD-REG-2026-08-17]: https://igualdad.sede.gob.es/servicio?id=Registro-Electrónico-General
[DEFENSA-REG-2026-08-17]: https://sede.defensa.gob.es/
[MITES-CERT-2026-08-17]: https://sede.mites.gob.es/inicio/detalleProcedimiento/38
[MPR-REG-2026-08-17]: https://mpr.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[INCLUSION-REG-2026-08-17]: https://sede.inclusion.gob.es/registroelectronico
[PAG-REG-AGE-2026-08-17]: https://administracion.gob.es/pag_Home/atencionCiudadana/Registros-electronicos-AGE.html
[MPTMD-REG-2026-08-17]: https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[INDUSTRIA-REG-2026-08-17]: https://sede.minetur.gob.es/es-es/procedimientoselectronicos/Paginas/consulta_registro.aspx
[TRANSPORTES-QYS-2026-08-17]: https://sede.transportes.gob.es/proc-servicios-comunes/presentacion-quejas-sugerencias-ambito-ministerio-transportes-movilidad-sostenible
[INTERIOR-REG-2026-08-17]: https://sede.interior.gob.es/portal/sede/tramites?codAgrupacion=GENERAL
[MIVAU-REG-2026-08-17]: https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General

[EIVISSA-INSTANCIA-GENERAL-2026-08-18]: https://seu.conselldeivissa.es/sta/CarpetaPublic/Public?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269002703260065905043
[EIVISSA-REG-AUTOFIRMA-2026-08-18]: https://seu.conselldeivissa.es/sta/reg/autofirma.js
[EIVISSA-CONTROLLED-AUTH-2026-08-18]: https://seu.conselldeivissa.es/sta/reg/auth/es/6269002703260065905043
[CATALUNYA-PETICIO-CLIENTTLS-2026-08-19]: https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2FPeticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c
[SEPES-TRANSPORTES-2026-08-24]: https://sede.transportes.gob.es/grupo-transportes/entidad-publica-empresarial-suelo-sepes/quejas-reclamaciones
[SEPES-TRANSPORTES-NORMATIVA-2026-08-24]: https://sede.transportes.gob.es/conoce-sede/normativa-de-la-sede
[ENAIRE-SEDE-2026-08-24]: https://enaire.sede.gob.es/
[ENAIRE-PROCEDURES-2026-08-24]: https://enaire.sede.gob.es/procedimientos
[ENAIRE-REQ-2026-08-24]: https://enaire.sede.gob.es/Requisitos
[ENAIRE-VALIDACION-2026-08-24]: https://enaire.sede.gob.es/servicio?id=Validacion-de-certificados-y-firma
[DGSFP-SEDE-2026-08-24]: https://www.sededgsfp.gob.es/
[CNMV-SEDE-2026-08-23]: https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx
[GC-SEDE-2026-08-23]: https://sede.guardiacivil.gob.es/
[GC-PROCEDURES-2026-08-23]: https://sede.guardiacivil.gob.es/procedimientos/index/language/es_ES
[GC-INSTRUCCION-2026-05-11]: https://sede.guardiacivil.gob.es/fichero-publico/descargar/id/5205
[CNMC-GENERAL-2026-08-23]: https://sede.cnmc.gob.es/tramites/general/remision-de-solicitudes-escritos-y-comunicaciones
[CSN-SEDE-2026-08-23]: https://sede.csn.gob.es/
[CSN-IDENT-2026-08-23]: https://sede.csn.gob.es/Sede20/identificacion?tipoacceso=3
