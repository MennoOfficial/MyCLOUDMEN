import { Injectable, Inject, Optional } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { AuthService as Auth0Service } from '@auth0/auth0-angular';
import { Router } from '@angular/router';
import { EnvironmentService } from '../services/environment.service';
import { DOCUMENT } from '@angular/common';

export type UserRole = 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER';
export type UserStatus = 'ACTIVATED' | 'DEACTIVATED' | 'PENDING' | 'REJECTED';

export interface User {
  id?: string;
  auth0Id?: string;
  email?: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  picture?: string;
  roles: UserRole[];
  status?: UserStatus;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSubject = new BehaviorSubject<User | null>(null);
  private sessionTimeoutId: any = null;
  private warningTimeoutId: any = null;
  private readonly SESSION_DURATION = 2 * 60 * 60 * 1000;
  private readonly WARNING_BEFORE_TIMEOUT = 5 * 60 * 1000;
  
  // New subject to emit session timeout warnings
  private sessionTimeoutWarningSubject = new Subject<number>();
  sessionTimeoutWarning$ = this.sessionTimeoutWarningSubject.asObservable();

  public user$ = this.userSubject.asObservable();
  
  constructor(
    @Inject(DOCUMENT) private document: Document,
    private router: Router,
    private http: HttpClient,
    private environmentService: EnvironmentService,
    @Optional() @Inject(Auth0Service) private auth0: Auth0Service
  ) {
    this.loadUserFromStorage();
    
    if (this.auth0) {
      this.initAuth0User();
    } else {
      console.error('Auth0Service is not available. Authentication features will be disabled.');
    }
  }
  
  private initAuth0User(): void {
    this.startSessionTimer();
    this.setupAuthenticationListeners();
    
    // First check if we're already authenticated
    this.auth0.isAuthenticated$.subscribe(isAuthenticated => {
      if (isAuthenticated) {
        // If authenticated, immediately get the user profile
        this.auth0.user$.pipe(
          tap(auth0User => {
            if (auth0User) {
              console.log('Auth0 user available:', auth0User);
              this.fetchOrCreateUserProfile(auth0User);
            }
          })
        ).subscribe();
      }
    });

    // Then set up the ongoing subscription for future changes
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        console.log('Auth0 user updated:', auth0User);
        this.fetchOrCreateUserProfile(auth0User);
      }
    });
  }

  private setupAuthenticationListeners(): void {
    if (!this.auth0) return;
    
    // Listen for authentication errors
    this.auth0.error$.subscribe(error => {
      if (error) {
        console.error('Auth0 error:', error);
        // Log failed authentication
        const email = this.extractEmailFromError(error);
        this.logFailedAuthentication(email, error.message || 'Unknown error');
      }
    });
  }

  private extractEmailFromError(error: any): string | null {
    // Try to extract email from error object
    if (error && error.email) {
      return error.email;
    }
    
    // Try to extract from local storage or session
    const storedUser = sessionStorage.getItem('user_profile');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        if (user && user.email) {
          return user.email;
        }
      } catch (e) {
        // Ignore parsing errors
      }
    }
    
    return null;
  }

  private logSuccessfulAuthentication(email: string): void {
    if (!email) return;
    
    // Check if we've already logged this authentication
    const lastAuthLog = sessionStorage.getItem('last_auth_log');
    if (lastAuthLog) {
      const lastLog = JSON.parse(lastAuthLog);
      const now = new Date().getTime();
      // If we've logged within the last 5 minutes, don't log again
      if (lastLog.email === email && (now - lastLog.timestamp) < 5 * 60 * 1000) {
        console.log('Skipping duplicate authentication log for:', email);
        return;
      }
    }
    
    console.log('Logging successful authentication for:', email);
    
    // Save this log to session storage
    sessionStorage.setItem('last_auth_log', JSON.stringify({
      email,
      timestamp: new Date().getTime()
    }));
    
    // Get the full Auth0 user profile to send to the backend
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        // Create a complete user data object with all available Auth0 information
        const userData = {
          email: email,
          sub: auth0User.sub,
          name: auth0User.name,
          given_name: auth0User.given_name,
          family_name: auth0User.family_name,
          nickname: auth0User.nickname,
          picture: auth0User.picture,
          updated_at: auth0User.updated_at,
          email_verified: auth0User.email_verified
        };
        
        console.log('Sending user data to backend for sync:', userData);
        
        // Send the complete user data to the backend
        this.http.post(`${this.environmentService.apiUrl}/auth0/log-authentication`, userData)
          .pipe(
            catchError(error => {
              console.error('Failed to log authentication:', error);
              return of(null);
            })
          )
          .subscribe(response => {
            console.log('Authentication logging response:', response);
          });
      } else {
        // Fallback to basic logging if user profile is not available
        this.http.post(`${this.environmentService.apiUrl}/auth0/log-authentication`, { email })
          .pipe(
            catchError(error => {
              console.error('Failed to log authentication:', error);
              return of(null);
            })
          )
          .subscribe(response => {
            console.log('Authentication logging response:', response);
          });
      }
    });
  }

  private logFailedAuthentication(email: string | null, reason: string): void {
    console.log('Logging failed authentication for:', email, 'Reason:', reason);
    
    this.http.post(`${this.environmentService.apiUrl}/auth0/log-authentication-failure`, { 
      email: email || 'unknown', 
      reason 
    })
    .pipe(
      catchError(error => {
        console.error('Failed to log authentication failure:', error);
        return of(null);
      })
    )
    .subscribe(response => {
      console.log('Authentication failure logging response:', response);
    });
  }

  private loadUserFromStorage(): void {
    const storedUser = sessionStorage.getItem('user_profile');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        // Verify the role is valid
        if (user.roles && Array.isArray(user.roles)) {
          // Only load valid roles
          user.roles = user.roles.filter((role: string) => 
            ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER'].includes(role)
          );
          if (user.roles.length === 0) {
            user.roles = ['COMPANY_USER'];
          }
        } else {
          user.roles = ['COMPANY_USER'];
        }
        this.userSubject.next(user);
      } catch (e) {
        console.error('Error loading user from storage:', e);
        sessionStorage.removeItem('user_profile');
      }
    }
  }

  private saveUserToStorage(user: User): void {
    sessionStorage.setItem('user_profile', JSON.stringify(user));
  }

  private fetchOrCreateUserProfile(auth0User: any): void {
    if (!auth0User?.sub) {
      console.warn('No Auth0 ID available in user profile');
      return;
    }

    const auth0Id = auth0User.sub;
    const encodedAuth0Id = encodeURIComponent(auth0Id);
    
    console.log('Fetching user profile for:', auth0Id);
    console.log('API URL being used:', this.environmentService.apiUrl);
    
    // Add a timestamp to bypass any caching
    const timestamp = new Date().getTime();
    const cacheBypass = `?_t=${timestamp}`;
    
    // Use only the environment service API URL, don't try fallbacks
    this.http.get<User>(`${this.environmentService.apiUrl}/users/${encodedAuth0Id}${cacheBypass}`, {
      headers: {
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache',
        'Expires': '0'
      }
    })
      .pipe(
        catchError(error => {
          console.log('Error fetching user profile:', error);
          
          // Last resort - create a new user
          if (error.status === 404) {
            console.log('User not found, creating new user');
            return this.createNewUser(auth0User);
          }
          
          return of(this.createDefaultUser(auth0User));
        })
      )
      .subscribe({
        next: (user) => {
          if (user) {
            console.log('User profile received:', user);
            
            // Ensure roles is always an array with at least one role
            if (!Array.isArray(user.roles) || user.roles.length === 0) {
              user.roles = ['COMPANY_USER'];
            }
            
            // Force UI update by clearing and re-adding user
            this.userSubject.next(null);
            setTimeout(() => {
              this.userSubject.next(user);
              this.saveUserToStorage(user);
              
              // Log successful authentication
              if (auth0User.email) {
                this.logSuccessfulAuthentication(auth0User.email);
              }
              
              // Check user status before redirecting
              if (user.status === 'PENDING') {
                console.log('User status is PENDING, redirecting to pending page');
                this.router.navigate(['/pending-account']);
              } else if (user.status === 'DEACTIVATED') {
                console.log('User status is DEACTIVATED, redirecting to deactivated page');
                this.router.navigate(['/account-deactivated'], {
                  queryParams: { status: user.status }
                });
              }
              // Only redirect based on role if this is the initial login and user is ACTIVATED
              else if ((this.router.url === '/' || this.router.url === '/callback') && user.status === 'ACTIVATED') {
                this.redirectBasedOnRoles(user.roles);
              }
            }, 100);
          }
        },
        error: (err) => {
          console.error('Final error getting user profile:', err);
          const defaultUser = this.createDefaultUser(auth0User);
          this.userSubject.next(defaultUser);
          this.saveUserToStorage(defaultUser);
          
          // Redirect new users to pending page
          this.router.navigate(['/pending-account']);
        }
      });
  }

  private createNewUser(auth0User: any): Observable<User> {
    const newUser = {
      auth0Id: auth0User.sub,
      email: auth0User.email,
      name: auth0User.name,
      picture: auth0User.picture,
      roles: ['COMPANY_USER'] as UserRole[], // Default to COMPANY_USER instead of COMPANY_ADMIN
      status: 'PENDING' as UserStatus, // Default status for new users
      provider: auth0User.sub.split('|')[0]
    };
    
    return this.http.post<User>(`${this.environmentService.apiUrl}/users/register`, newUser)
      .pipe(
        catchError(error => {
          console.error('Failed to create user:', error);
          this.logFailedAuthentication(auth0User.email, `Failed to create user: ${error.message || error.status}`);
          return of(this.createDefaultUser(auth0User));
        })
      );
  }

  private createDefaultUser(auth0User: any, roles: UserRole[] = ['COMPANY_USER']): User {
    return {
      auth0Id: auth0User.sub,
      email: auth0User.email,
      name: auth0User.name,
      picture: auth0User.picture,
      roles,
      status: 'PENDING' // Default status
    };
  }

  private redirectBasedOnRoles(roles: UserRole[]): void {
    if (this.router.url === '/' || this.router.url === '/callback') {
      if (roles.includes('SYSTEM_ADMIN')) {
        this.router.navigate(['/system-admin/companies']);
      } else if (roles.includes('COMPANY_ADMIN')) {
        this.router.navigate(['/company-admin/users']);
      } else {
        this.router.navigate(['/company-user/requests']);
      }
    }
  }

  login(): void {
    if (!this.auth0) {
      console.error('Auth0 not initialized');
      return;
    }
    
    // Clear any previous errors
    sessionStorage.removeItem('auth_error');
    
    this.auth0.loginWithRedirect({
      appState: { target: window.location.pathname }
    });
  }

  logout(): void {
    if (!this.auth0) {
      console.error('Auth0 not initialized');
      return;
    }
    
    this.userSubject.next(null);
    sessionStorage.removeItem('user_profile');
    this.auth0.logout({
      logoutParams: {
        returnTo: window.location.origin
      }
    });
  }

  getAccessToken(): Observable<string> {
    if (!this.auth0) {
      console.error('Auth0 not initialized');
      return of('');
    }
    return this.auth0.getAccessTokenSilently();
  }

  handleAuthCallback(): void {
    if (!this.auth0) {
      console.error('Auth0 not initialized');
      return;
    }
    
    this.auth0.handleRedirectCallback().subscribe({
      next: () => console.log('Auth callback handled successfully'),
      error: () => this.router.navigate(['/'])
    });
  }

  isAuthenticated(): Observable<boolean> {
    if (!this.auth0) {
      console.error('Auth0 not initialized');
      return of(false);
    }
    return this.auth0.isAuthenticated$;
  }

  getUserRoles(): UserRole[] {
    const user = this.userSubject.value;
    if (!user || !user.roles || user.roles.length === 0) {
      return ['COMPANY_USER'];
    }
    return user.roles;
  }

  getUserRole(): UserRole {
    return this.getUserRoles()[0];
  }

  hasRole(role: UserRole): boolean {
    return this.getUserRoles().includes(role);
  }

  updateUserRole(role: UserRole): void {
    const currentUser = this.userSubject.value;
    if (currentUser) {
      const updatedUser = { ...currentUser, roles: [role] };
      this.userSubject.next(updatedUser);
      this.saveUserToStorage(updatedUser);
    }
  }

  refreshUserProfile(): void {
    // Clear storage first
    sessionStorage.removeItem('user_profile');
    // Clear current user
    this.userSubject.next(null);
    
    // Fetch fresh data from Auth0
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        this.fetchOrCreateUserProfile(auth0User);
      }
    });
  }

  // Start the session timer
  startSessionTimer(): void {
    // Clear any existing timeout
    this.clearSessionTimeout();
    
    // We need to clear both timeouts
    if (this.warningTimeoutId) {
      clearTimeout(this.warningTimeoutId);
    }
    
    // Set timeout for warning
    this.warningTimeoutId = setTimeout(() => {
      this.sessionTimeoutWarningSubject.next(this.WARNING_BEFORE_TIMEOUT);
    }, this.SESSION_DURATION - this.WARNING_BEFORE_TIMEOUT);
    
    // Set timeout for logout
    this.sessionTimeoutId = setTimeout(() => {
      this.logout();
    }, this.SESSION_DURATION);
  }

  // Reset the session timer (call this on user activity)
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

  // Extend the session
  extendSession(): void {
    console.log('Extending session...');
    // Clear existing timeouts
    this.clearSessionTimeout();
    // Start a fresh timer
    this.startSessionTimer();
  }

  // Force a refresh of the user data from the backend
  forceSyncUserWithBackend(): void {
    console.log('Forcing sync with backend...');
    
    if (!this.auth0) {
      console.error('Auth0 not initialized, cannot sync user');
      return;
    }
    
    // Clear existing user data first
    sessionStorage.removeItem('user_profile');
    this.userSubject.next(null);
    
    // Get fresh user data from Auth0
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User && auth0User.sub) {
        console.log('Got Auth0 user for forced sync:', auth0User);
        
        // Clear any caches
        const timestamp = new Date().getTime();
        const auth0Id = auth0User.sub;
        const encodedAuth0Id = encodeURIComponent(auth0Id);
        
        // Try a direct request to the backend using environment service URL
        this.http.get<User>(`${this.environmentService.apiUrl}/users/${encodedAuth0Id}?_t=${timestamp}`, {
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        }).subscribe({
          next: (user) => {
            console.log('Force-refreshed user from backend:', user);
            if (user) {
              // Ensure roles array
              if (!Array.isArray(user.roles) || user.roles.length === 0) {
                user.roles = ['COMPANY_USER'];
              }
              
              // Update our store
              this.userSubject.next(user);
              this.saveUserToStorage(user);
            }
          },
          error: (err) => {
            console.error('Error force-refreshing user:', err);
            // If we can't get the user, fetch or create it through the normal flow
            this.fetchOrCreateUserProfile(auth0User);
          }
        });
      }
    });
  }
}