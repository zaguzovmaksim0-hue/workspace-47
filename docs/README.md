# Documentation index

## Current development

- [Repository lifecycle](../CONTEXT.md): stable main, bounded branches, exact-commit verification.
- [Contribution guide](../CONTRIBUTING.md).
- [Verification policy](agents/github-actions-verification.md): unit tests, lint and builds in GitHub Actions; emulator runs require explicit manual opt-in.
- [Test plan](test-plan.md) and [synthetic fixtures](test-fixtures.md).
- [Signing configuration](release-signing.md).
- [Security model](../SECURITY.md) and [provenance](provenance.md).

## Product and evidence

- [Specification](spec.md).
- [Compatibility inventory](compatibility/all-spanish-public-portals-inventory.md).
- [Protocol observations](protocol-observations.md).
- `compatibility/` contains discovery and compatibility evidence.
- `e2e/` contains historical bounded execution evidence, not current universal acceptance.

## Historical records

`autonomous/`, `superpowers/`, and old publication/test reports retain their original dates and commit-specific evidence. They are not instructions to resume obsolete branches. Preserve their paths so historical links continue to work.

The real-certificate Actions workflow is [archived](archive/workflows/real-e2e.yml.disabled) as inert documentation. Its GitHub environment credentials were removed and its run history cleared by the owner-approved cleanup. Policy tests still inspect the archive to preserve its recorded boundaries. It must not be restored or supplied with credentials without new operator authorization.

The unrelated Kai macOS profiler workflow and wheel were removed from the current tree; prior Git commits retain them for recovery. Android code, the catalog tooling, and the QA relay remain part of this project.

## Local checkout hygiene

Use one clean checkout for current work, tracking the intended live branch. Before reorganizing old worktrees, preserve their commits, binary diffs, and untracked source files. An old modified file is not automatically a missing feature: compare it against current code before any integration. Keep historical experiments outside the normal working directory and never silently apply stale patches to current signing code.
