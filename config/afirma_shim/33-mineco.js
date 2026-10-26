
  matchesMinecoSign = (args, successCallback, errorCallback) => {
    const minecoOrigin = "https://serviciosede.mineco.gob.es";
    const minecoSigningPage = "https://serviciosede.mineco.gob.es/FB/solicitud/firma.aspx";
    const minecoExtraProperties =
      "filters=signingCert:;nonexpired:\nexpPolicy=FirmaAGE\nsignatureSubFilter=ETSI.CAdES.detached";
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
    return isMinecoOrigin ? isExactMinecoCall : null;
  };
