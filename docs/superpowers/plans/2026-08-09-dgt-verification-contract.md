# DGT verification contract implementation plan

## TDD order

1. Add the smallest focused failing tests first and run them. Record the RED
   command and the expected failure before production/config changes.
2. Make the minimum implementation changes below.
3. Run focused GREEN tests, the canonical catalog generator/tests, diff
   checks, and inspect the complete diff.
4. Commit the worker result atomically on the assigned branch. Do not update
   broad ledgers, reports, or handoff documents.

## Exact change set

### Profile/parser/catalog contract

- Add `dgt-verificacion-equipo` to `config/site_profiles_v1.json`, preserving
  the existing catalog and Aragón values.
- Extend the endpoint-less MiniApplet CAdES branch in
  `SiteProfileCatalogParser` only for the exact DGT property map
  `{"filter":"nonexpired:"}`; retain Aragón's exact
  `{"mode":"explicit","filter":"nonexpired"}` branch and reject any
  other endpoint-less shape.
- Update the `dgt-sede` inventory record to the evidence packet's exact child
  URL, `MiniApplet`, the evidence-proven local CAdES contract fields, truthful
  `IMPLEMENTED_NOT_E2E` state, refreshed primary evidence IDs/reason/date/gate,
  and `endpoint: "NO_VERIFICADO"`.
- Regenerate the public catalog with:

  ```bash
  python tools/generate_public_portal_catalog.py \
    --source docs/compatibility/all-spanish-public-portals-inventory.md \
    --profiles config/site_profiles_v1.json \
    --output app/src/main/res/raw/public_portal_catalog_v1.json
  ```

### Signing boundary

- Change `CadesDetachedCodec.createPreSign`, `complete`, and `validate` to
  take an exact caller-supplied content length (bounded by a shared safe
  maximum). No caller may omit the bound.
- Keep `LocalCadesDetachedAdapter`'s `CHALLENGE_BYTES == 20` check and
  properties unchanged; pass 20 to the codec.
- Add `DgtVerificationCadesAdapter` with exact constants, 15-byte content
  gate, exact property gate, and shared codec calls passing 15.
- Add the DGT binding to `BuiltInProtocolAdapterRegistry`. Instantiate the
  adapter in `MainActivity` and resolve it by ID so catalog implementation
  status reflects a real runtime binding; no endpoint transport is added.

### Tests

- `SiteProfileCatalogParserTest`: exact DGT fields, QA-only status, release
  exclusion, and rejection of altered endpoint-less property shapes.
- `MiniAppletBridgeAdapterTest`: exact DGT request accepted and wrong active
  profile/origin/payload/property rejected.
- `DgtVerificationCadesAdapterTest`: detached verifiable CAdES for the exact
  data and rejection of every requested tuple/property/byte mismatch.
- `ProtocolAdapterRegistryTest`: exact DGT binding and no accidental binding
  reuse.
- `PublicPortalCatalogParserTest`/`PortalCatalogRepositoryTest` and the
  generator test: exact URL/profile/status/capabilities/format binding,
  `E2E_PENDING`, and no release enablement.
- Existing `LocalCadesDetachedAdapterTest` remains green and is extended only
  as needed to prove the 20-byte Aragón contract still holds.

## Focused commands

Expected RED command (after tests, before implementation):

```bash
./gradlew --no-daemon testDebugUnitTest \
  --tests 'dev.junta.firmamobile.profile.SiteProfileCatalogParserTest' \
  --tests 'dev.junta.firmamobile.browser.MiniAppletBridgeAdapterTest' \
  --tests 'dev.junta.firmamobile.signing.DgtVerificationCadesAdapterTest' \
  --tests 'dev.junta.firmamobile.signing.ProtocolAdapterRegistryTest' \
  --tests 'dev.junta.firmamobile.catalog.*'
```

Focused GREEN uses the same command after implementation, followed by:

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog -v
git diff --check
git diff --stat
git diff
```

The orchestrator owns the broad audit ledger/test report/handoff and full
repository gates.
