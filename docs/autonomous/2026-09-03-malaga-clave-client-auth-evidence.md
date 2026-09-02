# Diputación de Málaga — Cl@ve client TLS evidence — 2026-09-03

Scope: public authentication path only. No document was signed and no administrative submission was made.
Ephemeral `tkn`, SAMLRequest and RelayState values were intentionally not retained in this evidence.

## Live chain

1. `https://sede.malaga.es/instancia-general/nueva-instancia-general/`
   exposes one `form#claveFrm`, method POST, action `https://clave.malaga.es/clave.php`, with the submit label `Acceder con cl@ve`.
2. The local Cl@ve handoff returns a POST form to
   `https://pasarela.clave.gob.es/Proxy2/ServiceProvider` carrying SAMLRequest and RelayState.
3. A fresh Chromium session reached that exact ServiceProvider and observed:
   - `form[name=idpRedirect]`, method POST, action `/Proxy2/ServiceRedirect`;
   - populated SAMLRequest and RelayState;
   - empty SelectedIdP before selection;
   - exactly one AFIRMA control with
     `JAVASCRIPT:selectedIdP('AFIRMA');idpRedirect.submit();`;
   - exactly one `ImageRetrieve?id=IDP_AFIRMA` image.
4. Clicking only that reviewed AFIRMA control generated the exact network transition:
   `POST https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`
   → `POST https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`.

## Runtime contract

The QA profile is limited to `CLIENT_TLS_AUTH` and uses the existing fail-closed Cl@ve policy:

- transition: `DIRECT_FROM_SOURCE`;
- source: `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`;
- request origin: `https://pasarela-ident.clave.gob.es`;
- path: `/IdP2/AuthenticateCitizen`;
- method: POST;
- no query parameters;
- request port 443;
- 15-second grant;
- empty issuer list may be accepted;
- RSA and EC certificate keys are permitted, with digital-signature key usage required.

No signing capability, signature format, signing algorithm, callback or registration endpoint is trusted by this profile.
