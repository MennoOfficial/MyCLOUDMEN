import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { EnvironmentService } from '../services/environment.service';
import { UserDTO } from '../models/user.dto';

// Define possible status values
type UserStatus = 'ACTIVATED' | 'DEACTIVATED' | 'PENDING' | 'REJECTED' | string;

export const statusGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const http = inject(HttpClient);
  const environmentService = inject(EnvironmentService);
  
  // Skip the guard for special pages themselves
  if (state.url.includes('/auth/account-deactivated') || 
      state.url.includes('/auth/pending-account')) {
    console.log('Status guard skipped for special auth page');
    return true;
  }
  
  return authService.isAuthenticated().pipe(
    tap(isAuthenticated => {
      if (!isAuthenticated) {
        authService.login();
      }
    }),
    switchMap(isAuthenticated => {
      if (!isAuthenticated) {
        return of(false);
      }
      
      // Check session storage for status
      const currentUser = JSON.parse(sessionStorage.getItem('user_profile') || '{}');
      console.log('Checking user status from session storage:', currentUser);
      
      // Check for deactivated users
      if (currentUser?.status === 'DEACTIVATED') {
        console.log('User status is deactivated, redirecting');
        router.navigate(['/account-deactivated'], { 
          queryParams: { status: currentUser.status }
        });
        return of(false);
      }
      
      // Check for pending users
      if (currentUser?.status === 'PENDING') {
        console.log('User status is pending, redirecting to pending-account');
        router.navigate(['/pending-account']);
        return of(false);
      }
      
      // Otherwise verify with API but don't block if API check fails
      return checkUserStatus(http, environmentService, router);
    })
  );
};

function checkUserStatus(http: HttpClient, environmentService: EnvironmentService, router: Router): Observable<boolean> {
  const currentUser = JSON.parse(sessionStorage.getItem('user_profile') || '{}');
  
  // If we already know user has a non-active status from session storage, redirect immediately
  if (currentUser?.status === 'PENDING') {
    console.log('User status from session is PENDING, redirecting immediately');
    router.navigate(['/pending-account']);
    return of(false);
  } else if (currentUser?.status === 'DEACTIVATED') {
    console.log('User status from session is DEACTIVATED, redirecting immediately');
    router.navigate(['/account-deactivated'], { 
      queryParams: { status: currentUser.status } 
    });
    return of(false);
  }
  
  // If no user profile in session storage, allow access (auth service will handle the redirect)
  if (!currentUser || !currentUser.email) {
    console.log('No user profile in session storage or missing email, allowing access');
    return of(true); // Allow access if no user profile (newly authenticated)
  }
  
  console.log('Checking user status via API');
  
  // First try the "email" endpoint - likely to be most reliable
  return http.get<UserDTO>(`${environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`).pipe(
    tap(user => console.log('Found user by email, status:', user.status)),
    map(user => {
      // Update session storage with the latest status regardless of what we do next
      currentUser.status = user.status;
      sessionStorage.setItem('user_profile', JSON.stringify(currentUser));

      // Check if user is deactivated
      if (user.status === 'DEACTIVATED') {
        console.log('User status is deactivated, redirecting');
        router.navigate(['/account-deactivated'], { 
          queryParams: { status: user.status } 
        });
        return false;
      }
      
      // Check for pending users
      if (user.status === 'PENDING') {
        console.log('User status is pending, redirecting to pending-account');
        router.navigate(['/pending-account']);
        return false;
      }
      
      return user.status === 'ACTIVATED';
    }),
    catchError(error => {
      console.error('Error fetching user by email, trying by auth0Id:', error);
      
      if (!currentUser.auth0Id) {
        console.log('No auth0Id in profile, allowing access');
        return of(true);
      }
      
      // Next try the auth0Id endpoint
      return http.get<UserDTO>(`${environmentService.apiUrl}/users/auth0/${encodeURIComponent(currentUser.auth0Id)}`).pipe(
        tap(user => console.log('Found user by auth0Id, status:', user.status)),
        map(user => {
          // Update session storage with the latest status
          currentUser.status = user.status;
          sessionStorage.setItem('user_profile', JSON.stringify(currentUser));
          
          // Check if user is deactivated
          if (user.status === 'DEACTIVATED') {
            console.log('User status is deactivated, redirecting');
            router.navigate(['/account-deactivated'], { 
              queryParams: { status: user.status } 
            });
            return false;
          }
          
          // Check if user is pending
          if (user.status === 'PENDING') {
            console.log('User status is pending, redirecting to pending-account');
            router.navigate(['/pending-account']);
            return false;
          }
          
          return user.status === 'ACTIVATED';
        }),
        catchError(auth0Error => {
          console.error('Error fetching user by auth0Id, using session or default status:', auth0Error);
          
          // If API calls fail, check session storage first, then default to PENDING 
          // for new users if we're unsure about status
          if (currentUser.status === 'DEACTIVATED') {
            console.log('User status from session is deactivated, redirecting');
            router.navigate(['/account-deactivated'], { 
              queryParams: { status: currentUser.status } 
            });
            return of(false);
          } else if (currentUser.status === 'PENDING' || !currentUser.status) {
            // Default to pending for new users or those with PENDING status
            console.log('User status is pending or unknown, redirecting to pending-account');
            
            // Ensure status is stored in session
            if (!currentUser.status) {
              currentUser.status = 'PENDING';
              sessionStorage.setItem('user_profile', JSON.stringify(currentUser));
            }
            
            router.navigate(['/pending-account']);
            return of(false);
          }
          
          // Only allow access if we have an explicitly ACTIVATED status
          console.log('Could not verify user status definitively, using stored status:', currentUser.status);
          return of(currentUser.status === 'ACTIVATED');
        })
      );
    })
  );
}

// Helper function to check if a status should allow access
function isActiveStatus(status: UserStatus): boolean {
  return status === 'ACTIVATED';
} 