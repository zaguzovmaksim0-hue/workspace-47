# Evidencia de Implementación: ES-PUB-0003 Plataforma ACCEDA (`age-acceda`)

**Fecha:** 2026-08-16  
**Portal:** Plataforma ACCEDA — Sede electrónica de la Administración General del Estado (`age-acceda`, `ES-PUB-0003`)  
**Estado:** `IMPLEMENTED_NOT_E2E` (promovido desde `VERIFIED_CONTRACT`)  
**Origin estricto:** `https://sede.administracionespublicas.gob.es`  
**Start URL:** `https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES`  
**Protocolo:** AutoScript Local PAdES Detached (`age-acceda-local-pades-v1`)

---

## 1. Resumen Ejecutivo

Se ha implementado el soporte local PAdES para la **Plataforma ACCEDA** (`age-acceda`), satisfaciendo estrictamente el contrato estático analizado en `doSignSolicitud` de `afirma_funciones.js`. El estado del portal en el inventario maestro `docs/compatibility/all-spanish-public-portals-inventory.md` se actualiza de `VERIFIED_CONTRACT` a `IMPLEMENTED_NOT_E2E`, con activación restringida a `QA_ONLY` en el catálogo de perfiles hasta que se complete una prueba de aceptación E2E con formulario real.

---

## 2. Contrato Estático del Portal (Evidencias P15, P15A, P15B, D11)

### 2.1. Invocación de Firma (`afirma_funciones.js`)

El archivo público `https://sede.administracionespublicas.gob.es/js/afirma/afirma_funciones.js` expone la función `doSignSolicitud`:

```javascript
function doSignSolicitud(data, nif, tipo_certificado_logeado) {
    ...
    var params = "format=PAdES Detached\nexpPolicy=FirmaAGE\nnonexpired:true\n";
    ...
    AutoScript.sign(data, "SHA1withRSA", "PAdES", params, showSignResultCallback, showErrorCallback);
}
```

### 2.2. Parámetros y Callbacks

* **Cliente JS:** `AutoScript` (MiniApplet JS wrapper).
* **Algoritmo de firma:** `SHA1withRSA` (Capability `LEGACY_SHA1`).
* **Formato de firma:** `PAdES` (parámetros explícitos: `format=PAdES Detached\nexpPolicy=FirmaAGE\nnonexpired:true`).
* **Success callback:** `showSignResultCallback(signatureB64, certificateB64, extraData)`.
* **Error callback:** `showErrorCallback(errorType, errorMessage)`.
* **Sin servicios remotos:** No requiere endpoints `/StorageService`, `/RetrieveService`, ni trifásico.

---

## 3. Implementación Local

### 3.1. Modelo de Dominio y Coordinador de Firma

* Se agregó `SigningFormat.PADES` a `SigningModels.kt`.
* `SigningCoordinator.kt` mapea `SigningFormat.PADES` a `ProfileSignatureFormat.PADES` y asigna el nombre descriptivo `"PAdES"`.

### 3.2. Adaptador PAdES y Códec Delimitado (`AccedaPadesAdapter.kt`)

* **ID de Protocolo:** `age-acceda-local-pades-v1`.
* **`PadesDetachedCodec`:**
  * **Bounded PDF subset:** currently supports classic xref/trailer object graphs up to 512 KiB; xref streams/object streams and malformed structures fail closed with `PROTOCOL_FAILED` rather than emitting a signature.
  * Valida la estructura PDF (encabezado `%PDF-`, delimitación de tamaño `<= 524,288` bytes, presencia de `%%EOF`).
  * Construye la actualización incremental con objeto `/Type /Sig`, `/Filter /Adobe.PPKLite`, `/SubFilter /ETSI.CAdES.detached`.
  * Calcula con precisión atómica las posiciones de `/ByteRange [ 0 off1 off2 len2 ]` y reserva el campo `/Contents <...>` (16.384 caracteres hexadecimales = 8.192 bytes DER).
  * Genera el pre-firmado CMS (CAdES-BES) sobre los bytes del `ByteRange` utilizando BouncyCastle con atributos autenticados (`id-aa-signingCertificateV2`, `signingTime`, `messageDigest`).
  * En `complete`, inserta el valor de la firma RSA real en el `SignerInfo`, convierte los bytes DER a hexadecimal y los escribe en `/Contents`.
  * Valida criptográficamente el PDF resultante completo verificando el `ByteRange`, la estructura ASN.1 DER CMS y la firma RSA contra el certificado firmante.
  * **Zeroización:** Todas las estructuras intermedias y buffers de pre-firma (`pdfTemplate`, `byteRangeData`, `placeholderCms`, huella digital) se rellenan con ceros al invocarse `.close()`.

### 3.3. Intermediación y Enrutamiento en el WebView (`MiniAppletBridgeAdapter.kt` y `afirma_shim.js`)

* `MiniAppletBridgeAdapter.kt`:
  * Soporta formatos `"PAdES"` y `"PAdES Detached"`.
  * Función `isExactAccedaContract(...)` que valida estrictamente el perfil `age-acceda`, el origen `https://sede.administracionespublicas.gob.es`, el algoritmo `SHA1_WITH_RSA`, las propiedades fijas y el encabezado/delimitación PDF.
* `afirma_shim.js` y `AfirmaJavascriptShim.kt`:
  * Inyección del flag `accedaCompatibilityEnabled`.
  * Intercepción específica en `interceptMiniAppletSign` para llamadas directas desde `https://sede.administracionespublicas.gob.es` con `SHA1withRSA` y `PAdES`.

### 3.4. Registro de Adaptadores y Catálogo

* `BuiltInProtocolAdapterRegistry.kt`: Se añadió la vinculación de `age-acceda` + `ProtocolOperation.SIGN` -> `AccedaPadesAdapter.ID`.
* `MainActivity.kt`: Se registró `AccedaPadesAdapter` en el `adapterResolver` del `SigningCoordinator`.
* `config/site_profiles_v1.json`: Perfil `age-acceda` actualizado con `compatibilityStatus: "VERIFIED_CONTRACT"`, `activation: "QA_ONLY"`, `capabilities: ["SIGN", "LEGACY_SHA1"]` y la política de operación PAdES correspondiente.
* `docs/compatibility/all-spanish-public-portals-inventory.md`: Registro `ES-PUB-0003` actualizado a `inventory_status: "IMPLEMENTED_NOT_E2E"` y métricas de resumen actualizadas (`IMPLEMENTED_NOT_E2E: 21`, `VERIFIED_CONTRACT: 0`).
* `public_portal_catalog_v1.json`: Regenerado determinísticamente mediante `tools/generate_public_portal_catalog.py`.

---

## 4. Brecha para E2E (Por qué `IMPLEMENTED_NOT_E2E` y no `VERIFIED_E2E`)

1. **Procedimiento Real:** La función `doSignSolicitud` es un helper estático; se requiere probar la interacción completa en un trámite administrativo específico de ACCEDA con credenciales gubernamentales reales.
2. **Validación del Servidor:** Confirmar que el backend de ACCEDA valida y acepta la firma PAdES incremental generada localmente sin discrepancias en validaciones de políticas o sellado de tiempo.

---

## 5. Verificación y Pruebas

* **Tests de Python:** 133 pruebas pasadas en `tools/tests/` (1 omitida) y 17 pruebas adicionales del generador de catálogo pasadas; `test_ci_policy.py` queda incluido en la suite principal.
* **Tests Unitarios Kotlin/Robolectric:**
  * `AccedaPadesAdapterTest.kt`: Valida pre-firma, completado PAdES, verificación criptográfica BouncyCastle, rechazo fail-closed de payloads inválidos/desajustes contractuales y zeroización de memoria.
  * `AccedaProfileCatalogBindingTest.kt`: Valida resolución `IMPLEMENTED_NOT_E2E` en QA, ausencia del perfil `QA_ONLY` en el registry de Release y políticas de origen; el catálogo público de Release conserva `VERIFIED_CONTRACT` deshabilitado.
  * `MiniAppletBridgeAdapterTest.kt`: Valida enrutamiento y normalización de peticiones `PAdES` de ACCEDA.
  * `AfirmaJavascriptShimTest.kt`: Valida inyección de flags y script JS para ACCEDA.
* **Validación PDF independiente:** Poppler `pdfinfo` abrió el PDF sintético como `Form: AcroForm`; `pdfsig 26.02.0` detectó `Signature1`, tipo `ETSI.CAdES.detached`, cobertura de todo el documento y `Signature Validation: Signature is Valid`. La advertencia de validez temporal corresponde al certificado sintético de pruebas y no se usa como evidencia E2E.
* **Integridad Git:** `git diff --check` ejecutado sin errores.
