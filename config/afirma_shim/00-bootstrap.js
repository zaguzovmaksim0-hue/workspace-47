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
  // null: not applicable; false: reject; true: exact tuple.
  let matchesBadajozSign = () => null;
  let getBadajozExtraProperties = () => null;
  let notifyBadajozSignHook = () => {};
  let repairBadajozDiagnostics = () => {};
  let installBadajozLateHooks = () => {};
