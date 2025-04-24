import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, from, throwError, timer } from 'rxjs';
import { AuthService } from './auth.service';
import { catchError, mergeMap, retryWhen, take, tap, concatMap, finalize, switchMap } from 'rxjs/operators';
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
            console.log(`[Auth Interceptor] Request to ${req.url} completed`);
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
    console.log(`[Auth Interceptor] Processing request: ${req.url}`);
    
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
                console.log(`[Auth Interceptor] Retry attempt ${retryAttempt} for ${req.url}`);
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
    console.error(`[Auth Interceptor] Error in API request to ${req.url}:`, error);
    
    if (error.status === 401) {
      console.log('[Auth Interceptor] 401 Unauthorized error - handling authentication');
      return this.handle401Error(req, next);
    }
    
    return throwError(() => error);
  }
  
  /**
   * Handle 401 Unauthorized errors by refreshing token or redirecting to login
   */
  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      
      console.log('[Auth Interceptor] Handling 401 error, refreshing authentication');
      
      // Try refreshing the user profile
      this.authService.refreshUserProfile();
      
      // Wait a bit, then check if authentication is still valid
      return timer(2000).pipe(
        switchMap(() => {
          this.isRefreshing = false;
          
          // Check if we have a user after refresh
          if (!this.authService.getCurrentUser()) {
            console.log('[Auth Interceptor] Still unauthorized after refresh, redirecting to login');
            this.authService.login();
            return throwError(() => new Error('Authentication failed'));
          }
          
          // Retry the request with a fresh token
          console.log('[Auth Interceptor] Successfully refreshed, retrying request');
          return this.authService.getAccessToken().pipe(
            mergeMap(token => {
              const authReq = this.addHeadersToRequest(request, token);
              return next.handle(authReq);
            })
          );
        })
      );
    } else {
      // If already refreshing, wait for that to complete
      console.log('[Auth Interceptor] Already refreshing, redirecting to login');
      this.authService.login();
      return throwError(() => new Error('Authentication refresh in progress'));
    }
  }
}