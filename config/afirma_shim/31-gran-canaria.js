
  matchesGranCanariaSign = (args, successCallback, errorCallback) => {
    const granCanariaOrigin = "https://sede.grancanaria.com";
    const granCanariaExtraProperties = "headless=true\nfilters=nonexpired:";
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
    return isGranCanariaOrigin ? isExactGranCanariaCall : null;
  };
