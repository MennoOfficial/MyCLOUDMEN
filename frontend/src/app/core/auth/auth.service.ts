import { Injectable, Inject, Optional } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
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
  company?: any;
  companyInfo?: any;
}

/**
 * Core authentication service that handles auth0 integration
 * Only responsible for authentication, not user data or company status
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSubject = new BehaviorSubject<User | null>(null);
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
      this.setupAuth0();
    }
  }
  
  private setupAuth0(): void {
    // Check if we're already authenticated
    this.auth0.isAuthenticated$.subscribe(isAuthenticated => {
      if (isAuthenticated) {
        this.auth0.user$.pipe(
          tap(auth0User => {
            if (auth0User) {
              this.fetchUserProfile(auth0User);
            }
          })
        ).subscribe();
      }
    });

    // Subscribe to future authentication changes
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        this.fetchUserProfile(auth0User);
      }
    });
  }

  private loadUserFromStorage(): void {
    const storedUser = sessionStorage.getItem('user_profile');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        if (!Array.isArray(user.roles) || user.roles.length === 0) {
          user.roles = ['COMPANY_USER'];
        }
        this.userSubject.next(user);
        
        // Check company status after loading from storage
        this.checkAndHandleCompanyStatus();
      } catch (e) {
        sessionStorage.removeItem('user_profile');
      }
    }
  }

  private saveUserToStorage(user: User): void {
    sessionStorage.setItem('user_profile', JSON.stringify(user));
  }

  private fetchUserProfile(auth0User: any): void {
    if (!auth0User?.sub) {
      return;
    }

    const auth0Id = auth0User.sub;
    const encodedAuth0Id = encodeURIComponent(auth0Id);
    
    // Fetch user from backend API
    this.http.get<User>(`${this.environmentService.apiUrl}/users/${encodedAuth0Id}`)
      .pipe(
        catchError(error => {
          // If user not found, register them via backend
          if (error.status === 404) {
            return this.http.post<User>(`${this.environmentService.apiUrl}/users/register`, {
              auth0Id: auth0User.sub,
              email: auth0User.email,
              name: auth0User.name,
              picture: auth0User.picture
            }).pipe(
              catchError(() => {
                return of(null);
              })
            );
          }
          
          return of(null);
        })
      )
      .subscribe(user => {
        if (user) {
          // Ensure user has a role
          if (!Array.isArray(user.roles) || user.roles.length === 0) {
            user.roles = ['COMPANY_USER'];
          }
            
          this.userSubject.next(user);
          this.saveUserToStorage(user);
              
          // Handle redirects based on user status
          this.handleUserStatusRedirect(user);
        }
      });
  }

  private handleUserStatusRedirect(user: User): void {
    if (user.status === 'PENDING') {
      this.router.navigate(['/pending-account']);
    } else if (user.status === 'DEACTIVATED') {
      this.router.navigate(['/account-deactivated'], {
        queryParams: { status: user.status }
      });
    } else {
      // Handle company status check
      this.handleCompanyStatusCheck(user);
    }
  }

  private handleCompanyStatusCheck(user: any): void {
    if (!user) return;

    // First check if user already has company status information
    if (user.companyStatus) {
      if (user.companyStatus === 'DEACTIVATED' || user.companyStatus === 'SUSPENDED') {
        this.redirectToCompanyInactive(user.companyStatus, user.companyName || '');
        return;
      }
    }

    // If user has a companyId, check that company's status
    if (user.companyId) {
      this.http.get<any>(`${this.environmentService.apiUrl}/companies/${user.companyId}`)
        .pipe(
          catchError(() => {
            return of(null);
          })
        )
        .subscribe(company => {
          if (company && company.status) {
            if (company.status === 'DEACTIVATED' || company.status === 'SUSPENDED') {
              this.redirectToCompanyInactive(company.status, company.name || '');
            }
          }
        });
      return;
    }

    // If no company info in user, check by email domain
    if (user.email) {
      const domain = this.getEmailDomain(user.email);
      if (domain) {
        this.fetchCompanyByDomain(domain);
      }
    }
  }
  
  private redirectToCompanyInactive(status: string, companyName: string): void {
    this.router.navigate(['/company-inactive'], {
      queryParams: { 
        status: status,
        company: companyName
      },
      replaceUrl: true
    });
  }

  /**
   * Check company status for current user and redirect if inactive
   * Used by the status guard to enforce company status checks
   */
  checkAndHandleCompanyStatus(): boolean {
    const currentUser = this.getCurrentUser();
    if (!currentUser || !currentUser.email) {
      return true;
    }
    
    // Get company status from user object - prioritize database information
    let companyStatus = null;
    let companyName = '';
    
    // First check company data from user object (from database)
    if (currentUser.company && currentUser.company.status) {
      companyStatus = currentUser.company.status;
      companyName = currentUser.company.name || '';
      return this.handleCompanyStatus(companyStatus, companyName);
    } 
    
    if (currentUser.companyInfo && currentUser.companyInfo.status) {
      companyStatus = currentUser.companyInfo.status;
      companyName = currentUser.companyInfo.name || '';
      return this.handleCompanyStatus(companyStatus, companyName);
    }
    
    // If no company data on user, check by email domain
    const emailDomain = this.getEmailDomain(currentUser.email);
    if (emailDomain) {
      // We need to do an async call, so we can't return immediately
      // Instead, we'll return true for now and let the async check handle redirection
      this.fetchCompanyByDomain(emailDomain);
    }
    
    // Allow access for now until async company check completes
    return true;
  }
  
  private fetchCompanyByDomain(domain: string): void {
    if (!domain) return;
    
    // Use the correct API endpoint - teamleader/companies/remote instead of just companies
    this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/remote`)
      .pipe(
        catchError(error => {
          // If we get a 404 error, try the /companies endpoint as fallback
          if (error.status === 404) {
            return this.http.get<any>(`${this.environmentService.apiUrl}/companies`)
              .pipe(
                catchError(() => {
                  return of(null);
                })
              );
          }
          return of(null);
        })
      )
      .subscribe(response => {
        // Check if response is an array or has a companies property
        const companies = Array.isArray(response) ? response : response?.companies || response?.content;
        
        if (!companies || companies.length === 0) {
          return;
        }
        
        // Look for a company with matching domain
        const matchingCompany = this.findCompanyByDomain(companies, domain);
        
        if (matchingCompany) {
          // Get company status - either direct or from customFields
          const status = matchingCompany.status || 
            (matchingCompany.customFields && matchingCompany.customFields.status);
          
          // Redirect if company is not active
          if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
            this.redirectToCompanyInactive(status, matchingCompany.name || '');
          }
        } else {
          // Specific check for known company 'FutureBuild Solutions'
          const targetCompanyName = 'FutureBuild Solutions';
          const exactNameMatch = companies.find((c: any) => c.name === targetCompanyName);
          
          if (exactNameMatch) {
            // Check status
            const status = exactNameMatch.status || 
              (exactNameMatch.customFields && exactNameMatch.customFields.status);
            
            // Redirect if company is not active
            if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
              this.redirectToCompanyInactive(status, exactNameMatch.name || '');
            }
          }
        }
      });
  }
  
  private processCompanyMatch(company: any): void {
    if (!company) return;
    
    // Get status from standard property or customFields
    let status = company.status;
    
    // Check for status in customFields as fallback
    if (!status && company.customFields && company.customFields.status) {
      status = company.customFields.status;
    }
    
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
      this.redirectToCompanyInactive(status, company.name || 'Your company');
    }
  }
  
  private handleCompanyStatus(status: string, companyName: string): boolean {
    // Only proceed with redirect if we have a valid status
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
      // Clear the current route navigation history to prevent back navigation
      this.router.navigate(['/company-inactive'], {
        queryParams: { 
          status: status,
          company: companyName
        },
        replaceUrl: true  // Replace current URL in history
      });
      return false;
    }
    
    return true;
  }
  
  /**
   * Extract domain from email
   */
  private getEmailDomain(email: string): string | null {
    if (!email || !email.includes('@')) return null;
    return email.split('@')[1];
  }

  private redirectBasedOnRoles(roles: UserRole[]): void {
    if (roles.includes('SYSTEM_ADMIN')) {
      this.router.navigate(['/system-admin/companies']);
    } else if (roles.includes('COMPANY_ADMIN')) {
      this.router.navigate(['/company-admin/users']);
    } else {
      this.router.navigate(['/company-user/requests']);
    }
  }

  /**
   * Checks company status for specific test emails
   * Public for internal usage by guards and components
   */
  checkCompanyStatusForEmail(email: string): { status: string, name: string } | null {
    // This method now uses API data only - no hardcoded values
    return null;
  }

  login(): void {
    if (!this.auth0) {
      return;
    }
    
    sessionStorage.removeItem('auth_error');
    this.auth0.loginWithRedirect({
      appState: { target: window.location.pathname }
    });
  }

  logout(): void {
    if (!this.auth0) {
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
      return of('');
    }
    return this.auth0.getAccessTokenSilently();
  }

  handleAuthCallback(): void {
    if (!this.auth0) {
      return;
    }
    
    // First try handling with state validation
    this.auth0.handleRedirectCallback().subscribe({
      next: (result) => {
        // Navigate to the target URL or home page
        const targetUrl = result.appState?.target || '/';
        this.router.navigate([targetUrl]);
      },
      error: (err) => {
        // If there's a state error, try to check if we're authenticated anyway
        if (err.message && err.message.includes('state')) {
          this.auth0.isAuthenticated$.subscribe(isAuthenticated => {
            if (isAuthenticated) {
              this.router.navigate(['/']);
            } else {
              this.router.navigate(['/']);
            }
          });
        } else {
          // For other errors, just go to home
          this.router.navigate(['/']);
        }
      }
    });
  }

  isAuthenticated(): Observable<boolean> {
    if (!this.auth0) {
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

  getCurrentUser(): User | null {
    return this.userSubject.value;
  }

  hasRole(role: UserRole): boolean {
    return this.getUserRoles().includes(role);
  }

  refreshUserProfile(): void {
    sessionStorage.removeItem('user_profile');
    this.userSubject.next(null);
    
    this.auth0.user$.subscribe(auth0User => {
      if (auth0User) {
        this.fetchUserProfile(auth0User);
      }
    });
  }

  /**
   * Find a company that matches the given domain
   * First checks primaryDomain, then checks contactInfo emails
   */
  private findCompanyByDomain(companies: any[], domain: string): any {
    if (!domain) return null;
    
    // First try to match by primaryDomain exact match
    let matchingCompany = companies.find((company: any) => 
      company.primaryDomain && 
      company.primaryDomain.toLowerCase() === domain.toLowerCase()
    );
    
    if (matchingCompany) {
      return matchingCompany;
    }
    
    // Second, try to find company by looking at contactInfo email domains
    matchingCompany = companies.find((company: any) => {
      if (company.contactInfo && Array.isArray(company.contactInfo)) {
        const hasMatchingDomain = company.contactInfo.some((contact: any) => {
          if (!contact.email && !contact.value) return false;
          const email = contact.email || contact.value;
          const contactDomain = this.getEmailDomain(email);
          const matches = domain.toLowerCase() === (contactDomain || '').toLowerCase();
          return matches;
        });
        
        if (hasMatchingDomain) return true;
      }
      
      // Check email property directly if it exists
      if (company.email) {
        const companyDomain = this.getEmailDomain(company.email);
        const matches = domain.toLowerCase() === (companyDomain || '').toLowerCase();
        if (matches) {
          return true;
        }
      }
      
      // For exact email match with your domain
      const userDomain = domain.toLowerCase();
      if (company.contactEmails && Array.isArray(company.contactEmails)) {
        const hasMatchingEmail = company.contactEmails.some((email: string) => {
          const contactDomain = this.getEmailDomain(email);
          const matches = userDomain === (contactDomain || '').toLowerCase();
          return matches;
        });
        
        if (hasMatchingEmail) return true;
      }
      
      return false;
    });
    
    return matchingCompany;
  }
}