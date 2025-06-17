import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { map, switchMap, take, timeout, catchError, delay, retry } from 'rxjs/operators';
import { Observable, of, timer } from 'rxjs';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  // Skip auth check for auth-related routes
  if (
    state.url.includes('/auth/') ||
    state.url.includes('/login') ||
    state.url.includes('/callback') ||
    state.url.includes('/error')
  ) {
    return true;
  }
  
  // Check if authentication is currently in progress
  return authService.authLoading$.pipe(
    take(1),
    switchMap(isAuthLoading => {
      if (isAuthLoading) {
        // Authentication is in progress, redirect to loading page and let it handle the flow
        router.navigate(['/auth/loading'], { replaceUrl: true });
        return of(false);
      }
      
      // Not currently loading, proceed with normal auth checks
      return authService.isAuthenticated().pipe(
        timeout(5000),
        catchError(error => {
          // If authentication check fails, redirect to login
          sessionStorage.setItem('auth_target_url', state.url);
          authService.login();
          return of(false);
        }),
        switchMap(isAuthenticated => {
          if (!isAuthenticated) {
            // Not authenticated, redirect to login
            sessionStorage.setItem('auth_target_url', state.url);
            authService.login();
            return of(false);
          }
          
          // Check if we have a user profile first (might be available from session)
          const currentUser = authService.getCurrentUser();
          if (currentUser && currentUser.roles && currentUser.roles.length > 0) {
            // We already have a valid user, allow access
            return of(true);
          }
          
          // If authenticated but no user profile, try to get a token (with retry for timing issues)
          return authService.getAccessToken().pipe(
            retry(2), // Retry twice for timing issues during auth process
            timeout(8000), // Longer timeout to account for retries
            catchError(error => {
              // Token fetch failed after retries, redirect to login
              sessionStorage.setItem('auth_target_url', state.url);
              authService.login();
              return of(false);
            }),
            switchMap(token => {
              if (!token) {
                // No token available, redirect to login
                sessionStorage.setItem('auth_target_url', state.url);
                authService.login();
                return of(false);
              }
              
              // If we have a token, wait for user profile (with patience for auth process)
              return authService.user$.pipe(
                timeout(10000), // Longer timeout during auth process
                take(1),
                catchError(error => {
                  // User profile failed, redirect to loading page to handle properly
                  router.navigate(['/auth/loading'], { replaceUrl: true });
                  return of(false);
                }),
                map(user => {
                  // Only proceed if we have a valid user object with roles
                  if (user && typeof user === 'object' && user.roles && user.roles.length > 0) {
                    return true;
                  }
                  
                  // No valid user profile yet, redirect to loading page
                  router.navigate(['/auth/loading'], { replaceUrl: true });
                  return false;
                })
              );
            })
          );
        })
      );
    })
  );
};