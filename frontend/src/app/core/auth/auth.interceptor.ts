import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, from, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { catchError, mergeMap } from 'rxjs/operators';
import { EnvironmentService } from '../services/environment.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private authService: AuthService,
    private environmentService: EnvironmentService
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Include any URL that contains api and users
    const isApiUrl = req.url.includes('/api/') && 
                    (req.url.includes('/users/') || req.url.includes('/auth0/'));
    
    if (!isApiUrl) {
      return next.handle(req);
    }
    
    return from(this.authService.getAccessToken()).pipe(
      mergeMap(token => {
        
        // Always add these headers for API requests
        let headers = req.headers
          .set('Content-Type', 'application/json')
          .set('Accept', 'application/json')
          .set('Cache-Control', 'no-cache, no-store, must-revalidate')
          .set('Pragma', 'no-cache')
          .set('Expires', '0');
          
        // Add auth token if available
        if (token) {
          headers = headers.set('Authorization', `Bearer ${token}`);
        }
        
        // Clone the request with the new headers
        const cloned = req.clone({
          headers: headers
        });
        
        return next.handle(cloned).pipe(
          catchError((error: HttpErrorResponse) => {
            console.error(`Error in API request to ${req.url}:`, error);
            if (error.status === 401) {
              // Could try to refresh token here
            }
            return throwError(() => error);
          })
        );
      })
    );
  }
}