# Generation 45 — Galicia generic-request pre-auth boundary — 2026-08-11

## Scope and safety boundary

This research used only unauthenticated HTTPS GET requests to current official Xunta de Galicia Sede
pages. No authentication, certificate selection, credential/cookie use, form submission, upload,
signing, payment, or administrative action was performed.

## Current public procedure

The current `Solicitude xenérica` page returned HTTP 200 and publishes the active citizen procedure
`PR004A - Presentación electrónica de solicitudes, escritos e comunicacións que non conten cun sistema
electrónico específico nin cun modelo electrónico normalizado`. Its current "Tramitar en liña" link is
`https://sede.xunta.gal/presenta/novo/PR004A_2025_1`.

The public procedure page and the official `Sistemas de identificación e sinatura` page load ordinary
Xunta/Liferay/OpenCMS theme assets. The public pre-auth pages identify admitted certificate/@firma,
Chave365 and Cl@ve mechanisms at the product level but expose no AutoScript/MiniApplet algorithm,
signature format, payload, extra-parameter, callback, or signing-endpoint tuple for PR004A.

A bounded GET to the exact current `presenta/novo/PR004A_2025_1` start returned HTTP 302 to the
same-origin `/identificate/login` authentication boundary. Redirect query details were not retained,
and the authenticated route was not followed.

## Result

`galicia-sede` (`ES-PUB-0112`) remains `BROWSE_ONLY` / research-only. The current public evidence now
binds a live citizen procedure and its exact pre-auth launch, but the signing ABI remains behind the
authentication boundary. No profile, registry, inventory, catalog, release, or E2E promotion is
justified.
