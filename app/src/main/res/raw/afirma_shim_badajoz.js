
  {
    let badajozSignHookReadyDiagnosticPosted = false;
    const badajozDiagnosticWrappedFunctions = new WeakSet();
    const badajozDiagnosticPostedStages = new Set();
    const badajozOrigin = "https://sede.dip-badajoz.es";
    const badajozExtraProperties =
      "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true";

    function markBadajozSignHookReady() {
      try {
        window.__jfmBadajozSignHookReady = true;
      } catch (_) {}
      if (!badajozSignHookReadyDiagnosticPosted) {
        badajozSignHookReadyDiagnosticPosted = true;
        postShimDiagnostic("BADAJOZ_SIGN_HOOK_READY");
      }
    }

    function postBadajozDiagnosticOnce(stage) {
      if (badajozDiagnosticPostedStages.has(stage)) {
        return;
      }
      badajozDiagnosticPostedStages.add(stage);
      postShimDiagnostic(stage);
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

    getBadajozExtraProperties = () => badajozExtraProperties;

    notifyBadajozSignHook = call => {
      if (call === "SIGN" && badajozCompatibilityEnabled &&
          !badajozSignHookReadyDiagnosticPosted) {
        markBadajozSignHookReady();
      }
    };

    repairBadajozDiagnostics = () => {
        if (badajozCompatibilityEnabled) {
          wrapBadajozDiagnostic(window, "pulsarFirmarIdentificate", "BADAJOZ_PULSAR_SIGN_ENTRY");
          wrapBadajozDiagnostic(window, "firmar", "BADAJOZ_FIRMAR_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "getBase64FromText", "BADAJOZ_GET_BASE64_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "echo", "BADAJOZ_ECHO_ENTRY");
          wrapBadajozDiagnostic(window.MiniApplet, "setForceWSMode", "BADAJOZ_FORCE_WS_ENTRY");
        }
    };

    installBadajozLateHooks = () => {
      if (badajozCompatibilityEnabled) {
        postShimDiagnostic("BADAJOZ_LATE_REWRAP_STARTED");
        document.addEventListener("click", event => {
          if (event.target && event.target.id === "firmar") postBadajozDiagnosticOnce("BADAJOZ_CERT_BUTTON_CLICK");
        }, true);
      }
    };

    matchesBadajozSign = (args, successCallback, errorCallback) => {
      const isBadajozOrigin =
        badajozCompatibilityEnabled && window.location.origin === badajozOrigin;
      const isExactBadajozCall =
        isBadajozOrigin && args.length === 6 &&
        typeof args[0] === "string" && args[0].length > 0 &&
        args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
        args[1] === "SHA256withRSA" && typeof args[2] === "string" && args[2].toLowerCase() === "cades" &&
        equivalentLiteralProperties(args[3], badajozExtraProperties) &&
        typeof successCallback === "function" && typeof errorCallback === "function";
      return isBadajozOrigin ? isExactBadajozCall : null;
    };
  }
