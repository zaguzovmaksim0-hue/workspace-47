# Melilla catalog registry-binding remediation design

## Defect

The pushed catalog promotion commit `037b3614298cf2c33089b30fb73b789a26077249`
correctly binds public entry `ES-PUB-0107` to profile `melilla-sede`, but the focused
Cloud gate `task_e_6a7b8975ac94832392f8ab5ce0fd9411` fails in QA because
`PortalCatalogRepository.isImplementedAndActive()` requires the profile's `SIGN`
operation to have an exact `BuiltInProtocolAdapterRegistry` binding. Melilla's batch
runtime is implemented and Cloud-accepted, but that metadata registry has no Melilla
entry, so the public catalog resolves the profile as `VERIFIED_CONTRACT` instead of
`IMPLEMENTED_NOT_E2E` and keeps it non-openable in QA.

This is a registry-accounting defect, not a reason to weaken catalog checks.

## Behavioral seam

The narrow seam is `BuiltInProtocolAdapterRegistry.registry.resolve(profileId, SIGN)`.
The required binding is already fixed by the profile/runtime contract:

- profile: `melilla-sede`;
- operation: `SIGN`;
- input adapter: `melilla-batch-autoscript-v1`;
- callback contract: `melilla-batch-result-v1`;
- signing protocol: `MelillaBatchProtocolAdapter.ID`.

No fallback, generic MiniApplet widening, release activation, origin change, endpoint
change, algorithm change, retry, redirect, or TLS change is permitted.

## Files

RED:

- `app/src/test/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistryTest.kt`.

GREEN:

- `app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt`.

Evidence/publication after Cloud GREEN:

- `docs/autonomous/2026-08-04-audit-ledger.md`;
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`.

## Acceptance

1. Direct registry test proves the exact Melilla tuple and no operation fallback.
2. Existing QA catalog tests resolve Melilla as `IMPLEMENTED_NOT_E2E`, openable only
   under QA trust policy; release remains `VERIFIED_CONTRACT`, disabled, and
   `resolveLaunch == null`.
3. Focused Debug+QA catalog/registry Cloud tests pass on the exact pushed SHA with
   dependency verification enabled, unchanged verification metadata, and clean checkout.
4. No local Gradle execution is used.
