import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole } from '../models/auth.model';
import { map, take, filter } from 'rxjs/operators';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = route.data['requiredRoles'] as UserRole[];
  
  if (!requiredRoles || requiredRoles.length === 0) {
    console.error('[RoleGuard] No required roles specified for route:', state.url);
    return false;
  }
  
  return authService.user$.pipe(
    // Don't proceed until we have user data or definitely know there is none
    filter(user => user !== undefined),
    take(1),
    map(user => {
      if (!user) {
        console.log('[RoleGuard] No authenticated user found, redirecting to login');
        authService.login();
        return false;
      }
      
      const hasRequiredRole = requiredRoles.some(role => 
        user.roles.includes(role)
      );
      
      if (!hasRequiredRole) {
        console.log(`[RoleGuard] User doesn't have required roles (${requiredRoles.join(', ')}), redirecting`);
        redirectBasedOnUserRole(user.roles, router);
        return false;
      }
      
      return true;
    })
  );
};

function redirectBasedOnUserRole(roles: UserRole[], router: Router): void {
  if (roles.includes('SYSTEM_ADMIN')) {
    router.navigate(['/system-admin/companies']);
  } else if (roles.includes('COMPANY_ADMIN')) {
    router.navigate(['/company-admin/users']);
  } else {
    router.navigate(['/company-user/requests']);
  }
} 