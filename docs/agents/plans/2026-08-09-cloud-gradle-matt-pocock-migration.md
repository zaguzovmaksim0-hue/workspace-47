# Cloud Gradle + Matt Pocock agent workflow migration

Date: 2026-08-09
Status: approved for implementation

## Goal

Make the Android phone an orchestration host rather than a build host. ChatGPT Watchdog, Codex workers, and native Codex subagents must send every Gradle/Android test, lint, and assembly command to the saved Codex Cloud environment. Replace automatic/mandatory Superpowers workflow instructions with the installed Matt Pocock engineering-skill flow.

## Plan

- [x] Add a compact repository `AGENTS.md` that points agents to one Matt Pocock workflow policy and one Codex Cloud Gradle policy.
- [x] Configure the Matt Pocock repository metadata expected by the engineering skills: GitHub issue tracker, default triage labels, single-context domain documentation.
- [x] Version the `w47-cloud` launcher in the repository and require an exact pushed Git SHA for every Gradle Cloud task; add a canonical `full` gate.
- [x] Update the active portal/audit/UGR plans so they no longer mandate Superpowers skills and instead point to the Matt Pocock workflow and Cloud Gradle policy.
- [x] Add lightweight policy regression tests that fail if current agent instructions reintroduce mandatory Superpowers or allow automatic local Gradle.
- [ ] Update the durable Watchdog task revision so orchestrator and subagents use Matt Pocock skills and Codex Cloud-only Gradle. Local Gradle becomes an operator-authorized incident fallback, never an automatic fallback.
- [x] Change Termux MCP automatic skill selection from “Matt preferred, arbitrary fallback” to Matt-only automatic selection while preserving explicit `skill_search`/`skill_read` access to other installed skills.
- [ ] Verify repository policy tests, Termux MCP tests/build, launcher syntax, a real Codex Cloud Gradle smoke on an exact pushed SHA, and absence of local Gradle/Kotlin build processes afterward.

## Acceptance criteria

1. `prepare_task` cannot automatically select a Superpowers skill.
2. `AGENTS.md` and the durable Watchdog task state that all agent-initiated Gradle commands run through Codex Cloud.
3. Focused and full Gradle Cloud submissions require branch + exact SHA evidence.
4. No automatic local Gradle fallback exists; an incident fallback requires explicit operator authorization.
5. A real Cloud Gradle task passes against the exact policy commit without launching Gradle/Kotlin compilation on the phone.
