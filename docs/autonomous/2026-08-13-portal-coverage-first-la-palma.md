# Portal coverage first — La Palma STA batch binding — 2026-08-13

## Safety boundary

This milestone used only unauthenticated, read-only first-party HTTPS observations and repository tests.
No certificate, credential, cookie, authenticated session, identity document, signature, upload, payment,
or administrative submission was used or performed.

## Evidence-backed contract

`la-palma-sede-electronica` (`ES-PUB-0130`) was selected because the Cabildo de La Palma public portal
exposes the same bounded STA AutoFirma batch resources already supported by the shared STA core, while
its trust ownership can remain fixed to one exact HTTPS origin:
`https://sedeelectronica.cabildodelapalma.es`.

The first-party resources refreshed on 2026-08-13 were:

- `/sta/resources/js/autoscript.js` — SHA-256
  `dd77491f6e514ca22d40a1737e6bb13a11f05469c38ddf12ac4a90a7e35f0af5`;
- `/sta/resources/js/sta-autofirma-lote.js` — SHA-256
  `03f80b989f04d8f0a7fcbd1500831023f5d332eaed599cb48740c0af12a1706a`;
- `/sta/pages/webapps/js/webAppsFwk.js?ver=2605.0.3` — SHA-256
  `0960256cac00d1aea5f5e496031b37de1207d77683e1ae4e109fa5803c3bf5aa`.

The first two resources were byte-identical to the corresponding refreshed Extremadura resources.
The observed `sta-autofirma-lote.js` contract invokes AutoScript batch signing through
`signBatchProcess` and fixes the supported tuple used by this profile to `SHA256withRSA`, `CAdES`, and
suboperation `sign`; presign, postsign, and getdata URLs are supplied at runtime under the portal-owned
STA path rather than being promoted to static endpoints.

## Implementation boundary

The implementation reuses the existing shared STA batch parser/signing core but adds independent
La Palma wrappers for exact-host URL validation, WebMessage ownership, profile normalization, protocol
identity, and runtime adapter selection. The QA profile trusts only
`https://sedeelectronica.cabildodelapalma.es`; same-protocol behavior on another host does not inherit
that trust.

The catalog status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. This milestone does not claim release
support or end-to-end verification because no real certificate, signature, authenticated procedure, or
administrative submission was exercised.
