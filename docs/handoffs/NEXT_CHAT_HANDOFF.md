# NEXT CHAT HANDOFF — workspace-47 autonomous portal-first cycle

Updated: 2026-08-09, generation 36.

## Repository state to verify first

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on
  `agent/workspace-47-autonomous-20260803`.
- G36 research started from published autonomous base
  `80b95d3ef8876438156f42b287ff37bfe579e976`; this handoff/ledger update is the docs-only successor
  to that base. Resolve its exact published HEAD with `git rev-parse HEAD` after `git fetch --prune
  origin` rather than assuming the parent SHA is current.
- Canonical `origin/feature/ws024-secure-tunnel-20260728` must remain exactly
  `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- Verify branch, HEAD, upstream, divergence and tracked cleanliness before every mutation. Never
  merge/rebase/force-push or mutate canonical.

## Published milestones already complete

- Certificate read hardening: `0bf8f5767fa1104fd6d2bb951709484e1009d0e1`.
- Portal Coverage First planning: `758347a21301313656f106beb02ebbc847f8cb17`.
- DGT implementation/evidence: `fc52b0a68348f0f26e4ac368526ba7b58f62972f` and
  `136ae1ca6fc49fb5e877321dc4451e39f1ea0600`.
- UGR certificate contract: `ce06961f976c363280988cba81ca89e682dcc3b3`; profile remains
  `VERIFIED_CONTRACT` / `QA_ONLY`, inventory `IMPLEMENTED_NOT_E2E`, catalog `E2E_PENDING`.
- Current Cloud-Gradle/Matt-Pocock migration baseline above UGR:
  `483e917` → `1f969b8` → `c578a46` → `599961e` →
  `80b95d3ef8876438156f42b287ff37bfe579e976`. Preserve these commits and use
  `docs/agents/matt-pocock-workflow.md` plus `docs/agents/codex-cloud-gradle.md`.

## Current portal KPI

- 182 public entries; 10 exact profile bindings; 172 unbound.
- Inventory: 166 BROWSE_ONLY, 5 IMPLEMENTED_NOT_E2E, 1 VERIFIED_CONTRACT, 4 VERIFIED_E2E,
  4 INACCESSIBLE, 2 UNSUPPORTED_PROTOCOL.
- Generated catalog: 94 CATALOGED, 73 DISCOVERED, 6 BLOCKED, 5 E2E_PENDING, 4 E2E_VERIFIED.
- Research buffer: 16 classified public surfaces; implementation-ready unintegrated queue 0;
  native Codex/Luna implementation occupancy 0/8.
- Portals integrated in generation 36: 0.

## Generation 36 research conclusions

- `justicia-sede-judicial`: global first-party helper is exact
  SHA256withRSA/PAdES/mode=implicit, but five selected public procedure pages plus the trámite index
  still lack the matching `documentoDeclaracion`/`formFirmaBorrador` DOM and `firma()` binding.
  Static global-library presence remains insufficient.
- `age-acceda`: public `/certificado/valida` form calls `afirma.firmar(callback)` with a changing,
  server-issued `formularioweb`. First-party helper code includes several signing paths, including
  SHA1withRSA/PAdES `doSignSolicitud()`, but the defining wrapper linkage was not found. Do not guess
  the target helper and never hard-code `formularioweb`.
- `asturias-sede-tramite-autofirma`: official public
  `https://miprincipado.asturias.es/utilidades/comprobacion-firma` now provides a concrete inline
  `MiniApplet.sign('SG9sYQ==', getAlgoritmoFirma(), 'XAdES', getParamsFirma(), ...)` call and callback.
  It is near-ready, but the first-party `www30.asturias.es/Esign2/esign.jsp` helper defining algorithm
  and parameters could not be retrieved due a bounded `CONNECT ... 502`; do not infer them.
- SEPE, Ministerio de Justicia, Sevilla, Universidad de Sevilla and Catastro still lack a complete
  public algorithm/format/payload/callback contract.

## Exact next eight implementation candidates

1. `justicia-sede-judicial` — find a concrete public procedure page/form that actually binds the
   known `firma()` helper.
2. `age-acceda` — locate the first-party definition/runtime linkage of `afirma.firmar()` and its
   exact treatment of dynamic `formularioweb`.
3. `sepe-sede` — locate a public pre-auth exact invocation; do not cross the login boundary.
4. `mjusticia-sede` — locate an exact public signing ABI/callback before authentication.
5. `sevilla-sede` — locate a public procedure invocation, not FAQ text.
6. `us-sede` — locate portal-specific signing JS/ABI beyond certificate-access requirements.
7. `age-direccion-general-del-catastro` — establish exact public signing or Client-TLS contract.
8. `asturias-sede-tramite-autofirma` — recover the official `esign.jsp` definitions for
   `getAlgoritmoFirma()` and `getParamsFirma()` through a normal verified public path; do not guess.

When any candidate becomes implementation-ready, create one isolated worker worktree/branch per
candidate, route implementation through Matt Pocock `codex/implement` + `codex/tdd`, commit and push
the worker before Gradle, and use `$HOME/bin/w47-cloud` against the exact pushed SHA. Fill every ready
native Codex/Luna implementation slot up to 8/8; do not use review work to occupy implementation
capacity while a ready portal waits.

## Verification and manual gates

- Last product full JVM gate remains G35 `job_20260809_112549_86ad3219`: Debug 590/590 and QA
  590/590, zero failures/errors/skips. Last non-Android gate remains
  `job_20260809_113452_d6c2fa05`: Python 103 with one known hardlink skip, CiPolicy 20/20, Go
  test/vet/build PASS. Lint recovery `job_20260809_114615_8f54482e`, artifact gate
  `job_20260809_115034_135469ba`, and release fail-closed `job_20260809_115102_4ad9f309` passed.
- G36 made no product/config/catalog behavior change, so it intentionally ran no Gradle command.
- Physical UGR and DGT portal E2E remain manual. Existing manual/external gates remain AEAT
  Client-TLS E2E, real-portal JavaScript-dialog compatibility, TalkBack/physical visual accessibility
  and Go race on supported Linux.
- No APK installation/launch, ADB/UIAutomator/device control, authenticated portal navigation,
  credentials/private-certificate use, real signing, form POST, upload, payment or administrative
  submission occurred in G36.
