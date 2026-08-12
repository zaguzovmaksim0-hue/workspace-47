# Melilla catalog registry-binding remediation plan

1. Preserve the existing failing catalog promotion as the outer RED evidence.
2. Add one direct RED tracer to `ProtocolAdapterRegistryTest` for the exact Melilla
   profile/operation/input/callback/protocol tuple and absence of non-SIGN fallback.
3. Commit and push that RED SHA; run only the focused registry test in Codex Cloud and
   require failure because the Melilla binding is absent.
4. Add the minimum `ProtocolAdapterBinding` in `BuiltInProtocolAdapterRegistry`.
5. Commit and push the GREEN SHA; run focused Debug+QA registry + public catalog tests
   in Codex Cloud.
6. If focused GREEN passes, run any broader Android Cloud gate still required for the
   catalog slice, then direct Standards + Spec/static review.
7. Update the ledger and durable handoff with exact task IDs, counts, and manual E2E
   boundary; push and remote-verify the documentation checkpoint.
