# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-09, generation 35.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`.
- Autonomous branch: `agent/workspace-47-autonomous-20260803`.
- Before the G35 publication commit, local HEAD/upstream/remote are
  `136ae1ca6fc49fb5e877321dc4451e39f1ea0600`.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` must remain exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- Always `git fetch --prune origin` and verify branch, HEAD, upstream, divergence and worktree before
  mutation. Never merge/rebase/force-push or mutate canonical.

## Published milestones already complete

- Certificate read hardening: `0bf8f5767fa1104fd6d2bb951709484e1009d0e1`.
- Portal Coverage First planning: `758347a21301313656f106beb02ebbc847f8cb17`.
- DGT implementation: `fc52b0a68348f0f26e4ac368526ba7b58f62972f`.
- DGT verification/evidence: `136ae1ca6fc49fb5e877321dc4451e39f1ea0600`.

## G35 in-flight publication — UGR certificate contract

- Isolated worker commit: `efbaec48099d59b1a5073c59ac3b0a97358accc5` in
  `/data/data/com.termux/files/home/workspace-47-autonomous-g33-ugr`.
- Official public UGR contract: fixed text `Universidad de Granada`, `SHA1withRSA`, `CAdES`, empty
  filter, first-party Storage/Retrieve setup, callback form `/Hades/ValidacionCertificado`.
- Profile `ugr-certificado-login` is intentionally `VERIFIED_CONTRACT` / `QA_ONLY`; inventory
  `IMPLEMENTED_NOT_E2E`, catalog `E2E_PENDING`. Never promote to release or `VERIFIED_E2E`
  without new physical evidence.
- Sequential DGT+UGR integration fixed two merge-only defects found by
  `job_20260809_110346_f4c4c80c`: one missing closing brace and shared `CadesPreSignState`
  visibility. Tight compile `job_20260809_110747_72ac53e2` passed.
- Stale aggregate contracts exposed by `job_20260809_111214_e09df3ff` were updated only for the
  intentional tenth profile binding and exact `sede.ugr.es` origin. Exclusive regression rerun
  `job_20260809_111813_594c58d6` passed.

## G35 verification already passed

- Full rerun `job_20260809_112549_86ad3219`: runtime dependency locks, resolved core and portable
  AAPT2 guards PASS; Debug 590/590 and QA 590/590, zero failures/errors/skips.
- Non-Android `job_20260809_113452_d6c2fa05`: Python 103 PASS with one known hardlink skip;
  `CiPolicyTest` 20/20; Go test/vet/build PASS; relay SHA-256
  `b1fe3bd217203c920d528259cbd5ae7db2e5d2c7bfaa595ad6fb84dd14d1f5d6`.
- Combined `--rerun-tasks` lint/build `job_20260809_113545_e83211e4` reached all three assemblies
  and fresh Debug lint before the connector's 600-second limit. Dedicated QA lint recovery
  `job_20260809_114615_8f54482e` passed. Final lint Debug/QA: 0 errors / 26 warnings each.
- Artifact `job_20260809_115034_135469ba` PASS. SHA-256: Debug
  `0fbccd2252e7af13e9df4192671c095413a40347972ebb7d8a217b118d5f8ec7`; QA
  `d214c133d757af841e10ab52587fc6a8e97a70c402920219f4f7998f55d5f125`; QA AndroidTest
  `26e3b13c7c021012dcd7f6a44a671e523edcb72b10da5dcb6def858b48ac6af2`.
- Release fail-closed `job_20260809_115102_4ad9f309` PASS; zero release APKs.
- Before publication remove generated `ws024-relay/ws024-relay` and transient external `error.log`,
  run complete staged diff/sensitive/TLS/WebView/release-profile review and `git diff --check`, then
  commit once, push and verify exact remote SHA. The publication SHA is the resulting autonomous
  HEAD and must be recorded by the next Watchdog handoff after remote verification.

## Current portal KPI after staged UGR integration

- 182 public entries.
- 10 exact profile bindings; 172 unbound.
- Inventory: 166 BROWSE_ONLY, 5 IMPLEMENTED_NOT_E2E, 1 VERIFIED_CONTRACT, 4 VERIFIED_E2E,
  4 INACCESSIBLE, 2 UNSUPPORTED_PROTOCOL.
- Generated catalog: 94 CATALOGED, 73 DISCOVERED, 6 BLOCKED, 5 E2E_PENDING, 4 E2E_VERIFIED.
- Research buffer: 16 classified public surfaces. After UGR, implementation-ready unintegrated
  queue is 0; native implementation occupancy is 0/8.

## Exact next eight portal research candidates

1. `justicia-sede-judicial` — static first-party SHA256withRSA/PAdES contract exists, but exact
   public procedure invocation remains unbounded.
2. `age-acceda` — public page calls `afirma.firmar()`, but the defining runtime wrapper/dynamic
   `formularioweb` contract is incomplete.
3. `sepe-sede` — descriptive AutoFirma evidence only; exact public invocation needed.
4. `mjusticia-sede` — exact ABI/callback invocation not yet public-bounded.
5. `sevilla-sede` — exact invocation not yet found.
6. `us-sede` — requirements only; exact invocation not yet found.
7. `age-direccion-general-del-catastro` — exact operation/ABI/callback/TLS signing contract needed.
8. `asturias-sede-tramite-autofirma` — exact invocation not yet found.

Refill evidence toward at least 16 candidates and dispatch native Codex/Luna implementation workers
only when exact public unauthenticated evidence makes a candidate implementation-ready. Each worker
gets an isolated worktree. Do not consume implementation capacity on review while a ready candidate
is waiting.

## Manual/external gates and safety boundary

- Physical UGR and DGT portal E2E remain manual; automated/public evidence does not justify
  VERIFIED_E2E.
- Existing manual gates remain AEAT Client-TLS physical E2E, real-portal JavaScript-dialog
  compatibility, TalkBack/physical visual accessibility and supported-Linux Go race.
- No APK installation/launch, ADB/UIAutomator/device control, authenticated portal navigation,
  credentials, certificate unlock/private-key material, real signing, upload, payment or
  administrative submission occurred in G35.
