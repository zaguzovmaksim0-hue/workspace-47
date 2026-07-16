# Proceso reproducible de descubrimiento de portales públicos

Fecha de la especificación: 2026-07-16

Este proceso mantiene el inventario de portales como un censo fechado de lo
descubierto, no como una afirmación atemporal de que existe una lista finita de
«todos los sitios». La cobertura se mide contra snapshots concretos de fuentes
oficiales y cada promoción técnica conserva su evidencia.

## 1. Frontera de seguridad

El crawler local `tools/public_portal_inventory.py` es deliberadamente
read-only:

- acepta únicamente seeds HTTPS explícitos de un JSON local validado;
- no usa cookies persistentes, autenticación, certificados, claves ni
  credenciales;
- solo ejecuta `GET` público y sigue redirects HTTPS limitados dentro del
  origin inicial o de origins exactos declarados en el seed;
- inspecciona la entrada indicada y sus scripts same-origin, hasta los límites
  configurados;
- no sigue enlaces de trámite descubiertos, no envía formularios y no ejecuta
  `afirma://`, `intent://` ni JavaScript;
- no llama a endpoints PRE/POST, Storage/Retrieve u otros endpoints extraídos;
- elimina query y fragment de toda evidencia persistida, salvo selectores
  públicos declarados por nombre y con valor restringido;
- guarda hashes, tamaños, etiquetas cerradas y URLs sanitizadas, nunca cuerpos,
  cookies, challenges, payloads, documentos o datos personales;
- bloquea esquemas no HTTPS, userinfo, IP literals, localhost, hostnames no
  exactos y rutas ambiguas.

Un match estático siempre sale como `BROWSE_ONLY`. El crawler no puede asignar
`VERIFIED_CONTRACT`, crear un `SiteProfile` ni ampliar una allowlist.

## 2. Fuentes enumeradoras y claves

Las fuentes se cruzan porque ninguna contiene por sí sola instituciones,
portales, procedimientos y contratos técnicos.

| Fuente oficial | Función en el censo | Clave primaria y límite |
| --- | --- | --- |
| [DIR3][DIR3] y su [API JSON][DIR3-API] | Spine de AGE, CCAA, EELL, universidades y otras instituciones. | Código DIR3. No prueba que exista una sede o un protocolo. |
| [Directorio de sedes AGE][AGE-SEDES] | Seeds oficiales de sedes de la AGE; el dataset declara actualización diaria. | URL publicada, después normalizada por origin. Puede cambiar por redirect. |
| [PAG: portales públicos][PAG] | Índices AGE, CCAA, entidades locales, diputaciones, cabildos/consells y ayuntamientos. | Relación fuente-enlace; estar listado prueba como máximo existencia pública. |
| [SIA][SIA] | Procedimientos y servicios por administración. | Código SIA. Los exports de hasta 10 000 filas se particionan hasta no truncarse. |
| [INE: relación de municipios][INE] | Denominador geográfico anual de municipios. | Código INE de cinco cifras. No contiene URL ni evidencia de firma. |
| [BDGEL][BDGEL] | Diputaciones, cabildos/consells, ayuntamientos y sector público local dependiente. | Código BDGEL y, cuando corresponda, código INE. No prueba una sede. |
| [API de datos.gob.es][DATOS-API] | Descubre feeds públicos estatales, autonómicos y locales. | URI de dataset y distribution. Keywords no prueban un contrato. |
| [RUCT][RUCT] | Confirma el carácter público de universidades. | Institución RUCT; la sede se confirma después en dominio oficial. |

Las distribuciones DIR3 no se fijan en código: cada ola lee de nuevo el array
`distribution` del API, exige MIME y tamaño esperados, comprueba ZIP magic para
XLSX y valida el esquema de columnas. Si una distribución falla, se conserva el
último snapshot válido y la rama queda incompleta de forma explícita.

## 3. Modelo de datos de entrada

El scanner recibe un objeto JSON estricto de schema 1:

```json
{
  "schema_version": 1,
  "snapshot_id": "2026-07-age-wave-1",
  "snapshot_date": "2026-07-16",
  "seeds": [
    {
      "seed_id": "ES-PUB-0001",
      "institution_name": "Institución pública",
      "administrative_level": "ESTATAL",
      "autonomous_community": "",
      "province_or_municipality": "",
      "source_url": "https://catalogo-oficial.example.es/entrada",
      "entry_urls": ["https://sede-oficial.example.es/"],
      "public_query_keys": [],
      "allowed_redirect_origins": []
    }
  ]
}
```

Los dominios `.example.es` anteriores son ilustrativos y no se ejecutan. Los
seeds reales proceden de snapshots de las fuentes de §2. Campos desconocidos,
IDs repetidos, enums desconocidos, archivos symlink o URLs ambiguas invalidan
la entrada completa.

`public_query_keys` se usa solo para selectores públicos estables como un código
de provincia. Nombres sensibles (`token`, `session`, `code`, `state`,
`signature`, `certificate`, `password` y equivalentes) están prohibidos aunque
se declaren.

`allowed_redirect_origins` contiene únicamente origins HTTPS exactos cuya
relación con la superficie ya consta en la fuente. Un redirect same-origin está
permitido; un redirect cross-origin no declarado, o con query no declarada, se
detiene antes del siguiente request.

## 4. Ejecución

Primero se valida siempre en modo fixture, sin red:

```bash
python -m unittest discover -s tools/tests -p 'test_*.py' -v
python -m py_compile \
  tools/public_portal_inventory.py \
  tools/age_sede_directory.py
python tools/public_portal_inventory.py \
  --input "$HOME/.local/state/portal-inventory/seeds.json" \
  --offline-fixtures "$HOME/.local/state/portal-inventory/fixtures.json" \
  --output "$HOME/.local/state/portal-inventory/candidates.jsonl"
```

Una ola pública se habilita de forma explícita con `--live`:

```bash
python tools/public_portal_inventory.py \
  --input "$HOME/.local/state/portal-inventory/seeds.json" \
  --live \
  --output "$HOME/.local/state/portal-inventory/candidates.jsonl"
```

El directorio AGE tiene un enumerador dedicado. Este realiza un único `GET`
del índice, no abre las sedes enlazadas, colapsa anchors repetidos dentro de una
ficha y duplicados interministeriales, y conserva una URL solo después de
aplicar la política HTTPS exacta. La URL sessionizada publicada para CIEMAT se
reduce a su origin cerrado; ni path, query ni valores remotos llegan al JSONL:

```bash
python tools/age_sede_directory.py \
  --live \
  --snapshot-date 2026-07-16 \
  --output "$HOME/.local/state/portal-inventory/age-sedes-2026-07-16.jsonl"
```

La ejecución live exige el baseline estructural revisado del snapshot
2026-07-16: 22 ministerios, 81 fichas, 84 anchors y 79 pares únicos. Cualquier
delta detiene la materialización para que se revise la nueva estructura y se
actualice el baseline de forma explícita; no se absorben altas o cambios a
ciegas.

Los defaults limitan un body a 2 MiB, cuatro redirects, dos niveles de imports
JS, 32 intentos de script, 15 segundos por petición y un intervalo mínimo de
0,5 segundos por host. Los intentos fallidos consumen el mismo presupuesto y
depth cero impide cualquier fetch de script. Por entrada, el máximo de requests
queda acotado por `(1 + max_assets) × (1 + max_redirects)`.

El scanner procesa un request cada vez y no usa proxies de entorno. Cada
resolución DNS tiene timeout, debe devolver solo direcciones públicas y produce
el set inmutable al que se conecta ese request; el hostname original se conserva
para `Host`, SNI y hostname verification con el trust store TLS del sistema. No
se realiza una segunda resolución entre check y conexión. Este transporte sigue
sin ser apto para secretos y el proceso no tiene acceso a ninguno.

El JSONL se escribe de forma atómica y se solicita modo `0600`. Debe mantenerse
en almacenamiento privado de Termux: el almacenamiento compartido de Android
puede imponer permisos más amplios y no preservar bits POSIX. El contenido es
determinista para el mismo input y fixtures: no incorpora reloj de ejecución,
orden de red ni mensajes remotos.

## 5. Fingerprints y clasificación

Se registran etiquetas cerradas para:

- AutoScript, MiniApplet y Cliente @firma;
- referencias AutoFirma, `afirma://` e `intent://` ligado a AutoFirma;
- `sign`, `coSign`, `counterSign` y `selectCertificate`;
- PRE/POST, `TriPhaseSignatureService`, Storage y Retrieve;
- CAdES, PAdES, XAdES y FacturaE;
- algoritmos RSA declarados en el cliente.

Los endpoints candidatos solo se conservan como HTTPS origin/path sin query y
nunca se invocan. La confianza de discovery es:

| Valor | Significado |
| --- | --- |
| `NONE` | No se encontró un indicador cerrado. |
| `OBSERVED_STATIC` | Se encontró al menos un literal o símbolo aislado. |
| `LIKELY_FAMILY` | Hay dos señales correlacionadas, por ejemplo cliente más operación o operación más contrato. |

Ninguno equivale a `VERIFIED_CONTRACT`. Un analista debe confirmar operación,
origin iniciador, callback, formato, mode/packaging, algoritmo, properties,
endpoint y binding de resultado en código oficial vigente. Una referencia
genérica a AutoFirma o certificado permanece `BROWSE_ONLY`.

`ClientCertRequest` no es un fingerprint textual fiable. Solo una observación
runtime delimitada puede registrar `TLS_CERT_REQUEST_OBSERVED`; requiere host,
puerto, key types, principals, decisión y resultado, y aun así no habilita
`proceed()` sin profile exacto y consentimiento.

## 6. Olas y criterio de completitud

Orden de materialización:

1. AGE: directorio diario de sedes, DIR3, SIA y sector institucional.
2. Las 17 CCAA, Ceuta y Melilla, con una cola por territorio.
3. Las 41 diputaciones y todos los cabildos/consells publicados por PAG.
4. Todos los municipios del snapshot INE, contrastados con BDGEL, DIR3 y los
   catálogos oficiales enlazados por PAG.
5. Universidades públicas DIR3/SIA confirmadas contra RUCT.
6. Otras instituciones públicas, registros, justicia, contratación,
   subvenciones y boletines descubiertos en SIA, DIR3, INVENTE/BDGEL y
   datos.gob.es.

Cada rama de enumeración termina en uno de estos estados de pipeline:

- `COMPLETE_FOR_SOURCE_SNAPSHOT`;
- `INCOMPLETE_SOURCE_PARTITION`, por ejemplo export SIA truncado;
- `SOURCE_UNAVAILABLE_WITH_LAST_GOOD_SNAPSHOT`;
- `PENDING_ENTITY_TO_PORTAL_RESOLUTION`.

La contabilidad publicada incluye, sin porcentaje nacional inventado:

- entidades enumeradas por fuente y snapshot;
- entidades con origin oficial resuelto;
- superficies públicas accesibles;
- candidatos con fingerprint estático;
- contratos revisados;
- profiles implementados;
- E2E confirmados;
- entradas pendientes, inaccesibles, no soportadas o retiradas y su razón.

Una institución puede tener varios origins y un origin multi-tenant puede
servir a muchas instituciones. Por ello se conserva la relación many-to-many
`entity ↔ surface`; no se deduplica por nombre.

## 7. Cadencia y delta

- Diario: delta del directorio AGE y URLs contractuales activas.
- Mensual: DIR3, SIA particionado, PAG, BDGEL y datasets relacionados de
  datos.gob.es.
- Anual y ante publicación de cambios: spine municipal INE y verificación
  RUCT.
- Antes de cada release: origins/endpoints activados, hashes de JS contractual,
  redirects, TLS y evidencia de cada profile.

Cada resultado del scanner registra URL de fuente, fecha de snapshot,
ETag/Last-Modified cuando exista, SHA-256 y tamaño del recurso público. El
snapshot de cada enumerador añade el resultado de validación de schema. Las altas,
cambios y desapariciones se revisan; una desaparición produce tombstone, no
borrado inmediato. Ningún delta activa código automáticamente.

Este milestone implementa el scanner estático, su formato de seeds y el
generador específico del directorio de sedes AGE. Los generadores de DIR3, SIA,
los demás índices PAG, INE, BDGEL y RUCT se añaden y fijan a snapshots en las
olas de §6; hasta entonces las URLs se preparan desde la fuente oficial y se
revisan antes de ejecutar `--live`. La existencia de esta especificación no se
cuenta como una cola enumeradora materializada.

## 8. Promoción al producto

La salida del crawler entra en una revisión separada:

```text
official enumerator
  -> public surface candidate (BROWSE_ONLY)
  -> static correlated evidence (BROWSE_ONLY)
  -> exact contract review (VERIFIED_CONTRACT)
  -> adapter/profile + contract tests (IMPLEMENTED_NOT_E2E)
  -> safe accepted portal flow (VERIFIED_E2E)
```

`REQUIRES_AUTHENTICATED_RESEARCH`, `UNSUPPORTED_PROTOCOL`, `INACCESSIBLE` y
`DEPRECATED` necesitan una razón portal-specific y evidencia; nunca se asignan
por semejanza de plataforma. El inventario Markdown se actualiza después de
cada revisión o E2E, junto con conteos exactos por los ocho estados.

[DIR3]: https://datos.gob.es/es/catalogo/e05251701-directorio-comun-de-unidades-organicas-y-oficinas-dir3
[DIR3-API]: https://datos.gob.es/apidata/catalog/dataset/e05251701-directorio-comun-de-unidades-organicas-y-oficinas-dir3.json
[AGE-SEDES]: https://sede.administracion.gob.es/sedes-electronicas
[PAG]: https://administracion.gob.es/pag_Home/atencionCiudadana/SedesElectronicas-y-Webs-Publicas/websPublicas.html
[SIA]: https://administracion.gob.es/pag_Home/espanaAdmon/SIA.html
[INE]: https://www.ine.es/dyngs/INEbase/es/operacion.htm?c=Estadistica_C&cid=1254736177031&idp=1254735976614&menu=ultiDatos
[BDGEL]: https://datos.gob.es/es/catalogo/e05250001-base-general-de-datos-de-entidades-locales
[DATOS-API]: https://datos.gob.es/es/accessible-apidata
[RUCT]: https://www.ciencia.gob.es/Universidades/RUCT.html
