# Handoff для следующего чата — Junta Firma

## Где продолжать

- приватный репозиторий: `zaguzovmaksim0-hue/workspace-47`;
- ветка: `feature/ws024-secure-tunnel-20260728`;
- всегда сначала выполнить `git fetch` и проверить HEAD ветки;
- подробная E2E-фиксация:
  `docs/e2e/2026-07-29-junta-ofvirtual-auth-success.md`;
- дополнительная физическая проверка 24-часового unlock и повторного E2E записана в
  `docs/test-report.md`, milestone P07C.

## Подтверждённый результат

Профиль `junta-ofvirtual` повторно прошёл реальный вход на физическом POCO F6 Pro
30 июля 2026 года. Портал принял CAdES-аутентификацию и открыл:

`https://ws072.juntadeandalucia.es/ofvirtual/ovMisTramites/index`

Проверенный объём — только вход с сертификатом:

- origin `https://ws072.juntadeandalucia.es`;
- endpoint MiniApplet 1.5 на `ws024`;
- `SHA1withRSA`, `CAdES`, detached/explicit;
- PRE → локальная подпись → POST → callback → form submit → portal accepted;
- итоговая страница: `Mis trámites pendientes`, HTTP 200;
- страница входа и `No se pudo completar la firma` отсутствовали.

Не заявлять, что проверены все процедуры, отправка заявлений или документальная
подпись внутри кабинета.

## 24-часовое восстановление сертификата

- `32b27ca`: зашифрованное восстановление unlock в течение максимум 24 часов;
- AES-GCM key хранится в Android Keystore;
- ciphertext хранится атомарно в `noBackupFilesDir`;
- срок отсчитывается от успешного ручного ввода пароля и не продлевается после
  перезапуска процесса;
- force-stop, cold start и `pm install -r` не потребовали повторного пароля;
- cache file сохранился при обновлении и имел размер 101 bytes;
- manual lock, clear session, replacement, forget, wrong cached password,
  expiry/tamper/reference mismatch очищают cache;
- clear app data и uninstall также удаляют cache;
- пароль, PKCS#12, private key, certificate body и подпись не сохранять в Git,
  shell output или diagnostics.

`3045e7d` исправляет UI-текст: он теперь явно сообщает о 24 часах и условиях
досрочного сброса.

## Другие исправленные причины Oficina Virtual

- `6538e1a`: безопасный upgrade точного legacy HTTP GET Oficina Virtual на HTTPS;
- `26230ab`: сворачивание больше не блокирует сертификат и не уничтожает WebView;
- `b3f1817`: profile version 2, `VERIFIED_E2E / ENABLED`, каталог и UI показывают
  `VALIDADO CON EL PORTAL` / `Verificado: Firma electrónica`;
- текущий P07D patch ограничен точным `https://ws072.juntadeandalucia.es/ofvirtual/`
  и исправляет только три доказанных legacy-дефекта: `MenÃº`, несовместимые
  Font Awesome 5 classes при загруженном FA 4.1 и Bootstrap Collapse markup без
  загруженного Collapse JavaScript. Меню и иконки проверены на физическом устройстве.

## Установленная сборка

- package: `dev.junta.firmamobile`;
- versionName: `0.1.0-qa`;
- установленный SHA-256 (физически подтверждённая F-09/F-10 сборка):
  `0258378038d703979239c8701e1e8d2ce68ecabc7de5699b68cbccbef1e5ceec`;
- F-09/F-10 QA APK установлен поверх данных; локальный APK и `base.apk` совпадают;
- `zipalign`: PASS;
- APK Signature Scheme v2: PASS, one signer;
- direct-only: tunnel выключен, relay tuple отсутствует;
- зашифрованный 24-hour cache сертификата сохранён после обновления.

## Последний локальный QA gate — F-15B

- canonical site-profile JSON: one tracked source,
  `config/site_profiles_v1.json`; SHA-256
  `a45cf2bbfe13d3492a963d0b8866c676ec13e5e95fac99e0cf2e0eeac568dc4c`;
- public catalog: 182/182 inventory-backed entries, 7 exact profile bindings,
  source revision
  `018f94bb22cb42b3093f86b028cf87b490bcee8f5e15e255d8c9728594e71951`;
- Debug unit: 499/499, 0 failures/errors/skips;
- QA unit: 499/499, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Python catalog/tool tests: 91, 0 failures/errors, 1 environmental skip
  (`hardlinks unavailable`);
- Go `test ./... -count=1`, `go vet ./...` and relay build: PASS;
- pinned `govulncheck` 1.6.0: no vulnerabilities found;
- APK alignment, v2 signature, exactly one signer, QA manifest hardening and
  exact forbidden-canary scan: PASS;
- release without private signing inputs: expected fail-closed; no release APK;
- Debug APK SHA-256:
  `cce4e9c36668bb62520c9f7ccfa7cffbda5626b84230e56e9bc5deb9dd5573e7`;
- QA APK SHA-256:
  `d57ccc3850c8f44d4f01f5d578c5c0a9013c7310d98c40faa39cc1fc1f8ace6d`;
- QA AndroidTest APK SHA-256:
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`;
- current F-15B APKs were not installed and no physical portal/E2E flow was run;
  the installed physical-device build remains the earlier F-09/F-10 build.

Two earlier combined clean runs exposed an order-sensitive transient in the
existing `ProfileHttpTransportTest`: immediately after the bounded DNS saturation
test, a later resolver submission returned `NETWORK_ERROR` instead of the
expected classified private-address result. No network source changed in F-15B;
the exact test, full Debug, full QA and the final current-content combined gate
all passed on rerun. Treat this as residual unit-test determinism work, not as a
new portal or runtime-network claim.

## Сетевой инцидент 30 июля 2026

Перед успешным повтором наблюдался `SIGNING_SERVICE_UNAVAILABLE`: DNS `ws024`
работал, но TCP/TLS завершался `ConnectException`/timeout до PRE. Такой же timeout
был воспроизведён из Termux. Позже точный endpoint вернул HTTP 200 при активном
WARP, и неизменённая сборка выполнила E2E успешно. Считать это временным внешним
сбоем маршрута/доступности; точная причина не доказана. Не добавлять обходы,
ослабление TLS или relay fallback без новой воспроизводимой evidence.

## Исправление статуса Ovorion / F-15A

- legacy profile `junta-andalucia` MiniApplet 1.4 не имеет отдельной E2E-evidence;
- ошибочная историческая промоция внутри `84c3c937` отменена;
- profile catalog version 9: `EXPERIMENTAL / ENABLED`;
- sensitive runtime доступен для контролируемой QA-проверки, но release registry
  профиль не активирует;
- публичный каталог остаётся `E2E_PENDING / IMPLEMENTED_NOT_E2E`;
- новый test gate запрещает расхождение E2E-статуса между profile и public catalog.

## UniZAR login VERIFIED_E2E — 2026-07-30

- profile `unizar-tramitador`, acceptance build profile version 1;
- QA APK SHA-256
  `190115079eba9c942db9e1fa3a20b4119eac445fef9406c90c4254729cc5fc7f`;
- exact origin `https://tramita.unizar.es`;
- 20-byte precalculated challenge, `SHA1withRSA`, detached `CAdES`;
- exact properties `precalculatedHashAlgorithm=SHA1` and `serverUrl`;
- PRE → local RSA signature → POST → AutoScript callback → portal accepted;
- final authenticated area showed `Buzón Electrónico` and `Mis Gestiones`;
- no procedure was created, modified or submitted;
- promoted metadata: profile version 2, `VERIFIED_E2E / ENABLED`, profile
  catalog version 10, public `E2E_VERIFIED / VERIFIED_E2E`;
- Storage/Retrieve, co-sign, counter-sign and document signing remain blocked.
- promotion build final gate: Debug 452/452, QA 452/452, Python 75 with
  1 environmental skip; lint/builds PASS;
- promoted QA APK SHA-256
  `28373cb7cccf9a8a80347ff06e36192feab29474578a566d9021ef1384b36c61`.
- installed `base.apk` matches the same hash; `pm install -r` succeeded;
- cold launch restored the certificate without password; encrypted cache remained
  101 bytes, mode 600;
- device smoke reports `VERIFIED_E2E / OPEN_REQUESTED / WEBVIEW_ACTIVE` for the
  exact UniZAR profile and adapter.

## RedSARA safe E2E blocker — 2026-07-30

- exact profile `reg-age-redsara`, adapter `local-xades-detached-v1` opened in QA;
- public routes `Nuevo registro` and `Mis registros` both lead to `/es/login`;
- the only visible authentication action is Cl@ve;
- contractual XAdES signs a prepared application-summary XML and then calls
  `saveXMLAutoSign`, so portal acceptance requires a real administrative case;
- no Cl@ve login, draft, form data, XML, signature or submission was created;
- keep profile version 1, `VERIFIED_CONTRACT / QA_ONLY` and public
  `E2E_PENDING / IMPLEMENTED_NOT_E2E`;
- do not promote from local XAdES tests or the Android delivery callback.
- gate after catalog update: Debug 452/452, QA 452/452, lint and QA build
  PASS; Python 75 with 1 environmental skip;
- blocker-only QA APK is built but not installed separately; install the next
  combined security build instead.

## F-05 secure-window state policy — 2026-07-30

- `SensitiveWindowStatePolicy` is the single MainActivity decision point;
- `FLAG_SECURE=false` only for `LoadingReference` or `NoCertificate` with
  `SigningUiState.Idle`;
- `FLAG_SECURE=true` for Locked, Unlocking, Unlocked and every signing state
  other than Idle; therefore unlocked certificate UI, native catalog, portal
  WebView, confirmation, signing, completed and failed states are protected;
- first-run visual tests and isolated debug probe remain capturable;
- device: cold restored certificate and active UniZAR WebView both showed
  `SECURE` in `dumpsys window`;
- installed QA/base hash:
  `fe303b10658a8fcf3698e00d42e5714e4d7b42ba28208c8beb196da505963199`;
- no authenticated screenshots should be requested after this milestone; use
  sanitized UI semantics, smoke results, window flags and coarse logs.

## F-08 profile-scoped cookies/session data — 2026-07-30

- native cookie bridges require one active `SiteProfile` and exact endpoint URL;
- profiles without endpoints cannot construct the bridge;
- current-site cleanup uses exact HTTPS origin and never falls back to global
  deletion; parent-domain/malformed cookie metadata remains untouched;
- `Borrar datos de este sitio`, `Cerrar sesión` and `Borrar todos los datos web`
  are independent commands with independent confirmations;
- closing the session locks the certificate but no longer deletes other portal
  data;
- global data deletion exists only in `SiteDataCleaner.clearAllConfirmed`;
- device capability probe (no UI): Google WebView 150.0.7871.181; MULTI_PROFILE,
  GET_COOKIE_INFO, WEB_MESSAGE_LISTENER and DOCUMENT_START_SCRIPT all true;
- do not adopt physical WebView profiles from this observation alone;
  `WebViewCompat.setProfile` remains unused.

## F-17 public IPv6 DNS-result policy — 2026-07-30

- Android and Go relay policy revision: IANA IPv6 Special-Purpose 2025-10-09;
- ordinary IPv6 requires `2000::/3`; special-purpose, scoped and mapped fail;
- `64:ff9b::/96` requires a public embedded IPv4;
- profile URL/IP-literal policy is unchanged: only canonical DNS hostnames;
- Android filters unsafe answers, then OkHttp pins hostname, approved DNS set and
  actual connected peer;
- relay rejects an entire unsafe/mixed set, dials bracketed IPv6 literal and
  verifies exact `RemoteAddr`;
- full Android/Go/Python/artifact gates PASS;
- physical `PublicIpAddressPolicyInstrumentedTest` returned `OK (1 test)`;
- this proves Android classification only; do not interpret it as live IPv6
  routing or portal E2E.


## F-13 process-scoped Client TLS lifecycle — 2026-07-30

- `JuntaFirmaApplication` owns one `ClientCertPreferenceCoordinator` for the
  process;
- `WebView.clearClientCertPreferences` is isolated in one Android adapter;
- `CLEARING` and sticky `FAILED` prevent creation of every portal WebView;
- timeout is exactly 3 seconds; exception, missing callback and stale generation
  fail closed;
- a grant is activated only after callback `CLEARED` and exact profile/epoch/TTL
  revalidation;
- background, Activity disposal, renderer death, profile switch and certificate
  lock detach local callbacks and request process cleanup;
- a later successful cleanup is the only in-process recovery from `FAILED`;
- physical Android callback test passed without opening a WebView or portal;
- no additional Client TLS profile was enabled. F-03 remains open and requires
  exact runtime contract plus physical E2E evidence.

## F-09/F-10 monotonic TTL/replay hardening — 2026-07-30

- все security TTL для MiniApplet/signing/reply используют process monotonic
  time; civil clock не решает допуск операции;
- lifetime от bridge observation через PRE/local/POST/callback — 2 минуты;
- terminal request IDs хранятся в bounded replay ledger 5 минут и затем
  очищаются до capacity-check;
- monotonic rollback, exact boundary, concurrent confirm и concurrent terminal
  delivery покрыты hostile tests;
- JS shim не содержит `Math.random()` и без Web Crypto fail-closed;
- полный gate: Debug 499/499, QA 499/499, lint/builds/APK/Go/Python PASS;
- установленный QA/base SHA-256:
  `0258378038d703979239c8701e1e8d2ce68ecabc7de5699b68cbccbef1e5ceec`;
- cache 101 bytes/mode 600, cold unlock и `FLAG_SECURE` сохранены;
- физические Client TLS callback и IPv6 classifier regressions: `OK (1 test)`
  каждый;
- пользователь выполнил новый Oficina Virtual login на этой сборке и подтвердил
  корректное открытие портала и меню; scope только login CAdES;
- test package и staging APK/XML удалены; чувствительные артефакты не сохранены.

## F-14 CI/supply-chain gate — 2026-07-31

- workflows имеют только `contents: read`, immutable action SHAs, timeout и
  concurrency gates; `pull_request_target` отсутствует;
- Gradle distribution и wrapper JAR согласованы с 9.4.1 и закреплены официальными
  SHA-256; dependency verification без wildcard trust;
- Dependabot: Gradle, Go modules, GitHub Actions;
- Gitleaks 8.30.1 full-history: 166 commits, zero findings;
- Go и CI закреплены на 1.26.5; test/vet/build/govulncheck PASS;
- OSV 2.3.8 проверяет только `tools/requirements.txt` и `ws024-relay/go.mod`,
  zero vulnerable packages;
- Android: Debug/QA 499/499, lint/builds/APK checks PASS; release без private
  signing material корректно fail-closed;
- local race не выполнен: Android/arm64 не поддерживает `-race`; обязательный
  Linux CI gate сохранён;
- не утверждать полное CVE-покрытие Gradle: verification metadata — integrity
  ledger, а не runtime lockfile. Отдельный reviewed Gradle SCA остаётся residual
  hardening.


## F-15B catalog-generation deduplication — 2026-07-31

- `app/src/main/res/raw/site_profiles_v1.json` moved unchanged to
  `config/site_profiles_v1.json`; no second tracked profile JSON remains;
- Gradle safely escapes and emits the canonical file as
  `BuildConfig.SITE_PROFILE_CATALOG_JSON`; the large Kotlin JSON copy is gone;
- Oficina Virtual and Educación convocatoria 46 moved from Python supplemental
  objects into the reviewed inventory as `ES-PUB-0181` and `ES-PUB-0182`;
- `PROFILE_BINDINGS` and `_supplemental_entries()` were deleted;
- all eight profile/public bindings now require exact full URL equality between
  profile `startUrl` and inventory `entry_url`;
- malformed profile roots, duplicate IDs/start URLs, missing or multiple
  inventory matches and profile/surface collisions fail closed;
- generated public catalog remains 182 entries with no portal added/removed and
  no trust/evidence change; only the two inventory IDs and source revision differ;
- no push, device installation, portal navigation, authentication or signing was
  performed for this task.

## F-03 AEAT exact Client TLS profile — 2026-07-31

- profile: `aeat-mis-datos-censales`, version 1,
  `VERIFIED_CONTRACT / QA_ONLY`;
- exact source:
  `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`;
- exact target:
  `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`;
- TLS 1.2 probe without a certificate observed `CertificateRequest`, non-empty
  acceptable issuers and safe 403 termination; Android callback/acceptance is
  not inferred from this;
- new explicit modes: Carné Joven `REDIRECT_AFTER_SOURCE`, AEAT
  `DIRECT_FROM_SOURCE`;
- direct mode rejects legacy/subframe, wrong profile/source, suffix host,
  non-443, wrong/encoded path, fragment, any query and empty `?`; same tuple at
  the same epoch is one-shot;
- request handler retains exact host/port, TTL/epoch, validity, keyUsage, EKU and
  issuer checks; AEAT permits RSA/EC but `allowEmptyIssuerList=false`;
- QA registry resolves source as `TRUSTED_CLIENT_AUTH` and request origin only as
  `BROWSE_ONLY`; release resolves neither;
- public catalog remains 182 entries; AEAT is bound as
  `E2E_PENDING / IMPLEMENTED_NOT_E2E`;
- no tax modification, signature, payment or submission is in scope;
- fresh gates: Debug 509/509, QA 509/509, 143/143 Gradle tasks, lint/build,
  artifact, release fail-closed, Python 94 with one environmental skip and Go
  test/vet/build PASS;
- exact installed QA/base SHA-256:
  `ca5b351656cb41904f3774ed2a84ac002041d9babdf5fe877d6191d04d6befe2`;
- physical smoke resolved the profile but stopped at locked certificate state:
  `profileResolvedOnly=1`, `webViewActive=0`, zero failures;
- WebView callback/native confirmation/portal acceptance were not reached; the
  password was not read or automated;
- blocked evidence: `docs/e2e/2026-07-31-aeat-client-tls-blocked.md`;
- next gate: user manually unlocks the existing certificate, then run only the
  exact read-only `Mi área personal → Mis datos censales` flow. Retain
  `QA_ONLY` unless callback and accepted portal authentication both pass.

## Ограничения и следующие задачи

1. Legacy UI Oficina Virtual исправлен только для exact `ws072 /ofvirtual/`;
   не распространять compatibility script на другие сайты без отдельной evidence.
2. Instrumentation test APK на HyperOS иногда блокируется политикой установки
   `testOnly`; не обходить системную защиту ослаблением приложения.
3. Release остаётся direct-only; QA relay не нужен для уже подтверждённого
   direct E2E и не должен включаться в release.
4. `reg-age-redsara` остаётся `VERIFIED_CONTRACT / QA_ONLY`, пока портал
   реально не примет XAdES E2E. UniZAR уже повышен только для login CAdES.
5. Не сохранять в Git скриншоты кабинета, пароль, PKCS#12, сертификат, подпись,
   cookie или персональные идентификаторы.
6. DNS-executor unit-test isolation завершён: saturation test использует
   собственный bounded executor и ждёт его termination; production fail-closed
   policy и лимит двух worker'ов не изменены.
7. F-03 AEAT имеет точный runtime-контракт и QA-only реализацию, но остаётся
   заблокирован для release до WebView callback и принятого read-only E2E.
   F-12 по-прежнему требует отдельного реального административного случая.

Для продолжения в новом чате достаточно написать: «Открой приватный репозиторий,
ветку `feature/ws024-secure-tunnel-20260728`, прочитай
`docs/handoffs/NEXT_CHAT_HANDOFF.md` и продолжай от текущего HEAD».


## Deterministic DNS executor test isolation — 2026-07-31

- root cause: JVM tests shared the process-wide zero-core `ThreadPoolExecutor`;
  caller/Future completion could precede workers returning to
  `SynchronousQueue`, so a rapid next submission could fail closed;
- the initial saturation-only patch passed early repeats but a mandatory fresh
  QA run failed the ordinary address loop at `2001:10::1` with
  `NETWORK_ERROR`; this intermediate failure was not accepted or hidden;
- `HttpsProfileHttpTransport` now has an internal `ExecutorService` seam whose
  runtime default remains the unchanged production `DNS_EXECUTOR`;
- all 18 JVM-test transport constructions explicitly use test-owned executors;
  synchronous DNS is inline, while timeout/cancel/saturation own bounded pools
  and await termination;
- production remains `0..2`, 30-second keep-alive, daemon workers,
  `SynchronousQueue`, `AbortPolicy`, core-thread timeout and fail-closed
  `NETWORK_ERROR` on rejected submission;
- final focused evidence: combined Debug/QA PASS plus five additional sequential
  runs per variant; complete Debug 500/500 and QA 500/500;
- lint/build/toolchain/APK artifact/release fail-closed PASS; Python 91 with one
  environmental skip; Go test/vet/build PASS;
- final APK SHA-256: Debug
  `dbddc5a31a719fa59ff6a5d7ec1a7199f4fe916982f07399327e3869c0754758`,
  QA `6132831e16ddd807c2ac7ec4ddea3a6d63ab5045ce6f89d6365157a493300944`,
  AndroidTest
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`;
- current Termux has no `govulncheck`; relay code/module did not change and
  test/vet/build were rerun;
- no push, APK installation, physical-device test, portal navigation,
  authentication, certificate operation or signing was performed.


## Android runtime dependency SCA — 2026-07-31

- `app/gradle.lockfile`: 140 external Maven rows, only
  `debugRuntimeClasspath`, `qaRuntimeClasspath`, `releaseRuntimeClasspath`;
  SHA-256 `286bcc684775520851aa5de6a4bb01fa172a72ca87dae2dc73e671fc76afa64d`;
- `LockMode.STRICT`; no `lockAllConfigurations`, ignored dependency or test/build
  configuration claim;
- verification task must materialize artifact views. The first graph-only
  implementation accepted a hostile `0.0.0-stale-lock` and was rejected; the
  final task fails closed with dependency-lock enforcement;
- updater validates/removes only exact `empty=incomingCatalogForLibs0` settings
  sentinel, preserves unknown lock evidence, and reproduces the app lock
  byte-for-byte;
- security workflow verifies the lock before pinned OSV 2.3.8 and scans only the
  Android runtime lock plus Python/Go explicit inputs;
- checksum-verified OSV Linux ARM64 binary under Debian/proot: 140 Android + 1
  Python + 1 Go packages, `No issues found`; native Termux is blocked by seccomp
  `faccessat2` before scan and is not marked PASS;
- final gates: Debug 500/500, QA 500/500, Python 94 with one environmental skip,
  lint/build/toolchain/APK artifact/release fail-closed/Go test-vet-build PASS;
- final APK SHA-256: Debug
  `7a93dddcccc90b339e33df55f6cac8a24ad26acfe4b8ced7c6ed6707dee62233`,
  QA `7c99595546f9fa8cb0e6bd77832531c648ffa06d62081309d244b5bad840abcd`,
  AndroidTest
  `f1bb688aaae481752a3095a70ede7b16669ae06cab8c1c09b755308d4f04dabc`;
- no dependency version, runtime code, portal scope, device state, certificate,
  authentication or signing operation changed; no push performed.


## LATEST F-03 RESUME POINT — 2026-08-01

This section supersedes the older F-03 physical-state notes above.

The user manually unlocked the existing certificate on the installed QA build.
The password was not supplied to, read by, copied by, logged by or automated by
the agent.

Current verified physical state:

- installed QA/base SHA-256 remains
  `ca5b351656cb41904f3774ed2a84ac002041d9babdf5fe877d6191d04d6befe2`;
- unlocked UI state confirmed by `Bloquear certificado`, `Elegir otro` and
  `Olvidar certificado`;
- protected `aeat-sede` smoke returned `total=1`, `webViewActive=1`,
  `profileResolvedOnly=0`, `catalogOnly=0`, `failures=0`;
- with `dev.junta.firmamobile/.MainActivity` restored to foreground, the exact
  public WebView label `Mis datos censales` was observed;
- the target was **not clicked** and Client TLS authentication was **not**
  completed.

Why execution stopped: Android Control Bridge repeatedly brought
`io.termux.androidcontrol/.MainActivity` to foreground during accessibility
attempts. The first click attempt therefore targeted the wrong application's UI
and was rejected. After force-stopping that service UI and returning `Junta
Firma` to foreground, the following `uiautomator dump` was empty. No guessed
coordinate tap was performed.

Still unproven: exact AEAT WebView `ClientCertRequest`, request callback metadata,
native certificate confirmation and accepted authenticated read-only landing.
Status therefore remains `VERIFIED_CONTRACT / QA_ONLY` and public catalog remains
`E2E_PENDING / IMPLEMENTED_NOT_E2E`.

**New-chat starting point:** read
`docs/handoffs/F03_NEXT_CHAT_2026-08-01.md` first. Continue the physical F-03 test
from the exact click only; do not redo design/implementation/full gates or APK
installation unless source/build state changed. Prefer rish-only foreground
control and do not launch Android Control Bridge UI while the portal is active.

Latest sanitized evidence:
`docs/e2e/2026-08-01-aeat-client-tls-partial.md`.


## Autonomous audit G1-01 — QA WebView debugging boundary — 2026-08-04

- acceptance QA previously inherited `BuildConfig.DEBUG=true` and therefore
  enabled application-wide WebView remote debugging;
- explicit `ENABLE_WEBVIEW_CONTENTS_DEBUGGING` now permits DevTools only in the
  ordinary developer Debug variant; QA and Release are pinned false;
- QA remains debuggable for existing controlled diagnostics; portal/profile, TLS,
  origin, certificate and signing policy are unchanged;
- fresh automated gates: Debug 509/509, QA 509/509, lint/build/APK artifact,
  Python 95 (one environmental hardlink skip), Go test/vet/build and release
  fail-closed PASS;
- no APK installation, app launch, physical test or portal interaction occurred
  for this autonomous milestone.

Next autonomous audit line: classify the suppressed exposed network transport
type boundary, then continue QA-only catalog/release consistency and certificate/
storage/logging/signing trust-boundary review. Physical AEAT F-03 continuation
remains outside the autonomous safety boundary.


## Autonomous audit G1-02 — network failure-detail visibility — 2026-08-04

- removed the Kotlin `EXPOSED_*` suppressions by internalizing only route failure
  detail/primary construction; public failure code construction remains unchanged;
- rejected the data-class/internal-constructor alternative because Kotlin 2.3
  warns about generated `copy()` visibility;
- no network retry/fallback/TLS/DNS/tunnel/signing behavior changed;
- fresh gates: Debug 509/509, QA 509/509, lint/build/APK artifact, Python 96
  (one environmental hardlink skip), Go test/vet/build and release fail-closed PASS;
- no APK installation, launch, device control or portal/certificate operation.

Read-only next-line reconciliation found all 8 configured site profiles bound to
exactly 8 public-catalog entries. `reg-age-redsara` and
`aeat-mis-datos-censales` remain `QA_ONLY` with catalog
`E2E_PENDING / IMPLEMENTED_NOT_E2E`; verified profiles remain E2E-verified. Continue
by proving release-registry behavior for all sensitive non-E2E profiles, then audit
certificate/storage/logging/signing trust boundaries.


## Autonomous audit G2-01 — release-registry invariant evidence — 2026-08-04

- production `SiteProfileRegistry` already enforces the general sensitive-profile
  release rule; no runtime/profile/catalog mutation was made;
- the old downgrade regression test was a false-positive proof because it downgraded
  `unizar-tramitador` but asserted the unrelated already-ineligible
  `junta-andalucia`;
- the replacement is name/order independent: all sensitive non-E2E built-in profiles
  must be release-absent, and every current enabled E2E sensitive profile is
  independently downgraded and must disappear from release while remaining in QA;
- complete same-production-tree gates passed: Debug 509/509, QA 509/509, lint/build,
  artifacts, release fail-closed, Python 96 with one environmental hardlink skip, Go
  test/vet/build; final strengthened invariant passed focused Debug+QA and the
  complete final JVM rerun passed Debug 509/509 and QA 509/509;
- no APK install/launch, device control, portal interaction, certificate/credential
  use, real signing, upload, payment, or submission occurred.

Next autonomous audit line: continue certificate/storage/logging/signing trust
boundaries. Inspect the mismatch between `SanitizedLogger.clear()` (memory only) and
the QA file journal lifecycle, but treat it as a defect only if a concrete clear
contract is established; also inspect temporary signature/certificate byte-copy
lifetimes. Physical AEAT F-03 remains outside this autonomous task safety boundary.


## Autonomous audit G2-02 — QA diagnostic journal clear boundary — 2026-08-04

- `docs/test-plan.md` requires logger clear to eliminate the journal; QA persisted a
  second sanitized journal in `filesDir/qa-navigation.log` that previously survived
  `SanitizedLogger.clear()` during the same process.
- TDD RED was observed at the persisted-file assertion while the in-memory export was
  already empty; no current production caller of `sanitizedLogger.clear()` was found,
  so the defect was dormant rather than evidence of an observed user leak.
- `SanitizedLogSink` now exposes a default no-op `clear()` without losing SAM/lambda
  compatibility. The logger delegates clear best-effort, the QA file sink truncates
  its app-private journal, and the QA composite propagates clear. Release/non-QA
  persistence is unchanged; system Logcat erasure is not claimed.
- Focused Debug+QA GREEN passed. Fresh full gates passed: 510/510 Debug, 510/510 QA,
  lint 0 errors / 27 warnings per variant, Debug/QA/QA-AndroidTest builds, Android
  artifact verification, release fail-closed, Python 96 with one environmental
  hardlink skip, and Go test/vet/build.
- APK SHA-256: Debug
  `079506fc28ee108c37b2a5bb929bfe5214dda767284fe8c9dac04e8e811adbec`, QA
  `c253e07b0cb94321e31769dc96dc1fd7f142f8a907884ecc7617254d0cb53e85`, QA
  AndroidTest
  `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.
- The containing commit is the G2-02 milestone commit; obtain its exact SHA with
  `git rev-parse HEAD` after checkout/push verification.

Next autonomous line: inspect temporary final-signature/certificate byte-copy
lifetimes in CAdES/XAdES verification and related signing paths. Do not promote a
copy to a defect unless its retention exceeds the required verification/output
lifetime or crosses a persistence/logging boundary. Physical AEAT F-03 and Go race
remain external/manual gates outside this autonomous Termux-only pass.


## Autonomous audit G3-01 — CAdES capture-buffer zeroization — 2026-08-04

- Reproduced a real managed-heap retention defect: `ByteArrayOutputStream.toByteArray()`
  returned a copy, so the old `fill(0) + reset()` left the capturer's owned backing
  buffer unchanged; standalone JVM canary probe returned `retained=true`.
- Source-policy TDD RED rejected the old clearing pattern. A first `close()`-override
  implementation was also rejected after focused CAdES tests failed: BouncyCastle
  closes the supplied stream before `signedBytes()` is consumed.
- Final implementation preserves inherited stream close behavior and uses an explicit
  `ClearingByteArrayOutputStream.clear()` that zeros protected `buf` then resets it;
  `CapturingContentSigner.close()` invokes this explicit clear.
- Final gates PASS: pins; Debug 510/510; QA 510/510; Debug/QA/QA-AndroidTest builds;
  lint 0 errors / 27 warnings per variant; Android artifact verification; release
  fail-closed; Python 97 with one environmental hardlink skip; Go test/vet/build.
- APK SHA-256: Debug
  `f8d819a0de57e40ad7e1575a2c44ff8577d9b70a55ff5b53942a2fd3d2f1227e`, QA
  `96331ee7bddd782981a5b4900e906e27887ddc0dfd28698e62c17c38cbdb7f1b`, QA
  AndroidTest `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.
- No device/app/portal/credential/certificate/real-signing/upload/payment/submission
  action occurred. Go race remains an external supported-Linux CI gate.
- The containing commit is the G3-01 milestone commit; obtain its exact SHA with
  `git rev-parse HEAD` after checkout/push verification.

Next autonomous line: continue XAdES/final-signature/certificate temporary-copy
lifetime review without widening public APIs solely for test visibility. If no
reproducible excess-lifetime/persistence defect is found, record no-defect evidence
and move to a fresh architecture/lifecycle or UX/accessibility audit pass. Physical
AEAT F-03 remains a manual acceptance gate outside this task's autonomous boundary.

## Autonomous audit G4-01 — XAdES byte-stream zeroization — 2026-08-04

- A standalone JVM canary probe reproduced redundant XAdES heap retention from
  ordinary `ByteArrayOutputStream.toByteArray()` plus no-op `close()`: both the
  returned copy and the stream backing buffer retained the XML canary.
- Source-policy TDD RED was observed before the production state changed. The final
  XAdES implementation uses a private clearing stream in `serialize()` and
  `canonicalize()` and clears protected `buf` in `finally` after obtaining the
  intentional result copy; inherited close behavior is preserved.
- A guarded patch command later found the exact planned source diff already present
  before its own write step. No active mutator was found, the file hash was stable,
  and no unrelated source diff was present; exact write origin remains unclassified.
- Fresh gates PASS: forced focused XAdES Debug+QA; full Debug 510/510 and QA 510/510;
  pins; Debug/QA/QA-AndroidTest builds; lint 0 errors / 27 warnings per variant;
  Android artifact verification; release fail-closed; Python 98 with one
  environmental hardlink skip; Go test/vet/build. Generated relay binary removed.
- APK SHA-256: Debug
  `6a6b6e72006048ea9191de2b4b509cda21bb9f60b226386afa54ea872e753139`, QA
  `20740737b0e977e263192367de217f8f03262f59e4ba972e2a233da08b5e8810`, QA
  AndroidTest `6e41e3c8c41775194681a3a7b41f999422cb82b48b59ff3aa19c3923c6db252b`.
- No APK install/launch, device control, portal interaction, credential/certificate
  use, real signing, upload, payment or submission occurred. Go race remains an
  external supported-Linux CI gate.

Next autonomous line: after commit/push verification of G4-01, start a fresh
architecture/lifecycle/concurrency/recovery audit. Do not modify a new behavior line
without a separate subordinate design/plan and TDD RED. Physical AEAT F-03 remains a
manual acceptance gate outside this task's autonomous safety boundary.


## Autonomous audit G4-02 — persisted certificate-unlock threat-model reconciliation — 2026-08-04

- Runtime recovery semantics were confirmed intentional, not repaired: valid encrypted
  unlock state can restore the identity after process recreation or memory pressure
  before the original 24-hour expiry. Password persistence is limited to
  AES-256-GCM ciphertext in `noBackupFilesDir`; the AES key is in Android Keystore;
  PKCS#12 bytes/private-key objects are not persisted by this feature.
- The authoritative T5 threat-model text was stale and incorrectly implied process
  death guaranteed persistent locking. Asset/trust-boundary/T5 text now documents
  bounded recovery, clearing conditions, non-extension and residual risk. No runtime,
  profile, signing, network/WebView, build or dependency behavior changed.
- Policy TDD RED/GREEN is recorded. Fresh verification: Python 99 tests with one
  environmental hardlink skip; exact Debug+QA lifecycle focus PASS,
  `BUILD SUCCESSFUL`, 60/60 tasks executed. Two mis-scoped parallel Gradle retry jobs
  failed during broad test-class execution and were discarded as operator-command
  artifacts; the corrected task-scoped rerun passed.
- No APK install/launch, device control, portal interaction, credential/certificate
  use, real signing, upload, payment or submission occurred.

Next autonomous line: commit/push this documentation-only reconciliation, then start
a fresh architecture/lifecycle/concurrency/recovery audit. Prioritize browser
post-dispose callbacks, stale asynchronous completions and lifecycle ownership. Any
runtime change requires a new subordinate design/plan and TDD RED. Physical AEAT
F-03 and Go race remain external/manual gates.

## Autonomous audit G6-01 — public inventory deadline cleanup — 2026-08-04

- Fresh Python discovery initially ran 99 tests with one failure and one
  environmental hardlink skip. The failing blocking-read deadline test passed on
  five immediate reruns, but a deterministic expired post-start deadline probe
  proved that `_run_with_deadline()` returned before invoking its cleanup hook.
- TDD RED required cleanup after worker start even when deadline calculation
  itself raises. The helper now invokes one best-effort cleanup function on both
  post-start deadline-calculation failure and the existing live-worker timeout
  path; cleanup exceptions do not replace the stable deadline error.
- No deadline extension, retry, network/TLS/DNS/redirect/allowlist, portal,
  catalog, profile, Android, signing, certificate, dependency or release policy
  changed.
- Final Python evidence: complete `DeadlineTest` 3/3; two deadline regressions
  repeated 10/10; complete discovery 100 tests with zero failures/errors and one
  environmental hardlink skip; `py_compile` and diff whitespace checks PASS.
- G5-01 stale WebView callback ownership work remains a separate preserved local
  milestone and must be committed/pushed only after its own evidence-document and
  staged review.

Next autonomous action: push the isolated G6-01 milestone, then finish the
preserved G5-01 WebView stale-callback lease evidence/commit/push. Physical AEAT
F-03 and Go race remain external gates.

## Autonomous audit G5-01 — stale WebView callback ownership lease — 2026-08-04

- Normal and Client TLS WebView clients lacked an active-instance lease, allowing a
  released/replaced view to attempt obsolete navigation, state, Afirma, error or
  renderer callbacks against current browser state. TDD RED proved the ownership
  dependency was absent before production mutation.
- `BrowserScreen` now supplies exact `webViewRef` identity predicates to both clients.
  Stale navigation is consumed; stale UI/native callbacks are suppressed; predicate
  exceptions fail closed.
- SSL and safe-browsing rejection remains unconditional. Stale Client TLS requests
  are ignored and the one-shot handler is abandoned, retaining process-scoped client
  certificate cleanup. No URL/TLS/certificate/signing/profile/release policy changed.
- Final gates PASS: focused Debug+QA regressions; full Debug 513/513 and QA 513/513;
  lint zero errors / 27 warnings per variant; Debug/QA/QA-AndroidTest builds;
  Android artifacts; release fail-closed; Python 100 with one environmental hardlink
  skip; Go test/vet/build; generated relay binary removed; exact-scope and security
  scans PASS.
- APK SHA-256: Debug
  `ee01227e286ab371a24d326a1a414f822e7e975b80892c6e2266ba866aaf3365`, QA
  `d4eb3e09b4430e3a6a0007064577943195a1d8c9bfa02335aa33ab0ec9820dae`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- G6-01 public-inventory deadline cleanup is already pushed at
  `3d00846e13e8b2874e9abe9b1f4b90d5d0862352`.
- No APK install/launch, device control, portal interaction, credential/certificate
  use, real signing, upload, payment or submission occurred.

After remote verification of the containing G5-01 commit, continue a new independent
architecture/lifecycle/concurrency audit. Physical AEAT F-03 and supported-Linux Go
race remain external gates.

## Autonomous audit G6-02 — browser data-clear completion ownership — 2026-08-04

- Reproduced a stale asynchronous completion defect: a confirmed global WebView-data
  clear could complete after profile disposal, update current result state and reload
  the obsolete profile URL on a replacement active WebView.
- TDD RED was observed first through the source-policy regression and a missing-helper
  behavioral compile failure. The final helper is an atomic one-shot lease bound to
  the initiating WebView; later requests supersede earlier requests and disposal
  invalidates pending ownership.
- Successful completion reloads only when the initiating WebView remains the exact
  active instance. Stale completion is ignored without cancelling or misreporting the
  process-wide deletion already in progress.
- Final gates PASS: focused Debug+QA; full Debug 517/517 and QA 517/517; pins and
  Debug/QA/QA-AndroidTest builds; lint zero errors / 27 warnings per variant; Android
  artifacts; release fail-closed; Python 100 with one environmental hardlink skip;
  Go test/vet/build; generated relay binary and release APK absent; exact-scope and
  security scans PASS.
- APK SHA-256: Debug
  `e02c14c9383b480a7ca9792136737e0e1b71932ae7b8bd517459d76eab43702f`, QA
  `e14387a60d88127762ba552d7b34dcd39384cc6f36757da21dae0488d13c2742`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No APK install/launch, device control, portal interaction, credential/certificate
  use, real signing, upload, payment or submission occurred.

After commit/push verification of the containing G6-02 milestone, continue a fresh
architecture/lifecycle/concurrency audit for other delayed completions and ownership
transfers. Any behavior change requires a separate subordinate design/plan and an
observed TDD RED. Physical AEAT F-03 and supported-Linux Go race remain external
acceptance gates.

## Autonomous audit G7-01 — bridge compatibility-error ownership — 2026-08-04

- Reproduced a stale asynchronous UI-delivery defect: a WebMessageBridge attachment
  failure queued by an old WebView could set compatibility state after that WebView
  was released and replaced.
- TDD RED was observed first at `BrowserSecurityRegressionTest.kt:280`. The minimum
  repair keeps the existing WebView-posted delivery but requires
  `webViewRef.get() === webView` before mutating compatibility state.
- Current active-WebView failures remain visible; stale callbacks from a released,
  destroyed or replaced instance are ignored. No bridge, origin, script, TLS,
  certificate, signing, profile, release or dependency policy changed.
- Final gates PASS: focused Debug and Debug+QA; full Debug 518/518 and QA 518/518;
  pins and Debug/QA/QA-AndroidTest builds; lint zero errors / 27 warnings per variant;
  Android artifacts; release fail-closed; Python 100 with one environmental hardlink
  skip; Go test/vet/build; generated relay binary and release APK absent; exact-scope
  and security scans PASS.
- APK SHA-256: Debug
  `6c97ea151ffe4bfc8c1a0b53ac6657f03760a880d78e62dbec2284da72f7edc2`, QA
  `875b38927595c7f4b153d79f33e09395825ffeee38c1829e2d0333bcc85c233a`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No APK install/launch, device control, portal interaction, credential/certificate
  use, real signing, upload, payment or submission occurred.

After remote verification of the containing G7-01 commit, continue a fresh
lifecycle/concurrency audit for other delayed UI deliveries and ownership transfers.
Any behavior change requires a separate subordinate design/plan and observed TDD RED.
Physical AEAT F-03 and supported-Linux Go race remain external acceptance gates.

## Autonomous audit G7-02 — certificate unlock invalidation linearization — 2026-08-05

- Reproduced cache resurrection after explicit clear: a blocked store could complete
  later, return success and recreate the encrypted unlock record.
- Reproduced premature session publication: while cache persistence was suspended,
  `CertificateSession.identityForSigning()` already exposed the new identity.
- Cache clear now advances an atomic invalidation generation before deleting storage;
  any pre-clear store that writes late detects the mismatch, removes the stale record
  and returns failure.
- ViewModel unlock now awaits cache store and checks cancellation before session
  publication; session and `Unlocked` UI commit have no suspension between them.
- Final gates PASS: Debug 520/520, QA 520/520, pins and Debug/QA/QA-AndroidTest builds,
  lint 0 errors / 27 warnings per variant, Python 100 with one environmental hardlink
  skip, Android artifacts, release fail-closed and Go test/vet/build. Release APK and
  relay binary are absent.
- APK SHA-256: Debug
  `b2d414f4a74eb3f42dbf4cb6c63a4403e82a3e199b5b4fcd2d3c111a62345547`, QA
  `833081836caf0feb5060f9daee90ce4a0ee00646fb136006c8181aba1d1a376e`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No device/app/portal/credential/certificate/real-signing/upload/payment/submission
  action occurred. Physical AEAT F-03 and supported-Linux Go race remain external
  gates.

After remote verification of the containing G7-02 commit, continue a fresh independent
architecture/lifecycle/concurrency audit. Do not repeat G7-02. Any new behavior change
requires its own subordinate design/plan and observed TDD RED.

## Autonomous audit G8-01 — cancelled certificate-selection permission cleanup — 2026-08-05

- Reproduced a least-privilege defect: after persistable SAF permission was acquired, cancelling
  selection while reference `write()` was suspended before commit left the app permission
  retained even though no selected reference existed.
- The write cancellation branch now releases only a newly acquired URI that differs from the
  previous persisted reference, then rethrows the original `CancellationException`.
- Same-URI permission ownership, successful replacement ordering and ordinary storage-failure
  rollback are unchanged; no certificate/password/signing/WebView/network/profile/release scope
  changed.
- Final gates PASS: Debug 521/521, QA 521/521, pins and Debug/QA/QA-AndroidTest builds, lint
  0 errors / 27 warnings per variant, Python 100 with one environmental hardlink skip, Android
  artifacts, release fail-closed and Go test/vet/build. Release APK and relay binary are absent.
- APK SHA-256: Debug
  `6ceca12ed1254d6627c89406875bb57669c2ac64ae8b4852b4352cda7ed673d7`, QA
  `0e4789a79f4d0d4849825605f768dc677a1e7d844bdce449d6e952ed5d2b9096`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No APK/device/portal/credential/certificate/real-signing/upload/payment/submission action
  occurred. Physical AEAT F-03 and supported-Linux Go race remain external gates.

After remote verification of the containing G8-01 commit, continue a fresh independent
lifecycle/concurrency audit. Do not repeat G8-01; any new behavior change requires a separate
subordinate design/plan and observed RED.

## Autonomous audit G8-02 — cancelled unlock stale-reference summary write — 2026-08-05

- Reproduced a repository cancellation-ordering defect: an unlock cancelled while blocked in
  PKCS#12/document loading could return from that blocking work and initiate an old-reference
  summary write before coroutine cancellation was re-observed.
- RED `job_20260805_115511_14d81020` failed exactly because the non-suspending recording store
  received a write after cancellation; tests=1, failures=1, errors=0.
- `CertificateRepository.unlock()` now checks `currentCoroutineContext().ensureActive()` after
  blocking loading and before successful reference-summary persistence. Original cancellation and
  all non-cancelled certificate semantics are preserved.
- GREEN `job_20260805_120103_a7e93b2b` and complete repository Debug+QA
  `job_20260805_120546_d5ea1fd2` PASS. Full Android `job_20260805_121301_ef67a622` PASS:
  Debug 522/522, QA 522/522, zero failures/errors/skips, pins and all three assemblies.
- Forced lint `job_20260805_123048_ba6c0459` PASS: 55/55 tasks, 0 errors / 27 warnings per
  variant. Python `job_20260805_123629_7317943c` PASS: 100 tests, one environmental hardlink
  skip. Artifact `job_20260805_123802_8de46e94`, release fail-closed
  `job_20260805_124233_27c083ee`, and Go `job_20260805_123929_9870b5cf` all PASS. Release APK
  count zero; generated relay binary absent.
- APK SHA-256: Debug
  `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`, QA
  `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Final source/test whitespace, scope, secret, personal/certificate-literal and unsafe
  WebView/TLS/backup scans PASS. Threat-model wording remains unchanged.
- No APK/device/portal/credential/certificate/real-signing/upload/payment/submission action
  occurred. Physical AEAT F-03 and supported-Linux Go race remain external gates.

After remote verification of the containing G8-02 commit, continue a fresh independent
architecture/lifecycle/concurrency or UX/CI audit. Do not repeat G7-02, G8-01 or G8-02. Any new
behavior change requires a separate subordinate design/plan and observed TDD RED.

## Autonomous audit G9-01 — autonomous branch CI push coverage — 2026-08-05

- Both workflows previously excluded mandatory `agent/**` pushes while the autonomous contract
  requires every completed milestone on `agent/workspace-47-autonomous-20260803` to be pushed.
- TDD RED `job_20260805_132116_bf00a316` failed against unchanged workflows because `agent/**`
  was absent. The minimum change adds only that branch glob to `ci.yml` and `security.yml`, with a
  policy regression requiring `main`, `feature/**` and `agent/**` in both.
- GREEN/policy/Python `job_20260805_132135_fe5674af` PASS: `CiPolicyTest` 19/19, Python 101 with
  one environmental hardlink skip. Full Android `job_20260805_132209_da78308f` PASS: Debug
  522/522, QA 522/522, zero failures/errors/skips, pins and all three assemblies, 127/127 tasks.
- Lint `job_20260805_133103_ecb4c60e` PASS: 55/55 tasks, 0 errors / 27 warnings per variant.
  Artifact `job_20260805_133111_c4ee32c9`, Go `job_20260805_132223_437dc850`, and release
  fail-closed `job_20260805_133814_d06bfb4e` PASS; release APK count zero and relay binary absent.
- Permissions remain read-only; checkout credentials remain disabled; jobs, schedules, commands,
  immutable action pins, dependency versions and release policy are unchanged. Threat-model text
  is unchanged because this is CI trigger coverage, not an application trust-boundary change.
- APK SHA-256: Debug
  `5f7ccda5ed3aafc1800f8ec2e6190ff263f5c07d3abb01f67ced74104c863fe5`, QA
  `f89f4f5a8009ced7cb5eb97777d7a6e6ac99a4416908e45dd3fb303328d46146`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Local evidence does not claim a GitHub-hosted workflow run. No APK/device/portal/credential/
  certificate/real-signing/upload/payment/submission action occurred. Physical AEAT F-03 and the
  supported-Linux Go race gate remain external.

After remote verification of the containing G9-01 commit, continue a fresh independent
UX/accessibility, lifecycle/concurrency or CI/supply-chain audit. Do not repeat G9-01. Any new
behavior change requires a separate subordinate design/plan and observed TDD RED.
## Autonomous audit G10-01 — browser notice assertive live region — 2026-08-05

- `BrowserNoticeBanner` lacked a live-region property, so a newly appearing blocking portal/network
  error had no Compose instruction for immediate assistive-technology announcement.
- RED `job_20260805_191209_05c712cb` failed exactly because the tagged node lacked
  `LiveRegion = 'Assertive'`. The minimum change adds only that semantics property to the existing
  banner container; it does not move focus or alter text, layout, retry, WebView or security logic.
- Exact GREEN `job_20260805_191610_b601f0fe` PASS. Focused Debug+QA
  `job_20260805_191930_64b43357` PASS: 2/2 tests per variant, 60/60 tasks.
- Full Android `job_20260805_192433_0a9882e0` PASS: Debug 523/523, QA 523/523, zero
  failures/errors/skips, pins and all three assemblies, 127/127 tasks. Lint
  `job_20260805_193250_fbcb35e0` PASS: 55/55 tasks, 0 errors / 27 warnings per variant.
- Python `job_20260805_192440_7b0b9c8e`, Go `job_20260805_192506_91c193e3`, artifacts
  `job_20260805_193317_e2ccbe58` and release fail-closed `job_20260805_193923_b5015fe3` PASS;
  release APK count zero and relay binary absent.
- APK SHA-256: Debug `340114fc16b6603bb972d9f409fa4f0d3b4aa1a0eeb8ec0a177ffbea530788f9`, QA
  `d951d33a6f616242348a16a3ff3ae9017165a480253cffd8848a8e4bd4cc8061`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Robolectric proves semantics, not physical TalkBack timing or visual correctness; those remain
  manual gates. No APK/device/portal/credential/certificate/real-signing/upload/payment/submission
  action occurred. Threat model is unchanged.

After remote verification of the containing G10-01 commit, continue a fresh independent
lifecycle/concurrency, accessibility or supply-chain audit. Do not repeat G10-01. Any new behavior
change requires a separate subordinate design/plan and observed TDD RED.

## Autonomous audit G11-01 — WebMessage bridge release ownership — 2026-08-05

- Reproduced a lifecycle ownership defect: normal `AndroidView.onRelease` destroyed a
  WebView without closing its `WebMessageBridgeAttachment`; later recreation could
  overwrite the raw reference while stale listener/script/pending-reply state remained.
- Added a pure atomic exact-owner `BrowserOwnedResourceLease`. Replacement closes the
  superseded resource; stale-owner release cannot close the current resource; exact
  release and full close are one-shot. `BrowserScreen` binds each attachment to its
  exact WebView and releases it before WebView destruction.
- Renderer-death, Client TLS, navigation and full-disposal cleanup now use the same
  current bridge lease. WebMessage payload/script, origin, TLS, certificate, signing,
  profile/catalog, release and dependency policy are unchanged.
- RED evidence: missing-lease compile failure
  `job_20260805_195612_9b1c5899`; integration suite 15 tests / one exact ownership
  failure read in `job_20260805_201142_349e55bb`.
- GREEN/final gates PASS: focused Debug+QA 16/16 per variant; full Debug 525/525 and QA
  525/525; pins/locks and three assemblies; lint 0 errors / 27 warnings per variant;
  Python 101 with one environmental hardlink skip; Go test/vet/build; Android
  artifacts; release fail-closed. Release APK count is zero and relay binary is absent.
- APK SHA-256: Debug
  `6bf8e4722fe865b1137a7a4498bc824b83e4413ca9b9dd4c8c8e64414703e195`, QA
  `3a263176016595ec449bbaab3ee352c7a674bf79c48f5d9f0e954efa06aa8f37`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Exact-scope, whitespace, sensitive-content and unsafe WebView/TLS scans PASS. No APK/
  device/portal/credential/certificate/real-signing/upload/payment/submission action
  occurred. Threat model is unchanged.

After remote verification of the containing G11-01 commit, continue a fresh independent
lifecycle/concurrency, accessibility or supply-chain audit. Do not repeat G11-01. Any
new behavior change requires a separate subordinate design/plan and observed TDD RED.
Physical AEAT F-03, physical TalkBack/visual validation and supported-Linux Go race
remain external gates.

## Autonomous audit G12-01 — stale WebView network-diagnostic ownership — 2026-08-05

- Reproduced a lifecycle/logging provenance defect: a released/replaced normal WebView
  could still invoke `shouldInterceptRequest()` and append sanitized main-frame
  `NETWORK_REQUEST` metadata after its other browser callbacks were ownership-gated.
- RED `job_20260805_205546_7e6ca54a` failed the single new test exactly on the stale
  log record; XML read `job_20260805_205837_885abec9` confirmed 1 failure / 0 errors /
  0 skips. Production was unchanged at RED.
- Minimum fix adds only an exact active-WebView guard before request diagnostic logging;
  return value stays `null`. Active logging, subframes, navigation, SSL/Safe Browsing,
  DNS/TLS/Client TLS, certificate, signing, profile/catalog, release and dependencies
  are unchanged.
- Exact GREEN `job_20260805_205906_3890a3e5` PASS. Focused Debug+QA
  `job_20260805_210208_77f0117c` PASS: `JuntaWebViewClientTest` 18/18 per variant,
  60/60 tasks.
- Full Android `job_20260805_210652_14457a72` PASS: pins/locks, all three assemblies,
  128/128 tasks; XML aggregation `job_20260805_211450_91c4e83d`: Debug 526/526 and QA
  526/526, zero failures/errors/skips. Lint `job_20260805_211457_1604b9df` PASS 55/55;
  zero errors / unchanged 27 warnings per variant confirmed by
  `job_20260805_212114_570c9a57`.
- Python/Go `job_20260805_210659_0143787d`, Android artifacts
  `job_20260805_211505_15af8337` and release fail-closed
  `job_20260805_212127_371468b7` PASS. Python: 101 tests with one environmental
  hardlink skip; Go test/vet/build PASS.
- Cleanup whitelist assertion `job_20260805_212254_962eeacb` stopped before mutation
  because it omitted the just-built untracked relay executable. Diagnostic
  `job_20260805_212313_afba868a` confirmed it was the standard `go build` ARM64 ELF;
  corrected cleanup `job_20260805_212329_ecd65e0a` removed it and confirmed zero
  release APKs. This was not a source/test failure.
- Exact pre-evidence scope/whitespace/sensitive/unsafe-pattern scan
  `job_20260805_212358_bb60a813` PASS.
- APK SHA-256: Debug
  `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`, QA
  `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Threat-model wording is unchanged: existing diagnostics/output and WebView lifecycle
  boundaries cover this remediation, with no new trust edge. No APK/device/portal/
  credential/certificate/real-signing/upload/payment/submission action occurred.

After remote verification of the containing G12-01 commit, continue a fresh independent
certificate/signing/storage, accessibility or CI/supply-chain audit. Do not repeat
G12-01. Any new behavior change requires a separate subordinate design/plan and
observed TDD RED. Physical AEAT F-03, physical TalkBack/visual validation and
supported-Linux Go race remain external gates.

## Autonomous audit G12-02 — Python Dependabot coverage — 2026-08-05

- Found supply-chain control asymmetry: `tools/requirements.txt` was already scanned
  by OSV but `.github/dependabot.yml` had no Python/pip update-monitoring entry.
- RED `job_20260805_213934_d8a7096a` failed exactly on zero pip ecosystem entries.
  Minimum fix adds one weekly Monday `pip` entry scoped to `/tools`, PR limit 5; no
  dependency/tool/action version changed.
- Exact GREEN plus complete CI policy module `job_20260805_213957_df23d7c9`: 19/19
  policy tests PASS.
- Full Android `job_20260805_214008_05cba7fd`: pins/locks, all three assemblies,
  128/128 tasks PASS; `job_20260805_214720_ad44529f`: Debug 526/526 and QA 526/526,
  zero failures/errors/skips, requirements hash unchanged.
- Python/Go `job_20260805_214014_3f1f0af3`: Python 101 PASS with one environmental
  hardlink skip; Go test/vet/build PASS. Lint `job_20260805_214728_d0d3ec61` PASS
  55/55; count `job_20260805_215335_c01cf437`: 0 errors / unchanged 27 warnings per
  variant. Artifacts `job_20260805_214736_54d890ad` and release fail-closed
  `job_20260805_215344_817e8e2b` PASS.
- Cleanup `job_20260805_215505_a917c8de`: generated relay binary removed, release APK
  count zero. Pre-evidence scan `job_20260805_215529_fe814d2d`: exact 4-file scope,
  YAML shape, whitespace, sensitive scan, unchanged requirements/locks/verification
  metadata/workflows/runtime and SHA/permission policy PASS.
- APK hashes unchanged from G12-01: Debug
  `3beacea548b78ce09d110820212603ed538e5dc2072c8f218a6ec01658bf2b3f`, QA
  `cb34cce2fc515a6a20d7cab68eed742d9d5d0fe023912d9b8371175fcf78e546`, QA AndroidTest
  `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- No hosted Dependabot execution is claimed. No runtime/threat-model change and no
  APK/device/portal/credential/certificate/real-signing/upload/payment/submission
  action occurred.

After remote verification of the containing G12-02 commit, continue a fresh independent
audit. Do not repeat G12-01/G12-02. Physical AEAT F-03, physical TalkBack/visual
validation and supported-Linux Go race remain external gates. Treat the existing
`ProfileHttpCallPhaseTracker` parameter-name warning only as low-risk cleanup unless a
separate reproducible transport/API defect is found.

## Autonomous audit G13-01 — authoritative test-plan Dependabot reconciliation — 2026-08-06

- Reconciled one stale authoritative-doc statement left after G12-02: the actual
  Dependabot v2 config and policy test already cover weekly `pip` updates at `/tools`,
  but `docs/test-plan.md` still listed only Gradle, Go modules and GitHub Actions.
- The test-plan now names the existing `/tools` `pip` coverage. No dependency/tool/
  Action version, workflow, manifest, lockfile, verification metadata, runtime,
  profile/catalog or release behavior changed; no TDD RED applies to this docs-only
  correction.
- `python -m unittest tools.tests.test_ci_policy -v`: 19/19 PASS. Independent YAML
  and documentation assertions confirmed exactly one weekly `pip` entry at `/tools`
  and the matching plan text. A first `python -m pytest` attempt failed before
  collection because system Termux Python has no pytest; no package was installed,
  and the repository's documented unittest runner passed.
- Fresh G13 read-only audits found no justified change in Client TLS cleanup ownership,
  signing job/expiry ownership or certificate ViewModel cancellation: current exact-
  owner/generation/terminal controls cover the inspected paths. A privacy review of
  content-derived diagnostic short hashes remains a research lead because current
  test-plan/threat-model explicitly require truncated hashes; do not remove that
  observability contract without a narrow evidence-backed design and RED.

After remote verification of the containing G13-01 commit, continue an independent
architecture/lifecycle, accessibility, logging/privacy or supply-chain pass. Physical
AEAT F-03, physical TalkBack/visual validation and supported-Linux Go race remain
external gates.

## Autonomous audit G13-02 — browser notice live-region severity — 2026-08-06

- Reproduced an accessibility-semantics defect: the shared browser notice banner was
  always `Assertive`, including non-error Client TLS `CLEARING` and successful exact
  site/global data-clear status.
- RED `job_20260805_222337_0803500c` failed exactly on the two missing desired
  contracts before production mutation. The fix keeps the component default
  assertive, passes an explicit mode from a pure state policy, makes only progress and
  exact success polite, and preserves assertive failure/warning/error precedence.
- Focused GREEN `job_20260805_222533_f589b871` plus XML
  `job_20260805_222740_d7eee693`: 11/11 Debug and 11/11 QA, zero failures/errors/skips.
- Fresh split full gates PASS after two monolithic Termux calls lost HTTP-502 transport
  responses and were deliberately not counted as pass evidence: pins/locks
  `job_20260805_224216_debaec44`; full JVM `job_20260805_224421_3aee3897`; lint and
  three assemblies `job_20260805_224514_53d85d71`; counts/hashes
  `job_20260805_224616_c849f08f`; Python/Go `job_20260805_224626_7301ac9b`; Android
  artifacts `job_20260805_224646_40ff453a`; release fail-closed
  `job_20260805_224658_b2416ba2`.
- Full JVM is 528/528 per variant, lint 0 errors / 27 warnings per variant. APK SHA-256:
  Debug `cd499662a3fafc00f5b9370b5deaf604393611b0071b36487e47fba7aa13c2ae`, QA
  `c9732852c88117ab09b49f786bf2adc8f03c2144174534a7ee100ec6c84be098`, QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Generated relay binary was identified and removed; release APK count is zero.
  Strings/visuals and all browser/security/signing/profile/release behavior are
  otherwise unchanged; threat model unchanged. Physical TalkBack/visual validation,
  AEAT F-03 and supported-Linux Go race remain external gates.

After remote verification of the containing G13-02 commit, continue a fresh independent
logging/privacy, lifecycle/concurrency, accessibility or supply-chain audit. Do not
repeat G13-01/G13-02.


## Autonomous audit G14-01 — security-roadmap Dependabot reconciliation — 2026-08-06

- Reconciled two stale summary bullets in `docs/security-roadmap.md`: the verified
  Dependabot coverage includes the existing weekly `pip` entry scoped to `/tools` in
  addition to Gradle, Go modules and GitHub Actions.
- No workflow, dependency/tool version, manifest, lockfile, verification metadata,
  runtime, profile/catalog or release behavior changed; this is documentation-only.
- Verification `job_20260805_225840_0dc0db98`: CI-policy 19/19 PASS, Dependabot
  YAML↔roadmap consistency PASS, `git diff --check` PASS, sensitive-pattern scan PASS.
- Continue with a fresh runtime logging/privacy, lifecycle/concurrency, accessibility
  or supply-chain audit after remote verification. Do not repeat G14-01.


## Autonomous audit G14-02 — Client TLS issuer-filter hardening — 2026-08-06

- Reproduced a Client TLS CA-filter expansion: non-empty Android client-certificate
  principals are issuer constraints, but the handler also accepted certificate subjects.
  A two-certificate test fixture with distinct leaf subject/issuer failed RED in both
  Debug and QA before production mutation.
- Minimum fix retains DER constant-time comparison but matches only
  `issuerX500Principal`; all other Client TLS/profile/release boundaries are unchanged.
- Focused GREEN: 7/7 per variant; adjacent regression 55/55 per variant; fresh isolated
  full JVM 529/529 per variant; lint 0 errors / 27 warnings per variant; dependency,
  Debug/QA/QA-AndroidTest build, Python, Go, artifact and release-fail-closed gates PASS.
- Infrastructure note: one overlapping full-JVM connector retry produced broad Gradle
  test-executor class-execution failures, and two duplicate lint retries timed out. None
  are counted as pass evidence; isolated durable reruns supplied the successful evidence.
- APK SHA-256: Debug `a31bb8cdfdb05af38a26c3ec32bddf5415e6991d00453553e61f54bb01f32fa9`;
  QA `53dd0a15d69fc59a0fa70dde0032005ddf2f6425c9758d745e31d60b8e71f6e9`; QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Release APK count remains zero and the generated relay binary was removed. Physical
  AEAT F-03, real-device TalkBack/visual behavior and supported-Linux Go race remain
  external/manual gates.


## Autonomous audit G14-03 — persisted unlock stale-restore invalidation — 2026-08-06

- Reproduced a cache-level concurrency defect: after `read()` owned an encrypted snapshot,
  a concurrent `clear()` could complete yet that stale restore still returned a
  password-backed unlock. RED failed exactly in Debug and QA before production mutation.
- Minimum fix binds restore to `invalidationGeneration`, rejects a stale snapshot after
  read and checks again after password decode, zeroing the decoded password on mismatch.
  Record format, Keystore/AES-GCM policy and ViewModel/session behavior are unchanged.
- Focused GREEN 9/9 per variant; adjacent certificate/session/ViewModel 45/45 per variant;
  fresh full JVM 530/530 per variant; lint 0 errors / 27 warnings per variant; dependency,
  build, Python, Go, artifact and release-fail-closed gates PASS.
- APK SHA-256: Debug `b771e02dacc454a0f83c0e6049d73de09e0a231dd318a48469b5a2a8545e7daf`;
  QA `c717d9c212566c372331a68365c9b75006af92f2f3f503c37d3a66651896e660`; QA
  AndroidTest `5ee3e2350e958293e0e822d55042c4182630bb51efd748d3d8b336d3c26dc81a`.
- Generated relay binary is removed and release APK count is zero. Physical AEAT F-03,
  real-device TalkBack/visual validation and supported-Linux Go race remain external/manual
  gates.

## Autonomous audit G14-04 — complete Android backup/D2D domain exclusion — 2026-08-06

- Reproduced an app backup/D2D policy gap: both explicit resources excluded only
  `root`, although Android backup domains are independent and Android 12+ D2D can ignore
  `allowBackup=false`. The persisted unlock ciphertext itself remains under
  `noBackupFilesDir`; the finding protects app-domain metadata/files and future storage.
- RED `job_20260806_002638_74b167aa` failed exactly on the eight missing domains before
  either resource changed. Minimum fix adds `path="."` exclusions for exactly all nine
  supported app domains in legacy backup and independently in Android 12+ cloud backup
  and device transfer; no include rules or runtime storage changes.
- Focused GREEN/CiPolicy 20/20; full JVM 530/530 per variant; lint 0 errors / 27 warnings
  per variant; Debug/QA/QA-AndroidTest builds, dependency/toolchain, Python 101 with one
  environmental hardlink skip, Go test/vet/build, Android artifact and release
  fail-closed gates PASS. Release APK count is zero.
- Generated relay binary from the Go build was removed before final staging. Physical
  AEAT F-03, real-device TalkBack/visual validation and supported-Linux Go race remain
  external/manual gates.

After remote verification of the containing G14-04 commit, continue a fresh independent
certificate/signing/storage/logging, lifecycle/concurrency, accessibility or supply-chain
audit. Do not repeat G14-01 through G14-04. Any new behavior change requires a narrow
subordinate design/plan and observed TDD RED.
