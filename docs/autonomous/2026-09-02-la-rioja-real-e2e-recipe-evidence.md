# La Rioja Oficina electrónica CLIENT_TLS_AUTH REAL E2E evidence — 2026-09-02

Scope: fresh public unauthenticated navigation evidence only. No client certificate was sent and no administrative filing was submitted.

Profile/card: `la-rioja-oficina-electronica`.

The reviewed start URL `https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697` currently redirects once to the existing profile source `/casLR/login` and returns HTTP 200.

The current source URL contains exactly:

- fixed `inst=G`;
- fixed `apli=OFIVIR`;
- fixed `nodo=CIUDANO`;
- bounded ephemeral `param`;
- `TARGET` pointing back to `https://ias1.larioja.org/oficinavirtual/presentacion` with exactly `act_codi=24697` and a 40-hex `uuidep` value.

The page exposes the exact certificate authentication control:

- `button#boton_certificado`;
- type `button`;
- label `Conectar`;
- class `btn btn-success`;
- onclick `loginClientCertSSL('https://ias1.larioja.org/clientcertSSL/login')`.

The E2E recipe validates the complete bounded source/return contract before clicking that control. The existing profile authorizer independently constrains the resulting certificate request to same-origin `/clientcertSSL/login` on port 443.

No subsequent administrative submission control is touched.
