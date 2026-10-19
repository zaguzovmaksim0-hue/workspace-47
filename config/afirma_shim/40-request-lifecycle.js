
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
