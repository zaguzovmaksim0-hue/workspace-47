# Полный прогон каталога через QA E2E-harness

Дата прогона: 2026-08-27 UTC. Команда:

```text
./scripts/android-site-smoke.sh --implemented --timeout 8 --settle 1
```

Проверены все 183 записи каталога. Harness принимает только идентификатор записи,
запускает реальный `MainActivity` и наблюдает санитизированные runtime-события.
URL, JavaScript, селекторы, cookies, POST-body, сертификаты и пароли в bridge не
передаются и в отчёт не записываются.

## Результат

| Метрика | Значение |
|---|---:|
| Обработано записей | 183 |
| Дошли до WebView | 180 |
| WebView активен без terminal boundary | 175 |
| Ручной выбор сертификата | 1 |
| Ручное подтверждение client-TLS | 2 |
| Безопасный внешний HTTPS handoff | 1 |
| Остановлены fail-closed allowlist-политикой | 4 |
| Timeout | 0 |
| Crash/ANR | 0 |
| Подтверждённое принятие клиентского сертификата | 0 |
| Подтверждённая криптографическая подпись | 0 |
| Подтверждённый callback портала | 0 |

Единственный `failed` в полном проходе был у `diputacion-lugo-sede`: процесс
исчез без сигнала crash/ANR. Повторный изолированный запуск того же profile
прошёл (`failed=0`, WebView активен), поэтому это классифицировано как единичный
сбой среды/ресурсов, а не подтверждённый дефект кандидата.

## Наблюденные ручные границы

- `age-instituto-de-salud-carlos-iii` — системный выбор/подтверждение сертификата;
- `extremadura-portal-tributario` — ручное подтверждение client-TLS;
- `diputacion-toledo-sede` — ручное подтверждение client-TLS;
- `justicia-sede-judicial`, `caib-seu-electronica`, `caib-registre-electronic`,
  `diputacion-malaga-sede` — переход на origin вне точного profile allowlist,
  остановлен fail-closed;
- `diputacion-girona-portal` — разрешённый внешний HTTPS handoff, без ошибки
  процесса приложения.

## Что это доказывает

Для всех записей подтверждены разрешение записи каталога, запуск реального
приложения, создание WebView и наблюдаемая граница навигации. Для sensitive-flow
записей дополнительно проверяется, что runtime не выдаёт сертификат или подпись
без точного контракта profile.

Это не переводит 179 записей в `VERIFIED_E2E`: реальный E2E требует физически
выбрать сертификат, подтвердить client-TLS/подпись, увидеть принятие операции
порталом и остановиться до несанкционированной финальной подачи. Эти действия
нельзя честно заменить smoke-интентами.

## Состояние каталога после проверки

- `183` записи присутствуют в сгенерированном каталоге;
- `179` остаются `IMPLEMENTED_NOT_E2E / E2E_PENDING`;
- `4` остаются `VERIFIED_E2E`;
- generated catalog совпадает с результатом генератора;
- RedSARA получил точный QA-only client-TLS contract для наблюдённого POST,
  но остаётся `E2E_PENDING` до успешной аутентифицированной сессии и принятия
  XAdES порталом.

## Проверки исходников

- Android QA unit: `1282/1282`, failures/errors/skips `0`;
- Python catalog/policy/coverage: `156/156`, `OK`;
- `bash -n scripts/android-site-smoke.sh`: `OK`;
- `git diff --check`: `OK`;
- `assembleQa`: `BUILD SUCCESSFUL`;
- локальный QA APK SHA-256:
  `623e487dea2b6a0c9c0d026f0346511a2347e7cec28cb8cbdd427e0da647ba42`.

Подробные сырые результаты smoke-runner хранятся локально в
`build/reports/site-smoke/raw/`; они санитизированы и не содержат секретов.
