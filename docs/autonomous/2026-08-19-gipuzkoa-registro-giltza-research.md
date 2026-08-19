# Gipuzkoa Registro electrónico / Giltza public contract research — 2026-08-19

## Scope and safety boundary

This pass used only current unauthenticated HTTPS GET requests to first-party Gipuzkoa pages, the public Registro electrónico landing, structural inspection of the Izenpe OAuth/delegated-auth redirects, and a credential-free TLS handshake to the delegated client-certificate origin. No delegated authentication form was submitted, no certificate/private key was supplied, no authenticated form was created, and no document was uploaded, signed, paid for, registered, or submitted. Ephemeral OAuth state, cookies and hidden delegated-auth request values were not retained in this document.

## Current official Registro binding

The current first-party guidance at `https://egoitza.gipuzkoa.eus/es/registro/como-registrar` directs the generic electronic registry to Trámites Online and links `https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001`. The guidance states that an accepted digital certificate is required for the generic registration form. The linked page returned HTTP 200 and identifies itself as `Registro electrónico` / `Tramiteak Online`.

The public Registro landing captured during this pass was 31,840 bytes with SHA-256 `da86fc2c1e41ffc6a9342fec20618629a00f0fc8c3f91c7ab205a4fcf31d9463`. Its first-party inline JavaScript exposes `loginGiltza(tipoLogin)` and maps the certificate option to `/WAS/CORP/WATTramiteakWEB/loginCertificadoGiltzaCertificado.do`. The static first-party `estatico/lib/general.js` asset had SHA-256 `30e15d90bb0122c01cf1ba68c29f78bc4028580678c6e1cf9daee9f7075b475d`.

## Observed authentication boundary

A current unauthenticated GET to the first-party certificate-login endpoint returned HTTP 302 to `eidas.izenpe.com/trustedx-authserver/izenpe/oauth`. Structural parameters observed were `acr_values=urn:safelayer:tws:policies:authentication:flow:cert`, `client_id=izfe_giltza`, `response_type=code`, the first-party Gipuzkoa callback URL, scopes, locale and an ephemeral state value. The state value itself is intentionally omitted.

Following the OAuth step without credentials produced a delegated-authentication HTML form whose POST target is `https://eidas2.izenpe.com/cert-authn-external-validation/authenticate`. Hidden request/correlation values were not printed or retained. A separate TLS 1.2 handshake to `eidas2.izenpe.com:443` observed a server `CertificateRequest`, with RSA/ECDSA client certificate types among those accepted. This proves a real client-TLS boundary in the current certificate path, but not that Workspace-47 can safely authorize that transition.

## Implementation boundary

The existing Workspace-47 client-auth authorizer grants TLS identity only from explicit top-level navigation contracts. The Gipuzkoa/Izenpe client-certificate transition is reached by a POST form carrying opaque server state. Treating it as an existing GET/navigation client-auth profile would therefore overstate the implemented contract.

The smallest truthful capability implemented by this pass is a new `VERIFIED_CONTRACT` / `QA_ONLY` profile that launches only the exact public Registro landing on `egoitza.gipuzkoa.eus`. It exposes no SIGN, SELECT_CERTIFICATE or CLIENT_TLS_AUTH capability and does not trust either `eidas.izenpe.com` or `eidas2.izenpe.com`. Inventory metadata records the observed Giltza/OAuth/client-TLS boundary, while authenticated client TLS, document signing and final registration remain explicitly outside the implemented contract.
