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
      
      // Only block if we're sure the status is deactivated
      if (currentUser?.status && currentUser.status === 'DEACTIVATED') {
        console.log('User status from session is deactivated:', currentUser.status);
        router.navigate(['/auth/account-deactivated'], { 
          queryParams: { status: currentUser.status } 
        });
        return of(false);
      }
      
      // Otherwise verify with API but don't block if API check fails
      return checkUserStatus(http, environmentService, router);
    })
  );
};

function checkUserStatus(http: HttpClient, environmentService: EnvironmentService, router: Router): Observable<boolean> {
  const currentUser = JSON.parse(sessionStorage.getItem('user_profile') || '{}');
  
  if (!currentUser || !currentUser.email) {
    console.log('No user profile in session storage or missing email, allowing access');
    return of(true); // Allow access if no user profile (newly authenticated)
  }
  
  // First try the "email" endpoint - likely to be most reliable
  return http.get<UserDTO>(`${environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`).pipe(
    tap(user => console.log('Found user by email, status:', user.status)),
    map(user => {
      // Check if user has a non-active status that should be blocked
      if (!isActiveStatus(user.status)) {
        console.log(`User status is ${user.status}, redirecting to deactivated page`);
        router.navigate(['/auth/account-deactivated'], { 
          queryParams: { status: user.status } 
        });
        
        // Update session storage with the latest status
        currentUser.status = user.status;
        sessionStorage.setItem('user_profile', JSON.stringify(currentUser));
        
        return false;
      }
      return true;
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
          // Check if user has a non-active status that should be blocked
          if (!isActiveStatus(user.status)) {
            console.log(`User status is ${user.status}, redirecting to deactivated page`);
            router.navigate(['/auth/account-deactivated'], { 
              queryParams: { status: user.status } 
            });
            
            // Update session storage with the latest status
            currentUser.status = user.status;
            sessionStorage.setItem('user_profile', JSON.stringify(currentUser));
            
            return false;
          }
          return true;
        }),
        catchError(error2 => {
          console.error('Error fetching user by auth0Id, falling back to session storage:', error2);
          
          // Only block if we're sure the user is deactivated
          if (currentUser.status && !isActiveStatus(currentUser.status)) {
            console.log(`User status from session is ${currentUser.status}, redirecting to deactivated page`);
            router.navigate(['/auth/account-deactivated'], { 
              queryParams: { status: currentUser.status } 
            });
            return of(false);
          }
          
          // Allow access if API calls fail and we're not sure user is deactivated
          console.log('Could not verify user status definitively, allowing access');
          return of(true);
        })
      );
    })
  );
}

// Helper function to check if a status should allow access
function isActiveStatus(status: UserStatus): boolean {
  return status === 'ACTIVATED';
} 