# Runtime dependency license audit

**Status:** pre-publication source audit; binary-release verification remains required

**Reviewed publication baseline:** `oss/publication-readiness-20260811`

This document separates two different compliance questions:

1. **Publishing this repository's source code.** The reviewed Android/Maven/Python dependencies are resolved externally and are not copied into this repository as project source. Their upstream licenses therefore remain their own and are not replaced by a future Junta Firma Mobile project license.
2. **Distributing an APK/AAB or other binary.** Runtime libraries may be incorporated into the produced application. Before any binary release, the exact final dependency graph and packaged artifacts must be inspected and all required copyright, license and NOTICE material must be preserved or reproduced as required by each upstream license.

This is an engineering provenance/compliance inventory, not a legal opinion or a substitute for inspecting the exact final artifacts.

## Reviewed Android runtime families

The repository's locked runtime graph currently includes the families below. Versions shown here reflect the reviewed lock/catalog state and must be revalidated after the final synchronization with autonomous development.

| Family / locked component | Reviewed version(s) | Upstream license identified | Source-publication treatment | Binary-release treatment |
| --- | --- | --- | --- | --- |
| AndroidX / Jetpack / Compose | AndroidX/Compose versions from the current version catalog and lockfile | Apache-2.0 | External dependency; no source relicensing blocker identified | Preserve required Apache-2.0 license/NOTICE material for incorporated artifacts |
| Kotlin stdlib | 2.3.10 | Apache-2.0 | External dependency; no source relicensing blocker identified | Verify exact artifact metadata and preserve required Apache-2.0 material |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | External dependency | Verify packaged modules and preserve required material |
| kotlinx.serialization | 1.7.3 | Apache-2.0 | Transitive external dependency | Verify packaged modules and preserve required material |
| OkHttp | 5.4.0 | Apache-2.0 | External dependency | Verify packaged modules and preserve required material |
| Okio | 3.17.0 | Apache-2.0 | Transitive external dependency | Verify packaged modules and preserve required material |
| Bouncy Castle Java | 1.84 (`bcprov`, `bcpkix`, `bcutil`) | MIT | External dependency | Include/preserve the applicable copyright and MIT permission notice for distributed components |
| Apache Santuario XML Security | 3.0.6 | Apache-2.0 | External dependency | Preserve applicable Apache-2.0 license and NOTICE material |
| Apache Commons Codec | 1.18.0 | Apache-2.0 | Transitive external dependency | Preserve applicable Apache-2.0 license and NOTICE material |
| Woodstox Core | 6.5.1 | Apache-2.0 | Transitive external dependency | Preserve applicable Apache-2.0 material |
| Stax2 API | 4.2.1 | BSD-2-Clause | Transitive external dependency | Preserve the BSD copyright/conditions/disclaimer required by the distributed artifact |
| Jakarta XML Binding API | 3.0.1 | BSD-3-Clause / Eclipse Distribution License family | Transitive external dependency | Preserve the exact artifact's license/notice terms; verify final artifact metadata before release |
| Guava `listenablefuture` | 1.0 | Apache-2.0 | Transitive external dependency | Verify exact artifact metadata and preserve required Apache-2.0 material |
| JetBrains annotations | 23.0.0 | Apache-2.0 | Transitive external dependency | Verify final packaged presence and preserve required material if distributed |
| JSpecify | 1.0.0 | Apache-2.0 | Transitive external dependency | Verify final packaged presence and preserve required material if distributed |
| SLF4J API | 1.7.36 | MIT | Transitive external dependency | Preserve the applicable MIT copyright/permission notice if distributed |

## Tooling-only dependency

`tools/requirements.txt` currently pins `PyYAML==6.0.3`. The canonical upstream repository identifies PyYAML as MIT-licensed. It is a development/tooling dependency rather than Android project source and is not relicensed by the project's future root license.

The Go relay module currently has no third-party module requirements recorded in `ws024-relay/go.mod`; re-audit if that changes.

## Publication conclusion

For the **reviewed source tree**, no dependency family above was identified as requiring the Junta Firma Mobile project source itself to be relicensed under that dependency's license. The dependency inventory therefore does **not** remain a hard blocker to making the source repository public once the independent project-source, asset, secret-scan and final-synchronization gates are satisfied.

This conclusion does not authorize an APK/AAB release without additional work. Binary redistribution remains gated on the exact final dependency graph and exact artifact contents.

## Mandatory binary-release gate

Before distributing any release APK/AAB:

1. Regenerate/verify the final `releaseRuntimeClasspath` dependency lock from the exact release candidate.
2. Inspect the exact resolved AAR/JAR metadata (`META-INF/LICENSE*`, `META-INF/NOTICE*`, POM license data, and any component-specific attribution files).
3. Produce a release third-party notice bundle from those exact artifacts rather than relying solely on this family-level table.
4. Preserve Apache NOTICE material where the distributed upstream artifact contains applicable NOTICE text.
5. Preserve MIT/BSD copyright and license notices for incorporated components as required by their licenses.
6. Reconcile duplicate/transitive notices without deleting required attribution.
7. Verify the notice bundle against the final APK/AAB contents before release approval.

## Revalidation triggers

Repeat this audit when any of the following occurs:

- `gradle/libs.versions.toml` changes;
- `app/gradle.lockfile` changes;
- a new runtime dependency is added;
- `ws024-relay/go.mod` gains a third-party requirement;
- a binary release candidate is prepared;
- the OSS publication branch is synchronized with a newer autonomous-development head.
