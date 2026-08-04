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
