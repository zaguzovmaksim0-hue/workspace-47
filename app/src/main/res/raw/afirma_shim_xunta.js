
  {
    const xuntaOrigin = "https://sede.xunta.gal";
    const xuntaPage = "https://sede.xunta.gal/presenta/novo/PR004A_2025_1";
    const xuntaSelectProps = "filters=nonexpired";
    const xuntaFixedProperties = Object.freeze({
      format: "PAdES",
      signatureSubFilter: "ETSI.CAdES.detached",
      serverUrl: "https://sede.xunta.gal/presenta/sinatura/SignatureService",
      referencesDigestMethod: "http://www.w3.org/2000/09/xmldsig#sha1",
      mimeType: "hash/sha256",
      headless: "true"
    });
    const xuntaAllowedDynamicProperties = new Set([
      "filters", "locale", "nif", "id", "codigoSeguridad",
      "marcaFirmaCustom", "dataUser", "idBorrador"
    ]);

    function isExactXuntaProperties(value) {
      if (typeof value !== "string" || value.length === 0 || value.length > maxExtraPropertiesChars) {
        return false;
      }
      const entries = new Map();
      for (const rawLine of value.split("\n")) {
        const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;
        const separator = line.indexOf("=");
        if (separator <= 0) return false;
        const key = line.slice(0, separator);
        const propertyValue = line.slice(separator + 1);
        if (!/^[A-Za-z][A-Za-z0-9._-]{0,63}$/.test(key) || propertyValue.length > 8192 ||
            entries.has(key) || Array.from(propertyValue).some(ch => ch.charCodeAt(0) < 0x20 || ch.charCodeAt(0) === 0x7f)) {
          return false;
        }
        entries.set(key, propertyValue);
      }
      for (const [key, expected] of Object.entries(xuntaFixedProperties)) {
        if (entries.get(key) !== expected) return false;
      }
      if (!entries.has("filters") || !entries.has("locale") || entries.get("locale") === "") return false;
      const filters = entries.get("filters");
      if (filters !== "nonexpired" &&
          !(filters.startsWith("nonexpired;encodedcert:") &&
            base64Pattern.test(filters.slice("nonexpired;encodedcert:".length)))) {
        return false;
      }
      for (const key of entries.keys()) {
        if (!Object.prototype.hasOwnProperty.call(xuntaFixedProperties, key) &&
            !xuntaAllowedDynamicProperties.has(key)) return false;
      }
      return true;
    }

    matchesXuntaSelection = a =>
      xSel && location.href === xuntaPage && a.length === 3 && a[0] === xuntaSelectProps &&
        typeof a[1] === "function" && typeof a[2] === "function";

    matchesXuntaSign = (args, successCallback, errorCallback) => {
      const isXuntaOrigin = xSel && window.location.origin === xuntaOrigin;
      const isXuntaPage = isXuntaOrigin && window.location.href === xuntaPage &&
        window.location.search === "" && window.location.hash === "";
      const isExactXuntaCall =
        isXuntaPage && args.length === 6 && args[0] === "doc" &&
        args[1] === "SHA1withRSA" && args[2] === "PAdEStri" &&
        isExactXuntaProperties(args[3]) &&
        typeof successCallback === "function" && typeof errorCallback === "function";
      return isXuntaOrigin ? isExactXuntaCall : null;
    };
  }
