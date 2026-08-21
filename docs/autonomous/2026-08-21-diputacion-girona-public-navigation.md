# Diputació de Girona — current public e-Tram navigation boundary — 2026-08-21

## Current first-party evidence

- `https://www.ddgi.cat/web/` returned HTTP 200 and published the current `Seu electrònica` destination `https://seu.ddgi.cat`.
- `https://seu.ddgi.cat/web/nivell/651/s-1/seu` returned HTTP 200 and published the `Instància genèrica` route to the current `seu-e.cat` procedure page.
- The first-party procedure page `https://seu-e.cat/ca/web/ddgi/tramits-i-gestions/-/tramits/tramit/14139301` returned HTTP 200. It identifies the procedure as `Instància genèrica`, publishes `Comença` at the exact URL `https://seu-e.cat/tramits/8001760009/instancia-generica`, and lists idCAT Mòbil, digital certificate, and Cl@ve as recognized digital-identification options. It states that electronic processing requires completing and digitally signing the web form.
- A fresh public GET of `https://seu-e.cat/tramits/8001760009/instancia-generica` returned HTTP 200 and ended at `https://etram.seu-e.cat/tramits/8001760009/instancia-generica`, the current public e-Tram shell. The response exposed only the public Angular shell and no credential, session, or private document data.

The sanitized live-fetch observations were:

| URL | HTTP/final URL | bytes | SHA-256 |
| --- | --- | ---: | --- |
| `https://www.ddgi.cat/web/` | 200 / same URL | 711638 | `596cfdfcfcee2020aff6e0b4c13b2797e7e500de4e7508c9ec49bc523ec843ce` |
| `https://seu.ddgi.cat/web/nivell/651/s-1/seu` | 200 / same URL | 301542 | `56b4933137a38ff66b68995919e5c1fc26f872e6e151cb9059cbf5334be80a17` |
| `https://seu-e.cat/ca/web/ddgi/tramits-i-gestions/-/tramits/tramit/14139301` | 200 / same URL | 88687 | `e55c69726e0360a255653fb738e6c7dab81ef65440917cf0159aba885dbf9d45` |
| `https://seu-e.cat/tramits/8001760009/instancia-generica` | 200 / `https://etram.seu-e.cat/tramits/8001760009/instancia-generica` | 6256 | `4e6bae0861039e2dec6696782e4dcbc7babbf04e62b62186ddbae07c2661a352` |

## Bounded implementation

`ES-PUB-0154` is promoted to `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` for the exact public navigation contract only. The new QA-only `diputacion-girona-instancia-generica` profile starts at the published `seu-e.cat` URL, allows only the observed `etram.seu-e.cat` redirect origin, and has no signing operation, client-auth policy, endpoint, or sensitive capability.

The inventory keeps the institutional `ddgi.cat` page as official metadata and records the current procedure as the implemented entry point. Certificate/signature wording on the public procedure is evidence of the portal’s declared requirements, not proof of a signer ABI, certificate transport, accepted signature, or filing. The institutional origin is not added to the e-Tram profile trust boundary by analogy.

No credentials, certificate, session token, private key, document, upload, form submission, payment, signature, registration, or administrative filing was performed.
