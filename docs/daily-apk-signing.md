# APK signing keys and the persistent daily channel

Canonical reference for the keys that sign Firma Mobile APKs: which key signs
the daily channel, where it is kept, how to obtain and verify a signed APK, how
to migrate an installation signed with another key, and how to restore or
rotate the key. Spanish user steps: [Instalación y primeros
pasos](instalacion.md#firma-del-apk-y-actualizaciones).

## Daily key at a glance

| Property | Value |
| --- | --- |
| Package | `dev.junta.firmamobile` |
| Produced by | `Signed daily APK` workflow (`.github/workflows/daily-apk.yml`), manual dispatch on `main` |
| Certificate subject | `CN=Firma Mobile Daily APK` (self-signed) |
| Certificate SHA-256 | `f1bcad77c90bab402ac9671b0ac6c034cc1d916d4561f65e480740021e5d6971` |
| Certificate SHA-1 | `3ebac06c7bf4a5f1cadccb205335f1f0ee830e0b` |
| Key | RSA 4096, SHA256withRSA; valid from 2026-10-18 to 2054-03-05 |
| APK signature schemes | v1, v2 and v3 (v4 disabled) |
| Public pin in Git | `config/signing/daily-cert-sha256.txt` |

The daily key only identifies the app package. It is not the user's PKCS#12
document-signing certificate and is never bundled into the app.

## Known signer identities

| Signer | Certificate SHA-256 | Signs | Can update the daily channel |
| --- | --- | --- | --- |
| Daily key, `CN=Firma Mobile Daily APK` | `f1bcad77…1e5d6971` (full value above) | Every artifact of `Signed daily APK` since 2026-10-18 | Yes, this is the channel |
| Legacy release key, `CN=Firma Mobile, OU=Release` (RSA 3072, created 2026-10-05) | `b5d187690ee2a17d8c0812ac24139ee480280f780118ce2aefb6121ff05550dd` | APKs signed by hand outside the workflows, for example the 0.2.15 (17) installation on the maintainer's device | No |
| Android debug keys (CI runners, local `~/.android/debug.keystore`) | Differs per machine | Raw Gradle `debug`, `qa` and `optimized` outputs, including ordinary CI artifacts | No, test artifacts only |

Android accepts an update only from the signer that installed the app.

## Where the daily key lives

The private key is deliberately absent from this repository, from workflow
artifacts and from releases. It exists only here:

1. **Maintainer custody (master copy).** Generated on 2026-10-18 in owner-only
   Termux storage on the maintainer's Android device, directory
   `~/.config/firmamobile-signing/daily-20261018/` (mode `0700`, files `0600`):
   - `daily.p12`: PKCS#12 keystore with the private key;
   - `credentials.json`: `store_password`, `key_alias` and `key_password`
     (plus `store_file`);
   - `certificate.der` and `certificate-sha256.txt`: public certificate and pin.
2. **GitHub environment `daily-signing`** (*Settings → Environments →
   daily-signing → Environment secrets*): `JFM_DAILY_KEYSTORE_BASE64` (the
   keystore in base64), `JFM_DAILY_STORE_PASSWORD`, `JFM_DAILY_KEY_ALIAS` and
   `JFM_DAILY_KEY_PASSWORD`. Secrets are write-only: they can be replaced or
   deleted but never read back or downloaded, so this is a deployment copy, not
   a backup.

To get the key, copy it from maintainer custody. No workflow, artifact or API
returns it, by design. Keep an **offline encrypted backup** of the whole
directory outside the phone, which is otherwise a single point of failure:

```bash
tar -C ~/.config/firmamobile-signing -cf - daily-20261018 \
  | gpg --symmetric --cipher-algo AES256 -o firmamobile-daily-key.tar.gpg
```

Store the `.gpg` file on removable media or another machine and its passphrase
in a password manager. Never commit, upload or paste the keystore, its
passwords or `credentials.json`.

The legacy release keystore stays in maintainer custody only to identify old
installations: `~/.local/share/firmamobile-signing/release-20261005/`
(`release.p12`, `store-password` and the public `release-cert.pem`). No
workflow uses it. Signing new APKs with it would recreate an update line that
the daily channel cannot replace; see [private release signing](release-signing.md).

## Getting a signed daily APK

Preconditions: the commit is on `main` and its `CI` and `Security` push runs
completed successfully. The workflow re-checks this and stops otherwise.
Dispatching requires write access to the repository.

GitHub web: *Actions → Signed daily APK → Run workflow → Branch: `main` → Run
workflow*. When the run is green (typically about 10 minutes), download the
artifact `firmamobile-daily-<commit-sha>` from the run summary. Artifacts
expire after 14 days; dispatch again to get a fresh copy. The artifact contains:

- `firmamobile-daily.apk`: the installable APK;
- `SHA256SUMS`: SHA-256 of the APK;
- `COMMIT.txt`: the exact source commit;
- `daily-certificate.txt`: the `apksigner verify --print-certs` report.

GitHub CLI:

```bash
gh workflow run daily-apk.yml --ref main
gh run list --workflow daily-apk.yml --limit 1   # note the run id
gh run watch <run-id> --exit-status
gh run download <run-id> --name firmamobile-daily-<commit-sha> --dir firmamobile-daily
```

APK artifacts of ordinary CI runs are signed with a disposable debug key. They
are test artifacts, not daily updates.

## Verifying an APK

```bash
cd firmamobile-daily
sha256sum -c SHA256SUMS
apksigner verify --verbose --print-certs firmamobile-daily.apk > cert-report.txt
grep 'certificate SHA-256 digest' cert-report.txt
```

Every printed digest must be
`f1bcad77c90bab402ac9671b0ac6c034cc1d916d4561f65e480740021e5d6971`. From a
checkout of this repository, the strict check that gates the workflow is:

```bash
python3 tools/verify_daily_apk_signature.py --report cert-report.txt \
  --expected config/signing/daily-cert-sha256.txt
```

`apksigner` ships with the Android SDK build-tools; in Termux install it with
`pkg install apksigner`. To inspect the app installed on a device, copy its
APK first:

```bash
adb shell pm path dev.junta.firmamobile   # package:/data/app/.../base.apk
adb pull /data/app/.../base.apk installed.apk
apksigner verify --print-certs installed.apk | grep 'SHA-256'
```

## Signer mismatch: one-time migration

Installing a daily APK over an installation signed by another key fails with:

- package installer: “App not installed as package conflicts with an existing
  package” (or the localized equivalent);
- `adb install` / `pm install`: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (“signatures
  do not match”).

The installed app was signed by the legacy release key, a debug key or another
key. Do not try to bypass Android's check. The app sets
`android:allowBackup="false"`, so its data cannot move across signers. Migrate
once:

1. Keep the original `.p12`/`.pfx` certificate file and its password outside
   the app.
2. Download and verify the daily APK as above.
3. Optionally confirm the installed signer as above.
4. Uninstall Firma Mobile (*Settings → Apps → Firma Mobile → Uninstall*, or
   `adb uninstall dev.junta.firmamobile`). This deletes imported certificates,
   saved certificate references, the selected region and all other app data.
5. Install `firmamobile-daily.apk`, open it and import the certificate again.

Afterwards every newer daily APK installs as an ordinary update. Android still
rejects a lower `versionCode` (`INSTALL_FAILED_VERSION_DOWNGRADE`).

The maintainer's device was migrated this way on 2026-11-02: 0.2.15 (17),
signed with the legacy release key, was replaced by 0.2.17 (19), signed with
the daily key.

## Trust boundary

- Only `main` dispatches may run; checkout uses the exact dispatch SHA.
- CI and security push workflows must have succeeded on that exact SHA.
- Private material is provided only to the signing step, after Gradle finishes.
- The `daily-signing` GitHub environment permits deployments only from `main`.
- There is no debug fallback: without the four environment secrets the
  workflow fails.
- Temporary runner keystore is removed on exit and by an always-run cleanup.
- APK verification requires v2, exactly one signer, and the public certificate
  SHA-256 in `config/signing/daily-cert-sha256.txt`; a mismatch blocks upload.
- Only the verified APK, checksums, commit and public certificate report are
  uploaded.
- Repository write/admin access is security-sensitive because trusted `main`
  workflows can use environment secrets.

## Restoring the GitHub secrets from maintainer custody

Needed when the environment is recreated, the repository moves or a secret was
overwritten. Run where the keystore is kept; values are piped, never echoed or
stored in shell history:

```bash
cd ~/.config/firmamobile-signing/daily-20261018
R=zaguzovmaksim0-hue/workspace-47
PW=$(jq -j .store_password credentials.json) keytool -list -v -storetype PKCS12 \
  -keystore daily.p12 -storepass:env PW | grep 'SHA256:'
# expected: F1:BC:AD:77:C9:0B:AB:40:2A:C9:67:1B:0A:C6:C0:34:CC:1D:91:6D:45:61:F6:5E:48:07:40:02:1E:5D:69:71
base64 -w0 daily.p12 | gh secret set JFM_DAILY_KEYSTORE_BASE64 --env daily-signing -R "$R"
jq -j .store_password credentials.json | gh secret set JFM_DAILY_STORE_PASSWORD --env daily-signing -R "$R"
jq -j .key_alias credentials.json | gh secret set JFM_DAILY_KEY_ALIAS --env daily-signing -R "$R"
jq -j .key_password credentials.json | gh secret set JFM_DAILY_KEY_PASSWORD --env daily-signing -R "$R"
```

Then dispatch the workflow; its pin check proves that the stored keystore and
`config/signing/daily-cert-sha256.txt` agree.

## Loss, compromise and rotation

Changing the daily key is an explicit, maintainer-authorized migration, never
an automatic recovery step.

- **Device copy and offline backup both lost:** the workflow keeps signing
  while the environment secrets stay intact, but they cannot be exported. Do
  not delete or overwrite them; plan a rotation.
- **Compromise suspected:** delete the four environment secrets immediately so
  nothing else can be signed, then rotate. Treat APKs signed with the old key
  after the suspected compromise as untrusted.
- **Rotation:**
  1. Generate a new keystore in owner-only storage and back it up offline
     first, for example `keytool -genkeypair -storetype PKCS12 -keystore
     daily.p12 -alias <alias> -keyalg RSA -keysize 4096 -sigalg SHA256withRSA
     -validity 10000 -dname "CN=Firma Mobile Daily APK"`.
  2. Replace the four environment secrets as described above.
  3. In one pull request, replace `config/signing/daily-cert-sha256.txt` and
     the fingerprints in this document, `README.md`, `README.en.md`,
     `docs/instalacion.md` and `CHANGELOG.md`.
  4. After merge and green CI/security on `main`, dispatch the workflow.
  5. Every existing installation then needs the one-time migration above. The
     workflow signs with a single signer and no APK Signature Scheme v3
     rotation lineage. A lineage rotation (`apksigner rotate`) would keep
     updates working on Android 9+ (the app's `minSdk` is 26), but it needs the
     old key and changes to `tools/verify_daily_apk_signature.py`; treat it as
     a separate engineering change.