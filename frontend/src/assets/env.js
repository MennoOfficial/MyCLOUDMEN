(function (window) {
  const hostname = window.location.hostname;
  const isProduction =
    hostname.includes("mycloudmen") || hostname.includes("mennoplochaet.be");

  window.env = {
    production: isProduction,
    apiUrl: isProduction
      ? "https://mycloudmen.mennoplochaet.be/api"
      : "http://localhost:8080/api",
    auth0: {
      domain: "dev-cybjbck012c334m7.us.auth0.com",
      clientId: "La7lbgEb4pLxjLNdeClnZ41cl57aD2bk",
      audience: "https://mycloudmen-api",
      redirectUri: window.location.origin + "/auth/callback",
    },
  };
})(this);
