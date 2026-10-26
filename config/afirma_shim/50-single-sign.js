
  function interceptCertificateSelection(a) {
    if (!iSel && !vSel && !xSel) return false;
    let valid = false;
    if (matchesIsciiiSelection(a)) {
      valid = true;
    } else if (matchesValenciaSelection(a)) {
      valid = true;
    } else if (matchesXuntaSelection(a)) {
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
    const isExactSevillaAtseCall = matchesSevillaAtseSign(args, successCallback, errorCallback);
    if (isExactSevillaAtseCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactAccedaCall = matchesAccedaSign(args, successCallback, errorCallback);
    if (isExactAccedaCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);
    if (isExactAirefCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactCanariasCall = matchesCanariasSign(args, successCallback, errorCallback);
    if (isExactCanariasCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);
    if (isExactGranCanariaCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);
    if (isExactFuerteventuraCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactMinecoCall = matchesMinecoSign(args, successCallback, errorCallback);
    if (isExactMinecoCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactXuntaCall = matchesXuntaSign(args, successCallback, errorCallback);
    if (isExactXuntaCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);
    if (isExactCdtiCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);
    if (isExactPoliciaCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);
    if (isExactJccmRegistroCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactBadajozCall = matchesBadajozSign(args, successCallback, errorCallback);
    if (isExactBadajozCall === false) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactUgrLiteralCall = matchesUgrSign(args);
    const isExactCantabriaCall = matchesCantabriaSign(args);
    const isExactJccmCall = matchesJccmSign(args);
    const dataB64 = isExactUgrLiteralCall ? encodeUgrLiteral() :
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
        extraProperties: isExactBadajozCall ? getBadajozExtraProperties() : args[3]
      }));
      postShimDiagnostic("SIGN_MESSAGE_POSTED");
    } catch (_) {
      clearPending(directRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }
