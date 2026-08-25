# ES-PUB-0125 — Cabildo Insular de El Hierro — 2026-08-21

## Scope

Current bounded public pass for the institutional Cabildo Insular de El Hierro target. No authentication, private-key signing, final filing, registration, submission, or payment was performed.

## Decisive current evidence

- `https://www.elhierro.es/es` returned HTTP 200 and currently publishes a direct **SEDE ELECTRÓNICA** link to `https://elhierro.sedelectronica.es`.
- `https://elhierro.sedelectronica.es/` resolves to the current sede home and exposes the public **Trámites** catalog.
- `https://elhierro.sedelectronica.es/dossier` resolves to the public catalog and currently lists **Solicitud general** at `https://elhierro.sedelectronica.es/catalog/t/7944e884-3b98-48fc-abcd-d6db6ef8bd71`.
- That procedure page exposes **Iniciar tramitación electrónica** at the exact stable launch `https://elhierro.sedelectronica.es/catalog/tw/7944e884-3b98-48fc-abcd-d6db6ef8bd71`.
- The exact launch reaches the current **Identificación electrónica** page and exposes a POST handoff to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`. Hidden SAML/session material was not retained or committed.

## Implementable boundary

A QA-only navigation profile can safely bind the exact Solicitud general launch, the exact `elhierro.sedelectronica.es` initiator origin, and the observed first Cl@ve handoff origin. No signer ABI, signature format/algorithm, client-TLS contract, callback, or final submission contract is claimed.
