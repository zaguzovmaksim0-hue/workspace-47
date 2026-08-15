# Murcia CARM public AutoFirma probe — bounded CMS evidence

## Scope

This implementation is deliberately limited to the public CARM AutoFirma test surface. It does **not** claim support for an administrative submission procedure.

- Catalog surface: `murcia-sede` (`ES-PUB-0113`).
- Exact QA launch surface: `https://sede.carm.es/cryptoApplet/ayuda/probarautofirma.html`.
- Exact observed signer tuple recorded from prior first-party public probe inspection on 2026-08-13: `SHA256withRSA`, wire format `CMS/PKCS#7`, `filters=nonexpired:`, `mode=implicit`.
- The architecture returns an attached CMS/PKCS#7 `SignedData` result through the local MiniApplet callback boundary only.

## 2026-08-15 revalidation

The current official CARM FAQ still links a “Prueba de firma con AutoFirma” surface. A normal TLS-verified unauthenticated GET to the exact probe was attempted. The request reached the CARM infrastructure but the runtime was replaced by a Radware WAF challenge. No challenge bypass, cookie replay, TLS weakening, authentication, POST, certificate operation, signature, StorageService/RetrieveService request, or administrative action was performed.

Because the live signer runtime could not be re-read safely on 2026-08-15, the cryptographic tuple remains pinned to the prior 2026-08-13 first-party observation rather than being silently generalized from the administrative procedure page.

## Implementation boundary

The QA profile and native bridge require the exact probe URL, exact CARM origin, exact SHA-256/RSA algorithm, exact CMS/PKCS#7 wire token, attached/implicit mode, and exact two-line properties. The local adapter rechecks certificate validity at signing time and uses the central local-signature engine for RSA signing. Plain CMS signed attributes are used; CAdES-only `SigningCertificateV2` is intentionally absent.

No remote storage/retrieval or portal result endpoint is invoked. Release remains disabled until sanitized physical acceptance evidence exists.
