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
| Origins primarios distintos | 181 |
| Fuentes enumeradoras oficiales registradas | 12 |
| Colas enumeradoras ingeridas de extremo a extremo | 4/12 |
| Colas enumeradoras pendientes de ingestión | 8/12 |
| Fuentes oficiales portal-specific registradas | 214 |
| Fuentes oficiales totales registradas | 226 |
| Entradas `VERIFIED_E2E` | 4 |
| Entradas `IMPLEMENTED_NOT_E2E` | 44 |
| Entradas implementadas (`VERIFIED_E2E` + `IMPLEMENTED_NOT_E2E`) | 48 |
| Entradas restantes fuera de ambos estados | 135 |
| Evidencia exacta de `ClientCertRequest` | 1 |

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
| `IMPLEMENTED_NOT_E2E` | 44 |
| `VERIFIED_CONTRACT` | 1 |
| `REQUIRES_AUTHENTICATED_RESEARCH` | 0 |
| `BROWSE_ONLY` | 128 |
| `UNSUPPORTED_PROTOCOL` | 2 |
| `INACCESSIBLE` | 4 |
| `DEPRECATED` | 0 |
| **Total** | **183** |

Por mantenimiento del inventario:

| Estado | Registros |
| --- | ---: |
| `REVIEWED` | 123 |
| `RECHECK_REQUIRED` | 5 |
| `DISCOVERED` | 55 |
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
Sus 67 fuentes portal-specific definen una sola URL por ID: `A` acredita la
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
47/50; no se publica un cociente agregado para las 207 fuentes porque las
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
    evidence_ids: ["P04", "D11"]
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
    evidence_ids: ["P06B", "P06C", "D11"]
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.aemet.gob.es"
    official_site: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
    e_sede: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
    entry_url: "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home"
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

  - inventory_id: "ES-PUB-0025"
    surface_key: "age-agencia-estatal-de-seguridad-aerea-aesa"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal de Seguridad Aérea (AESA)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.seguridadaerea.gob.es"
    official_site: "https://sede.seguridadaerea.gob.es/"
    e_sede: "https://sede.seguridadaerea.gob.es/"
    entry_url: "https://sede.seguridadaerea.gob.es/"
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

  - inventory_id: "ES-PUB-0026"
    surface_key: "age-agencia-estatal-del-boletin-oficial-del-estado-boe"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Agencia Estatal del Boletín Oficial del Estado (BOE)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.boe.es"
    official_site: "https://www.boe.es/"
    e_sede: "https://www.boe.es/"
    entry_url: "https://www.boe.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de la Presidencia, Justicia y Relaciones con las Cortes."

  - inventory_id: "ES-PUB-0027"
    surface_key: "age-autoridad-independiente-de-responsabilidad-fiscal-airef"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Autoridad Independiente de Responsabilidad Fiscal (AIReF)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://airef.sede.gob.es"
    official_site: "https://airef.sede.gob.es/"
    e_sede: "https://airef.sede.gob.es/"
    entry_url: "https://airef.sede.gob.es/"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.cnmc.gob.es"
    official_site: "https://sede.cnmc.gob.es/"
    e_sede: "https://sede.cnmc.gob.es/"
    entry_url: "https://sede.cnmc.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Industria y Turismo; Ministerio para la Transición Ecológica y el Reto Demográfico."

  - inventory_id: "ES-PUB-0035"
    surface_key: "age-consejo-de-transparencia-y-buen-gobierno-ctbg"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Consejo de Transparencia y Buen Gobierno (CTBG)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.consejodetransparencia.gob.es"
    official_site: "https://sede.consejodetransparencia.gob.es/"
    e_sede: "https://sede.consejodetransparencia.gob.es/"
    entry_url: "https://sede.consejodetransparencia.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio del Interior."

  - inventory_id: "ES-PUB-0041"
    surface_key: "age-direccion-general-de-ordenacion-del-juego"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Ordenación del Juego"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.ordenacionjuego.gob.es"
    official_site: "https://sede.ordenacionjuego.gob.es/"
    e_sede: "https://sede.ordenacionjuego.gob.es/"
    entry_url: "https://sede.ordenacionjuego.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Derechos Sociales, Consumo y Agenda 2030; Ministerio de Vivienda y Agenda Urbana."

  - inventory_id: "ES-PUB-0042"
    surface_key: "age-direccion-general-de-seguros-y-fondos-de-pensiones"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General de Seguros y Fondos de Pensiones"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.sededgsfp.gob.es"
    official_site: "https://www.sededgsfp.gob.es/"
    e_sede: "https://www.sededgsfp.gob.es/"
    entry_url: "https://www.sededgsfp.gob.es/"
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

  - inventory_id: "ES-PUB-0043"
    surface_key: "age-direccion-general-del-catastro"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Dirección General del Catastro"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.sedecatastro.gob.es"
    official_site: "https://www.sedecatastro.gob.es/"
    e_sede: "https://www.sedecatastro.gob.es/"
    entry_url: "https://www.sedecatastro.gob.es/"
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

  - inventory_id: "ES-PUB-0045"
    surface_key: "age-entidad-publica-empresarial-de-suelo-sepes"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Entidad Pública Empresarial de Suelo (SEPES)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.sepes.es"
    official_site: "https://www.sepes.es/es/sede-electronica"
    e_sede: "https://www.sepes.es/es/sede-electronica"
    entry_url: "https://www.sepes.es/es/sede-electronica"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.sede.fega.gob.es"
    official_site: "https://www.sede.fega.gob.es/"
    e_sede: "https://www.sede.fega.gob.es/"
    entry_url: "https://www.sede.fega.gob.es/"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://cervantes.sede.gob.es"
    official_site: "https://cervantes.sede.gob.es/"
    e_sede: "https://cervantes.sede.gob.es/"
    entry_url: "https://cervantes.sede.gob.es/"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.mineco.gob.es"
    official_site: "https://sede.mineco.gob.es/"
    e_sede: "https://sede.mineco.gob.es/"
    entry_url: "https://sede.mineco.gob.es/"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.inclusion.gob.es"
    official_site: "https://sede.inclusion.gob.es/"
    e_sede: "https://sede.inclusion.gob.es/"
    entry_url: "https://sede.inclusion.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Inclusión, Seguridad Social y Migraciones."

  - inventory_id: "ES-PUB-0069"
    surface_key: "age-ministerio-de-industria-y-turismo"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Industria y Turismo"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.minetur.gob.es"
    official_site: "https://sede.minetur.gob.es/"
    e_sede: "https://sede.minetur.gob.es/"
    entry_url: "https://sede.minetur.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Industria y Turismo."

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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://mpr.sede.gob.es"
    official_site: "https://mpr.sede.gob.es/"
    e_sede: "https://mpr.sede.gob.es/"
    entry_url: "https://mpr.sede.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de la Presidencia, Justicia y Relaciones con las Cortes."

  - inventory_id: "ES-PUB-0072"
    surface_key: "age-ministerio-de-politica-territorial-y-memoria-democratica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Política Territorial y Memoria Democrática"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://mptmd.sede.gob.es"
    official_site: "https://mptmd.sede.gob.es/"
    e_sede: "https://mptmd.sede.gob.es/"
    entry_url: "https://mptmd.sede.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Política Territorial y Memoria Democrática."

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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.mitma.gob.es"
    official_site: "https://sede.mitma.gob.es/sede_electronica/lang_castellano/"
    e_sede: "https://sede.mitma.gob.es/sede_electronica/lang_castellano/"
    entry_url: "https://sede.mitma.gob.es/sede_electronica/lang_castellano/"
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

  - inventory_id: "ES-PUB-0076"
    surface_key: "age-ministerio-de-vivienda-y-agenda-urbana"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio de Vivienda y Agenda Urbana"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://mivau.sede.gob.es"
    official_site: "https://mivau.sede.gob.es/"
    e_sede: "https://mivau.sede.gob.es/"
    entry_url: "https://mivau.sede.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Vivienda y Agenda Urbana."

  - inventory_id: "ES-PUB-0077"
    surface_key: "age-ministerio-del-interior"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio del Interior"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.mir.gob.es"
    official_site: "https://sede.mir.gob.es/opencms/export/sites/default/es/inicio/"
    e_sede: "https://sede.mir.gob.es/opencms/export/sites/default/es/inicio/"
    entry_url: "https://sede.mir.gob.es/opencms/export/sites/default/es/inicio/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio del Interior."

  - inventory_id: "ES-PUB-0078"
    surface_key: "age-ministerio-para-la-transformacion-digital-y-de-la-funcion-publica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Ministerio para la Transformación Digital y de la Función Pública"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://digital.sede.gob.es"
    official_site: "https://digital.sede.gob.es/"
    e_sede: "https://digital.sede.gob.es/"
    entry_url: "https://digital.sede.gob.es/"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://museoreinasofia.sede.gob.es"
    official_site: "https://museoreinasofia.sede.gob.es/"
    e_sede: "https://museoreinasofia.sede.gob.es/"
    entry_url: "https://museoreinasofia.sede.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Cultura."

  - inventory_id: "ES-PUB-0081"
    surface_key: "age-mutualidad-general-judicial-mugeju"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Mutualidad General Judicial (MUGEJU)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sedemugeju.gob.es"
    official_site: "https://sedemugeju.gob.es/"
    e_sede: "https://sedemugeju.gob.es/"
    entry_url: "https://sedemugeju.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de la Presidencia, Justicia y Relaciones con las Cortes."

  - inventory_id: "ES-PUB-0082"
    surface_key: "age-oficina-espanola-de-patentes-y-marcas"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Oficina Española de Patentes y Marcas"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.oepm.gob.es"
    official_site: "https://sede.oepm.gob.es/"
    e_sede: "https://sede.oepm.gob.es/"
    entry_url: "https://sede.oepm.gob.es/"
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
    notes: "Ministerio(s) enumerador(es): Ministerio de Industria y Turismo."

  - inventory_id: "ES-PUB-0083"
    surface_key: "age-portal-de-la-transparencia"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Portal de la Transparencia"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.transparencia.gob.es"
    official_site: "https://sede.transparencia.gob.es/"
    e_sede: "https://sede.transparencia.gob.es/"
    entry_url: "https://sede.transparencia.gob.es/"
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

  - inventory_id: "ES-PUB-0084"
    surface_key: "age-portal-funciona"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Portal Funciona"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.funciona.gob.es"
    official_site: "https://sede.funciona.gob.es/public/servicios"
    e_sede: "https://sede.funciona.gob.es/public/servicios"
    entry_url: "https://sede.funciona.gob.es/public/servicios"
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

  - inventory_id: "ES-PUB-0085"
    surface_key: "age-puertos-del-estado"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Puertos del Estado"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.puertos.gob.es"
    official_site: "https://sede.puertos.gob.es/Paginas/Contenido.aspx"
    e_sede: "https://sede.puertos.gob.es/Paginas/Contenido.aspx"
    entry_url: "https://sede.puertos.gob.es/Paginas/Contenido.aspx"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.comercio.gob.es"
    official_site: "https://sede.comercio.gob.es/"
    e_sede: "https://sede.comercio.gob.es/"
    entry_url: "https://sede.comercio.gob.es/"
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

  - inventory_id: "ES-PUB-0088"
    surface_key: "age-sede-electronica-central-del-ministerio"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Sede electrónica central del Ministerio"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.hacienda.gob.es"
    official_site: "https://sede.hacienda.gob.es/"
    e_sede: "https://sede.hacienda.gob.es/"
    entry_url: "https://sede.hacienda.gob.es/"
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

  - inventory_id: "ES-PUB-0089"
    surface_key: "age-sede-electronica-de-la-s-e-de-digitalizacion-e-inteligencia-artificial-y-s-e-de-telecomunica"
    administrative_level: "ESTATAL"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Sede Electrónica de la S.E. de Digitalización e Inteligencia Artificial y S.E. de Telecomunicaciones e Infraestructuras Digitales del Ministerio de Transformación Digital"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sedediatid.digital.gob.es"
    official_site: "https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"
    e_sede: "https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"
    entry_url: "https://sedediatid.digital.gob.es/es-es/Paginas/Index.aspx"
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
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://www.tesoropublico.gob.es"
    official_site: "https://www.tesoropublico.gob.es/"
    e_sede: "https://www.tesoropublico.gob.es/"
    entry_url: "https://www.tesoropublico.gob.es/"
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

  - inventory_id: "ES-PUB-0092"
    surface_key: "age-universidad-nacional-de-educacion-a-distancia-uned"
    administrative_level: "UNIVERSIDAD_PUBLICA"
    autonomous_community: "NO_APLICA"
    province_or_municipality: "NO_APLICA"
    institution_name: "Universidad Nacional de Educación a Distancia (UNED)"
    surface_name: "Sede electrónica / entrada oficial del directorio AGE"
    surface_type: "SEDE"
    origin: "https://sede.uned.es"
    official_site: "https://sede.uned.es/"
    e_sede: "https://sede.uned.es/"
    entry_url: "https://sede.uned.es/"
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
    surface_name: "Sede electrónica de la Junta de Andalucía"
    surface_type: "SEDE"
    origin: "https://www.juntadeandalucia.es"
    official_site: "https://www.juntadeandalucia.es/servicios/sede"
    e_sede: "https://www.juntadeandalucia.es/servicios/sede"
    entry_url: "https://www.juntadeandalucia.es/servicios/sede"
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
    operation_summary: "Acceso público a información y servicios de la sede autonómica."
    protocol_evidence: "Las fuentes acreditan titularidad y entrada oficial, no un flujo de certificado o firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A01A", "A01B"]
    reason: "Certificado, firma, ABI, formato, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Identificar un procedimiento vigente y su contrato técnico exacto."

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
    surface_name: "MiPrincipado"
    surface_type: "SEDE"
    origin: "https://miprincipado.asturias.es"
    official_site: "https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica"
    e_sede: "https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica"
    entry_url: "https://miprincipado.asturias.es/sobre-miprincipado/identificacion-sede-electronica"
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
    operation_summary: "Información de sede y acceso con sistemas de identificación admitidos."
    protocol_evidence: "La fuente de sistemas acredita certificado como opción, no firma ni contrato portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A03A", "A03C"]
    reason: "Firma, transporte del certificado, ABI, formato y endpoint no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Delimitar una operación autenticada concreta sin seleccionar certificado."

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
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación pública cuya ficha indica firma con AutoFirma."
    protocol_evidence: "La ficha acredita el requisito de AutoFirma, pero no versión, ABI, callback ni endpoint."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A03B"]
    reason: "AutoFirma como producto no basta para autorizar bridge ni inferir el transporte."
    reviewed_at: "2026-07-16"
    next_gate: "Inspeccionar el JavaScript vigente y la entrega exacta del resultado."

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
    entry_url: "https://www.caib.es/seucaib/ca/"
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
    operation_summary: "Acceso y firma mediante los sistemas admitidos según el trámite."
    protocol_evidence: "La información oficial cita certificado, AutoFirma, Cl@veFirma y Firma àgil sin contrato runtime."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A04A", "A04B"]
    reason: "No se verificaron cliente JS, operación, formato, algoritmo, callback ni endpoint concretos."
    reviewed_at: "2026-07-16"
    next_gate: "Capturar un trámite vigente y separar identificación de firma."

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
    procedure_page: "https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/"
    certificate_required: "NO_VERIFICADO"
    signature_required: "NO_VERIFICADO"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Información y acceso al registro electrónico autonómico."
    protocol_evidence: "La entrada oficial acredita la función de registro, no un contrato de autenticación o firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A04B", "A04C"]
    reason: "No se verificaron entrada operativa final, certificado, firma, ABI ni endpoint."
    reviewed_at: "2026-07-16"
    next_gate: "Resolver el frontend operativo exacto sin iniciar una presentación."

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
    operation_summary: "Tramitación con certificado y AutoFirma cuando el procedimiento lo requiere."
    protocol_evidence: "Los requisitos citan AutoFirma y AutoFirma Móvil, pero no publican el ABI portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A05A", "A05B"]
    reason: "Producto, formato, algoritmo, callback, endpoint y TLS cliente exactos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento concreto y su invocación de firma."

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
    surface_name: "Tramitacastillayleon"
    surface_type: "SEDE"
    origin: "https://www.tramitacastillayleon.jcyl.es"
    official_site: "https://www.tramitacastillayleon.jcyl.es/"
    e_sede: "https://www.tramitacastillayleon.jcyl.es/"
    entry_url: "https://www.tramitacastillayleon.jcyl.es/"
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
    operation_summary: "Acceso a trámites y firma electrónica cuando la actuación la exige."
    protocol_evidence: "Los requisitos citan certificado y AutoFirma, pero no prueban el contrato runtime vigente."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A07A", "A07B"]
    reason: "La documentación de requisitos no acredita ABI, formato, callback ni endpoint concretos."
    reviewed_at: "2026-07-16"
    next_gate: "Localizar un trámite actual y su JavaScript servido por el portal."

  - inventory_id: "ES-PUB-0103"
    surface_key: "castilla-la-mancha-sede"
    administrative_level: "AUTONOMICO"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "NO_APLICA"
    institution_name: "Junta de Comunidades de Castilla-La Mancha"
    surface_name: "Sede electrónica de Castilla-La Mancha"
    surface_type: "SEDE"
    origin: "https://www.jccm.es"
    official_site: "https://www.jccm.es/"
    e_sede: "https://www.jccm.es/"
    entry_url: "https://www.jccm.es/"
    procedure_page: "https://www.jccm.es/tramites/1001243"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación con los sistemas de identificación y firma admitidos."
    protocol_evidence: "La sede y su ayuda acreditan certificado y firma condicionales, no contrato técnico exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A08A", "A08B", "A08C"]
    reason: "Cliente JS, formato, algoritmo, callback, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Inspeccionar el flujo y los assets de un trámite que requiera firma."

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
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Presentación de una petición genérica con identificación y firma cuando corresponda."
    protocol_evidence: "La ficha y el catálogo de sistemas no revelan un ABI ni endpoint portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A09B", "A09C", "A09D"]
    reason: "La condición de firma no acredita cliente JS, formato, algoritmo ni callback exactos."
    reviewed_at: "2026-07-16"
    next_gate: "Recorrer el flujo seguro hasta antes de la identificación o envío."

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
    entry_url: "https://sede.ceuta.es/controlador/controlador?cmd=info&modulo=info"
    procedure_page: "https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación con certificado, Cl@ve o AutoFirma según el procedimiento."
    protocol_evidence: "Los requisitos citan certificado y AutoFirma sin publicar ABI, formato o endpoint."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A10A", "A10B", "A10C"]
    reason: "La mención de certificado no prueba TLS cliente ni un contrato de firma."
    reviewed_at: "2026-07-16"
    next_gate: "Inspeccionar un trámite firmado sin iniciar presentación."

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
    surface_name: "Sede electrónica de la Generalitat Valenciana"
    surface_type: "SEDE"
    origin: "https://sede.gva.es"
    official_site: "https://sede.gva.es/es/"
    e_sede: "https://sede.gva.es/es/"
    entry_url: "https://sede.gva.es/es/"
    procedure_page: "https://sede.gva.es/es/detall-tramit?id_proc=15602"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación mediante los sistemas de identificación y firma admitidos."
    protocol_evidence: "Las fuentes acreditan certificado y firma condicionales, no un ABI exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A12A", "A12B", "A12C", "A12D"]
    reason: "El término ClientCert de la ayuda no prueba ClientCertRequest TLS; formato y endpoint tampoco están verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Observar la entrada de certificado sin seleccionar una identidad."

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
    operation_summary: "Entrada histórica de sede cuya relación con el portal de trámites actual no está resuelta."
    protocol_evidence: "La entrada contiene referencias a AutoFirma, pero no se verificó su vigencia ni contrato."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A13C"]
    reason: "No se ha acreditado retirada, sustitución, operación vigente ni contrato técnico; se conserva sin promoción."
    reviewed_at: "2026-07-16"
    next_gate: "Resolver oficialmente su relación con tramites.juntaex.es y revisar una operación vigente."

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
    entry_url: "https://portaltributario.juntaex.es/PortalTributario/web/guest/requisitos-tecnicos"
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
    operation_summary: "Tramitación tributaria con certificado y AutoFirma según los requisitos publicados."
    protocol_evidence: "Los requisitos acreditan certificado y AutoFirma, no ABI, callback ni formato exactos."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A13D"]
    reason: "La exigencia del producto no identifica cliente JS, algoritmo, endpoint ni TLS cliente."
    reviewed_at: "2026-07-16"
    next_gate: "Localizar un trámite tributario público y revisar su invocación sin enviar datos."

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
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Presentación de solicitud genérica mediante sistemas de identificación y firma admitidos."
    protocol_evidence: "La sede documenta AutoFirma y cliente móvil, sin publicar el ABI ni transporte del trámite."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A14A", "A14B", "A14C"]
    reason: "Cliente JS, formato, algoritmo, callback, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar los assets vigentes de la solicitud genérica sin iniciar presentación."

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
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación con certificado, Cl@ve y AutoFirma según el procedimiento."
    protocol_evidence: "La ayuda y la prueba de AutoFirma no publican el contrato runtime de la sede."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A16A", "A16B", "A16C"]
    reason: "Cliente JS, formato, algoritmo, callback, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Inspeccionar un trámite actual y su JavaScript sin enviar formulario."

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
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Presentación en el Registro General con certificado y firma cuando corresponda."
    protocol_evidence: "La ayuda documenta certificado y firma de documentos, sin contrato de integración exacto."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A17A", "A17B", "A17C"]
    reason: "La documentación no acredita cliente JS, formato aceptado, callback ni endpoint."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar el flujo público del registro hasta antes de la autenticación."

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
    entry_url: "https://web.larioja.org/oficina-electronica/"
    procedure_page: "https://web.larioja.org/oficina-electronica/tramite?n=24697"
    certificate_required: "CONDICIONAL"
    signature_required: "CONDICIONAL"
    js_client: "NO_VERIFICADO"
    protocol_family: "NO_VERIFICADO"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Tramitación electrónica con certificado y AutoFirma cuando el trámite lo requiere."
    protocol_evidence: "Las páginas oficiales citan certificado y AutoFirma sin ABI ni endpoint portal-specific."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["D03", "A19A", "A19B", "A19C"]
    reason: "Cliente JS, formato, algoritmo, callback, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Inspeccionar el trámite publicado y sus assets sin presentar datos."

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
    surface_name: "Portal institucional del Consell Insular de Menorca"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.cime.es"
    official_site: "https://www.cime.es/"
    e_sede: "https://seuelectronica.cime.es/"
    entry_url: "https://www.cime.es/"
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
    evidence_ids: ["D12", "I01A", "I01B"]
    reason: "Certificado, firma, ABI, formato, algoritmo, endpoint y TLS cliente no verificados para el portal informativo."
    reviewed_at: "2026-07-16"
    next_gate: "Seleccionar una operación administrativa en la sede separada y revisar su contrato específico."

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
    origin: "https://seu.conselldemallorca.net"
    official_site: "https://seu.conselldemallorca.net/"
    e_sede: "https://seu.conselldemallorca.net/"
    entry_url: "https://seu.conselldemallorca.net/"
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
    evidence_ids: ["I02B"]
    reason: "Cliente JS, familia de protocolo, formato, algoritmo, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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
    evidence_ids: ["I03B"]
    reason: "Cliente JS, familia de protocolo, formato, algoritmo, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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
    entry_url: "https://sede.cabildofuer.es/eAdmin/Sede.do"
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
    evidence_ids: ["I09B"]
    reason: "Cliente JS, familia de protocolo, formato, algoritmo, endpoint y TLS cliente no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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
    surface_name: "Portal oficial de Diputación Provincial de Alicante"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.diputacionalicante.es"
    official_site: "https://www.diputacionalicante.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.diputacionalicante.es"
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
    evidence_ids: ["D06", "DP01A", "DP01B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0140"
    surface_key: "diputacion-alava-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "País Vasco"
    province_or_municipality: "Álava (provincia)"
    institution_name: "Diputación Foral de Álava"
    surface_name: "Portal oficial de Diputación Foral de Álava"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://web.araba.eus"
    official_site: "https://web.araba.eus/es/home"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://web.araba.eus/es/home"
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
    evidence_ids: ["D06", "DP02A"]
    reason: "Propietario, origin y mención condicionada a certificado/firma revisados; procedimiento exacto y seis campos técnicos no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0141"
    surface_key: "diputacion-albacete-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Castilla-La Mancha"
    province_or_municipality: "Albacete (provincia)"
    institution_name: "Diputación Provincial de Albacete"
    surface_name: "Portal oficial de Diputación Provincial de Albacete"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.dipualba.es"
    official_site: "https://www.dipualba.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dipualba.es"
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
    evidence_ids: ["D06", "DP03A", "DP03B"]
    reason: "Propietario y origin revisados; certificado, firma, procedimiento y los seis campos técnicos permanecen no verificados."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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
    surface_name: "Portal oficial de Diputación Provincial de Ávila"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.diputacionavila.es"
    official_site: "https://www.diputacionavila.es"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.diputacionavila.es"
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
    evidence_ids: ["D06", "DP05A", "DP05B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.dip-badajoz.es"
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
    evidence_ids: ["D06", "DP06A", "DP06B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

  - inventory_id: "ES-PUB-0145"
    surface_key: "diputacion-barcelona-portal"
    administrative_level: "PROVINCIAL"
    autonomous_community: "Cataluña"
    province_or_municipality: "Barcelona (provincia)"
    institution_name: "Diputació de Barcelona"
    surface_name: "Portal oficial de Diputació de Barcelona"
    surface_type: "PORTAL_SERVICIO"
    origin: "https://www.diba.cat"
    official_site: "https://www.diba.cat/es/"
    e_sede: "NO_VERIFICADO"
    entry_url: "https://www.diba.cat/es/"
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
    evidence_ids: ["D06", "DP07A", "DP07B"]
    reason: "Origin primario revisado; certificado, firma, procedimiento y seis campos técnicos no verificados para esta superficie."
    reviewed_at: "2026-07-16"
    next_gate: "Revisar un procedimiento vigente hasta antes de autenticación o envío y delimitar su contrato exacto."

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

La entrada oficial se conserva como navegación inventariada. El profile no
concede firma porque no existe evidencia suficiente del transporte downstream,
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
    protocol_family: "CLAVE_GATEWAY_UNVERIFIED"
    signature_format: "NO_VERIFICADO"
    signature_algorithm: "NO_VERIFICADO"
    endpoint: "NO_VERIFICADO"
    discovery_state: "REVIEWED"
    inventory_status: "BROWSE_ONLY"
    operation_summary: "Consulta de la convocatoria de homologación y convalidación"
    protocol_evidence: "La entrada oficial fue revisada; no se observó un contrato downstream suficiente para exponer certificado o firma."
    client_tls_auth: "NO_VERIFICADO"
    evidence_ids: ["LIVE-EDUCACION-ENTRY-2026-07-22"]
    reason: "Transporte downstream de certificado y callback no verificados; firma bloqueada."
    reviewed_at: "2026-07-22"
    next_gate: "Obtener evidencia pública o autenticada controlada del transporte exacto sin realizar presentación jurídica."
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
7. Hay cuatro entradas con evidencia E2E delimitada y una sola evidencia exacta de `ClientCertRequest`; ningún otro registro hereda esos resultados.
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
[D12]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas/WP_EELL/WP_CabildosConsejos.html

### Evidencia portal-specific

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

### Evidencia de comunidades y ciudades autónomas

[A01A]: https://www.juntadeandalucia.es/servicios/sede/sobre-sede/titularidad.html
[A01B]: https://www.juntadeandalucia.es/servicios/sede
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
[DP04A]: https://www.dipalme.org
[DP05A]: https://www.diputacionavila.es
[DP05B]: https://diputacionavila.sedelectronica.es/
[DP06A]: https://www.dip-badajoz.es
[DP06B]: https://sede.dip-badajoz.es/
[DP07A]: https://www.diba.cat/es/
[DP07B]: https://seuelectronica.diba.cat/es/suport-a-la-tramitaci%C3%B3
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
[EDU-REG-2026-08-17]: https://www.educacionfpydeportes.gob.es/servicios-al-ciudadano/catalogo/general/20/203317/italia/laboral-liceo-cervantes-roma-2026.html
[CDTI-CERT-2026-08-16]: https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx
[MITECO-REG-2026-08-17]: https://www.miteco.gob.es/es/costas/participacion-publica/30-cnc12-07-30-0006.html
[MAEC-REG-2026-08-16]: https://www.exteriores.gob.es/Consulados/monterrey/es/ServiciosConsulares/Paginas/index.aspx?scca=Inscripci%C3%B3n+Consular&scco=M%C3%A9xico&scd=198&scs=Baja+del+Registro+de+Matr%C3%ADcula
[CULTURA-REG-2026-08-17]: https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[JUVENTUD-REG-2026-08-17]: https://juventudeinfancia.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General
[IGUALDAD-REG-2026-08-17]: https://igualdad.sede.gob.es/servicio?id=Registro-Electrónico-General
[DEFENSA-REG-2026-08-17]: https://sede.defensa.gob.es/
[MITES-CERT-2026-08-17]: https://sede.mites.gob.es/inicio/detalleProcedimiento/38
