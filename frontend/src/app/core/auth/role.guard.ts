import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, UserRole } from './auth.service';
import { map } from 'rxjs/operators';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = route.data['requiredRoles'] as UserRole[];
  
  return authService.user$.pipe(
    map(user => {
      if (!user) {
        authService.login();
        return false;
      }
      
      const hasRequiredRole = requiredRoles.some(role => 
        user.roles.includes(role)
      );
      
      if (!hasRequiredRole) {
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