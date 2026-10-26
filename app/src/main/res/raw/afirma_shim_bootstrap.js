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
  let badajozSignHookReadyDiagnosticPosted = false;
  const badajozDiagnosticWrappedFunctions = new WeakSet();
  const badajozDiagnosticPostedStages = new Set();

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

  let matchesUgrSign = () => false;
  let interceptUgrSetupCall = () => false;
  let installUgrMethodHooks = () => {};
  let encodeUgrLiteral = () => null;
  let matchesCantabriaSign = () => false;
  let matchesJccmSign = () => false;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesJccmRegistroSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesSevillaAtseSign = () => null;
  let interceptSevillaAtseSetupCall = () => false;
  let installSevillaAtseClickHook = () => {};
  let installSevillaAtseMethodHooks = () => {};
  // null: not applicable; false: reject; true: exact tuple.
  let matchesAirefSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesCdtiSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesPoliciaSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesGranCanariaSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesCanariasSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesFuerteventuraSign = () => null;
  // null: not applicable; false: reject; true: exact tuple.
  let matchesMinecoSign = () => null;
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
  // null: not applicable; false: reject; true: exact tuple.
  let matchesAccedaSign = () => null;
  const badajozOrigin = "https://sede.dip-badajoz.es";
  const badajozExtraProperties =
    "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true";
  const euskadiProfileId = "euskadi-sede-electronica";
  const euskadiAuthPage =
    "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication";
  const euskadiClientAuthTarget =
    "https://eidas2.izenpe.com/cert-authn-external-validation/authenticate";
  const euskadiFormContentType = "application/x-www-form-urlencoded";
  // null: not applicable; false: reject; true: exact tuple.
  let matchesXuntaSign = () => null;
  let matchesXuntaSelection = () => false;
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
  const installedMethodGetters = new WeakSet();
  const pendingCallbacks = new Map();
  const pendingBatchCallbacks = new Map();
  let activeMelillaBatch = null;
  let activeProbeRequestId = null;
