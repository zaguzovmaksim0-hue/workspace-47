
  matchesCantabriaSign = args => {
    const cantabriaOrigin = "https://rec.cantabria.es";
    const cantabriaChallengePattern = /^[0-9a-f]{40}$/;
    const cantabriaExtraProperties = "filters=\nmode=implicit";
    return cantabriaCompatibilityEnabled &&
      window.location.origin === cantabriaOrigin &&
      typeof args[0] === "string" &&
      cantabriaChallengePattern.test(args[0]) &&
      args[1] === "SHA512withRSA" &&
      args[2] === "CAdES" &&
      args[3] === cantabriaExtraProperties;
  };
