# Gran Canaria direct PAdES contract — 2026-08-17

## Public first-party surface

- Public information page: `https://sede.grancanaria.com/informacion-instancia`.
- That page publicly links the Sede entry `https://sede.grancanaria.com/sede-privado/instancia-general?inicio`.
- The public configuration fixes `SHA512withRSA`, enables certificate filters, forces the AutoFirma server mode, and exposes same-origin Storage/Retrieve service URLs.
- The first-party JSF resource `AFIRMA/operaciones.js` is retrievable directly from the public information page without authentication or cookie replay.

## Exact MiniApplet call

The public `AFIRMA/operaciones.js` resource was revalidated on 2026-08-17. Its body SHA-256 was:

`6d1b19186f95f704a68e1a9ea87af87f678d597ebde7f33fbd1ad4fe7ac470cd`

`firmarSolicitud(...)` builds the direct local-signing call from the public configuration:

- algorithm: `SHA512withRSA`;
- format: `PAdES`;
- base extra properties: `headless=true` followed by `filters=nonexpired:` on the next line;
- success/error callbacks supplied to `MiniApplet.sign`;
- data: the Base64 PDF passed into the signing function.

The script can conditionally append a `qualified:` certificate-serial filter when a private-flow serial is present. No public pre-auth value establishes that serial binding, so Workspace-47 intentionally rejects that optional branch instead of guessing it.

The setup code may force AutoFirma WS mode and configures same-origin Storage/Retrieve services before the local signing call. Workspace-47 implements only the exact local PAdES payload/signature contract once PDF bytes are presented to the bridge; it does not replay portal sessions or perform portal Storage/Retrieve requests itself.

## Workspace-47 scope

The implementation is QA-only and fail-closed:

- profile `gran-canaria-sede-electronica`;
- exact origin `https://sede.grancanaria.com`;
- public profile start `https://sede.grancanaria.com/sede-privado/instancia-general?inicio`;
- current signing document must remain on that exact HTTPS origin;
- RSA + SHA-512 only;
- PAdES only;
- exact `headless=true\nfilters=nonexpired:` base properties;
- no dynamic `qualified:` certificate-serial branch;
- no authenticated progression, session replay, real certificate use, real signature, upload, administrative submission, or registration during research.

The portal remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` until a safe physical Android validation is performed.
