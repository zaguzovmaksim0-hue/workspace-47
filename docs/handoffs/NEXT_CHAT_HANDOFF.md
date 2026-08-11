# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-11, generation 45.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- Pre-handoff published checkpoint is `a15484764948c6fea35a285b89e7461936092845`, verified equal to
  local HEAD, tracking ref and `git ls-remote`, divergence `0/0`, with a clean tracked worktree after
  deleting generated Cloud CLI diagnostics. This handoff itself is committed afterward, so resolve the
  containing published HEAD on continuation rather than assuming `a154847...` remains final.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` remains exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`; no merge, rebase, force-push, rewrite or PR occurred.
- Start the next generation with `prepare_task`, `git fetch --prune origin`, branch/HEAD/upstream/
  divergence/status checks, canonical verification, and fresh inspection of preserved worker worktrees.
- Gradle policy is **Codex Cloud only** in saved environment `workspace-47-android`. Never execute local
  `./gradlew`, local Gradle/JVM/Kotlin, or a phone fallback, including after 429/timeout/infrastructure
  failure, unless a later explicit operator amendment changes the policy.

## Generation 45 published work

- Generation 45 reconciled the actual main branch, which was newer than the generation-44 prose
  handoff. Published Sevilla ATSE in-flight history already present on main was preserved:
  `1f97c48bcfcc75363a0b2e0bf5931023da72ba53` shim GREEN implementation,
  `c909a4a1ce1ed39cc196d31880c38bc46b14adef` native-bridge RED,
  `06316274733c7f2aa62638ca441e80dd5c36776d` native-bridge implementation,
  `84f75e7c9c5130a30c85c4b66c2757dca7d2112e` XAdES Enveloping adapter RED,
  `1208a2774d6a6ad7994b6c6f3d590b0b072998e0` dedicated adapter implementation, and
  `44244f96933dbccfcd90bfa066eaf57e283c263a` exact QA-only profile RED. These commits are pushed
  repository state but must not be called accepted Gradle GREEN merely because they are published.
- `ba65b92056860825b7803d172eebd53fab581abe` (`docs(portal): refresh Extremadura STA contract`) is
  pushed. GET-only refresh of three current first-party `tramites.juntaex.es` STA resources returned
  HTTP 200 and byte-for-byte matched generation-42 hashes. Exact evidence is in
  `docs/autonomous/2026-08-11-g45-extremadura-refresh.md`.
- `a15484764948c6fea35a285b89e7461936092845` (`docs(portal): record Murcia public WAF boundary`) is
  pushed. Current CARM landing/procedure pages expose only generic site assets before electronic-start
  links cross to the WAF boundary; no challenge was retained or bypassed and `murcia-sede` remains
  research-only. Evidence is in `docs/autonomous/2026-08-11-g45-murcia-research.md`.
- Portals fully integrated in generation 45: zero. Sevilla remains an in-flight implementation slice,
  not a completed/profile-bound/catalog-promoted portal.

## Sevilla in-flight state and Cloud evidence

- Published main currently contains the profile-scoped shim, exact native bridge seam and dedicated
  `SevillaAtseXadesEnvelopingAdapter`. The worktree additionally contains **unstaged/uncommitted**
  prospective profile GREEN changes in `SiteProfileCatalogParser.kt` and `config/site_profiles_v1.json`;
  they match the exact QA-only profile RED contract under static review but have no accepted Cloud RED
  evidence, so they must be preserved without publishing. Registry/catalog phases are still absent.
  The design/plan remain:
  `docs/superpowers/specs/2026-08-09-sevilla-atse-certificate-login-design.md` and
  `docs/superpowers/plans/2026-08-09-sevilla-atse-certificate-login.md`.
- Exact contract remains profile `sevilla-atse-certificate-login`, start URL
  `https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente`, exact origin
  `https://www.sevilla.org`, 40-character URL-safe decoded challenge, `SHA1withRSA`, `XAdES`, null
  extra properties, dedicated Enveloping/XAdES adapter, `QA_ONLY` / `VERIFIED_CONTRACT`, release
  disabled, and public inventory at most `IMPLEMENTED_NOT_E2E` until physical evidence exists.
- Adapter focused Cloud task `task_e_6a7aa19ae4a08323b0f8200f3f9584bc` was submitted for exact pushed
  SHA `1208a2774d6a6ad7994b6c6f3d590b0b072998e0` and
  `SevillaAtseXadesEnvelopingAdapterTest`; it is lifecycle `READY` with no diff.
- Profile RED Cloud task `task_e_6a7aa3b8def08323970f6b302ea0ad64` was submitted for exact pushed
  SHA `44244f96933dbccfcd90bfa066eaf57e283c263a` and focused test
  `SiteProfileCatalogParserTest.preservesTheExactSevillaAtseQaOnlyCertificateLoginContract`; it moved
  from `PENDING` to lifecycle `READY`.
- **Neither READY is accepted as Gradle evidence.** Installed Codex CLI `0.148.0-alpha.6` exposes
  lifecycle/status/list/diff/apply but no supported task logs/output/wait result containing Gradle
  stdout, exit code, observed SHA or conclusion. The interactive `codex cloud` TUI also failed to open
  through the connector PTY at its cursor-position handshake. Do not scrape private endpoints or infer
  PASS/expected-RED from READY. The local prospective profile GREEN must remain unstaged/unpushed
  while that evidence is unavailable.
- Exact next Sevilla action: retrieve supported terminal evidence if/when the Cloud surface exposes it.
  For the profile RED, verify exact SHA `44244f...` and the intended single focused test failure (not an
  infrastructure/dependency failure). For adapter GREEN, verify exact SHA `1208a277...`, dependency
  verification enabled, focused Gradle exit 0 / BUILD SUCCESSFUL and clean Cloud checkout. Only then
  review the preserved local profile GREEN against that observed RED, then commit/push it as the exact
  candidate SHA for a Cloud-only GREEN gate; continue registry/catalog only after accepted evidence.

## Preserved worker states

- Sevilla worker `/data/data/com.termux/files/home/workspace-47-autonomous-g38-sevilla`, branch
  `agent/g38-sevilla-atse-certificate-login`, remains clean and remote-identical at
  `069c6fd73a19b54b92dc4771867fff712617301d`, divergence `0/0`. Preserve it; main has advanced beyond
  its shim-only contents, so do not re-integrate/replay it.
- Melilla worker `/data/data/com.termux/files/home/workspace-47-autonomous-g36-melilla`, branch
  `agent/g36-melilla-batch-contract`, remains clean at local
  `ce1b1639b322b616fb71cce12c73305db26e6a1a`; upstream is
  `25df9f7ed5bef0387568d6c2db5c7083f154fa9b`, so local is one commit ahead (`0 1` in
  `@{upstream}...HEAD`). The local Android-compatible `JSONObject.keys()` validation fix remains
  intentionally unpushed pending verifiable terminal Cloud evidence. Never reset/rebase/force-push it.

## Portal KPI, queue, and blockers

- Current generated catalog: 183 entries; 13 bound surfaces; 12 unique profile IDs; 170 unbound.
- Inventory states: 164 `BROWSE_ONLY`, 8 `IMPLEMENTED_NOT_E2E`, 1 `VERIFIED_CONTRACT`, 4
  `VERIFIED_E2E`, 4 `INACCESSIBLE`, 2 `UNSUPPORTED_PROTOCOL`.
- Generated states: 92 `CATALOGED`, 73 `DISCOVERED`, 6 `BLOCKED`, 8 `E2E_PENDING`, 4
  `E2E_VERIFIED`; discovery states are 105 `REVIEWED`, 5 `RECHECK_REQUIRED`, 73 `DISCOVERED`.
- Classified research buffer remains at least 16 public surfaces.
- Exact implementation sequence remains: finish Sevilla ATSE only after acceptable Cloud evidence;
  then preserved Melilla STA; then `extremadura-tramites` (`ES-PUB-0109`).
- Extremadura remains implementation-ready in research only. Fresh generation-45 GET evidence confirms
  the same exact STA batch contract: `SHA256withRSA`, CAdES default, per-document CAdES/PAdES/XAdES,
  PAdES `signatureSubFilter=ETSI.CAdES.detached`, XAdES `mode=implicit`, backend-supplied pre/post/data
  URLs, and `PRESENTAR_FIRMA` / `validationResponse` callback. Do not implement it ahead of a stable,
  verified shared Melilla STA seam.
- Murcia remains `BROWSE_ONLY`: current public procedure assets do not reveal a signing ABI before WAF.
- Other research-only leads needing a complete public binding include Justicia Sede Judicial,
  ACCEDA/MPTMD `ES-PUB-0072`, SEPE, Ministerio de Justicia, Asturias, and Deputación de Ourense.
- Manual/physical E2E-only gates remain UGR, DGT, Cantabria and JCCM; AEAT requires Client-TLS E2E.
  Real-portal JS-dialog compatibility, TalkBack/physical visual validation, and Go race on supported
  Linux remain external/manual environment gates.

## Verification and safety

- Generation-45 documentation commits passed `git diff --check`; bounded changed-doc scans found no
  private-key, bearer/JWT, Authorization/Cookie, GitHub/OpenAI/MCP token, dependency-verification
  disablement, trust-all or hostname-verifier bypass patterns.
- Extremadura research was unauthenticated HTTPS GET-only to known first-party static resources. Murcia
  research was bounded unauthenticated GET-only; WAF challenge values/bodies were discarded and no
  bypass was attempted.
- A generated untracked Cloud CLI `error.log` was classified, scanned for sensitive token/key patterns,
  and deleted; do not retain generated Cloud diagnostic logs in the project worktree.
- Final worktree is intentionally dirty only with the preserved unstaged Sevilla profile GREEN in
  `SiteProfileCatalogParser.kt` and `config/site_profiles_v1.json`; documentation is committed separately.
- No Android Gradle/JVM/Kotlin command was run on the phone in generation 45. No APK install/launch,
  ADB/device control, authenticated government navigation, credentials/cookies/bearer/private keys/
  certificate unlock, real signing, form POST, upload, payment, or administrative submission occurred.
- Worker delegation remains disabled: no native Codex/Luna implementation subagents, no
  `@Termuх agent_spawn`, and no delegated `codex/code-review`. Continue direct Matt Pocock-style TDD,
  implementation, diagnosis, and bounded Standards + Spec review in the main Watchdog.
