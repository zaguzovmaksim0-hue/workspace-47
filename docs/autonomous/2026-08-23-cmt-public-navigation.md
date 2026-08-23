# ES-PUB-0031 — CMT public-navigation boundary

Reviewed: 2026-08-23

## Current first-party evidence

- `https://sede.cmt.gob.es/` returned HTTP 200 and identified itself as the electronic office of the Comisionado para el Mercado de Tabacos.
- The landing page currently links its public service catalogue at `https://sede.cmt.gob.es/catalogoservicios.aspx`; that page also returned HTTP 200 and exposed individual public service links under the same Sede origin.
- The landing page also exposes a separate citizen-management login under `https://serviciostelematicosext.hacienda.gob.es/CMT/gestionciudadano/...`. That separate origin is evidence of the boundary only: it is not added to the profile trust set and no authentication, client-TLS, signer, callback, payload, format, algorithm, or filing contract is inferred from it.
- The public landing mentions admitted digital certificates. This pass does not convert that informational statement into a runtime certificate/authentication capability because no exact protected transition was exercised.

## Implemented boundary

`cmt-public-navigation` is a QA-only profile whose exact start URL and sole initiator origin are the public CMT Sede. It has no redirect origins, trusted browse origins, endpoints, operation policies, capabilities, or client-auth policy. The inventory is promoted only to `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` for this bounded public-navigation contract.

No private-key signature, final filing/registration/submission, payment, or authenticated state change was performed.
