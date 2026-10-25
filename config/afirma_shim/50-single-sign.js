
  function isValidSevillaAtseChallenge(value) {
    if (typeof value !== "string") {
      return false;
    }
    try {
      const decoded = atob(value);
      return decoded.length === 40 && sevillaAtseChallengePattern.test(decoded);
    } catch (_) {
      return false;
    }
  }

  function isValidAirefPayload(value) {
    if (typeof value !== "string" || value.length !== 44 || !base64Pattern.test(value)) {
      return false;
    }
    try {
      const decoded = atob(value);
      return decoded.length === 32 && btoa(decoded) === value;
    } catch (_) {
      return false;
    }
  }

  function isExactAirefSigningPage() {
    return window.location.origin === airefOrigin &&
      window.location.pathname === airefSigningPath &&
      airefSigningQueryPattern.test(window.location.search) &&
      window.location.hash === "";
  }

  function isExactCanariasChallenge(value) {
    if (typeof value !== "string" || !base64Pattern.test(value)) {
      return false;
    }
    try {
      const decoded = globalThis.atob(value);
      return canariasChallengePattern.test(decoded) && globalThis.btoa(decoded) === value;
    } catch (_) {
      return false;
    }
  }

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

  function interceptCertificateSelection(a) {
    if (!iSel && !vSel && !xSel) return false;
    let valid = false;
    if (iSel && location.href === iPage && a.length === 3 && a[0] === iProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    } else if (vSel && location.href === vPage && a.length === 3 && a[0] === vProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    } else if (xSel && location.href === xuntaPage && a.length === 3 && a[0] === xuntaSelectProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    }
    if (!valid) {
      if (a.length >= 3 && typeof a[2] === "function") {
        rejectDirectCall(a[2], "INVALID_REQUEST");
      }
      return true;
    }
    if (!functionalSigningEnabled || !bridge || typeof bridge.postMessage !== "function" ||
        !canonicalUuidPattern.test(probeDocumentId) || pendingCallbacks.size) {
      rejectDirectCall(a[2], "PROTOCOL_FAILED"); return true;
    }
    const id = secureRequestId();
    if (!id) { rejectDirectCall(a[2], "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => { if (clearPending(id)) {
      notifyNativeCancel(id, "MINIAPPLET_SELECT_CERTIFICATE_CANCEL");
      rejectDirectCall(a[2], "REQUEST_EXPIRED"); } }, signTimeoutMillis);
    pendingCallbacks.set(id, { selection: true, successCallback: a[1], errorCallback: a[2], timeoutId });
    try { bridge.postMessage(JSON.stringify({ type: "MINIAPPLET_SELECT_CERTIFICATE",
      documentId: probeDocumentId, requestId: id, extraProperties: a[0] })); }
    catch (_) { clearPending(id); rejectDirectCall(a[2], "PROTOCOL_FAILED"); }
    return true;
  }

  function equivalentLiteralProperties(raw, expected) {
    if (typeof raw !== "string" || raw.length > maxExtraPropertiesChars) return false;
    const parse = value => {
      const map = new Map();
      const lines = value.split(/\r\n|\n|\r/);
      if (lines.length > 64) return null;
      for (const line of lines) {
        if (line === "") continue;
        const at = line.indexOf("=");
        if (at < 1 || /[\x00-\x1f\x7f\\]/.test(line)) return null;
        const key = line.slice(0, at);
        if (!/^[A-Za-z0-9_.-]+$/.test(key) || map.has(key)) return null;
        map.set(key, line.slice(at + 1));
      }
      return map;
    };
    const actual = parse(raw), required = parse(expected);
    return actual !== null && required !== null && actual.size === required.size &&
      Array.from(required).every(([key, value]) => actual.get(key) === value);
  }

  function interceptMiniAppletSign(args) {
    if (!functionalSigningEnabled) {
      return false;
    }
    postShimDiagnostic("SIGN_INTERCEPT_ENTRY");
    const successCallback = args[4];
    const errorCallback = args[5];
    const isSevillaAtseOrigin =
      sevillaAtseCompatibilityEnabled && window.location.origin === sevillaAtseOrigin;
    const isExactSevillaAtseCall =
      isSevillaAtseOrigin && isExactSevillaAtsePage() && sevillaAtseSetupState === 3 &&
      args.length === 6 &&
      isValidSevillaAtseChallenge(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      args[3] == null &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isSevillaAtseOrigin && !isExactSevillaAtseCall) {
      sevillaAtseSetupState = 0;
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (isExactSevillaAtseCall) sevillaAtseSetupState = 0;
    const isAccedaOrigin =
      accedaCompatibilityEnabled && window.location.origin === accedaOrigin;
    const isExactAccedaCall =
      isAccedaOrigin &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "PAdES" &&
      typeof args[3] === "string" &&
      args[3].trim() === accedaExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isAccedaOrigin && !isExactAccedaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isAirefOrigin =
      airefCompatibilityEnabled && window.location.origin === airefOrigin;
    const isExactAirefCall =
      isAirefOrigin && isExactAirefSigningPage() &&
      args.length === 6 &&
      isValidAirefPayload(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      args[3] === null &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isAirefOrigin && !isExactAirefCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isCanariasOrigin =
      canariasCompatibilityEnabled && window.location.origin === canariasOrigin;
    const isCanariasPage =
      isCanariasOrigin && window.location.href === canariasPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCanariasCall =
      isCanariasPage && args.length === 6 && isExactCanariasChallenge(args[0]) &&
      args[1] === "SHA1withRSA" && args[2] === "CAdES" &&
      args[3] === canariasExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isCanariasOrigin && !isExactCanariasCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isGranCanariaOrigin =
      granCanariaCompatibilityEnabled && window.location.origin === granCanariaOrigin;
    const isExactGranCanariaCall =
      isGranCanariaOrigin &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "PAdES" &&
      args[3] === granCanariaExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isGranCanariaOrigin && !isExactGranCanariaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isFuerteventuraOrigin =
      fuerteventuraCompatibilityEnabled && window.location.origin === fuerteventuraOrigin;
    const isFuerteventuraSigningPage =
      isFuerteventuraOrigin && window.location.href === fuerteventuraSigningPage &&
      window.location.hash === "";
    const isExactFuerteventuraCall =
      isFuerteventuraSigningPage &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA256withRSA" &&
      args[2] === "PAdES" &&
      args[3] === fuerteventuraExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isFuerteventuraOrigin && !isExactFuerteventuraCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isMinecoOrigin =
      minecoCompatibilityEnabled && window.location.origin === minecoOrigin;
    const isMinecoSigningPage =
      isMinecoOrigin && window.location.href === minecoSigningPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactMinecoCall =
      isMinecoSigningPage && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" && args[2] === "PAdES" &&
      args[3] === minecoExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isMinecoOrigin && !isExactMinecoCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isXuntaOrigin = xSel && window.location.origin === xuntaOrigin;
    const isXuntaPage = isXuntaOrigin && window.location.href === xuntaPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactXuntaCall =
      isXuntaPage && args.length === 6 && args[0] === "doc" &&
      args[1] === "SHA1withRSA" && args[2] === "PAdEStri" &&
      isExactXuntaProperties(args[3]) &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isXuntaOrigin && !isExactXuntaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isCdtiOrigin =
      cdtiCompatibilityEnabled && window.location.origin === cdtiOrigin;
    const isCdtiPage =
      isCdtiOrigin && window.location.href === cdtiPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCdtiCall =
      isCdtiPage && args.length === 6 &&
      typeof args[0] === "string" && cdtiChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "XAdES Enveloping" &&
      args[3] === cdtiExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isCdtiOrigin && !isExactCdtiCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isPoliciaOrigin =
      policiaCompatibilityEnabled && window.location.origin === policiaOrigin;
    const isPoliciaProcedurePage =
      isPoliciaOrigin &&
      window.location.href === policiaProcedurePage &&
      window.location.search === "" &&
      window.location.hash === "";
    const isExactPoliciaCall =
      isPoliciaProcedurePage &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      (args[3] === policiaExtraProperties || args[3] === policiaExtraProperties.replace(/\n/g, "\r\n")) &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isPoliciaOrigin && !isExactPoliciaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isJccmRegistroOrigin =
      jccmRegistroCompatibilityEnabled && window.location.origin === jccmRegistroOrigin;
    const isJccmRegistroPage =
      isJccmRegistroOrigin &&
      window.location.href === jccmRegistroProtectedPage &&
      window.location.hash === "";
    const isExactJccmRegistroCall =
      isJccmRegistroPage && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" && args[2] === "XADES" &&
      args[3] === jccmRegistroExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isJccmRegistroOrigin && !isExactJccmRegistroCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isBadajozOrigin =
      badajozCompatibilityEnabled && window.location.origin === badajozOrigin;
    const isExactBadajozCall =
      isBadajozOrigin && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA256withRSA" && typeof args[2] === "string" && args[2].toLowerCase() === "cades" &&
      equivalentLiteralProperties(args[3], badajozExtraProperties) &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isBadajozOrigin && !isExactBadajozCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactUgrLiteralCall =
      ugrCompatibilityEnabled &&
      window.location.origin === ugrOrigin &&
      args[0] === ugrLiteral &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      args[3] === "";
    const isExactCantabriaCall = matchesCantabriaSign(args);
    const isJccmOrigin =
      jccmCompatibilityEnabled && window.location.origin === jccmOrigin;
    const isExactJccmCall =
      isJccmOrigin &&
      args[0] === jccmPayloadBase64 &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      (args[3] === null || args[3] === "");
    const dataB64 = isExactUgrLiteralCall ? ugrLiteralBase64 :
      isExactCantabriaCall && typeof globalThis.btoa === "function" ?
        globalThis.btoa(args[0]) :
      isExactXuntaCall && typeof globalThis.btoa === "function" ?
        globalThis.btoa(args[0]) :
      isExactCdtiCall ? args[0] + "=" : args[0];
    const hasValidUgrDataEncoding = base64Pattern.test(dataB64);
    const hasValidCantabriaDataEncoding = isExactCantabriaCall &&
      typeof globalThis.btoa === "function" &&
      base64Pattern.test(dataB64);
    const isJuntaCades =
      !jccmCompatibilityEnabled && !canariasCompatibilityEnabled &&
      (args[1] === "SHA1withRSA" || args[1] === "SHA256withRSA") &&
      args[2] === "CAdES" && typeof args[3] === "string" &&
      args[3].length <= maxExtraPropertiesChars;
    const isRegXades = !jccmCompatibilityEnabled &&
      args[1] === "SHA512withRSA" &&
      args[2] === "XAdES Detached" && args[3] === null;
    if (args.length !== 6 || typeof successCallback !== "function" ||
        typeof errorCallback !== "function" || typeof args[0] !== "string" ||
        args[0].length === 0 || args[0].length > maxDirectDataChars ||
        ((!isExactUgrLiteralCall && !isExactCantabriaCall && !isExactJccmCall &&
          !isExactJccmRegistroCall && !isExactSevillaAtseCall && !isExactAirefCall &&
          !isExactGranCanariaCall && !isExactFuerteventuraCall && !isExactCanariasCall &&
          !isExactMinecoCall && !isExactCdtiCall && !isExactXuntaCall &&
          !isExactAccedaCall && !isExactBadajozCall &&
          !base64Pattern.test(args[0])) ||
          (isExactUgrLiteralCall && !hasValidUgrDataEncoding) ||
          (isExactCantabriaCall && !hasValidCantabriaDataEncoding) ||
          (isExactXuntaCall && (typeof globalThis.btoa !== "function" || !base64Pattern.test(dataB64)))) ||
        (!isJuntaCades && !isRegXades && !isExactUgrLiteralCall &&
          !isExactCantabriaCall && !isExactJccmCall && !isExactJccmRegistroCall &&
          !isExactSevillaAtseCall &&
          !isExactAirefCall && !isExactCdtiCall && !isExactPoliciaCall &&
          !isExactGranCanariaCall && !isExactFuerteventuraCall && !isExactCanariasCall && !isExactMinecoCall &&
          !isExactXuntaCall && !isExactAccedaCall && !isExactBadajozCall)) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    postShimDiagnostic("SIGN_INTERCEPT_ACCEPTED");
    if (pendingCallbacks.size !== 0) {
      const pending = pendingCallbacks.values().next().value;
      if (isIdenticalInFlightCall(pending, args)) {
        // A repeated tap must not submit the portal's error form while the
        // original, byte-identical operation is still completing.
        return true;
      }
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const directRequestId = secureRequestId();
    if (!directRequestId || !probeDocumentId) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const timeoutId = setTimeout(() => {
      const expired = clearPending(directRequestId);
      if (expired) {
        notifyNativeCancel(directRequestId);
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingCallbacks.set(directRequestId, {
      dataB64: args[0],
      algorithm: args[1],
      format: args[2],
      extraProperties: args[3],
      successCallback,
      errorCallback,
      timeoutId
    });
    const nativeFormat = isExactBadajozCall ? "CAdES" : args[2];
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_SIGN",
        documentId: probeDocumentId,
        requestId: directRequestId,
        dataB64,
        algorithm: args[1],
        format: nativeFormat,
        extraProperties: isExactBadajozCall ? badajozExtraProperties : args[3]
      }));
      postShimDiagnostic("SIGN_MESSAGE_POSTED");
    } catch (_) {
      clearPending(directRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }
