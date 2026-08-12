# Insular portal research: Eivissa, Formentera, La Palma — generation 47 — 2026-08-11

## Scope and safety boundary

This slice used only bounded unauthenticated HTTPS GET requests to public pages and same-origin static
JavaScript for three already-catalogued insular electronic-administration surfaces. No cookie jar was
used; no issued session cookie was replayed. No login, certificate selection, Cl@ve flow, form POST,
procedure submission, signing call, file upload, payment, APK launch, ADB, or device-control workflow
was performed. Public response bodies were retained only in a temporary Termux cache for bounded
inspection and are not Git-tracked.

## Consell d'Eivissa — ES-PUB-0122

The official public entry `https://seu.conselldeivissa.es/` returned HTTP 200 and identifies itself as
STA / Servicios Telemáticos Avanzados, version `2602.0.3`. Its public catalog exposes a stable detail
for `Presentación de documentación a expedientes en trámite` with request id
`6269002701678511205043`.

That unauthenticated detail page exposes the product choice immediately before the protected
transition:

- hidden `dboidSolicitud=6269002701678511205043`;
- hidden `autoFirma=false`;
- hidden target `Relec/TramitaForm`;
- `Con certificado digital` invokes `submitFormulario(false)`;
- `Con Autofirma` invokes `submitFormulario(false,true)`.

The public `catserv.js` (4,892 bytes, SHA-256
`ffdf496a7486c190e4dc2b5e33ae785d99acb1a01daecb1aef2a3069360227e3`) implements that switch by
setting `autoFirma=true` and then submitting the form to the authenticated `/frame.jsp` boundary. The
public procedure page and its `webAppsFwk.js` (74,175 bytes, SHA-256
`25124759cbea11edae00f1196fce47ee1c04361bb10de9c76e5e83101df933d6`) do not expose AutoScript,
MiniApplet, `STAAutofirmaLote`, a signing algorithm/format tuple, a local transport contract, or a
signature callback/result shape before that submission boundary.

This is direct evidence that the live procedure supports an AutoFirma branch, but it is not enough to
construct a fail-closed runtime profile. `ES-PUB-0122` remains `BROWSE_ONLY`; no profile, origin,
adapter, catalog binding, or release status changes in this checkpoint.

## Consell de Formentera OVAC — ES-PUB-0124

The catalogued root `https://ovac.conselldeformentera.cat/` returned HTTP 200 and contains a public
meta-refresh to the current OVAC entry under `/ovac/catala/emiservicio/`. Following that public GET
without cookies also returned HTTP 200. The public page states that some requests can be initiated
online and separates unidentified services from the citizen folder; the disconnected identity button
is an HTTP form submission and was not used.

Public first-party JavaScript confirms an older ABSIS/EAD signature seam but does not expose a complete
cryptographic contract. In `absEdiForm.js` (301,072 bytes, SHA-256
`3b8367397a8b2d532f3414245bbcf8916526f43e6fc687371555dcc7d4dc5d7a`), the form path checks
`hayElementoFirma(theForm)` and calls `firmar(theForm, theForm.FIRMA_SOLICITUD, ...)`; the public code
also contains certificate-selection callbacks and subsequently submits `FormLoginCertificatDigital`.
The selected public script set contains no AutoFirma/AutoScript/MiniApplet marker and no verified
`SHA*withRSA`, CAdES, PAdES, XAdES, PKCS#7/CMS, local-transport, or result-callback tuple that can be
safely bound to the Android runtime.

The public information page titled `Certificats digitals i signatures electròniques admeses` is
reachable without authentication, but the current body does not publish the missing signer ABI.
Therefore `ES-PUB-0124` remains `BROWSE_ONLY`; the presence of generic signing functions is not treated
as a protocol contract.

## Cabildo Insular de La Palma — ES-PUB-0130

The official public entry `https://sedeelectronica.cabildodelapalma.es/` returned HTTP 200. Public STA
pages for signature information and the catalog load the current first-party signing runtime before
any authentication or form submission. Three contract-defining resources returned HTTP 200:

- `/sta/resources/js/autoscript.js` — 222,756 bytes, SHA-256
  `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`;
- `/sta/resources/js/sta-autofirma-lote.js` — 12,522 bytes, SHA-256
  `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`;
- `/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.3` — 86,696 bytes, SHA-256
  `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`.

Those three files are byte-for-byte identical to the contract resources already verified for the
research-ready Extremadura STA surface and the preserved Melilla STA implementation seam. The exact
public contract therefore remains:

- `STAAutofirmaLote.firmarLote(params, onSuccess, onError)`;
- AutoScript v1.9.0 `createBatch`, `addDocumentToBatch`, and `signBatchProcess`;
- default `SHA256withRSA`, `CAdES`, suboperation `sign`, `stopOnError=false`;
- PAdES adds `signatureSubFilter=ETSI.CAdES.detached`;
- XAdES adds `mode=implicit`;
- CAdES has no format-specific extra parameter;
- `batchPreSignerUrl`, `batchPostSignerUrl`, and per-document `datareference` are backend-supplied and
  must never be invented or broadened cross-origin;
- `startFirma(signInfo)` invokes the batch helper and returns the opaque result through
  `PRESENTAR_FIRMA` as `validationResponse=JSON.stringify(resultado)`;
- the portal loads AutoFirma through its existing safe loader rather than exposing a new transport to
  infer.

The public catalog itself also carries an AutoFirma installation notice and currently lists active
telematic procedures, including `AACC_006_05` (`6269000080304944107769`), whose published window is
2026-07-30 through 2026-08-20. A bounded direct GET with that id did not resolve a standalone public
detail and no POST/authenticated transition was attempted, so this checkpoint does not claim an exact
procedure-level E2E path.

The combination of a live official STA surface, public AutoFirma integration notice, and an exact
byte-identical already-understood batch ABI is sufficient to move `ES-PUB-0130` to
**implementation-ready in the research queue only**. Product truth remains unchanged: the catalog
entry stays `BROWSE_ONLY`, no new release profile is enabled, and no E2E claim is made. Any future
implementation must use a La-Palma-specific fail-closed origin policy and the existing shared STA seam,
then remain at most `QA_ONLY` / `IMPLEMENTED_NOT_E2E` until separate physical evidence exists.

## Queue impact

This checkpoint classifies three additional unbound insular surfaces. Eivissa and Formentera remain
research-only blockers; La Palma becomes the next verified shared-STA implementation candidate after
the already-ordered Sevilla, preserved Melilla, and `extremadura-tramites` (`ES-PUB-0109`) slices.
No catalog/status counters change in this research-only commit.
