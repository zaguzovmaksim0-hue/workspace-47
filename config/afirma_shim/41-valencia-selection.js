
  matchesValenciaSelection = a => {
    const vPage = "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml";
    const vProps = "filters=keyusage.nonrepudiation:true;nonexpired:true\nheadless=true";
    return vSel && location.href === vPage && a.length === 3 && a[0] === vProps &&
        typeof a[1] === "function" && typeof a[2] === "function";
  };
