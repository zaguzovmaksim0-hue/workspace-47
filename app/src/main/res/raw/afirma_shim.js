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
  const maxUriChars = 1048576;

  function isSupportedUri(value) {
    if (typeof value !== "string" || value.length === 0 || value.length > maxUriChars) {
      return false;
    }
    const prefix = value.slice(0, 16).toLowerCase();
    return prefix.startsWith("afirma:") || prefix.startsWith("intent:");
  }

  function requestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  function forwardUri(value) {
    if (!isSupportedUri(value) || !bridge || typeof bridge.postMessage !== "function") {
      return false;
    }
    bridge.postMessage(JSON.stringify({
      type: "AFIRMA_URI",
      requestId: requestId(),
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
})();
