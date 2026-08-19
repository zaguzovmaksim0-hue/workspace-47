# Ministerio de Justicia — bounded public launch evidence — 2026-08-19

Target: `ES-PUB-0010` / Sede electrónica del Ministerio de Justicia.

Current official procedure page:
`https://sede.mjusticia.gob.es/tramites/organos-gobierno`.

For **Modificaciones estatutarias de la fundación**, the current first-party page exposes
`Tramitación On-line con CL@VE con Certificado Digital` and links exactly to
`https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75`.
That URL currently redirects on the same origin to `/login/index/idp/75`.

The current login page describes Cl@ve, electronic certificates/DNIe and AutoFirma. Its DOM does not
contain the active certificate-signing controls used by the loaded first-party login module. The
module `js/modules/default/login/index.js?v2026081917` remains SHA-256
`a9a173e74c2d09781856021856ba40be9d48748aa979cbcb1d9cbc611f6e489c` and contains an inactive
`XAdES Detached` / implicit `accAfirma.signData(...)` branch, but none of the current same-origin
scripts defines the `accAfirma` wrapper or a signing algorithm.

Implementation boundary: QA-only exact navigation to the observed delegated launch. The inventory
retains its pre-existing `signature_required: CONDICIONAL` presentation metadata, but the profile
exposes no certificate-selection or document-signing capability. No client-TLS authentication,
signing format/algorithm, endpoint, callback, authentication success, final
filing/registration/submission, or payment is claimed.
