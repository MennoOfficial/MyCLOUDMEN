import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, from, throwError, timer, switchMap, map, of, take } from 'rxjs';
import { AuthService } from './auth.service';
import { catchError, mergeMap, retryWhen, tap, concatMap, finalize } from 'rxjs/operators';
import { EnvironmentService } from '../services/environment.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private readonly RETRYABLE_STATUS = [408, 429, 500, 502, 503, 504];
  
  constructor(
    private authService: AuthService,
    private environmentService: EnvironmentService,
    private router: Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Skip interceptor for non-API calls
    if (!this.shouldIntercept(req)) {
      return next.handle(req);
    }
    
    return from(this.authService.getAccessToken()).pipe(
      mergeMap(token => {
        // Clone the request with standard headers and token if available
        const cloned = this.addHeadersToRequest(req, token);
        
        return next.handle(cloned).pipe(
          // Add retry logic for transient errors
          this.addRetryLogic(req),
          // Handle auth errors
          catchError((error: HttpErrorResponse) => this.handleRequestError(error, req, next)),
          // Log completion
          finalize(() => {
            // Request completed - log success if needed
          })
        );
      })
    );
  }
  
  /**
   * Determine if this request should be intercepted
   */
  private shouldIntercept(req: HttpRequest<any>): boolean {
    // Include api URLs
    return req.url.includes('/api/');
  }
  
  /**
   * Add standard headers and auth token to request
   */
  private addHeadersToRequest(req: HttpRequest<any>, token: string): HttpRequest<any> {
    // Add standard API headers
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
    return req.clone({ headers });
  }
  
  /**
   * Add retry logic for transient errors
   */
  private addRetryLogic(req: HttpRequest<any>) {
    return <T>(source: Observable<T>) => 
      source.pipe(
        retryWhen(errors => 
          errors.pipe(
            concatMap((error, i) => {
              const retryAttempt = i + 1;
              
              if (retryAttempt <= 2 && error instanceof HttpErrorResponse && 
                  this.RETRYABLE_STATUS.includes(error.status)) {
                return timer(retryAttempt * 1000); // Increasing delay: 1s, 2s
              }
              
              return throwError(() => error);
            })
          )
        )
      );
  }
  
  /**
   * Handle HTTP request errors
   */
  private handleRequestError(error: HttpErrorResponse, req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (error.status === 401) {
      return this.handle401Error(req, next);
    }
    
    return throwError(() => error);
  }
  
  /**
   * Handle 401 Unauthorized errors by refreshing token or redirecting to login
   */
  private handle401Error(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      
      // Try refreshing the user profile
      this.authService.refreshUserProfile();
      
      // Wait a bit, then check if authentication is still valid
      return timer(2000).pipe(
        switchMap(() => {
          this.isRefreshing = false;
          
          // Check if we have a user after refresh
          if (!this.authService.getCurrentUser()) {
            this.authService.login();
            return throwError(() => new Error('Authentication failed'));
          }
          
          // Retry the request with a fresh token
          return this.authService.getAccessToken().pipe(
            mergeMap(token => {
              const authReq = this.addHeadersToRequest(req, token);
              return next.handle(authReq);
            })
          );
        })
      );
    } else {
      // If already refreshing, wait for that to complete
      this.authService.login();
      return throwError(() => new Error('Authentication refresh in progress'));
    }
  }
}