# F-15B Catalog Generation Deduplication Design

## Scope

F-15B removes the remaining manually maintained copies in the profile/public-catalog pipeline without changing any portal trust, protocol, activation or E2E claim.

The current tree has two independent drift surfaces:

1. the complete site-profile catalog is committed both as `app/src/main/res/raw/site_profiles_v1.json` and as a large Kotlin string in `BuiltInSiteProfiles`;
2. `tools/generate_public_portal_catalog.py` separately hardcodes profile bindings and two supplemental public entries that are absent from the reviewed inventory.

## Chosen design

### Canonical site-profile source

Move the site-profile JSON to one non-packaged canonical file:

`config/site_profiles_v1.json`

Gradle reads this exact file through a tracked file-content provider and emits it as `BuildConfig.SITE_PROFILE_CATALOG_JSON`. `BuiltInSiteProfiles` parses that generated field. The old raw Android resource and handwritten Kotlin JSON body are removed.

This keeps the existing context-free `BuiltInSiteProfiles` API used by runtime code and JVM tests, avoids Android `Context` injection, does not add Python to the Android build, and does not package a second raw copy in the APK.

### Public-catalog generation

The reviewed Markdown inventory becomes the only source of public portal entries. Add the two previously supplemental surfaces to the inventory with stable IDs:

- `junta-andalucia-ofvirtual`;
- `educacion-convocatoria-46`.

The generator also reads `config/site_profiles_v1.json`. It binds a public entry to a runtime profile only when the profile `startUrl` exactly equals one and only one inventory `entry_url`. It fails closed when:

- profile JSON is malformed or has unexpected root/profile keys needed by the binding layer;
- profile IDs or start URLs are duplicated;
- a runtime profile has no inventory entry;
- a runtime profile matches more than one inventory entry;
- two profiles resolve to the same inventory surface.

The hardcoded `PROFILE_BINDINGS` and `_supplemental_entries()` are deleted.

## Data and security invariants

- Runtime trust still comes only from the versioned site-profile catalog.
- Inventory metadata never grants origins, endpoints, adapters, capabilities or activation.
- Exact URL equality is required; no host, prefix, redirect or fuzzy-name matching is permitted.
- All seven current runtime profiles must map to exactly seven reviewed inventory surfaces.
- Public E2E metadata remains subject to the existing F-15A consistency gate.
- No portal request, authentication, certificate operation or real E2E flow is performed.

## Alternatives rejected

1. **Load `res/raw` through Android `Context`.** This removes the Kotlin copy but introduces application-context coupling into security policy singletons and unit tests.
2. **Generate a Kotlin source file with a custom Gradle task.** Correct but more build plumbing than a tracked BuildConfig field and still requires generated-source lifecycle management.
3. **Keep supplemental records in Python but move them to another data file.** This relocates rather than removes the duplicate public-entry source.

## Testing

- RED/GREEN Python tests prove the generator requires and consumes the canonical profile catalog, maps all profiles by exact start URL, rejects missing/duplicate mappings, and reproduces the committed public catalog byte for byte.
- A repository-policy test proves there is one committed site-profile JSON, no old raw copy and no embedded Kotlin JSON body.
- Android JVM tests prove `BuiltInSiteProfiles` parses the generated BuildConfig value and all profile/public consistency tests remain green.
- Full Debug/QA unit, lint, APK, AndroidTest APK, Python, Go, artifact and forbidden-canary gates are rerun before commit.
