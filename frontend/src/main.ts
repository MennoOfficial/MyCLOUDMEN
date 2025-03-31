import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { provideRouter } from '@angular/router';
import { routes } from './app/app-routing.module';
import { provideHttpClient, withInterceptors, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideAuth0, authHttpInterceptorFn } from '@auth0/auth0-angular';
import { AuthInterceptor } from './app/core/auth/auth.interceptor';
import { APP_INITIALIZER } from '@angular/core';
import { EnvironmentService } from './app/core/services/environment.service';

function initializeApp(environmentService: EnvironmentService) {
  return () => {
    // Force environment service initialization
    console.log('Initializing app with API URL:', environmentService.apiUrl);
    console.log('Initializing app with Auth0 domain:', environmentService.auth0Domain);
    console.log('Initializing app with Auth0 client ID:', environmentService.auth0ClientId);
    console.log('Initializing app with Auth0 audience:', environmentService.auth0Audience);
    return Promise.resolve();
  };
}

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authHttpInterceptorFn])),
    EnvironmentService,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [EnvironmentService],
      multi: true
    },
    provideAuth0({
      domain: (window as any).env.auth0.domain,
      clientId: (window as any).env.auth0.clientId,
      authorizationParams: {
        redirect_uri: window.location.origin + '/auth/callback',
        audience: (window as any).env.auth0.audience
      }
    })
  ]
}).catch(err => console.error(err));