import { Injectable } from '@angular/core';

interface Auth0Config {
  domain: string;
  clientId: string;
  audience: string;
  redirectUri: string;
}

interface Environment {
  production: boolean;
  apiUrl: string;
  auth0: Auth0Config;
}

@Injectable({
  providedIn: 'root'
})
export class EnvironmentService {
  private environment: Environment;

  constructor() {
    console.log('EnvironmentService initializing...');
    const windowEnv = (window as any).env;
    console.log('Window env available:', windowEnv ? 'Yes' : 'No');
    
    if (!windowEnv) {
      throw new Error('Environment configuration not found. Make sure env.js is loaded.');
    }

    const hostname = window.location.hostname;
    console.log('Current hostname:', hostname);
    console.log('Environment from window.env:', {
      production: windowEnv.production,
      apiUrl: windowEnv.apiUrl
    });

    // Use window.env values directly
    this.environment = {
      production: windowEnv.production,
      apiUrl: windowEnv.apiUrl,
      auth0: {
        domain: windowEnv.auth0.domain,
        clientId: windowEnv.auth0.clientId,
        audience: windowEnv.auth0.audience,
        redirectUri: window.location.origin + '/auth/callback'
      }
    };
    
    // Double check that we're using the correct API URL
    if (this.environment.production && !this.environment.apiUrl.includes('mycloudmen.mennoplochaet.be')) {
      console.warn('Production environment detected but using non-production API URL. Correcting...');
      this.environment.apiUrl = 'https://mycloudmen.mennoplochaet.be/api';
    }
    
    console.log('Final environment configuration:', {
      production: this.environment.production,
      apiUrl: this.environment.apiUrl,
      auth0Domain: this.environment.auth0.domain,
      currentOrigin: window.location.origin
    });
  }

  get isProduction(): boolean {
    return this.environment.production;
  }

  get apiUrl(): string {
    return this.environment.apiUrl;
  }

  get auth0Domain(): string {
    return this.environment.auth0.domain;
  }

  get auth0ClientId(): string {
    return this.environment.auth0.clientId;
  }

  get auth0Audience(): string {
    return this.environment.auth0.audience;
  }

  get auth0Config(): Auth0Config {
    return this.environment.auth0;
  }

  get allowedList(): Array<{uri: string, tokenOptions: {authorizationParams: {audience: string}}}> {
    return [
      {
        uri: `${this.environment.apiUrl}/*`,
        tokenOptions: {
          authorizationParams: {
            audience: this.environment.auth0.audience
          }
        }
      }
    ];
  }
} 