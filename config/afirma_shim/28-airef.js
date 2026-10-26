
  matchesAirefSign = (args, successCallback, errorCallback) => {
    const airefOrigin = "https://sede.airef.es";
    const airefSigningPath = "/invesiteRE/action/solicitud/view";
    const airefSigningQueryPattern = /^\?id=[0-9]{1,20}$/;

    function isValidAirefPayload(value) {
      if (typeof value !== "string" || value.length !== 44 || !base64Pattern.test(value)) {
        return false;
      }
      try {
        const decoded = atob(value);
        return decoded.length === 32 && btoa(decoded) === value;
      } catch (_) {
        return false;
      }
    }

    function isExactAirefSigningPage() {
      return window.location.origin === airefOrigin &&
        window.location.pathname === airefSigningPath &&
        airefSigningQueryPattern.test(window.location.search) &&
        window.location.hash === "";
    }

    const isAirefOrigin =
      airefCompatibilityEnabled && window.location.origin === airefOrigin;
    const isExactAirefCall =
      isAirefOrigin && isExactAirefSigningPage() &&
      args.length === 6 &&
      isValidAirefPayload(args[0]) &&
      args[1] === "SHA1withRSA" &&
      args[2] === "XAdES" &&
      args[3] === null &&
      typeof successCallback === "function" &&
      typeof errorCallback === "function";
    return isAirefOrigin ? isExactAirefCall : null;
  };
