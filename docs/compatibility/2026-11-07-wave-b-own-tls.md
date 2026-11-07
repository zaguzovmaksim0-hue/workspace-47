# Wave B: own TLS login recipes

Implementation scope: `IMPL_B.md`, `IMPL_COMMON.md` and `waveB.txt` supplied by the operator on 2026-11-07.
All changes belong to `e2e/wave-b-own-tls`. The requested deliverable is one local commit, without publication.

These are QA-only login contracts and recipes, not evidence of successful Android authentication.
No signature of an application, filing, payment or document upload is enabled by this change.
No real certificate, authentication cookie, SAML body or session token is included in the change.

## Implemented

| Portal ID | Profile/recipe change |
| --- | --- |
| `age-ministerio-de-sanidad` | Follow Registro electrónico and its form index; expand the exact accordion, then choose Certificado digital. Replace expired `TRAM_TARDESCONPLAN` with the publicly active `TRAM_TEMPRANAATENCION` in the independent pins and catalog. |
| `aragon-tramites-sede` | In the exact same-origin `mfe_iframe`, click the certificate radio and the uniquely matched Acceder button; retain the existing broker redirect policy. |
| `comunidad-madrid-cuenta-digital-carne-joven` | Fresh web login uses the Stencil `ui-button` with `text=Acceder a Cuenta Digital`, `xRole=link`, `type=link`, then the certificate anchor containing one `ui-text` with `text=Certificado Digital`. Pin the existing IN_PLACE_FROM_SOURCE/POST mode, exact `seleccion-idp.jsf` source with ephemeral `id`, and `gestiona2` target. |
| `diputacion-huelva-portal` | Wait for the redirected public index, click Identificarse and the exact Acceder button with `onclick=login();`. Pin the authserver GET target with its ephemeral ticket. The Android lifecycle limitation below remains material. |
| `diputacion-jaen-sede` | Click Acceder, then Acceder con certificado digital; retain the existing exact redirect/back/key contract. |
| `diputacion-valladolid-sede` | Click the exact certificate login anchor; retain the existing port 21460 policy and issuer rules. |
| `gva-sede` | Click Acceder con certificado on the login page, allowing only the servlet session path suffix during recipe matching. Retain linked `idSesion` policy matching. |
| `mallorca-portal-institucional` | Reuse the existing Mallorca recipe because the catalog launches this alias directly at the reviewed trámite. The recipe therefore avoids the institutional site's separate popup hop. |
| `seguridad-social-sede` | Click the exact public Acceder button, then `button#IPCEIdP` with its reviewed label, aria-label and submit type. Add the exact query-bound IdP source and IPCE POST contract. |
| `tgss-importass` | Let the portal run its own login auto-posts and choose the same exact IPCE button. Correct the previous GET assumption to the observed POST contract, sharing the independent policy pin with Seguridad Social. |

No new transition mode, repeated-query representation, bootstrap authority or popup authority is introduced.
The new shared-origin declarations admit only the two reviewed Social Security owners and the reviewed Madrid pair.
The native signing bridge remains unavailable to these login-only profiles.

## Skipped

The eight corresponding profile objects remain identical to the base commit.

| Portal ID | Причина пропуска |
| --- | --- |
| `age-comision-nacional-de-los-mercados-y-la-competencia-cnmc` | Проверенная ссылка открывает popup. Дочернее окно намеренно не наследует профиль и разрешение источника; одного настоящего тапа недостаточно, а обход этой границы не разрешён заданием. |
| `age-sede-electronica-de-los-tribunales-economico-administrativos-tea` | Переход к селектору сертификата открывается в popup без профильных полномочий. Новый URL TLS сам по себе не решает отсутствие разрешённого источника в дочернем окне. |
| `diputacion-alava-portal` | Реальный Izenpe POST исходит со страницы без query; валидатор допускает такой непараметризованный POST только для отдельно закреплённых Cl@ve-переходов. |
| `diputacion-bizkaia-sede` | Непараметризованный Izenpe POST и его источник не выражаются существующим reviewed-контрактом этого профиля; поля тела формы нельзя выдавать за query. |
| `diputacion-gipuzkoa-sede` | Izenpe POST со страницы без query не проходит текущие ограничения источника. Нужен отдельный пересмотр безопасности, а не подмена метода на GET. |
| `extremadura-portal-tributario` | TLS CertificateRequest наблюдался уже на стартовом `externos.jsf`, до разрешённой цели. При повторном использовании соединения второго запроса на цели не было; прежняя target-only policy этого не покрывает. |
| `formentera-sede-electronica` | Живой URL содержит повторяющиеся `atributo` и несвязанные с top-level источником параметры popup. Текущая модель уникальных имён query и привязки сессии это отвергает. |
| `galicia-sede` | `_eventId` не проходит ограничения имени параметра; профиль также не входит в закреплённые same-origin TLS-исключения. Модель и разрешения не расширялись. |

## Evidence and limitations

The operator's saved specifications and sanitized TLS traces describe the observed endpoints.
Additional review corrected the incomplete Madrid DOM assumptions using these public sources:

- [Application configuration](https://gestiona.comunidad.madrid/mova_configuraciones_mova3/cudc_webapp_cuentadigital2/v3/app-config.json)
- [Application Spanish translations](https://gestiona.comunidad.madrid/mova_configuraciones_mova3/cudc_webapp_cuentadigital2/v3/i18n/es.json)
- [Angular login bundle](https://gestiona.comunidad.madrid/mova3_mf_arquitectura/6.5.2/1465.55bd7d102c4d128f.js)
- [Stencil button](https://d1ugy3v77gzx7h.cloudfront.net/uploads/mova3/cudc-lib-componentes-stencil/esm/ui-button.entry.js)
- [Stencil text](https://d1ugy3v77gzx7h.cloudfront.net/uploads/mova3/cudc-lib-componentes-stencil/esm/ui-text.entry.js)

The Sanidad accordion's public `form_gen.js` handler returns false, so selecting its reviewed panel does not add
a fragment to the pinned source URL. New helper fixtures execute the actual recipe JavaScript against synthetic
DOM objects, checking exact selection and rejection of duplicate controls, foreign pages and other login methods.

Huelva has an unresolved Android lifecycle condition: REDIRECT_AFTER_SOURCE starts its 15-second grant when the
authentication page is opened. The subsequent source POST does not refresh it. A second page-start event can
advance the epoch and clear the grant; a slow login can expire it. Tests preserve this fail-closed behavior;
they do not establish the actual Android callback order. Do not claim an accepted Huelva login until REAL E2E
observes that order. No TTL or epoch rule has been relaxed.

TLS renegotiation, port 21460, SPA rendering, redirects and real certificate/provider behavior still require
operator-authorized Android acceptance. Existing QA_ONLY activation and VERIFIED_CONTRACT status are retained.

## Validation

- `python3 -m pytest -q tools/tests/test_real_e2e_policy.py`: 69 passed, 12 subtests passed.
- Focused public-catalog tests for the affected entries and reproducibility: 5 passed, 153 deselected.
- Checksum-verified detekt 1.23.8 on the new production pin file: passed with zero findings.
- Standards and specification reviews: identified pin, fixture, catalog and DOM issues were corrected;
  no additional confirmed defect remained in the repeat review. Huelva's lifecycle limitation is recorded above.
- `git diff --check`: passed; no tracked file was deleted. Added Kotlin lines are at most 120 characters.
- `:app:compileQaAndroidTestKotlin`: passed; up-to-date on the final repeat.
- Final release test run: 884 tests across 158 suites, 883 passed, one failed, zero errors or skips.
  The sole failure is `ShimFeatureCatalogResolverScriptTest.resolverFeaturesProduceIdenticalLegacyScriptsForEveryProfile`:
  `The Robolectric native runtime is not supported on Linux (aarch64)`.
  This is the exact pre-existing environment limitation the operator instructed this wave to ignore.
  Gradle therefore exits 1 (`BUILD FAILED`), not a clean full-gate pass.
- `OwnTlsLoginProfileTest` (5), `DirectTlsProfilePinTest` (2), `SharedOriginCharacterizationTest` (14) and
  `ClientAuthNavigationAuthorizerTest` (54) all passed. Three test-fixture failures from the first run were corrected
  and passed on the final repeat.

The final Gradle command used the required shared lock and the requested filters, plus the affected browser authorizer:

```bash
flock ~/.cache/w47-gradle.lock env ANDROID_HOME="$HOME/android-sdk" GRADLE_USER_HOME="$HOME/.gradle-workspace47" \
  ./gradlew --continue :app:compileQaAndroidTestKotlin :app:testReleaseUnitTest \
  --tests 'dev.junta.firmamobile.profile.*' --tests 'dev.junta.firmamobile.catalog.*' \
  --tests 'dev.junta.firmamobile.network.*' \
  --tests 'dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizerTest' \
  -x verifyReleaseSigning --no-daemon
```

The final log is `~/.cache/w47-waveB-gradle.log`; the initial run is retained as
`~/.cache/w47-waveB-gradle-first.log`. Python results are in `~/.cache/w47-waveB-pytest.log` and
`~/.cache/w47-waveB-catalog-pytest.log`. No GitHub Actions or authenticated Android run was performed for this local-only task.

The local AAPT2 provision was initially absent and apt metadata no longer offered the pinned package version.
The existing local package provision was copied into this worktree and passed the repository's archive, runtime,
launcher and smoke verification. No bootstrap integrity check or signing requirement was weakened.

## Changed paths

```text
app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
app/src/main/java/dev/junta/firmamobile/profile/OwnTlsLoginProfilePins.kt
app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt
app/src/main/res/raw/public_portal_catalog_v1.json
app/src/test/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizerTest.kt
app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt
app/src/test/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParserTest.kt
app/src/test/java/dev/junta/firmamobile/network/JuntaOriginPolicyTest.kt
app/src/test/java/dev/junta/firmamobile/profile/DirectTlsProfilePinTest.kt
app/src/test/java/dev/junta/firmamobile/profile/HuelvaPublicSedeProfileTest.kt
app/src/test/java/dev/junta/firmamobile/profile/ImportassClientAuthProfileTest.kt
app/src/test/java/dev/junta/firmamobile/profile/MadridGestiona2PublicNavigationProfileTest.kt
app/src/test/java/dev/junta/firmamobile/profile/OwnTlsLoginProfileTest.kt
app/src/test/java/dev/junta/firmamobile/profile/ReviewedOriginSharesTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SanidadClientAuthProfileTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SeguridadSocialAutoFirmaProfileCatalogBindingTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SharedOriginCharacterizationTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt
app/src/test/java/dev/junta/firmamobile/profile/SiteProfileRegistryTest.kt
config/site_profiles_v1.json
docs/compatibility/2026-11-07-wave-b-own-tls.md
docs/compatibility/all-spanish-public-portals-inventory.md
docs/debt/20261107185703-own-tls-model-gaps.md
docs/debt/20261107190552-own-tls-device-acceptance.md
docs/debt/20261107191156-own-tls-popup-boundary.md
docs/debt/20261107191158-pattex-entry-auth-boundary.md
docs/debt/20261107191843-huelva-login-lifecycle.md
tools/tests/test_real_e2e_policy.py
```
