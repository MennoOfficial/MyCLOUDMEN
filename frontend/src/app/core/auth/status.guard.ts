import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { map, tap } from 'rxjs/operators';

/**
 * Guard that checks company status and redirects to company-inactive page 
 * if the company status is DEACTIVATED or SUSPENDED
 */
@Injectable({
  providedIn: 'root'
})
export class StatusGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    // Log the route being checked
    console.log('StatusGuard checking access to route:', state.url);
    
    // Always allow access to auth routes, activation routes, or company-inactive page
    if (
      state.url.includes('/auth/') ||
      state.url.includes('/login') ||
      state.url.includes('/sign-up') ||
      state.url.includes('/callback') ||
      state.url.includes('/company-inactive') ||
      state.url.includes('/logout')
    ) {
      console.log('Access is allowed to auth/status route:', state.url);
      return true;
    }

    const isActivationRoute = route.routeConfig?.path?.includes('activation') || false;
    if (isActivationRoute) {
      console.log('Access is allowed to activation route');
      return true;
    }

    // If user is not authenticated yet, allow access (auth guard will handle this)
    if (!this.authService.isAuthenticated()) {
      console.log('Auth check deferred to auth guard');
      return true;
    }

    // Use the user observable from AuthService
    return this.authService.user$.pipe(
      tap(user => console.log('User in StatusGuard:', user ? `${user.email} (${user.status})` : 'No user')),
      map(user => {
        if (!user) {
          console.log('No user found, allowing access (auth guard will handle)');
          return true;
        }

        console.log('User found, checking company status for:', user.email);
        
        // Check company status
        const result = this.authService.checkAndHandleCompanyStatus();
        console.log('Company status check result:', result ? 'Access allowed' : 'Access denied');
        
        // Allow access by default - any redirects will happen in the checkAndHandleCompanyStatus method
        return result;
      })
    );
  }
}
