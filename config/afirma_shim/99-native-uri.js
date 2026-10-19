
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
