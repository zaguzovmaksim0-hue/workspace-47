# Sevilla ATSE certificate-login design — 2026-08-09

## Objective

Implement the smallest QA-only compatibility profile for the public ATSE contributor certificate
login documented in
`docs/autonomous/2026-08-09-g38-sevilla-atse-certificate-login-evidence.md`.

## Exact contract

Profile id: `sevilla-atse-certificate-login`.

Exact start URL:
`https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente`

The shim may intercept literal `AutoScript.sign()` / MiniApplet-compatible signing only when the
active runtime profile is exactly this profile, the current origin is exactly
`https://www.sevilla.org`, data is Base64 whose decoded bytes are exactly 40 URL-safe ASCII
characters matching `[A-Za-z0-9_-]{40}`, algorithm is exactly `SHA1withRSA`, format is exactly
`XAdES`, and extra parameters are null/absent. Never hard-code one observed runtime challenge.

The native bridge independently validates those dimensions after Base64 decoding. Wrong profile,
origin, payload length/charset, algorithm, format, or non-null/non-empty properties fail closed.

## XAdES output

Use a dedicated profile-scoped adapter, not the existing REG-AGE adapter. Reproduce the current
official AutoFirma defaults proven in the evidence packet:

- XAdES Enveloping packaging;
- binary challenge contained inside the XAdES XML Object with Base64 transform semantics;
- XML reference digest algorithm SHA-512;
- XML SignatureMethod corresponding to `SHA1withRSA`;
- existing RSA certificate validity/key-usage/trust policy.

Do not change the behavior of `LocalXadesDetachedAdapter` or generic XAdES routing. Any reusable XML
helpers extracted from it must remain parameterized and covered so REG-AGE SHA512/detached behavior
is byte-/contract-stable where expected.

## Callback / authentication boundary

Return signature and certificate only through the existing AutoScript/MiniApplet success callback
channel. Do not implement or call ATSE `authenticate`, `StorageService`, `RetrieveService`,
`CheckTimeService`, JSF Ajax, cookies, session state, form submission, or authenticated redirects.

## Trust and status

The profile is `VERIFIED_CONTRACT`, `QA_ONLY`, RSA-only, requires digitalSignature key usage, and
carries `SIGN` + `LEGACY_SHA1` only. Release stays disabled. Public inventory may advance only to
`IMPLEMENTED_NOT_E2E`; never `VERIFIED_E2E` without separate physical evidence.

## Expected seams

- `app/src/main/res/raw/afirma_shim.js`;
- `AfirmaJavascriptShim.kt`, `WebMessageBridge.kt`, `MiniAppletBridgeAdapter.kt`;
- a dedicated `SevillaAtseXadesEnvelopingAdapter` plus focused codec/adapter tests;
- `ProtocolAdapterRegistry.kt`, `MainActivity.kt` only for exact profile binding;
- `config/site_profiles_v1.json` and profile parser/registry tests;
- shared public inventory/catalog only after the worker implementation is focused-GREEN.
