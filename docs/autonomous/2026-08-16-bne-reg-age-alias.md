# BNE → REG-AGE exact delegation — 2026-08-16

## First-party evidence

- BNE official `Quejas y sugerencias` procedure page: `https://sede.bne.gob.es/es/tramites/quejas-sugerencias`.
- The page explicitly offers the Registro Electrónico General as an electronic route for complaints or suggestions addressed to the Biblioteca Nacional de España.
- Its public `Registro Electrónico General` link resolves to the existing REG-AGE start URL `https://reg.redsara.es/es/`.

## Bounded implementation

`age-biblioteca-nacional-de-espana` keeps the BNE page as its catalog `entryUrl` and uses only the exact REG-AGE URL as `launchUrl`. The generated catalog therefore binds the entry to the existing `reg-age-redsara` profile in QA without treating `sede.bne.gob.es` as a signing origin or inventing a BNE-specific signature ABI.

The entry is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Release remains fail-closed until a safe physical transition BNE → REG-AGE is validated. No request, signature, certificate, credential, or administrative submission was sent while collecting this evidence.
