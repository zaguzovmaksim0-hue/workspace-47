# ES-PUB-0173 — Diputación Provincial de Teruel — bounded Instancia General evidence — 2026-08-21

## Scope

Current public HTTPS review only. A transient session cookie was accepted solely to make the official sede pages stable and was deleted at the end of each request batch. No authentication, administrative POST, document upload, private-key operation, filing, registration, submission, or payment was performed.

## Decisive current observations

- `https://dpteruel.sedelectronica.es/` resolves to the official Teruel sede and returns stable public HTML when its issued session cookie is replayed transiently.
- The current public catalog at `/dossier` exposes **Instancia General** at `https://dpteruel.sedelectronica.es/catalog/t/5161fa8d-970e-4b48-a506-b2ac34ceafe5`.
- That procedure page identifies **Código SIA: 2094606** and links **Iniciar tramitación electrónica** exactly to `https://dpteruel.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5`.
- Opening that exact telematic URL reaches the same-origin **Identificación electrónica** boundary and states that identification is required, with access through **sistema Cl@ve**.

## Implemented boundary

The candidate models only the exact QA navigation launch above. It deliberately exposes no native signing, certificate-selection, client-TLS, endpoint, format, or algorithm capability. Authentication and all later form/document/signature/final-registration states remain outside the implemented contract and `NO_VERIFICADO` where applicable.
