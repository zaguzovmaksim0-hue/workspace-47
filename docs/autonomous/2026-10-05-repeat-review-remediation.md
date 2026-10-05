# Repeat review remediation

Baseline: `9582a2a389877750b95d7c8c8288dbafd564a1a6`.

- Serialize durable unlock-key creation and record commits with revocation. Mark
  the generation invalid before waiting for an existing commit, and reject stale
  writers inside the persistence boundary before they can recreate a key. Clear
  acknowledges completion only after older commits can no longer persist data.
- Revalidate certificate validity at the shared session boundary, including
  existing signing snapshots and per-phase identity lookups. Both profile
  coordinators already query that boundary before signing/completion. The raw
  cryptographic primitive remains separate from session authorization.
- Preserve partial profile-cookie cleanup as a typed outcome through recovery
  and logout. Explain persistent-cookie limits before confirmation and after
  partial logout. Never replace scoped cleanup with global cookie deletion.
- Display generic XAdES in the profile confirmation instead of falsely claiming
  Detached for Enveloping/Enveloped adapters. The universal engine keeps its
  own exact packaging description.

Regression coverage includes a cancelled writer delayed before key creation,
blocking durable commit versus clear completion, new-instance restoration,
certificate expiry/rollback and signing snapshots, and persistent path cookies.
Synthetic JVM probes reproduce the old failures and pass the corrected boundary.
The canonical acceptance gate is GitHub Actions on the final exact commit.
No E2E, administrative submissions, version increment, data wipe or merge.
