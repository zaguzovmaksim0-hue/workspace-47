# Параметризованные CAdES-detached / XAdES-enveloping: план, не реализация

Дата: 2026-10-24. Задача 4 из TASKS_workspace47_batch2.md.
База исследования: `07b8232d` (`origin/main` на момент создания worktree).
Ветка: `docs/parametrized-adapters-plan-20261024`.

## Цель и ограничения

Убрать повторяющуюся оркестрацию prepare/complete, не объединяя доверенные origins, портальные payload-контракты или XML/CMS-движки. Этот PR меняет только `docs/handoffs/`; ниже — предложения для отдельных будущих PR, а не разрешение менять портальные адаптеры сейчас. Нет нового runtime-кода, зависимостей, каталогов, baseline, протоколов или security-исключений.

Прочитаны весь пакет задач и общие правила, AGENTS.md, CONTEXT.md, CONTRIBUTING.md, разделы 3 и 6 PLAN_FIRMA_MOBILE.txt, политики `docs/agents/matt-pocock-workflow.md` и `github-actions-verification.md`. Применён маршрут ask-matt → codebase-design. `prepare_task` не найден среди доступных MCP-инструментов; использованы skill_search/skill_read. Не запускались субагенты, локальные Gradle/сборки/компиляция, E2E, instrumentation или эмулятор.

## Источники и полнота инвентаризации

Все сокращённые пути `signing/` ниже означают `app/src/main/java/dev/junta/firmamobile/signing/`; `tests/` — `app/src/test/java/dev/junta/firmamobile/signing/`. Имена классов в таблицах — одноимённые `.kt` в `signing/`, а символы `matchesContract`, `prepare`, `complete`, companion constants — места проверки фактов.

Проверены:

- [RuntimeSigningAdapters.kt](../../app/src/main/java/dev/junta/firmamobile/signing/RuntimeSigningAdapters.kt): `ordinary()` содержит 26 адаптеров, `batch()` — 7; это не старое число 25 из октябрьского плана. **CantabriaRecCertificateLoginCadesAdapter уже зарегистрирован**; исторический пробел из раздела 3 больше не актуален на этой базе.
- Все `*Adapter.kt` в `signing/`, ссылки на `CadesDetachedCodec`, `SevillaAtseXadesEnvelopingCodec` и `SigningFormat.CADES/XADES`: 11 обычных локальных CAdES-detached и 3 XAdES-enveloping. Других реализаций этих двух семейств в текущем runtime-списке нет. Общие native-signing движки и сетевые tri-phase/batch протоколы не считаются локальными адаптерами этих семейств (см. исключения).
- [LocalCadesDetachedAdapter.kt](../../app/src/main/java/dev/junta/firmamobile/signing/LocalCadesDetachedAdapter.kt): общий `CadesDetachedCodec` уже существует, как и `CadesPreSignState`; повторяется прежде всего политика/оркестрация, а не весь CMS-движок.
- [SevillaAtseXadesEnvelopingAdapter.kt](../../app/src/main/java/dev/junta/firmamobile/signing/SevillaAtseXadesEnvelopingAdapter.kt): один codec уже используется Sevilla, AIReF и CDTI; различаются tuple/payload/algorithm/error policy.
- [SigningModule.kt](../../app/src/main/java/dev/junta/firmamobile/signing/SigningModule.kt), `SigningCoordinator.kt`, `SigningModels.kt`, `MiniAppletPayloadCodec.kt`, `MiniAppletCallbackAdapter.kt`, `browser/MiniAppletBridgeAdapter.kt`: создание, dispatch, владение состоянием и ответ.

Воспроизводимые read-only команды из worktree:

```sh
grep -RnE 'CadesDetachedCodec|SevillaAtseXadesEnvelopingCodec' app/src/main/java
grep -lE 'SigningFormat.CADES|SigningFormat.XADES' app/src/main/java/dev/junta/firmamobile/signing/*Adapter.kt
cat app/src/main/java/dev/junta/firmamobile/signing/RuntimeSigningAdapters.kt
```

Это поиск для аудита, не новые Python-тесты, читающие Kotlin как текст. Список надо переснять перед первым implementation PR, если main изменится.

## 1. Полный перечень CAdES-detached

Общее для 11 строк: `SigningFormat.CADES`, profileVersion **1**, точное равенство protocol ID + profile ID + serialized origin; алгоритмы ниже — RSA. Все ограничивают цепочку на входе prepare диапазоном **1..10**. Каждый возвращает `LocalSignature` с полным detached CMS (не голый PKCS#1). В `CadesDetachedCodec.createPreSign` используется `generate(..., false)`: даже портальное extra-property `mode=implicit` не превращает этот результат в attached CMS.

В столбце «общий адаптер» **C** означает предложенный `ParameterizedCadesDetachedAdapter` с неизменённым `CadesDetachedCodec`; это отдельные контрактные экземпляры, не один разрешающий набор origins. «Особый» означает сохранить самостоятельную портальную политику/обёртку до отдельного доказательства эквивалентности.

| Класс / protocol ID | profile ID / точный origin | Алгоритм и payload | Extra properties и дополнительные guards | Общий адаптер |
| --- | --- | --- | --- | --- |
| `LocalCadesDetachedAdapter` / `aragon-siraw-local-cades-v1` | `aragon-siraw` / `https://aplicaciones.aragon.es` | SHA1; ровно 20 произвольных байт | Точное `mode=explicit\nfilter=nonexpired`; safeDescription/pageUrl здесь не проверяются | C: fixed-length |
| `DgtVerificationCadesAdapter` / `dgt-verificacion-equipo-local-cades-v1` | `dgt-verificacion-equipo` / `https://sede.dgt.gob.es` | SHA1; ровно 15 байт `Cadena a firmar` | Точное `filter=nonexpired:`; без safeDescription/pageUrl guard; собственный `DgtCadesPreSignState` | C: fixed-literal, отдельный state до миграции DGT |
| `DiputacionLleidaCadesAdapter` / `diputacion-lleida-login-cades-v1` | `diputacion-lleida-sede` / `https://seu.diputaciolleida.cat` | SHA256; 1..512 байт печатного ASCII 0x20..0x7e | Точное `policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true`; точный `SAFE_DESCRIPTION` | C: printable challenge + exact properties |
| `DiputacionBadajozCadesAdapter` / `diputacion-badajoz-login-cades-v1` | `diputacion-badajoz-portal` / `https://sede.dip-badajoz.es` | SHA256; 1..512 байт печатного ASCII 0x20..0x7e | Та же ожидаемая строка, но **LiteralSigningProperties.canonicalize**, не literal equality; точный `SAFE_DESCRIPTION` | C: printable challenge + отдельная literal-canonicalization policy |
| `CanariasCertificateLoginCadesAdapter` / `canarias-certificate-login-cades-v1` | `canarias-sede` / `https://sede.gobiernodecanarias.org` | SHA1; ровно 29 ASCII-байт, regex weekday/date/time/GMT из `isExactChallenge` | Точные `SAFE_DESCRIPTION` и pageUrl `https://sede.gobiernodecanarias.org/sede/identificacion`; exact `EXPECTED_EXTRA_PROPERTIES`: format=CAdES Detached, pinned SignatureService serverUrl, sha512 referencesDigestMethod, полный issuer filter | C + особая Canarias policy; не нормализовать properties в адаптере |
| `UgrCadesDetachedAdapter` / `ugr-certificado-login-local-cades-v1` | `ugr-certificado-login` / `https://sede.ugr.es` | SHA1; ровно 22 байта `Universidad de Granada` | Пустые properties; точный `SAFE_DESCRIPTION` | C: fixed-literal |
| `JccmCertificateLoginProbeCadesAdapter` / `jccm-certificate-login-probe-local-cades-v1` | `jccm-certificate-login-probe` / `https://ventanillaelectronica.jccm.es` | SHA1; ровно 5 байт `ABCDE` | Пустые properties; точный `SAFE_DESCRIPTION` | C: fixed-literal; public probe не объявлять общей portal compatibility |
| `MitesCertificateLoginCadesAdapter` / `mites-certificate-login-local-cades-v1` | `mites-certificate-login` / `https://sede.mites.gob.es` | SHA512; ровно 10 байт `[a-z]` | Exact `mode=implicit\nfilters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:`; точный `SAFE_DESCRIPTION`; наличие LOGIN_PAGE_URL constant не означает pageUrl guard | C: lowercase challenge |
| `CantabriaRecCertificateLoginCadesAdapter` / `cantabria-rec-cert-login-cades-v1` | `cantabria-rec-cert-login` / `https://rec.cantabria.es` | SHA512; ровно 40 байт `[0-9a-f]` | Exact `filters=\nmode=implicit`; точный `SAFE_DESCRIPTION` | C: hex challenge; runtime registration уже есть |
| `TenerifeCadesDetachedAdapter` / `tenerife-sede-local-cades-v1` | `tenerife-sede-electronica` / `https://sede.tenerife.es` | SHA512; 1..524288 байт документа | Exact `mode=explicit`; точный `SAFE_DESCRIPTION` | C: bounded document |
| `EivissaCadesDetachedAdapter` / `eivissa-instancia-general-cades-v1` | `eivissa-sede-electronica` / `https://seu.conselldeivissa.es` | SHA256; 1..524288 байт документа | Точный `SAFE_DESCRIPTION`; properties <=16384 chars, regex `headless=true`, `filter=encodedcert:<base64>;filter=nonexpired:`, `mode=implicit`, optional mimeType и финальные LF; encodedcert должен совпадать с **первым выбранным сертификатом** | C технически возможен; особый Eivissa validator оставить отдельным, мигрировать последним |

Длинные literal constants Canarias/Eivissa намеренно не переписаны в новую таблицу настроек: источником истины остаются `EXPECTED_EXTRA_PROPERTIES`/`EXTRA_PROPERTIES` в их файлах. Не превращать pinned serverUrl в транспортный endpoint общего адаптера: этот адаптер подписывает локально и не должен ходить в сеть. `policy=FirmaAGE` у Lleida/Badajoz проверяется как входная строка; эти обёртки не передают `signaturePolicy` в codec, поэтому нельзя автоматически добавлять CMS policy attribute по имени extra-property.

## 2. Полный перечень XAdES-enveloping

Общее: `SigningFormat.XADES`, profileVersion **1**, точный protocol/profile/algorithm/safeDescription. Результат — полный XML XAdES Enveloping через `SevillaAtseXadesEnvelopingCodec`, с вложенным base64 data object и ссылками SignedInfo/SignedProperties/KeyInfo; не detached XML, не enveloped XML и не raw RSA. В codec RSA-сертификат обязателен; цепочка **1..8**, а не CAdES 1..10. Пустая цепочка отвергается обёрткой как UNSUPPORTED_PROTOCOL, превышение 8 обнаруживает codec внутри try как PROTOCOL_FAILED. Сохранять это различие.

**X** = предложенный `ParameterizedXadesEnvelopingAdapter` поверх существующего codec.

| Класс / protocol ID | profile ID / origin contract | Алгоритм / payload | Properties и URL guards | Общий адаптер |
| --- | --- | --- | --- | --- |
| `SevillaAtseXadesEnvelopingAdapter` / `sevilla-atse-xades-enveloping-v1` | `sevilla-atse-certificate-login`; scheme=https, host=`www.sevilla.org`, port=443 | SHA1; ровно 40 ASCII-байт `[0-9A-Za-z_-]` (`isExactSevillaAtseChallenge`) | Только пустые properties; pageUrl в самом адаптере не проверяется | X: Sevilla policy |
| `AirefXadesEnvelopingAdapter` / `airef-xades-enveloping-v1` | `airef-instancia-general`; scheme=https, host=`sede.airef.es`, port=443 | SHA1; ровно 32 произвольных байта (`isExactAirefPayload`), не ASCII/hex regex | Только пустые properties; SIGNING_PATH/CLIENT_AUTH constants не являются дополнительными guards этого адаптера | X: fixed-length policy |
| `CdtiXadesEnvelopingAdapter` / `cdti-xades-enveloping-v1` | `cdti-certificate-validation`; serialized=`https://sede.cdti.gob.es` | SHA512; 47 decoded bytes, из которых без padding получается 63-char token; prefix regex `CertExp[0-9a-f]{32}[0-9a-z]{23}` плюс особая проверка tail quartet | Exact `filters=nonexpired`; exact pageUrl `https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx`; payload проверяется **до try** в prepare и complete | X + отдельная CDTI policy, не заменять простым Base64 regex |

Для всех X оставить существующие XML parser hardening, canonicalization `http://www.w3.org/TR/2001/REC-xml-c14n-20010315`, base64 transform и digest/reference validation. UUID в XML генерируются случайно; нельзя считать любые два корректных результата побайтово одинаковыми. Codec поддерживает SHA1/SHA512 RSA для этих контрактов; расширение алгоритмов, EC и разбор raw r||s не часть миграции.

## 3. Различия, которые легко потерять

Обозначения: U = `UNSUPPORTED_PROTOCOL`, P = `PROTOCOL_FAILED`; «нет» = payload не декодируется повторно на complete, используется привязанный pre-sign state. Ошибка tuple на prepare/complete всегда U; ошибка потребления state / подписи внутри try — P. Таблица относится к живому объекту request; closed-request поведение также фиксировать отдельными тестами.

| Адаптеры | Неверный payload/properties на prepare | Повторная payload-проверка на complete / её ошибка |
| --- | --- | --- |
| Local CAdES, DGT, Eivissa | P внутри try | Нет |
| Lleida, Badajoz, Canarias, UGR, JCCM, Tenerife | P внутри try | Да / P, после проверки tuple |
| MITES, Cantabria | P внутри try | Да / U, в общем условии с tuple |
| Sevilla, AIReF | P внутри try | Нет |
| CDTI | U до try (и повторная проверка внутри try для разрешённого входа) | Да / U |

Нельзя «почистить» всё до одного валидатора с единым кодом ошибки или добавить одинаковый revalidation для всех строк. Это будет изменение наблюдаемого поведения. Аналогично нельзя объединить exact properties Lleida с canonicalizing Badajoz. При необходимости исправления уязвимости — отдельное решение/security PR, а не скрытая часть механического рефакторинга.

## 4. Seam и предлагаемый общий дизайн

### Варианты

1. **Один универсальный адаптер для CMS/XML/PDF/tri-phase** — отвергнут. Большой interface с флагами формата/сети/портала, изменения security-модели и слишком много одновременно затронутых файлов.
2. **Только общие функции в каждом классе** — безопасный промежуточный вариант, но оставляет копирование порядка prepare/complete и управления чувствительным состоянием.
3. **Два глубоких модуля C и X** — предпочтительный план. Существующий `SigningProtocolAdapter` остаётся внешним seam с теми же `id`, `prepare`, `complete`; codecs не переписываются. Конкретный портал поставляет маленький закрытый контракт, а модуль скрывает декодирование, материал pre-sign, consume-once и cleanup.

Параметры предлагаются как Kotlin immutable descriptors, не JSON/профильные scripts и не публичный API:

- `RequestContract`: ровно один protocol ID, profile ID/version, origin matcher (serialized либо scheme/host/port с прежней семантикой), optional exact pageUrl и optional exact safeDescription. **Отсутствие конкретного guard означает отсутствие только в этой обёртке**, не отключение upstream checks.
- `CadesContract`: algorithm, payload-size/literal validator, properties policy (exact / Badajoz canonicalize / именованный cert-aware Eivissa), известный completion revalidation variant. Clock/provider внедряются как сегодня. Не выбирать algorithm из произвольных properties. CAdES chain guard 1..10 остаётся до try.
- `XadesEnvelopingContract`: algorithm и именованный payload validator для codec, точные properties, вариант preflight CDTI. Clock внедряется как сегодня. Chain >8 остаётся ошибкой codec, а не новой U-проверкой в обёртке.
- Не экспортировать callback-encoding в descriptors: все 14 возвращают готовый контейнер, callback уже отдельный module. Не расширять `SigningProtocolAdapter`, не вводить новый registry/catalog schema.
- Именованные политики с небольшим закрытым числом вариантов вместо десятков boolean flags и произвольных plugin closures. Если descriptor становится сложнее отдельного адаптера — оставить самостоятельную обёртку (особенно Eivissa/CDTI).

На первом переходе сохранить публичные классы и companion constants как стабильные входы: конструктор делегирует одному C/X с **одним** контрактом. Тогда `RuntimeSigningAdapters`, тесты и catalog/parser references к `ID` не надо массово менять. Это не обязательство держать вечный pass-through: удаление обёрток и перенос constants оценить после всей серии, отдельно от миграции поведения.

### Владение состоянием

`SigningModels.kt::PreSignResult.consumeState` сверяет **identity объекта request** (`!==`), а не только UUID или tuple, потребляет state один раз и очищает bytesToSign. Нельзя ослабить до совпадения ID. `CadesPreSignState` хранит CMS placeholder, detached content, fingerprint; DGT пока имеет отдельный аналог. XAdES хранит unsigned XML/fingerprint в трёх частных state-классах.

Оставить `state.use`, idempotent synchronized close и zero-fill всех массивов при завершении/ошибке/отмене. Сохранить порядок: contract checks → допустимая повторная payload-проверка → consumeState → codec.complete → cleanup. Неверный owner не должен потребить чужое состояние. Не добавлять cache, повтор подписи или fallback при ошибке. В миграционном тесте явно проверять использование preSign через другой portal-adapter и уже закрытого/потреблённого preSign.

## 5. Текущие места вызова и что не объединять

Текущая цепочка: `SigningModule.ordinary()` → `RuntimeSigningAdapters.ordinary()` → `SigningAdapterIndex.resolve` (duplicate IDs запрещены) → `SigningCoordinator` через `adapterResolver` → выбранный `SigningProtocolAdapter.prepare/complete`. Consent, navigation epoch/current origin/current page и срок запроса остаются обязанностью существующего coordinator/browser; рефакторинг адаптеров их не заменяет.

На выходе `browser/MiniAppletBridgeAdapter.kt` (доставка рядом со строками 1946–1954 на базе аудита) base64-кодирует готовый контейнер и leaf certificate; `MiniAppletCallbackAdapter` выдаёт JSON `type=MINIAPPLET_RESULT`, `requestId`, `status=success`, `signature`, `certificate`, либо `status=error`, `errorCode`. Для C это base64 CMS, для X — base64 XML. Размерные лимиты, canDeliver и очистку при недоставке сохранить. Одинаковый callback **не** делает одинаковыми подписываемые байты или формат контейнера.

| Оставить отдельно | Причина / фактическая связь |
| --- | --- |
| `PoliciaXadesDetachedAdapter`, `LocalXadesDetachedAdapter` | Это **XAdES detached**, не enveloping; собственные XML structure/reference/payload contracts. Пример Policia в задании — повод проверить соседство, не основание включать его в X |
| `TransportesXadesEnvelopedAdapter` | XAdES **enveloped**: подпись внутри исходного XML; другая модель ссылок/валидации |
| `AccedaPadesAdapter` | PAdES, использует `CadesDetachedCodec` внутри PDF workflow (`createPreSign`, `complete`, `validate`), но наружный контейнер PDF и ByteRange/резервирование содержимого иные |
| `MiniAppletPadesAdapter` и `GranCanariaPadesAdapter`, `FuerteventuraPadesAdapter`, `MinecoPadesAdapter`, `TransparenciaPadesAdapter` | PAdES-обёртки/общая PDF orchestration; внутреннее CMS не делает их CAdES-detached portal-adapters |
| `JuntaTriPhaseAdapter`, `JuntaOfvirtualTriPhaseAdapter`, `UnizarTriPhaseAdapter`, `XuntaPadesTriPhaseAdapter` | Server pre/post-sign, transport/origin/endpoint policies и tri-phase response. Даже поддержка CAdES/XAdES у сервера не равна локальному codec-контракту |
| Все 7 `RuntimeSigningAdapters.batch()` | `BatchSigningProtocolAdapter`, STA/batch job lifecycle, множественные документы и сетевые/агрегированные ответы: Melilla, Extremadura, LaPalma, Huesca, Lugo, Caib, Burgos |
| `CadesSigner` / native AutoFirma signing, protocol input/registry/callback adapters | Иной interface и dispatch; это не дополнительные экземпляры C или X из таблиц. Не менять origin/security/response model для их включения |

Eivissa и CDTI — **кандидаты только на общую оркестрацию**, не на слияние их валидаторов с другими порталами. Их особые контракты неизбежны. Canarias и Badajoz также сохраняют отдельные policy реализации. Слияние всех 14 в один «общий разрешённый portal» не предлагается.

## 6. Golden/characterization: доказательство неизменности

Новых тестов в этом docs-only PR нет. План для implementation PR: сначала зафиксировать поведение старого адаптера через публичный seam, затем запускать тот же набор через новый. Golden — результаты **синтетических существующих fixture builders**, не SHA исходника и не приватные portal captures.

### Фактически существующие основы

Для каждой строки таблиц есть одноимённый `tests/<Class>Test.kt`, **кроме UGR**: его положительный и отрицательный сценарии находятся в `LocalCadesDetachedAdapterTest.kt` (`createsVerifiableUgrDetachedCadesForExactlyTheSynthetic22BytePayload`, `ugrAdapterFailsClosedForEveryNonExactContractMutation`).

- `LocalCadesDetachedAdapterTest`, `DgtVerificationCadesAdapterTest`: detached CMS validation/tamper/owner cases, синтетические 20 байт и `Cadena a firmar`.
- `DiputacionLleidaCadesAdapterTest`, `DiputacionBadajozCadesAdapterTest`: printable 64-char challenge; обязательно добавить characterization различных permutations/CRLF/duplicates/unknown properties для exact vs canonicalize, не уравнивать ожидания.
- `CanariasCertificateLoginCadesAdapterTest`: `Wed, 02 Jan 2030 03:04:05 GMT`, точные properties и signing page; regex здесь проверка формы, а не новая календарная/TTL policy.
- `JccmCertificateLoginProbeCadesAdapterTest`: `ABCDE`; текущий позитивный тест проверяет prepare. Для миграции добавить полноценный complete/verify, а не считать его уже существующим.
- `MitesCertificateLoginCadesAdapterTest`, `CantabriaRecCertificateLoginCadesAdapterTest`: 10 lowercase / 40 hex bytes, SHA512, tampering/replay; Cantabria также chain/owner/consumed-state случаи.
- `TenerifeCadesDetachedAdapterTest`: `synthetic Tenerife application document`; `EivissaCadesDetachedAdapterTest`: `synthetic Eivissa xsig`, свойства с выбранным certificate. Покрыть optional mimeType, точные LF, чужой certificate, 0/limit/limit+1 bytes и max properties chars.
- `SevillaAtseXadesEnvelopingAdapterTest`: существующий 40-byte CHALLENGE и independent XML verification; `AirefXadesEnvelopingAdapterTest`: сгенерированные 32 bytes и wrong tuple/length; `CdtiXadesEnvelopingAdapterTest`: dynamic token и `acceptsNoncanonicalBase64TailUsedByLiveCdtiChallenge` — обязательный regression, не «исправлять» canonical tail.
- `SigningTestIdentity.kt::syntheticIdentity()` и `certificate/TestCertificateFactory`: синтетические RSA identity builders, а не закоммиченный личный PKCS12. Фиксированный Clock в этих adapter tests — `2030-01-02T03:04:05Z`. Fixture identity создаётся в тестах; не ожидать одинаковых certificate/key bytes между независимыми запусками JVM.
- `RuntimeSigningAdaptersTest`, `ProtocolAdapterRegistryTest`, `SigningProtocolAdapterContractTest`, `DgtVerificationProtocolAdapterRegistryTest`, browser `MiniAppletBridgeAdapterTest`: сохранить dispatch/registry/callback coverage. Это вспомогательные regression suites, не свидетельство portal E2E.

### Что сравнивать

1. **CAdES differential golden**: один и тот же synthetic identity/chain, Clock, payload и независимые request owners с одинаковым содержимым для старого и нового адаптера в одном тесте. Сравнить bytesToSign, весь CMS output и callback поля. Старый/new state не смешивать. Проверить CMS независимо через BC: detached flag, signed attributes, content digest, signingTime, algorithm OIDs, certificate/fingerprint, отсутствие неожиданного signaturePolicy, успешная подпись и отказ при tampering. Для долговечных checked-in goldens хранить ожидаемую семантическую структуру/OIDs/payload digest, не случайный DER сертификата или приватный ключ.
2. **XAdES golden**: из-за UUID нельзя требовать равенство двух independently prepared XML byte-for-byte или просто маскировать весь SignedInfo. Сначала проверить каждый реальный XML независимым verifier: все Reference digests, signature, fingerprint/chain, uniqueness и согласованность ID/URI, payload extraction. Затем сравнить семантическую проекцию со старой и ожидаемой: namespaces, topology, количество/типы references, algorithms/transforms, payload, certificate chain и signingTime. Нормализовать только случайные generated ID и связанные URI в согласованное отображение; derived digests/SignatureValue не сравнивать как стабильные golden bytes, но обязательно проверять криптографически до проекции. Никаких production UUID hooks только ради теста. Существующий codec не менять ради детерминизма.
3. **Ошибки как golden**: для каждой из 14 строк зафиксировать prepare/complete result code и момент consumption для bad protocol/profile/version/origin/port/scheme/page/description/format/algorithm, chain 0/1/max/max+1, wrong payload/properties, closed request. Матрица раздела 3 обязательна; invalid complete после потребления не должен разрешать retry.
4. **Полный response contract**: JSON type/status/requestId/signature/certificate/errorCode, standard Base64, отсутствие raw PKCS#1 вместо контейнера, canDeliver false после navigation/origin/expiry, one-shot delivery. Не менять JS shim, parser normalization или callback encoding в adapter-migration PR.
5. **Security negatives**: чужой request с тем же UUID/tuple, state другого портала, повтор complete, ошибочная localSignature, cancellation/close, certificate substitution и превышение лимитов. Проверять cleanup через доступные observer/seams и поведение close, не чтением `.kt` как текста.

Golden failures не обновлять автоматически: reviewer должен отличить случайную энтропию от изменения алгоритма, signed bytes, допуска origins или ошибки. Если существующий тест противоречит правильному security-поведению — отдельный issue/PR с объяснением, без подгонки production под тест.

## 7. Поэтапная миграция: строго один адаптер на PR

Это последовательность будущих работ, не содержимое текущего PR. Каждый PR стартует со свежего main, добавляет characterization **своего** адаптера до переключения, меняет не более одной строки runtime поведения; остальные строки остаются legacy. Сохранять class/ID wrapper, чтобы не трогать `RuntimeSigningAdapters`/`SigningModule` без необходимости. Общий helper можно добавить с первым потребителем, но не переключать второй для доказательства reuse в том же PR.

| PR-порядок | Единственный мигрируемый адаптер | Работа / gate |
| --- | --- | --- |
| 1 | LocalCadesDetachedAdapter | Ввести C, перенести только Aragón; codec и CadesPreSignState остаются на месте; differential CMS/owner golden |
| 2 | UgrCadesDetachedAdapter | Reuse C с fixed literal и safeDescription; оставить UGR cases в LocalCades tests |
| 3 | JccmCertificateLoginProbeCadesAdapter | Добавить недостающий end-to-end JVM prepare/sign/complete/verify для probe |
| 4 | DgtVerificationCadesAdapter | Отдельно решить перенос DgtCadesPreSignState; сохранить отсутствие description guard и revalidation |
| 5 | DiputacionLleidaCadesAdapter | Exact multiline properties и printable boundaries |
| 6 | DiputacionBadajozCadesAdapter | Только canonicalizing policy; comparison с Lleida не должен расширить Lleida |
| 7 | MitesCertificateLoginCadesAdapter | SHA512, lowercase challenge, complete payload failure U |
| 8 | CantabriaRecCertificateLoginCadesAdapter | 40 hex, registry остаётся зарегистрированным, complete failure U и replay tests |
| 9 | TenerifeCadesDetachedAdapter | Документ 524288-byte limit, revalidation failure P |
| 10 | CanariasCertificateLoginCadesAdapter | Особая page/payload/properties policy; parser-origin исключения не переносить в общий descriptor |
| 11 | EivissaCadesDetachedAdapter | Только если cert-aware policy остаётся компактной; иначе оставить standalone с объяснением; selected-cert/mime/limits goldens |
| 12 | SevillaAtseXadesEnvelopingAdapter | Ввести X, не переписывая codec; semantic XML golden и chain >8 error P |
| 13 | AirefXadesEnvelopingAdapter | X reuse, 32 arbitrary bytes (не ограничивать ASCII), origin tuple equivalence |
| 14 | CdtiXadesEnvelopingAdapter | Отдельный preflight/error policy и token-tail characterization; оставить standalone если это требует ослабления/излишних flags |

Затронутые будущие production-файлы на шаг: новый общий C/X (только при введении/нужной небольшой policy), **один** соответствующий `<Class>.kt`, при доказанной необходимости небольшой именованный validator рядом. Тесты: его `*Test.kt` и общий parameterized behavioral harness; registry tests только при необходимости. Не делать «PR 15» с удалением всех wrapper/перемещением codecs автоматически: это отдельная оценка scope, после доказанного поведения всей серии.

Общий интерфейс `SigningProtocolAdapter`/каталог/security модель не меняются. Если реализация требует нового протокола или выхода за ~20 production-файлов — остановиться и пересогласовать план. Нельзя одновременно объединять codecs, добавлять EC, модернизировать SHA1, менять consent, catalog loader, origin exceptions, JS shim или dependencies.

Каждый implementation PR: минимальный behavioral RED/GREEN там, где добавляется тестовый seam, затем CI/security на **точном head SHA**. На телефоне по текущему ограничению только дешёвые read-only/текстовые проверки, без локальных сборок/Gradle/компиляции. Не запускать `gh workflow run`, E2E/instrumentation; обычные PR workflows сами дают no-E2E gate. Откат — revert единственного adapter-migration PR; сохранённый класс/ID упрощает локализацию.

## 8. Риски и критерии приёмки

- Главный риск — расширить язык входов при «унификации»: literal vs canonicalized properties, pageUrl только у некоторых контрактов, origin serialized vs tuple, bounds, CDTI noncanonical tail. У каждой строки собственные negatives; origin sets не объединять.
- Нельзя считать наличие константы signing path/page/description фактическим guard. Добавление guard — отдельное поведенческое изменение; upstream browser/parser guards при этом сохраняются.
- Опасны изменение порядка ошибок, повторное декодирование после закрытия request, premature state consumption и потеря zero-fill. Positive signature test недостаточен.
- Отдельный риск — ошибочно принять `mode=implicit`/`policy=FirmaAGE`/`referencesDigestMethod` за команды codec. Сохранять фактические container semantics и algorithm, не интерпретировать properties заново.
- Fixtures синтетические: успешный CMS/XML verifier и CI не доказывают принятие на реальном портале, physical provider поведение или E2E-совместимость. Данные/сеть порталов не запрашивались, приватные ключи/документы не добавлялись.
- Предложенные C/X пока не реализованы; стоимость descriptors и возможность безопасно покрыть Eivissa/CDTI требуют review. Хороший результат — меньше дублирования управления состоянием без роста разрешённых операций, а не максимальное число удалённых классов.

Приёмка **этого** PR: только данный handoff; все 14 адаптеров перечислены и трассируются к коду/тестам, исключения объяснены, миграция по одному адаптеру/PR, `git diff --check`, проверка относительных ссылок и file ownership. Широкое выполнение тестов доверено обычному GitHub Actions CI/security точного PR head; окончательный результат фиксируется в описании PR, не объявляется заранее. Никаких merge/ready действий.
