# Private release signing

Firma Mobile APKs can carry three kinds of signatures. Only the daily key
updates installed daily-use apps:

| Identity | Used by | Notes |
| --- | --- | --- |
| Android debug key | Gradle `debug`, `qa` and `optimized` outputs, ordinary CI | Disposable and different per machine; test artifacts only |
| Persistent daily key, `CN=Firma Mobile Daily APK` (SHA-256 `f1bcad77…1e5d6971`) | `Signed daily APK` workflow, which re-signs the tested `optimized` payload | The daily-use channel; see [daily APK signing](daily-apk-signing.md) |
| Private release key (`JFM_RELEASE_*`) | Local `release` builds only; no workflow has it | See below |

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

The same names can live in a private `~/.gradle/gradle.properties` (mode `600`);
`keystore.properties.example` shows the format. Gradle does not read a
`keystore.properties` file.

The keystore and passwords must never be committed. `preReleaseBuild` depends on
`verifyReleaseSigning` and fails before packaging when the configuration is incomplete or
the keystore path does not exist.

## Legacy release key

The private release keystore created on 2026-10-05 (`CN=Firma Mobile, OU=Release`, RSA 3072,
certificate SHA-256 `b5d187690ee2a17d8c0812ac24139ee480280f780118ce2aefb6121ff05550dd`) is kept
in maintainer custody at `~/.local/share/firmamobile-signing/release-20261005/` on the
maintainer's device (`release.p12`, `store-password` and the public `release-cert.pem`). It
signed APKs built by hand outside the workflows, such as the 0.2.15 (17) installation on the
maintainer's device. Daily APKs cannot update those installations; they need the
[one-time migration](daily-apk-signing.md#signer-mismatch-one-time-migration). Do not sign
daily-use builds with this key. A public `release` channel needs its own explicit decision
about which key it uses.

Portal policy:

- `release`: the maintainer explicitly opted to retain the existing unverified profiles on 2026-10-05. `ALLOW_UNVERIFIED_PROFILES=true` enables them without enabling `ALLOW_QA_PROFILES` diagnostics. Their evidence states are unchanged and the UI discloses unconfirmed compatibility. Disabled profiles remain disabled. The strict `releaseRegistry` remains available as a separate policy.
- `debug` / `qa`: also permits `QA_ONLY` profiles for controlled testing.

## Public distribution checkpoint (2026-10-05)

The daily-use `optimized` variant is non-debuggable, enables QA profiles and disables R8.
Gradle signs it with the debug configuration; the `Signed daily APK` workflow then replaces
that signature with the persistent daily key. It is not the public `release` candidate. See
[the distribution review](public-distribution-review-2026-10-05.md).

Creating or configuring a new release key requires explicit authorization.
Do not replace the existing installed app signer as part of documentation work;
a new signer needs a deliberate update/migration plan to protect existing data.