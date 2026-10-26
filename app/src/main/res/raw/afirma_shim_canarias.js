
  matchesCanariasSign = (args, successCallback, errorCallback) => {
    const canariasOrigin = "https://sede.gobiernodecanarias.org";
    const canariasPage = "https://sede.gobiernodecanarias.org/sede/identificacion";
    const canariasChallengePattern =
    /^(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT$/;
    const canariasExtraProperties =
      "format=CAdES Detached\n" +
      "serverUrl=https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService\n" +
      "referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\n" +
      "filters=nonexpired:true;signingCert:true;issuer.rfc2254:(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))(!(O=PKI))(!(O=DO_NOT_TRUST*)))";

    function isExactCanariasChallenge(value) {
      if (typeof value !== "string" || !base64Pattern.test(value)) {
        return false;
      }
      try {
        const decoded = globalThis.atob(value);
        return canariasChallengePattern.test(decoded) && globalThis.btoa(decoded) === value;
      } catch (_) {
        return false;
      }
    }

    const isCanariasOrigin =
      canariasCompatibilityEnabled && window.location.origin === canariasOrigin;
    const isCanariasPage =
      isCanariasOrigin && window.location.href === canariasPage &&
      window.location.search === "" && window.location.hash === "";
    const isExactCanariasCall =
      isCanariasPage && args.length === 6 && isExactCanariasChallenge(args[0]) &&
      args[1] === "SHA1withRSA" && args[2] === "CAdES" &&
      args[3] === canariasExtraProperties &&
      typeof successCallback === "function" && typeof errorCallback === "function";
    return isCanariasOrigin ? isExactCanariasCall : null;
  };
