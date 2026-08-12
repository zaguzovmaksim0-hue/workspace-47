# Aragón SIRAW Profile Activation Design

## Objective

Activate the already reviewed Gobierno de Aragón SIRAW contract as a QA-only signing profile. Reuse the existing local detached CAdES implementation, expose the portal through the native catalog, and preserve the current fail-closed trust model. This milestone does not claim portal acceptance or enable Storage/Retrieve.

## Current state

- The public catalog contains `aragon-siraw` as `VERIFIED_CONTRACT` without a `profileId`.
- `LocalCadesDetachedAdapter` and its cryptographic tests already exist.
- The adapter is not registered in `BuiltInProtocolAdapterRegistry`, instantiated in `MainActivity`, or represented in `site_profiles_v1.json`.
- The reviewed contract is limited to the exact origin `https://aplicaciones.aragon.es`, a 20-byte access challenge, `SHA1withRSA`, detached CAdES, and exact properties `mode=explicit` plus `filter=nonexpired`.

## Architecture

Add a seventh `SiteProfile` named `aragon-siraw`, with `VERIFIED_CONTRACT` and `QA_ONLY`. The profile has one `SIGN` policy and no network endpoint because the signature is produced locally and returned through the existing MiniApplet callback. Bind the profile to `LocalCadesDetachedAdapter.ID`, instantiate that adapter in `MainActivity`, and add the profile binding to the generated public catalog.

The profile must not add StorageService or RetrieveService to the runtime endpoint allowlist. Their existence is documented evidence only; this login milestone neither calls them nor grants WebView trust to them.

## Security boundaries

- Exact initiator origin: `https://aplicaciones.aragon.es`.
- Exact start URL: `https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw`.
- Exact algorithm: `SHA1_WITH_RSA`; the profile requires `LEGACY_SHA1`.
- Exact format and packaging: detached CAdES.
- Exact extra properties: `mode=explicit` and `filter=nonexpired`; no additional properties.
- Exact challenge length: 20 bytes, enforced by `LocalCadesDetachedAdapter`.
- Activation: `QA_ONLY`; release builds do not resolve the profile.
- Status remains `VERIFIED_CONTRACT` until a physical-device portal E2E succeeds.

## Data flow

1. The user opens SIRAW from `PortalCatalogScreen`.
2. The catalog resolves `aragon-siraw` through `SiteProfileRegistry` and opens only its canonical `startUrl`.
3. The page invokes the existing MiniApplet bridge.
4. `ProtocolInputAdapter` validates the exact profile operation tuple.
5. `BuiltInProtocolAdapterRegistry` resolves `LocalCadesDetachedAdapter.ID`.
6. `SigningCoordinator` signs the adapter-produced CMS signed attributes with the unlocked RSA identity.
7. `LocalCadesDetachedAdapter` inserts the RSA signature, validates the detached CMS/CAdES result, and returns it through the existing callback contract.

## Testing

- Parser tests prove the exact profile metadata and reject widened origin, algorithm, format, mode, or properties.
- Registry tests prove QA resolution and release exclusion.
- Protocol registry tests prove the exact profile-to-adapter binding.
- Coordinator/bridge tests prove routing to the local CAdES adapter and rejection under another active profile.
- Catalog generator and repository tests prove `profileId=aragon-siraw` and `E2E_PENDING` without status inflation.
- Existing cryptographic tests remain the authority for CMS/CAdES correctness and tamper rejection.

## Completion criteria

- Focused tests pass.
- Full debug and QA unit suites pass.
- Debug and QA lint and APK assembly pass.
- The generated catalog is reproducible.
- No existing profile or catalog entry regresses.
- No E2E or release-support claim is added.
