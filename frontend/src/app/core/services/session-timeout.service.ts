import { Injectable, OnDestroy } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Router } from '@angular/router';
import { switchMap, catchError, take } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SessionTimeoutService implements OnDestroy {
  private showTimeoutModalSubject = new BehaviorSubject<boolean>(false);
  showTimeoutModal$ = this.showTimeoutModalSubject.asObservable();
  
  private remainingTimeSubject = new BehaviorSubject<number>(0);
  remainingTime$ = this.remainingTimeSubject.asObservable();
  
  private countdownInterval: any;
  private sessionTimeoutId: any = null;
  private warningTimeoutId: any = null;
  private jwtCheckInterval: any = null;
  private authSubscription: Subscription | null = null;
  
  // Session constants
  private readonly INACTIVITY_TIMEOUT = 60 * 60 * 1000; // 1 hour of inactivity
  private readonly INACTIVITY_WARNING = 1 * 60 * 1000; // 1 minute warning for inactivity
  private readonly JWT_WARNING = 5 * 60 * 1000; // 5 minutes warning for JWT expiration
  private readonly JWT_CHECK_INTERVAL = 30 * 1000; // 30 seconds
  private readonly AUTO_REFRESH_INTERVAL = 50 * 60 * 1000; // Auto-refresh every 50 minutes
  
  private lastActivityTime: number = Date.now();
  private autoRefreshInterval: any = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.setupActivityListeners();
    

    this.authService.user$.subscribe(user => {
              if (user) {
          // Reset activity time when user logs in
          this.lastActivityTime = Date.now();
          this.startSessionTimer();
          this.startJwtMonitoring();
          this.startAutoRefresh();
        } else {
          this.clearSessionTimeout();
          this.stopJwtMonitoring();
          this.stopAutoRefresh();
        }
    });
  }

  private setupActivityListeners(): void {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart'];
    
    events.forEach(event => {
      window.addEventListener(event, () => {
        this.updateLastActivity();
      }, { passive: true });
    });
  }
  
  private updateLastActivity(): void {
    this.lastActivityTime = Date.now();
    
    // Only reset the inactivity timer if modal is not showing
    if (!this.showTimeoutModalSubject.value) {
      this.resetSessionTimer();
    }
  }
  
  // Start the session timer
  startSessionTimer(): void {
    // Clear any existing timeout
    this.clearSessionTimeout();
    
    // Set timeout for warning
    this.warningTimeoutId = setTimeout(() => {
      this.showTimeoutModalSubject.next(true);
      this.startCountdown(this.INACTIVITY_WARNING);
    }, this.INACTIVITY_TIMEOUT - this.INACTIVITY_WARNING);
    
    // Set timeout for logout
    this.sessionTimeoutId = setTimeout(() => {
      this.logout();
    }, this.INACTIVITY_TIMEOUT);
  }

  // Reset the session timer
  resetSessionTimer(): void {
    this.clearSessionTimeout();
    this.startSessionTimer();
  }

  // Clear the session timeout
  clearSessionTimeout(): void {
    if (this.sessionTimeoutId) {
      clearTimeout(this.sessionTimeoutId);
      this.sessionTimeoutId = null;
    }
    
    if (this.warningTimeoutId) {
      clearTimeout(this.warningTimeoutId);
      this.warningTimeoutId = null;
    }
  }

  private startCountdown(duration: number): void {
    let timeLeft = Math.floor(duration / 1000);
    this.remainingTimeSubject.next(timeLeft);
    
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
    
    this.countdownInterval = setInterval(() => {
      timeLeft -= 1;
      this.remainingTimeSubject.next(timeLeft);
      
      if (timeLeft <= 0) {
        clearInterval(this.countdownInterval);
        this.logout();
      }
    }, 1000);
  }



  closeTimeoutModal(): void {
    this.showTimeoutModalSubject.next(false);
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
  }

  logout(): void {
    this.closeTimeoutModal();
    this.stopJwtMonitoring();
    this.authService.logout();
  }

  /**
   * Start monitoring JWT token expiration
   */
  private startJwtMonitoring(): void {
    setTimeout(() => {
      this.authSubscription = this.authService.isAuthenticated().subscribe(isAuthenticated => {
        if (isAuthenticated) {
          this.startJwtExpirationCheck();
        } else {
          this.stopJwtExpirationCheck();
        }
      });
    }, 2000);
  }

  /**
   * Stop JWT monitoring
   */
  private stopJwtMonitoring(): void {
    if (this.authSubscription) {
      this.authSubscription.unsubscribe();
      this.authSubscription = null;
    }
    this.stopJwtExpirationCheck();
  }

  private startJwtExpirationCheck(): void {
    this.stopJwtExpirationCheck();
    
    this.jwtCheckInterval = setInterval(() => {
      this.checkJwtExpiration();
    }, this.JWT_CHECK_INTERVAL);
    
    this.checkJwtExpiration();
  }

  private stopJwtExpirationCheck(): void {
    if (this.jwtCheckInterval) {
      clearInterval(this.jwtCheckInterval);
      this.jwtCheckInterval = null;
    }
  }

  private checkJwtExpiration(): void {
    this.authService.isAuthenticated().pipe(
      take(1),
      switchMap(isAuthenticated => {
        if (!isAuthenticated) {
          return of(null);
        }
        return this.authService.getAccessToken();
      }),
      catchError(error => {
        if (this.authService.getCurrentUser()) {
          this.handleTokenExpired();
        }
        return of(null);
      })
    ).subscribe(token => {
      if (!token) {
        return;
      }

      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const exp = payload.exp;
        
        if (!exp) {
          return;
        }
        
        const now = Math.floor(Date.now() / 1000);
        const expiresIn = exp - now;
        
        if (expiresIn <= 0) {
          this.handleTokenExpired();
          return;
        }
        
        // Check if we should show modal based on JWT expiration OR inactivity
        const timeSinceLastActivity = Date.now() - this.lastActivityTime;
        const inactivityTimeLeft = this.INACTIVITY_TIMEOUT - timeSinceLastActivity;
        
        // Show modal if either JWT expires soon OR user has been inactive for too long
        const jwtWarningThreshold = this.JWT_WARNING / 1000; // 5 minutes in seconds
        const inactivityWarningThreshold = this.INACTIVITY_WARNING; // 1 minute in milliseconds
        
        if ((expiresIn <= jwtWarningThreshold || inactivityTimeLeft <= inactivityWarningThreshold) && !this.showTimeoutModalSubject.value) {
          // Determine which timeout is triggering and use appropriate countdown
          if (expiresIn <= jwtWarningThreshold && inactivityTimeLeft > inactivityWarningThreshold) {
            // JWT expiring soon, use JWT countdown
            this.handleTokenNearExpiry(expiresIn);
          } else if (inactivityTimeLeft <= inactivityWarningThreshold) {
            // Inactivity timeout, use inactivity countdown
            this.handleTokenNearExpiry(Math.floor(inactivityTimeLeft / 1000));
          } else {
            // Both are close, use the shorter one
            const timeUntilLogout = Math.min(expiresIn, Math.floor(inactivityTimeLeft / 1000));
            this.handleTokenNearExpiry(timeUntilLogout);
          }
        }
      } catch (error) {
        // Silently handle JWT parsing errors
      }
    });
  }

  private handleTokenNearExpiry(expiresInSeconds: number): void {
    this.clearSessionTimeout();
    this.showTimeoutModalSubject.next(true);
    this.startCountdown(expiresInSeconds * 1000);
  }

  private handleTokenExpired(): void {
    this.logout();
  }

  extendSession(): void {
    this.closeTimeoutModal();
    
    // Reset activity time when user actively chooses to continue
    this.lastActivityTime = Date.now();
    
    // Force refresh the JWT token to get a fresh one
    this.forceTokenRefresh();
  }

  private forceTokenRefresh(): void {
    // Force Auth0 to get a fresh token (bypassing cache)
    this.authService.getAccessToken().pipe(
      switchMap(() => {
        // Get a completely fresh token by calling Auth0 directly
        return this.authService.getAccessToken();
      }),
      catchError(error => {
        // If we can't refresh the token, logout
        this.logout();
        return of(null);
      })
    ).subscribe(token => {
      if (token) {
        // Reset the inactivity timer
        this.resetSessionTimer();
      } else {
        this.logout();
      }
    });
  }

  

  private startAutoRefresh(): void {
    this.stopAutoRefresh(); // Clear any existing interval
    
    // Auto-refresh token every 50 minutes for active users
    this.autoRefreshInterval = setInterval(() => {
      const timeSinceLastActivity = Date.now() - this.lastActivityTime;
      
      // Only auto-refresh if user has been active in the last 10 minutes
      if (timeSinceLastActivity < (10 * 60 * 1000)) {
        this.forceTokenRefresh();
      }
    }, this.AUTO_REFRESH_INTERVAL);
  }

  private stopAutoRefresh(): void {
    if (this.autoRefreshInterval) {
      clearInterval(this.autoRefreshInterval);
      this.autoRefreshInterval = null;
    }
  }

  ngOnDestroy(): void {
    this.stopJwtMonitoring();
    this.clearSessionTimeout();
    this.stopAutoRefresh();
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
  }
} 