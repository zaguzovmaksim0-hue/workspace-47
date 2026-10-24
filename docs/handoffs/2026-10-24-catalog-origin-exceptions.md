# Catalog origin exceptions: fail-closed migration handoff

## Disposition and scope

Task 6 is **plan only**. No production code, catalog entry, protocol, adapter, test,
build configuration, or security policy is changed by this handoff.

The stop condition is triggered by the shared protocol/adapter boundary, not by an
assumed file count: the same origin facts participate in catalog ownership checks,
client-TLS transitions, WebMessage dispatch, and batch URL/session validation.
Moving strings out of those checks independently would leave competing authorities
or make a catalog entry authorize a previously forbidden operation. Even a small
initial schema patch must not be presented as the complete migration.

Research baseline: fresh `origin/main` at
`ae22e7aa15c6d09644287063db3d25a46d05f4a7`, fetched on 2026-10-24.
`config/site_profiles_v1.json` has schema version 1, catalog version 61, 137 profiles,
and 191,140 bytes; its SHA-256 is
`4e03e8df32d63776cd96793abb7368da301b3cb160d157310f439a53d3aad885`.
These are baseline observations, not new integrity pins.

Research was offline, using the repository and its existing tests. It does not
establish live portal compatibility, authorize remote requests, or upgrade any
profile's activation/evidence status. The branch starts from main independently of
task 1; no rebase/cherry-pick or catalog conflict resolution is part of this task.

## Findings and concrete ownership boundaries

Paths below are relative to `app/src/main/java/dev/junta/firmamobile/` unless a
repository-root path is given. Line anchors refer to the baseline above.

| Location | Existing behavior / hardcoded exception | Safe data candidate; boundary that stays executable |
| --- | --- | --- |
| `profile/SiteProfileCatalogParser.kt:818-838,2478-2608` | Cross-profile origin ownership admits only reviewed origin/owner/redirect-role combinations. | Exact origin and explicitly named owner relationships can be reviewed catalog facts. Do not replace the predicate with a shared-host boolean or an owner group closure. |
| `profile/SiteProfileCatalogParser.kt:500-538` | Same-origin TLS has a direct-transition profile set and a separate La Rioja redirect exception, constrained by source/target/query facts. | Typed origin-role relationships may be data; the transition contract and its complete validation must remain bound together. |
| `profile/SiteProfileCatalogParser.kt:241-264,1505-1541` | Malaga's unparameterized in-place POST is pinned to one profile, origin, source URL, request path, method, and port. | Existing `clientAuthPolicy` already carries most facts. A general `allowUnparameterizedPost` would broaden authority and is not an acceptable replacement. |
| `profile/SiteProfileCatalogParser.kt:347-498,567-615,630-816` | Per-profile validators also pin activation, evidence, capabilities, source-origin roles, adapters, callback contracts, algorithms and endpoint bindings. | Classify each condition before removing it. Many are protocol/security contracts, not duplicated host data. `serverUrl` still has to equal its bound endpoint. |
| `profile/SiteProfileCatalogParser.kt:36` | Cantabria alone admits blank fixed extra-property values. | A protocol-semantic exception, not an origin exception; leave it outside a host migration. |
| `browser/MelillaBatchBridgeAdapter.kt:68-249` | Melilla, Extremadura, La Palma, Huesca and Burgos wrappers bind profile/origin facts to the existing `StaBatchBridgeContract` / `StaBatchBridgeAdapter` seam. | Reuse that typed seam, deriving facts from a validated profile descriptor; do not make a new configurable scripting engine. |
| `browser/LugoBatchBridgeAdapter.kt:165-181`; `browser/CaibBatchBridgeAdapter.kt:149-165` | Exact Lugo and CAIB origins/profile IDs are checked again in bridge routing. | Origins are data candidates, but the adapters have different batch grammars and binding rules. They cannot become one host-only adapter. |
| `network/MelillaBatchUrlPolicy.kt`; `network/LugoBatchUrlPolicy.kt:18-55`; `network/CaibBatchUrlPolicy.kt:14-47` | STA wrappers already share `StaBatchUrlPolicy`; Lugo pairs a 32-hex session and CAIB a 28-character token across pre/post URLs. | Inject validated exact origins through typed contracts. Keep registered path grammars, token lengths, pre/post pairing and operation binding in code; do not load regexes from JSON. |
| `browser/MiniAppletBridgeAdapter.kt:867-1730,1777-1885` | Profile-specific dispatch/validation mixes origins with algorithms, extras and callbacks; direct host examples include JCCM registro, Eivissa and AIReF. | Review one registered contract at a time; a matching origin cannot replace those other checks. |
| `browser/WebMessageBridge.kt:74-196,273,738-782,1069` | Profile IDs choose adapters, STA runtime origins and shim/validation modes; script setup has a Melilla source-origin fallback. | A validated descriptor must reach the shared composition boundary. Remove a literal/fallback only after all consumers use the same descriptor; JSON must not choose arbitrary classes or JS. |
| `browser/EuskadiClientAuthPostBridgeAdapter.kt:164-174,232-245` | Public `www.euskadi.eus`, broker `eidas.izenpe.com`, target `eidas2.izenpe.com`, exact source page and target path form a single POST contract. | Bind the existing profile policy to a registered transition contract; keep active-profile/document/navigation/one-shot checks and consent independent of catalog sharing. |
| `browser/IsciiiCertificateSelectionBridgeAdapter.kt:259-264`; `browser/ValenciaCertificateSelectionBridgeAdapter.kt:238-242`; `browser/XuntaCertificateSelectionBridgeAdapter.kt:125,171` | ISCIII and Valencia pin origin plus start URL; Xunta obtains its origin/profile through the signing adapter. | Reuse validated origin/start references, but preserve certificate-selection operation/callback restrictions. The Xunta dependency crosses into `signing/`, so scanning URL literals alone is insufficient. |
| `browser/CarneJovenPreTlsRetryController.kt:40-80` | Exact source/request facts identify a special pre-TLS retry contract; the retry maximum is four and the target must not simply be replayed. | Facts may be referenced from the profile. Retry state, fresh-source requirements and bounds are executable security behavior, not catalog-controlled knobs. |
| `profile/ClientAuthUrlMatcher.kt:128-176` | `comeBackURL` uses strict Base64 equivalence; `idtoken` has a narrow Leon-shaped grammar. | Adjacent protocol semantics, not DNS exceptions. Preserve URL/query/ephemeral bounds and decoding behavior; do not generalize them in an origin patch. |

### Shared-origin inventory to preserve, not expand

The complete current predicate is `isReviewedSharedNavigationOrigin` at
`profile/SiteProfileCatalogParser.kt:2478-2608`. This table groups its branches;
the function, not this summary, defines the precise current combinations.

| Lines | Reviewed family | Important restriction |
| --- | --- | --- |
| 2485-2534 | `pasarela.clave.gob.es` and `pasarela-ident.clave.gob.es` | Two different explicit owner sets, not every profile using Cl@ve. |
| 2536-2541 | Guadalajara with Mineco / AIReF / Avila | Explicit pair cases; do not infer unlisted relationships from a common broker. |
| 2542-2552 | Barcelona 2057 / Catalunya / Catalunya Seu at `valid.aoc.cat` | Listed pairs require both claims to be redirect origins. |
| 2553-2558 | Catalunya / Catalunya Seu at `tramits.gencat.cat` and `ovt.gencat.cat` | Mixed redirect roles for the former; both redirect for the latter. |
| 2560-2563 | Barcelona 2057 / Catalunya Seu at `cert.valid.aoc.cat` | The first owner's redirect flag and second owner's non-redirect flag are directional. |
| 2565-2569 | Leon with Mallorca / Albacete / Cuenca at `identificacionssl.sedipualba.es` | Specific pairs, not an arbitrary Sedipualba clique. |
| 2571-2580 | Asturias auth/public navigation; Madrid registro/cuenta; Transportes/SEPES | Mixed redirect roles for Asturias/Madrid; both non-redirect for Transportes/SEPES. |
| 2582-2588 | Carne Joven / VEA at `ws235.juntadeandalucia.es`; Aragon local CAdES / tramites at `aplicaciones.aragon.es` | Keep the exact pairs and the existing role conditions. |
| 2589-2596 | Tarragona with Barcelona 2057 / Catalunya / Catalunya Seu at the AOC origins | Pair- and origin-specific conditions differ; no blanket AOC grant. |
| 2597-2608 | El Hierro and La Gomera signing/public pairs; Lanzarote pair | Both non-redirect for the first two; mixed redirect for Lanzarote. |

**Order is an explicit design gate.** `validateCatalog` uses `putIfAbsent` for an
origin and compares each later claimant with the **first** owner. It does not
check all pairs. Consequently, an unordered owner set, transitive closure, or a
new all-pairs algorithm is not automatically behavior-equivalent. The directional
AOC branch makes this more than a theoretical concern. Capture canonical and
reordered-catalog behavior in JVM tests before choosing a representation. Any
change to the accepted set/order semantics is a separately reviewed security
change, not cleanup hidden inside data extraction.

[ADR 0001](../adr/0001-reviewed-catalunya-shared-origin.md) documents an earlier,
narrower Barcelona/Catalunya exception. It is useful rationale, but not a complete
inventory of today's additional owners. A migration must reconcile/supersede that
ADR explicitly rather than treating its historical pair as the whole policy.

### Negative findings and exclusions

- `afirma/AfirmaUriParser.kt:12-16,118-125` already obtains the selected runtime
  profile/capability and validates callback origin through `JuntaOriginPolicy`.
  No separate portal-name allowlist was found in the inspected `afirma/` parsers.
- `afirma/servlet/AfirmaServletInvocationParser.kt:11-31` performs syntax
  normalization, not authorization. Its URI operation names are not portal DNS
  names. `afirma/servlet/AfirmaServletTransport.kt:45-57` has generic HTTPS/public
  endpoint syntax, including a different port model. Do not accidentally replace
  it with the catalog's HTTPS/443 origin rules or change transport consent,
  DNS/address checks, redirects, retry or cookie policy.
- `browser/JuntaWebViewClient.kt:549-577` recognizes VEA auth-return URL shapes for
  diagnostics. This is not permission to navigate, sign, call back or use TLS.
  Likewise Play Store host checks in `browser/JuntaNavigationPolicy.kt:408-409`
  are external-app routing, not portal exceptions. XML namespaces are not hosts
  to migrate.
- Catalog packaging/assets work is separate. Today `app/build.gradle.kts:113,204`
  feeds the canonical JSON into BuildConfig and `profile/SiteProfileRegistry.kt:173`
  reads it. See the [existing assets-boundary handoff](2026-10-18-site-profile-catalog-assets-boundary.md).
  Do not add a second catalog, loader, generator or integrity mechanism here.

## Proposed data boundary and fail-closed rules

**Feasible:** exact reviewed origin/owner relationships and immutable host/start
facts for an already registered contract. **Not feasible as a mechanical JSON
move:** the complete protocol policy, independent profile pins, consent, retry
state, network destinations or release eligibility.

1. Keep `config/site_profiles_v1.json` as the only committed profile authority.
   Reuse `ExactOrigin`, profile IDs and endpoint references from
   `profile/ProfileModels.kt:33-67,109-173`; do not introduce URL suffix matching,
   wildcard hosts, configurable regexes, executable code or adapter class names.
2. A candidate per-profile `reviewedOriginShares` field should contain exact
   origin, exact peer profile ID, explicit first/later-owner role predicates and
   a reviewed evidence reference. This is a design candidate, **not an accepted
   schema or a field implemented by this PR**. Gate 0 must settle how declarations
   bind the ordered first-owner algorithm. No reciprocal inference, transitive
   owner group, inferred sibling domain or implicit role union is allowed.
   Reject missing peers, self-references, duplicate/conflicting declarations,
   origins absent from either profile, wrong roles and unreviewed extra owners.
   Missing declarations mean no sharing exception, not legacy permissive fallback.
3. Evidence references document review; they do not prove portal control or confer
   authority. Keep independent registered contract validation and current profile
   pins until the security owner explicitly approves making reviewed catalog data
   authoritative for a particular fact. A profile must not authorize itself by
   selecting an arbitrary contract ID or by repeating its own fields in a validator.
4. Keep TLS facts in the existing `clientAuthPolicy`, bound to a closed, registered
   transition contract where necessary. No generic `allowSameOrigin`,
   `allowAnySource`, `allowPost`, `allowEmptyQuery` or skip-validation switch.
   Validate method, port, source/target origin and path, exact query names/values,
   ephemeral/session bindings, continuation/return constraints, grant TTL, offered
   key type/EKU requirements and issuer-list behavior as a complete unit.
5. For bridges, construct an immutable validated descriptor once, then pass it
   through WebMessage composition and the matching network/signing adapter.
   Existing STA, Lugo and CAIB protocols remain distinct registered implementations.
   A shared navigation origin never becomes a signing, callback, TLS or endpoint
   permission. Unknown descriptor/adapter/callback combinations reject before use.
6. Preserve parser strictness: exact allowed keys and JSON types, duplicate-key
   rejection, trailing-data rejection and depth limit 32 in `StrictJson`; catalog
   maximum 262,144 Kotlin characters; canonical exact origins, HTTPS/default 443,
   no userinfo/path/query/fragment in an origin, no IP/localhost/wildcard or alias
   trick. `origins()` also requires raw text to equal canonical serialization.
   Retain every existing per-request/body/count bound; give new collections a
   reviewed finite bound within the existing catalog budget, never increase it to
   make a migration pass.
7. Validate the entire catalog before publishing any registry. Malformed or unknown
   exception data rejects the catalog; no partial-load, silent-ignore or remote
   fallback. Keep endpoint ID/URL uniqueness and redirect denial. Keep ambiguous
   global origin resolution fail-closed (`SiteProfileRegistry.kt:101`), selected
   profile scoping, QA gating and release `VERIFIED_E2E` requirements unchanged.
8. Decide schema compatibility before implementation: an explicitly recognized
   optional v1 field with deny-by-default absence requires an atomic parser/data
   update; a v2 design requires coordinated consumers and explicit version handling.
   Do not just loosen root/profile unknown-key validation. The Python public
   catalog generator (`tools/generate_public_portal_catalog.py:99-146`) validates a
   projection of IDs/start URLs, not runtime origin/adapter security. Passing that
   generator cannot certify the Kotlin catalog policy.

## Staged implementation handoff (none of these stages is implemented here)

| Gate | Concrete work / likely files | Exit evidence |
| --- | --- | --- |
| 0. Characterize and decide | Add independent behavioral fixtures to `profile/SiteProfileCatalogParserTest.kt` and `profile/SiteProfileRegistryTest.kt` under `app/src/test/java/dev/junta/firmamobile/`. Pin each shared-origin family, role combinations, first-owner order, unrelated third owners, TLS exceptions and global ambiguity. Decide ordered representation and data-vs-code authority with the security owner; update ADR rationale. | Existing canonical catalog still parses; adversarial mutations fail exactly as characterized. Expected policy values must not be generated from the same catalog being tested. No unresolved order/authority decision moves forward. |
| 1. One atomic catalog seam | Change `profile/ProfileModels.kt`, `profile/SiteProfileCatalogParser.kt`, canonical JSON and their JVM tests together. Keep legacy pins while establishing validated typed declarations. Update generator projection tests only as required by the chosen schema, not to replace Kotlin validation. | Whole-catalog fail-closed behavior, finite bounds and old/new decision equivalence on the reviewed corpus. No unused metadata presented as completed migration and no parallel unvalidated authority. |
| 2. TLS contract binding | Review parser TLS exceptions with `profile/ClientAuthUrlMatcher.kt`, `browser/ClientAuthNavigationAuthorizer.kt`, Euskadi POST bridge and Carne Joven retry controller, plus their behavioral tests. Change only one reviewed transition family per subsequent PR. | Same sources, methods, queries, TTL, navigation/document binding, one-shot use and explicit consent; no new client-certificate opportunity. If facts cannot be detached safely, retain the specialized contract. |
| 3. Bridge/URL consumers | Start with the existing STA seam in `browser/MelillaBatchBridgeAdapter.kt` and `network/MelillaBatchUrlPolicy.kt`, wiring a validated descriptor through `browser/WebMessageBridge.kt`. Then review Lugo and CAIB separately with their `network/*BatchUrlPolicy.kt` and `signing/*BatchProtocolAdapter.kt` consumers/tests. Review MiniApplet and certificate-selection families separately, including Xunta's signing dependency. | Forged source, stale navigation/document, wrong selected profile, wrong operation, mismatched pre/post token and callback all reject. No network or signing before existing authorization/consent. No protocol consolidation merely because hosts moved. |
| 4. Retire proven duplication | Remove only literals/pins demonstrated redundant by independent behavioral coverage and the approved authority model; reconcile ADR and domain docs. | One canonical source per migrated fact; no weaker accepted input set, activation promotion, dependency/lock/baseline/suppression changes or catalog limit increase. Review the full integration diff, not just a JSON sample. |

Each gate is a separately scoped follow-up, not permission to exceed the operator's
stop condition. If a gate still crosses shared adapters/protocols or the file
budget, obtain a new implementation scope before writing code. Task 1's catalog
changes must be integrated by the owner; this branch must not silently absorb them.

## Required future behavioral verification

Use real Kotlin parser/registry/matcher/bridge APIs with fixed fixtures, not Python
source-string assertions against Kotlin. Minimum negative/positive pairs:

- Unknown/duplicate keys and IDs, unsupported schema/contract, absent declaration,
  duplicate relation, missing peer/evidence, oversize and excessive depth.
- Canonical origin vs lookalike suffix, explicit noncanonical `:443`, non-443 port,
  userinfo, path/query/fragment, IDN/encoding confusion and unknown third owner.
- Every shared-origin family, reversed owner order and redirect-role mutation;
  valid selected-profile lookup while global resolution remains ambiguous.
- Direct/redirect/in-place TLS sources, Malaga POST, La Rioja same-origin exception,
  fresh Carne Joven source/retry exhaustion and invalid Euskadi POST envelope.
- STA/Lugo/CAIB operation and pre/post pairing, CAIB token grammar, cross-profile
  callback, stale document/epoch, duplicate request, subframe and one-shot reuse.
- Adapter/callback/algorithm/format/extra-property mismatch and endpoint rebinding;
  no signing/network/TLS grant from a sharing declaration alone.
- Generator output reproducibility and ID/start-URL joins, explicitly distinct
  from runtime security verification. All release/QA gates and existing bounds
  remain unchanged.

Existing behavioral witnesses include `SiteProfileRegistryTest.kt`'s shared Clave,
Transportes and Aragon ambiguity cases, and `SiteProfileCatalogParserTest.kt`'s
Sanidad same-origin, Navarra source-role, unknown Clave owner, Transportes pair and
Asturias owner mutations. Bridge/URL/protocol test suites supply additional
regressions; none was executed locally for this documentation-only change.

## Actual verification for this handoff

Four existing data-behavior tests were run through Chipupa on the phone and passed:

```sh
python3 -B -m unittest -v \
  tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest.test_committed_resource_is_byte_for_byte_reproducible \
  tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest.test_missing_inventory_match_fails_closed \
  tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest.test_duplicate_profile_start_url_fails_closed \
  tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest.test_unexpected_site_profile_root_key_fails_closed
```

These exercise generated data and malformed input, not Kotlin source text. They
provide limited baseline/projection evidence only; no new schema or runtime
security implementation exists to validate. The candidate is also checked with
`git diff --check` and a single-file scope review. Broad evidence must come from
**automatic GitHub Actions on the exact draft PR head SHA**, recorded in the PR.
No phone Gradle/assemble/lint/full unit gate, E2E, instrumentation, emulator, APK
execution or manual workflow dispatch is part of this task.

## Risks and owner decisions still open

- First-owner order/direction can cause accidental widening or regressions if
  converted into a symmetric group. Resolve with independent fixtures and review.
- Making the catalog an authority is a security-model decision, not a consequence
  of moving a literal. A generic schema alone does not replace independent pins.
- A bad sharing declaration must reject the whole catalog; schema/consumer rollout
  must be atomic, size-bounded and compatible with the canonical build path.
- Catalog edits may conflict with parallel task 1. The owner resolves the final
  catalog; this handoff changes no catalog bytes and claims no integration with it.
- No live signing/TLS result or profile promotion is claimed. This PR delivers the
  migration boundary and review gates, **not** removal of hardcoded exceptions.
