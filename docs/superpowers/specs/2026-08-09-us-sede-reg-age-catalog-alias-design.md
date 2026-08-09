# US Sede → REG-AGE catalog alias design

## Problem

The official Universidad de Sevilla public procedure `ISG_01` links its `Iniciar trámite` action exactly to `https://reg.redsara.es/es/`. Junta Firma Mobile already has a QA-only `reg-age-redsara` profile with an independently verified REG-AGE signing contract. Reusing that profile is safer than cloning it, but the public catalog currently enforces one public entry per profile and requires the public `entryUrl` to equal the profile `startUrl`.

## Safety invariant

A public alias may select an already configured profile only if its optional `launchUrl` is byte-for-byte/ASCII-string equal to that profile's `startUrl`. Public metadata never authorizes new trust. `SiteProfileRegistry`, origin sets, signing algorithms, callback contracts, endpoints, certificate rules, and profile activation remain unchanged.

## Data model

Add nullable `launchUrl: URI?` to `PublicPortalEntry`.

- `entryUrl`: unique official public metadata/procedure URL shown for the catalog record.
- `launchUrl`: optional canonical launch destination for an evidence-backed alias.
- A record without `launchUrl` keeps existing behavior: its launch URL is `entryUrl`.

The strict JSON schema explicitly admits `launchUrl` and applies the same strict HTTPS parser to it. A null `profileId` requires a null `launchUrl`.

## Binding validation

The repository derives `effectiveLaunchUrl = launchUrl ?: entryUrl` and considers a binding valid only when all of the following hold:

1. `profileId` resolves to exactly one bundled profile;
2. registry metadata equals that profile;
3. `effectiveLaunchUrl` exactly equals `profile.startUrl`;
4. the profile is active for the current build trust policy and its existing protocol binding remains complete.

`resolveLaunch` continues returning `profile.startUrl`, never a caller-built destination. Caller-supplied `entryUrl` remains checked against the public record to prevent substitution.

Multiple public entries may reference the same `profileId` only because the profile itself remains unique in `SiteProfileCatalog`; public `entryUrl` values remain unique. No duplicate signing origin is created.

## US binding

Update `us-sede` only:

- keep `entryUrl = https://sede.us.es/opencms/system/modules/sede/contents/pages/requisitosTecnicos` unless the existing catalog's evidence URL is deliberately updated to the exact `ISG_01` procedure page;
- prefer the exact procedure page as `entryUrl`: `https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01`;
- set `launchUrl = https://reg.redsara.es/es/`;
- set `profileId = reg-age-redsara`;
- keep the inherited implementation QA-only through the profile;
- set truthful pending statuses after tests, with a limitation stating that the US page delegates to REG-AGE and no physical E2E has been performed.

## Behavioral seams

TDD is at public seams only:

- `PublicPortalCatalogParser.parse` for strict alias syntax/invariants;
- `PortalCatalogRepository.portals` / `resolveLaunch` for validated alias behavior and tamper rejection;
- bundled catalog parsing for the exact `us-sede` record.

No WebView, signing adapter, cryptographic primitive, or trust-origin behavior is modified.
