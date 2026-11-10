# Fable remediation before E2E — 2026-11-10

## Scope and evidence boundary

This is the local implementation and verification record for the six-stage Firma
Mobile remediation plan. Work starts from `c66be8288a17479911ea7d71381cd387a9e98392`
and is assembled on `fix/fable-completion-integration-20261110`. The inspected
production/test source is `819e0abe6900298fed398edbe1608b5db2fe3c71`.
It passed the final targeted QA run; the following report update changes only
documentation. Commit references below identify local changes, not published
releases.

The owner explicitly deferred E2E. No real-site E2E, emulator launch, personal
certificate operation, APK installation, publication, PR merge or workspace
cleanup was performed in this remediation session. A successful certificate
challenge or login is not evidence of a successful signature or result delivery.
The intended outcome remains broad portal compatibility, not a guarantee for
every Spanish public-administration website.

## 1. Fix session mode and the test baseline

**Implemented.** The ordinary catalog tap resolves the exact active bundled
profile/start-URL binding and opens that profile. An unavailable profile or a
public address opens a separate universal session with `profileId = null`.
Explicit universal recovery remains available for profiled catalog entries.
The page and an unsupported AutoFirma request cannot silently switch the selected
session mode or borrow another profile. These changes are in `4662cdf3` and
`479fb4de`; catalog, navigation and fallback regression coverage was updated.

**Verified locally.** Source and independent review confirm that launch authority
comes from the registry and bundled binding, not mutable catalog presentation
flags. Universal sessions do not adopt a profile merely because the destination
matches one. The final QA/Robolectric run includes the catalog, MainActivity root
navigation and browser-session regressions. This is local UI/controller evidence;
it does not establish acceptance by a live portal.

**Deferred acceptance.** Real E2E must exercise the ordinary profiled launch and
explicit universal launch separately. Each result must distinguish authorization,
certificate selection, signature creation, delivery back to the site, and error
handling. No live portal is newly marked successful by this work.

## 2. Strengthen the universal AutoFirma engine

**Implemented.** Public navigation admits valid HTTPS ports, including `8443` and
`4443`, and provides a validated HTTP-to-HTTPS upgrade. Port-sensitive AutoFirma
requests and client-certificate confirmations retain their full origin. A
host-only credential scope is not widened by the navigation parser. Relevant
changes include `5e409330`, `29014ffd`, `48e1fad9` and `12bf689f`.

The native AutoFirma HTTPS transports can complete an incomplete server chain
using narrowly matched bundled intermediates. The platform trust manager still
validates the completed chain, and the HTTP client retains hostname validation.
The bounded path search has no arbitrary-certificate acceptance. This change
applies to native transports: **WebView SSL errors still call `handler.cancel()`**.
It does not claim to repair every incomplete chain encountered by WebView.

Local PAdES now has one coherent budget through URI parsing, consent preview,
signing, byte-range/history checks, result encoding and relay: 8 MiB input,
10 MiB PDF output, 12 MiB configuration/plaintext, 16 MiB URI/result characters,
and 17 MiB retrieval wire data. Base64 decoding avoids a second re-encoded
whole-document copy. PDF byte-range hashing uses a 16 KiB buffer, the output
buffer has a hard cap, and PDFBox uses its byte-array loading path with a 16 MiB
scratch limit. CAdES, XAdES, batch, remote/triphase and Android external-intent
limits remain separately bounded. See [document budgets](../afirma-payload-budgets.md)
for the exact scope and memory limitations. The payload change is `78a4dc21`,
followed by helper refactors `aea2d579` and `4d356d69` (original worker commits
`dc359de7` and `e70a5c0a`).

Unverifiable iframe requests now have an explicit refusal. The universal browser
can forward top-document-owned iframe assignments through a WebMessage bridge
that checks browser-provided source origin, main-frame status, live WebView,
navigation epoch and document identity before offering the existing consent flow.
The official AutoScript `createAttribute`/`setAttributeNode` sequence is covered,
as is the supported no-namespace `setAttributeNodeNS` case. Foreign-document
frames do not gain this authority (`23605cc4`, `2e980f92`, `27c15a67`).

External AutoFirma delivery returns typed, localized refusals for indirect
retrieval, desktop WebSocket, unsupported certificate filters and constraints,
malformed requests, and attempts to suppress confirmation. No fake browser
origin is created. External `fileid` and a desktop loopback service remain
disabled. The [external protocol boundary review](2026-11-10-external-autofirma-protocol-boundary.md)
records the separate authorization and protocol work required before enabling
those paths (`e414ec37`, `cbd968e5`). Browser-delivered desktop WebSocket
requests also reach a precise localized refusal (`819e0abe`, original
`260272df`). Recognition of this known protocol creates no signing operation and
starts no loopback listener; unknown and malformed requests keep their guards.

**Verified locally.** The final engine suite passes 174/174 under a 256 MiB JVM
heap. It includes a synthetic eight-page PDF above 7 MiB: parsing, preview-byte
access, signing with a fresh software test identity, verification, reopening and
wire roundtrip. Relay and frame tests exercise synthetic JavaScript environments.
This is not a measurement of the Android app's peak heap or native PDF renderer.

**Deferred acceptance.** Representative out-of-catalog websites still need
separate authorized login/sign/delivery checks. Device memory and responsiveness
must be measured with normal scanned and multipage PDFs. External Chrome
`fileid`, additional certificate filters and desktop WebSocket support need the
reviewed models described above; none is inferred from a URI handler alone.

## 3. Problematic portals

**Soria.** The actual fix from [PR #628](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/628),
original `3d6f3d32`, is imported as `3413fd38`. It activates the selected profile
when its start URL is the client-auth source. Profile regression coverage exists;
real authorization and any signature scenario remain unverified in this session.

**Lugo.** The form-body correction from
[PR #630](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/630), original
`4f0de857`, is imported as `9fd86d14`. Protocol parameters are sent in the POST
form body. `04d78990` preserves sanitized pre-sign/post-sign HTTP status in
diagnostics. The real presign/sign/postsign sequence and site acceptance of the
returned result still require E2E; the presence of logging is not that evidence.

**Formentera.** Scripted popup requests now require native confirmation before
Chromium's pending window handoff. They remain bound to the original owner and
epoch, with the original 15-second attachment deadline. The child receives no
profile authority (`66d57c99`, with admission clarification `d571cadf`). Ordinary
URI navigation preserves repeated query text such as `atributo`; this does not
relax the distinct reviewed profile constraints. Popup completion and the exact
Formentera session/parameter contract remain acceptance work.

**Castilla y León and Izenpe.** A regression covers main-frame POST and subresource
POST/XHR reaching the actual TLS callback without replaying a request, using a
certificate before confirmation, or creating profile authority (`41640a54`).
This protects the shared interactive consent path. No new Izenpe-specific POST
policy is activated, and neither portal is claimed to authenticate successfully.
The earlier [own-TLS recipe review](2026-11-07-wave-b-own-tls.md) describes
profile/recipe limitations; those limitations are not a blanket statement that
the separate universal interactive TLS path is unavailable.

**Funciona.** [PR #629](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/629)
is deliberately not imported. Its request-side client-TLS pins do not establish
a complete authenticated terminal return. The configured destination is
`https://sede.funciona.gob.es/signin` with an OIDC fragment, while the current
`REDIRECT_AFTER_SOURCE` parser rejects return constraints. The required
post-certificate redirect shape and original OIDC-state/browser-storage binding
are missing; relaxing one fragment check would not supply them. The
[terminal-return blocker and design](../autonomous/2026-11-10-funciona-terminal-return-blocker.md)
and three boundary tests are imported as `33dbdb9a` from `1da0b9d2`, without
changing the production profile.

**Other portals.** Existing specific failure reasons remain valid unless new
evidence supersedes them. This change does not add dozens of individual adapters
or claim that every catalog entry is repaired. New portal policy requires a
reviewed source, request and return contract rather than a guessed exception.

## 4. Reduce architectural debt in independent changes

**Implemented.** Common operation-admission checks are centralized in
`BrowserOperationState` without merging ownership phases (`624ac6dd`). Popup
confirmation and ownership, frame-source checks, bounded decoding, certificate
path search and PDF budget validation have small separate helpers. These are
limited extractions with regression coverage; BrowserScreen is still large and
has not been rewritten wholesale.

The detekt baseline audit at `12bf689f` found 167 obsolete exemption IDs. Commit
`e0b64bb2` removes those IDs: **1693 to 1526, with zero additions**. This is cleanup
of stale suppressions, not a claim that 167 live defects were fixed. Newly
introduced detekt findings were addressed in code rather than added to the
baseline.

**Verified locally.** The baseline count and zero-addition diff were checked.
Full standalone detekt against the final production source completed with exit 0
and no new findings (`job_20261110_122121_ad0f3273`).
`AppNavigationState`, `AppNavigationRoutes` and the browser-session registry are
used by current MainActivity navigation, so they and their guards are retained.
The original review's suggestion of unused navigation code does not justify
deleting this active implementation.

**Remaining.** Continue decomposing BrowserScreen only at independently testable
boundaries. The remaining baseline entries and profile/adapter commonality need
actual code review and targeted changes, not blanket baseline regeneration.

## 5. CI, dependency updates and APK release

**Implemented.** `9f3fa8ca` splits release work from debug/QA/optimized checks into
independent 35- and 45-minute jobs. The required status name
`Android unit, lint, APK` is retained as an aggregate that succeeds only when
both prerequisites succeed. Existing unit/lint/APK, lock, legal-evidence and
fail-closed signing gates remain. QA APK compilation precedes synthetic emulator
startup. Ordinary push/PR checks do not enable emulator or real-site E2E jobs.
The timeout-only [PR #631](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/631)
is superseded locally by this split.

**Verified evidence.** All PRs #628–631 and Dependabot #569–573 were rechecked
read-only on 2026-11-10 and remain open/unmerged. Their historical runs do not
certify this local integration head:

- #628 has six successful ordinary checks; its emulator job is skipped.
- #629's [Android job](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/42674485808/job/129352491163)
  explicitly exceeded the 45-minute limit.
- [#630's run](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/42674013219)
  and [#631's run](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/42674172691)
  failed; complete Android evidence was not present in the returned job lists.
- The [November 10 nightly](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/42794999116)
  and [November 9 nightly](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/42596813632)
  booted their emulators in approximately 49 and 54 seconds, then failed later
  during `connectedQaAndroidTest`. A cold-boot timeout is not established as the
  cause. Prebuilding reduces resource overlap; it has not yet proved a repair.

Each Android log for [WebKit #569](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/569),
[AGP #570](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/570),
[OkHttp #571](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/571),
[Gradle #572](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/572) and
[DataStore #573](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/573)
shows missing verification metadata for new artifacts. #569 also has legal
inventory/version assertions to reconcile. Its OSV-labelled job stops at runtime
lock verification before scanning, so that failure is not evidence of a CVE.
The updates need independently checked artifacts, matching locks/checksums/legal
inventory, API review and their own CI; verification was not disabled.

`version.properties` and the installed app read-back agree on **0.2.21 / code 23**.
The Spanish and English READMEs now point to the source of truth instead of the
stale 0.2.17 claim. No version bump or APK signer re-verification is claimed.

**Remaining.** Run canonical GitHub Actions for the final published candidate
once publication is authorized. A release must use the existing trusted
[main-only daily signing process](../daily-apk-signing.md) and original daily
key. Installing over the existing app, preserving data and retaining access to
stored certificates are separate acceptance checks that have not been run.

## 6. Inventory and preserve the workspace

**Completed.** The initial saved inventory records 94 worktrees and 394 local
branches, including the active remediation workers. A final count records
94 worktrees and 395 branches after the separate WebSocket-refusal branch was
created; the original engine branch was preserved. Dirty work was identified.
Important `w47-fixsign` material was copied without changing the original tree:
the local WebKit JAR was hash-checked after copying, and committed/working patches
and a manifest were retained. A conservative candidate list for later cleanup
was prepared.

**Deferred.** No worktree, branch, log or experiment was deleted or reset. The
candidate list still needs review of ignored artifacts and separate owner
permission before deletion. The complete inventory, audit logs and backups stay
outside tracked Git content; this report does not replace those preserved files.

## Local verification ledger

These are separate scoped runs, not additive totals. Some suites overlap, and
smaller earlier runs must not be added to later expanded runs.

- Final targeted QA Gradle/Robolectric run: **532/532 in 40 classes**, with
  zero failures, errors or skipped tests (`job_20261110_122132_e7adf28a`). The
  command was `:app:testQaUnitTest` with an explicit class filter for the changed
  JVM-test files, `--no-daemon --console=plain --max-workers=2`. All 40 requested
  classes produced JUnit XML. Main QA and JVM-test source compilation succeeded;
  this task did not run Android instrumentation or build/install a release APK.
  Included examples are MainActivity external-entry 9/9, root navigation 20/20,
  WebView client 69/69, large-PDF 4/4, catalog default launch 8/8 and Lugo adapter
  8/8. These examples are part of the 532 total, not additional tests.
- Final engine suite: **174/174**, 256 MiB JVM heap, retained run
  `job_20261110_114601_63b83c60`; this supersedes the earlier 124-test engine run
  for reporting scope. No live endpoint or personal certificate was used.
- Relay JavaScript: **21/21**, including a result above the former relay cap and
  refusal above the new cap (`job_20261110_110118_d0fc4ab3`).
- Root's focused CI/frame Python run: **10/10**. The frame fixture includes
  **13 JavaScript scenarios**, not 13 additional Python tests.
- Frame source/document guards: **7/7** standalone JVM tests.
- CI policy/split-job checks: **34/34**, including execution of the actual
  aggregate Bash over 25 prerequisite combinations. Those combinations are
  assertions within the suite, not 25 additional tests.
- External controller/parser: **43/43** after the typed-result refactor
  (`job_20261110_112014_d750094a`); parser coverage overlaps other runs.
- Locale checks: **50/50**; all five new external-refusal resource XML files
  also compiled with the verified local `aapt2`.

The initial integration QA run at `33dbdb9a` executed 505 tests: 490 passed
and 15 failed. Fourteen relay tests failed before their bodies because host
Conscrypt required glibc's `libpthread.so.0`, unavailable in Termux. The relay and
URL-policy tests now explicitly disable that optional host provider; their
transport is fake or no TLS connection is involved. Production TLS settings are
unchanged. The other failure was an obsolete catalog fixture classifying valid
HTTPS port 444 as unsafe. The corrected fixture checks out-of-range port 65536
and separately proves that 8443/4443 aliases stay universal without activating an
unrelated profile. The relay size regression now uses otherwise valid JSON plus
whitespace to test the actual shared message limit, avoiding an unrelated
unknown-field rejection. These corrections are `e6d41555` and `a917a7f4`.

The final run includes those corrections and the additional WebSocket refusal
regressions. Its source commit and XML summaries are retained with the original
failure evidence. Canonical GitHub Actions has not run on the local integration
head. None of these results replaces real portal E2E, installation/update
acceptance or an Android heap measurement.
