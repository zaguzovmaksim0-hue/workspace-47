
  {
    const euskadiProfileId = "euskadi-sede-electronica";
    const euskadiAuthPage =
      "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication";
    const euskadiClientAuthTarget =
      "https://eidas2.izenpe.com/cert-authn-external-validation/authenticate";
    const euskadiFormContentType = "application/x-www-form-urlencoded";

    let euskadiClientAuthPostSent = false;

    function isSafeEuskadiOpaqueRequest(value) {
      if (typeof value !== "string" || value.length < 1 || value.length > 4096) {
        return false;
      }
      for (let index = 0; index < value.length; index += 1) {
        const code = value.charCodeAt(index);
        if (code < 0x21 || code > 0x7e) return false;
      }
      return true;
    }

    function interceptEuskadiClientAuthPost(form) {
      if (!euskadiClientAuthPostEnabled || window.top !== window ||
          location.href !== euskadiAuthPage) {
        return false;
      }
      if (!(form instanceof HTMLFormElement)) return false;

      let action;
      try {
        action = new URL(form.action, location.href).href;
      } catch (_) {
        return true;
      }
      // Unrelated forms on the reviewed page remain untouched. Only the exact client-auth target is mediated.
      if (action !== euskadiClientAuthTarget) return false;
      if (form.method.toUpperCase() !== "POST" ||
          form.enctype.toLowerCase() !== euskadiFormContentType) {
        return true;
      }

      const namedControls = Array.from(form.elements).filter(control => control && control.name);
      if (namedControls.length !== 2 || namedControls.some(control => control.type !== "hidden")) {
        return true;
      }
      const names = namedControls.map(control => control.name).sort();
      if (names[0] !== "request" || names[1] !== "x_correlation_id") return true;

      const requestControl = form.elements.namedItem("request");
      const correlationControl = form.elements.namedItem("x_correlation_id");
      const requestValue = requestControl && requestControl.value;
      const correlationValue = correlationControl && correlationControl.value;
      if (!isSafeEuskadiOpaqueRequest(requestValue) ||
          typeof correlationValue !== "string" ||
          !canonicalUuidPattern.test(correlationValue) ||
          euskadiClientAuthPostSent || !bridge || typeof bridge.postMessage !== "function") {
        return true;
      }

      const requestId = secureRequestId();
      if (!requestId || !canonicalUuidPattern.test(requestId)) return true;
      euskadiClientAuthPostSent = true;
      try {
        bridge.postMessage(JSON.stringify({
          type: "EUSKADI_CLIENT_AUTH_POST",
          profileId: euskadiProfileId,
          requestId,
          method: "POST",
          contentType: euskadiFormContentType,
          targetUrl: euskadiClientAuthTarget,
          request: requestValue,
          x_correlation_id: correlationValue
        }));
      } catch (_) {
        euskadiClientAuthPostSent = false;
      }
      // The normal WebView never sends this POST; native replays the exact form body only in the isolated TLS WebView.
      return true;
    }

    if (euskadiClientAuthPostEnabled && typeof HTMLFormElement === "function") {
      const nativeSubmit = HTMLFormElement.prototype.submit;
      const nativeRequestSubmit = HTMLFormElement.prototype.requestSubmit;
      try {
        HTMLFormElement.prototype.submit = function() {
          if (interceptEuskadiClientAuthPost(this)) return undefined;
          return Reflect.apply(nativeSubmit, this, []);
        };
        if (typeof nativeRequestSubmit === "function") {
          HTMLFormElement.prototype.requestSubmit = function(submitter) {
            if (interceptEuskadiClientAuthPost(this)) return undefined;
            const args = submitter === undefined ? [] : [submitter];
            return Reflect.apply(nativeRequestSubmit, this, args);
          };
        }
        document.addEventListener("submit", event => {
          if (interceptEuskadiClientAuthPost(event.target)) {
            event.preventDefault();
            event.stopImmediatePropagation();
          }
        }, true);
      } catch (_) {
        // If the exact hook cannot be installed, do not add any broader fallback interception.
      }
    }
  }
