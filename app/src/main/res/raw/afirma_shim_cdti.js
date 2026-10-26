
  matchesCdtiSign = (args, successCallback, errorCallback) => {
    const cdtiOrigin = "https://sede.cdti.gob.es";
    const cdtiPage =
      "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx";
    const cdtiChallengePattern = /^CertExp[0-9a-f]{32}[0-9a-z]{24}$/;
    const cdtiExtraProperties = "filters=nonexpired";
    const isCdtiOrigin =
      cdtiCompatibilityEnabled && window.location.origin === cdtiOrigin;
    const isCdtiPage =
      isCdtiOrigin && window.location.href === cdtiPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCdtiCall =
      isCdtiPage && args.length === 6 &&
      typeof args[0] === "string" && cdtiChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "XAdES Enveloping" &&
      args[3] === cdtiExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    return isCdtiOrigin ? isExactCdtiCall : null;
  };
