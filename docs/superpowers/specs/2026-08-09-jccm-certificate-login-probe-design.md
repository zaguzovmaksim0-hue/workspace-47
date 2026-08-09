# JCCM certificate-login probe design — 2026-08-09

## Objective

Add the smallest QA-only compatibility profile for the official JCCM public certificate-login
component-validation page documented in
`docs/autonomous/2026-08-09-g38-jccm-certificate-login-evidence.md`.

## Exact behavioral boundary

The profile id is `jccm-certificate-login-probe` and is enabled only in QA. Its exact `startUrl` is:

`https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml`

The WebView shim may intercept `MiniApplet.sign()` only when all of these are true:

1. the active runtime profile is exactly `jccm-certificate-login-probe`;
2. the current origin is exactly `https://ventanillaelectronica.jccm.es`;
3. data is exactly `QUJDREU=`;
4. algorithm is exactly `SHA1withRSA`;
5. format is exactly `CAdES`;
6. extra parameters are absent/null;
7. the call supplies the standard success/error callbacks.

The native bridge must independently validate the same profile/origin/algorithm/format/payload
contract after decoding Base64. It may sign only the five decoded ASCII bytes `ABCDE`. Certificate
selection remains subject to the existing RSA/key-usage/validity/trust constraints used by other QA
MiniApplet profiles.

On success, return the signature through the existing MiniApplet callback channel. Do not emulate,
trigger, or authorize `FORMPROC.submit()`. No cookie/session transfer, authenticated redirect,
server-side storage endpoint, upload, or administrative action belongs to this milestone.

## Fail-closed cases

Reject wrong profile, origin, path/profile binding, input value, invalid Base64, algorithm, format, or
non-empty extra properties. Keep generic CAdES policy unchanged. No JCCM exception may broaden any
other profile.

## Catalog truthfulness

Add exactly one QA-only profile and bind the existing Castilla-La Mancha/JCCM inventory surface only
when its catalog row can cite this exact public page. Status is at most `VERIFIED_CONTRACT` /
`IMPLEMENTED_NOT_E2E`; never `VERIFIED_E2E`. Release remains disabled.

## Files/interfaces

Expected implementation seams:

- `app/src/main/res/raw/afirma_shim.js`;
- `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`;
- `app/src/main/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapter.kt`;
- `config/site_profiles_v1.json` and profile parser/registry tests as needed;
- public inventory/catalog generator/resource only after the profile seam is GREEN;
- focused shim/adapter/profile/catalog tests.
