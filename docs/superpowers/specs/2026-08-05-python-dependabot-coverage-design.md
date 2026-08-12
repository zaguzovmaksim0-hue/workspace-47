# Python Dependabot coverage design

## Finding

The repository has one explicit Python source dependency manifest,
`tools/requirements.txt`, currently pinning `PyYAML==6.0.3`. The security workflow
already treats that file as a supply-chain input and sends it to OSV-Scanner, but
`.github/dependabot.yml` has version-update entries only for Gradle, Go modules and
GitHub Actions. Therefore the same pinned Python dependency is vulnerability-scanned
but is outside the repository's automated version-update monitoring.

GitHub's current Dependabot documentation lists `pip` as the ecosystem value for
Python package manifests including `requirements.txt`, and requires a separate
package-ecosystem/directory/schedule entry for each monitored ecosystem. The manifest
lives under `/tools`, so that is the narrow directory scope.

This is a maintenance/supply-chain coverage gap, not evidence that the currently
pinned PyYAML version is vulnerable and not a reason to upgrade any dependency in
this milestone.

## Scope

Configuration:

- Modify `.github/dependabot.yml` to add exactly one weekly `pip` version-update entry
  scoped to `/tools` with the same Monday cadence and PR limit as existing ecosystems.

Policy test:

- Strengthen `tools/tests/test_ci_policy.py` so the repository fails closed if the
  Python Dependabot entry disappears, duplicates, or points outside `/tools`.

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No dependency version, lockfile, Gradle metadata, GitHub Action SHA, workflow
permission, runtime source, Android resource, release signing rule or portal policy
changes.

## Approaches considered

1. **Add a `/tools` pip Dependabot entry — selected.** This matches the existing
   `requirements.txt` location and the repository's current weekly update policy.
2. Move the Python manifest to repository root merely to reuse a root directory.
   Rejected because it creates unrelated path/tool churn.
3. Upgrade PyYAML while adding monitoring. Rejected because the dependency policy
   forbids proactive upgrades without a demonstrated vulnerability/incompatibility or
   required feature.
4. Leave OSV scanning as the only Python dependency control. Rejected because scanning
   and update discovery are independent controls; Gradle/Go/Actions already have both
   forms of maintenance coverage.

## Required behavior

1. `.github/dependabot.yml` contains exactly one `pip` ecosystem entry.
2. That entry is scoped to `/tools`, where `requirements.txt` is stored.
3. It runs weekly on Monday and retains `open-pull-requests-limit: 5`.
4. Existing Gradle, Go module and GitHub Actions entries remain exactly one each.
5. `tools/requirements.txt` remains byte-for-byte unchanged at this milestone.
6. No claim is made that Dependabot executed on the autonomous branch; the config
   becomes operative according to GitHub repository/default-branch behavior after the
   change is integrated where Dependabot reads configuration.

## Verification strategy

- First strengthen the existing CI policy test and observe RED because the current
  config has zero `pip` entries.
- Add only the narrow Dependabot block and rerun the exact test GREEN.
- Run the complete Python policy suite plus the repository's full Android, lint,
  Python, Go, artifact and release fail-closed gates to ensure configuration-only
  scope did not perturb any build or security invariant.
- Inspect exact diff, whitespace, pinned-action/security patterns and sensitive content
  before evidence mutation, commit and push.
