
  const probeDocumentId = secureRequestId();
  Object.defineProperty(window, "__jfmProbeDocumentId", {
    value: probeDocumentId,
    writable: false,
    configurable: false
  });

  function notifyNativeDocumentReady() {
    const isStaBatchDocument = melillaBatchCompatibilityEnabled &&
      window.location.origin === staBatchOrigin;
    const isLugoBatchDocument = lugoBatchCompatibilityEnabled &&
      window.location.origin === lugoOrigin;
    const isCaibBatchDocument = caibRequestToken() !== null;
    if (!functionalSigningEnabled || (!isStaBatchDocument && !isLugoBatchDocument && !isCaibBatchDocument) ||
        !bridge || typeof bridge.postMessage !== "function" ||
        !probeDocumentId) {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_DOCUMENT_READY",
        documentId: probeDocumentId
      }));
    } catch (_) {
      // Native lifecycle binding remains fail-closed if registration is unavailable.
    }
  }

  notifyNativeDocumentReady();

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
        if (call === "SIGN" && interceptMiniAppletSign(args)) {
          return undefined;
        }
        if (call === "SELECT_CERTIFICATE" && interceptCertificateSelection(args)) {
          return undefined;
        }
        if (call === "BATCH_SIGN" && interceptMelillaBatchSign(args)) {
          return undefined;
        }
        if (call === "LUGO_BATCH_SIGN" && interceptLugoBatchSign(args)) {
          return undefined;
        }
        if (call === "CAIB_BATCH_SIGN" && interceptCaibBatchSign(args)) {
          return undefined;
        }
        if (interceptCaibSetupCall(call, args)) {
          return undefined;
        }
        if (interceptLugoSetupCall(call, args)) {
          return undefined;
        }
        if (interceptSevillaAtseSetupCall(call, args) || interceptUgrSetupCall(call, args)) {
          return undefined;
        }
        return Reflect.apply(method, this, args);
      }
      const previousRequestId = activeProbeRequestId;
      activeProbeRequestId = observedRequestId;
      try {
        if ((call === "SIGN" && interceptMiniAppletSign(args)) ||
            (call === "SELECT_CERTIFICATE" && interceptCertificateSelection(args)) ||
            (call === "BATCH_SIGN" && interceptMelillaBatchSign(args)) ||
            (call === "LUGO_BATCH_SIGN" && interceptLugoBatchSign(args)) ||
            (call === "CAIB_BATCH_SIGN" && interceptCaibBatchSign(args)) ||
            interceptCaibSetupCall(call, args) ||
            interceptLugoSetupCall(call, args) ||
            interceptSevillaAtseSetupCall(call, args) ||
            interceptUgrSetupCall(call, args)) {
          return undefined;
        }
        const result = Reflect.apply(method, this, args);
        if (call === "BATCH_CREATE") {
          rememberMelillaBatchCreate(args);
        } else if (call === "BATCH_ADD_DOCUMENT") {
          rememberMelillaBatchDocument(args);
        }
        return result;
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
    if (descriptor && descriptor.get && installedMethodGetters.has(descriptor.get)) return;
    if (descriptor && descriptor.configurable === false) {
      if (descriptor.writable === true && typeof target[name] === "function") {
        target[name] = wrapMiniAppletMethod(target[name], call);
        if (call === "SIGN" && badajozCompatibilityEnabled &&
            !badajozSignHookReadyDiagnosticPosted) {
          markBadajozSignHookReady();
        }
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
    installedMethodGetters.add(Object.getOwnPropertyDescriptor(target, name).get);
    if (typeof current === "function" && call === "SIGN" && badajozCompatibilityEnabled &&
        !badajozSignHookReadyDiagnosticPosted) {
      markBadajozSignHookReady();
    }
  }

  function wrapBadajozDiagnostic(target, name, stage) {
    if (target == null || (typeof target !== "object" && typeof target !== "function")) {
      return;
    }
    const current = target[name];
    if (typeof current !== "function" || badajozDiagnosticWrappedFunctions.has(current)) {
      return;
    }
    const wrapped = function() {
      postBadajozDiagnosticOnce(stage);
      return Reflect.apply(current, this, arguments);
    };
    try {
      target[name] = wrapped;
      badajozDiagnosticWrappedFunctions.add(wrapped);
    } catch (_) {}
  }

  function wrapMiniApplet(
    value,
    includeUgrSetup = false,
    includeMelillaBatch = false,
    includeIsciiiCertificateSelection = false,
    includeLugoBatch = false,
    includeCaibBatch = false
  ) {
    if ((typeof value !== "object" || value === null) && typeof value !== "function") {
      return value;
    }
    try {
      installMethodHook(value, "sign", "SIGN");
      installMethodHook(value, "cargarMiniApplet", "LOAD");
      if (includeIsciiiCertificateSelection) {
        installMethodHook(value, "selectCertificate", "SELECT_CERTIFICATE");
      }
      if (includeMelillaBatch) {
        installMethodHook(value, "createBatch", "BATCH_CREATE");
        installMethodHook(value, "addDocumentToBatch", "BATCH_ADD_DOCUMENT");
        installMethodHook(value, "signBatchProcess", "BATCH_SIGN");
      }
      if (includeLugoBatch) {
        installMethodHook(value, "cargarAppAfirma", "LUGO_CARGAR_APP_AFIRMA");
        installMethodHook(value, "signBatch", "LUGO_BATCH_SIGN");
      }
      if (includeCaibBatch) {
        installMethodHook(value, "setForceWSMode", "CAIB_SET_FORCE_WS_MODE");
        installMethodHook(value, "cargarAppAfirma", "CAIB_CARGAR_APP_AFIRMA");
        installMethodHook(value, "setServlets", "CAIB_SET_SERVLETS");
        installMethodHook(value, "signBatch", "CAIB_BATCH_SIGN");
      }
      if (sevillaAtseCompatibilityEnabled) {
        installMethodHook(value, "setForceWSMode", "SEVILLA_SET_FORCE_WS_MODE");
        installMethodHook(value, "setServlets", "SEVILLA_SET_SERVLETS");
        installMethodHook(value, "cargarAppAfirma", "SEVILLA_CARGAR_APP_AFIRMA");
      }
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
    let miniApplet = wrapMiniApplet(window.MiniApplet, false, false, xSel, false, caibBatchCompatibilityEnabled);
    Object.defineProperty(window, "MiniApplet", {
      enumerable: true,
      configurable: true,
      get() {
        return miniApplet;
      },
      set(value) {
        miniApplet = wrapMiniApplet(value, false, false, xSel, false, caibBatchCompatibilityEnabled);
      }
    });
    const rewrapCurrentMiniApplet = stage => {
      // Some portal scripts replace the global with defineProperty after document-start.
      // Rewrap the object currently exposed by the page before its login button is used.
      miniApplet = wrapMiniApplet(window.MiniApplet, false, false, xSel, false, caibBatchCompatibilityEnabled);
      postShimDiagnostic(stage);
    };
    window.addEventListener("DOMContentLoaded", () => {
      rewrapCurrentMiniApplet("MINIAPPLET_DOMCONTENTLOADED_REWRAP");
    }, { once: true });
    window.addEventListener("load", () => {
      rewrapCurrentMiniApplet("MINIAPPLET_LOAD_REWRAP");
    }, { once: true });
  } else {
    wrapMiniApplet(window.MiniApplet, false, false, xSel, false, caibBatchCompatibilityEnabled);
  }

  const autoScriptDescriptor = Object.getOwnPropertyDescriptor(window, "AutoScript");
  if (!autoScriptDescriptor || autoScriptDescriptor.configurable === true) {
    let autoScript = wrapMiniApplet(window.AutoScript, ugrCompatibilityEnabled, melillaBatchCompatibilityEnabled, iSel || vSel || xSel, lugoBatchCompatibilityEnabled);
    Object.defineProperty(window, "AutoScript", {
      enumerable: true,
      configurable: true,
      get() {
        return autoScript;
      },
      set(value) {
        autoScript = wrapMiniApplet(
          value,
          ugrCompatibilityEnabled,
          melillaBatchCompatibilityEnabled,
          iSel || vSel || xSel,
          lugoBatchCompatibilityEnabled,
        );
      }
    });
    const rewrapCurrentAutoScript = () => {
      // Keep the hook on the object that the portal actually exposed after document-start.
      autoScript = wrapMiniApplet(
        window.AutoScript,
        ugrCompatibilityEnabled,
        melillaBatchCompatibilityEnabled,
        iSel || vSel || xSel,
        lugoBatchCompatibilityEnabled,
      );
      postShimDiagnostic("AUTOSCRIPT_REWRAP");
    };
    window.addEventListener("DOMContentLoaded", rewrapCurrentAutoScript, { once: true });
    window.addEventListener("load", rewrapCurrentAutoScript, { once: true });
  } else {
    wrapMiniApplet(
      window.AutoScript,
      ugrCompatibilityEnabled,
      melillaBatchCompatibilityEnabled,
      iSel || vSel || xSel,
      lugoBatchCompatibilityEnabled,
    );
  }

  if (functionalSigningEnabled) {
    let hookGuardTimer = null;
    let documentActive = true;
    const repairCurrentSigningLibraries = () => {
      if (!documentActive) return;
      try {
        wrapMiniApplet(window.MiniApplet, false, false, xSel, false, caibBatchCompatibilityEnabled);
        wrapMiniApplet(window.AutoScript, ugrCompatibilityEnabled, melillaBatchCompatibilityEnabled,
          iSel || vSel || xSel, lugoBatchCompatibilityEnabled);
        if (badajozCompatibilityEnabled) {
          wrapBadajozDiagnostic(window, "pulsarFirmarIdentificate", "BADAJOZ_PULSAR_SIGN_ENTRY");
          wrapBadajozDiagnostic(window, "firmar", "BADAJOZ_FIRMAR_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "getBase64FromText", "BADAJOZ_GET_BASE64_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "echo", "BADAJOZ_ECHO_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "setForceWSMode", "BADAJOZ_FORCE_WS_ENTRY");
        }
      } catch (_) {
        // A non-configurable or throwing page object is not forcibly replaced.
      }
    };
    const stopHookGuard = () => {
      if (hookGuardTimer !== null) window.clearInterval(hookGuardTimer);
      hookGuardTimer = null;
    };
    const startHookGuard = () => {
      if (!documentActive || document.visibilityState === "hidden") return;
      repairCurrentSigningLibraries();
      if (hookGuardTimer === null) {
        hookGuardTimer = window.setInterval(repairCurrentSigningLibraries, 500);
      }
    };
    // Capture runs before ordinary page click/submit handlers, not just after
    // DOMContentLoaded. Polling covers programmatic late library replacement.
    document.addEventListener("click", repairCurrentSigningLibraries, true);
    document.addEventListener("submit", repairCurrentSigningLibraries, true);
    document.addEventListener("load", repairCurrentSigningLibraries, true);
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") stopHookGuard(); else startHookGuard();
    });
    window.addEventListener("pagehide", () => { documentActive = false; stopHookGuard(); });
    window.addEventListener("pageshow", () => { documentActive = true; startHookGuard(); });
    if (badajozCompatibilityEnabled) {
      postShimDiagnostic("BADAJOZ_LATE_REWRAP_STARTED");
      document.addEventListener("click", event => {
        if (event.target && event.target.id === "firmar") postBadajozDiagnosticOnce("BADAJOZ_CERT_BUTTON_CLICK");
      }, true);
    }
    startHookGuard();
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
