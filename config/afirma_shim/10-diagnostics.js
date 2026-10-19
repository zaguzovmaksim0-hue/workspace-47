
  function markBadajozSignHookReady() {
    try {
      window.__jfmBadajozSignHookReady = true;
    } catch (_) {}
    if (!badajozSignHookReadyDiagnosticPosted) {
      badajozSignHookReadyDiagnosticPosted = true;
      postShimDiagnostic("BADAJOZ_SIGN_HOOK_READY");
    }
  }

  function postShimDiagnostic(stage) {
    if (!qaDiagnosticsEnabled || !bridge || typeof bridge.postMessage !== "function") {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_SHIM_DIAGNOSTIC",
        stage
      }));
    } catch (_) {
      // Diagnostics must never change the portal method's behavior.
    }
  }

  function postBadajozDiagnosticOnce(stage) {
    if (badajozDiagnosticPostedStages.has(stage)) {
      return;
    }
    badajozDiagnosticPostedStages.add(stage);
    postShimDiagnostic(stage);
  }

  postShimDiagnostic("SCRIPT_INSTALLED");
