# Generation 45 — Navarra and La Rioja public signing-boundary research — 2026-08-11

## Scope and safety boundary

This research used only unauthenticated HTTPS GET requests to current official public pages. No form
POST, file upload, certificate selection, authenticated navigation, credential/cookie use, signing,
payment, or administrative submission was performed.

## Navarra — Registro General Electrónico

The current official Registro General Electrónico page and the official "Firmar documentos" help page
both returned HTTP 200. The registry page documents certificate/DNIe access and links general online
processing help, but its current public HTML and loaded theme/Liferay assets expose no portal-local
AutoScript/MiniApplet signing tuple, format, algorithm, payload, callback, or signing endpoint.

The official help page explicitly documents AutoFirma as a tool used by some Navarra electronic
procedures, but it is general user guidance rather than a binding for the current Registro General
surface. It does not establish which exact AutoFirma ABI, algorithm, signature format, extra
parameters, or callback applies to the registry entry.

Result: `navarra-sede-registro-general` (`ES-PUB-0114`) remains research-only / `BROWSE_ONLY`; no
profile or catalog promotion is justified from the public pre-auth evidence.

## La Rioja — public file-signing utility

The current Oficina Electrónica page returned HTTP 200 and publicly links an exact utility page,
`/oficina-electronica/utilidades-y-servicios/utilidad-para-firmar-ficheros`. That page also returned
HTTP 200 and embeds the official signing utility from
`https://ias1.larioja.org/copiasverificables/realizarFirma/firmar.jsp`.

A bounded GET of the embedded utility returned a server-side form with a PDF/file input and a POST
boundary. The public response contained no `AutoScript` or `MiniApplet` object before that boundary.
The response also contained ephemeral server-session values; they were not copied into tracked
content and the raw iframe response was deleted immediately after extracting only the non-sensitive
form structure. No POST or upload was attempted.

Result: the exact La Rioja utility is a useful product/research lead, but it does not expose a complete
browser-local signing contract before a prohibited upload/POST boundary. The broad
`larioja-oficina-electronica` surface (`ES-PUB-0116`) therefore remains `BROWSE_ONLY`; no new exact
profile/catalog row is justified from this evidence.
