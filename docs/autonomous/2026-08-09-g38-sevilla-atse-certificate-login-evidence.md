# Sevilla ATSE certificate-login public contract evidence — 2026-08-09

## Scope and safety boundary

All observations are from official public, unauthenticated, GET-only surfaces of the Ayuntamiento de
Sevilla / Agencia Tributaria de Sevilla (ATSE), plus the official public AutoFirma source repository.
No credentials, client certificate, authenticated navigation, signing, JSF action, form POST, upload,
payment, submission, APK launch, or device-control workflow was used.

Candidate profile id: `sevilla-atse-certificate-login`.

Ignored raw evidence is under
`build/autonomous-evidence/g38-sevilla-atse-cert-login/`.

## Official public launch chain

The official Sevilla electronic-office map at
`https://sede.sevilla.org/opencms/system/modules/sede/contents/footer/mapa_web` contains the link
"Oficina virtual de la Agencia Tributaria de Sevilla" and points it to
`https://www.sevilla.org/ovweb/`.

That official ATSE root exposes the certificate path "Acceder a Contribuyente con certificado" and
links to the stable public entry:

`https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente`

The entry is public before authentication and contains the certificate-login AutoFirma invocation.

## Exact public AutoScript ABI

The public contributor entry defines a runtime challenge, Base64-encodes it with `btoa`, and calls:

```javascript
AutoScript.sign(datosFirmar, 'SHA1withRSA', 'XAdES', null, saveSignatureCallback, showLogCallback);
```

Four independent public GETs across the ATSE certificate surface produced different challenge values.
The contributor entry's challenge is a 40-character URL-safe ASCII value matching
`[A-Za-z0-9_-]{40}`; it is also the prefix of that response's JSF client-window value. The captured
runtime values are intentionally not copied into tracked documentation and must never be hard-coded.

The page configures first-party AutoScript storage/retrieval and time-check URLs on the same official
`www.sevilla.org/ovweb` surface, then invokes `cargarAppAfirma()`. On successful signing its callback
passes the signature into a JSF `authenticate(...)` action. That post-sign authentication action is
outside this milestone and must not be emulated or triggered by the app.

The signing ABI that may be reproduced is therefore bounded to:

- exact profile: `sevilla-atse-certificate-login`;
- exact origin: `https://www.sevilla.org`;
- exact public start URL above;
- decoded input: exactly 40 URL-safe ASCII bytes matching `[A-Za-z0-9_-]{40}`;
- algorithm: `SHA1withRSA`;
- format argument: literal `XAdES`;
- extra parameters: `null`;
- success callback: AutoScript signature/certificate callback; error callback: the page's error logger.

## Official AutoFirma format defaults

The current official AutoFirma source is the public CTT repository
`ctt-gob-es/clienteafirma`. The exact source paths inspected were:

- `afirma-core/.../AOSignConstants.java`;
- `afirma-crypto-xades/.../AOXAdESSigner.java`;
- `afirma-crypto-xades/.../XAdESSigner.java`;
- `afirma-crypto-xades/.../XAdESConstants.java`.

`AOXAdESSigner.sign()` converts a null properties object to an empty `Properties` and delegates to
`XAdESSigner.sign()`. With no XAdES `format` extra property, `XAdESSigner` defaults to
`SIGN_FORMAT_XADES_ENVELOPING`. For non-XML input, that path places the data in a Base64-transformed
XML Object. Current official source uses SHA-512 as the default XAdES reference digest method. The
signature algorithm still comes from the caller and is `SHA1withRSA` for the ATSE page.

This removes the packaging ambiguity for a QA compatibility adapter: the evidence-backed current
AutoFirma behavior is XAdES Enveloping with SHA1withRSA signature method and SHA-512 reference
digests when the ATSE page supplies no extra parameters.

## Product interpretation

This is a certificate-login compatibility contract, not evidence of a completed tax procedure. The
profile must stay QA-only / VERIFIED_CONTRACT (catalog equivalent IMPLEMENTED_NOT_E2E). The app may
return the local signature through the existing MiniApplet/AutoScript callback channel only. It must
not call `authenticate`, submit JSF state, use the public Storage/Retrieve services, transfer cookies,
or enter an authenticated ATSE session. Physical E2E remains a manual gate.
