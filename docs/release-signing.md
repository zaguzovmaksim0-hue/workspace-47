# Private release signing

`debug` and `qa` builds use the Android debug key and may expose QA-only portal profiles.
They are not distribution builds.

A `release` build never falls back to the debug key. Before building it, provide all four
values as Gradle properties or environment variables:

- `JFM_RELEASE_STORE_FILE`
- `JFM_RELEASE_STORE_PASSWORD`
- `JFM_RELEASE_KEY_ALIAS`
- `JFM_RELEASE_KEY_PASSWORD`

Example using environment variables:

```bash
export JFM_RELEASE_STORE_FILE=/absolute/private/path/junta-firma-release.jks
export JFM_RELEASE_STORE_PASSWORD='...'
export JFM_RELEASE_KEY_ALIAS='...'
export JFM_RELEASE_KEY_PASSWORD='...'
./gradlew :app:assembleRelease
```

The keystore and passwords must never be committed. `preReleaseBuild` depends on
`verifyReleaseSigning` and fails before packaging when the configuration is incomplete or
the keystore path does not exist.

Portal policy:

- `release`: only sensitive profiles with `VERIFIED_E2E` evidence and `ENABLED` activation.
- `debug` / `qa`: also permits `QA_ONLY` profiles for controlled testing.
