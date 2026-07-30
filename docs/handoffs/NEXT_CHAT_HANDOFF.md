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
  `VALIDADO CON EL PORTAL` / `Verificado: Firma electrónica`.

## Установленная сборка

- package: `dev.junta.firmamobile`;
- versionName: `0.1.0-qa`;
- установленный SHA-256:
  `880e72d7cd4e69bc61412ae3a75ed976a6857da0c56f37c031073136a1938a11`;
- локальный QA APK имеет тот же SHA-256;
- `zipalign`: PASS;
- APK Signature Scheme v2: PASS, one signer;
- direct-only: tunnel выключен, relay tuple отсутствует;
- зашифрованный 24-hour cache сертификата сохранён после обновления.

## Последний QA gate

- QA unit: 442/442, 0 failures/errors/skips;
- `lintQa`: PASS;
- `assembleQa`: PASS;
- QA APK SHA-256:
  `880e72d7cd4e69bc61412ae3a75ed976a6857da0c56f37c031073136a1938a11`.

Ранее полный Debug + QA gate также проходил с 442 тестами в каждой variant,
`lintDebug/lintQa`, `assembleDebug/assembleQa/assembleQaAndroidTest` без ошибок.

## Сетевой инцидент 30 июля 2026

Перед успешным повтором наблюдался `SIGNING_SERVICE_UNAVAILABLE`: DNS `ws024`
работал, но TCP/TLS завершался `ConnectException`/timeout до PRE. Такой же timeout
был воспроизведён из Termux. Позже точный endpoint вернул HTTP 200 при активном
WARP, и неизменённая сборка выполнила E2E успешно. Считать это временным внешним
сбоем маршрута/доступности; точная причина не доказана. Не добавлять обходы,
ослабление TLS или relay fallback без новой воспроизводимой evidence.

## Ограничения и следующие задачи

1. Вёрстка самого старого портала иногда показывает mojibake (`MenÃº`) и
   повреждённые иконки; это косметика страницы Junta, не сбой подписи.
2. Instrumentation test APK на HyperOS иногда блокируется политикой установки
   `testOnly`; не обходить системную защиту ослаблением приложения.
3. Release остаётся direct-only; QA relay не нужен для уже подтверждённого
   direct E2E и не должен включаться в release.
4. Следующие порталы (`reg-age-redsara`, `unizar-tramitador`) остаются
   `VERIFIED_CONTRACT / QA_ONLY`, пока портал реально не примет их E2E.
5. Не сохранять в Git скриншоты кабинета, пароль, PKCS#12, сертификат, подпись,
   cookie или персональные идентификаторы.

Для продолжения в новом чате достаточно написать: «Открой приватный репозиторий,
ветку `feature/ws024-secure-tunnel-20260728`, прочитай
`docs/handoffs/NEXT_CHAT_HANDOFF.md` и продолжай от текущего HEAD».
