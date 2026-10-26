
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

  installSevillaAtseClickHook();

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
    resetMelillaBatch();
  });
