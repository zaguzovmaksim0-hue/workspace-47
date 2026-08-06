# Backup-domain exclusion hardening design

## Finding

The manifest intentionally sets `android:allowBackup="false"` and points both legacy
`android:fullBackupContent` and Android 12+ `android:dataExtractionRules` at explicit
backup-rule resources. Both resources currently exclude only `domain="root" path="."`.

Android backup treats `root`, `file`, `database`, `sharedpref`, `external`, and the
corresponding device-protected domains as distinct semantic domains. In AOSP they map to
distinct backup tokens/directories. Android 12+ device-to-device migration can ignore
`android:allowBackup="false"`, so the `dataExtractionRules` exclusions are the relevant
fail-closed boundary for D2D.

Junta Firma Mobile persists certificate-reference metadata through Preferences DataStore,
which lives under app `filesDir`, and QA diagnostics also use `filesDir`. A root-only
exclusion therefore does not establish the intended no-backup/no-transfer contract for the
`file` domain. The certificate PKCS#12 itself is not copied into app storage; this finding
concerns persisted metadata and any current/future backup-eligible app-domain files.

Public evidence checked 2026-08-06:

- Android Auto Backup documentation lists each supported domain separately and states that
  an exclude applies to the selected domain/path.
- Android 12 backup behavior documents that `allowBackup=false` can be ignored for D2D and
  that `dataExtractionRules` must define D2D exclusions.
- AOSP `FullBackup` maps XML domains to distinct backup tokens/directories.

## Scope

Strengthen only backup/transfer policy resources and their policy test. Preserve runtime
storage locations, certificate selection/persisted permission behavior, Android Keystore
unlock-cache storage under `noBackupFilesDir`, diagnostic content policy, release signing,
network/TLS and profile behavior.

Exclude `path="."` for every backup-eligible credential-protected and device-protected
application domain supported by the project minSdk/targetSdk:

- `root`
- `file`
- `database`
- `sharedpref`
- `external`
- `device_root`
- `device_file`
- `device_database`
- `device_sharedpref`

For Android 12+ apply the exact set independently under both `<cloud-backup>` and
`<device-transfer>`. Keep the Android 11-and-lower `full-backup-content` resource with the
same complete domain set. `noBackupFilesDir` and cache domains remain system-excluded and
are not valid/necessary custom include targets.

## TDD contract

Add a CI-policy test that parses both XML resources instead of relying on string presence.
It must first prove RED because the current resources contain only `root`. The test requires:

1. the legacy resource has exactly the complete domain set as exclusions, each with path `.`;
2. the Android 12+ resource has exactly `cloud-backup` and `device-transfer` sections;
3. each section has exactly the same complete exclusion-domain set, each with path `.`;
4. no `<include>` elements exist.

The production/configuration fix is only to expand those XML exclusion lists.

## Exact files

Policy/configuration:

- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

Test:

- `tools/tests/test_ci_policy.py`

Evidence after GREEN:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `docs/threat-model.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`
