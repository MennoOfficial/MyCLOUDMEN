import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  
  constructor(private authService: AuthService, private router: Router) {}
  
  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean {
    // For development, always return true to allow access to all routes
    return true;
    
    // When you're ready to implement authentication:
    /*
    if (this.authService.isAuthenticated()) {
      return true;
    }
    
    // Redirect to Auth0 login
    this.authService.login(state.url);
    return false;
    */
  }
} 