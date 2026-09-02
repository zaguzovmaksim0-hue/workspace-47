# STA Burgos/Huesca REAL E2E recipe evidence — 2026-09-02

Scope: public, unauthenticated navigation evidence only. No certificate was submitted and no REAL E2E signing run was started while collecting this evidence.

## Diputación de Burgos

Catalog entry:

- `https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO`

Observed public DOM on 2026-09-02:

- exactly labeled entry control: `Identificate`
- href: `https://registro.diputaciondeburgos.es/sta/CarpetaPrivate/doEvent?APP_CODE=STA&PAGE_CODE=HOME`
- target returned HTTP 200 and retained that effective URL
- certificate login control id: `link-certificado`
- certificate login href: `/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=HOME`
- label: `Acceso con Certificado Digital`

## Diputación de Huesca

Catalog entry:

- `https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME`

Observed public DOM on 2026-09-02:

- exactly labeled entry control: `Identificate`
- href: `https://ovc24.dphuesca.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME`
- target returned HTTP 200 and retained that effective URL
- certificate login control id: `link-certificado`
- certificate login href: `/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=HOME`
- label: `Acceso con Certificado Digital`

## Implementation choice

The REAL E2E recipe uses the already fail-closed exact-anchor helpers. The first step matches the reviewed full entry href and exact `Identificate` label. The second step waits for the exact login URL and matches the stable `link-certificado` id plus exact certificate href.

Burgos and Huesca share the same second-stage STA contract, so the implementation is factored into one `clickStaCertificateLogin` helper. Other STA-looking portals are deliberately not included without separate evidence; for example, the current Ourense login path transitions into a different SAML flow.

## Consell Insular d’Eivissa

Two catalog cards share profile `eivissa-sede-electronica`.

Institutional card:

- entry: `https://www.conselldeivissa.es/`
- exact public link label: `Seu electrònica`
- exact href: `https://seu.conselldeivissa.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_HOME&lang=ES`

Sede card:

- entry: `https://seu.conselldeivissa.es/`
- on 2026-09-02 the root returned HTTP 200 after redirecting to `https://seu.conselldeivissa.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=PTS2_HOME`

On both resulting STA home pages:

- exact identify label: `Identifícate`
- exact href: `https://seu.conselldeivissa.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME`

On the login page:

- HTTP 200 with the same effective URL
- certificate login id: `link-certificado`
- certificate login href: `/sta/CarpetaPrivate/Certificate?APP_CODE=STA&PAGE_CODE=HOME`
- label: `Acceso con Certificado Digital`

The institutional card therefore uses one exact reviewed transition to the sede and then reuses the shared STA certificate-login recipe. No generic text search or consequential form submission was added.
