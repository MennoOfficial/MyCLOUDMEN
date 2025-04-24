import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { map, switchMap, take, tap } from 'rxjs/operators';
import { Observable, of } from 'rxjs';

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
  
  return authService.isAuthenticated().pipe(
    switchMap(isAuthenticated => {
      if (!isAuthenticated) {
        // Store the intended URL and redirect to login
        sessionStorage.setItem('auth_target_url', state.url);
        authService.login();
        return of(false);
      }
      
      // Wait for the user profile to be loaded
      return authService.user$.pipe(
        take(1),
        map(user => {
          // Only proceed if we have a valid user object with roles
          if (user && user.roles && user.roles.length > 0) {
            return true;
          }
          
          // If we're authenticated but don't have a user profile yet,
          // try refreshing the user profile and return false to prevent navigation
          if (!user) {
            authService.refreshUserProfile();
            // Redirect to loading page
            router.navigate(['/auth/loading'], { replaceUrl: true });
            return false;
          }
          
          return true;
        })
      );
    })
  );
};