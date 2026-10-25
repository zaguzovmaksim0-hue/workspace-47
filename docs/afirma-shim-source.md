# Afirma shim source composition

## Source and packaged resource

The ordered files in `config/afirma_shim/manifest.json` are the editable source sections. `tools/generate_afirma_shim.py` concatenates their exact UTF-8 bytes into four tracked raw resources, in this fixed order:

1. `afirma_shim_bootstrap.js`: source sections 00–20 (bootstrap, diagnostics and contracts).
2. `afirma_shim_cantabria.js`: section 25 (optional stateless Cantabria exact-tuple matcher).
3. `afirma_shim_handlers.js`: sections 30–80 (interception, request lifetime, signing, batches and results).
4. `afirma_shim_installation.js`: sections 90–99 (observation, wrappers, installation and native URI forwarding).

The Kotlin assembler always includes bootstrap, handlers and installation, and includes Cantabria only when the existing `cantabria` compatibility flag is true. The closed enum manifest fixes this order and maps to explicit `R.raw` IDs; page code and catalog data cannot register handlers. There are no added separators, headers or wrappers. They retain one IIFE and the existing initializer order. Missing, blank, duplicated or reordered manifest entries abort assembly; the generator rejects unlisted packaged fragments. Resource read failures propagate, and the aggregate bounded read and final configured 96 Ki-character limit apply to the whole script. There is no production monolith or legacy fallback; the frozen legacy reference lives only under test resources.

The original mechanical decomposition preserved the complete packaged resource from main `04d67ba69cf8f722be151a2716542f26124ebf72`: **72,186 bytes**, SHA-256 `1aa66f0381553973a642df4ffc38f46215ce04a810f79a31ca31fe5e46cdc26d`. The regression fixture pins that baseline for this refactor. It is not a new runtime authenticity mechanism.

The source sections separate bootstrap/diagnostics/contracts, Euskadi client-auth interception, shared request lifetime, single signing, Melilla/Lugo batch, CAIB batch, setup/results, observation/wrapping, installation and native-URI forwarding. They remain in the existing shared lexical closure. Cantabria is the first optional module: a private matcher replaces only its inline tuple predicate at the same dispatch position, after UGR and before JCCM. A non-match continues through the existing generic validation; a match with invalid encoding or callbacks is still rejected by the unchanged shared validation, without falling through to another signer. Encoding, dispatch, pending state, replies and navigation cancellation remain core-owned. All 24 configuration tokens remain in the mandatory bootstrap, including the Cantabria flag when its body is omitted. No catalog/schema or runtime capability policy changes are involved; other portal bodies remain mandatory.

## Editing and regeneration

Edit only the source sections, then run:

```sh
python tools/generate_afirma_shim.py --write
python tools/generate_afirma_shim.py --check
python -m unittest tools.tests.test_generate_afirma_shim -v
```

Without flags the tool checks only; writing requires `--write`. An intentional behavioral change must update its applicable behavioral regressions and the legacy-output assertions. The existing Python discovery gate checks that source and packaged bytes agree; no Gradle/Python build dependency or workflow change is introduced. Android consumers retain the `AfirmaJavascriptShim.load` facade, which opens explicit `R.raw` IDs for the ordered fragments.

Composition rejects duplicate/unsafe names, missing/unlisted JavaScript sections, unsupported manifest versions/fields, duplicate manifest keys, linked source files/directory, empty/non-UTF-8/CRLF or unterminated sections, and bounded-size violations. The 96 Ki-character bound counts UTF-16 units, matching Kotlin's runtime `String.length`, including supplementary characters. This development tool assumes a trusted checkout; it is not a filesystem sandbox or adversarial concurrent-editor lock.

Each output replacement is explicit and atomic, preserving an existing file's mode. Failure before that replacement retains the previous fragment and removes its temporary file; `--check` detects any incomplete multi-file regeneration. Output symlinks are rejected.

## Verification boundary

No portal origin, exact-match contract, algorithm, request/reply protocol, signing capability, QA flag, placeholder substitution, Gradle configuration, permission or security baseline changes in this refactor. The frozen legacy fixture remains unchanged. Tests undo only the explicit Cantabria predicate extraction to compare every other configured byte, and compare the extracted predicate and constants with that fixture. The JVM assembly tests compare configured facade output against the test-only legacy oracle with that bounded extraction normalization for every packaged profile flag combination, defaults, each single flag and all flags, in both modes with QA on/off. JVM fragment tests load real raw files, cover manifest/read failures, and verify that disabling Cantabria skips only its resource and leaves the core identical apart from the flag value. JavaScript parsing and focused Python regressions are local evidence; canonical exact-head Android/Python/Go/security CI remains required before integration. Real WebView, device and portal acceptance is not claimed.
