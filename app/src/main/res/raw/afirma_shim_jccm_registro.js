
  matchesJccmRegistroSign = (args, successCallback, errorCallback) => {
    const jccmRegistroOrigin = "https://registrounicociudadanos.jccm.es";
    const jccmRegistroProtectedPage =
      "https://registrounicociudadanos.jccm.es/registrounicociudadanos/accesoclvd.do";
    const jccmRegistroExtraProperties = "format=XAdES Detached\nmode=implicit";
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
    return isJccmRegistroOrigin ? isExactJccmRegistroCall : null;
  };
