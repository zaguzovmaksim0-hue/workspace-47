/* Called with NativeAfirmaLimits.URI_CHARS by the native document-start bridge.
 * Only iframe URLs assigned by the HTTPS top-level document are forwarded.
 * Native code separately checks sourceOrigin, isMainFrame and the live document.
 */
(function (maxUriChars) {
    "use strict";
    if (window !== window.top || location.protocol !== "https:") return;
    var bridge = window.FirmaMobileFrameAfirma;
    if (!bridge || typeof bridge.postMessage !== "function" ||
            !window.crypto || typeof window.crypto.getRandomValues !== "function") return;
    var prototype = window.HTMLIFrameElement && window.HTMLIFrameElement.prototype;
    if (!prototype) return;
    var src = Object.getOwnPropertyDescriptor(prototype, "src");
    var setAttribute = prototype.setAttribute;
    if (!src || typeof src.set !== "function" || !src.configurable ||
            typeof setAttribute !== "function") return;
    var random = new Uint8Array(16);
    window.crypto.getRandomValues(random);
    var documentId = Array.prototype.map.call(random, function (value) {
        return ("0" + value.toString(16)).slice(-2);
    }).join("");
    Object.defineProperty(window, "FirmaMobileFrameDocumentId", {
        value: documentId, writable: false, configurable: false
    });
    function forward(frame, value) {
        if (frame.ownerDocument !== document || typeof value !== "string" ||
                value.length > maxUriChars || !/^afirma:\/\//i.test(value)) return false;
        try {
            bridge.postMessage(documentId + "\n" + value);
            return true;
        } catch (_) {
            // Preserve the browser path, which refuses unverifiable frames.
            return false;
        }
    }
    Object.defineProperty(prototype, "src", {
        get: src.get,
        set: function (value) {
            if (!forward(this, value)) src.set.call(this, value);
        },
        enumerable: src.enumerable,
        configurable: src.configurable
    });
    Object.defineProperty(prototype, "setAttribute", {
        value: function (name, value) {
            if (typeof name === "string" && name.toLowerCase() === "src" &&
                    forward(this, value)) return;
            return setAttribute.call(this, name, value);
        },
        writable: true,
        configurable: true
    });
})
