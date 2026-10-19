# Application languages

The application declares Spanish (`es`), English (`en`), Catalan (`ca`), Galician (`gl`) and Basque (`eu`). Spanish remains the unqualified resource fallback. Product names, cryptographic algorithm identifiers and protocol identifiers are not translated.

On Android 13 and later, the operating system can expose the declared languages in its per-application language settings. On older supported Android versions, Android selects resources using the system language preferences. There is no custom in-app language selector or forced global locale override. Changing the application language can recreate the activity; do not change it during a pending portal operation.

Translations cover resource-backed application text. Remaining hardcoded text and text supplied by a portal, certificate, operating system or external application are outside that coverage. Choosing an application language does not translate a portal or change its signing, origin or compatibility policy.

## Maintaining translations

Keep names, resource types, format argument indexes/types, markup and plural contracts aligned with `app/src/main/res/values`. Never translate non-translatable resources. Preserve the meaning of consent, unavailable-data, privacy and security warnings; an identifier, challenge, digest or protocol envelope must not be described as verified document content.

The locale-specific Python tests check resource coverage and format/XML contracts. `test_app_locale_config.py` checks the manifest declaration and exact supported locale list. `LocalizedResourcesTest` exercises Android resource resolution and formatting on synthetic configurations in ordinary canonical CI. These checks do not establish native-speaker acceptance, real-device rendering, accessibility or portal compatibility.

Run the Python checks with:

```sh
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

Android compilation, resource processing, lint and resource-selection tests require the normal exact-head CI. Do not treat XML parsing alone as a successful Android build.

## Deterministic test language

Existing Robolectric UI/copy regressions assert Spanish wording and now explicitly select `es-rES` in test-only configuration, including display-size/night overrides. Their assertions remain unchanged. This avoids silently changing their subject when the host JVM defaults to English. Dedicated locale tests still create separate explicit English, Catalan, Galician and Basque contexts on API 28/33; production locale selection is not forced.
