import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, RedirectResult } from '../auth.service';
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
  maxWaitTime = 10000; // 10 seconds max wait time
  
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    // Start a timer for UX purposes
    const startTime = Date.now();
    const timer = setInterval(() => {
      this.loadingDuration = Date.now() - startTime;
      
      // Update message after 3 seconds
      if (this.loadingDuration > 3000 && this.loadingDuration <= 7000) {
        this.loadingMessage = 'Retrieving user profile...';
      } else if (this.loadingDuration > 7000) {
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
        console.log(`[Loading] Authentication completed for: ${user.email}`);
        
        // Check if we're still on the loading page - if not, don't redirect
        const currentUrl = this.router.url;
        if (currentUrl !== '/auth/loading') {
          console.log(`[Loading] Already navigated away from loading page to: ${currentUrl}`);
          return;
        }
        
        // AuthService will handle all navigation including approvals
        // So we just check for manual target URLs here
        
        // Check if there's a stored target URL
        const targetUrl = sessionStorage.getItem('auth_target_url');
        if (targetUrl) {
          console.log(`[Loading] Redirecting to stored target URL: ${targetUrl}`);
          sessionStorage.removeItem('auth_target_url');
          this.router.navigateByUrl(targetUrl, { replaceUrl: true });
          return;
        }
      },
      error: (error) => {
        clearInterval(timer);
        console.error('[Loading] Authentication timeout or error:', error);
        this.router.navigate(['/auth/error'], {
          queryParams: { error: 'authentication_timeout' },
          replaceUrl: true
        });
      }
    });
  }
} 