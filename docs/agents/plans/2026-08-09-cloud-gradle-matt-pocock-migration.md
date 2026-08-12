# Cloud Gradle + Matt Pocock agent workflow migration

Date: 2026-08-09
Status: implemented and verified

## Goal

Make the Android phone an orchestration host rather than a build host. ChatGPT Watchdog, Codex workers, and native Codex subagents must send every Gradle/Android test, lint, and assembly command to the saved Codex Cloud environment. Replace automatic/mandatory Superpowers workflow instructions with the installed Matt Pocock engineering-skill flow.

## Plan

- [x] Add a compact repository `AGENTS.md` that points agents to one Matt Pocock workflow policy and one Codex Cloud Gradle policy.
- [x] Configure the Matt Pocock repository metadata expected by the engineering skills: GitHub issue tracker, default triage labels, single-context domain documentation.
- [x] Version the `w47-cloud` launcher in the repository and require an exact pushed Git SHA for every Gradle Cloud task; add a canonical `full` gate.
- [x] Update the active portal/audit/UGR plans so they no longer mandate Superpowers skills and instead point to the Matt Pocock workflow and Cloud Gradle policy.
- [x] Add lightweight policy regression tests that fail if current agent instructions reintroduce mandatory Superpowers or allow automatic local Gradle.
- [x] Update the durable Watchdog task revision so orchestrator and subagents use Matt Pocock skills and Codex Cloud-only Gradle. Local Gradle becomes an operator-authorized incident fallback, never an automatic fallback.
- [x] Change Termux MCP automatic skill selection from “Matt preferred, arbitrary fallback” to Matt-only automatic selection while preserving explicit `skill_search`/`skill_read` access to other installed skills.
- [x] Verify repository policy tests, Termux MCP tests/build, launcher syntax, a real Codex Cloud Gradle smoke on an exact pushed SHA, and absence of local Gradle/Kotlin build processes afterward.

## Acceptance criteria

1. `prepare_task` cannot automatically select a Superpowers skill.
2. `AGENTS.md` and the durable Watchdog task state that all agent-initiated Gradle commands run through Codex Cloud.
3. Focused and full Gradle Cloud submissions require branch + exact SHA evidence.
4. No automatic local Gradle fallback exists; an incident fallback requires explicit operator authorization.
5. A real Cloud Gradle task passes against the exact policy commit without launching Gradle/Kotlin compilation on the phone.

## Verification evidence

- Repository policy commit `599961e905e71eff67497d0bf17f0dd4d78c8bc7` was pushed and then fast-forwarded unchanged into `agent/workspace-47-autonomous-20260803`; the canonical branch remained unchanged at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- `tools/tests/test_agent_cloud_policy.py`: 7/7 PASS; `bash -n tools/w47-cloud` and `git diff --check` PASS. The launcher requires explicit `--branch` plus exact 40-hex `--sha` and has a dynamic fake-`codex` regression test that rejects shell-substitution regressions.
- Termux MCP automatic routing was changed to `mattpocock-only-auto`; full MCP suite: 577/577 PASS, TypeScript check/build PASS. Live smoke selected a Matt `diagnosing-bugs` skill for a debugging task, selected no automatic skill for a non-Matt flight task, while explicit `skill_search` still exposed the wider installed registry.
- Real Codex Cloud task `task_e_6a788571c5ec8323b40ccfe0bc1572fd` ran in `workspace-47-android` against exact SHA `599961e905e71eff67497d0bf17f0dd4d78c8bc7`. It executed `verifyResolvedCoreVersion`, `verifyPortableAapt2Configuration`, and `testDebugUnitTest` directly inside Cloud: exit 0, `BUILD SUCCESSFUL in 5m 34s`, 590/590 tests across 88 suites, 0 failures/errors/skips, dependency verification enabled and unchanged, final Cloud worktree clean.
- Phone-side Gradle/Kotlin build-daemon count was 0 before Cloud submission, immediately after submission, throughout the Cloud run, and after completion. No local Gradle fallback was used.
- Durable Watchdog task `workspace-47-autonomous-20260803-01` was revised to revision 8 with task SHA-256 `852659f0e24179ad6c9026450e7aa99656bd1ad063b41f5efbd01b3e2b787a8c` and intentionally left `PAUSED`; rollout/handoff recovery was not resumed as part of this migration.
- Cloud execution boundary is explicit: phone/local agents delegate with `w47-cloud`; once already inside `workspace-47-android`, the Cloud agent runs the requested `./gradlew` directly and must not recursively invoke `w47-cloud` or `codex cloud`.
