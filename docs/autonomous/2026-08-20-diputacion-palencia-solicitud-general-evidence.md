# Diputación Provincial de Palencia — Solicitud carácter general — bounded evidence — 2026-08-20

## Scope and stop boundary

Current public OpenSIAC pages and the unauthenticated pre-authentication transition were inspected for ES-PUB-0166. The pass stopped before authentication. No credential, certificate/private key, cryptographic signature, upload, payment, final filing, registration or administrative presentation was performed. Dynamic Cl@ve/SAML values and session identifiers are intentionally not recorded.

## Decisive current observations

- `https://sede.diputaciondepalencia.es` resolves to the official OpenSIAC surface on the same origin.
- The live procedure catalog exposes `1- Solicitud carácter general` as an electronic level-4 procedure. Its information page is `https://sede.diputaciondepalencia.es/opensiac/informacionpublica/tramitesinfo.action?tramitesInfoForm.id=5` and its public `Tramitar` link is `https://sede.diputaciondepalencia.es/opensiac/informacionpublica/tramitacion.action?tramitacionForm.id=5`.
- Opening that `Tramitar` URL returns a 302 to `/opensiac/certlogin/enter.action`. The resulting page says that access to the private area requires identification through Cl@ve.
- Its POST to `/SPProxy2/IndexPage` returns an auto-submit form whose exact external action is `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`. Only field names were inspected; dynamic SAML/relay values were not retained.
- The current official technical-requirements page states that AutoFirma is required to sign requests in the Sede. This establishes an observed product requirement, not a signing ABI for procedure id=5.

## Implemented boundary

The profile is QA-only and implements only the exact procedure launch plus the observed pre-authentication Cl@ve navigation origin. It has no signing operation policy, no privileged endpoint, no client-TLS policy and no signature format/algorithm/callback claim. The truthful inventory state is `IMPLEMENTED_NOT_E2E`; the generated catalog remains `E2E_PENDING`.

## First unknown

The first material unknown is the post-authentication state of procedure id=5: the exact AutoFirma invocation/format/algorithm/payload/callback cannot be observed without completing Cl@ve authentication. Any future pass must stop before actual private-key signature and before final filing/registration.
