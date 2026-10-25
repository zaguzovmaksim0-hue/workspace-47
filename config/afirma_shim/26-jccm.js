
  matchesJccmSign = args => {
    const jccmOrigin = "https://ventanillaelectronica.jccm.es";
    const jccmPayloadBase64 = "QUJDREU=";
    const isJccmOrigin =
      jccmCompatibilityEnabled && window.location.origin === jccmOrigin;
    const isExactJccmCall =
      isJccmOrigin &&
      args[0] === jccmPayloadBase64 &&
      args[1] === "SHA1withRSA" &&
      args[2] === "CAdES" &&
      (args[3] === null || args[3] === "");
    return isExactJccmCall;
  };
