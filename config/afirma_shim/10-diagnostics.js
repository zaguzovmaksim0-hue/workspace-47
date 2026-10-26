
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

  postShimDiagnostic("SCRIPT_INSTALLED");
