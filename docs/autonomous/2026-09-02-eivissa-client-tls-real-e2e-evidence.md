# Eivissa Instancia General CLIENT_TLS evidence — 2026-09-02

Scope: public unauthenticated inspection only. No administrative submission was performed.

- Exact procedure: `https://seu.conselldeivissa.es/sta/CarpetaPublic/Public?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269002703260065905043` (PID `6269002703260065905043`).
- Live procedure DOM exposes exactly `Registro electrónico` with `javascript:enterNewReg(window.catser.dboid,'es');`.
- Live first-party auth SPA: `https://seu.conselldeivissa.es/sta/reg/auth/es/6269002703260065905043`.
- The auth SPA exposes a dedicated `CERTIFICADO DIGITAL` anchor with exact target `/sta/reg/auth/do/CERT/es/6269002703260065905043`.
- A TLS 1.2 GET to `https://seu.conselldeivissa.es/sta/reg/auth/do/CERT/es/6269002703260065905043` triggers server renegotiation: `HelloRequest`, then a TLS `CertificateRequest`. Without a client certificate the server terminates with `handshake_failure`.
- This establishes a same-origin CLIENT_TLS_AUTH boundary for the exact PID. It does not establish successful authenticated return, document signing acceptance, or administrative submission.
- REAL E2E should therefore use an exact one-shot `IN_PLACE_FROM_SOURCE` client-auth grant and may proceed to the existing CAdES signing contract only after the authenticated flow returns. Administrative presentation remains outside this evidence step.
