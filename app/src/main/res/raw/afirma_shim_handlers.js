
  function secureRequestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    if (!globalThis.crypto || typeof globalThis.crypto.getRandomValues !== "function") {
      return null;
    }
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  let euskadiClientAuthPostSent = false;

  function isSafeEuskadiOpaqueRequest(value) {
    if (typeof value !== "string" || value.length < 1 || value.length > 4096) {
      return false;
    }
    for (let index = 0; index < value.length; index += 1) {
      const code = value.charCodeAt(index);
      if (code < 0x21 || code > 0x7e) return false;
    }
    return true;
  }

  function interceptEuskadiClientAuthPost(form) {
    if (!euskadiClientAuthPostEnabled || window.top !== window ||
        location.href !== euskadiAuthPage) {
      return false;
    }
    if (!(form instanceof HTMLFormElement)) return false;

    let action;
    try {
      action = new URL(form.action, location.href).href;
    } catch (_) {
      return true;
    }
    // Unrelated forms on the reviewed page remain untouched. Only the exact client-auth target is mediated.
    if (action !== euskadiClientAuthTarget) return false;
    if (form.method.toUpperCase() !== "POST" ||
        form.enctype.toLowerCase() !== euskadiFormContentType) {
      return true;
    }

    const namedControls = Array.from(form.elements).filter(control => control && control.name);
    if (namedControls.length !== 2 || namedControls.some(control => control.type !== "hidden")) {
      return true;
    }
    const names = namedControls.map(control => control.name).sort();
    if (names[0] !== "request" || names[1] !== "x_correlation_id") return true;

    const requestControl = form.elements.namedItem("request");
    const correlationControl = form.elements.namedItem("x_correlation_id");
    const requestValue = requestControl && requestControl.value;
    const correlationValue = correlationControl && correlationControl.value;
    if (!isSafeEuskadiOpaqueRequest(requestValue) ||
        typeof correlationValue !== "string" ||
        !canonicalUuidPattern.test(correlationValue) ||
        euskadiClientAuthPostSent || !bridge || typeof bridge.postMessage !== "function") {
      return true;
    }

    const requestId = secureRequestId();
    if (!requestId || !canonicalUuidPattern.test(requestId)) return true;
    euskadiClientAuthPostSent = true;
    try {
      bridge.postMessage(JSON.stringify({
        type: "EUSKADI_CLIENT_AUTH_POST",
        profileId: euskadiProfileId,
        requestId,
        method: "POST",
        contentType: euskadiFormContentType,
        targetUrl: euskadiClientAuthTarget,
        request: requestValue,
        x_correlation_id: correlationValue
      }));
    } catch (_) {
      euskadiClientAuthPostSent = false;
    }
    // The normal WebView never sends this POST; native replays the exact form body only in the isolated TLS WebView.
    return true;
  }

  if (euskadiClientAuthPostEnabled && typeof HTMLFormElement === "function") {
    const nativeSubmit = HTMLFormElement.prototype.submit;
    const nativeRequestSubmit = HTMLFormElement.prototype.requestSubmit;
    try {
      HTMLFormElement.prototype.submit = function() {
        if (interceptEuskadiClientAuthPost(this)) return undefined;
        return Reflect.apply(nativeSubmit, this, []);
      };
      if (typeof nativeRequestSubmit === "function") {
        HTMLFormElement.prototype.requestSubmit = function(submitter) {
          if (interceptEuskadiClientAuthPost(this)) return undefined;
          const args = submitter === undefined ? [] : [submitter];
          return Reflect.apply(nativeRequestSubmit, this, args);
        };
      }
      document.addEventListener("submit", event => {
        if (interceptEuskadiClientAuthPost(event.target)) {
          event.preventDefault();
          event.stopImmediatePropagation();
        }
      }, true);
    } catch (_) {
      // If the exact hook cannot be installed, do not add any broader fallback interception.
    }
  }

  const closedErrorMessages = Object.freeze({
    USER_CANCELLED: "La operación de firma se ha cancelado.",
    REQUEST_EXPIRED: "La solicitud de firma ha caducado.",
    CERTIFICATE_LOCKED: "El certificado está bloqueado.",
    NAVIGATION_CHANGED: "La página cambió durante la firma.",
    SESSION_EXPIRED: "La sesión del portal ha caducado.",
    PROTOCOL_FAILED: "El portal no pudo completar la firma."
  });

  function safeErrorMessage(errorCode) {
    return closedErrorMessages[errorCode] || "No se pudo completar la firma.";
  }

  function postQaPortalDiagnostic(stage, requestIdValue) {
    if (!qaDiagnosticsEnabled || !bridge || typeof bridge.postMessage !== "function" ||
        !canonicalUuidPattern.test(probeDocumentId) ||
        !canonicalUuidPattern.test(requestIdValue)) {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: "QA_PORTAL_DIAGNOSTIC",
        documentId: probeDocumentId,
        requestId: requestIdValue,
        stage
      }));
    } catch (_) {
      // QA diagnostics are best-effort and never alter portal behavior.
    }
  }

  function clearPending(requestIdValue) {
    const pending = pendingCallbacks.get(requestIdValue);
    if (!pending) {
      return null;
    }
    pendingCallbacks.delete(requestIdValue);
    clearTimeout(pending.timeoutId);
    return pending;
  }

  function clearPendingBatch(requestIdValue) {
    const pending = pendingBatchCallbacks.get(requestIdValue);
    if (!pending) {
      return null;
    }
    pendingBatchCallbacks.delete(requestIdValue);
    clearTimeout(pending.timeoutId);
    return pending;
  }

  function notifyNativeCancel(requestIdValue, messageType = "MINIAPPLET_CANCEL") {
    if (!bridge || typeof bridge.postMessage !== "function") {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: messageType,
        documentId: probeDocumentId,
        requestId: requestIdValue
      }));
    } catch (_) {
      // Native cancellation is best-effort; page teardown still drops callbacks.
    }
  }

  function rejectDirectCall(errorCallback, errorCode) {
    if (typeof errorCallback === "function") {
      try {
        errorCallback(errorCode, safeErrorMessage(errorCode));
      } catch (_) {
        // Portal callback exceptions must not reopen native signing.
      }
    }
  }

  function isIdenticalInFlightCall(pending, args) {
    return pending !== undefined &&
      pending.dataB64 === args[0] &&
      pending.algorithm === args[1] &&
      pending.format === args[2] &&
      pending.extraProperties === args[3] &&
      pending.successCallback === args[4] &&
      pending.errorCallback === args[5];
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
