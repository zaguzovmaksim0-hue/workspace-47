
  installSevillaAtseClickHook();

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
