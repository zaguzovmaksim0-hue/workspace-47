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

## Research queue result

No candidate was promoted to implementation-ready in this research slice. The classified public
research buffer remains at least 16 surfaces. Current exact priority after the in-flight JCCM,
Sevilla, and Melilla work is: `justicia-sede-judicial`, `age-acceda`, `sepe-sede`,
`mjusticia-sede`, then `asturias-sede-tramite-autofirma` among the next researched candidates.
