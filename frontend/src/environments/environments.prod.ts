export const environment = {
    production: true,
    apiUrl: 'https://your-production-api.com/api',
    auth0: {
      domain: 'dev-cybjbck012c334m7.us.auth0.com',
      clientId: 'La7lbgEb4pLxjLNdeClnZ41cl57aD2bk',
      authorizationParams: {
        redirect_uri: window.location.origin + '/auth/callback',
        audience: 'https://mycloudmen-api',
      },
      httpInterceptor: {
        allowedList: [
          {
            uri: 'https://your-production-api.com/api/*',
            tokenOptions: {
              audience: 'https://mycloudmen-api'
            }
          }
        ]
      }
    }
  };