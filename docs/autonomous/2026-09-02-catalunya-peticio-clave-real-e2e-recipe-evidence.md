# Catalunya Petició genèrica Cl@ve REAL E2E recipe evidence — 2026-09-02

Scope: fresh unauthenticated public navigation/DOM/first-party JavaScript evidence. No captcha was bypassed, no client certificate was sent, and no petition was filed or submitted.

Catalog/profile: `catalunya-tramits-peticio-generica` / `catalunya-peticio-generica-client-auth`.

## Current public chain

1. The reviewed Petició genèrica page currently exposes the exact signed-flow anchor `Inicia . Ves a Presentar amb signatura electrònica` to the reviewed `ovt.gencat.cat/.../renderitzar.do` URL for service `ING001HTM2` and the exact return URL to the same Petició genèrica page.
2. After the public queue redirect settles, that OVT page exposes one exact `input type=button` with value `Accedeix` and first-party onclick to `/gsitgf/AppJava/traint/renderitzaruploadSecure.do?reqCode=autenticarFormulariHtml&authMFA=false`.
3. Following that authentication action performs the current Gencat SSO redirects and lands at the AOC VALId page with the exact fixed OAuth tuple:
   - `lang=ca`;
   - `scope=autenticacio_usuari`;
   - `state=state`;
   - redirect URI `https://ovt.gencat.cat/gsitfc/AppJava/redirectservlet`;
   - `response_type=code`;
   - `client_id=gsit.gencat.cat`;
   - `approval_prompt=auto`.
4. Current AOC DOM offers both certificate and Cl@ve methods. The certificate method carries `g-recaptcha`; this recipe does not click it and does not attempt to bypass reCAPTCHA.
5. The separate `button#btnContinuaClave` has exact handler `submitLoginForm('clave')` and does not carry `g-recaptcha`.
6. Fresh first-party `https://valid.aoc.cat/o/oauth2/js/login.js` shows that `submitLoginForm('clave')` changes `form#login-form` action to `/o/oauth2/clave`, sets hidden `authMethod=clave`, and submits that login form. This is the Cl@ve authentication path expected by the existing profile, which subsequently pins `pasarela.clave.gob.es/Proxy2/ServiceProvider` to `pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen` before CLIENT_TLS_AUTH.

The recipe validates each public step and clicks only the non-captcha Cl@ve authentication control. It stops before any petition content or final submission.

## Separate `catalunya-seu-electronica` card

The old electronic-register start URL currently redirects to the same Petició genèrica page, but its existing profile expects direct AOC certificate authentication. The live certificate button is reCAPTCHA-protected. That card is therefore deliberately not automated by this recipe; its current contract should be reviewed separately instead of bypassing captcha or silently switching its profile mechanism.
