
  matchesIsciiiSelection = a => {
    const iPage = "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null";
    const iProps = "serverUrl=http://dtomcat7.isciiides.es:8080/afirma-server-triphase-signer/SignatureService";
    return iSel && location.href === iPage && a.length === 3 && a[0] === iProps &&
        typeof a[1] === "function" && typeof a[2] === "function";
  };
