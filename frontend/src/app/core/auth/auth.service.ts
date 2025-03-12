import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService as Auth0Service } from '@auth0/auth0-angular';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

export type UserRole = 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER';

export interface User {
  id?: string;
  auth0Id?: string;
  email?: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  picture?: string;
  roles: UserRole[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSubject = new BehaviorSubject<User | null>(null);
  private sessionTimeoutId: any = null;
  private warningTimeoutId: any = null;
  private readonly SESSION_DURATION = 60 * 60 * 1000;
  private readonly WARNING_BEFORE_TIMEOUT = 15 * 1000;
  
  // New subject to emit session timeout warnings
  private sessionTimeoutWarningSubject = new Subject<number>();
  sessionTimeoutWarning$ = this.sessionTimeoutWarningSubject.asObservable();

  public user$ = this.userSubject.asObservable();
  
  constructor(
    private auth0: Auth0Service,
    private router: Router,
    private http: HttpClient
  ) {
    this.loadUserFromStorage();
    this.initAuth0User();
    this.startSessionTimer();
  }

  private initAuth0User(): void {
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        this.fetchOrCreateUserProfile(auth0User);
      }
    });
  }

  private loadUserFromStorage(): void {
    const storedUser = sessionStorage.getItem('user_profile');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        this.userSubject.next(user);
      } catch (e) {
        sessionStorage.removeItem('user_profile');
      }
    }
  }

  private saveUserToStorage(user: User): void {
    sessionStorage.setItem('user_profile', JSON.stringify(user));
  }

  private fetchOrCreateUserProfile(auth0User: any): void {
    const auth0Id = auth0User.sub;
    const encodedAuth0Id = encodeURIComponent(auth0Id);
    
    this.http.get<User>(`${environment.apiUrl}/users/${encodedAuth0Id}`)
      .pipe(
        catchError(error => {
          if (error.status === 404) {
            return this.createNewUser(auth0User);
          }
          return of(this.createDefaultUser(auth0User));
        })
      )
      .subscribe(user => {
        if (user) {
          if (!user.roles) {
            user.roles = user.roles ? [user.roles as UserRole] : ['COMPANY_USER'];
          }
          
          this.userSubject.next(user);
          this.saveUserToStorage(user);
          this.redirectBasedOnRoles(user.roles);
        }
      });
  }

  private createNewUser(auth0User: any): Observable<User> {
    const newUser = {
      auth0Id: auth0User.sub,
      email: auth0User.email,
      name: auth0User.name,
      picture: auth0User.picture,
      roles: ['COMPANY_ADMIN'] as UserRole[],
      provider: auth0User.sub.split('|')[0]
    };
    
    return this.http.post<User>(`${environment.apiUrl}/users/register`, newUser)
      .pipe(
        catchError(() => of(this.createDefaultUser(auth0User, ['COMPANY_ADMIN'])))
      );
  }

  private createDefaultUser(auth0User: any, roles: UserRole[] = ['COMPANY_USER']): User {
    return {
      auth0Id: auth0User.sub,
      email: auth0User.email,
      name: auth0User.name,
      picture: auth0User.picture,
      roles
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
    this.auth0.loginWithRedirect({
      appState: { target: window.location.pathname }
    });
  }

  logout(): void {
    this.userSubject.next(null);
    sessionStorage.removeItem('user_profile');
    this.auth0.logout({
      logoutParams: {
        returnTo: window.location.origin
      }
    });
  }

  getAccessToken(): Observable<string> {
    return this.auth0.getAccessTokenSilently();
  }

  handleAuthCallback(): void {
    this.auth0.handleRedirectCallback().subscribe({
      next: () => console.log('Auth callback handled successfully'),
      error: () => this.router.navigate(['/'])
    });
  }

  isAuthenticated(): Observable<boolean> {
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
    sessionStorage.removeItem('user_profile');
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
}