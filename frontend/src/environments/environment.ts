export const environment = {
    production: false,
    apiUrl: 'http://localhost:8080/api',
    auth0: {
      domain: 'dev-cybjbck012c334m7.us.auth0.com',
      clientId: 'La7lbgEb4pLxjLNdeClnZ41cl57aD2bk',
      redirectUri: window.location.origin + '/auth/callback',
      audience: 'https://mycloudmen-api'
    }
  };