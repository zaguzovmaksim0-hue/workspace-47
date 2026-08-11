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
