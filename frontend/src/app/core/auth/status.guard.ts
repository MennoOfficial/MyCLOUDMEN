import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Guard that checks user and company status and redirects to appropriate pages
 * based on status (pending, deactivated, company inactive, etc.)
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
    // Always allow access to auth routes, status pages, or authentication flows
    if (
      state.url.includes('/auth/') ||
      state.url.includes('/login') ||
      state.url.includes('/sign-up') ||
      state.url.includes('/callback') ||
      state.url.includes('/company-inactive') ||
      state.url.includes('/company-not-registered') ||
      state.url.includes('/pending-account') ||
      state.url.includes('/account-deactivated') ||
      state.url.includes('/logout') ||
      state.url.includes('/error')
    ) {
      return true;
    }

    // Always allow activation routes
    const isActivationRoute = route.routeConfig?.path?.includes('activation') || false;
    if (isActivationRoute) {
      return true;
    }

    // Use the canNavigateToUrl method which handles all status checks
    return this.authService.canNavigateToUrl(state.url);
  }
}
