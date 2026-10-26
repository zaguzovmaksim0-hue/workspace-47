
  matchesAccedaSign = (args, successCallback, errorCallback) => {
    const accedaOrigin = "https://sede.administracionespublicas.gob.es";
    const accedaExtraProperties = "format=PAdES Detached\nexpPolicy=FirmaAGE\nnonexpired:true";
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
    return isAccedaOrigin ? isExactAccedaCall : null;
  };
