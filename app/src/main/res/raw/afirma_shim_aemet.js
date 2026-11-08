
  matchesAemetSign = (args, successCallback, errorCallback) => {
    const aemetOrigin = "https://sede.aemet.gob.es";
    const aemetPage = "https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso";
    const isAemetOrigin = aemetCompatibilityEnabled && window.location.origin === aemetOrigin;
    if (!isAemetOrigin) return null;
    if (window.top !== window || window.location.href !== aemetPage ||
        window.location.search !== "" || window.location.hash !== "" ||
        args.length !== 6 || args[1] !== "SHA1withRSA" || args[2] !== "AUTO" || args[3] !== null ||
        typeof successCallback !== "function" || typeof errorCallback !== "function" ||
        typeof args[0] !== "string" || args[0].length > 8 || !base64Pattern.test(args[0])) return false;
    try {
      const nonce = globalThis.atob(args[0]);
      return /^(?:0|[1-9][0-9]{0,3}|10000)$/.test(nonce) && globalThis.btoa(nonce) === args[0];
    } catch (_) {
      return false;
    }
  };
