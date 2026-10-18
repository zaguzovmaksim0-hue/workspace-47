# Dependency update status

## Compile-only API 37 / OkHttp 5.5.0 candidate

The previous OkHttp PR #261 failed `checkOptimizedAarMetadata`, not an application
unit test: `okhttp-android:5.5.0` declares `minCompileSdk=37`. Its AAR was retrieved
again from Maven Central; SHA-256 matches the committed verification entry:
`6c7fd12f092e64ca2eae0b8a8023900c0d7ffec57888cf8abc8085c6f42e1dcc`.
All ten added artifact checksum entries were independently checked against Maven
Central. Dependency verification and strict runtime locks remain enabled.

The replacement candidate starts from current main, imports the dependency lock,
verification and legal-bundle changes from #261, and changes only the compile API
to 37. The SDK manager package is `platforms;android-37.0`; all build workflows
install it. AGP 9.3 supports API 37. `targetSdk=36`, `minSdk=26`, permissions and
application consent/trust policies are unchanged. API36 emulator configuration is
not a compile platform declaration and remains unchanged.

Sources:
- [Old failing CI](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/39065847071)
- [AGP 9.3 compatibility](https://developer.android.com/build/releases/past-releases/agp-9-3-0-release-notes)
- [API37 setup](https://developer.android.com/about/versions/17/setup-sdk)
- [SDK package index](https://dl.google.com/android/repository/repository2-3.xml)

This change has no E2E acceptance claim. Use the exact PR-head ordinary CI and
security checks for integration; do not run real portal authentication or final
permanent-key distribution as part of dependency maintenance.

## Compose BOM 2026.09.00 candidate

The replacement for #260 starts from main after #370, retaining OkHttp 5.5.0
and Okio 3.18.1. It merges the generated Compose 2026.09.00 lock graph and
reviewed artifact evidence from #260, preserves the four current HTTP-stack
modules and their license evidence, and regenerates the legal index. Strict
Gradle resolution and legal verification in CI must confirm the combined graph.

All 63 added SHA-256 entries were independently checked by downloading their
artifacts from Google Maven. Gradle module metadata resolves Android variant
logical names (for example `animation.aar`) to their actual published URLs.
Of the 21 checked AARs, 13 require compile API37 and eight require API34. The
compile prerequisite was merged in #370; no new SDK or runtime-policy change
is needed here. Material3 remains 1.4.0; the core Compose runtime family becomes
1.12.1, with regression checks for the reviewed BOM/runtime combination.

The previous failure was an AAR metadata gate, not evidence of passing UI/E2E
behavior: [old CI](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/39065930277).
No temporary metadata-generation workflow, verification bypass, final signing
or device installation is included. The current no-E2E restriction still applies.

## Compose Compiler 2.4.21 candidate

The replacement for #259 starts from main after #371. Metadata was generated in
an isolated temporary read-only CI workflow, then all 73 added SHA-256 entries
were independently verified by downloading their files from Maven Central.
No existing checksum was removed or changed. The temporary workflow is removed
before integration; canonical CI still enforces strict dependency verification.

Compose Compiler brings Kotlin build dependencies to 2.4.21. The only runtime
lock change is kotlin-stdlib 2.3.10 to 2.4.21 across debug, qa, optimized and
release. The old jdk7/jdk8 compatibility stubs remain unchanged. Fresh optimized
and release license inventories have identical modules: only the stdlib row
changes, with its Apache-2.0 POM evidence. The legal index is regenerated;
release_approved stays false and this is not a legal/distribution approval.

Debug, qa and optimized Kotlin compilation passed in the isolated generation
run: [metadata evidence](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/39132590967).
Release signing is not bypassed there: release compilation/build and unit/lint
validation belong to canonical CI with its disposable CI key. Exact-head CI and
security must pass before integration. No E2E, permanent-key release or device
installation is included. Regression checks bind compiler, runtime lock and
vendored evidence so a later version edit cannot silently retain old stdlib.

## Remaining open work (not included)

| PR | Observed blocker / disposition |
| --- | --- |
| #259 Compose compiler 2.4.21 | Replacement #372 described above; original stays open until exact-head CI/security pass and the replacement merges. |
| #249, #311, #314, #317, #319 | QA/real-E2E control and portal recipes. Deferred under the current no-E2E instruction; neither merged nor closed by this dependency candidate. |
| #369 authenticated saved unlock | Separate draft awaiting physical authenticated-Keystore acceptance; not silently included or marked complete here. |

The outstanding PR list is not proof that their changes are absent from every
later branch. Inspect each diff against current main before integrating it.

## WebKit 1.17.1 stable candidate

This isolated update starts from main `5cb79e778aa8ffaf633d9123abb0607d8f97fe9a`.
Google Maven metadata lists 1.17.1 as the newest stable version; 1.18.0-rc01
is deliberately not selected. Upstream 1.17.1 removes Kotlin from the library
to address compiler compatibility. The 1.17.0 API removals/deprecations were
checked against app sources; no affected API use was found.

Primary sources:
- https://developer.android.com/jetpack/androidx/releases/webkit
- https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/maven-metadata.xml
- https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.17.1/webkit-1.17.1.module

The AAR, Gradle module metadata and POM were downloaded independently from
Google Maven and SHA-256 pinned additively; old verification entries remain.
The AAR requires minSdk 24, compile SDK 33 and AGP 7.2.0, below this app's
existing minSdk 26, compile SDK 37 and AGP 9.3.1. No SDK change is included.
The API/runtime module variants declare annotation 1.8.1,
annotation-experimental 1.3.0, core 1.1.0 and jspecify 1.0.0; all are already
covered by equal or newer runtime locks. Canonical CI must validate actual
resolution and packaging rather than treating this metadata inspection as a
resolved-graph proof.

Only WebKit changes in the four runtime lock configurations. Its vendored
license row was regenerated from the downloaded AAR/POM using the repository
collector in an isolated evidence directory, then the legal index was rebuilt.
Embedded notice bytes are unchanged. This is not a fresh full Gradle cache
inventory: optimized/release CI inventory comparison remains required.
`release_approved` remains false. No verification bypass, workflow change,
E2E, portal acceptance, permanent-key signing or device installation is included.

The policy regression first failed on the unchanged dependency (three failures
out of five tests), then passed with the candidate. Fourteen focused policy,
legal-bundle and collector tests pass locally. Full Gradle unit/lint/build,
strict verification, resolved license evidence and security gates belong to
exact-head canonical CI before integration.
