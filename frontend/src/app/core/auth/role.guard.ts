import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole } from '../models/auth.model';
import { map, take, filter } from 'rxjs/operators';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = route.data['requiredRoles'] as UserRole[];
  
  // Check if user has required roles
  if (!requiredRoles || requiredRoles.length === 0) {
    // No specific roles required for this route
    return true;
  }

  // Get current user
  const user = authService.getCurrentUser();
  
  if (!user) {
    // No authenticated user - redirect to login
    router.navigate(['/auth/loading']);
    return false;
  }

  // Check if user has no roles assigned
  if (!user.roles || user.roles.length === 0) {
    // User has no roles - redirect to pending account page
    router.navigate(['/pending-account'], { replaceUrl: true });
    return false;
  }

  // Check if user has any of the required roles
  const hasRequiredRole = requiredRoles.some(role => user.roles.includes(role as UserRole));
  
  if (!hasRequiredRole) {
    // User doesn't have required roles - redirect to appropriate page
    const redirectPath = authService.getRoleBasedRedirectPath(user.roles);
    router.navigate([redirectPath], { replaceUrl: true });
    return false;
  }

  return true;
};

function redirectBasedOnUserRole(roles: UserRole[], router: Router): void {
  // If user has no roles, redirect to pending account page
  if (!roles || roles.length === 0) {
    router.navigate(['/pending-account'], { replaceUrl: true });
    return;
  }
  
  if (roles.includes('SYSTEM_ADMIN')) {
    router.navigate(['/companies']);
  } else if (roles.includes('COMPANY_ADMIN')) {
    router.navigate(['/users']);
  } else {
    router.navigate(['/requests']);
  }
} 