# Portal research evidence — generation 44 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated GET requests. No credentials, client
certificate, authenticated navigation, signing, form POST, upload, payment, administrative
submission, APK launch, ADB, or device-control workflow was used. Redirects were followed only to
classify the public pre-auth boundary. No authentication link or POST form was activated.

## Asturias — signature-check helper remains unreachable

The official public page
`https://miprincipado.asturias.es/utilidades/comprobacion-firma` returned HTTP 200 and still exposes
the public simulation call:

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

The fixed payload remains Base64 `SG9sYQ==` (`Hola`), and the format remains `XAdES`. The algorithm
and extra parameters are still delegated to first-party helpers rather than published inline.

The required official helper `https://www30.asturias.es/Esign2/esign.jsp` remains unavailable from
both current network routes: the configured proxy returned CONNECT HTTP 502, while a direct no-proxy
request failed DNS resolution (`curl` exit 6). No algorithm or extra-property value is inferred from
another portal or from AutoFirma defaults. `asturias-sede-tramite-autofirma` therefore remains
research-only.

## SEPE — public procedure launches stop at authentication boundary

The official SEPE AutoFirma FAQ remains public at
`https://sede.sepe.gob.es/portalSede/firma-electronica/preguntas-frecuentes/autofirma.html` and
confirms AutoFirma/certificate use in general, but publishes no algorithm, format, payload, callback,
or transport ABI.

Four current public procedure-information pages were fetched with HTTP 200. Their HTML contains no
`AutoScript`, `MiniApplet`, `SHA*withRSA`, `XAdES`, `PAdES`, or `CAdES` contract string. Three
concrete procedure launches were then followed with bounded unauthenticated GETs only:

- `https://sede.sepe.gob.es/PSolicitudUnicaWEB/solicitudUnica` redirects through the official SEPE
  authentication gateway to the protected-resource login page. The final public page offers Cl@ve
  and digital-certificate authentication but no signing ABI.
- `https://sede.sepe.gob.es/PBajaPrestacion/flows/bajaprestacion` follows the same protected-resource
  authentication pattern and likewise exposes only Cl@ve/digital-certificate login choices before
  the procedure.
- `https://sede.sepe.gob.es/DServiciosPrestanetWEB/CertificadosPrestaWeb.do` returns HTTP 200 before
  authentication, but the page exposes authentication choices and a POST form to
  `/DServiciosPrestanetWEB/TipoAutenticadoAction.do`; it contains no public AutoFirma signing ABI.
  That POST was not submitted.

The public launch boundary therefore confirms that the implementation-relevant signing contract, if
any, lies after authentication or a POST transition on the observed procedures. No safe public GET
currently proves a portal-specific algorithm/format/payload/callback tuple. `sepe-sede` remains
`BROWSE_ONLY` / research-only and no profile should be added from this evidence.

## Queue impact

No new implementation-ready candidate was promoted by this slice. The classified research buffer
remains at least 16 surfaces. The implementation-ready order remains Sevilla ATSE (pending terminal
Cloud GREEN evidence), preserved Melilla STA, then `extremadura-tramites`. SEPE and Asturias stay in
the research-only queue.

## ACCEDA idp/509 handoff and MPTMD public AutoFirma lead

SEPE's public benefits page links the official ACCEDA chooser
`https://sede.administracionespublicas.gob.es/procedimientos/choose-ambit/idp/509`. A bounded GET
returned HTTP 200 with no AutoFirma/AutoScript/MiniApplet contract and no form submission boundary;
the page is only an ambit chooser. Two official targets published by that chooser are current MPTMD
procedure pages at `https://mptmd.sede.gob.es/procedimiento/ambitos?idProc=133655` and
`...?idProc=134479`. Both returned HTTP 200 unauthenticated and exposed only the public Cl@ve login
entry before procedure state.

Those MPTMD pages load four same-origin JavaScript files. The first-party
`/.resources/ac2-front/webresources/js/ac2-formularios.js` was fetched at SHA-256
`ac1983eb5ed614c9f446ebbfbea38160a4d28ea99080cbb2ed0adf8a62d1c7cc`. It contains a generic
post-auth AutoFirma orchestration: after a POST to `/.rest/formulario/v1/expediente` creates an
expediente and returns `idExpediente` plus `idDocumentoSolicitud`, `startAutofirma()` downloads the
server-created document, calls `doSignAsPromise(file, nifSol)`, and later uploads the signed file with
a POST to `/.rest/formulario/v1/autofirma`. Neither POST was executed.

The pre-auth HTML loads exactly four same-origin scripts (`ac2-commons.js`,
`ac2-detalleExpediente.js`, `ac2-formularios.js`, and `ac2-usuariosLogin.js`). Across that exact
public script set, `doSignAsPromise` is referenced but not defined, and no `AutoScript`, `MiniApplet`,
`SHA*withRSA`, `XAdES`, `PAdES`, or `CAdES` tuple defining that function is present. The public page
therefore proves that MPTMD has a later AutoFirma workflow but does **not** prove the local signing
ABI needed for an autonomous profile before the authenticated/POST boundary.

This evidence does not strengthen the existing `age-acceda` contract into an implementation-ready
profile. It creates a separate research lead for inventory surface `ES-PUB-0072`
`age-ministerio-de-politica-territorial-y-memoria-democratica`; no inventory or catalog state is
changed by this documentation-only slice. The next safe gate is a public first-party definition and
procedure binding for `doSignAsPromise`, if one becomes observable without authentication or form
submission.
