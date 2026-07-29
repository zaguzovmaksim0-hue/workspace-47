# Handoff для следующего чата — Junta Firma

## Где продолжать

- приватный репозиторий: `zaguzovmaksim0-hue/workspace-47`;
- ветка: `feature/ws024-secure-tunnel-20260728`;
- всегда сначала выполнить `git fetch` и проверить HEAD ветки;
- подробная E2E-фиксация:
  `docs/e2e/2026-07-29-junta-ofvirtual-auth-success.md`.

## Подтверждённый результат

Профиль `junta-ofvirtual` прошёл реальный вход на физическом POCO F6 Pro. Портал
принял CAdES-аутентификацию и открыл внутреннюю страницу Oficina Virtual.
Проверенный объём — только вход с сертификатом:

- origin `https://ws072.juntadeandalucia.es`;
- endpoint MiniApplet 1.5 на `ws024`;
- `SHA1withRSA`, `CAdES`, detached/explicit;
- PRE → локальная подпись → POST → callback → form submit → portal accepted.

Не заявлять, что проверены все процедуры, отправка заявлений или документальная
подпись внутри кабинета.

## Исправленные причины

- `6538e1a`: безопасный upgrade точного legacy HTTP GET Oficina Virtual на HTTPS;
- `26230ab`: сворачивание больше не блокирует сертификат и не уничтожает WebView;
  in-memory unlock window — 2 часа;
- `b3f1817`: profile version 2, `VERIFIED_E2E / ENABLED`, каталог и UI показывают
  `VALIDADO CON EL PORTAL` / `Verificado: Firma electrónica`.

## Установленная сборка

- package: `dev.junta.firmamobile`;
- versionName: `0.1.0-qa`;
- установленный SHA-256:
  `ba82c501c4e1e4d9843dc263648d4b051ea2d9bbbbefd6f7ff451ab197b30e34`;
- direct-only: tunnel выключен, relay tuple отсутствует;
- выбранная ссылка на сертификат сохранена, пароль не сохраняется;
- после полной выгрузки процесса Android пароль потребуется снова.

## Последний полный gate

- Python: 75 tests, 1 skipped, остальное PASS;
- Debug unit: 431/431;
- QA unit: 431/431;
- lint Debug/QA: PASS;
- Debug/QA/QA-AndroidTest build: PASS.

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
