# Private release signing

`debug`, `qa`, and `optimized` builds use the Android debug key and may expose QA-only portal profiles.
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

## Public distribution checkpoint (2026-10-05)

The daily-use `optimized` variant is non-debuggable but still uses the debug
signing configuration, enables QA profiles, and disables R8. It is not the
public `release` candidate. See [the distribution review](public-distribution-review-2026-10-05.md).

Creating or configuring a new release key requires explicit authorization.
Do not replace the existing installed app signer as part of documentation work;
a new signer needs a deliberate update/migration plan to protect existing data.
