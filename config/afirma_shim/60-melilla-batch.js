
  {
    let activeMelillaBatch = null;
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

    rememberMelillaBatchCreate = (args) => {
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
    };

    rememberMelillaBatchDocument = (args) => {
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
    };

    interceptMelillaBatchSign = (args) => {
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
    };

    resetMelillaBatch = () => { activeMelillaBatch = null; };

    isMelillaBatchDocument = () =>
      melillaBatchCompatibilityEnabled &&
      window.location.origin === staBatchOrigin;

    installMelillaBatchMethodHooks = (value, includeMelillaBatch) => {
      if (includeMelillaBatch) {
        installMethodHook(value, "createBatch", "BATCH_CREATE");
        installMethodHook(value, "addDocumentToBatch", "BATCH_ADD_DOCUMENT");
        installMethodHook(value, "signBatchProcess", "BATCH_SIGN");
      }
    };
  }
