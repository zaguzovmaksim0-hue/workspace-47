# DGT verification contract design

## Scope

Implement the isolated `dgt-sede` QA-only `VERIFIED_CONTRACT` candidate from
the public evidence packet. The change must preserve all existing Aragón
behavior and must not claim portal acceptance, an endpoint, authentication,
or release eligibility.

## Exact contract

The profile ID is `dgt-verificacion-equipo`, version `1`, with:

- compatibility `VERIFIED_CONTRACT` and activation `QA_ONLY`;
- start URL
  `https://sede.dgt.gob.es/es/otros-tramites/verificacion-de-equipos-firmas-y-certificados/verificacion-de-mi-equipo/`;
- only initiator origin `https://sede.dgt.gob.es`;
- empty redirect origins, trusted-browse origins, and endpoint list;
- `SIGN` and `LEGACY_SHA1` capabilities only;
- RSA certificates with the digital-signature key-usage requirement;
- `miniapplet-autoscript-v1`, `miniapplet-sign-callback-v1`, `CADES`,
  `SHA1_WITH_RSA`, `DETACHED`, `EXPLICIT`;
- fixed properties exactly `filter=nonexpired:` and no allowed properties.

The exact observed JavaScript call is:

```text
MiniApplet.sign("Q2FkZW5hIGEgZmlybWFy","SHA1withRSA", "CAdES", "filter=nonexpired:", saveSignatureFuntion, showErrorFuntion);
```

The adapter must accept only the decoded UTF-8 bytes `Cadena a firmar`
(exactly 15 bytes), the exact DGT profile/version/origin/protocol tuple, and
the exact canonical property string. Every mismatch returns a failure without
producing a signature.

## Interfaces and files

- `SiteProfileCatalogParser.kt`: retain the current strict endpoint-less
  MiniApplet CAdES validation and add exactly one accepted fixed-property
  shape for DGT: `filter=nonexpired:` with no `mode`, endpoint, or other key.
- `LocalCadesDetachedAdapter.kt`: generalize only `CadesDetachedCodec` with a
  caller-supplied exact content length. Aragón keeps its adapter-level 20-byte
  check and exact `mode=explicit\\nfilter=nonexpired` properties.
- `DgtVerificationCadesAdapter.kt`: implement
  `SigningProtocolAdapter` with `dgt-verificacion-equipo-local-cades-v1`,
  exact request tuple and payload/property checks, and the shared detached CMS
  pre-sign/complete codec.
- `ProtocolAdapterRegistry.kt`: bind the DGT profile to the DGT protocol and
  MiniApplet callback contract.
- `config/site_profiles_v1.json`: add the exact profile and evidence URLs;
  no endpoint field is introduced.
- `docs/compatibility/all-spanish-public-portals-inventory.md`: update only
  `dgt-sede` to the verified child URL and evidence-proven fields/status.
- `app/src/main/res/raw/public_portal_catalog_v1.json`: regenerate through
  `tools/generate_public_portal_catalog.py`; do not hand-edit it.
- Focused tests under `profile`, `browser`, `signing`, and `catalog`: cover
  parsing/release exclusion, bridge fail-closed behavior, detached CAdES
  verification, registry binding, generated catalog binding/status, and the
  unchanged Aragón contract.

## Fail-closed invariants

1. No DGT request is accepted unless the active profile is the exact DGT
   profile and the source origin is the exact DGT origin.
2. The bridge still enforces the profile operation's exact algorithm, format,
   fixed properties, callback, and adapter binding.
3. DGT signing accepts only 15 bytes equal to `Cadena a firmar`; the shared
   codec accepts only the caller-supplied bounded length.
4. The DGT adapter rejects all profile, version, origin, protocol ID,
   algorithm, format, payload, property, certificate, or state mismatches.
5. No endpoint, redirect origin, trusted-browse origin, Storage/Retrieve
   behavior, or release enablement is added.
6. Aragón's exact 20-byte/property gates and generated behavior remain
   unchanged.
7. The profile remains absent from the release registry; the public catalog is
   `E2E_PENDING` with inventory `IMPLEMENTED_NOT_E2E` and only evidence-proven
   capabilities/format metadata.

## Verification boundary

All cryptographic tests use the repository's synthetic identity and local
codec only. No portal navigation, network request, credential, certificate
file, private key, or real submission is used.
