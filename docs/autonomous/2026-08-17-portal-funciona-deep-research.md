# Portal Funciona — deep public research — 2026-08-17

## Decision

`ES-PUB-0084` has a bounded `NEW_PROFILE` contract: QA-only integrated navigation to the exact current public Funciona home `https://sede.funciona.gob.es/es/home`.

The implementation intentionally stops before authentication. Current public evidence proves a multi-hop OIDC/PKCE → Autentica/SAML → TLS client-certificate boundary and also exposes protected FNC signing helpers in the loaded application bundle. Those sensitive seams are not projected into the profile: there is no `CLIENT_TLS_AUTH`, `SIGN`, `SELECT_CERTIFICATE` or `AFIRMA_URI` capability, no operation policy, no endpoint, and no trusted external auth origin. This keeps the implementation fail-closed rather than guessing a REG-AGE or signing contract.

## Static first-party map

Public unauthenticated GETs in this pass included:

- `https://sede.funciona.gob.es/public/servicios` → HTTP 200 SPA shell, title `Funciona`, 26,266 bytes, SHA-256 `be75cd810774f92bac5ccdfa3a19ccdd7c793b30b7f31bdb50545c58a603595d`.
- `https://sede.funciona.gob.es/` → HTTP 200 SPA shell, SHA-256 `703067df35c6b964e45e57d5235fbc090d106db06965fdda32d1988c2ff0833d`.
- `https://sede.funciona.gob.es/es/home` → HTTP 200, title `Funciona`, 26,267 bytes, SHA-256 `3fcd44a18846573211d34f8571a13ac8647a92e680c3903f22ad538ae9611463`.
- `https://sede.funciona.gob.es/es/consulta-csv` → HTTP 200, title `Funciona`, 26,265 bytes, SHA-256 `4b0a8d48d5986dd52a9424b79fa63c44c273d6a00a0345109cf78a5359173c16`.
- `https://auth-api.redsara.es/auth/realms/sgad-appfactory/.well-known/openid-configuration` → public OIDC metadata, 6,285 bytes, SHA-256 `da079cfd680e60a87fe8b5e3ead46ecf21f8eb01c383cc733e3a25e5e63e3d7d`.

The SPA shell uses `<base href="/es/">` for Spanish and the current public home shows `Servicios electrónicos de interés para el personal de la Administración del Estado`, a button `Acceder con Autentica`, and a public `Consulta CSV` route.

The public CSV route was opened read-only. It asks for a CSV and CAPTCHA before `Descargar documento`; neither field was filled and the submit/download action was not activated.

## Loaded application graph

The current first-party application graph actually loaded by Chromium was inspected. Stable representative assets include:

- `main-LKOPQKJH.js` — SHA-256 `7e7c7c14bfed86e176dd3a037839ab6f36d2ccc5726fdf392ef912ab3eb08d2b`.
- `chunk-I4YBF7RI.js` — SHA-256 `d2505c81a3ce67b94e30000fd1bf9af77daeb5dc48703d1acebe6cfb0e5ad1fc`.
- `chunk-264T2ZVF.js` — SHA-256 `13eed4c6bb17bca0d6e381c7e4847b4e0b7cdf3730e5f155339bc05c2bba4036`.
- `chunk-4LVVP3G7.js` — SHA-256 `fec040a6df6395980e4084e7875448a7c4708680e3f8ba6d90e49e9b342e1166`.
- `chunk-YG6XD7G7.js` — SHA-256 `779dfe02feee8e15126885122e70240a957909c7954c4df536f5a992a2f172fb`.
- `chunk-ROU4DOXI.js` — SHA-256 `abfb85d2cc61471cd403337226d0b8d53ad8d91576d359afc17d1ad6a0f3b53d`.
- `chunk-QOGL65NU.js` — SHA-256 `bb97cc45218c93beb7cc7a0745445e2d310c0e48c950c9fc7183fa47858246ec`.
- `chunk-ZEECXP34.js` — SHA-256 `5b9beedf7cb5e8cbc26a4ff8e94fed5f6030678ed624aaa0a4509035f97dfb62`.
- `chunk-EDVFNRKF.js` — SHA-256 `f2c0c42d9fbc2955b50bec4e8719bbc500a5ba412f92998df6b1c3622405749c`.
- `chunk-RLZFQSO5.js` — SHA-256 `a37437b723a44940de929e01efcebd94bc70c3e37d88aa824eaf9eed8a442777`.

Additional actually-loaded chunks were fetched and hashed as part of exhaustion (`BCGZNYQD`, `IYALYN6A`, `IOIWWXTX`, `LICJAZZT`, `RGSZX6LQ`, `GC5XFMBB`, `UWM42ZWJ`, `ZKSZTEU3`, `4XJLCKIE`). None introduced an AutoScript/MiniApplet/XAdES/CAdES/PAdES/SHA signing contract on the public route.

The current production configuration embedded in the first-party loaded bundle exposes:

- backend base `https://apigw.redsara.es/eapi-funciona-api`;
- OIDC authority `https://auth-api.redsara.es/auth/realms/sgad-appfactory`;
- OIDC client id `fe66bc25-ec04-41e4-8202-809dbded381a`;
- redirect URI `https://sede.funciona.gob.es/signin`;
- `response_type=code`, `scope=openid`, `acr_values=loa:2`, `response_mode=fragment`;
- Autentica base `https://autentica.redsara.es` and `appId=5524`.

The public OIDC metadata independently confirms the issuer/authorization endpoint and support for Authorization Code plus PKCE `S256`.

## BROWSER_PUBLIC_RUNTIME pass

A real unauthenticated Chromium/Playwright session established the current runtime behavior.

Direct navigation to `https://sede.funciona.gob.es/es/home` remained on that public page. Runtime inspection found no forms and the sensitive globals `AutoScript`, `MiniApplet`, `clienteFirma`, `ClienteFirma`, `AppletFirma`, and `afirma` were all undefined. The visible login action is `Acceder con Autentica`.

The original AGE-directory URL `https://sede.funciona.gob.es/public/servicios` was also opened. The SPA normalized it to a locale home with `redirectTo=/inicio` and automatically entered the same public authentication redirect chain. This is why the implementation canonicalizes `entry_url` to the stable exact `/es/home` while retaining `/public/servicios` as `official_site`/`e_sede` provenance from the AGE directory.

## Authentication network boundary

Activating the real public `Acceder con Autentica` button from `/es/home` performed GET-only public redirects. No credentials or certificate were supplied.

The observed structural chain was:

1. OIDC authorization endpoint on `auth-api.redsara.es` with the fixed current Funciona client id, `redirect_uri=https://sede.funciona.gob.es/signin`, `response_type=code`, `scope=openid`, `response_mode=fragment`, `acr_values=loa:2`, a dynamic `state`, and a dynamic PKCE `code_challenge` using `S256`.
2. Keycloak login action and `broker/autentica/login` with transient session parameters.
3. Autentica SAML entry with `action=goToAutentica`, `appId=5524`, transient `SAMLRequest`/`RelayState`, `SigAlg` equal to XMLDSig RSA-SHA256, and a transient signature generated by the identity broker. That RSA-SHA256 value describes the broker's signed SAML request; it is not a Funciona document-signature algorithm and is not copied into a signing profile.
4. `samlAutentica/servlet/SamlAutenticaCertServlet?action=redirect&appId=5524`.
5. A public Autentica information page stating: `Para acceder a esta aplicación se requiere disponer de certificado digital o eDNI.`

No transient OIDC/SAML/session values are persisted in repository evidence.

## TLS boundary proof

A separate public TLS 1.2 handshake to `autentica.redsara.es:443`, with SNI and **without** a client certificate, observed a server `CertificateRequest`. The server advertised client certificate types RSA sign, DSA sign and ECDSA sign and sent no client-certificate CA-name list. The connection was allowed to continue with an empty client certificate and was then closed normally.

This proves that the external Autentica origin asks for a TLS client certificate. It does not by itself prove which certificate should be selected for Funciona or authorize broad client-auth handling for all Autentica traffic.

## Protected FNC signing observations

The loaded first-party application bundle contains protected-service code that constructs FNC URLs based on `https://autentica.redsara.es` and `appId=5524`, including `goToAutenticaFnc` and `goToXMLFncDocument`, plus references to protected Funciona backend paths under `/secure/...`.

Those endpoints were **not invoked**. The public static code available before authentication did not establish a complete current mobile signing contract: no exact document-signature algorithm, XAdES/CAdES/PAdES format, packaging, callback/result semantics, certificate filter, or bounded server-side submission contract was proven. Consequently no FNC signing constants or adapters are added.

## Why client TLS is observed but not implemented

The repository's existing `ClientAuthNavigationAuthorizer` supports either an exact direct source→client-auth target or a bounded two-stage source/redirect transition. The current Funciona flow is materially longer and dynamic: Funciona home → OIDC authorization → Keycloak login action → broker Autentica → SAML Autentica → TLS certificate boundary. Reusing a current profile or broadening `redirectOrigins` would not preserve the existing exact-transition guarantees.

Therefore the inventory records the observed `CLIENT_TLS_AUTH` fact, but the new profile deliberately has no `CLIENT_TLS_AUTH` capability or client-auth policy. `auth-api.redsara.es` and `autentica.redsara.es` are not navigation origins of the profile. The public home remains usable in QA; the first cross-origin authentication transition remains fail-closed in the integrated browser.

## Implementation boundary

`portal-funciona-public-home` is:

- `VERIFIED_CONTRACT`;
- `QA_ONLY`;
- start URL exactly `https://sede.funciona.gob.es/es/home`;
- initiator origin only `https://sede.funciona.gob.es`;
- no redirect/trusted-browse external origins;
- no endpoints or operation policies;
- no sensitive capabilities;
- no client-auth policy.

Schema-required `certificateRules` are inert because no profile capability consumes them; they are intentionally permissive (`RSA`, `EC`, no digital-signature key-usage requirement) and must not be read as a claim about the external Autentica certificate policy.

## Safety boundary

No credentials were entered. No token or userinfo endpoint was invoked. No client certificate was supplied. No secure Funciona backend endpoint was invoked. No FNC signing POST was performed. No CSV/CAPTCHA form was submitted. No upload, payment, signature or administrative submission occurred.

## Truthful status

`ES-PUB-0084` is `IMPLEMENTED_NOT_E2E / E2E_PENDING`. Physical E2E of the public integrated launch has not yet been recorded, and the authentication/signing flows are explicitly outside this profile.
