# CAIB Registro Electrónico public-boundary research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests. No form POST, upload,
authenticated navigation, credentials, certificate material, cookies, signing operation, payment,
administrative submission, APK launch, ADB, or device-control workflow was used. Temporary public
HTML bodies were deleted after extracting bounded status, hash, and non-sensitive markers.

## Current first-party evidence

Inventory surfaces `ES-PUB-0097` (`caib-seu-electronica`) and `ES-PUB-0098`
(`caib-registre-electronic`) remain `BROWSE_ONLY`.

The current CAIB electronic-signature guidance at
`https://www.caib.es/seucaib/es/fichainformativa/3392758` returned HTTP 200. The fetched HTML had
SHA-256 `5bc112af9ef2adefadcf404cf85fc5477e9a91cdd11b7d0e9dba881f4f3bfde3` and explicitly names
AutoFirma and Cl@veFirma. This proves supported signing products at the sede level, not a
portal-specific runtime ABI.

The current `Registro Electrónico General` information page at
`https://www.caib.es/seucaib/es/fichainformativa/1445668` returned HTTP 200 with SHA-256
`7f91c9afdd9d18d8ce2587b08e4dfe1e43e2d0fdb3ac36a8cfa6dd9d19e7c796`. It directs users who
have no specific procedure to the current generic-instance procedure and states that electronic
submission requires an identification system shown at procedure start.

The exact generic-instance page at
`https://www.caib.es/seucaib/es/200/persones/tramites/tramite/4213695` returned HTTP 200 with
SHA-256 `a254ab690b9cdaff15aa5f12dbd6b18a57f2a7135b6429523df5542154836852`. It identifies the
procedure as SIA 2307649 and states that telematic access requires a digital certificate, DNI-e, or
Cl@ve Permanente.

The exact public telematic-start URL published by that page is:

`https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?idTramiteCatalogo=4213963&idioma=es&parametros=&servicioCatalogo=false&tramite=CAIB.SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1`

A GET to that URL returned HTTP 302 to `https://www.caib.es/sistramitfront/login.html`; following
that single redirect returned HTTP 200. The public login response had SHA-256
`8adaf64e908dbd5b95c279ab1a9541ba5c2cbc4fd4e0fe8e94c58dd770c42182` and currently renders an
unexpected-error page before any signing contract is exposed.

The older inventory reference
`https://apps.caib.es/sites/atenciociutadania/ca/registre_electranic/` timed out on the current
Termux network route (`curl` exit 28, no HTTP response). Because this is a route-specific timeout,
it is not treated as evidence that the public resource is globally unavailable.

## Contract conclusion

The current public evidence strengthens the exact CAIB pre-auth transition but still does not expose
a signing algorithm, signature format/mode, payload semantics, local bridge callback, result-delivery
endpoint, or other portal-specific AutoFirma ABI before authentication. No values are inferred from
AutoFirma defaults or from another Spanish administration.

Therefore neither `ES-PUB-0097` nor `ES-PUB-0098` is implementation-ready. Both remain
`BROWSE_ONLY`; no `SiteProfile`, origin allowlist, bridge capability, catalog binding, or release state
is changed. The next safe gate is a first-party unauthenticated procedure asset that exposes the
actual local-signing invocation before authentication or submission.

## Queue impact

The classified research buffer remains at least 16 surfaces. Implementation priority remains Sevilla
ATSE after acceptable terminal Codex Cloud evidence, then the preserved Melilla STA slice, then
`extremadura-tramites` (`ES-PUB-0109`). CAIB remains a research lead and does not displace those
implementation-ready candidates.
