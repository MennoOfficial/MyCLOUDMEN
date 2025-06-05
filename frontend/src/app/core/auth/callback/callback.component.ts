import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { Router } from '@angular/router';
import { filter, first, switchMap, take, tap, timeout } from 'rxjs/operators';
import { of, timer } from 'rxjs';
import { User } from '../../models/auth.model';

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
        this.navigateBasedOnRoles(user);
      },
      error: () => {
        // Fallback on timeout or error
        this.router.navigate(['/purchase-requests']);
      }
    });
  }

  private navigateBasedOnRoles(user: User): void {
    // **IMPORTANT: Check if there's a target URL stored from before login**
    const targetUrl = sessionStorage.getItem('auth_target_url');
    
    if (targetUrl) {
      // Clear the stored URL
      sessionStorage.removeItem('auth_target_url');
      console.log(`[DEBUG] Redirecting to stored target URL: ${targetUrl}`);
      
      // Navigate to the stored URL
      this.router.navigateByUrl(targetUrl);
      return;
    }
    
    // Default navigation based on roles if no target URL
    console.log(`[DEBUG] No target URL found, navigating based on roles`);
    if (user.roles.includes('SYSTEM_ADMIN')) {
      this.router.navigate(['/companies']);
    } else if (user.roles.includes('COMPANY_ADMIN')) {
      this.router.navigate(['/users']);
    } else {
      this.router.navigate(['/requests']);
    }
  }
}
