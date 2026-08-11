# AC2 shared AutoFirma boundary: Instituto Cervantes + Ministerio de Igualdad — generation 47 — 2026-08-11

## Scope and safety boundary

This slice used only bounded unauthenticated HTTPS GET requests to official public pages and
same-origin static JavaScript of the Instituto Cervantes and Ministerio de Igualdad electronic sedes.
No login was started; no Cl@ve flow, certificate selection, cookie jar, form POST, expediente creation,
file upload, signing operation, payment, APK launch, ADB, or device-control workflow was used.
Temporary public response bodies were kept only in the Termux cache for bounded inspection and are not
tracked by Git.

## Instituto Cervantes — ES-PUB-0049

`https://cervantes.sede.gob.es/` returned HTTP 200. The current public technical-requirements page
`https://cervantes.sede.gob.es/Requisitos` also returned HTTP 200 and explicitly states that AutoFirma
must be installed when signing electronically with a certificate in the sede; it separately states
that Cl@ve is required to initiate an expediente and access expediente/notification areas.

The unauthenticated root loads exactly four same-origin AC2 application scripts:

- `ac2-commons.js` — SHA-256 `a0602be1828809d6d6e5705175c30646361662104427f8ff42745be9d7e70156`;
- `ac2-detalleExpediente.js` — SHA-256 `a94e39ed8bf7301ac4383e845dfa9b86863ff06168adcd43419916fb39cbf962`;
- `ac2-formularios.js` — SHA-256 `ac1983eb5ed614c9f446ebbfbea38160a4d28ea99080cbb2ed0adf8a62d1c7cc`;
- `ac2-usuariosLogin.js` — SHA-256 `0dcb1bda71626e301181230c123c789454600430cb9e1cb7d0bbd4b0befc8a92`.

`ac2-formularios.js` exposes the same generic post-auth orchestration previously observed on MPTMD:
a server-created document is passed to `doSignAsPromise(file, nifSol)` and the result is consumed via
`signatureB64` before the signed file is sent to the server-side AutoFirma processing endpoint.
However, across the exact four public scripts, `doSignAsPromise` is referenced but not defined and no
`AutoScript`, `MiniApplet`, `SHA*withRSA`, CAdES, PAdES, or XAdES tuple is exposed.

The public pre-auth state therefore proves that this live AC2 tenant uses a later AutoFirma flow but
does not expose the local signing ABI required for a safe runtime profile. `ES-PUB-0049` remains
`BROWSE_ONLY`; no `SiteProfile`, origin allowlist, adapter binding, catalog binding, or release state is
changed.

## Ministerio de Igualdad — ES-PUB-0067

`https://igualdad.sede.gob.es/` and `https://igualdad.sede.gob.es/Requisitos` both returned HTTP 200.
The requirements page carries the same public AutoFirma and Cl@ve requirements described above.

The unauthenticated root loads the same four same-origin AC2 application-script paths, and all four
files are byte-for-byte identical to the Instituto Cervantes copies at the SHA-256 values listed
above. The same single `doSignAsPromise` reference is present in `ac2-formularios.js`; the exact public
script set again contains no implementation of that function and no algorithm/format tuple.

This is direct cross-tenant evidence for a shared AC2 frontend seam, but shared platform code is not a
license to infer the hidden signer implementation or per-procedure cryptographic contract. Therefore
`ES-PUB-0067` also remains `BROWSE_ONLY` / research-only.

## Contract conclusion and queue impact

The shared AC2 asset fingerprint strengthens platform classification for two previously unreviewed AGE
inventory surfaces and confirms that the MPTMD blocker is not tenant-specific: the browser-visible
pre-auth layer stops one function boundary before the local signer contract. A future promotion gate
requires an official public definition/binding of `doSignAsPromise` that supplies the algorithm,
format/mode, payload semantics, callback/result shape, and any local transport constraints without
crossing authentication or POST boundaries.

No implementation-ready portal is promoted by this checkpoint. The classified research buffer grows
by two researched public surfaces; exact implementation priority remains the already-preserved Sevilla
ATSE slice once acceptable terminal Codex Cloud evidence exists, then preserved Melilla STA, then
`extremadura-tramites` (`ES-PUB-0109`).
