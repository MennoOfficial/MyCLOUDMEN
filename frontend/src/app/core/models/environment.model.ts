/**
 * Auth0 configuration settings
 */
export interface Auth0Config {
  domain: string;
  clientId: string;
  audience: string;
  redirectUri: string;
}

/**
 * Application environment configuration
 */
export interface Environment {
  production: boolean;
  apiUrl: string;
  auth0: Auth0Config;
} 