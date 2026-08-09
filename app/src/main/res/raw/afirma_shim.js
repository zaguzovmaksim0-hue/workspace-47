(() => {
  "use strict";

  if (window.__jfmAfirmaShimInstalled === true) {
    return;
  }
  Object.defineProperty(window, "__jfmAfirmaShimInstalled", {
    value: true,
    writable: false,
    configurable: false
  });

  const bridge = window.JuntaFirmaMobile;
  const probe = window.JuntaFirmaProbe;
  const functionalSigningEnabled = __JFM_FUNCTIONAL_SIGNING_ENABLED__;
  const qaDiagnosticsEnabled = __JFM_QA_DIAGNOSTICS_ENABLED__;
  const ugrCompatibilityEnabled = __JFM_UGR_COMPATIBILITY_ENABLED__;
  const cantabriaCompatibilityEnabled = __JFM_CANTABRIA_COMPATIBILITY_ENABLED__;
  const jccmCompatibilityEnabled = __JFM_JCCM_COMPATIBILITY_ENABLED__;
  const ugrOrigin = "https://sede.ugr.es";
  const cantabriaOrigin = "https://rec.cantabria.es";
  const cantabriaChallengePattern = /^[0-9a-f]{40}$/;
  const cantabriaExtraProperties = "filters=\nmode=implicit";
  const ugrLiteral = "Universidad de Granada";
  const ugrLiteralBase64 = "VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==";
  const ugrStorageUrl = "https://sede.ugr.es/afirma-signature-storage/StorageService";
  const ugrRetrieveUrl = "https://sede.ugr.es/afirma-signature-retriever/RetrieveService";
  const jccmOrigin = "https://ventanillaelectronica.jccm.es";
  const jccmPayloadBase64 = "QUJDREU=";
  const maxUriChars = 1048576;
  const maxArgumentLength = 1048576;
  const maxArguments = 32;
  const maxDirectDataChars = 699052;
  const maxExtraPropertiesChars = 65536;
  const signTimeoutMillis = 120000;
  const safeTokenPattern = /^[A-Za-z0-9._+\-]{1,64}$/;
  const canonicalUuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const base64Pattern = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
  const wrappedMethods = new WeakSet();
  const pendingCallbacks = new Map();
  let activeProbeRequestId = null;

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

  function notifyNativeCancel(requestIdValue) {
    if (!bridge || typeof bridge.postMessage !== "function") {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_CANCEL",
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

  function interceptMiniAppletSign(args) {
    if (!functionalSigningEnabled) {
      return false;
    }
    const successCallback = args[4];
    const errorCallback = args[5];
    const isExactUgrLiteralCall =
      ugrCompatibilityEnabled &&
      window.location.origin === ugrOrigin &&
      args[0] === ugrLiteral &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      args[3] === "";
    const isExactCantabriaCall =
      cantabriaCompatibilityEnabled &&
      window.location.origin === cantabriaOrigin &&
      typeof args[0] === "string" &&
      cantabriaChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "CAdES" &&
      args[3] === cantabriaExtraProperties;
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
        globalThis.btoa(args[0]) : args[0];
    const hasValidUgrDataEncoding = base64Pattern.test(dataB64);
    const hasValidCantabriaDataEncoding = isExactCantabriaCall &&
      typeof globalThis.btoa === "function" &&
      base64Pattern.test(dataB64);
    const isJuntaCades =
      !jccmCompatibilityEnabled &&
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
          !base64Pattern.test(args[0])) ||
          (isExactUgrLiteralCall && !hasValidUgrDataEncoding) ||
          (isExactCantabriaCall && !hasValidCantabriaDataEncoding)) ||
        (!isJuntaCades && !isRegXades && !isExactUgrLiteralCall &&
          !isExactCantabriaCall && !isExactJccmCall)) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
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
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_SIGN",
        documentId: probeDocumentId,
        requestId: directRequestId,
        dataB64,
        algorithm: args[1],
        format: args[2],
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPending(directRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
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
    if (!result || result.type !== "MINIAPPLET_RESULT" ||
        typeof result.requestId !== "string" ||
        !canonicalUuidPattern.test(result.requestId)) {
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
      notifyNativeCancel(pendingRequestId);
    }
    pendingCallbacks.clear();
  });

  const probeDocumentId = secureRequestId();
  Object.defineProperty(window, "__jfmProbeDocumentId", {
    value: probeDocumentId,
    writable: false,
    configurable: false
  });

  function safeToken(value) {
    return typeof value === "string" && safeTokenPattern.test(value) ? value : null;
  }

  function argumentLength(value) {
    if (typeof value === "string") {
      return Math.min(value.length, maxArgumentLength);
    }
    if (value instanceof ArrayBuffer) {
      return Math.min(value.byteLength, maxArgumentLength);
    }
    if (ArrayBuffer.isView(value)) {
      return Math.min(value.byteLength, maxArgumentLength);
    }
    return 0;
  }

  function postProbeMessage(message) {
    if (!probe || typeof probe.postMessage !== "function") {
      return;
    }
    try {
      probe.postMessage(JSON.stringify(message));
    } catch (_) {
      // Diagnostics must never replace the portal method's return value or exception.
    }
  }

  function triggerPublicJuntaAccessForProbe() {
    if (!probe || typeof probe.postMessage !== "function" || window.top !== window) {
      return;
    }
    if (window.location.protocol !== "https:" ||
        window.location.hostname !== "www.juntadeandalucia.es" ||
        window.location.pathname !==
          "/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs") {
      return;
    }
    const accessButton = document.getElementById("btnacceso");
    if (!(accessButton instanceof HTMLInputElement) ||
        accessButton.type !== "button" || accessButton.value.trim() !== "Acceder") {
      return;
    }
    accessButton.click();
  }

  function observeMiniAppletCall(call, args) {
    const limitedArgs = Array.from(args).slice(0, maxArguments);
    const observedRequestId = secureRequestId();
    if (!observedRequestId || !probeDocumentId) {
      return null;
    }
    postProbeMessage({
      type: "MINIAPPLET_OBSERVATION",
      documentId: probeDocumentId,
      requestId: observedRequestId,
      call,
      algorithm: call === "SIGN" ? safeToken(limitedArgs[1]) : null,
      format: call === "SIGN" ? safeToken(limitedArgs[2]) : null,
      argumentLengths: limitedArgs.map(argumentLength)
    });
    return observedRequestId;
  }

  function tryObserveMiniAppletCall(call, args) {
    try {
      return observeMiniAppletCall(call, args);
    } catch (_) {
      return null;
    }
  }

  function observeBranch(branch) {
    if (activeProbeRequestId === null) {
      return;
    }
    postProbeMessage({
      type: "RUNTIME_BRANCH_OBSERVATION",
      documentId: probeDocumentId,
      requestId: activeProbeRequestId,
      branch
    });
  }

  function completeMiniAppletCall(observedRequestId) {
    postProbeMessage({
      type: "MINIAPPLET_CALL_END",
      documentId: probeDocumentId,
      requestId: observedRequestId
    });
  }

  function wrapMiniAppletMethod(method, call) {
    if (typeof method !== "function" || wrappedMethods.has(method)) {
      return method;
    }
    function wrappedMiniAppletMethod(...args) {
      const observedRequestId = tryObserveMiniAppletCall(call, args);
      if (observedRequestId === null) {
        if (interceptUgrSetupCall(call, args)) {
          return undefined;
        }
        return Reflect.apply(method, this, args);
      }
      const previousRequestId = activeProbeRequestId;
      activeProbeRequestId = observedRequestId;
      try {
        if ((call === "SIGN" && interceptMiniAppletSign(args)) ||
            interceptUgrSetupCall(call, args)) {
          return undefined;
        }
        return Reflect.apply(method, this, args);
      } finally {
        activeProbeRequestId = previousRequestId;
        completeMiniAppletCall(observedRequestId);
      }
    }
    wrappedMethods.add(wrappedMiniAppletMethod);
    return wrappedMiniAppletMethod;
  }

  function installMethodHook(target, name, call) {
    const descriptor = Object.getOwnPropertyDescriptor(target, name);
    if (descriptor && descriptor.configurable === false) {
      if (descriptor.writable === true && typeof target[name] === "function") {
        target[name] = wrapMiniAppletMethod(target[name], call);
      }
      return;
    }
    let current = wrapMiniAppletMethod(target[name], call);
    Object.defineProperty(target, name, {
      enumerable: descriptor ? descriptor.enumerable : true,
      configurable: true,
      get() {
        return current;
      },
      set(value) {
        current = wrapMiniAppletMethod(value, call);
      }
    });
  }

  function wrapMiniApplet(value, includeUgrSetup = false) {
    if ((typeof value !== "object" || value === null) && typeof value !== "function") {
      return value;
    }
    try {
      installMethodHook(value, "cargarMiniApplet", "LOAD");
      installMethodHook(value, "sign", "SIGN");
      if (includeUgrSetup) {
        installMethodHook(value, "setForceWSMode", "UGR_SET_FORCE_WS_MODE");
        installMethodHook(value, "cargarAppAfirma", "UGR_CARGAR_APP_AFIRMA");
        installMethodHook(value, "setServlets", "UGR_SET_SERVLETS");
      }
    } catch (_) {
      // A hostile/non-configurable object remains untouched and signing stays fail-closed.
    }
    return value;
  }

  const miniAppletDescriptor = Object.getOwnPropertyDescriptor(window, "MiniApplet");
  if (!miniAppletDescriptor || miniAppletDescriptor.configurable === true) {
    let miniApplet = wrapMiniApplet(window.MiniApplet);
    Object.defineProperty(window, "MiniApplet", {
      enumerable: true,
      configurable: true,
      get() {
        return miniApplet;
      },
      set(value) {
        miniApplet = wrapMiniApplet(value);
      }
    });
    window.addEventListener("DOMContentLoaded", () => {
      miniApplet = wrapMiniApplet(miniApplet);
    }, { once: true });
  } else {
    wrapMiniApplet(window.MiniApplet);
  }

  const autoScriptDescriptor = Object.getOwnPropertyDescriptor(window, "AutoScript");
  if (!autoScriptDescriptor || autoScriptDescriptor.configurable === true) {
    let autoScript = wrapMiniApplet(window.AutoScript, true);
    Object.defineProperty(window, "AutoScript", {
      enumerable: true,
      configurable: true,
      get() {
        return autoScript;
      },
      set(value) {
        autoScript = wrapMiniApplet(value, true);
      }
    });
    window.addEventListener("DOMContentLoaded", () => {
      autoScript = wrapMiniApplet(autoScript, true);
    }, { once: true });
  } else {
    wrapMiniApplet(window.AutoScript, true);
  }

  if (document.readyState === "loading") {
    window.addEventListener("DOMContentLoaded", triggerPublicJuntaAccessForProbe, {
      once: true
    });
  } else {
    queueMicrotask(triggerPublicJuntaAccessForProbe);
  }

  function isSupportedUri(value) {
    if (typeof value !== "string" || value.length === 0 || value.length > maxUriChars) {
      return false;
    }
    const prefix = value.slice(0, 16).toLowerCase();
    return prefix.startsWith("afirma:") || prefix.startsWith("intent:");
  }

  function observeSupportedUriBranch(value) {
    if (!isSupportedUri(value)) {
      return false;
    }
    const branch = value.slice(0, 7).toLowerCase().startsWith("intent:") ?
      "INTENT" : "AFIRMA";
    observeBranch(branch);
    return true;
  }

  function forwardUri(value) {
    if (!observeSupportedUriBranch(value)) {
      return false;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      return false;
    }
    const uriRequestId = secureRequestId();
    if (!uriRequestId) {
      return false;
    }
    bridge.postMessage(JSON.stringify({
      type: "AFIRMA_URI",
      requestId: uriRequestId,
      uri: value
    }));
    return true;
  }

  Object.defineProperty(window, "__jfmDispatchAfirmaUri", {
    value: forwardUri,
    writable: false,
    configurable: false
  });

  const originalOpen = window.open;
  window.open = function(url, ...args) {
    if (typeof url === "string" && forwardUri(url)) {
      return null;
    }
    return Reflect.apply(originalOpen, window, [url, ...args]);
  };

  const NativeWebSocket = window.WebSocket;
  if (typeof NativeWebSocket === "function") {
    function isBlockedLoopbackWebSocket(value) {
      try {
        const parsed = new URL(String(value), window.location.href);
        const host = parsed.hostname.toLowerCase();
        return (parsed.protocol === "ws:" || parsed.protocol === "wss:") &&
          (host === "127.0.0.1" || host === "localhost" || host === "[::1]");
      } catch (_) {
        return false;
      }
    }

    function JfmWebSocket(url, protocols) {
      if (isBlockedLoopbackWebSocket(url)) {
        observeBranch("WEBSOCKET");
        throw new DOMException("Local signing WebSocket blocked", "SecurityError");
      }
      const args = protocols === undefined ? [url] : [url, protocols];
      return Reflect.construct(NativeWebSocket, args, new.target || JfmWebSocket);
    }
    Object.setPrototypeOf(JfmWebSocket, NativeWebSocket);
    JfmWebSocket.prototype = NativeWebSocket.prototype;
    Object.defineProperty(window, "WebSocket", {
      value: JfmWebSocket,
      writable: false,
      configurable: false
    });
  }
})();
