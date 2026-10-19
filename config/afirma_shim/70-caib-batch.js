
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
