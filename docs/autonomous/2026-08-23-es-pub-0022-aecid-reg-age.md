# ES-PUB-0022 — AECID → REG-AGE bounded public evidence

Reviewed 2026-08-23. `https://www.aecid.gob.es/` currently identifies itself as “Sede electrónica AECID”. Its first-party `https://www.aecid.gob.es/registro-de-solicitud` page states that other electronic requests/writings/communications without a dedicated electronic procedure may use the Registro Electrónico General (REG), and the published REG icon links directly to `https://reg.redsara.es/`.

The same AECID page documents that REG identifies citizens with a recognized digital certificate or DNIe. This is descriptive evidence for the delegated REG service, not a new AECID client-TLS or signing ABI. Workspace-47 therefore reuses only the existing QA-only `reg-age-redsara` canonical start `https://reg.redsara.es/es/`; it does not add AECID origins to that profile or claim an AECID-specific signer/authentication contract. No authentication, private-key action, signature or administrative submission was performed.
