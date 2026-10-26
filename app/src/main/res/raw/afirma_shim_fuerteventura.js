
  matchesFuerteventuraSign = (args, successCallback, errorCallback) => {
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
    const isFuerteventuraOrigin =
      fuerteventuraCompatibilityEnabled && window.location.origin === fuerteventuraOrigin;
    const isFuerteventuraSigningPage =
      isFuerteventuraOrigin && window.location.href === fuerteventuraSigningPage &&
      window.location.hash === "";
    const isExactFuerteventuraCall =
      isFuerteventuraSigningPage &&
      args.length === 6 &&
      typeof args[0] === "string" &&
      args[0].length > 0 &&
      args[0].length <= maxDirectDataChars &&
      base64Pattern.test(args[0]) &&
      args[1] === "SHA256withRSA" &&
      args[2] === "PAdES" &&
      args[3] === fuerteventuraExtraProperties &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    return isFuerteventuraOrigin ? isExactFuerteventuraCall : null;
  };
