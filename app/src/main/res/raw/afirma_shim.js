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
  const functionalSigningEnabled = __JFM_FUNCTIONAL_SIGNING_ENABLED__;
  const qaDiagnosticsEnabled = __JFM_QA_DIAGNOSTICS_ENABLED__;
  const ugrCompatibilityEnabled = __JFM_UGR_COMPATIBILITY_ENABLED__;
  const cantabriaCompatibilityEnabled = __JFM_CANTABRIA_COMPATIBILITY_ENABLED__;
  const jccmCompatibilityEnabled = __JFM_JCCM_COMPATIBILITY_ENABLED__;
  const jccmRegistroCompatibilityEnabled = __JFM_JCCM_REGISTRO_COMPATIBILITY_ENABLED__;
  const sevillaAtseCompatibilityEnabled = __JFM_SEVILLA_ATSE_COMPATIBILITY_ENABLED__;
  const airefCompatibilityEnabled = __JFM_AIREF_COMPATIBILITY_ENABLED__;
  const cdtiCompatibilityEnabled = __JFM_CDTI_COMPATIBILITY_ENABLED__;
  const policiaCompatibilityEnabled = __JFM_POLICIA_COMPATIBILITY_ENABLED__;
  const granCanariaCompatibilityEnabled = __JFM_GRAN_CANARIA_COMPATIBILITY_ENABLED__;
  const fuerteventuraCompatibilityEnabled = __JFM_FUERTEVENTURA_COMPATIBILITY_ENABLED__;
  const canariasCompatibilityEnabled = __JFM_CANARIAS_COMPATIBILITY_ENABLED__;
  const minecoCompatibilityEnabled = __JFM_MINECO_COMPATIBILITY_ENABLED__;
  const melillaBatchCompatibilityEnabled = __JFM_MELILLA_BATCH_COMPATIBILITY_ENABLED__;
  const lugoBatchCompatibilityEnabled = __JFM_LUGO_BATCH_COMPATIBILITY_ENABLED__;
  const caibBatchCompatibilityEnabled = __JFM_CAIB_BATCH_COMPATIBILITY_ENABLED__;
  const iSel = __JFM_ISCIII_CERTIFICATE_SELECTION_ENABLED__;
  const vSel = __JFM_VALENCIA_CERTIFICATE_SELECTION_ENABLED__;
  const xSel = __JFM_XUNTA_GALICIA_COMPATIBILITY_ENABLED__;
  const euskadiClientAuthPostEnabled = __JFM_EUSKADI_CLIENT_AUTH_POST_ENABLED__;
  const accedaCompatibilityEnabled = __JFM_ACCEDA_COMPATIBILITY_ENABLED__;
  const badajozCompatibilityEnabled = __JFM_BADAJOZ_COMPATIBILITY_ENABLED__;
  const ugrOrigin = "https://sede.ugr.es";
  const cantabriaOrigin = "https://rec.cantabria.es";
  const cantabriaChallengePattern = /^[0-9a-f]{40}$/;
  const cantabriaExtraProperties = "filters=\nmode=implicit";
  const ugrLiteral = "Universidad de Granada";
  const ugrLiteralBase64 = "VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==";
  const ugrStorageUrl = "https://sede.ugr.es/afirma-signature-storage/StorageService";
  const ugrRetrieveUrl = "https://sede.ugr.es/afirma-signature-retriever/RetrieveService";
  const jccmOrigin = "https://ventanillaelectronica.jccm.es";
  const jccmPayloadBase64 = "QUJDREU=";
  const jccmRegistroOrigin = "https://registrounicociudadanos.jccm.es";
  const jccmRegistroProtectedPage =
    "https://registrounicociudadanos.jccm.es/registrounicociudadanos/accesoclvd.do";
  const jccmRegistroExtraProperties = "format=XAdES Detached\nmode=implicit";
  const sevillaAtseOrigin = "https://www.sevilla.org";
  const sevillaAtseChallengePattern = /^[A-Za-z0-9_-]{40}$/;
  const airefOrigin = "https://sede.airef.es";
  const airefSigningPath = "/invesiteRE/action/solicitud/view";
  const airefSigningQueryPattern = /^\?id=[0-9]{1,20}$/;
  const cdtiOrigin = "https://sede.cdti.gob.es";
  const cdtiPage =
    "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx";
  const cdtiChallengePattern = /^CertExp[0-9a-f]{32}[0-9a-z]{24}$/;
  const cdtiExtraProperties = "filters=nonexpired";
  const policiaOrigin = "https://sede.policia.gob.es";
  const granCanariaOrigin = "https://sede.grancanaria.com";
  const canariasOrigin = "https://sede.gobiernodecanarias.org";
  const canariasPage = "https://sede.gobiernodecanarias.org/sede/identificacion";
  const canariasChallengePattern =
    /^(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$/;
  const canariasExtraProperties =
    "format=CAdES Detached\n" +
    "serverUrl=https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService\n" +
    "referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\n" +
    "filters=nonexpired:true;signingCert:true;issuer.rfc2254:(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))(!(O=PKI))(!(O=DO_NOT_TRUST*)))";
  const granCanariaExtraProperties = "headless=true\nfilters=nonexpired:";
  const fuerteventuraOrigin = "https://sede.cabildofuer.es";
  const fuerteventuraSigningPage =
    "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert";
  const fuerteventuraExtraProperties =
    "signaturePositionOnPageLowerLeftX = 50\n" +
    "signaturePositionOnPageLowerLeftY = 15\n" +
    "signaturePositionOnPageUpperRightX = 150\n" +
    "signaturePositionOnPageUpperRightY = 50\n" +
    "signaturePages = all\n" +
    "layer2Text= Firmado por $$SUBJECTCN$$ el día $$SIGNDATE=dd/MM/yyyy$$ $$ORGANIZATION$$\n" +
    "layer2FontSize= 6\n" +
    "layer2FontFamily= 0\n" +
    "layer2FontStyle= 0\n" +
    "signatureRotation= 0\n" +
    "includeQuestionMark= false\n" +
    "obfuscateCertText= true\n";
  const minecoOrigin = "https://serviciosede.mineco.gob.es";
  const minecoSigningPage = "https://serviciosede.mineco.gob.es/FB/solicitud/firma.aspx";
  const minecoExtraProperties =
    "filters=signingCert:;nonexpired:\nexpPolicy=FirmaAGE\nsignatureSubFilter=ETSI.CAdES.detached";
  const policiaProcedurePage =
    "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml";
  const policiaExtraProperties =
    "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:";
  const staBatchOrigin = __JFM_STA_BATCH_ORIGIN__;
  const lugoOrigin = "https://sede.deputacionlugo.org";
  const lugoClientBase = "https://sede.deputacionlugo.org/opencms";
  const lugoExtraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\n";
  const caibOrigin = "https://intranet.caib.es";
  const caibClientBase = "https://intranet.caib.es/portafibback";
  const caibSignerPathPattern = /^\/portafibback\/public\/signmodule\/requestPlugin\/([A-Za-z0-9_-]{28})\/-1\/index$/;
  const caibSignatureIdPattern = /^[A-Za-z0-9_-]{40}$/;
  const caibBatchBase64Pattern = /^[A-Za-z0-9_-]+={0,2}$/;
  const iPage = "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null";
  const iProps = "serverUrl=http://dtomcat7.isciiides.es:8080/afirma-server-triphase-signer/SignatureService";
  const vPage = "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml";
  const vProps = "filters=keyusage.nonrepudiation:true;nonexpired:true\nheadless=true";
  const accedaOrigin = "https://sede.administracionespublicas.gob.es";
  const accedaExtraProperties = "format=PAdES Detached\nexpPolicy=FirmaAGE\nnonexpired:true";
  const badajozOrigin = "https://sede.dip-badajoz.es";
  const badajozExtraProperties =
    "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true";
  const euskadiProfileId = "euskadi-sede-electronica";
  const euskadiAuthPage =
    "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication";
  const euskadiClientAuthTarget =
    "https://eidas2.izenpe.com/cert-authn-external-validation/authenticate";
  const euskadiFormContentType = "application/x-www-form-urlencoded";
  const xuntaOrigin = "https://sede.xunta.gal";
  const xuntaPage = "https://sede.xunta.gal/presenta/novo/PR004A_2025_1";
  const xuntaSelectProps = "filters=nonexpired";
  const xuntaFixedProperties = Object.freeze({
    format: "PAdES",
    signatureSubFilter: "ETSI.CAdES.detached",
    serverUrl: "https://sede.xunta.gal/presenta/sinatura/SignatureService",
    referencesDigestMethod: "http://www.w3.org/2000/09/xmldsig#sha1",
    mimeType: "hash/sha256",
    headless: "true"
  });
  const xuntaAllowedDynamicProperties = new Set([
    "filters", "locale", "nif", "id", "codigoSeguridad",
    "marcaFirmaCustom", "dataUser", "idBorrador"
  ]);
  const maxUriChars = 1048576;
  const maxArgumentLength = 1048576;
  const maxArguments = 32;
  const maxDirectDataChars = 699052;
  const maxExtraPropertiesChars = 65536;
  const maxBatchUrlChars = 8192;
  const maxBatchIdentifierChars = 1024;
  const maxBatchDocuments = 64;
  const maxBatchResultChars = 786432;
  const signTimeoutMillis = 120000;
  const safeTokenPattern = /^[A-Za-z0-9._+\-]{1,64}$/;
  const canonicalUuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const base64Pattern = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
  const wrappedMethods = new WeakSet();
  const pendingCallbacks = new Map();
  const pendingBatchCallbacks = new Map();
  let activeMelillaBatch = null;
  let activeProbeRequestId = null;

  function secureRequestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    if (!globalThis.crypto || typeof globalThis.crypto.getRandomValues !== "function") {
      return null;
    }
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  let euskadiClientAuthPostSent = false;

  function isSafeEuskadiOpaqueRequest(value) {
    if (typeof value !== "string" || value.length < 1 || value.length > 4096) {
      return false;
    }
    for (let index = 0; index < value.length; index += 1) {
      const code = value.charCodeAt(index);
      if (code < 0x21 || code > 0x7e) return false;
    }
    return true;
  }

  function interceptEuskadiClientAuthPost(form) {
    if (!euskadiClientAuthPostEnabled || window.top !== window ||
        location.href !== euskadiAuthPage) {
      return false;
    }
    if (!(form instanceof HTMLFormElement)) return false;

    let action;
    try {
      action = new URL(form.action, location.href).href;
    } catch (_) {
      return true;
    }
    // Unrelated forms on the reviewed page remain untouched. Only the exact client-auth target is mediated.
    if (action !== euskadiClientAuthTarget) return false;
    if (form.method.toUpperCase() !== "POST" ||
        form.enctype.toLowerCase() !== euskadiFormContentType) {
      return true;
    }

    const namedControls = Array.from(form.elements).filter(control => control && control.name);
    if (namedControls.length !== 2 || namedControls.some(control => control.type !== "hidden")) {
      return true;
    }
    const names = namedControls.map(control => control.name).sort();
    if (names[0] !== "request" || names[1] !== "x_correlation_id") return true;

    const requestControl = form.elements.namedItem("request");
    const correlationControl = form.elements.namedItem("x_correlation_id");
    const requestValue = requestControl && requestControl.value;
    const correlationValue = correlationControl && correlationControl.value;
    if (!isSafeEuskadiOpaqueRequest(requestValue) ||
        typeof correlationValue !== "string" ||
        !canonicalUuidPattern.test(correlationValue) ||
        euskadiClientAuthPostSent || !bridge || typeof bridge.postMessage !== "function") {
      return true;
    }

    const requestId = secureRequestId();
    if (!requestId || !canonicalUuidPattern.test(requestId)) return true;
    euskadiClientAuthPostSent = true;
    try {
      bridge.postMessage(JSON.stringify({
        type: "EUSKADI_CLIENT_AUTH_POST",
        profileId: euskadiProfileId,
        requestId,
        method: "POST",
        contentType: euskadiFormContentType,
        targetUrl: euskadiClientAuthTarget,
        request: requestValue,
        x_correlation_id: correlationValue
      }));
    } catch (_) {
      euskadiClientAuthPostSent = false;
    }
    // The normal WebView never sends this POST; native replays the exact form body only in the isolated TLS WebView.
    return true;
  }

  if (euskadiClientAuthPostEnabled && typeof HTMLFormElement === "function") {
    const nativeSubmit = HTMLFormElement.prototype.submit;
    const nativeRequestSubmit = HTMLFormElement.prototype.requestSubmit;
    try {
      HTMLFormElement.prototype.submit = function() {
        if (interceptEuskadiClientAuthPost(this)) return undefined;
        return Reflect.apply(nativeSubmit, this, []);
      };
      if (typeof nativeRequestSubmit === "function") {
        HTMLFormElement.prototype.requestSubmit = function(submitter) {
          if (interceptEuskadiClientAuthPost(this)) return undefined;
          const args = submitter === undefined ? [] : [submitter];
          return Reflect.apply(nativeRequestSubmit, this, args);
        };
      }
      document.addEventListener("submit", event => {
        if (interceptEuskadiClientAuthPost(event.target)) {
          event.preventDefault();
          event.stopImmediatePropagation();
        }
      }, true);
    } catch (_) {
      // If the exact hook cannot be installed, do not add any broader fallback interception.
    }
  }

  const closedErrorMessages = Object.freeze({
    USER_CANCELLED: "La operación de firma se ha cancelado.",
    REQUEST_EXPIRED: "La solicitud de firma ha caducado.",
    CERTIFICATE_LOCKED: "El certificado está bloqueado.",
    NAVIGATION_CHANGED: "La página cambió durante la firma.",
    SESSION_EXPIRED: "La sesión del portal ha caducado.",
    PROTOCOL_FAILED: "El portal no pudo completar la firma."
  });

  function safeErrorMessage(errorCode) {
    return closedErrorMessages[errorCode] || "No se pudo completar la firma.";
  }

  function postQaPortalDiagnostic(stage, requestIdValue) {
    if (!qaDiagnosticsEnabled || !bridge || typeof bridge.postMessage !== "function" ||
        !canonicalUuidPattern.test(probeDocumentId) ||
        !canonicalUuidPattern.test(requestIdValue)) {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: "QA_PORTAL_DIAGNOSTIC",
        documentId: probeDocumentId,
        requestId: requestIdValue,
        stage
      }));
    } catch (_) {
      // QA diagnostics are best-effort and never alter portal behavior.
    }
  }

  function clearPending(requestIdValue) {
    const pending = pendingCallbacks.get(requestIdValue);
    if (!pending) {
      return null;
    }
    pendingCallbacks.delete(requestIdValue);
    clearTimeout(pending.timeoutId);
    return pending;
  }

  function clearPendingBatch(requestIdValue) {
    const pending = pendingBatchCallbacks.get(requestIdValue);
    if (!pending) {
      return null;
    }
    pendingBatchCallbacks.delete(requestIdValue);
    clearTimeout(pending.timeoutId);
    return pending;
  }

  function notifyNativeCancel(requestIdValue, messageType = "MINIAPPLET_CANCEL") {
    if (!bridge || typeof bridge.postMessage !== "function") {
      return;
    }
    try {
      bridge.postMessage(JSON.stringify({
        type: messageType,
        documentId: probeDocumentId,
        requestId: requestIdValue
      }));
    } catch (_) {
      // Native cancellation is best-effort; page teardown still drops callbacks.
    }
  }

  function rejectDirectCall(errorCallback, errorCode) {
    if (typeof errorCallback === "function") {
      try {
        errorCallback(errorCode, safeErrorMessage(errorCode));
      } catch (_) {
        // Portal callback exceptions must not reopen native signing.
      }
    }
  }

  function isIdenticalInFlightCall(pending, args) {
    return pending !== undefined &&
      pending.dataB64 === args[0] &&
      pending.algorithm === args[1] &&
      pending.format === args[2] &&
      pending.extraProperties === args[3] &&
      pending.successCallback === args[4] &&
      pending.errorCallback === args[5];
  }

  function isValidSevillaAtseChallenge(value) {
    if (typeof value !== "string") {
      return false;
    }
    try {
      const decoded = atob(value);
      return decoded.length === 40 && sevillaAtseChallengePattern.test(decoded);
    } catch (_) {
      return false;
    }
  }

  function isValidAirefPayload(value) {
    if (typeof value !== "string" || value.length !== 44 || !base64Pattern.test(value)) {
      return false;
    }
    try {
      const decoded = atob(value);
      return decoded.length === 32 && btoa(decoded) === value;
    } catch (_) {
      return false;
    }
  }

  function isExactAirefSigningPage() {
    return window.location.origin === airefOrigin &&
      window.location.pathname === airefSigningPath &&
      airefSigningQueryPattern.test(window.location.search) &&
      window.location.hash === "";
  }

  function isExactCanariasChallenge(value) {
    if (typeof value !== "string" || !base64Pattern.test(value)) {
      return false;
    }
    try {
      const decoded = globalThis.atob(value);
      return canariasChallengePattern.test(decoded) && globalThis.btoa(decoded) === value;
    } catch (_) {
      return false;
    }
  }

  function isExactXuntaProperties(value) {
    if (typeof value !== "string" || value.length === 0 || value.length > maxExtraPropertiesChars) {
      return false;
    }
    const entries = new Map();
    for (const rawLine of value.split("\n")) {
      const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;
      const separator = line.indexOf("=");
      if (separator <= 0) return false;
      const key = line.slice(0, separator);
      const propertyValue = line.slice(separator + 1);
      if (!/^[A-Za-z][A-Za-z0-9._-]{0,63}$/.test(key) || propertyValue.length > 8192 ||
          entries.has(key) || Array.from(propertyValue).some(ch => ch.charCodeAt(0) < 0x20 || ch.charCodeAt(0) === 0x7f)) {
        return false;
      }
      entries.set(key, propertyValue);
    }
    for (const [key, expected] of Object.entries(xuntaFixedProperties)) {
      if (entries.get(key) !== expected) return false;
    }
    if (!entries.has("filters") || !entries.has("locale") || entries.get("locale") === "") return false;
    const filters = entries.get("filters");
    if (filters !== "nonexpired" &&
        !(filters.startsWith("nonexpired;encodedcert:") &&
          base64Pattern.test(filters.slice("nonexpired;encodedcert:".length)))) {
      return false;
    }
    for (const key of entries.keys()) {
      if (!Object.prototype.hasOwnProperty.call(xuntaFixedProperties, key) &&
          !xuntaAllowedDynamicProperties.has(key)) return false;
    }
    return true;
  }

  function interceptCertificateSelection(a) {
    if (!iSel && !vSel && !xSel) return false;
    let valid = false;
    if (iSel && location.href === iPage && a.length === 3 && a[0] === iProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    } else if (vSel && location.href === vPage && a.length === 3 && a[0] === vProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    } else if (xSel && location.href === xuntaPage && a.length === 3 && a[0] === xuntaSelectProps &&
        typeof a[1] === "function" && typeof a[2] === "function") {
      valid = true;
    }
    if (!valid) {
      if (a.length >= 3 && typeof a[2] === "function") {
        rejectDirectCall(a[2], "INVALID_REQUEST");
      }
      return true;
    }
    if (!functionalSigningEnabled || !bridge || typeof bridge.postMessage !== "function" ||
        !canonicalUuidPattern.test(probeDocumentId) || pendingCallbacks.size) {
      rejectDirectCall(a[2], "PROTOCOL_FAILED"); return true;
    }
    const id = secureRequestId();
    if (!id) { rejectDirectCall(a[2], "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => { if (clearPending(id)) {
      notifyNativeCancel(id, "MINIAPPLET_SELECT_CERTIFICATE_CANCEL");
      rejectDirectCall(a[2], "REQUEST_EXPIRED"); } }, signTimeoutMillis);
    pendingCallbacks.set(id, { selection: true, successCallback: a[1], errorCallback: a[2], timeoutId });
    try { bridge.postMessage(JSON.stringify({ type: "MINIAPPLET_SELECT_CERTIFICATE",
      documentId: probeDocumentId, requestId: id, extraProperties: a[0] })); }
    catch (_) { clearPending(id); rejectDirectCall(a[2], "PROTOCOL_FAILED"); }
    return true;
  }

  function interceptMiniAppletSign(args) {
    if (!functionalSigningEnabled) {
      return false;
    }
    const successCallback = args[4];
    const errorCallback = args[5];
    const isSevillaAtseOrigin =
      sevillaAtseCompatibilityEnabled && window.location.origin === sevillaAtseOrigin;
    const isExactSevillaAtseCall =
      isSevillaAtseOrigin &&
      args.length === 6 &&
      isValidSevillaAtseChallenge(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      args[3] == null &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isSevillaAtseOrigin && !isExactSevillaAtseCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isAccedaOrigin =
      accedaCompatibilityEnabled && window.location.origin === accedaOrigin;
    const isExactAccedaCall =
      isAccedaOrigin &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "PAdES" &&
      typeof args[3] === "string" &&
      args[3].trim() === accedaExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isAccedaOrigin && !isExactAccedaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isAirefOrigin =
      airefCompatibilityEnabled && window.location.origin === airefOrigin;
    const isExactAirefCall =
      isAirefOrigin && isExactAirefSigningPage() &&
      args.length === 6 &&
      isValidAirefPayload(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      args[3] === null &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isAirefOrigin && !isExactAirefCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isCanariasOrigin =
      canariasCompatibilityEnabled && window.location.origin === canariasOrigin;
    const isCanariasPage =
      isCanariasOrigin && window.location.href === canariasPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCanariasCall =
      isCanariasPage && args.length === 6 && isExactCanariasChallenge(args[0]) &&
      args[1] === "SHA1withRSA" && args[2] === "CAdES" &&
      args[3] === canariasExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isCanariasOrigin && !isExactCanariasCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isGranCanariaOrigin =
      granCanariaCompatibilityEnabled && window.location.origin === granCanariaOrigin;
    const isExactGranCanariaCall =
      isGranCanariaOrigin &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "PAdES" &&
      args[3] === granCanariaExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isGranCanariaOrigin && !isExactGranCanariaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isFuerteventuraOrigin =
      fuerteventuraCompatibilityEnabled && window.location.origin === fuerteventuraOrigin;
    const isFuerteventuraSigningPage =
      isFuerteventuraOrigin && window.location.href === fuerteventuraSigningPage &&
      window.location.hash === "";
    const isExactFuerteventuraCall =
      isFuerteventuraSigningPage &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA256withRSA" &&
      args[2] === "PAdES" &&
      args[3] === fuerteventuraExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isFuerteventuraOrigin && !isExactFuerteventuraCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isMinecoOrigin =
      minecoCompatibilityEnabled && window.location.origin === minecoOrigin;
    const isMinecoSigningPage =
      isMinecoOrigin && window.location.href === minecoSigningPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactMinecoCall =
      isMinecoSigningPage && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" && args[2] === "PAdES" &&
      args[3] === minecoExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isMinecoOrigin && !isExactMinecoCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isXuntaOrigin = xSel && window.location.origin === xuntaOrigin;
    const isXuntaPage = isXuntaOrigin && window.location.href === xuntaPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactXuntaCall =
      isXuntaPage && args.length === 6 && args[0] === "doc" &&
      args[1] === "SHA1withRSA" && args[2] === "PAdEStri" &&
      isExactXuntaProperties(args[3]) &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isXuntaOrigin && !isExactXuntaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isCdtiOrigin =
      cdtiCompatibilityEnabled && window.location.origin === cdtiOrigin;
    const isCdtiPage =
      isCdtiOrigin && window.location.href === cdtiPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCdtiCall =
      isCdtiPage && args.length === 6 &&
      typeof args[0] === "string" && cdtiChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "XAdES Enveloping" &&
      args[3] === cdtiExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isCdtiOrigin && !isExactCdtiCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isPoliciaOrigin =
      policiaCompatibilityEnabled && window.location.origin === policiaOrigin;
    const isPoliciaProcedurePage =
      isPoliciaOrigin &&
      window.location.href === policiaProcedurePage &&
      window.location.search === "" &&
      window.location.hash === "";
    const isExactPoliciaCall =
      isPoliciaProcedurePage &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      (args[3] === policiaExtraProperties || args[3] === policiaExtraProperties.replace(/\n/g, "\r\n")) &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    if (isPoliciaOrigin && !isExactPoliciaCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isJccmRegistroOrigin =
      jccmRegistroCompatibilityEnabled && window.location.origin === jccmRegistroOrigin;
    const isJccmRegistroPage =
      isJccmRegistroOrigin &&
      window.location.href === jccmRegistroProtectedPage &&
      window.location.hash === "";
    const isExactJccmRegistroCall =
      isJccmRegistroPage && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA512withRSA" && args[2] === "XADES" &&
      args[3] === jccmRegistroExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isJccmRegistroOrigin && !isExactJccmRegistroCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isBadajozOrigin =
      badajozCompatibilityEnabled && window.location.origin === badajozOrigin;
    const isExactBadajozCall =
      isBadajozOrigin && args.length === 6 &&
      typeof args[0] === "string" && args[0].length > 0 &&
      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&
      args[1] === "SHA256withRSA" && args[2] === "Cades" &&
      args[3] === badajozExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    if (isBadajozOrigin && !isExactBadajozCall) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    const isExactUgrLiteralCall =
      ugrCompatibilityEnabled &&
      window.location.origin === ugrOrigin &&
      args[0] === ugrLiteral &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      args[3] === "";
    const isExactCantabriaCall =
      cantabriaCompatibilityEnabled &&
      window.location.origin === cantabriaOrigin &&
      typeof args[0] === "string" &&
      cantabriaChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "CAdES" &&
      args[3] === cantabriaExtraProperties;
    const isJccmOrigin =
      jccmCompatibilityEnabled && window.location.origin === jccmOrigin;
    const isExactJccmCall =
      isJccmOrigin &&
      args[0] === jccmPayloadBase64 &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      (args[3] === null || args[3] === "");
    const dataB64 = isExactUgrLiteralCall ? ugrLiteralBase64 :
      isExactCantabriaCall && typeof globalThis.btoa === "function" ?
        globalThis.btoa(args[0]) :
      isExactXuntaCall && typeof globalThis.btoa === "function" ?
        globalThis.btoa(args[0]) :
      isExactCdtiCall ? args[0] + "=" : args[0];
    const hasValidUgrDataEncoding = base64Pattern.test(dataB64);
    const hasValidCantabriaDataEncoding = isExactCantabriaCall &&
      typeof globalThis.btoa === "function" &&
      base64Pattern.test(dataB64);
    const isJuntaCades =
      !jccmCompatibilityEnabled && !canariasCompatibilityEnabled &&
      (args[1] === "SHA1withRSA" || args[1] === "SHA256withRSA") &&
      args[2] === "CAdES" && typeof args[3] === "string" &&
      args[3].length <= maxExtraPropertiesChars;
    const isRegXades = !jccmCompatibilityEnabled &&
      args[1] === "SHA512withRSA" &&
      args[2] === "XAdES Detached" && args[3] === null;
    if (args.length !== 6 || typeof successCallback !== "function" ||
        typeof errorCallback !== "function" || typeof args[0] !== "string" ||
        args[0].length === 0 || args[0].length > maxDirectDataChars ||
        ((!isExactUgrLiteralCall && !isExactCantabriaCall && !isExactJccmCall &&
          !isExactJccmRegistroCall && !isExactSevillaAtseCall && !isExactAirefCall &&
          !isExactGranCanariaCall && !isExactFuerteventuraCall && !isExactCanariasCall &&
          !isExactMinecoCall && !isExactCdtiCall && !isExactXuntaCall &&
          !isExactAccedaCall && !isExactBadajozCall &&
          !base64Pattern.test(args[0])) ||
          (isExactUgrLiteralCall && !hasValidUgrDataEncoding) ||
          (isExactCantabriaCall && !hasValidCantabriaDataEncoding) ||
          (isExactXuntaCall && (typeof globalThis.btoa !== "function" || !base64Pattern.test(dataB64)))) ||
        (!isJuntaCades && !isRegXades && !isExactUgrLiteralCall &&
          !isExactCantabriaCall && !isExactJccmCall && !isExactJccmRegistroCall &&
          !isExactSevillaAtseCall &&
          !isExactAirefCall && !isExactCdtiCall && !isExactPoliciaCall &&
          !isExactGranCanariaCall && !isExactFuerteventuraCall && !isExactCanariasCall && !isExactMinecoCall &&
          !isExactXuntaCall && !isExactAccedaCall && !isExactBadajozCall)) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    if (pendingCallbacks.size !== 0) {
      const pending = pendingCallbacks.values().next().value;
      if (isIdenticalInFlightCall(pending, args)) {
        // A repeated tap must not submit the portal's error form while the
        // original, byte-identical operation is still completing.
        return true;
      }
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const directRequestId = secureRequestId();
    if (!directRequestId || !probeDocumentId) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const timeoutId = setTimeout(() => {
      const expired = clearPending(directRequestId);
      if (expired) {
        notifyNativeCancel(directRequestId);
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingCallbacks.set(directRequestId, {
      dataB64: args[0],
      algorithm: args[1],
      format: args[2],
      extraProperties: args[3],
      successCallback,
      errorCallback,
      timeoutId
    });
    const nativeFormat = isExactBadajozCall ? "CAdES" : args[2];
    try {
      bridge.postMessage(JSON.stringify({
        type: "MINIAPPLET_SIGN",
        documentId: probeDocumentId,
        requestId: directRequestId,
        dataB64,
        algorithm: args[1],
        format: nativeFormat,
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPending(directRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function isMelillaBatchPage() {
    return functionalSigningEnabled && melillaBatchCompatibilityEnabled &&
      window.location.origin === staBatchOrigin;
  }

  function isSafeBatchIdentifier(value) {
    return typeof value === "string" && value.length > 0 &&
      value.length <= maxBatchIdentifierChars &&
      !Array.from(value).some(character => /\s/.test(character) ||
        character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f);
  }

  function batchExtraParams(format) {
    if (format === "PAdES") {
      return "signatureSubFilter=ETSI.CAdES.detached";
    }
    if (format === "XAdES") {
      return "mode=implicit";
    }
    return null;
  }

  function isSupportedBatchFormat(value) {
    return value === "CAdES" || value === "PAdES" || value === "XAdES";
  }

  function rememberMelillaBatchCreate(args) {
    if (!isMelillaBatchPage()) {
      activeMelillaBatch = null;
      return;
    }
    if (args.length !== 4 || args[0] !== "SHA256withRSA" ||
        !isSupportedBatchFormat(args[1]) || args[2] !== "sign" || args[3] !== null) {
      activeMelillaBatch = null;
      return;
    }
    activeMelillaBatch = {
      algorithm: args[0],
      format: args[1],
      suboperation: args[2],
      documents: [],
      invalid: false
    };
  }

  function rememberMelillaBatchDocument(args) {
    const batch = activeMelillaBatch;
    if (!isMelillaBatchPage() || !batch) {
      return;
    }
    const documentFormat = args[2];
    const documentSuboperation = args[3];
    const effectiveFormat = documentFormat === null ? batch.format : documentFormat;
    const valid = args.length === 5 && isSafeBatchIdentifier(args[0]) &&
      typeof args[1] === "string" && args[1].length > 0 &&
      args[1].length <= maxBatchUrlChars && !args[1].includes("\u0000") &&
      (documentFormat === null || isSupportedBatchFormat(documentFormat)) &&
      (documentSuboperation === null || documentSuboperation === "sign") &&
      args[4] === batchExtraParams(effectiveFormat) &&
      !batch.documents.some(document => document.id === args[0]) &&
      batch.documents.length < maxBatchDocuments;
    if (!valid) {
      batch.invalid = true;
      return;
    }
    const document = {
      id: args[0],
      datareference: args[1]
    };
    if (documentFormat !== null) {
      document.format = documentFormat;
    }
    if (documentSuboperation !== null) {
      document.suboperation = documentSuboperation;
    }
    batch.documents.push(document);
  }

  function interceptMelillaBatchSign(args) {
    if (!isMelillaBatchPage()) {
      return false;
    }
    const errorCallback = args[5];
    const batch = activeMelillaBatch;
    if (args.length !== 6 || args[0] !== false || typeof args[1] !== "string" ||
        args[1].length === 0 || args[1].length > maxBatchUrlChars ||
        typeof args[2] !== "string" || args[2].length === 0 ||
        args[2].length > maxBatchUrlChars || args[3] !== null ||
        typeof args[4] !== "function" || typeof errorCallback !== "function" ||
        !batch || batch.invalid || batch.documents.length === 0 || !probeDocumentId) {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function") {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    if (pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "MINIAPPLET_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4],
      errorCallback,
      timeoutId
    });
    const message = {
      type: "MINIAPPLET_BATCH",
      documentId: probeDocumentId,
      requestId: batchRequestId,
      batchPreSignerUrl: args[1],
      batchPostSignerUrl: args[2],
      algorithm: batch.algorithm,
      format: batch.format,
      suboperation: batch.suboperation,
      stopOnError: false,
      documentos: batch.documents.map(document => ({ ...document }))
    };
    activeMelillaBatch = null;
    try {
      bridge.postMessage(JSON.stringify(message));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function interceptLugoSetupCall(call, args) {
    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {
      return false;
    }
    return call === "LUGO_CARGAR_APP_AFIRMA" &&
      args.length === 1 && args[0] === lugoClientBase;
  }

  function interceptLugoBatchSign(args) {
    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {
      return false;
    }
    const errorCallback = args[5];
    if (args.length !== 6 || typeof args[0] !== "string" || args[0].length === 0 ||
        args[0].length > maxBatchResultChars || typeof args[1] !== "string" ||
        args[1].length === 0 || args[1].length > maxBatchUrlChars ||
        typeof args[2] !== "string" || args[2].length === 0 || args[2].length > maxBatchUrlChars ||
        args[3] !== lugoExtraProperties || typeof args[4] !== "function" ||
        typeof errorCallback !== "function") {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function" || !probeDocumentId ||
        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) { rejectDirectCall(errorCallback, "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "LUGO_XML_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4], errorCallback, timeoutId, lugoXml: true
    });
    try {
      bridge.postMessage(JSON.stringify({
        type: "LUGO_XML_BATCH",
        documentId: probeDocumentId,
        requestId: batchRequestId,
        batchXml: args[0],
        batchPreSignerUrl: args[1],
        batchPostSignerUrl: args[2],
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function caibRequestToken() {
    if (!caibBatchCompatibilityEnabled || window.location.origin !== caibOrigin) return null;
    const match = window.location.pathname.match(caibSignerPathPattern);
    return match ? match[1] : null;
  }

  function decodeCaibUrlBase64(value) {
    if (typeof value !== "string" || !caibBatchBase64Pattern.test(value)) return null;
    try {
      const normalized = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
      return atob(normalized);
    } catch (_) { return null; }
  }

  function caibSignatureId(batchXml, token) {
    if (typeof batchXml !== "string" || batchXml.length === 0 || batchXml.length > 8192) return null;
    const xml = decodeCaibUrlBase64(batchXml);
    if (xml === null || xml.length > 6144) return null;
    const ids = Array.from(xml.matchAll(/<singlesign\s+Id="([A-Za-z0-9_-]{40})">/g));
    if (ids.length !== 1 || !caibSignatureIdPattern.test(ids[0][1])) return null;
    return decodeCaibUrlBase64(ids[0][1]) === `${token}|0` ? ids[0][1] : null;
  }

  function caibExtraProperties(signatureId) {
    return "mode=implicit\n" +
      "signatureSubFilter=ETSI.CAdES.detached\n" +
      `SignatureId=${signatureId}\n` +
      "signReason=FORM-1.pdf\n" +
      "formatmobile=PAdEStri\n" +
      "formatbatch=PAdES\n" +
      "format=PAdES\n" +
      "filters.1=nonexpired:\n" +
      "allowSigningCertifiedPdfs=true\n" +
      "algorithm=SHA256withRSA\n";
  }

  function caibEndpoint(token, name) {
    return `${caibOrigin}/portafibback/public/signmodule/requestPlugin/${token}/-1/${name}`;
  }

  function interceptCaibSetupCall(call, args) {
    const token = caibRequestToken();
    if (!token) return false;
    if (call === "CAIB_SET_FORCE_WS_MODE") return args.length === 1 && args[0] === true;
    if (call === "CAIB_CARGAR_APP_AFIRMA") return args.length === 1 && args[0] === caibClientBase;
    if (call === "CAIB_SET_SERVLETS") {
      return args.length === 2 && args[0] === caibEndpoint(token, "StorageService") &&
        args[1] === caibEndpoint(token, "RetrieveService");
    }
    return false;
  }

  function interceptCaibBatchSign(args) {
    const token = caibRequestToken();
    if (!token) return false;
    const errorCallback = args[5];
    const signatureId = caibSignatureId(args[0], token);
    if (args.length !== 6 || signatureId === null ||
        args[1] !== caibEndpoint(token, "BatchPresigner") ||
        args[2] !== caibEndpoint(token, "BatchPostsigner") ||
        args[3] !== caibExtraProperties(signatureId) || typeof args[4] !== "function" ||
        typeof errorCallback !== "function") {
      rejectDirectCall(errorCallback, "INVALID_REQUEST");
      return true;
    }
    if (!bridge || typeof bridge.postMessage !== "function" || !probeDocumentId ||
        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
      return true;
    }
    const batchRequestId = secureRequestId();
    if (!batchRequestId) { rejectDirectCall(errorCallback, "PROTOCOL_FAILED"); return true; }
    const timeoutId = setTimeout(() => {
      const expired = clearPendingBatch(batchRequestId);
      if (expired) {
        notifyNativeCancel(batchRequestId, "CAIB_XML_BATCH_CANCEL");
        rejectDirectCall(expired.errorCallback, "REQUEST_EXPIRED");
      }
    }, signTimeoutMillis);
    pendingBatchCallbacks.set(batchRequestId, {
      successCallback: args[4], errorCallback, timeoutId, caibXml: true
    });
    try {
      bridge.postMessage(JSON.stringify({
        type: "CAIB_XML_BATCH",
        documentId: probeDocumentId,
        requestId: batchRequestId,
        batchXml: args[0],
        batchPreSignerUrl: args[1],
        batchPostSignerUrl: args[2],
        extraProperties: args[3]
      }));
    } catch (_) {
      clearPendingBatch(batchRequestId);
      rejectDirectCall(errorCallback, "PROTOCOL_FAILED");
    }
    return true;
  }

  function interceptUgrSetupCall(call, args) {
    if (!ugrCompatibilityEnabled || window.location.origin !== ugrOrigin) {
      return false;
    }
    if (call === "UGR_SET_FORCE_WS_MODE") {
      return args.length === 1 && args[0] === true;
    }
    if (call === "UGR_CARGAR_APP_AFIRMA") {
      return args.length === 0;
    }
    return call === "UGR_SET_SERVLETS" && args.length === 2 &&
      args[0] === ugrStorageUrl && args[1] === ugrRetrieveUrl;
  }

  function receiveMelillaBatchResult(result) {
    postQaPortalDiagnostic("RESULT_RECEIVED", result.requestId);
    const pending = clearPendingBatch(result.requestId);
    if (!pending) {
      postQaPortalDiagnostic("RESULT_IGNORED", result.requestId);
      return;
    }
    if (result.status === "success" && typeof result.validationResponse === "string" &&
        result.validationResponse.length <= maxBatchResultChars) {
      if (pending.lugoXml === true || pending.caibXml === true) {
        if (!base64Pattern.test(result.validationResponse)) {
          rejectDirectCall(pending.errorCallback, "PROTOCOL_FAILED");
          return;
        }
        try { pending.successCallback(result.validationResponse); } catch (_) {}
        return;
      }
      let validationResponse;
      try {
        validationResponse = JSON.parse(result.validationResponse);
      } catch (_) {
        rejectDirectCall(pending.errorCallback, "PROTOCOL_FAILED");
        return;
      }
      postQaPortalDiagnostic("CALLBACK_STARTED", result.requestId);
      try {
        pending.successCallback(validationResponse);
        postQaPortalDiagnostic("CALLBACK_RETURNED", result.requestId);
      } catch (_) {
        postQaPortalDiagnostic("CALLBACK_THROWN", result.requestId);
        // The portal callback is terminal; no submission is attempted here.
      }
      return;
    }
    const errorCode = typeof result.errorCode === "string" &&
      safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED";
    rejectDirectCall(pending.errorCallback, errorCode);
  }

  function receiveMiniAppletResult(event) {
    if (!functionalSigningEnabled || !event || typeof event.data !== "string") {
      return;
    }
    let result;
    try {
      result = JSON.parse(event.data);
    } catch (_) {
      return;
    }
    if (!result || typeof result.requestId !== "string" ||
        !canonicalUuidPattern.test(result.requestId)) {
      return;
    }
    if (result.type === "MINIAPPLET_BATCH_RESULT") {
      receiveMelillaBatchResult(result);
      return;
    }
    if (result.type === "MINIAPPLET_SELECT_CERTIFICATE_RESULT") {
      const p = clearPending(result.requestId);
      if (!p || p.selection !== true) return;
      if (result.status === "success" && typeof result.certificate === "string" && base64Pattern.test(result.certificate)) {
        const certificateB64 = result.certificate;
        try { p.successCallback(certificateB64); } catch (_) {} return;
      }
      rejectDirectCall(p.errorCallback, typeof result.errorCode === "string" && safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED"); return;
    }
    if (result.type !== "MINIAPPLET_RESULT") {
      return;
    }
    postQaPortalDiagnostic("RESULT_RECEIVED", result.requestId);
    const pending = clearPending(result.requestId);
    if (!pending) {
      postQaPortalDiagnostic("RESULT_IGNORED", result.requestId);
      return;
    }
    if (result.status === "success" && typeof result.signature === "string" &&
        typeof result.certificate === "string" &&
        base64Pattern.test(result.signature) && base64Pattern.test(result.certificate)) {
      const signatureB64 = result.signature;
      const certificateB64 = result.certificate;
      postQaPortalDiagnostic("CALLBACK_STARTED", result.requestId);
      try {
        pending.successCallback(signatureB64, certificateB64);
        postQaPortalDiagnostic("CALLBACK_RETURNED", result.requestId);
      } catch (_) {
        postQaPortalDiagnostic("CALLBACK_THROWN", result.requestId);
        // Portal callback exceptions are terminal.
      }
      return;
    }
    const errorCode = typeof result.errorCode === "string" &&
      safeTokenPattern.test(result.errorCode) ? result.errorCode : "PROTOCOL_FAILED";
    const errorCallback = pending.errorCallback;
    rejectDirectCall(errorCallback, errorCode);
  }

  if (functionalSigningEnabled && bridge) {
    bridge.onmessage = receiveMiniAppletResult;
  }

  window.addEventListener("pagehide", () => {
    for (const [pendingRequestId, pending] of pendingCallbacks.entries()) {
      clearTimeout(pending.timeoutId);
      notifyNativeCancel(pendingRequestId, pending.selection === true ?
        "MINIAPPLET_SELECT_CERTIFICATE_CANCEL" : "MINIAPPLET_CANCEL");
    }
    for (const [pendingRequestId, pending] of pendingBatchCallbacks.entries()) {
      clearTimeout(pending.timeoutId);
      notifyNativeCancel(
        pendingRequestId,
        pending.lugoXml === true ? "LUGO_XML_BATCH_CANCEL" :
          (pending.caibXml === true ? "CAIB_XML_BATCH_CANCEL" : "MINIAPPLET_BATCH_CANCEL")
      );
    }
    pendingCallbacks.clear();
    pendingBatchCallbacks.clear();
    activeMelillaBatch = null;
  });

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
        if (interceptUgrSetupCall(call, args)) {
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
      installMethodHook(value, "cargarMiniApplet", "LOAD");
      installMethodHook(value, "sign", "SIGN");
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
    const rewrapCurrentMiniApplet = () => {
      // Some portal scripts replace the global with defineProperty after document-start.
      // Rewrap the object currently exposed by the page before its login button is used.
      miniApplet = wrapMiniApplet(window.MiniApplet, false, false, xSel, false, caibBatchCompatibilityEnabled);
    };
    window.addEventListener("DOMContentLoaded", rewrapCurrentMiniApplet, { once: true });
    window.addEventListener("load", rewrapCurrentMiniApplet, { once: true });
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
