import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, RedirectResult } from '../auth.service';
import { filter, take } from 'rxjs/operators';

@Component({
  selector: 'app-auth-loading',
  standalone: true,
  imports: [CommonModule],
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
    
    // Subscribe to auth loading state
    this.authService.authLoading$.pipe(
      filter(loading => !loading),  // Wait until loading is false
      take(1)
    ).subscribe(async () => {
      clearInterval(timer);
      
      // Get the user and check where to navigate
      const user = this.authService.getCurrentUser();
      if (user) {
        console.log(`[Loading] User loaded: ${user.email}, ${user.status}`);
        
        // Allow the AuthService to determine the correct redirect
        // This will follow the appropriate sequence of checks
        const redirectResult = await this.authService['determineUserRedirect'](user);
        
        if (redirectResult) {
          console.log(`[Loading] Redirecting to ${redirectResult.path}`);
          this.router.navigate([redirectResult.path], {
            queryParams: redirectResult.queryParams,
            replaceUrl: redirectResult.replaceUrl ?? true
          });
          return;
        }
        
        // Check for stored target URL if no redirection was determined
        const targetUrl = sessionStorage.getItem('auth_target_url');
        if (targetUrl) {
          console.log(`[Loading] Redirecting to target URL: ${targetUrl}`);
          sessionStorage.removeItem('auth_target_url');
          this.router.navigateByUrl(targetUrl, { replaceUrl: true });
          return;
        }
        
        // Default redirects based on role if nothing else matches
        console.log(`[Loading] Using role-based redirect`);
        if (user.roles.includes('SYSTEM_ADMIN')) {
          this.router.navigate(['/companies'], { replaceUrl: true });
        } else if (user.roles.includes('COMPANY_ADMIN')) {
          this.router.navigate(['/users'], { replaceUrl: true });
        } else {
          this.router.navigate(['/requests'], { replaceUrl: true });
        }
      } else {
        // No user available, redirect to home
        console.log(`[Loading] No user found, redirecting to home`);
        this.router.navigate(['/'], { replaceUrl: true });
      }
    });
  }
} 