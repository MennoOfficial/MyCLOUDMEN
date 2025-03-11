import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSubject = new BehaviorSubject<any>(null);
  public user$ = this.userSubject.asObservable();
  
  // Set the default role to SYSTEM_ADMIN for development
  private userRole: 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' = 'SYSTEM_ADMIN';

  constructor(private router: Router) {
    // Check if user is already authenticated on app initialization
    this.checkAuth();
  }
  
  // Check if the user is authenticated
  isAuthenticated(): boolean {
    // For development, always return true
    return true;
  }
  
  // Get the current user role
  getUserRole(): 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' {
    return this.userRole;
  }
  
  // Mock method to check authentication status
  private checkAuth(): void {
    // For development, create a mock user
    const mockUser = {
      id: '123',
      email: 'admin@example.com',
      name: 'System Admin',
      role: this.userRole
    };
    
    this.userSubject.next(mockUser);
  }
  
  // Mock login method
  login(redirectUrl: string): void {
    console.log('Redirecting to login page, will return to:', redirectUrl);
    // In a real app, this would redirect to Auth0
  }
  
  // Add the missing handleAuthCallback method
  handleAuthCallback(): void {
    // For development, just redirect to the home page
    console.log('Processing authentication callback');
    setTimeout(() => {
      this.router.navigate(['/']);
    }, 1000);
    
    // In a real app with Auth0, this would:
    // 1. Parse the URL hash for tokens
    // 2. Validate the tokens
    // 3. Store the tokens
    // 4. Redirect to the intended destination
  }
}
