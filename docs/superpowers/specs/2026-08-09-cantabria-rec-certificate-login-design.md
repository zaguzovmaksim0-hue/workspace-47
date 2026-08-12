# Cantabria REC certificate-login contract design

## Problem

The Cantabria Registro Electrónico Común exposes a public certificate-login surface whose first-party JavaScript performs an ordinary AutoFirma/MiniApplet signature before the form is submitted. The current generic Junta Firma Mobile MiniApplet route supports evidence-backed generic contracts but deliberately does not admit SHA512withRSA+CAdES. Broadening that generic allowlist would be unsafe.

## Evidence boundary

Use only `docs/autonomous/2026-08-09-g37-cantabria-rec-certificate-login-evidence.md` and its preserved public captures. The implementation stops at delivering the signature/certificate callback to the page. It never submits the form or claims successful authentication.

## Exact contract

Candidate profile id: `cantabria-rec-cert-login`.

- start/initiator origin: `https://rec.cantabria.es`;
- activation: `QA_ONLY`;
- compatibility: `VERIFIED_CONTRACT` only after functional tests pass;
- operation: `SIGN`;
- input: existing `miniapplet-autoscript-v1` envelope, but profile-scoped compatibility validation;
- callback: existing MiniApplet sign callback shape;
- algorithm: only `SHA512withRSA`;
- format: only `CAdES`;
- packaging/mode: implicit CAdES as represented by exact portal extra properties;
- challenge: runtime page value matching `^[0-9a-f]{40}$`; the client never synthesizes or stores a captured challenge.

The portal wrapper builds extra properties as `filters=` followed by newline `mode=implicit`. The profile-scoped shim compatibility path may canonicalize only this exact observed value; arbitrary filters or extra properties are rejected.

## Shim boundary

Add a `cantabriaCompatibilityEnabled` placeholder analogous to the existing UGR compatibility flag. It is true only when the active profile is exactly `cantabria-rec-cert-login` and the runtime profile registry contains that QA profile.

The functional shim accepts SHA512withRSA+CAdES only when all of these are true:

1. Cantabria compatibility is enabled;
2. `window.location.origin === "https://rec.cantabria.es"`;
3. the data argument is exactly 40 lowercase hexadecimal characters;
4. format is exactly `CAdES`;
5. extra properties equal the exact canonical Cantabria value;
6. success and error callbacks are functions and the ordinary six-argument MiniApplet.sign contract is used.

All other generic behavior is unchanged. Do not make SHA512withRSA+CAdES globally valid.

## Native adapter/profile boundary

The native MiniApplet adapter resolves the current profile and validates an exact Cantabria contract before accepting SHA512withRSA+CAdES. The validation includes profile id/version, `VERIFIED_CONTRACT`, `QA_ONLY`, exact start URL and initiator origin, no unexpected redirect/browse origins/endpoints, RSA certificate rules, exact operation policy, algorithm/format/mode, input adapter and callback contract.

The decoded challenge bytes must represent the exact page challenge. Because the shim sends Base64 data to the native bridge, the native adapter validates decoded UTF-8/ASCII content against exactly 40 lowercase hex characters for this profile. It zeroizes the decoded byte buffer after normalization as existing MiniApplet paths do.

## Callback and safety boundary

Success uses the existing MiniApplet result channel, which calls the page-provided success callback with signature/certificate. The portal JavaScript may subsequently set `docFirmado`; Junta Firma Mobile does not own or trigger form submission.

Do not add POST endpoints, cookie handling, authenticated redirects, session reconstruction, or any login-completion logic. Existing navigation/origin/request-id/replay/certificate/session fail-closed behavior remains mandatory.

## TDD seams

1. `AfirmaJavascriptShim.load`: Cantabria mode is profile-scoped and exact; generic non-Cantabria shim rejects/does not advertise this compatibility.
2. `MiniAppletBridgeAdapter.route`: exact synthetic Cantabria challenge/algorithm/format/properties is accepted only under the exact Cantabria profile; uppercase/length mismatch, wrong origin/profile/algorithm/format/properties fail closed.
3. `SiteProfileCatalogParser` / registry: bundled profile is QA-only and exact.
4. Public catalog/inventory only after the functional profile passes its gates.

## Lifecycle

Automated completion may advance only to QA-only `VERIFIED_CONTRACT` / `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Physical login/E2E remains manual.
