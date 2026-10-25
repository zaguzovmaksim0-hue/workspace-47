# Modular AutoFirma JavaScript shim: implementation handoff

## Scope and evidence

Task 5 only; **plan, not implementation**. This change adds no runtime code, resources, dependencies, schema changes or tests. The proposed follow-up PRs require separate review; they are not authorization to change portal protocols or trust policy.

Inspected on 2026-10-24 at `origin/main` commit `07b8232d1e3db006260fd11c6a40c52b80286a79`. Read `AGENTS.md`, `CONTEXT.md`, `CONTRIBUTING.md`, the Matt workflow and GitHub Actions verification policy, the complete batch-2 task package, and project-plan sections 3 and 6. The latter require bounded architecture work, preservation of consent/origin/security policies and exact-head CI, without treating unit tests as E2E acceptance.

[PR #362](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/362) is MERGED (2026-10-18, merge `a43ce14f0ff4365487bbbf0ddc4dc1e0c996ef85`). Its description and diff establish the baseline: 21 positional boolean options became `AfirmaShimCompatibilityFlags`, all defaulting to false; substitution became table-driven. It did **not** modularize JavaScript. Keep that flags object as the migration interface rather than reintroducing positional booleans.

## Current code and call map

Paths below are relative to the repository; browser/profile Kotlin paths are under `app/src/main/java/dev/junta/firmamobile/`.

| File / seam | Verified current responsibility | Follow-up touch point |
| --- | --- | --- |
| `browser/AfirmaJavascriptShim.kt` | Reads `R.raw.afirma_shim`; 21-entry `FLAG_PLACEHOLDERS`; mode, QA and STA-origin substitutions; strict origin predicate and final 96 Ki-character cap | Keep `load` as facade; delegate assembly to a pure, testable assembler with packaged-resource reader |
| `app/src/main/res/raw/afirma_shim.js` | One 1,809-line, 72,186-byte source at inspected base, one strict IIFE, one install sentinel; portal branches and common lifecycle intermixed | Mechanically split into static raw resources, then isolate feature handlers incrementally |
| `browser/WebMessageBridge.kt:attach` | Computes origin rules, checks WebView capabilities, attaches listener and one document-start script using the same origin rules | Build the script completely before installing anything that can accept requests; retain attachment cleanup semantics |
| `browser/WebMessageBridge.kt:shimCompatibilityFlags` | Hardcoded profile-ID selection; ordinary flags require active runtime profile; three batch flags use `staBatchEnabled` and family IDs | Replace selection only after a catalog-equivalence test exists; preserve batch-specific gate |
| `ui/BrowserScreen.kt` around 1383; `ui/BrowserSupport.kt:profileRequiresWebMessageBridge` | Browser creates the bridge for the selected profile | Audit lifetime/profile switch and detachment; do not refactor UI in this workstream |
| `profile/ProfileModels.kt:SiteProfile`, `profile/SiteProfileCatalogParser.kt` | No shim-feature field; parser requires schema version 1 and exact object keys | A separately reviewed schema migration is necessary, not an unvalidated optional string list |
| `profile/SiteProfileRegistry.kt`; `network/JuntaOriginPolicy.kt` | Runtime activation/build-trust/origin policy | Remain authoritative; feature selection never grants trust |
| `config/site_profiles_v1.json`, `app/build.gradle.kts` around 113 and 204; `tools/generate_public_portal_catalog.py` | Catalog source, BuildConfig embedding and catalog generation | Future feature migration must update canonical data and applicable generator/schema contracts together; coordinate with task 3, do not implement its asset migration |
| `app/src/test/java/dev/junta/firmamobile/browser/` | `AfirmaJavascriptShimTest`, `CaibAfirmaJavascriptShimTest`, `CanariasAfirmaJavascriptShimTest`, `EuskadiAfirmaJavascriptShimTest` | Existing Robolectric resource/output assertions, plus new pure JVM assembly/resolver tests |

`wrapMiniAppletMethod` dispatches sign, selection, three batch families and setup hooks in a specific order, both with and without a probe request ID. `wrapMiniApplet` and later MiniApplet/AutoScript setters and load listeners handle late replacement. `receiveMiniAppletResult` owns response dispatch; `pagehide` cancels pending calls. These cross-cutting paths are not independent portal fragments and must not be duplicated per feature.

## Target architecture

Choose **packaged static fragments assembled in Kotlin into one document-start script**, still one IIFE. Prefer `res/raw/afirma_shim_*.js` with explicit `R.raw` resource IDs, alongside the existing raw-resource mechanism. There currently is no shim under `assets/`; introducing an asset directory or a JS bundler is unnecessary. No remote scripts, ES-module imports, eval, dynamic filenames from JSON, Gradle generation plugin or new dependency.

The facade accepts the existing flags/mode/QA/origin during migration. Proposed internal seam: `assemble(configuration, readFragment): String`, where a Kotlin-owned ordered manifest maps closed module IDs to resource IDs. Tests provide in-memory strings through the same reader seam. Reject unknown/duplicate IDs, missing resources, dependency cycles and invalid selections; never quietly substitute a partial script or fall back to the monolith after failure.

Assembly order is fixed, independent of JSON array/set order:

1. IIFE opening, strict mode, existing installation sentinel and configuration header.
2. Shared constants and transport/lifecycle declarations (UUID generation, pending registries, validation helpers, cancellation, result routing).
3. Selected portal handler declarations, each with its private state; shared batch helpers only if required by an explicitly selected batch module.
4. Shared MiniApplet/AutoScript wrapper installation, response handler, document-ready and navigation listeners, generic URI/loopback protection, IIFE closing.

Initially keep declaration/side-effect order byte-identical through contiguous raw fragments, **including all portal code**. Only then introduce handler seams and selective omission one feature at a time. Do not reorder execution merely because functions are hoisted: lexical constants, top-level side effects and closure state are not.

The configuration header initially retains all **24** current placeholders (21 feature flags, mode, QA, STA origin), each exactly once, even when a feature body is omitted. Excluded feature flags are false. Portal fragments have no substitution tokens. This preserves #362's exact-once contract without accepting zero occurrences for a disabled module. A later removal of obsolete config declarations is a separate reviewed change, not part of the mechanical split.

### Feature inventory and proposed module boundaries

Names in the first column are current flags; resource/module names are proposals. Each ordinary portal module keeps its own exact origin/page/challenge/algorithm/format/property checks from `interceptMiniAppletSign`, not just the flag. Do not generalize them into permissive configuration.

| Current flag(s) | Proposed feature module | Content / non-merge constraint |
| --- | --- | --- |
| `ugr` | `ugr-login` | UGR literal, exact services, `interceptUgrSetupCall`, sign tuple; setup and sign state belong together |
| `cantabria` | `cantabria-login` | 40-hex challenge, exact extra properties and origin |
| `jccm` | `jccm-probe` | Exact base64 probe contract |
| `jccmRegistro` | `jccm-registro` | Independent protected-page XAdES tuple; not implied by `jccm` |
| `sevillaAtse` | `sevilla-atse` | `isExactSevillaAtsePage`, challenge validator, setup sequence/state and sign handler |
| `airef` | `airef-sign` | `isExactAirefSigningPage`, bounded query and payload contract |
| `cdti` | `cdti-sign` | Exact validation page/challenge and XAdES-enveloping contract |
| `policia` | `policia-sign` | Procedure page and exact XAdES-detached/filter contract |
| `granCanaria` | `gran-canaria-sign` | Exact origin and headless/filter contract |
| `fuerteventura` | `fuerteventura-sign` | Exact signing URL and PAdES visible-signature properties |
| `canarias` | `canarias-login` | `isExactCanariasChallenge`, exact page and CAdES property/filter contract |
| `mineco` | `mineco-sign` | Exact signing page and PAdES/AGE policy properties |
| `acceda` | `acceda-sign` | Exact PAdES tuple and extra properties |
| `badajoz` | `badajoz-sign` | Existing sign path plus late rewrap and bounded diagnostic WeakSet/Set state; do not extract diagnostics alone first |
| `isciiiCertificateSelection` | `isciii-selection` | ISCIII branch of `interceptCertificateSelection`, exact callback/selection semantics |
| `valenciaCertificateSelection` | `valencia-selection` | Valencia branch; do not conflate with ISCIII |
| `xuntaGalicia` | `xunta-sign-selection` | `isExactXuntaProperties`, Xunta signing and selection-hook behavior; preserve use of `xSel` in MiniApplet hooks |
| `euskadiClientAuthPost` | `euskadi-client-auth-post` | `isSafeEuskadiOpaqueRequest`, `interceptEuskadiClientAuthPost`, one-shot form guard; not a signing capability grant |
| `melillaBatch` | `sta-json-batch` | `rememberMelillaBatchCreate/Document`, `interceptMelillaBatchSign`; generic STA runtime source origin, not only a hardcoded Melilla profile |
| `lugoBatch` | `lugo-xml-batch` | `interceptLugoSetupCall/BatchSign`, exact client base and XML callback behavior |
| `caibBatch` | `caib-portafib-batch` | `caibRequestToken`, URL-base64 decode, signature ID/endpoints, setup and sign; exact path/token constraints |

Always-on core keeps generic URI forwarding, loopback WebSocket block, generic signing behavior, limits, request IDs, callback ownership and navigation cancellation. QA observation stays explicitly gated by mode/QA, not catalog-controlled. Shared helpers such as `equivalentLiteralProperties`, `base64Pattern`, and generic sign validation remain core while multiple handlers need them. Batch result decoding can use shared infrastructure but must retain JSON vs Lugo/CAIB base64-XML distinctions and cancel message types.

Absent handler means “not handled” only where the old disabled-flag branch already did so. An enabled portal handler must distinguish **not applicable**, **handled** and **rejected**; rejected input must never fall through to generic signing or another portal. Preserve dispatch precedence and `Reflect.apply` receiver/return/exception behavior. Freeze a private handler interface, not a new public JavaScript global or extensible page-controlled registry.

## Catalog-controlled activation (separate schema PR)

Propose a closed enum set `shimFeatures` on the catalog profile, mapped to reviewed module IDs in Kotlin. Treat it as code selection only, never as an origin/algorithm/endpoint/consent permission. No executable source, URL overrides, regexes or resource paths in the catalog.

1. Introduce an explicitly versioned schema transition (proposed v2) with strict key validation. Old v1 parsing remains unchanged; do not silently allow arbitrary new keys. A temporary v1-to-features resolver may reproduce today's mapping exactly until all packaged data/generators migrate. Remove that resolver only after differential tests cover every profile.
2. For v2 require the field on every profile, using `[]` for none; reject unknown enum values, duplicates and unsupported/conflicting combinations. Do not ignore unknown features or infer features from display name, host suffix or capabilities alone.
3. Resolve the selected ID through the **active runtime registry actually used by the browser**. Missing/inactive/QA-filtered profiles select no portal features; preserve generic behavior and current bridge-attachment eligibility. Release/QA and verified-only selection must not be widened.
4. During migration validate catalog feature sets against the existing `shimCompatibilityFlags` mapping for every catalog profile, with active/inactive cases. Preserve the non-ordinary batch rule: `staBatchEnabled` and runtime adapter/source-origin must still authorize the batch family. In particular today's `melillaBatch` is the enabled-STA fallback excluding Lugo and CAIB; enumerate all existing STA profiles before migrating, not just Melilla by name.
5. Require each configured feature to match its reviewed profile/operation/adapter contract. A known feature on a wrong profile must fail validation rather than enabling another portal's path. Multiple features require an explicit reviewed combination; auto-added dependencies are shared infrastructure only, never another portal feature.
6. Create a fresh immutable assembly selection on attachment/profile change. Detach old handlers/listeners, abandon old requests and retain document/epoch association. Initially avoid script caching; any future cache key must include feature set, mode, QA, normalized batch origin and resource version, not only profile ID.

Coordinate this PR with tasks 3 and 8 (catalog loading and parser debt). Do not modify their files concurrently or bundle their refactors here. Catalog schema approval and the precise v1 retirement point are owner decisions; this document does not change either.

## Security and assembly invariants

- **Native trust remains final:** preserve `JuntaOriginPolicy.webMessageOriginRules`, exact STA source-origin matching, main-frame checks, active profile/adapter checks, document/navigation epoch and native routing/consent. Do not add wildcard origin rules, more endpoints, automatic portal submission or signing privileges.
- **Origin substitution remains exact:** retain `URI` validation: non-opaque HTTPS with host, no user info, port absent/443, empty or `/` path, no query or fragment. Strip only the existing trailing slash and use `JSONObject.quote`; never interpolate JSON values as JavaScript source. Validate even when no batch feature is enabled, as today.
- **Template integrity:** before substitution assert each of 24 expected tokens occurs exactly once in the composed template, and reject unexpected `__JFM_` tokens. After substitution require no unresolved token. Missing/duplicate token, fragment or malformed configuration aborts before any script installation; do not “repair” it with defaults.
- **Script size:** retain `MAX_SCRIPT_CHARS = 96 * 1024`, nonblank output and the final post-substitution check on the entire returned string, not each fragment independently. Count Kotlin characters, not UTF-8 bytes; include header, separators and suffix. Add bounded fragment/aggregate reading without increasing this final limit.
- **Payload limits:** preserve URI/argument limits 1,048,576; max arguments 32; direct data 699,052; extra properties 65,536; batch URL 8,192; identifier 1,024; batch documents 64; batch result 786,432; timeout 120,000 ms; Euskadi opaque request 1..4,096. Keep native limits independently enforced. Module omission must not remove generic guards.
- **Lifecycle:** one installed sentinel, one response owner, shared pending maps, cryptographic request IDs only, duplicate/in-flight handling, timeout cleanup, pagehide cancel types, callback terminality and rewrap behavior. Observation mode must still never enable direct functional signing; URI handling retains its current separate behavior.
- **Privacy/provenance:** QA emits only existing bounded stages/IDs, no new payload/certificate/signature logging. Fragments derive from existing first-party code, not copied upstream scripts; no new dependency or legal claim. Do not weaken strict verification, locks, detekt baseline or add suppressions.

## Verification design: JVM assembly, no WebView E2E

Use JUnit and the existing Robolectric resource loader; keep pure assembly/resolver tests independent of Android. These are tests of returned output and failure behavior through the assembler interface, **not** Python scans/hashes of Kotlin source. Do not require a new JS engine dependency: no Rhino/Nashorn/Graal test engine is declared in the inspected Gradle/version-catalog inputs, and JDK 17 is not evidence that one is available.

| Test slice | Required cases and assertions |
| --- | --- |
| Mechanical split parity | A reviewed test-only legacy JS fixture from the exact pre-split base; compare full configured output for defaults, each single flag, all flags, FUNCTIONAL/OBSERVATION and QA on/off. Preserve line endings and separators deliberately. Compare strings, not Kotlin source hashes; later intentional module omission updates explicit expected fixtures in the same feature PR. |
| Manifest/reader behavior | Missing/blank fragment, duplicate ID, unknown ID, dependency cycle, ordering independent of input set order; resource read failure returns no partial script. Assert a module is read once and inactive portal bodies are absent, while all mandatory core fragments remain. |
| Placeholder integrity | Delete or duplicate each expected token in a synthetic fragment; inject unknown token; assert failure. Defaults/singles/all-enabled output contains no `__JFM_`; header exact-once contract still holds after omission. |
| Size | Synthetic fragments test final lengths cap-1, cap, cap+1 after substitutions and separators; blank output rejected; multibyte UTF-8 input demonstrates character-vs-byte semantics. |
| STA origin | Existing accepted HTTPS origins with optional slash/443; reject HTTP, user info, opaque/missing host, other port, path, query, fragment; verify JSON-quoted normalized output and rejection even with batch flags off. |
| Feature resolver | Every packaged profile vs legacy flags, inactive and missing profiles, release/QA filtering, empty list, unknown/duplicate/mismatched feature, conflicting batch families, STA fallback profiles and runtime gate off; schema v1/v2 compatibility and strict-key failures. |
| Selection isolation | Core only, each of 21 flags, all valid profile combinations, and low-level all-enabled assembly stress case. All-enabled is a fixture/size stress case, not a valid production profile. No unrelated portal body; dependencies do not enable another portal. |
| Integration resources | Retain/update the four existing shim test classes to load the real packaged fragments through the facade; existing native bridge/adapter tests continue to cover origin/main-frame/consent/epoch rejects. Add targeted attachment-failure tests for no leaked listener if assembly fails. |

Existing string assertions and exact-output fixtures detect assembly regressions; they do **not execute JavaScript**, prove late-script timing, DOM behavior, callback execution or real portal acceptance. Do not relabel them behavioral JS execution tests. Native bridge regression tests cover the native trust seam independently. Any future JS-execution harness or physical/WebView acceptance needs a separately authorized plan; it is not a prerequisite to write this document and is not run here. No WebView-E2E, instrumentation, emulator, APK installation or manual workflow dispatch.

## Delivery sequence and stop conditions

Each step is a separate bounded PR with one concern, regression evidence and exact-head CI/security. Never start the next step from an unmerged candidate. If extraction needs over roughly 20 production files in a PR, split further; changes to shared protocols, portal adapters or trust model stop implementation and require an amended plan/owner decision.

1. **Test seam and failure baseline.** Introduce a pure assembler behind the unchanged facade and current monolithic resource. Add synthetic-reader placeholder/origin/size/error tests plus reviewed legacy output fixtures. No catalog or script semantic changes. Acceptance: existing output identical, all negative cases fail closed.
2. **Mechanical fragments, all included.** Split only a few contiguous ranges per PR, explicit resource order, same IIFE and configured bytes. Add real-resource reconstruction parity checks. Preserve existing initializer order; no feature omission yet. Keep any needed legacy fixture test-only, never as production fallback.
3. **First portal isolation.** Start with a stateless exact-tuple branch such as Cantabria, introducing the minimal private dispatch seam and closed manifest entry. Keep rejected-vs-not-applicable distinction and legacy order. Select using existing flags only. Acceptance: enabled profile output/contract preserved, disabled output omits only that portal body; core unchanged. If JVM assembly checks cannot give adequate evidence for a proposed semantic change, stop at extraction rather than claim equivalence.
4. **One feature per PR.** Repeat for ordinary portals, then selection, then stateful UGR/Sevilla/Badajoz, and finally each batch family/Euskadi. Move declarations, setup, dispatch and response/lifecycle dependencies together; do not copy common pending state. Recheck exact native contract tests per portal. No adapter/protocol rewrite. Keep configuration header/table stable.
5. **Catalog schema and resolution.** After owner approval and coordination with tasks 3/8, add versioned feature data/model/parser/generator tests and legacy-vs-catalog mapping for every profile. Switch `WebMessageBridge` to validated registry-derived features while preserving batch gates, origin rules and facade. This is separate from extraction, and may need multiple data/consumer PRs with a documented compatible transition.
6. **Retire migration glue.** Remove the old ID resolver and obsolete production monolith only after every profile has explicit parity evidence. Keep fixtures and real-resource tests. Keep flags as a facade compatibility layer until all callers are audited; removing that interface is optional, separate work.

Rollback is a normal revert of the last feature PR, or restoring the previous reviewed static mapping/resource set, not a runtime “try all features” switch. Do not raise catalog compatibility status or claim verified E2E in any extraction PR.

## Checks for this documentation candidate and remaining risks

Local verification is limited to whitespace/scope, referenced-path and Markdown checks without compilation. No local Gradle, build, compiler, Kotlin tests or JS runtime execution was requested or performed. Automatic PR CI/security is the broad check on the final commit; record its actual results in the PR rather than predicting them here. Instrumentation/R8 startup skips are not passes.

Primary risks for implementation: closure/initialization order, catch/return/fallthrough changes, late MiniApplet/AutoScript replacement, confusing the three batch families, unintended catalog trust expansion, and stale scripts after navigation/profile changes. Assembly parity cannot eliminate DOM/timing or real-portal risks. Catalog field/version approval, invalid-assembly attachment error UX, and any later device acceptance remain explicit follow-up decisions. Generic signing policy, portal protocol support and all other batch-2 tasks remain out of scope.
