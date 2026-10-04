# AutoFirma consent presentation
User requested identifying the long signing dialog, then approved collapsing technical information and restyling this specific dialog to match the application.

## Scope
NativeAfirmaConsentDialog only: the existing paper/teal palette, cut-corner panel shape, typographic hierarchy, identity panel and a filled 50dp primary action. Technical format, algorithm, size and fingerprint start collapsed with a 48dp accessible toggle and request-token keyed expansion state.
Source, exact recipient, certificate, delegated-service destinations, operation, batch count, policy/hash warnings, cancellation disclosures and results remain outside the disclosure. The original secure window policy and all action callbacks/gates are unchanged. No real document signing or administrative submission is part of validation.

## Verification
31 focused Python workflow/policy tests pass; git diff --check passes.
New Robolectric Compose tests cover repeated expansion, new-request reset, explicit confirmation only, locked certificate, sending/cancel state and uncertain result/hash warnings. Broad unit/lint/build verification is required on the exact CI head before installing.
Version 0.2.11 (13), optimized remains nondebuggable with R8 disabled as in the preceding verified installation.
