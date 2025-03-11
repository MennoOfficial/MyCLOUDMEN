import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AuthService as Auth0Service } from '@auth0/auth0-angular';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

export interface User {
  id?: string;
  auth0Id?: string;
  email?: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  picture?: string;
  role?: 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER';
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSubject = new BehaviorSubject<User | null>(null);
  public user$ = this.userSubject.asObservable();

  constructor(
    private auth0: Auth0Service,
    private router: Router,
    private http: HttpClient
  ) {
    // Initialize auth state from Auth0
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        this.fetchOrCreateUserProfile(auth0User);
      }
    });
  }

  // Fetch user from backend or create if not exists
  private fetchOrCreateUserProfile(auth0User: any): void {
    const auth0Id = auth0User.sub;
    
    // Try to get existing user first
    this.http.get<User>(`${environment.apiUrl}/users/by-auth0-id/${auth0Id}`)
      .pipe(
        catchError(error => {
          if (error.status === 404) {
            // User doesn't exist, create a new one
            return this.createNewUser(auth0User);
          }
          console.error('Error fetching user profile:', error);
          return of(null);
        })
      )
      .subscribe(user => {
        if (user) {
          this.userSubject.next(user);
        }
      });
  }

  // Create a new user in our backend - fix return type to match
  private createNewUser(auth0User: any): Observable<User | null> {
    const isGoogleAuth = auth0User.sub?.startsWith('google-oauth2');
    const nameParts = auth0User.name?.split(' ') || ['', ''];
    
    const newUser = {
      auth0Id: auth0User.sub,
      email: auth0User.email,
      name: auth0User.name,
      firstName: nameParts[0],
      lastName: nameParts.slice(1).join(' '),
      picture: auth0User.picture,
      provider: isGoogleAuth ? 'Google' : 'Email',
      customerGoogleId: isGoogleAuth ? auth0User.sub.split('|')[1] : null
    };
    
    return this.http.post<User>(`${environment.apiUrl}/users/register`, newUser)
      .pipe(
        catchError(error => {
          console.error('Error creating user:', error);
          return of(null);
        })
      );
  }

  // Login with Auth0
  login(): void {
    this.auth0.loginWithRedirect({
      appState: { target: window.location.pathname }
    });
  }

  // Logout
  logout(): void {
    // Clear local user data
    this.userSubject.next(null);
    
    // Use Auth0 logout
    this.auth0.logout({
      logoutParams: {
        returnTo: window.location.origin
      }
    });
  }

  // Get access token for API calls
  getAccessToken(): Observable<string> {
    return this.auth0.getAccessTokenSilently();
  }

  // Handle auth callback
  handleAuthCallback(): void {
    this.auth0.handleRedirectCallback().subscribe({
      next: () => {
        // Redirect to the saved target URL or home
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        console.error('Error handling auth callback:', err);
        this.router.navigate(['/']);
      }
    });
  }

  // Check if user is authenticated
  isAuthenticated(): Observable<boolean> {
    return this.auth0.isAuthenticated$;
  }

  // Get user role - fix type to match expected type in components
  getUserRole(): 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' {
    return this.userSubject.value?.role || 'COMPANY_USER';
  }
}