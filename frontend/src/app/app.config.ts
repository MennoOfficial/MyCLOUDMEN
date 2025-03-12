import { APP_INITIALIZER, ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './core/auth/auth.service';
import { routes } from './app.routes';

// Factory function to initialize auth
export function initializeAuth(authService: AuthService) {
  return () => {
    // The auth service constructor will handle loading the user
    // This function just ensures the auth service is instantiated
    return new Promise<void>((resolve) => {
      // Wait a bit to ensure Auth0 has time to initialize
      setTimeout(() => resolve(), 100);
    });
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService],
      multi: true
    }
  ]
};
