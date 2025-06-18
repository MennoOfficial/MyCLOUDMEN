import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, RedirectResult } from '../auth.service';
import { User } from '../../models/auth.model';
import { filter, take, timeout, catchError, switchMap } from 'rxjs/operators';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { Subscription, of, timer } from 'rxjs';

@Component({
  selector: 'app-auth-loading',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  templateUrl: './loading.component.html',
  styleUrls: ['./loading.component.scss']
})
export class LoadingComponent implements OnInit, OnDestroy {
  loadingMessage = 'Authenticating...';
  loadingDuration = 0;
  maxWaitTime = 8000; // Reduced back to 8 seconds for faster fallback
  loadingState: string = 'authenticating';
  errorMessage: string = '';
  hasNavigated: boolean = false;
  
  private subscriptions: Subscription[] = [];
  private timer: any;
  
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    
    // Start a timer for UX purposes
    const startTime = Date.now();
    this.timer = setInterval(() => {
      this.loadingDuration = Date.now() - startTime;
      
      // Update message based on duration
      if (this.loadingDuration <= 2000) {
        this.loadingMessage = 'Authenticating...';
      } else if (this.loadingDuration <= 4000) {
        this.loadingMessage = 'Setting up your account...';
      } else if (this.loadingDuration <= 6000) {
        this.loadingMessage = 'Almost ready...';
      } else {
        this.loadingMessage = 'This is taking longer than expected...';
      }
      
      // If loading takes too long, handle the timeout
      if (this.loadingDuration >= this.maxWaitTime) {
        this.handleTimeout();
      }
    }, 1000);
    
    // Check current authentication state immediately
    this.checkAuthenticationState();
    
    // Handle auth errors
    const errorSub = this.authService.authError$.subscribe(error => {
      if (error) {
        this.loadingState = 'error';
        this.errorMessage = error;
        this.handleAuthenticationFailure();
      }
    });
    this.subscriptions.push(errorSub);
  }
  
  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
  
  private checkAuthenticationState(): void {
    // First, check if we already have a user
    const currentUser = this.authService.getCurrentUser();
    
    if (currentUser && currentUser.roles && currentUser.roles.length > 0) {
      this.handleSuccessfulAuth(currentUser);
      return;
    }
    
    // Check authentication state
    this.authService.isAuthenticated().pipe(take(1)).subscribe(isAuthenticated => {
      if (!isAuthenticated) {
        this.redirectToLogin('Not authenticated');
        return;
      }
      
      // Check if authentication is in progress
      this.authService.authLoading$.pipe(take(1)).subscribe(isLoading => {
        if (isLoading) {
          this.waitForAuthCompletion();
        } else {
          this.waitForUserProfile();
        }
      });
    });
  }
  
  private waitForAuthCompletion(): void {
    const authSub = this.authService.authLoading$.pipe(
      filter(isLoading => !isLoading), // Wait until loading is done
      take(1),
      timeout(this.maxWaitTime),
      catchError(() => {
        this.handleTimeout();
        return of(false);
      })
    ).subscribe(() => {
      this.waitForUserProfile();
    });
    
    this.subscriptions.push(authSub);
  }
  
  private waitForUserProfile(): void {
    const userSub = this.authService.user$.pipe(
      filter(user => {
        return user !== null && user !== undefined && user.roles && user.roles.length > 0;
      }),
      take(1),
      timeout(5000),
      catchError(error => {
        this.handleTimeout();
        return of(null);
      })
    ).subscribe(user => {
        if (user) {
        this.handleSuccessfulAuth(user);
      }
    });
    
    this.subscriptions.push(userSub);
  }
  
  private handleSuccessfulAuth(user: User): void {
    if (this.hasNavigated) {
      return;
    }
    
    this.loadingState = 'almost-ready';
    
    // Short delay before navigation to let user see the final state
    setTimeout(() => {
      this.navigateAfterAuth(user);
    }, 500);
  }
  
  private handleTimeout(): void {
    if (this.hasNavigated) {
      return;
    }
    
    if (this.timer) {
      clearInterval(this.timer);
    }
    
    // Check current state for debugging
    this.authService.isAuthenticated().pipe(take(1)).subscribe(isAuthenticated => {
      const currentUser = this.authService.getCurrentUser();
      
      if (!isAuthenticated) {
        this.redirectToLogin('Authentication timeout - not authenticated');
      } else if (!currentUser) {
        this.authService.refreshUserProfile();
        this.giveOneMoreChance();
      } else {
        this.handleSuccessfulAuth(currentUser);
      }
    });
  }
  
  private giveOneMoreChance(): void {
    // One final attempt to get the user profile
    this.authService.user$.pipe(
      filter(user => user !== null && user !== undefined && user.roles && user.roles.length > 0),
      take(1),
      timeout(3000)
    ).subscribe({
      next: user => {
        if (user) {
          this.handleSuccessfulAuth(user);
        }
      },
      error: () => {
        this.redirectToLogin('Unable to complete authentication after retry');
      }
    });
  }
  
  private handleAuthenticationFailure(): void {
    if (this.hasNavigated) {
      return;
    }
    
    if (this.timer) {
      clearInterval(this.timer);
    }
    
    this.redirectToLogin('Authentication failed');
  }
  
  private redirectToLogin(reason?: string): void {
    if (this.hasNavigated) {
      return;
    }
    
    this.hasNavigated = true;
    
    // Clear any stored user data
    sessionStorage.removeItem('user_profile');
    
    // Store current URL as target for after login (but not loading page itself)
    const currentUrl = this.router.url;
    if (currentUrl && currentUrl !== '/auth/loading' && !currentUrl.includes('/auth/')) {
      sessionStorage.setItem('auth_target_url', currentUrl);
      }
    
    // Redirect to login
    this.authService.login();
  }

  private navigateAfterAuth(user: User): void {
    if (this.hasNavigated) {
      return;
    }

    this.hasNavigated = true;
    
    const currentUrl = this.router.url;
    if (currentUrl !== '/auth/loading') {
      return;
    }

    // Let the auth service handle post-authentication navigation
    this.authService['handlePostAuthNavigation'](user);
  }
} 