# ES-PUB-0119 — Consell de Mallorca institutional portal current evidence — 2026-08-21

## Scope and boundary

This pass covers only `ES-PUB-0119`, the institutional Consell de Mallorca portal. It identifies the
current separate electronic seat and one concrete administrative operation that already has an exact
Workspace-47 QA-only profile. Only public GET navigation was needed for the alias decision. No
certificate was supplied, no private-key operation occurred, no document was signed, and no
filing/registration/submission or payment was performed.

## Current institutional delegation

On 2026-08-21, `https://www.conselldemallorca.es/` returned HTTP 200 and its current markup exposed
`Seu Electrònica` with the exact target `https://seu.conselldemallorca.net/`.

The separate Seu returned HTTP 200 and published the Consell `Registre electrònic` with the exact
Consell operation URL `https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082`. That exact
operation also returned HTTP 200 and identified itself as `Registre Electrònic del Consell de
Mallorca`. The page currently states that the operation requires a digital certificate; later signing
remains outside this alias claim.

## Smallest truthful capability

The selected Registre URL is byte-for-byte equal to the canonical `startUrl` of the existing QA-only
profile `consell-mallorca-sede`. Therefore `ES-PUB-0119` can be represented as an `ALIAS_ONLY`
delegation from the institutional entry to that exact reviewed operation.

The institutional entry remains `https://www.conselldemallorca.es/`. Its own certificate, signing,
endpoint, format and algorithm fields remain `NO_VERIFICADO`; the alias does not expand profile
origins or copy the Sede's client-auth/signing contract onto the institutional origin.

## Decisive current URLs

- `https://www.conselldemallorca.es/` — official institutional entry, current `Seu Electrònica` link.
- `https://seu.conselldemallorca.net/` — official separate Seu, current `Registre electrònic` link.
- `https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082` — exact current Registre operation and existing profile `startUrl`.
