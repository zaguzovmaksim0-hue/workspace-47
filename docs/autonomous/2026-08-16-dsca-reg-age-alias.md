# DSCA → REG-AGE exact delegation — 2026-08-16

## Public surface

- Institution: Ministerio de Derechos Sociales, Consumo y Agenda 2030.
- Exact first-party page: `https://www.dsca.gob.es/es/derechos-sociales/derechos-animales/premios/artisticos/v-certamen-clipmetraje`.
- The live page states an application window from 13 July 2026 through 10 October 2026 at 23:59 (GMT+2).
- The primary route is the Ministry form at `https://dsca.sede.gob.es/procedimiento/portada?idProc=155723`.
- The same page explicitly offers an alternative filing route through Registro Electrónico General and links exactly to `https://reg.redsara.es/es/`, directed to Dirección General de Derechos de los Animales (E05080001), Ministerio de Derechos Sociales, Consumo y Agenda 2030 (E05235701).

## Bounded implementation

The catalog entry preserves the Ministry page as `entryUrl` and binds `launchUrl` exactly to the existing QA-only `reg-age-redsara` profile start URL. This is an exact delegation alias; `www.dsca.gob.es` is not added to signing trust and no DSCA-specific signing ABI is inferred. The Ministry's own form remains outside this profile.

## Safety / verification boundary

Research used safe GET requests only. No certificate was selected, no signature was produced, no form was completed, and no administrative submission was performed. The entry remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` until a safe physical transition test can be performed without submitting a candidacy. Release remains fail-closed.
