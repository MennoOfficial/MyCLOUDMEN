import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { Router } from '@angular/router';
import { filter, first, switchMap, take, tap, timeout } from 'rxjs/operators';
import { of, timer } from 'rxjs';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  template: '<div class="auth-callback"><app-loading-spinner text="Completing authentication..."></app-loading-spinner></div>',
  styles: ['.auth-callback { display: flex; justify-content: center; align-items: center; height: 100vh; }']
})
export class CallbackComponent implements OnInit {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    // Process the authentication callback with improved profile loading
    this.processAuthCallback();
  }

  private processAuthCallback(): void {
    // First handle the callback
    this.authService.handleAuthCallback();
    
    // Wait for user profile to be loaded before proceeding
    // This helps prevent the double login issue
    this.authService.user$.pipe(
      // Delay a bit to ensure auth0 processes are complete
      switchMap(user => {
        if (user) {
          return of(user);
        }
        
        // Give some time for the user profile to load
        return timer(1000).pipe(
          switchMap(() => this.authService.user$.pipe(take(1)))
        );
      }),
      // Only proceed when we have a user
      filter(user => !!user),
      // Limit to first value
      first(),
      // Add timeout to prevent indefinite hanging
      timeout(10000),
      // Make sure we trigger a refresh if needed
      tap(user => {
        if (!user || !user.roles || user.roles.length === 0) {
          this.authService.refreshUserProfile();
        }
      })
    ).subscribe({
      next: (user) => {
        // Navigate based on roles once we have a proper user
        if (user && user.roles) {
          this.navigateBasedOnRoles(user.roles);
        } else {
          // Fallback to default page
          this.router.navigate(['/company-user/requests']);
        }
      },
      error: () => {
        // Fallback on timeout or error
        this.router.navigate(['/company-user/requests']);
      }
    });
  }

  private navigateBasedOnRoles(roles: string[]): void {
    if (roles.includes('SYSTEM_ADMIN')) {
      this.router.navigate(['/system-admin/companies']);
    } else if (roles.includes('COMPANY_ADMIN')) {
      this.router.navigate(['/company-admin/users']);
    } else {
      this.router.navigate(['/company-user/requests']);
    }
  }
}
