# Minimal AppContainer composition root

Base: `8827cbd7f236cc89714165b97c8d6e8f457afe12` (local joint foundations candidate).
Branch: `refactor/app-container-20261019`.
Worktree: `~/.cache/w47-app-container-20261019`.

## Implemented boundary

- `AppContainer` eagerly invokes its certificate-module factory once and retains only the resulting immutable `CertificateModule` reference. Its default factory delegates directly to the existing `CertificateModule.create(application)` without changing that module's internals, constructor arguments, component order or Android owners.
- `JuntaFirmaApplication.onCreate` constructs the container at the former certificate-module construction point, after `clientCertPreferenceCoordinator`. It publishes the same session, unlock cache and gateway in the same order. All existing public/internal mutable properties and setter visibility remain unchanged; the new internal container property has a private setter.
- `createSigningModule(certificateSession, scope)` invokes the existing `SigningModule(certificateSession, scope)` constructor afresh on every call. There is deliberately no default session: `MainActivity` passes the current `app.certificateSession`, then its existing `lifecycleScope`, preserving test-injected session replacements and argument evaluation order.
- The container stores neither an Activity, scope, factory closure nor navigation callback. The supplied scope belongs only to the returned signing module. Transport creation, ordinary/batch construction and live callbacks remain in their existing Activity locations.
- `CertificateModule.kt` and `SigningModule.kt` production internals are untouched. `BrowserSecurityRegressionTest` changes only the source marker locating the moved construction call; every assertion and all real-object test behavior are preserved.

No DI framework, global service locator, production reflection, dependency, workflow, catalog, version, baseline or suppression is introduced. AppRoot and legal UI are outside this change.

## Executed local evidence

Evidence directory: `~/.cache/w47-app-container-checks-20261019`.

- `python -m unittest tools.tests.test_app_container_composition -v`: 4 source-wiring contracts pass. The certificate slice first failed because `AppContainer.kt` did not exist; the signing slice then failed because the Activity did not yet call the container. Logs: `red-certificate-source.log`, `green-certificate-source.log`, `red-signing-source.log`, `green-signing-source.log`. These are source-only RED/GREEN results, not Android runtime proof.
- Cached `kotlinc`/JUnit compiled the actual, unchanged `MonotonicSecurityTime` and `CertificateExpirationPolicy` production sources together with their two existing test classes: 21 tests pass. No generated substitutes or Android stubs were used. Logs, source hashes and compiler version are under `pure-jvm/`. This supporting policy evidence does not execute AppContainer, certificate Android factories or signing coordinators.
- Cached detekt 1.23.8, SHA-256 `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`, reports zero findings on AppContainer, Application, Activity and the new construction test with the existing unchanged baseline. Logs: `detekt-scoped.log`, `detekt-scoped.xml`, `detekt-scoped-checksum.log`.
- An additional invocation including the existing large `BrowserSecurityRegressionTest` reported 24 findings exclusively in that old test (large class, long/complex methods and long lines). That test is outside the repository script's production detekt inputs. No cleanup, baseline addition or suppression was made. The original invocation remains in `detekt.log`/`detekt.xml`; an exact source transformation check confirms the test differs from the base solely in its assembly marker.
- Final staged-file whitespace and scoped secret-snapshot outcomes are recorded in `handoff-checks.log`, `gitleaks.log` and `gitleaks.json` before the local commit. The parent should use the returned commit SHA and the final external exact-commit evidence rather than treating this document as Android acceptance.

## Prepared, not executed

`AppContainerConstructionTest` contains six Robolectric tests using the real existing modules: eager factory count/order and stable component identities, independent certificate graphs, fresh signing modules with current Application session overrides and separate supplied scopes, actual coordinator construction/cleanup isolation, compatible mutable Application injection hooks, and absence of stored scopes/factories. Cache/gateway doubles stay at the existing CertificateModule factory seams. Constructor-owner observations use test-only reflection, following the existing SigningModule regression fixture; there is no production reflection.

These six tests, the existing CertificateModule/SigningModule tests and the existing BrowserSecurityRegressionTest were not compiled or run locally. The Android dependency graph is not replaced by fake modules to manufacture a local result. Canonical Android compilation and runtime acceptance remain the parent's CI responsibility.

## Boundaries and continuation

The frozen integration worktree was not modified. No Codex execution, review/audit/self-review phase, push, PR or GitHub mutation was performed. No local Android Gradle/bootstrap/download, device, E2E, R8, portal, real-certificate/key or APK action was performed.

This is only a small composition root. BrowserModule, ViewModel ownership, navigation migration/Compose NavHost and the full DI roadmap remain incomplete. The parent owns integration and canonical CI on the integrated candidate SHA.
