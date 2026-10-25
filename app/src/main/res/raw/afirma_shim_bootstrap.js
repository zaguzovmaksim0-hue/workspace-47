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

  const ugrOrigin = "https://sede.ugr.es";
  let matchesCantabriaSign = () => false;
  const ugrLiteral = "Universidad de Granada";
  const ugrLiteralBase64 = "VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==";
  const ugrStorageUrl = "https://sede.ugr.es/afirma-signature-storage/StorageService";
  const ugrRetrieveUrl = "https://sede.ugr.es/afirma-signature-retriever/RetrieveService";
  let matchesJccmSign = () => false;
  const jccmRegistroOrigin = "https://registrounicociudadanos.jccm.es";
  const jccmRegistroProtectedPage =
    "https://registrounicociudadanos.jccm.es/registrounicociudadanos/accesoclvd.do";
  const jccmRegistroExtraProperties = "format=XAdES Detached\nmode=implicit";
  const sevillaAtseOrigin = "https://www.sevilla.org";
  const sevillaAtsePage = "/ovweb/ov-web-certificado/index.xhtml";
  const sevillaAtseStorageUrl = "https://www.sevilla.org/ovweb/sign/StorageService";
  const sevillaAtseRetrieveUrl = "https://www.sevilla.org/ovweb/sign/RetrieveService";
  const sevillaAtseChallengePattern = /^[A-Za-z0-9_-]{40}$/;
  let sevillaAtseSetupState = 0;
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
  const installedMethodGetters = new WeakSet();
  const pendingCallbacks = new Map();
  const pendingBatchCallbacks = new Map();
  let activeMelillaBatch = null;
  let activeProbeRequestId = null;
