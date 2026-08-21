# ES-PUB-0006 — Import@ss exact certificate entry and client-TLS boundary

Reviewed: 2026-08-21

## Bounded result

The current Import@ss public flow proves one narrow authentication capability: `CLIENT_TLS_AUTH` for the certificate/DNIe route. This evidence does not establish document signing, a signing ABI, signature format/algorithm, upload, filing, registration, submission, payment, or accepted E2E authentication on Android.

## Current public chain

1. The official Import@ss help page states that online access can use SMS, Cl@ve, or an electronic certificate/DNIe.
2. The current `Área personal` action opens `https://portal.seg-social.gob.es/wps/myportal/importass/importass/personal/`.
3. Import@ss creates a SAML identity request to `https://idp.seg-social.es/PGIS/Login`. Session-bound SAML/RelayState values were treated as transient secrets and are not recorded here.
4. The current IdP method page exposes the exact `DNIe o certificado` selection as a POST to `https://idp.seg-social.es/PGIS/Login?seleccion=IPCE`.
5. That selection returns an auto-submit form whose exact target is `https://ipce.seg-social.es/IPCE/Login`; its session-bound hidden fields are not retained.

## TLS observation

A direct TLS 1.2 handshake to `ipce.seg-social.es:443`, without providing any client certificate, received a server `CertificateRequest`. The request advertised client-certificate types `RSA sign`, `DSA sign`, and `ECDSA sign` and supplied acceptable CA names. A forced TLS 1.3 handshake failed, so this evidence only establishes the observed TLS 1.2 client-certificate boundary. A no-certificate HTTP request to `/IPCE/Login` returned `403 Forbidden`.

The app profile therefore permits only RSA/EC identities that satisfy the existing certificate-filter safety rules; DSA is not newly supported by the app. The mTLS request origin is not added to normal browser navigation trust.

## Implemented boundary

- start: `https://portal.seg-social.gob.es/wps/myportal/importass/importass/personal/`
- browser/identity intermediary: `https://idp.seg-social.es`
- exact client-auth source: `https://idp.seg-social.es/PGIS/Login?seleccion=IPCE`
- exact TLS client-auth target: `https://ipce.seg-social.es/IPCE/Login`
- port: `443`
- activation: `QA_ONLY`
- capability: `CLIENT_TLS_AUTH` only
- status: `IMPLEMENTED_NOT_E2E / E2E_PENDING`

No client certificate was supplied during this public evidence pass, and no authentication result, signature, filing, registration, submission, or payment was performed.
