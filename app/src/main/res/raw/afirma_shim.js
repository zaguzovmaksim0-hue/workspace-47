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
  const maxUriChars = 1048576;
  const maxArgumentLength = 1048576;
  const maxArguments = 32;
  const safeTokenPattern = /^[A-Za-z0-9._+\-]{1,64}$/;
  const wrappedMethods = new WeakSet();

  function requestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    const bytes = new Uint8Array(16);
    if (globalThis.crypto && typeof globalThis.crypto.getRandomValues === "function") {
      globalThis.crypto.getRandomValues(bytes);
    } else {
      for (let index = 0; index < bytes.length; index += 1) {
        bytes[index] = Math.floor(Math.random() * 256);
      }
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

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
    probe.postMessage(JSON.stringify(message));
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
    postProbeMessage({
      type: "MINIAPPLET_OBSERVATION",
      requestId: requestId(),
      call,
      algorithm: call === "SIGN" ? safeToken(limitedArgs[1]) : null,
      format: call === "SIGN" ? safeToken(limitedArgs[2]) : null,
      argumentLengths: limitedArgs.map(argumentLength)
    });
  }

  function observeBranch(branch) {
    postProbeMessage({
      type: "RUNTIME_BRANCH_OBSERVATION",
      requestId: requestId(),
      branch
    });
  }

  function wrapMiniAppletMethod(method, call) {
    if (typeof method !== "function" || wrappedMethods.has(method)) {
      return method;
    }
    function wrappedMiniAppletMethod(...args) {
      observeMiniAppletCall(call, args);
      return Reflect.apply(method, this, args);
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

  function wrapMiniApplet(value) {
    if ((typeof value !== "object" || value === null) && typeof value !== "function") {
      return value;
    }
    try {
      installMethodHook(value, "cargarMiniApplet", "LOAD");
      installMethodHook(value, "sign", "SIGN");
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

  function forwardUri(value) {
    if (!isSupportedUri(value)) {
      return false;
    }
    observeBranch(value.slice(0, 7).toLowerCase().startsWith("intent:") ? "INTENT" : "AFIRMA");
    if (!bridge || typeof bridge.postMessage !== "function") {
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
