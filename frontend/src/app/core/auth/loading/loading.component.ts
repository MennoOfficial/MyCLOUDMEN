import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, RedirectResult } from '../auth.service';
import { User } from '../../models/auth.model';
import { filter, take, timeout } from 'rxjs/operators';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-auth-loading',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  templateUrl: './loading.component.html',
  styleUrls: ['./loading.component.scss']
})
export class LoadingComponent implements OnInit {
  loadingMessage = 'Authenticating...';
  loadingDuration = 0;
  maxWaitTime = 20000; // 20 seconds max wait time (increased for retry mechanism)
  loadingState: string = 'authenticating';
  errorMessage: string = '';
  hasNavigated: boolean = false;
  
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    // Start a timer for UX purposes
    const startTime = Date.now();
    const timer = setInterval(() => {
      this.loadingDuration = Date.now() - startTime;
      
      // Update message based on duration - match with retry timing
      if (this.loadingDuration <= 2000) {
        this.loadingMessage = 'Authenticating...';
      } else if (this.loadingDuration <= 5000) {
        this.loadingMessage = 'Setting up your account...';
      } else if (this.loadingDuration <= 10000) {
        this.loadingMessage = 'Configuring your permissions...';
      } else if (this.loadingDuration <= 15000) {
        this.loadingMessage = 'Almost ready...';
      } else {
        this.loadingMessage = 'This is taking longer than expected...';
      }
      
      // If loading takes too long, redirect to error page
      if (this.loadingDuration >= this.maxWaitTime) {
        clearInterval(timer);
        this.router.navigate(['/auth/error'], {
          queryParams: { error: 'timeout' },
          replaceUrl: true
        });
      }
    }, 1000);
    
    // Wait for authentication to complete and user to be available
    this.authService.user$.pipe(
      filter(user => user !== null && user !== undefined),
      take(1),
      timeout(this.maxWaitTime)
    ).subscribe({
      next: (user) => {
        clearInterval(timer);
        
        if (user) {
          this.loadingState = 'almost-ready';
          
          // Short delay before navigation to let user see the final state
          setTimeout(() => {
            this.navigateAfterAuth(user);
          }, 1000);
        }
      },
      error: (error) => {
        clearInterval(timer);
        this.router.navigate(['/auth/error'], {
          queryParams: { error: 'authentication_timeout' },
          replaceUrl: true
        });
      }
    });

    // Handle auth errors
    this.authService.authError$.subscribe(error => {
      if (error) {
        this.loadingState = 'error';
        this.errorMessage = error;
      }
    });

    // Set up timeout for loading
    setTimeout(() => {
      if (this.loadingState === 'authenticating' || this.loadingState === 'setting-up' || 
          this.loadingState === 'configuring' || this.loadingState === 'almost-ready') {
        this.loadingState = 'error';
        this.errorMessage = 'Authentication is taking longer than expected. Please try refreshing the page.';
      }
    }, this.maxWaitTime);
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