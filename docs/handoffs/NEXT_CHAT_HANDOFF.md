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
- установленный SHA-256 (последняя физически подтверждённая F-08 сборка):
  `40f03d634b5053b0b79a217b88edf65ca32a3c57c5b36d0371ab968f9bc558b7`;
- текущий локальный F-17 QA APK имеет SHA-256
  `46788b0c65380aab91ff02bccde2d5f4dafe931320bf58fe4e7e645e5772c013`
  и не установлен из-за недоступного Shizuku/ADB;
- `zipalign`: PASS;
- APK Signature Scheme v2: PASS, one signer;
- direct-only: tunnel выключен, relay tuple отсутствует;
- зашифрованный 24-hour cache сертификата сохранён после обновления.

## Последний QA gate

- Debug unit: 473/473, 0 failures/errors/skips;
- QA unit: 473/473, 0 failures/errors/skips;
- `lintDebug`, `lintQa`: PASS;
- `assembleDebug`, `assembleQa`, `assembleQaAndroidTest`: PASS;
- Go `test ./... -count=1` and `go vet ./...`: PASS;
- Python catalog/tool tests: 75, 0 failures/errors, 1 environmental skip;
- Debug APK SHA-256:
  `c4ded880e4310d21e1818a5878424e091a9ef1863626069d9f3ebfd37d4afec6`;
- QA APK SHA-256:
  `46788b0c65380aab91ff02bccde2d5f4dafe931320bf58fe4e7e645e5772c013`;
- QA AndroidTest APK SHA-256:
  `7182651ac0926cf65f4bcf0a6cd067b819f5a512a92d1a2e54c20f8f21a21acf`;
- zipalign, v2 signature, one signer, manifest hardening and forbidden-canary
  scan: PASS;
- F-17 device classifier: `NOT_RUN_ENVIRONMENTAL`; Shizuku server unavailable,
  ADB empty, failure before install. Installed F-08 app remains unchanged.

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
  `WebViewCompat.setProfile` remains unused;
- next security block after F-17: remaining Client TLS grant lifecycle (F-03/F-13).

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
- physical classifier test compiled but did not run because Shizuku/rish was
  unavailable and ADB had no device; no APK installation occurred;
- repeat only `PublicIpAddressPolicyInstrumentedTest` after device transport is
  restored; do not interpret it as live portal IPv6 E2E.

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

Для продолжения в новом чате достаточно написать: «Открой приватный репозиторий,
ветку `feature/ws024-secure-tunnel-20260728`, прочитай
`docs/handoffs/NEXT_CHAT_HANDOFF.md` и продолжай от текущего HEAD».
