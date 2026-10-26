
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
      installSevillaAtseMethodHooks(value);
      installUgrMethodHooks(value, includeUgrSetup);
    } catch (_) {
      // A hostile/non-configurable object remains untouched and signing stays fail-closed.
    }
    return value;
  }
