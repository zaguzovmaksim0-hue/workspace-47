
  const ugrOrigin = "https://sede.ugr.es";
  let matchesCantabriaSign = () => false;
  const ugrLiteral = "Universidad de Granada";
  const ugrLiteralBase64 = "VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==";
  const ugrStorageUrl = "https://sede.ugr.es/afirma-signature-storage/StorageService";
  const ugrRetrieveUrl = "https://sede.ugr.es/afirma-signature-retriever/RetrieveService";
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
