# ES-PUB-0149 — Diputació de Castelló — bounded public-launch evidence

Reviewed: 2026-08-23

## Current public chain

- Institutional portal `https://www.dipcas.es/es/` returned HTTP 200 and currently links **Sede electrónica** to `https://dipcas.sedelectronica.es/info.0`.
- The delegated Sede returned HTTP 200 and its **Trámites** navigation resolves to `https://dipcas.sedelectronica.es/dossier.0`.
- The public dossier lists **Instancia General** at `https://dipcas.sedelectronica.es/catalog/t/5161fa8d-970e-4b48-a506-b2ac34ceafe5`.
- The procedure page returned HTTP 200, identifies **Código SIA: 1881117**, and publishes **Iniciar tramitación electrónica** at `https://dipcas.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5`.
- An unauthenticated GET to the exact start URL returned the Sede's **Identificación electrónica** page. The page exposes a POST form to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider` with `SAMLRequest` and `RelayState` fields.

## Implemented boundary

The Android profile is intentionally QA-only and limited to exact navigation to `https://dipcas.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5` on the exact origin `https://dipcas.sedelectronica.es`.

It does **not** model or enable the observed Cl@ve POST, certificate selection, client TLS, document upload, signing, submission, registration, or payment. `redirectOrigins`, `trustedBrowseOrigins`, `endpoints`, `operationPolicies`, and `capabilities` remain empty and `clientAuthPolicy` remains null.

No credential, SAMLRequest, private-key operation, document signature, upload, final filing/registration/submission, or payment was performed during this evidence pass.
