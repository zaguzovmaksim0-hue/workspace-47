
  matchesPoliciaSign = (args, successCallback, errorCallback) => {
    const policiaOrigin = "https://sede.policia.gob.es";
    const policiaProcedurePage =
      "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml";
    const policiaExtraProperties =
      "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:";
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
    return isPoliciaOrigin ? isExactPoliciaCall : null;
  };
