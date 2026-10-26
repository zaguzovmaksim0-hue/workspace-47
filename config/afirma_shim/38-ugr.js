
  {
    const ugrOrigin = "https://sede.ugr.es";
    const ugrLiteral = "Universidad de Granada";
    const ugrLiteralBase64 = "VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==";
    const ugrStorageUrl = "https://sede.ugr.es/afirma-signature-storage/StorageService";
    const ugrRetrieveUrl = "https://sede.ugr.es/afirma-signature-retriever/RetrieveService";

    interceptUgrSetupCall = (call, args) => {
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
    };

    installUgrMethodHooks = (value, includeUgrSetup) => {
      if (includeUgrSetup) {
        installMethodHook(value, "setForceWSMode", "UGR_SET_FORCE_WS_MODE");
        installMethodHook(value, "cargarAppAfirma", "UGR_CARGAR_APP_AFIRMA");
        installMethodHook(value, "setServlets", "UGR_SET_SERVLETS");
      }
    };

    encodeUgrLiteral = () => ugrLiteralBase64;
    matchesUgrSign = args => {
      return ugrCompatibilityEnabled &&
      window.location.origin === ugrOrigin &&
      args[0] === ugrLiteral &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      args[3] === "";
    };
  }
