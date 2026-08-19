# Ayuntamiento de Madrid — bounded OIDC/Cl@ve navigation evidence — 2026-08-19

## Decision

`ES-PUB-0017` now has a deliberately narrow `NEW_PROFILE` contract: QA-only browser navigation from the current Ayuntamiento de Madrid Sede procedure into the municipal Tarjeta Azul application and its municipal OIDC/PKCE identity selector. No signing adapter, certificate-selection bridge, TLS client-auth policy, protocol endpoint, signature format, or submission behavior is inferred.

The implemented browser origins stop at the municipal boundary. The subsequent shared Cl@ve portal `pasarela.clave.gob.es` was observed after choosing `DNIe / Certificado`, but it is intentionally not claimed by this profile because it is shared across unrelated profiles and no Madrid-specific client-certificate request was observed.

## Current public runtime evidence

The inventory entry remains the official Sede URL:

- `https://sede.madrid.es/portal/site/tramites/menuitem.62876cb64654a55e2dbd7003a8a409a0/?vgnextchannel=23a99c5ffb020310VgnVCM100000171f5a0aRCRD&vgnextoid=ac464e85763fd310VgnVCM1000000b205a0aRCRD`

A direct CLI GET still receives an Akamai HTTP 403. A normal Chromium 149 public session on the same device loads the page successfully with title `Tarjeta Azul de transportes para autobuses (EMT) y metro - SEDE ELECTRÓNICA`. The rendered current page exposes `Tramitar en línea` and an exact `Solicitud o renovación digital de Tarjeta Azul` link to:

- `https://servcla.madrid.es/TAZUL_FTWEBINTER/`

Opening that exact application URL redirects into the Ayuntamiento identity service at `https://cas.madrid.es/authenticationendpoint/login.do` with an authorization-code OIDC request. The observed request contains per-session `state`, `nonce`, `sessionDataKey`, and `code_challenge`; the durable contract records none of their values. Stable observed parameters include:

- `response_type=code`;
- `code_challenge_method=S256`;
- `commonAuthCallerPath=/oauth2/authorize`;
- `redirect_uri=https://servcla.madrid.es/oauth/client/redirect`;
- an OpenID scope for `/TAZUL_FTWEBINTER/`;
- configured authenticators for Cl@ve and IDentifica.

The municipal page is titled `SISTEMA DE IDENTIFICACIÓN` and offers five visible methods: `Cl@ve Móvil`, `Cl@ve Permanente`, `DNIe / Certificado`, `Ciudadanos UE`, and `IDentifica`.

## Certificate-choice boundary

The rendered `DNIe / Certificado` option invokes the municipal handler with stable identifiers `IDPCLCTM` and `CLAVE`. Activating it in a disposable public browser target transitioned to:

- `https://pasarela.clave.gob.es/Proxy2/ResponseRedirect`

No credential was entered, no certificate was selected, and no private-key operation occurred. The observed Cl@ve page did not expose a Madrid-specific TLS `CertificateRequest`, signing payload, signing algorithm, callback, or final filing action. Therefore this evidence supports a certificate **access route**, not `CLIENT_TLS_AUTH` or document signing.

## Implemented boundary

The QA-only profile uses:

- exact start URL: the reviewed Sede Tarjeta Azul procedure above;
- initiator origin: `https://sede.madrid.es`;
- redirect/browse transition origins: `https://servcla.madrid.es` and `https://cas.madrid.es`;
- empty `capabilities`;
- empty `operationPolicies`;
- empty `endpoints`;
- `clientAuthPolicy = null`.

`pasarela.clave.gob.es` deliberately remains outside the profile-owned origin set. If navigation reaches it, the generic navigation policy treats it as an external HTTPS boundary rather than granting profile trust.

## Unknowns deliberately retained

- successful authenticated post-login state;
- whether any later branch issues a TLS client-certificate challenge;
- whether the selected procedure requires an electronic document/statement signature;
- signature format, algorithm, packaging, endpoint, and callback;
- final filing/registration/submission behavior;
- payment behavior for other Ayuntamiento procedures.

Future work may authenticate and inspect intermediate/pre-sign state under RUNBOOK v2.4, but must stop before an actual private-key document signature, final filing/registration/submission, or payment.
