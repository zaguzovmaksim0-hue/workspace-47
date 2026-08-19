# Catalunya Seu electronic register client-TLS boundary — 2026-08-19

Target: `ES-PUB-0104`, Generalitat de Catalunya — Seu electrònica. This note records only the bounded certificate-authentication seam. It does not claim a document-signature ABI, successful authenticated return, or filing acceptance.

## Current official route

- Seu: `https://web.gencat.cat/ca/seu-electronica`
- Official electronic-register service: `https://web.gencat.cat/ca/seu-electronica/serveis-de-la-seu/registre-electronic/`
- The service currently redirects to `https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?category=72461610-a82c-11e3-a972-000c29052e2c`.
- The signed presentation action currently enters `https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2FPeticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c`.
- The OVT page identifies the service as **Petició genèrica amb signatura electrònica** and requires a valid digital-identification mechanism before the HTML form.

## Decisive certificate boundary

Following only the public identification entry reaches VALId at `https://valid.aoc.cat/o/oauth2/auth`. The current first-party script `https://valid.aoc.cat/o/oauth2/js/login.js` (SHA-256 `cfeb53df13636f043ebc3ff71cb18272526af771e7af4ecc1d0e78b0138212e2`) implements the digital-certificate choice by changing the login form action to `https://cert.valid.aoc.cat/o/oauth2/cert`.

A TLS 1.2 handshake to `cert.valid.aoc.cat:443` on 2026-08-19 returned a TLS `CertificateRequest`; the advertised client-certificate types included RSA signing and ECDSA signing, and the server supplied no client-certificate CA-name list. No client certificate or private key was supplied for this observation.

## Bounded implementation decision

Implement a QA-only `CLIENT_TLS_AUTH` profile whose start URL is the exact official electronic-register service and whose certificate request is restricted to HTTPS port 443, origin `https://cert.valid.aoc.cat`, path `/o/oauth2/cert`, and the exact observed VALId source URL. The current literal `state=state` value is pinned rather than generalized; any source-URL drift fails closed until revalidated. Preserve the intermediate `tramits.gencat.cat`, `ovt.gencat.cat`, and `valid.aoc.cat` origins as browse-only redirects for the selected profile.

Unknown later fields remain conservative: no signing client, signature format, signature algorithm, signing callback, final filing endpoint, or E2E acceptance is asserted. No authentication, cryptographic signature, final filing, registration, submission, or payment was performed during this evidence pass.
