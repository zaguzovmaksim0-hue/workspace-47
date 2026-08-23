# JCCM Registro Electrónico / Solicitud Genérica — controlled pre-sign evidence (2026-08-19)

Target: `ES-PUB-0103` only. This packet records sanitized protocol facts; it contains no SAML assertion, cookie, certificate material, personal field values, or private-key output.

## Auth/session return

- Public entry: `https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ`.
- Reused prior controlled chain: JCCM CAS/ALTCHA → Cl@ve `ServiceProvider`/`ServiceRedirect` → `SelectedIdP=AFIRMA` → `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`.
- The operator-authorized certificate was accepted and eIdentifier returned a SAML response to Cl@ve.
- Under RUNBOOK v2.4 the session-bound SAML relay was forwarded to `https://sso.jccm.es/cas-jccm-clave/login`. JCCM returned HTTP 302 into the protected `https://registrounicociudadanos.jccm.es/registrounicociudadanos/accesoclvd.do`.
- The protected page exposed form `AltaReg`, POST action `AltaRegGenericaAction.do`, with administrative fields plus hidden `firma` and `certificado`. No administrative values were invented or submitted.

## Protected signer contract

The current protected page's first-party inline code separates the certificate and Cl@ve branches. In the certificate branch it:

1. calls `getXmlForm()` to build the registration XML;
2. Base64-encodes that XML as ISO-8859-1;
3. calls `MiniApplet.sign` with `SHA512withRSA`, API format `XADES`, and exact extra properties `format=XAdES Detached\nmode=implicit`;
4. on success, decodes the returned certificate and signature into `AltaReg.certificado` and `AltaReg.firma`;
5. only then targets `AltaRegGenericaAction.do?accion=Guardar` and submits.

The Cl@ve branch calls `firmarFormClave`, places the unsigned `getXmlForm()` result in `firma`, then targets the same `Guardar` action. The controlled run did **not** invoke either final button, did **not** execute a private-key signature over the registration XML, and did **not** POST `accion=Guardar`.

The public `ABCDEF` MiniApplet call is retained only as authentication evidence. Its tuple happens to match the protected certificate branch, but the final signer is independently established by the protected page because its payload is the dynamic `getXmlForm()` XML rather than the login challenge.
The filing-sign bridge is deliberately fail-closed on the public `acceso.do?id=SJLZ` page; it accepts this tuple only on the protected `accesoclvd.do` page. Therefore the authentication challenge cannot be routed as a filing signature merely because its algorithm/format tuple matches.

## Product boundary

The new profile is `QA_ONLY` / `VERIFIED_CONTRACT`. It grants signing only to `registrounicociudadanos.jccm.es` and recognizes the exact JCCM registration pages/tuple. The Cl@ve client-certificate transition is restricted to the observed `ServiceRedirect` → `pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen` path. Release/E2E remains pending.
