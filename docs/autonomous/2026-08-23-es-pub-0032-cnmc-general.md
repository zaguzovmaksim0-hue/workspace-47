# ES-PUB-0032 — CNMC generic submission public boundary

Reviewed 2026-08-23. The first-party CNMC Sede page `https://sede.cnmc.gob.es/tramites/general/remision-de-solicitudes-escritos-y-comunicaciones` is active and identifies the procedure as an online procedure with digital certificate. It publishes two separate launch boundaries: `https://tramitesclave.cnmc.gob.es/formulario/21` for Cl@ve and `https://tramites.cnmc.gob.es/formulario/21` for electronic certificate access.

The public instructions describe a later electronically signed submission and automatic extraction of signer identity from the certificate, but do not establish an exact signer ABI, signature format/algorithm, packaging, callback or endpoint. Workspace-47 therefore adds only a QA-only navigation profile for the public first-party procedure page. Both authenticated form origins remain outside profile trust and no authentication/signing capability is added.

No Cl@ve flow, certificate selection, form submission, upload, signing, payment or final administrative presentation was performed.
