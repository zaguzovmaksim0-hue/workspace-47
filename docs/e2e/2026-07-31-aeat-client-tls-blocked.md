# AEAT Client TLS physical gate — blocked before WebView

Date: 2026-07-31

## Exact build

- variant: QA
- package: `dev.junta.firmamobile`
- version: `0.1.0-qa`
- QA APK SHA-256:
  `ca5b351656cb41904f3774ed2a84ac002041d9babdf5fe877d6191d04d6befe2`
- the local APK, shared-storage staging file, `/data/local/tmp` file and installed
  `base.apk` matched exactly; `pm install -r -t` returned `Success`.

## Sanitized outcome

- requested portal: `aeat-sede`;
- requested profile: `aeat-mis-datos-censales`;
- QA smoke: `total=1`, `profileResolvedOnly=1`, `webViewActive=0`,
  `catalogOnly=0`, `failures=0`;
- visible allowlisted UI markers: `Contraseña del certificado`,
  `Desbloquear certificado`, `Elegir otro`, `Olvidar certificado`;
- certificate state: locked;
- WebView Client TLS callback: not reached;
- request host/port/key types/issuers: not observed on Android because the
  WebView was not created;
- native Client TLS confirmation: not shown;
- portal authentication accepted: not tested;
- final authenticated origin/path: not reached.

No password was read, copied, logged or automated. No PKCS#12, certificate body,
private key, cookie, authenticated page, screenshot or personal identifier was
retained.

## Status decision

The runtime contract and QA implementation remain valid, but the physical E2E
gate is incomplete because certificate unlocking requires manual user input. The
profile therefore remains `VERIFIED_CONTRACT / QA_ONLY` and the public catalog
remains `E2E_PENDING / IMPLEMENTED_NOT_E2E`. Release trust is unchanged.

The next authorized attempt must manually unlock the existing certificate, open
only `Mi área personal → Mis datos censales`, observe the exact WebView
`ClientCertRequest`, and stop before every modification, payment, signature or
submission control.
