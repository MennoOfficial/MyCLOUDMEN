import { Injectable } from '@angular/core';
import { Auth0Config, Environment } from '../models/environment.model';

@Injectable({
  providedIn: 'root'
})
export class EnvironmentService {
  private environment: Environment;

  constructor() {
    const windowEnv = (window as any).env;
    
    if (!windowEnv) {
      throw new Error('Environment configuration not found. Make sure env.js is loaded.');
    }

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
      this.environment.apiUrl = 'https://mycloudmen.mennoplochaet.be/api';
    }
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