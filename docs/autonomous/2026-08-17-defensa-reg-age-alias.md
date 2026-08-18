# Ministerio de Defensa → REG-AGE delegation evidence — 2026-08-17

## Public first-party evidence

- Official electronic office: `https://sede.defensa.gob.es/`.
- An unauthenticated public GET returned the current Sede Electrónica Central del Ministerio de Defensa.
- The page publishes multiple links labelled **Registro Electrónico General AGE** and points those links to `https://rec.redsara.es/`.
- A public unauthenticated request to that legacy REC endpoint returned HTTP `301` with `Location: https://reg.redsara.es/`.
- Public browser navigation reaches `REG - Registro Electrónico General` on `reg.redsara.es`; locale selection is negotiated by the service, so the evidence does not claim that the legacy root itself has a fixed `/es/` redirect.
- Workspace-47 already contains the QA-only `reg-age-redsara` profile with canonical Spanish `startUrl` `https://reg.redsara.es/es/`.

## Bounded implementation

`ES-PUB-0063` keeps the official Defensa Sede as `entryUrl` and binds only to the existing REG-AGE profile using its exact canonical Spanish `startUrl` as `launchUrl`. The Defensa origin is not added to REG-AGE initiator, redirect, or browse trust. No Defensa-specific signing ABI, endpoint, algorithm, certificate rule, AutoFirma behavior, client-TLS rule, or cryptographic contract is inferred.

The Sede also exposes separate Ministry-native procedures, some of which mention certificate/Cl@ve requirements. Those flows are explicitly outside this alias and are not used to broaden the profile contract.

Status remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Research used only public GET/navigation. No login, certificate selection, private key, signature, form completion, upload, payment, or administrative submission was performed.
