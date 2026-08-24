# ES-PUB-0033 — CNMV public Sede boundary

Reviewed 2026-08-23 using unauthenticated first-party HTTP navigation only. `https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx` returned HTTP 200 and identifies itself as the CNMV electronic Sede / electronic register. The page documents valid electronic certificates, use of the public zone by natural persons with an electronic certificate, and a signing platform that points users to the official electronic-signature application.

This evidence is sufficient only for an exact QA-only public-navigation profile. It is not sufficient to infer a signer ABI, algorithm, format, packaging, callback, client-TLS contract, certificate-selection bridge, or final-filing protocol. Those capabilities remain disabled and outside trust.

No certificate was selected, no authentication was completed, no document was uploaded, no signing operation was triggered, and no administrative submission was made. Transient ASP.NET session material observed during the public request was not stored in the repository.
