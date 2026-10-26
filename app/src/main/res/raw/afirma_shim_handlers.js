
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
    const isExactJccmCall = matchesJccmSign(args);
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

  function isMelillaBatchPage() {
    return functionalSigningEnabled && melillaBatchCompatibilityEnabled &&
      window.location.origin === staBatchOrigin;
  }

  function isSafeBatchIdentifier(value) {
    return typeof value === "string" && value.length > 0 &&
      value.length <= maxBatchIdentifierChars &&
      !Array.from(value).some(character => /\s/.test(character) ||
        character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f);
  }

  function batchExtraParams(format) {
    if (format === "PAdES") {
      return "signatureSubFilter=ETSI.CAdES.detached";
    }
    if (format === "XAdES") {
      return "mode=implicit";
    }
    return null;
  }

  function isSupportedBatchFormat(value) {
    return value === "CAdES" || value === "PAdES" || value === "XAdES";
  }

  function rememberMelillaBatchCreate(args) {
    if (!isMelillaBatchPage()) {
      activeMelillaBatch = null;
      return;
    }
    if (args.length !== 4 || args[0] !== "SHA256withRSA" ||
        !isSupportedBatchFormat(args[1]) || args[2] !== "sign" || args[3] !== null) {
      activeMelillaBatch = null;
      return;
    }
    activeMelillaBatch = {
      algorithm: args[0],
      format: args[1],
      suboperation: args[2],
      documents: [],
      invalid: false
    };
  }

  function rememberMelillaBatchDocument(args) {
    const batch = activeMelillaBatch;
    if (!isMelillaBatchPage() || !batch) {
      return;
    }
    const documentFormat = args[2];
    const documentSuboperation = args[3];
    const effectiveFormat = documentFormat === null ? batch.format : documentFormat;
    const valid = args.length === 5 && isSafeBatchIdentifier(args[0]) &&
      typeof args[1] === "string" && args[1].length > 0 &&
      args[1].length <= maxBatchUrlChars && !args[1].includes("\u0000") &&
      (documentFormat === null || isSupportedBatchFormat(documentFormat)) &&
      (documentSuboperation === null || documentSuboperation === "sign") &&
      args[4] === batchExtraParams(effectiveFormat) &&
      !batch.documents.some(document => document.id === args[0]) &&
      batch.documents.length < maxBatchDocuments;
    if (!valid) {
      batch.invalid = true;
      return;
    }
    const document = {
      id: args[0],
      datareference: args[1]
    };
    if (documentFormat !== null) {
      document.format = documentFormat;
    }
    if (documentSuboperation !== null) {
      document.suboperation = documentSuboperation;
    }
    batch.documents.push(document);
  }

  function interceptMelillaBatchSign(args) {
    if (!isMelillaBatchPage()) {
      return false;
    }
    const errorCallback = args[5];
    const batch = activeMelillaBatch;
    if (args.length !== 6 || args[0] !== false || typeof args[1] !== "string" ||
        args[1].length === 0 || args[1].length > maxBatchUrlChars ||
        typeof args[2] !== "string" || args[2].length === 0 ||
        args[2].length > maxBatchUrlChars || args[3] !== null ||
        typeof args[4] !== "function" || typeof errorCallback !== "function" ||
        !batch || batch.invalid || batch.documents.length === 0 || !probeDocumentId) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    if (pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "MINIAPPLET_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4],
      errorCallback,
      timeoutId
    });
    const message = {
      type: "MINIAPPLET_BATCH",
      documentId: probeDocumentId,
      requestId: batchRequestId,
      batchPreSignerUrl: args[1],
      batchPostSignerUrl: args[2],
      algorithm: batch.algorithm,
      format: batch.format,
      suboperation: batch.suboperation,
      stopOnError: false,
      documentos: batch.documents.map(document => ({ ...document }))
    };
    activeMelillaBatch = null;
    try {
      bridge.postMessage(JSON.stringify(message));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function interceptLugoSetupCall(call, args) {
    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {
      return false;
    }
    return call === "LUGO_CARGAR_APP_AFIRMA" &&
      args.length === 1 && args[0] === lugoClientBase;
  }

  function interceptLugoBatchSign(args) {
    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {
      return false;
    }
    const errorCallback = args[5];
    if (args.length !== 6 || typeof args[0] !== "string" || args[0].length === 0 ||
        args[0].length > maxBatchResultChars || typeof args[1] !== "string" ||
        args[1].length === 0 || args[1].length > maxBatchUrlChars ||
        typeof args[2] !== "string" || args[2].length === 0 || args[2].length > maxBatchUrlChars ||
        args[3] !== lugoExtraProperties || typeof args[4] !== "function" ||
        typeof errorCallback !== "function") {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function" || !probeDocumentId ||
        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) { rejectDirectCall(errorCallback, "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "LUGO_XML_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4], errorCallback, timeoutId, lugoXml: true
    });
    try {
      bridge.postMessage(JSON.stringify({
        type: "LUGO_XML_BATCH",
        documentId: probeDocumentId,
        requestId: batchRequestId,
        batchXml: args[0],
        batchPreSignerUrl: args[1],
        batchPostSignerUrl: args[2],
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function caibRequestToken() {
    if (!caibBatchCompatibilityEnabled || window.location.origin !== caibOrigin) return null;
    const match = window.location.pathname.match(caibSignerPathPattern);
    return match ? match[1] : null;
  }

  function decodeCaibUrlBase64(value) {
    if (typeof value !== "string" || !caibBatchBase64Pattern.test(value)) return null;
    try {
      const normalized = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
      return atob(normalized);
    } catch (_) { return null; }
  }

  function caibSignatureId(batchXml, token) {
    if (typeof batchXml !== "string" || batchXml.length === 0 || batchXml.length > 8192) return null;
    const xml = decodeCaibUrlBase64(batchXml);
    if (xml === null || xml.length > 6144) return null;
    const ids = Array.from(xml.matchAll(/<singlesign\s+Id="([A-Za-z0-9_-]{40})">/g));
    if (ids.length !== 1 || !caibSignatureIdPattern.test(ids[0][1])) return null;
    return decodeCaibUrlBase64(ids[0][1]) === `${token}|0` ? ids[0][1] : null;
  }

  function caibExtraProperties(signatureId) {
    return "mode=implicit\n" +
      "signatureSubFilter=ETSI.CAdES.detached\n" +
      `SignatureId=${signatureId}\n` +
      "signReason=FORM-1.pdf\n" +
      "formatmobile=PAdEStri\n" +
      "formatbatch=PAdES\n" +
      "format=PAdES\n" +
      "filters.1=nonexpired:\n" +
      "allowSigningCertifiedPdfs=true\n" +
      "algorithm=SHA256withRSA\n";
  }

  function caibEndpoint(token, name) {
    return `${caibOrigin}/portafibback/public/signmodule/requestPlugin/${token}/-1/${name}`;
  }

  function interceptCaibSetupCall(call, args) {
    const token = caibRequestToken();
    if (!token) return false;
    if (call === "CAIB_SET_FORCE_WS_MODE") return args.length === 1 && args[0] === true;
    if (call === "CAIB_CARGAR_APP_AFIRMA") return args.length === 1 && args[0] === caibClientBase;
    if (call === "CAIB_SET_SERVLETS") {
      return args.length === 2 && args[0] === caibEndpoint(token, "StorageService") &&
        args[1] === caibEndpoint(token, "RetrieveService");
    }
    return false;
  }

  function interceptCaibBatchSign(args) {
    const token = caibRequestToken();
    if (!token) return false;
    const errorCallback = args[5];
    const signatureId = caibSignatureId(args[0], token);
    if (args.length !== 6 || signatureId === null ||
        args[1] !== caibEndpoint(token, "BatchPresigner") ||
        args[2] !== caibEndpoint(token, "BatchPostsigner") ||
        args[3] !== caibExtraProperties(signatureId) || typeof args[4] !== "function" ||
        typeof errorCallback !== "function") {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function" || !probeDocumentId ||
        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) { rejectDirectCall(errorCallback, "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "CAIB_XML_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4], errorCallback, timeoutId, caibXml: true
    });
    try {
      bridge.postMessage(JSON.stringify({
        type: "CAIB_XML_BATCH",
        documentId: probeDocumentId,
        requestId: batchRequestId,
        batchXml: args[0],
        batchPreSignerUrl: args[1],
        batchPostSignerUrl: args[2],
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function isExactSevillaAtsePage() {
    return sevillaAtseCompatibilityEnabled && location.origin === sevillaAtseOrigin &&
      location.pathname === sevillaAtsePage && location.search === "?modo=Contribuyente" &&
      location.hash === "";
  }

  if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled) {
    document.addEventListener("click", event => {
      if (!isExactSevillaAtsePage()) return;
      const target = event.target?.closest?.("a");
      if (target?.getAttribute("href") === "#" &&
          target.getAttribute("onclick") === "doSign();" &&
          document.querySelectorAll('a[href="#"][onclick="doSign();"]').length === 1) {
        event.preventDefault();
      }
    }, true);
  }

  function interceptSevillaAtseSetupCall(call, args) {
    if (!isExactSevillaAtsePage()) return false;
    if (call === "SEVILLA_SET_FORCE_WS_MODE") {
      sevillaAtseSetupState = args.length === 1 && args[0] === true ? 1 : -1;
      return true;
    }
    if (call === "SEVILLA_SET_SERVLETS") {
      sevillaAtseSetupState = sevillaAtseSetupState === 1 && args.length === 2 &&
        args[0] === sevillaAtseStorageUrl && args[1] === sevillaAtseRetrieveUrl ? 2 : -1;
      return true;
    }
    if (call === "SEVILLA_CARGAR_APP_AFIRMA") {
      sevillaAtseSetupState = sevillaAtseSetupState === 2 && args.length === 0 ? 3 : -1;
      return true;
    }
    return false;
  }

  function interceptUgrSetupCall(call, args) {
    if (!ugrCompatibilityEnabled || window.location.origin !== ugrOrigin) {
      return false;
    }
    if (call === "UGR_SET_FORCE_WS_MODE") {
      return args.length === 1 && args[0] === true;
    }
    if (call === "UGR_CARGAR_APP_AFIRMA") {
      return args.length === 0;
    }
    return call === "UGR_SET_SERVLETS" && args.length === 2 &&
      args[0] === ugrStorageUrl && args[1] === ugrRetrieveUrl;
  }

  function receiveMelillaBatchResult(result) {
    postQaPortalDiagnostic("RESULT_RECEIVED", result.requestId);
    const pending = clearPendingBatch(result.requestId);
    if (!pending) {
      postQaPortalDiagnostic("RESULT_IGNORED", result.requestId);
      return;
    }
    if (result.status === "success" && typeof result.validationResponse === "string" &&
        result.validationResponse.length <= maxBatchResultChars) {
      if (pending.lugoXml === true || pending.caibXml === true) {
        if (!base64Pattern.test(result.validationResponse)) {
          rejectDirectCall(pending.errorCallback, "PROTOCOL_FAILED");
          return;
        }
        try { pending.successCallback(result.validationResponse); } catch (_) {}
        return;
      }
      let validationResponse;
      try {
        validationResponse = JSON.parse(result.validationResponse);
      } catch (_) {
        rejectDirectCall(pending.errorCallback, "PROTOCOL_FAILED");
        return;
      }
      postQaPortalDiagnostic("CALLBACK_STARTED", result.requestId);
      try {
        pending.successCallback(validationResponse);
        postQaPortalDiagnostic("CALLBACK_RETURNED", result.requestId);
      } catch (_) {
        postQaPortalDiagnostic("CALLBACK_THROWN", result.requestId);
        // The portal callback is terminal; no submission is attempted here.
      }
      return;
    }
    const errorCode = typeof result.errorCode === "string" &&
      safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED";
    rejectDirectCall(pending.errorCallback, errorCode);
  }

  function receiveMiniAppletResult(event) {
    if (!functionalSigningEnabled || !event || typeof event.data !== "string") {
      return;
    }
    let result;
    try {
      result = JSON.parse(event.data);
    } catch (_) {
      return;
    }
    if (!result || typeof result.requestId !== "string" ||
        !canonicalUuidPattern.test(result.requestId)) {
      return;
    }
    if (result.type === "MINIAPPLET_BATCH_RESULT") {
      receiveMelillaBatchResult(result);
      return;
    }
    if (result.type === "MINIAPPLET_SELECT_CERTIFICATE_RESULT") {
      const p = clearPending(result.requestId);
      if (!p || p.selection !== true) return;
      if (result.status === "success" && typeof result.certificate === "string" && base64Pattern.test(result.certificate)) {
        const certificateB64 = result.certificate;
        try { p.successCallback(certificateB64); } catch (_) {} return;
      }
      rejectDirectCall(p.errorCallback, typeof result.errorCode === "string" && safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED"); return;
    }
    if (result.type !== "MINIAPPLET_RESULT") {
      return;
    }
    postQaPortalDiagnostic("RESULT_RECEIVED", result.requestId);
    const pending = clearPending(result.requestId);
    if (!pending) {
      postQaPortalDiagnostic("RESULT_IGNORED", result.requestId);
      return;
    }
    if (result.status === "success" && typeof result.signature === "string" &&
        typeof result.certificate === "string" &&
        base64Pattern.test(result.signature) && base64Pattern.test(result.certificate)) {
      const signatureB64 = result.signature;
      const certificateB64 = result.certificate;
      postQaPortalDiagnostic("CALLBACK_STARTED", result.requestId);
      try {
        pending.successCallback(signatureB64, certificateB64);
        postQaPortalDiagnostic("CALLBACK_RETURNED", result.requestId);
      } catch (_) {
        postQaPortalDiagnostic("CALLBACK_THROWN", result.requestId);
        // Portal callback exceptions are terminal.
      }
      return;
    }
    const errorCode = typeof result.errorCode === "string" &&
      safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED";
    const errorCallback = pending.errorCallback;
    rejectDirectCall(errorCallback, errorCode);
  }

  if (functionalSigningEnabled && bridge) {
    bridge.onmessage = receiveMiniAppletResult;
  }

  window.addEventListener("pagehide", () => {
    for (const [pendingRequestId, pending] of pendingCallbacks.entries()) {
      clearTimeout(pending.timeoutId);
      notifyNativeCancel(pendingRequestId, pending.selection === true ?
        "MINIAPPLET_SELECT_CERTIFICATE_CANCEL" : "MINIAPPLET_CANCEL");
    }
    for (const [pendingRequestId, pending] of pendingBatchCallbacks.entries()) {
      clearTimeout(pending.timeoutId);
      notifyNativeCancel(
        pendingRequestId,
        pending.lugoXml === true ? "LUGO_XML_BATCH_CANCEL" :
          (pending.caibXml === true ? "CAIB_XML_BATCH_CANCEL" : "MINIAPPLET_BATCH_CANCEL")
      );
    }
    pendingCallbacks.clear();
    pendingBatchCallbacks.clear();
    activeMelillaBatch = null;
  });
