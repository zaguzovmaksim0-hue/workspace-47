# MITECO / REG-AGE alias evidence — 2026-08-17

- Inventory surface: `age-ministerio-para-la-transicion-ecologica-y-el-reto-demografico` (`ES-PUB-0079`).
- Safe unauthenticated GET of the official MITECO Costas public-information page returned HTTP 200 with the same final URL; no session was stored or replayed.
- The page states that users with a valid electronic certificate or DNIe may use the Registro General Electrónico de la AGE and publishes the exact destination `https://reg.redsara.es/es/`.
- The same page states that the concrete documentation window ran from 30 June 2026 through 27 July 2026. This implementation records the exact delegation contract and does not claim that this particular phase remains open.
- Workspace-47 already has the QA-only `reg-age-redsara` profile whose immutable `startUrl` is exactly `https://reg.redsara.es/es/`.
- This change is only a public-catalog alias: retain the MITECO page as `entryUrl`, use exact REG-AGE as `launchUrl`, and reuse `reg-age-redsara` without adding a MITECO trusted origin, signing endpoint, algorithm, callback, certificate rule, or cryptographic ABI.
- Release remains fail-closed. No authentication, certificate selection, signature, form completion, POST, or administrative submission was performed.
