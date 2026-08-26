# QA E2E Intent control plane

Дата: 2026-08-26

`E2E_CONTROL` — debug/QA-only control plane для воспроизводимого device E2E без поиска элементов UI и координатных tap-сценариев. Release-сборка не содержит receiver/action/parser/secret inbox или app-private E2E certificate adapter.

## Ingress

Receiver регистрируется только пока `MainActivity` находится в foreground и требует platform permission `android.permission.DUMP`. Команды передаются ordered broadcast через action:

```text
dev.junta.firmamobile.action.E2E_CONTROL
```

Ответ — JSON schema v3 в ordered-broadcast result data. Он содержит только bounded state/result fields и sanitized portal diagnostics. Certificate/secret handles, URI сертификата, пароль, certificate bytes, private key, cookies и signing payload в JSON не экспортируются.

Операторский wrapper:

```text
scripts/android-e2e-control.sh
```

Он сам поднимает `MainActivity` explicit Intent перед lifecycle-bound командой. `PACKAGE_NAME` можно переопределить только для отдельной QA/debug установки.

## Закрытый набор команд

- `STATE` — состояние сертификатной/signing сессии;
- `CERT_SELECT` — выбрать сертификат через одноразовый opaque `certificateHandle`;
- `CERT_UNLOCK` — разблокировать через одноразовый `secretHandle`;
- `CERT_LOCK` / `CERT_FORGET`;
- `PORTAL_OPEN` / `PORTAL_INSPECT` / `PORTAL_CLOSE` — только exact catalog portal/profile ID;
- `CLIENT_AUTH_CONFIRM` / `CLIENT_AUTH_CANCEL` — подтверждение/отмена уже policy-authorized client-TLS transition;
- `PORTAL_CERT_CONFIRM` / `PORTAL_CERT_CANCEL` — подтверждение/отмена уже validated portal certificate-selection request;
- `SIGN_CONFIRM` / `SIGN_CANCEL` / `SIGN_DISMISS` — только для exact active portal/profile и того же `runId`.

Arbitrary URL, JavaScript, selector, network payload, certificate bytes и raw password extras отсутствуют.

## Сертификат

`cert-select` через Shizuku/`run-as` помещает PKCS#12 в app-private `no_backup/e2e-control/certificates/<random-handle>.p12` с mode `600`. Broadcast содержит только 32-hex `certificateHandle`; путь, исходное имя и bytes в Intent/JSON не попадают.

В debug/QA `BuildVariantCertificateDocumentAccessFactory` добавляет узкий `E2eStagedCertificateDocumentAccess`: он обслуживает только exact synthetic authority `dev.junta.firmamobile.e2e.certificate` + один bounded handle и делегирует все остальные URI обычному `ContentResolverCertificateDocumentAccess`. Production `CertificateRepository`, reference store и `Pkcs12Loader` не дублируются. Release factory использует только обычный `ContentResolverCertificateDocumentAccess` и не содержит E2E adapter. При `CERT_FORGET`/замене reference repository вызывает release permission hook, который удаляет exact staged private file.

Пример формы команды без реального пути:

```text
scripts/android-e2e-control.sh cert-select run-1 /path/to/fixture.p12
```

## Пароль

Raw password **никогда** не передаётся параметром CLI или `Intent` extra. Есть два входа:

```text
scripts/android-e2e-control.sh cert-unlock run-1 --password-file /path/to/mode-600-file
producer-without-logging | scripts/android-e2e-control.sh cert-unlock run-1 --password-stdin
```

`--password-stdin` отказывается читать интерактивный TTY. Wrapper через `run-as` пишет поток в app-private `cache/e2e-control/secrets/<random-handle>` с mode `600`. Broadcast содержит только random handle. `E2eSecretInbox`:

1. проверяет bounded handle;
2. запрещает symlink/non-regular file;
3. ограничивает вход 8192 bytes / 2048 chars;
4. строго декодирует UTF-8;
5. удаляет файл после единственной попытки чтения;
6. очищает временные byte/char buffers.

Далее вызывается существующий production `CertificateViewModel.unlock`, поэтому PKCS#12 validation, encrypted unlock cache и `CertificateSession` не дублируются.

## Signing boundary

`SIGN_CONFIRM` вызывает тот же `SigningCoordinator`/`BatchSigningCoordinator` path, что и UI confirmation. Перед вызовом controller повторно выполняет `PORTAL_INSPECT` для exact `runId` + portal/profile и требует `WEBVIEW_ACTIVE`. Это не общий «подпиши текущий запрос» endpoint.

Наличие команды не является разрешением на использование. Реальная private-key operation выполняется только когда это одновременно разрешено текущей authoritative execution policy и отдельным явным операторским разрешением для exact portal/action. Final filing/registration/submission/payment остаются отдельной границей и не разрешаются фактом наличия `SIGN_CONFIRM`.

## Fail-closed свойства

- lifecycle-bound receiver;
- sender должен иметь `android.permission.DUMP`;
- ordered broadcasts only;
- closed command enum и exact argument shape;
- portal/profile IDs вместо URL;
- browser/signing confirmation commands требуют exact run/profile и соответствующий runtime-required event;
- `CERT_SELECT` не принимает Intent data URI; только exact 32-hex app-private `certificateHandle`;
- staged PKCS#12 ограничен `Pkcs12Loader.MAX_INPUT_BYTES`, regular-file/no-symlink и exact app-private directory;
- password только one-shot app-private handle;
- serialized result не содержит certificate identity, URI, password, payload или private key;
- release factory no-op; action/parser/secret inbox отсутствуют в main/release sources.
