
  {
    const lugoOrigin = "https://sede.deputacionlugo.org";
    const lugoClientBase = "https://sede.deputacionlugo.org/opencms";
    const lugoExtraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\n";

    interceptLugoSetupCall = (call, args) => {
      if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {
        return false;
      }
      return call === "LUGO_CARGAR_APP_AFIRMA" &&
        args.length === 1 && args[0] === lugoClientBase;
    };

    interceptLugoBatchSign = (args) => {
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
    };

    matchesLugoBatchDocument = () => {
      const isLugoBatchDocument = lugoBatchCompatibilityEnabled &&
        window.location.origin === lugoOrigin;
      return isLugoBatchDocument;
    };

    installLugoBatchMethodHooks = (value, includeLugoBatch) => {
      if (includeLugoBatch) {
        installMethodHook(value, "cargarAppAfirma", "LUGO_CARGAR_APP_AFIRMA");
        installMethodHook(value, "signBatch", "LUGO_BATCH_SIGN");
      }
    };
  }
