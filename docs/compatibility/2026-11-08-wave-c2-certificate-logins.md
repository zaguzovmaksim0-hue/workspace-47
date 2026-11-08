# Wave C2: Pontevedra and Extremadura certificate logins

Scope: operator-provided `IMPL_C2.md`, `IMPL_COMMON.md` and `IMPL_C.md`.
This is a second local commit after Wave C (`2d90f5cf`), without push or pull request.

## Implemented

- `diputacion-pontevedra-sede`: upgrade the existing profile to version 2, with only
  `CLIENT_TLS_AUTH`, the reviewed Cl@ve `IN_PLACE_FROM_SOURCE` POST contract, and QA-only activation.
  Click the single visible `En liña` / `a.rightformbutton` pointing at procedure 50709505,
  then the exact `Certificado dixital ou DNI electrónico` JSF anchor inside `formclave`,
  whose POST action is `/web/public/login/login.xhtml`. Choose the existing AFIRMA provider.
  The first click only opens the login boundary; it does not submit an application.
- `extremadura-tramites`: reuse the existing version-2 profile and its TLS/signing pins.
  From the public home, click `Miespacio` to `Login?APP_CODE=STA&PAGE_CODE=HOME`, then the exact
  `Acceso mediante el sistema CLAVE` anchor to `Clave2Request` with the same query, then AFIRMA.
  Add the two HOME URLs to the independent evidence pins and catalog evidence.
  This recipe never invokes the existing document-signing operation.

## Origin and DOM boundaries

Pontevedra joins the two reviewed Cl@ve origin families. Independent parser pins, directional
sharing declarations and characterization tests include its owner; selected-profile TLS authority
and the absence of native signing remain covered. The catalog has 149 directional declarations
with 5,668 peer references.

`extremadura-sede-anterior` already launches the same `extremadura-tramites` profile. It is an alias,
not a second TLS owner, so no declaration for a second `tramites.juntaex.es` profile is added.
The existing Madrid shared-origin declarations and both skipped profile objects stay identical to Wave C.

The common exact-anchor helper can require one visible match using `getClientRects().length`.
Only Pontevedra's responsive entry uses this option: two visible copies remain ambiguous,
and zero visible copies cannot be clicked. Other reviewed recipes retain their existing matching:
the saved Extremadura home exposes `Miespacio` as hidden, and its observed login route was reached
by the portal anchor's own click handler. Pontevedra also opts into membership in the one exact POST form;
existing form-metadata checks retain their prior behavior for other recipes, including ISFAS.
Synthetic DOM tests execute the actual recipe JavaScript for duplicates, hidden copies, detached
anchors, wrong forms, labels, destinations and stale/foreign documents.

## Skipped without changing the security model

- `comunidad-madrid-gestiona2`: the saved TLS evidence requests a client certificate during the
  initial handshake on `accesos.jsf?numref=2094`, before an observed source page exists.
  `DIRECT_FROM_SOURCE` requires an exact current or redirecting source; `REDIRECT_AFTER_SOURCE`
  and `IN_PLACE_FROM_SOURCE` need a previously observed transition. There is no initial-entry mode.
  REC, Tenerife, CAIB and JCCM use the reviewed Cl@ve source POST, not certificate bootstrap on entry.
  Making the entry its own fictitious source or adding a bootstrap grant would extend the model.
  The closed convocatoria's hidden certificate links are not used.
- `extremadura-portal-tributario`: the same initial-source limitation applies to the early
  `externos.jsf` CertificateRequest. The saved reused-connection flow has no second request at the
  existing `accesoCertificadoSEDE.jsf` target. The current target-only policy remains unchanged;
  a fresh-target probe does not establish authorization on the initial entry.

`ClientAuthNavigationAuthorizerTest` explicitly checks that no built-in profile can create a TLS
grant from its initial entry without an observed source. No new transition mode, trusted origin,
popup inheritance, signing permission, TTL extension or bootstrap authority is introduced.

## Evidence and remaining acceptance

The operator's real-Chrome probe `42459905561`, stored in
`~/.cache/probe-42459905561/portal-dom/dom.json`, shows both implemented routes reaching
`https://pasarela.clave.gob.es/Proxy2/ServiceProvider` with AFIRMA available.
The saved portal specifications and Wave B TLS observations support the skipped-entry decisions.
No authentication cookie, SAML body, private certificate or token is copied into this commit.

Pontevedra's `formclave:j_idt231` is a generated JSF component id. A deployment can change it;
the exact recipe then fails closed and needs revalidation. The risk and the missing Android
acceptance are registered under `docs/debt/`. This task does not establish authenticated Android
login, document signing, filing, payment, upload or administrative acceptance.

## Validation

- Final policy Python run: 75 passed, 37 subtests passed; output in `build/wave-c2-policy-final.log`.
  Five affected public-catalog and byte-reproducibility cases also passed. The earlier combined run
  (80 passed, 152 deselected, 37 subtests passed) is saved in `build/wave-c2-pytest.log`.
- `git diff --cached --check`: passed; all new Kotlin lines are at most 120 characters.
- Standards and specification reviews: identified issues corrected, no remaining findings.

- Required broad locked Gradle run: `BUILD FAILED`, exit 1, after 31m 37s. Both requested Kotlin
  compilation targets passed in that run. Release unit tests: 1,708 tests in 274 suites,
  1,672 passed, 36 failed, zero errors or skips. All failures belong to the known families the
  operator explicitly allows this wave to ignore: 35 cannot load Conscrypt's Linux aarch64 JNI
  library in the Termux linker namespace; one requires Robolectric's unsupported Linux aarch64
  native runtime. No production code or test expectation was changed to suppress these failures.
- All affected suites passed: Pontevedra binding (2), Extremadura binding (4), parser (81),
  origin shares (11), shared-origin characterization (15), navigation authorizer (55) and
  origin policy (6). The preserved gestiona2 (4) and PATTEX (2) suites also passed.
  Machine-readable counts and environment classification are in `build/wave-c2-unit-summary.json`.

- Final locked `:app:compileQaAndroidTestKotlin`: `BUILD SUCCESSFUL`, exit 0, in 7m 14s;
  29 actionable tasks, one executed and 28 up-to-date. This compiles the final scoped form check.
The requested local Gradle run includes `compileQaAndroidTestKotlin` and release unit-test filters
for `profile.*`, `catalog.*`, `network.*` and `browser.*`; its log is `build/wave-c2-gradle.log`.
After scoping the instrumentation-only form-membership check to Pontevedra, final Android-test
compilation ran separately under the same shared lock; its log is `build/wave-c2-androidtest-compile.log`.
No GitHub Actions, installation or authenticated device run is part of this local-only task.

## Changed paths

```text
app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt
app/src/main/res/raw/public_portal_catalog_v1.json
app/src/test/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizerTest.kt
app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt
app/src/test/java/dev/junta/firmamobile/profile/ExtremaduraProfileCatalogBindingTest.kt
app/src/test/java/dev/junta/firmamobile/profile/PontevedraProfileCatalogBindingTest.kt
app/src/test/java/dev/junta/firmamobile/profile/ReviewedOriginSharesTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SharedOriginCharacterizationTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt
config/site_profiles_v1.json
docs/compatibility/2026-11-08-wave-c2-certificate-logins.md
docs/compatibility/all-spanish-public-portals-inventory.md
docs/debt/20261108094132-pontevedra-jsf-control-pin.md
docs/debt/20261108094154-wave-c2-entry-auth-boundary.md
docs/debt/20261108094728-wave-c2-device-acceptance.md
docs/debt/20261108111631-legacy-anchor-form-membership.md
tools/tests/test_generate_public_portal_catalog.py
tools/tests/test_real_e2e_policy.py
```
