
  {
    const sevillaAtseOrigin = "https://www.sevilla.org";
    const sevillaAtsePage = "/ovweb/ov-web-certificado/index.xhtml";
    const sevillaAtseStorageUrl = "https://www.sevilla.org/ovweb/sign/StorageService";
    const sevillaAtseRetrieveUrl = "https://www.sevilla.org/ovweb/sign/RetrieveService";
    const sevillaAtseChallengePattern = /^[A-Za-z0-9_-]{40}$/;
    let sevillaAtseSetupState = 0;

    function isValidSevillaAtseChallenge(value) {
      if (typeof value !== "string") {
        return false;
      }
      try {
        const decoded = atob(value);
        return decoded.length === 40 && sevillaAtseChallengePattern.test(decoded);
      } catch (_) {
        return false;
      }
    }

    function isExactSevillaAtsePage() {
      return sevillaAtseCompatibilityEnabled && location.origin === sevillaAtseOrigin &&
        location.pathname === sevillaAtsePage && location.search === "?modo=Contribuyente" &&
        location.hash === "";
    }

    interceptSevillaAtseSetupCall = (call, args) => {
      if (!isExactSevillaAtsePage()) return false;
      if (call === "SEVILLA_SET_FORCE_WS_MODE") {
        sevillaAtseSetupState = args.length === 1 && args[0] === true ? 1 : -1;
        return true;
      }
      if (call === "SEVILLA_SET_SERVLETS") {
        sevillaAtseSetupState = sevillaAtseSetupState === 1 && args.length === 2 &&
          args[0] === sevillaAtseStorageUrl && args[1] === sevillaAtseRetrieveUrl ? 2 : -1;
        return true;
      }
      if (call === "SEVILLA_CARGAR_APP_AFIRMA") {
        sevillaAtseSetupState = sevillaAtseSetupState === 2 && args.length === 0 ? 3 : -1;
        return true;
      }
      return false;
    };

    installSevillaAtseClickHook = () => {
      if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled) {
        document.addEventListener("click", event => {
          if (!isExactSevillaAtsePage()) return;
          const target = event.target?.closest?.("a");
          if (target?.getAttribute("href") === "#" &&
              target.getAttribute("onclick") === "doSign();" &&
              document.querySelectorAll('a[href="#"][onclick="doSign();"]').length === 1) {
            event.preventDefault();
          }
        }, true);
      }

    };

    installSevillaAtseMethodHooks = value => {
      if (sevillaAtseCompatibilityEnabled) {
        installMethodHook(value, "setForceWSMode", "SEVILLA_SET_FORCE_WS_MODE");
        installMethodHook(value, "setServlets", "SEVILLA_SET_SERVLETS");
        installMethodHook(value, "cargarAppAfirma", "SEVILLA_CARGAR_APP_AFIRMA");
      }
    };

    matchesSevillaAtseSign = (args, successCallback, errorCallback) => {
      const isSevillaAtseOrigin =
        sevillaAtseCompatibilityEnabled && window.location.origin === sevillaAtseOrigin;
      const isExactSevillaAtseCall =
        isSevillaAtseOrigin && isExactSevillaAtsePage() && sevillaAtseSetupState === 3 &&
        args.length === 6 &&
        isValidSevillaAtseChallenge(args[0]) &&
        args[1] === "SHA1withRSA" &&
        args[2] === "XAdES" &&
        args[3] == null &&
        typeof successCallback === "function" &&
        typeof errorCallback === "function";
      if (isSevillaAtseOrigin && !isExactSevillaAtseCall) sevillaAtseSetupState = 0;
      if (isExactSevillaAtseCall) sevillaAtseSetupState = 0;
      return isSevillaAtseOrigin ? isExactSevillaAtseCall : null;
    };
  }
