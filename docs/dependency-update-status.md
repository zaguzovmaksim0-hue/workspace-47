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

## Remaining open work (not included)

| PR | Observed blocker / disposition |
| --- | --- |
| #260 Compose BOM 2026.09.00 | Replacement candidate described above; original PR remains open until the replacement passes exact-head CI/security and is merged. |
| #259 Compose compiler 2.4.21 | Previous CI rejects unverified plugin-marker POM; temporary metadata workflow is still in its diff. Requires reviewed metadata generation and Kotlin/AGP compatibility checks; never disable dependency verification to merge it. |
| #249, #311, #314, #317, #319 | QA/real-E2E control and portal recipes. Deferred under the current no-E2E instruction; neither merged nor closed by this dependency candidate. |
| #369 authenticated saved unlock | Separate draft awaiting physical authenticated-Keystore acceptance; not silently included or marked complete here. |

The outstanding PR list is not proof that their changes are absent from every
later branch. Inspect each diff against current main before integrating it.
