# Импорт ключа подписи PKCS12 в AndroidKeyStore / StrongBox

Дата: 2026-10-24. Статус: дизайн, не реализация и не подтверждение аппаратной совместимости.
Основа аудита: `origin/main` = `07b8232d` (полный SHA записан в PR).
Область этого изменения: только этот документ; production-код, ключи, зависимости и политики не меняются.

## 1. Цель и граница доверия

Импортировать **существующий приватный ключ подписи из PKCS12**, сохранив исходный сертификат и цепочку, и затем подписывать через неэкспортируемый AndroidKeyStore `PrivateKey`-handle. Это не генерация нового ключа и не AES-ключ `CertificateUnlockCache.kt`, который защищает сохранённый пароль. Генерация нового ключа сделает старый сертификат непригодным: потребуется перевыпуск у УЦ.

Неэкспортируемость относится к импортированной копии: исходный PKCS12 и его резервные копии остаются экспортируемыми. Обычный импорт не доказывает, что ключ никогда не был в host memory, и не превращает сертификат в квалифицированное средство подписи. Компрометация приложения всё ещё может позволить злоупотребить операцией подписи; обязательны прежние consent, origin policy, лимиты, привязка к документу/сертификату, отмена и self-verification. Аппаратная аутентификация — отдельное согласуемое решение функции 5, не замена consent.

Предлагаемая первая поставка после отдельного security review: явный opt-in, RSA signing-only, обычный импорт; аппаратная защита обязательна для выбранного аппаратного режима. Wrapped import и EC runtime — отдельные этапы. Никакого автоматического перевода существующих записей и скрытого fallback на программный ключ. Это предложение, а не уже принятая security-модель.

## 2. Два способа импорта

Оба используют `KeyStore.getInstance("AndroidKeyStore")`, `load(null)` и `setEntry`, но принимают разные контейнеры. AndroidKeyStore не принимает файл PKCS12 непосредственно. Официальные описание и API: [Keystore guide](https://developer.android.com/privacy-and-security/keystore), [KeyProtection](https://developer.android.com/reference/android/security/keystore/KeyProtection), [WrappedKeyEntry](https://developer.android.com/reference/android/security/keystore/WrappedKeyEntry).

| Свойство | `PrivateKeyEntry` + `KeyProtection` | `WrappedKeyEntry` (API 28+) |
| --- | --- | --- |
| Вход | `KeyStore.PrivateKeyEntry(privateKey, certificateChain)` после ограниченного разбора PKCS12 и проверки выбранного alias | ASN.1 `SecureKeyWrapper`, alias wrapping-ключа, transformation и параметры unwrap; это **не** зашифрованный PKCS12 |
| Вызов | `setEntry(newAlias, entry, protection)` | `setEntry(newAlias, wrappedEntry, null)`; authorization list находится внутри wrapper |
| RSA / EC | AndroidKeyStore допускает импорт RSA и EC private keys; ограничения размера/кривой/авторизаций задают реализация и security level. Нужен пригодный к импорту software key, не произвольный opaque handle | Secure import может переносить RSA и EC asymmetric material (PKCS#8 внутри wrapper); RSA wrapping-ключ не означает, что целевой ключ обязан быть RSA. Поддержка конкретной кривой/размера и сочетания wrapping/target security level проверяется отдельно |
| Доступность | API 23+ для `KeyProtection`; minSdk проекта 26, но это не гарантия TEE | API 28+ **и** устройство с Keymaster 4+ / подходящим KeyMint, поддерживающее secure import; одного SDK check недостаточно |
| Где plaintext | PKCS12 расшифрован в приложении; сериализуемое представление может проходить через framework при импорте. После импорта выданный handle не экспортирует материал | При корректном внешнем wrapping содержимое ключа расшифровывается в secure hardware, а не в Android host memory |
| Авторизации | `PURPOSE_SIGN`, минимальный согласованный набор digest, для RSA `SIGNATURE_PADDING_RSA_PKCS1`; EC без RSA padding; auth-policy выбирается до импорта | Целевые authorizations закодированы в `KeyDescription.authorizationList`; отдельно требуется wrapping key с `PURPOSE_WRAP_KEY` и согласованными OAEP-параметрами |
| Цепочка | Передаётся в `PrivateKeyEntry`, leaf первым; проверяется соответствие ключа leaf | Сертификаты не являются частью `SecureKeyWrapper`; отдельный этап привязки цепочки к импортированному alias, без повторного импорта private material. Точный public-API путь и OEM-поведение должны быть доказаны spike до реализации |

Источник ограничений secure import и origin: [Android guide, Import encrypted keys into secure hardware](https://developer.android.com/privacy-and-security/keystore#import-encrypted-keys), [KeyMint implementer reference](https://source.android.com/docs/security/features/keystore/implementer-ref). PKCS#8 как asymmetric import format определён в [KeyFormat.aidl](https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/security/keymint/aidl/android/hardware/security/keymint/KeyFormat.aidl). Параметры импорта RSA/EC и wrapped key описаны в [IKeyMintDevice](https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/security/keymint/aidl/android/hardware/security/keymint/IKeyMintDevice.aidl) при реализации; HAL-возможность не является обещанием поддержки public API на каждом устройстве.

### Обычный импорт: предлагаемый порядок

1. Пользователь отдельно подтверждает импорт, выбирает PKCS12 alias (не путать с новым внутренним Keystore alias) и вводит пароль. Сохранить существующие ограничения размера, числа entries, цепочки, validity, keyUsage и проверку key/certificate match из `Pkcs12Loader.kt`.
2. Под случайным новым, не переиспользуемым alias импортировать `PrivateKeyEntry` с исходной цепочкой. Никогда не затирать действующий alias «на месте». Разрешать только нужные операции и алгоритмы; не включать decrypt/unwrap/TLS «на всякий случай».
3. Получить новый `PrivateKey` через `getKey(alias, null)`, проверить metadata и security level; `getEncoded() == null` / `getFormat() == null` — проверка интерфейса неэкспортируемости, **не** доказательство аппаратной защиты.
4. До публикации записи выполнить ограниченную локальную challenge-sign/verify по исходному public key и каждому разрешённому алгоритму. Это техническая проверка ключа, не подпись документа и не consent; для auth-bound key потребуется согласованная аутентификация. Проверить соответствие возвращённого сертификата исходному leaf и fingerprint цепочки.
5. Атомарно опубликовать reference только после всех проверок. Удалить временный alias при ошибке; не выдавать старую PKCS12 identity за успешный hardware import. Очистить свои byte/char buffers; нельзя обещать детерминированное обнуление `BigInteger`, provider objects или всех JVM-копий ключа.

`SigningAlgorithm` сейчас содержит SHA1/256/384/512 with RSA. Не расширять список из-за возможностей Keystore. Авторизовать только реально согласованные существующие варианты; legacy SHA1 допустим лишь там, где уже разрешён, не включать глобально и не заменять SHA256 на SHA1 при отказе железа. RSA PKCS#1 signing — не RSA-PSS и не RSA-OAEP. Ограничения hardware могут сделать конкретный маршрут недоступным; это причина отказа/предварительного предупреждения, не повод менять протокол.

### Wrapped import: отдельный, отложенный протокол

На устройстве создаётся RSA wrapping key с `PURPOSE_WRAP_KEY`; доверенная offline-машина получает его public key, при необходимости проверяет attestation и готовит `SecureKeyWrapper`. Внутри — зашифрованный transport key, IV, authenticated key description, зашифрованный target material и tag; `WrappedKeyEntry` ссылается на существующий wrapping alias. Не изобретать формат, OAEP/MGF1 параметры или схему аутентификации по аналогии с PKCS12. Сначала воспроизводимый синтетический interoperability spike и негативные тесты tamper/wrong alias/wrong authorization.

Если расшифровать PKCS12 на телефоне и затем локально завернуть ключ, plaintext **уже был** в host memory. Такой путь не обеспечивает преимущество «никогда не появлялся в памяти Android», хотя финальный handle неэкспортируем. Нельзя отправлять личный PKCS12/пароль на новый сервер ради wrapping. Внешний trusted tool, проверка attestation и доставка wrapper меняют trust boundary и требуют отдельного решения владельца. До такого решения обычный локальный import — честно ограниченная цель, а не «secure wrapped import».

## 3. StrongBox, TEE и проверяемый fallback

StrongBox — опциональный secure element на части устройств API 28+, а не синоним AndroidKeyStore. Базовый набор включает RSA-2048 и EC P-256; RSA-3072/4096 и P-384 нельзя обещать для StrongBox. TEE и конкретный OEM могут поддерживать больше, но без доказательства это неизвестно. StrongBox медленнее и имеет ограничения concurrent operations; batch не должен запускать неограниченное число аппаратных операций. Источник: [StrongBox KeyMint](https://developer.android.com/privacy-and-security/keystore#HardwareSecurityModule).

Предлагаемый capability flow для обычного импорта:

- На API 26–27 не обращаться к API 28: только явно обозначенный TEE-кандидат; StrongBox/wrapped недоступны.
- На API 28+ `PackageManager.hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)` — предварительный сигнал. Затем реальный импорт с `KeyProtection.Builder.setIsStrongBoxBacked(true)`; наличие feature не гарантирует нужный размер/кривую/операцию.
- При `StrongBoxUnavailableException` или доказанном unsupported algorithm/size не менять алгоритм и не генерировать заменяющий ключ. Предложить пользователю отдельный переход на TEE, только если выбранная политика допускает downgrade. При hardware-required/StrongBox-required режиме — отказ. Не обобщать любой `KeyStoreException` до «попробуем менее защищённо»; auth/cancellation/storage/mismatch failures — fail closed.
- Для согласованного fallback удалить только собственный временный alias и импортировать под другим alias без `setIsStrongBoxBacked(true)`. Это **запрос default implementation, не принудительный выбор TEE**. Затем подтвердить реальное размещение.
- На API 31+ `KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)` и `getSecurityLevel()` должны показать `SECURITY_LEVEL_TRUSTED_ENVIRONMENT` / `SECURITY_LEVEL_STRONGBOX`. `SOFTWARE`/`UNKNOWN` не удовлетворяют аппаратному режиму. На API 26–30 использовать `isInsideSecureHardware()`; true подтверждает secure hardware, но не различает TEE/StrongBox. Не вызывать `getSecurityLevel()` на API 29 только потому, что guide обсуждает targetSdk: сам метод добавлен в **31**, как указывает [KeyInfo API](https://developer.android.com/reference/android/security/keystore/KeyInfo#getSecurityLevel()).
- Для UI сохранить запрошенный и подтверждённый уровень, origin imported/securely-imported при доступности и доступный набор операций. На старых API честно показывать «аппаратная защита, точный тип не подтверждён», не выдавать feature flag за attestation. Логи не содержат alias, subject, сертификат, ключ, URI, пароль или документы.

Wrapped import нельзя механически повторить как обычный import на TEE: wrapping key и wrapper привязаны к выбранному пути, authorization/auth-SID должны согласовываться. Нужен отдельный TEE wrapping key и новый wrapper, либо честный отказ. Никакого автоматического распаковывания в host memory при ошибке secure import.

## 4. Полный прямой доступ к private key внутри `signing/`

Пути ниже относительно `app/src/main/java/dev/junta/firmamobile/signing/`; номера строк относятся к базовому SHA. Проверены все production `.kt` в каталоге поиском `private.?key`, `withPrivateKey`, `initSign`, `generatePrivate`, `key.s`, `encoded`, `setProvider` и просмотром найденных границ.

| Файл / место | Что делает сейчас | Следствие для opaque key |
| --- | --- | --- |
| `LocalSignatureEngine.kt:55–64,85–94` — `JcaLocalSignatureEngine.sign/signatureForKey` | `identity.withPrivateKey`, RSA-only, `initSign`, `update`, `sign`; для `RSAPrivateKey` выбирает isolated bundled BC, иначе native JCA dispatch | RSA handle уже предусмотрен; не требуется экспорт. Но необходимо доказать корректную классификацию реального AndroidKeyStore key: если handle реализует `RSAPrivateKey`, type-check может ошибочно выбрать BC. Нельзя читать exponent/encoded или retry через software provider |
| `CadesSigner.kt:86–98` — `BouncyCastleCadesSigner.signDetached` | RSA-only, `JcaContentSignerBuilder.build(privateKey)`; pin BC только для `RSAPrivateKey`; digest/CMS остаются BC | BC container/digest не требуют private bytes, native Signature должен остаться у Keystore. Тот же риск type-based provider routing; проверить реальный ContentSigner, а не только raw signature |
| `SoftwareEcSignatureModels.kt:21` — `SoftwareEcSignatureRequest.privateKey` | Передаёт `PrivateKey` напрямую как request field | Не означает поддержку opaque key; это software-only вход |
| `SoftwareEcCurvePolicy.kt:27–35` — `privateSpec` | Cast к `ECPrivateKey`, чтение `params` и **`key.s`**, создание `ECPrivateKeySpec` | Несовместимо принципиально: скаляр не доступен. Существующий fail-closed `UNSUPPORTED_KEY` сохранить |
| `SoftwareEcSignatureEngine.kt:29,34–40` — `sign` | Извлекает private spec, `KeyFactory` bundled BC, `generatePrivate`, `Signature(..., BC).initSign(signingKey)` | Нельзя направлять сюда AndroidKeyStore handle и нельзя добавлять export fallback; нужен отдельный native EC backend, не переделка software isolation |
| `SoftwareEcCadesEngine.kt:22–23` — `signDetached` | Передаёт тот же `request.privateKey` в `SoftwareEcSignatureRequest` для подписи DER attributes | Наследует ограничение software EC; native CMS/EC activation отдельно, не автоматически из возможности импорта |

Это все прямые обращения/передачи private key в `signing/` на базовом SHA. Остальные совпадения `.encoded` здесь относятся к **публичным сертификатам, CMS/ASN.1 или signed attributes**, не экспорту приватного ключа. `Signature.initVerify` в XAdES/CAdES/tri-phase codecs — верификация public key, не скрытый signing backend.

### Косвенные маршруты и соседние границы

- `SigningCoordinator.kt:259` и `BatchSigningCoordinator.kt:174` вызывают `localSignatureEngine.sign`; portal adapters готовят to-be-signed bytes и завершают контейнер. Поэтому local CAdES-detached, XAdES detached/enveloping/enveloped, PAdES и tri-phase не нужно переписывать для RSA handle: требуется подтвердить тот же поток pre-sign → raw signature → complete, лимиты, подпись нужного DER/канонизованного XML/PDF digest и self-verification. `LocalCadesDetachedAdapter.kt:211–227` использует `CapturingContentSigner` для шаблона/attributes, не private key. Успех импорта не доказывает приёмку порталом.
- `certificate/CertificateModels.kt:14–20`: `UnlockedIdentity` уже скрывает `PrivateKey` за `withPrivateKey`; не добавлять `getEncoded` в identity contract. Потребуется отличать происхождение ключа явно, а не только по Java interface. `Pkcs12Loader.kt:174–218` сейчас принимает **только RSA**, проверяет match посредством SHA256withRSA. Software EC engines не активируют EC import/runtime.
- Вне `signing/` ещё один настоящий signer: `afirma/servlet/NativeCadesEngine.kt:106–128`; такой же RSA guard и conditional BC `JcaContentSignerBuilder`. Нативный AutoFirma CAdES/PAdES и prehashed-ветки должны тестироваться отдельно; «CMS builder работает» не означает сохранения prehash-семантики.
- Другие `withPrivateKey` вне `signing/`: `browser/ClientAuthWebViewClient.kt:85`, `browser/InteractiveClientAuthController.kt:146`, `browser/download/ClientTlsIdentity.kt:21` передают identity для клиентского TLS. Signing-only imported key нельзя автоматически использовать в WebView/Conscrypt/OkHttp: TLS может требовать дополнительные digest/padding и несовместимую per-use auth. Это отдельная граница trust/consent. Первая поставка не заменяет существующий TLS источник; перед включением общего default identity требуется отдельный review и явное разделение key capabilities.
- Файл `CertificateUnlockCache.kt` содержит AndroidKeyStore AES, но не signing-key import. Его alias/версии записей не переиспользовать и не удалять как signing keys.

### EC: чего не обещаем

Keystore EC import и runtime EC signing — разные возможности. Для P-256/SHA256 и P-384/SHA384 будущий native backend использует public curve parameters из сертификата, `Signature` с native provider и opaque handle, а не `key.s`. Проверять DER ECDSA, диапазоны r/s и каноничность; XAdES может требовать преобразование DER ↔ фиксированное r||s, CMS — DER. Текущие software-only EC тесты полезны как oracle, но не подтверждают AndroidKeyStore или XML/PAdES compatibility. Не ослаблять guards software engines и RSA-only протоколы ради EC.

## 5. Сохранение, миграция, удаление и восстановление

### Что сохранено сейчас

`CertificateReferenceStore.kt` хранит одну SAF URI reference + отображаемые metadata/summary, **не** private key. `CertificateRepository.unlock` читает PKCS12 по URI и паролю заново; `forget` очищает reference и отпускает доступ к документу. `CertificateSession` держит identity/lease в памяти, сверяет snapshot/fingerprint и блокируется по lifecycle/expiry. Persistent password cache — отдельный механизм. PR [#369](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/369) на момент аудита OPEN/DRAFT, не часть базового main; его native unlock authentication нельзя считать готовой per-sign policy.

### Предлагаемая versioned migration

Нужен согласованный record kind `Pkcs12Reference` / `AndroidKeystoreReference` (имена концептуальные), schemaVersion, случайный alias, leaf/chain, fingerprint, capabilities, запрошенный/подтверждённый security level и auth-policy version. Это меняет общую certificate/security модель, поэтому реализация не входит в этот PR и требует отдельного решения/ADR. Согласовать с параллельным multi-certificate дизайном, не реализовывать конкурирующий storage format здесь.

1. Старые references остаются читаемыми без изменения. Пользователь явно выбирает перевод и предупреждён: перенос на другое устройство потребует исходный PKCS12 или перевыпуск. Проверить доступность исходного URI и повторно запросить пароль, не продлевать cache lease.
2. Создать staging record/alias; validate → import → hardware check → challenge verify → commit нового record. Старую запись и её работоспособность сохранять до commit. Учитывать crash/cancel на каждой границе: собственные orphan aliases очищаются по namespace и журналу, не массовой очисткой Keystore. Metadata не должна активировать неподтверждённый alias после process death.
3. После commit закрыть прежнюю in-memory identity, инвалидировать старые signing snapshots/consents, очистить password-cache record **только этого сертификата** и его парольные копии. Не удалять общий AES alias, если он ещё обслуживает другие записи. Сохранить прежние timeout/background правила: постоянный alias не равен постоянному разрешению подписывать.
4. Отпустить SAF grant, когда он больше не нужен. **Не удалять исходный PKCS12 автоматически**: он принадлежит пользователю/документ-провайдеру, и его удаление не гарантирует удаления облачных копий. Пользователь отдельно решает, хранить ли offline encrypted original. Сообщать: пока original существует, ключ в целом не стал «единственной неэкспортируемой копией».
5. При ошибке до commit удалить staging alias, оставить старый путь и явную ошибку импорта. После commit пропавший/инвалидированный alias — состояние reimport-required, не молчаливое чтение старого URI или создание нового ключа. Проверки revoked/expired certificate остаются прежними.

### Удаление

Блокировать новые операции и закрыть session/lease/snapshots; отменить незавершённые операции и не отдавать уже полученный результат после удаления. Затем `deleteEntry(alias)` только для owned signing alias; удалить record/chain/cache, отпустить URI grant. Это не транзакция с DataStore: при ошибке `deleteEntry` оставлять tombstone/pending-delete и блокировать использование, retry при следующем открытии; не показывать успешное уничтожение ключа. Отдельно обработать orphan после успешного delete и сбоя записи metadata. Удаление локального ключа не отзывает сертификат у УЦ и не уничтожает external PKCS12. Замена/ротация — новый alias, не overwrite.

### Backup / reinstall / invalidation

Приватный материал AndroidKeyStore нельзя экспортировать в backup приложения, перенести на другой телефон или восстановить из цепочки сертификатов. Uninstall, clear app data, factory reset и некоторые auth-policy изменения могут сделать alias недоступным; перестановка приложения с другой подписью особенно требует заранее сохранить исходный PKCS12. Из публичного сертификата private key восстановить нельзя. Заранее объяснить выбор: encrypted offline original вне приложения либо перевыпуск/отзыв у УЦ при потере. Reimport из оригинала создаёт новый локальный alias, но не возвращает старую аппаратную запись.

Уже есть `android:allowBackup="false"`, `res/xml/backup_rules.xml` и `data_extraction_rules.xml`, исключающие app data и device transfer. Их не ослаблять. Не копировать keystore blobs, wrapper secrets, URI или password cache в export. Если metadata каким-либо внешним механизмом всё же восстановлена, проверять существование alias и fingerprint/chain заново и fail closed; не показывать working hardware identity по одной восстановленной summary. См. [Keystore guide](https://developer.android.com/privacy-and-security/keystore) и текущие manifest/XML проекта.

## 6. Этапы по отдельным PR

Каждый этап требует bounded diff, неизменных origin/consent/size guards и CI на точном head. При изменении security-модели сначала согласовать дизайн; не реализовывать все этапы одним PR.

| PR / gate | Предполагаемые файлы и места вызова | Критерий завершения |
| --- | --- | --- |
| 1. ADR и capability model без активации | Новый ADR; `certificate/CertificateModels.kt`, `CertificateReferenceStore.kt`, будущий backend interface; согласование multi-certificate/auth дизайнов | Явные software/opaque origins, signing/TLS capabilities, versioned records, downgrade policy и recovery semantics; старый путь без изменений |
| 2. Provider-routing seam и JVM regression | `signing/LocalSignatureEngine.kt`, `CadesSigner.kt`, `afirma/servlet/NativeCadesEngine.kt`, их JVM tests | Opaque handles не попадают в bundled BC из-за одного `RSAPrivateKey` cast; ни `encoded`, ни exponent/scalar read, ни provider retry; software RSA regressions зелёные. Не менять портал/протокол |
| 3. Android import adapter без включения в UI | Новые `certificate/` import/store/capability компоненты; fake-based JVM/Robolectric tests | RSA import staging, policy, security-level verification, отказ/cleanup. Настоящее железо ещё не объявлено проверенным |
| 4. Lifecycle и явная migration | `CertificateRepository.kt`, `CertificateSession.kt`, `CertificateReferenceStore.kt`, `CertificateUnlockCache.kt` (только адресная инвалидизация), согласованные места UI выбора/forget | Crash-safe commit, tombstones, cancel/background/expiry и no silent fallback. UI строки во всех пяти локалях. Точное разделение файлов определить после ADR, не захватывать параллельные работы |
| 5. Ограниченное включение RSA signing | `SigningModule.kt`, `SigningCoordinator.kt:259`, `BatchSigningCoordinator.kt:174`, нативный signer wiring и тесты; browser TLS selection только после отдельного согласования | Consent/fingerprint binding сохранены; raw/CMS/XAdES/PAdES/tri-phase/batch positive/negative coverage. Аппаратное включение только после отдельной разрешённой физической приёмки |
| 6. EC backend и форматные маршруты | Новые native EC backend/tests; `SoftwareEc*` остаются software-only; loader/algorithms/контейнеры отдельными малыми PR | Согласованное расширение RSA-only policy; P-256/P-384, DER/r||s/OID coverage; StrongBox P-384 не обещается |
| 7. Wrapped import feasibility → отдельный opt-in | Trusted offline wrapper specification/tooling proposal, chain attachment spike, import adapter | Public API + RSA/EC wrapper interoperability доказаны, attestation и auth-SID policy рассмотрены; без нового сервера, личных ключей и plaintext fallback |

TLS-интеграция, если нужна, — самостоятельный дизайн/PR с WebView/Conscrypt key capabilities и consent; не побочный эффект этапа 5. Аналогично per-sign authentication имеет собственный дизайн задачи 1. Для каждого будущего этапа >20 production-файлов или изменения portal protocols/security-модели — сначала новый ограниченный план, не половинчатая активация.

## 7. Проверки и остающиеся риски

### Без эмулятора и локальной компиляции

В этом docs-only PR: `git diff --check`, проверка существования всех перечисленных source paths, review полного inventory private-key consumers и дешёвые Python tool tests; широкие CI/security — GitHub Actions на точном PR head. Новых source-text/SHA-pinning тестов нет. `prepare_task` отсутствует среди доступных MCP-инструментов; применён доступный `codex/research` через `skill_search`/`skill_read`.

Для реализации в CI, с синтетическими ключами, генерируемыми в тесте (не коммитить private fixtures):

- Fake key/backend, бросающий при export/scalar access, и контролируемый JCA test provider: доказать native dispatch и отсутствие retry; подпись должна реально верифицироваться, а не только проверять порядок вызовов. Реальный AndroidKeyStore provider JVM fake не заменяет.
- `LocalSignatureEngineTest`, `CadesSignerTest`, NativeCades tests: RSA SHA256/384/512 и только существующие разрешённые SHA1 маршруты, raw/CMS verification, wrong certificate, unsupported algorithm, пределы входа/выхода, cancellation, failure cleanup. Auth required/invalidation failures не понижают уровень защиты и не повторяют операцию.
- `SoftwareEcSignature*Test` / `SoftwareEcCades*Test`: opaque handles продолжают отвергаться без чтения секретов; новый native backend тестируется отдельно. P-256/P-384, DER scalar ranges, XML r||s и алгоритм identifiers не считать проверенными одним RSA test.
- JVM/Robolectric с fake Keystore/capabilities: API 26/27/28/30/31 gates, StrongBox feature без поддержки нужного ключа, подтверждённый TEE fallback/запрет downgrade, SOFTWARE/UNKNOWN, отмена auth, отсутствующая блокировка, invalidated key, повреждённая цепочка, wrong fingerprint, alias collision и security/storage errors.
- Repository state-machine tests: сбой перед/после каждого durable step, process death, orphan cleanup, delete failure/tombstone, restored metadata без alias, stale consent/snapshot, timeout не продлевается, пароль/PKCS12 копии не сохраняются. Существующие signing coordinator/adapter tests подтверждают неизменность boundaries.
- Wrapped негативные fixtures: corrupted ASN.1/tag/authorization list, wrong wrapping alias/security level/algorithm, invalid certificate association; отказ без host decrypt fallback. Не создавать Python-тесты, которые читают Kotlin как текст.

### Что сейчас не проверено и блокирует hardware claim

Никакие реальные ключи не импортировались, существующие alias/секреты не читались и не менялись. Не запускались локальные Gradle, сборка, compilation, emulator, instrumentation, установка APK или E2E. Будущая отдельно разрешённая физическая приёмка должна подтвердить хотя бы TEE RSA и StrongBox RSA-2048/P-256 на подходящих устройствах, unsupported RSA-4096/P-384, provider dispatch, chain association, auth/invalidation, batch latency/concurrency, delete/reinstall/reimport и отсутствие экспортируемого материала через public API. Её нельзя заменить зелёными JVM/CI или skipped jobs, и сейчас она **не выполняется**.

Открытые решения: набор поддерживаемых hardware algorithms по OEM, auth-bound policy и её совместимость с CMS/TLS/batch, безопасная chain attachment после wrapped import, PKCS12 recovery UX, точный storage schema после согласования multi-certificate. Самый существенный функциональный риск — выдать signing-only key общему identity/TLS пути; security-риск — назвать программный default fallback TEE или забыть, что original PKCS12 остаётся экспортируемым. Любой неподтверждённый capability отключает соответствующий новый маршрут, а не ослабляет прежние проверки.
