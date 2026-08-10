# Portal research evidence — generation 42 — 2026-08-10

## Scope and safety boundary

This generation used only official public, unauthenticated, GET-only evidence. No credentials,
client certificate, authenticated navigation, signing, form POST, upload, payment, administrative
submission, APK launch, ADB, or device-control workflow was used. Volatile server-issued values were
observed only to classify their lifecycle and are intentionally not persisted here.

## Asturias — public signature-check utility

Official page:
`https://miprincipado.asturias.es/utilidades/comprobacion-firma`

The current public HTML loads `https://www30.asturias.es/Esign2/applet/1.2/miniapplet.js` and a
relative first-party script reference `/Esign2/applet/1.2/../../esign.jsp`, which resolves to
`https://www30.asturias.es/Esign2/esign.jsp`.

The inline public call remains:

```javascript
MiniApplet.sign(
    'SG9sYQ==',
    getAlgoritmoFirma(),
    'XAdES',
    getParamsFirma(),
    saveSignatureFunction0,
    showErrorFunction
);
```

`SG9sYQ==` decodes to ASCII `Hola`. The page therefore proves the exact fixed input, the `XAdES`
format, and the success/error callback boundary for this public simulation. It does **not** expose the
algorithm or extra parameters inline: those are delegated to `getAlgoritmoFirma()` and
`getParamsFirma()`.

A bounded request to `https://www30.asturias.es/Esign2/esign.jsp` could not resolve that host through
the direct Termux DNS path, while the configured proxy path returned a CONNECT 502. No first-party
definition of either helper was recovered. The candidate remains not implementation-ready; algorithm
and extra parameters must not be inferred from another portal or an AutoFirma default.

## AGE ACCEDA — public certificate validation surface

Official public surface:
`https://sede.administracionespublicas.gob.es/certificado/valida`

The current unauthenticated HTML loads these first-party signing resources:

- `/js/afirma/constantes.js` — SHA-256
  `150405151c4327bd88049b08a17943f13d82e5f811df2b9b194530e08ea55026`;
- `/js/afirma/afirma_funciones.js` — SHA-256
  `b522f95b00836f420cb9c52b0075b3f1db4856bf93c19cef2af3cdc563e7c6a5`;
- `/js/afirma/autoscript.js` — SHA-256
  `e5f17e93816d1875c57198917ed9fd1c6d6f9e71dd2d5c9fec3650d76544c713`.

The page's current submit hook assigns `afirma.sn`, `afirma.formulario`, `afirma.submit`, and a
server-issued `afirma.formularioweb`, then invokes `afirma.firmar(callback)`. Repeated bounded GETs
produced different `formularioweb` values. The values themselves are intentionally not retained;
the only durable conclusion is that the field is volatile/server-issued and must never be hard-coded.

The current first-party `afirma_funciones.js` independently exposes several AutoScript paths:

- `doSign(data)` uses algorithm/format selected from DOM fields and adds
  `format=XAdES Detached` plus `expPolicy=FirmaAGE` for its XAdES branch;
- `doSignSolicitud(data, nif, tipo_certificado_logeado)` uses `SHA1withRSA`, `PAdES`,
  `format=PAdES Detached`, `expPolicy=FirmaAGE`, `nonexpired:true`, and optional certificate filters;
- `showSignResultCallback` writes the returned signature to `#firma_formularioweb` and triggers its
  change handler.

However, none of the nine scripts directly loaded by the public `/certificado/valida` page defines
or links the observed `afirma.firmar` wrapper to one specific AutoScript branch. Co-location of
`doSignSolicitud()` is not sufficient to prove that linkage. ACCEDA therefore remains
`VERIFIED_CONTRACT`, not implementation-ready for a new runtime profile from this evidence alone.

## Justicia Sede Judicial — theme helper versus live procedure binding

The current first-party theme helper
`https://sedejudicial.justicia.es/o/sedjude-theme/js/libs/firma.js?t=1781026126000` was fetched with
SHA-256 `df9e4af8777b724a64c7a5fbbb772e5bd88ef99dc1ca504bb879bcb0e14ec1af`. It defines a concrete
`MiniApplet.sign` helper using `SHA256withRSA`, `PAdES`, `mode=implicit`, the
`#documentoDeclaracion` payload field, a success callback that obtains a same-origin token, and
submission of `#formFirmaBorrador`.

That library-level contract is not yet a public-procedure contract. A bounded GET-only crawl of 11
current procedure pages linked from the official `/tramites` surface returned HTTP 200 for every
page but found no `documentoDeclaracion`, `formFirmaBorrador`, `firma()` call, or `autofirma` DOM
binding. The portal therefore remains research-only: the global helper must not be attached to an
unobserved procedure flow.

## Ministerio de Justicia — public pre-auth login module

Three current public procedure URLs (`idp/75`, `idp/44`, and `idp/63`) redirect unauthenticated GETs
to their corresponding `https://sede2.mjusticia.gob.es/login/index/idp/<id>` page. The rendered login
page states that certificate signing may be required and that AutoFirma is required, but its current
DOM offers only the Cl@ve form and does not contain `#submitBtnCertificate`, `#formCertificate`,
`#pseudonym-signature`, or `#certificate`.

The same public page nevertheless loads the first-party module
`/js/modules/default/login/index.js?v2026081017`, SHA-256
`a9a173e74c2d09781856021856ba40be9d48748aa979cbcb1d9cbc611f6e489c`. That module documents an
inactive certificate branch which:

- calls `accAfirma.selectCertificate("pseudonym", ...)`;
- sends the selected certificate to `/login/get-info-from-pseudonym-certificate`;
- builds an XML payload containing the returned certificate serial number and the current local
  timestamp;
- sets `XAdES Detached`, implicit mode, and the returned serial number before
  `accAfirma.signData(...)`;
- on success writes the signature into `#pseudonym-signature` and submits `#formCertificate`.

All 10 first-party scripts directly loaded by the login page were scanned. None defines
`accAfirma`, `signData`, or the signing algorithm, and no first-party dynamic loader for that wrapper
was found. Because the branch is absent from the current DOM and the underlying wrapper/algorithm is
not public in the observed surface, `mjusticia-sede` is not implementation-ready.

## SEPE — public AutoFirma boundary

The public pre-auth endpoint redirects to the official FAQ
`https://sede.sepe.gob.es/portalSede/firma-electronica/preguntas-frecuentes/autofirma.html`. The current
page states that AutoFirma is needed when the user identified with a digital certificate performs a
procedure requiring a signature, and that AutoFirma communicates with the SEPE Sede to deliver the
completed signature. The page does not expose the algorithm, signature format, payload, callback, or
transport contract needed for a runtime profile. `sepe-sede` therefore remains research-only.

## Comunidad de Madrid — Registro Electrónico General

The official public Registro page links to the exact unauthenticated launch URL
`https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm` and explicitly
recommends AutoFirma for document signing. The launch page returns HTTP 200 and requires the user to
select a procedure form before POSTing it to `InicioDistribuidorProcesa.icm`. That upload/POST step is
outside the autonomous safety boundary and was not executed. The page's published equipment-check
link `https://gestiona.madrid.org/ereg_virtual/run/j/InicioRequisitosAFC.icm` currently returned HTTP
404. No public algorithm/format/payload/callback contract was recovered before the upload boundary, so
`comunidad-madrid-sede` remains research-only.

## Comunidad de Madrid — Cuenta Digital / external procedure route

The official public SPA surface `https://digital.comunidad.madrid/ext/53F1` exposes a first-party
configuration chain. The root shell loads `main.84fe736d0b3d70bb.js`; its first lazy chunk is
`948.c7e949fb454f7c61.js` (SHA-256
`92afddd42b59769a0ea02946ea2f3330ed379923d9b0464787ae7c0f86f37fb8`). That chunk points to the
first-party Cuenta Digital application configuration. Only non-sensitive routing/version metadata was
used from that configuration: Cuenta Digital v3.0.51 exposes route `firmar-documentos` backed by
`cudc_mf_procedures` v3.9.1 / `SignatureComponent`, while `ext/:procedure` is marked as requiring
authentication.

The same public configuration resource also contained credential-like/static authorization material.
Those values were neither retained, copied into the repository, used for any request, nor included in
this evidence. No authenticated API was called.

The procedures microfrontend remote entry
`https://gestiona.comunidad.madrid/cudc_mf_procedures/3.9.1/remoteEntry.js` has SHA-256
`598903446b7691ddfc593e91c824a24b0bc0b0dce1921177301e465fb922324c` and maps
`SignatureComponent` to chunk `6900.0b93eaca147e5d5d.js` (SHA-256
`f124f484f461df73d03962e413e4e9f19ef52f2441ff22c38320ca5e7d2e6e66`). That component is a
multiple-signature UI for the 2026–2027 school-canteen scholarship flow. It reads `id`/`number` query
parameters, obtains signer metadata through `SignsApiService.getSigners`, and sends the user's signing
action through `SignsApiService.sendSign`. It contains no AutoFirma, AutoScript, MiniApplet,
SHA*withRSA, PAdES, CAdES, or XAdES runtime string.

`@mova3/cudc-lib-data-services/signs` resolves to the published static chunk
`1191.ab499f58d559a08c.js`, SHA-256
`19e33f1109fe21c8300963a4d31671d2c4df05c77bd983a4f0c00e6adb6d10da`. That service proves the
boundary: `getSigners` and `sendSign` obtain an authenticated validation token and issue POST requests
to signing BFF paths (`/firmantes/get` and `/firmas`) with bearer authorization. Those API calls were
not executed. The observed surface is therefore an authenticated/server-mediated signing flow, not a
public browser-local AutoFirma ABI that Junta Firma Mobile can implement autonomously.

## Research queue result

No candidate was promoted to implementation-ready in this research slice. The classified public
research buffer remains at least 16 surfaces. Current exact priority after the in-flight JCCM,
Sevilla, and Melilla work is: `justicia-sede-judicial`, `age-acceda`, `sepe-sede`,
`mjusticia-sede`, then `asturias-sede-tramite-autofirma` among the next researched candidates.
