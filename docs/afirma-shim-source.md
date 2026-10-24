# Afirma shim source composition

## Source and packaged resource

The ordered files in `config/afirma_shim/manifest.json` are the editable source sections. `tools/generate_afirma_shim.py` concatenates their exact UTF-8 bytes into the tracked `app/src/main/res/raw/afirma_shim.js` resource. It inserts no separators, headers, wrappers or transformations.

This first decomposition preserves the complete packaged resource from main `04d67ba69cf8f722be151a2716542f26124ebf72`: **72,186 bytes**, SHA-256 `1aa66f0381553973a642df4ffc38f46215ce04a810f79a31ca31fe5e46cdc26d`. The regression fixture pins that baseline for this refactor. It is not a new runtime authenticity mechanism.

The source sections separate bootstrap/diagnostics/contracts, Euskadi client-auth interception, shared request lifetime, single signing, Melilla/Lugo batch, CAIB batch, setup/results, observation/wrapping, installation and native-URI forwarding. They intentionally remain in the existing shared lexical closure and evaluation order. This is source-organization groundwork, **not independent feature modules, tree-shaking, or a changed runtime capability policy**. The single-sign section still contains several exact portal contracts.

## Editing and regeneration

Edit only the source sections, then run:

```sh
python tools/generate_afirma_shim.py --write
python tools/generate_afirma_shim.py --check
python -m unittest tools.tests.test_generate_afirma_shim -v
```

Without flags the tool checks only; writing requires `--write`. An intentional behavioral change must update its applicable behavioral regressions and the packaged-resource fixture. The existing Python discovery gate checks that source and packaged bytes agree; no Gradle/Python build dependency or workflow change is introduced. Android consumers continue loading `R.raw.afirma_shim` exactly as before.

Composition rejects duplicate/unsafe names, missing/unlisted JavaScript sections, unsupported manifest versions/fields, duplicate manifest keys, linked source files/directory, empty/non-UTF-8/CRLF or unterminated sections, and bounded-size violations. The 96 Ki-character bound counts UTF-16 units, matching Kotlin's runtime `String.length`, including supplementary characters. This development tool assumes a trusted checkout; it is not a filesystem sandbox or adversarial concurrent-editor lock.

Output replacement is explicit and atomic, preserving an existing file's mode. Failure before replacement retains the old packaged resource and removes the temporary file. Output symlinks are rejected.

## Verification boundary

No portal origin, exact-match contract, algorithm, request/reply protocol, signing capability, QA flag, placeholder substitution, Kotlin loader, Gradle configuration, permission or security baseline changes in this refactor. Byte equality is the primary no-runtime-change invariant. JavaScript parsing and focused Python regressions are local evidence; canonical exact-head Android/Python/Go/security CI remains required before integration. Real WebView, device and portal acceptance is not claimed.
