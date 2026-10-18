# Signing coordinator construction boundary

Base: `dfc4744599ab7fb39e2f9da385622480fe160acb`.

## Implemented scope

`MainActivity` delegates ordinary and batch coordinator construction to an internal `SigningModule`. The Activity continues to supply its existing certificate session, lifecycle scope, transport instances and live navigation/origin/page callbacks. The same adapter registries and default protocol IDs remain in use. Each coordinator still receives a separate local signature engine and expiry scheduler. The module does not retain an Activity, create an application scope or own lifecycle cancellation.

Three regression tests cover the existing ordinary/batch adapter order and resolver identities, supplied owner/callback identities, separate engines/schedulers, idle state and cleanup without cancelling the supplied scope. Test-only reflection observes private constructor dependencies; changing those internal names requires updating the fixture. These tests do not perform real signing, network or portal acceptance.

## Verification status

- Checksum-pinned cached detekt 1.23.8 passed on the changed Activity and new module with the unchanged baseline.
- Whitespace checks passed.
- Android compilation and the three new Kotlin tests have not been run locally. Canonical CI is required before merge; the cached-tool checks are not Android acceptance.
- Full Python regression execution is tracked separately in the parent continuation checkpoint.

## Remaining architecture work

This is only a construction extraction, not completion of AppContainer, CertificateModule, BrowserModule, Activity/ViewModel ownership migration or Compose NavHost. Signing state, expiry, protocol boundaries, consent, certificate ownership and cleanup implementations are unchanged. No feature capability, EC runtime/import/TLS path, catalog/origin contract, dependency, workflow or baseline expansion is introduced.

Codex generated the initial patch from the exact source bundle; the parent applied it and added the lifecycle regression. No separate review/audit pass was performed. Device, portal, real-certificate, E2E and final-APK actions were not performed.
