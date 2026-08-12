# Workspace-47 Autonomous Audit Design

## Objective

Run a bounded autonomous engineering cycle over Junta Firma Mobile from the exact
canonical continuation point, improving security, reliability, portal-contract
coverage, accessibility, CI, and documentation using only evidence that can be
generated safely and automatically.

The cycle is executed through ChatGPT Watchdog Rolling Chats. Each generation has
40 minutes of confirmed active time, receives a wrap-up at approximately 38
minutes, and hands exact durable state to the next generation. The complete task
has a 43,200,000 ms confirmed active-time budget. Offline time, transport loss,
wrong-page state, reconciliation, browser downtime, authentication challenges,
and controller downtime do not consume the task budget.

## Repository isolation

- Repository: `github.com/zaguzovmaksim0-hue/workspace-47.git`.
- Canonical source branch: `feature/ws024-secure-tunnel-20260728`.
- Canonical source commit: `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- Autonomous branch: `agent/workspace-47-autonomous-20260803`.
- Worktree: `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`.
- Never initialize another Git repository.
- Never mutate another workspace-47 worktree.
- Never merge, rebase, force-push, or change the canonical branch automatically.
- Every completed milestone is an atomic commit pushed to the autonomous branch.

## Safety boundary

The agent may modify source, tests, build configuration, static resources, and
project documentation. It may run JVM/Robolectric tests, Python tests, Go tests,
lint, Gradle builds, dependency checks, APK static checks, and local synthetic
servers or fixtures.

The agent must not:

- install or launch an APK;
- open the Android app for manual inspection;
- use ADB, UIAutomator, Mobilerun, or device-control tools for this task;
- open a government portal interactively or navigate authenticated areas;
- enter a certificate password, PKCS#12 material, Cl@ve data, credentials, or
  personal data;
- create drafts, upload documents, sign, pay, submit, or modify an administrative
  procedure;
- persist or print private keys, certificate bodies, passwords, cookies, bearer
  values, signatures, or personal form data;
- promote compatibility to `VERIFIED_E2E` without new physical evidence supplied
  by the user.

Any manual or physical check is recorded as a precise acceptance gate. It does
not stop independent automated work.

## Evidence and compatibility statuses

New portal profiles may be researched and implemented from public evidence only.
Allowed evidence includes official public pages, official documentation,
publicly served JavaScript, DNS/TLS/HTTP metadata, safe unauthenticated `GET` or
`HEAD` requests, and synthetic local fixtures.

New profiles remain limited to the following evidence boundary:

- `EXPERIMENTAL` or `VERIFIED_CONTRACT`;
- `QA_ONLY`;
- `IMPLEMENTED_NOT_E2E` or the repository's equivalent public-catalog state.

No profile becomes release-enabled or `VERIFIED_E2E` without a separate,
explicitly evidenced physical acceptance event. Existing E2E claims retain their
exact documented scope and must not be broadened.

## Product and UX boundary

UX changes are local improvements within the existing Material 3 and Compose
design language. Permitted changes include semantics, focus order, error and
recovery copy, state clarity, touch-target contracts, adaptive layout contracts,
and accessibility behavior that can be checked automatically.

Radical redesign, visual-quality claims, and device-rendering claims are outside
the autonomous scope. Automated Compose, Robolectric, resource, semantics, and
accessibility-contract tests are the evidence source.

## Dependency policy

Dependencies and toolchain versions remain frozen unless one of these conditions
is demonstrated:

- a verified security vulnerability;
- a reproducible incompatibility;
- a required defect fix;
- a necessary feature requirement with no safe implementation on current pins.

Each permitted update is isolated, reviewed independently, accompanied by lock
and verification-metadata review, scanned with the available pinned tools, and
must pass the complete relevant gate. Broad proactive upgrades are prohibited.

## Audit order

The autonomous cycle uses this priority order while allowing independent work to
continue when one line is blocked:

1. establish a clean, reproducible baseline and evidence ledger;
2. security and privacy invariants;
3. architecture, lifecycle, concurrency, determinism, and recovery;
4. WebView, network, redirects, DNS, TLS, Client TLS, cookies, bridge, and signing
   boundaries;
5. public-only portal contract research and QA-only profiles;
6. UX, accessibility, semantics, and error states;
7. test determinism, CI, dependencies, supply-chain controls, and documentation;
8. repeated fresh audit passes for remaining actionable defects.

A blocked line is recorded and skipped temporarily. It is not an overall task
stop condition. If a pass finds no immediately actionable issue, the agent starts
a fresh audit pass using a different evidence surface instead of declaring the
project complete.

## Milestone protocol

Every behavior-changing milestone follows this sequence:

1. inspect the current Git, remote, file, and test state;
2. record the defect or hardening hypothesis with reproducible evidence;
3. create or update a narrow design and implementation plan naming exact files;
4. use test-driven development: RED, observed expected failure, minimal GREEN;
5. run focused tests, then all relevant full gates;
6. run `git diff --check`, inspect the complete diff, and scan changed content for
   credentials, personal data, unsafe TLS patterns, and unrelated changes;
7. update the audit ledger, test report, security roadmap, threat model, or
   compatibility documentation only where evidence changed;
8. create one atomic commit and push it to the autonomous branch;
9. verify the remote branch contains the exact commit before starting the next
   milestone.

A failed test, build, scan, commit, or push is diagnosed and repaired. It does not
become a false PASS and does not terminate the overall task. Temporary push
failures retain the local commit and exact SHA for a later safe retry. Force-push
is prohibited.

## Rolling handoff contract

Each generation must end with exactly one current `WATCHDOG_HANDOFF` containing:

- completed milestones and pushed commit SHAs;
- current branch, HEAD, worktree cleanliness, and remote relation;
- modified and generated files;
- exact tests and checks run, including failures and environmental skips;
- unresolved local blockers and deferred manual gates;
- precise next commands and next engineering actions.

The ordinary handoff status remains `ACTIVE`. Local blockers are described inside
the handoff instead of using overall `BLOCKED`. Before the task-wide active-time
budget is exhausted, the agent must not emit task-level `DONE` merely because a
backlog pass is empty, a manual E2E is pending, a product choice is ambiguous, an
external system is risky, or infrastructure is temporarily unavailable.

When Watchdog sends its terminal task-budget wrap-up, the generation safely
closes its current step, records remaining work, emits `status: DONE`, and adds
the exact task completion marker required by Watchdog. A direct user stop command
also ends the task.

## Completion evidence

The terminal handoff must identify:

- the final pushed autonomous-branch HEAD;
- all completed milestone commits;
- current full-gate results and environmental limitations;
- remaining manual E2E gates and deferred product decisions;
- unresolved defects or research leads;
- confirmation that no canonical branch was merged or rewritten;
- confirmation that no physical device, certificate, credential, real signing,
  or administrative submission was used.
