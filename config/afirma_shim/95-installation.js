
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
