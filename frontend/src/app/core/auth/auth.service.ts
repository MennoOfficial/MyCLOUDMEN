import { Injectable, Inject, Optional } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError, timer, from, firstValueFrom, Subject, forkJoin } from 'rxjs';
import { catchError, concatMap, map, retryWhen, switchMap, take, tap, timeout, filter, finalize, shareReplay, distinctUntilChanged } from 'rxjs/operators';
import { AuthService as Auth0Service } from '@auth0/auth0-angular';
import { Router } from '@angular/router';
import { EnvironmentService } from '../services/environment.service';
import { DOCUMENT } from '@angular/common';
import { User, UserRole, UserStatus } from '../models/auth.model';

// Define types for better structure
export interface RedirectResult {
  path: string;
  queryParams?: {[key: string]: string};
  replaceUrl?: boolean;
}

export interface CompanyStatusResult {
  status: string;
  name: string;
  domain?: string;
}

/**
 * Core authentication service that handles auth0 integration and user/company status
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // State Observables
  private userSubject = new BehaviorSubject<User | null>(null);
  public user$ = this.userSubject.asObservable().pipe(distinctUntilChanged());
  
  private authLoadingSubject = new BehaviorSubject<boolean>(false);
  public authLoading$ = this.authLoadingSubject.asObservable();
  
  private authErrorSubject = new BehaviorSubject<string | null>(null);
  public authError$ = this.authErrorSubject.asObservable();
  
  // Define status constants
  private readonly INACTIVE_STATUSES = ['DEACTIVATED', 'SUSPENDED'];
  
  private isRedirectInProgress = false;
  
  // Cache for company domain status to prevent repeated API calls
  private companyStatusCache: Map<string, CompanyStatusResult> = new Map();
  private cacheTTL = 30 * 1000; // 30 seconds in milliseconds (reduced from 5 minutes)
  
  // Track if this is a genuine new authentication (not refresh/restore)
  private isGenuineNewLogin = false;
  
  // Prevent multiple simultaneous authentication attempts
  private authenticationInProgress = false;
  private authenticationPromise: Promise<User | null> | null = null;
  
  // Approval handling
  private readonly APPROVAL_STORAGE_KEY = 'pending_approval_request';
  
  constructor(
    @Inject(DOCUMENT) private document: Document,
    private router: Router,
    private http: HttpClient,
    private environmentService: EnvironmentService,
    @Optional() @Inject(Auth0Service) private auth0: Auth0Service
  ) {
    this.initializeAuth();
    
    // Expose auth service globally for debugging (development only)
    if (typeof window !== 'undefined' && !this.environmentService.isProduction) {
      (window as any).authService = this;
    }
  }
  
  /**
   * Initialize auth flow by checking session storage and setting up Auth0
   */
  private initializeAuth(): void {
    // **FIRST: Always check for approval parameters before anything else**
    this.checkForApprovalParameters();
    
    // Log if we found approval data
    const pendingApproval = this.getPendingApprovalRequest();
    
    // First try to restore from session if possible
    this.restoreUserFromSession();
    
    // Then setup Auth0 listener for authentication state changes
    if (this.auth0) {
      this.setupAuth0Listeners();
    }
  }
  
  /**
   * Setup Auth0 listeners for auth state changes
   */
  private setupAuth0Listeners(): void {
    // Listen for authentication state changes
    this.auth0.isAuthenticated$.pipe(
      distinctUntilChanged()
    ).subscribe(isAuthenticated => {
      if (isAuthenticated) {
        // Prevent multiple simultaneous authentication attempts
        if (this.authenticationInProgress) {
          return;
        }
        
        this.handleAuthentication();
      } else {
        // If not authenticated and we have a user, clear it
        if (this.userSubject.value) {
          this.clearUserState();
        }
      }
    });
  }

  /**
   * Handle authentication in a single, controlled flow
   */
  private async handleAuthentication(): Promise<void> {
    if (this.authenticationInProgress) {
      await this.authenticationPromise;
      return;
    }

    this.authenticationInProgress = true;
    this.authLoadingSubject.next(true);

    this.authenticationPromise = new Promise<User | null>(async (resolve) => {
      try {
        // Get user info from Auth0
        const auth0User = await firstValueFrom(
          this.auth0.user$.pipe(
            filter(user => !!user),
            take(1),
            timeout(10000)
          )
        );

        if (!auth0User?.sub) {
          throw new Error('No auth0 user ID found');
        }
        
        // Check if this is from a callback (genuine new login) or from refresh
        const currentPath = this.router.url;
        this.isGenuineNewLogin = currentPath.includes('/auth/callback') || currentPath.includes('/auth/loading');
        
        // Fetch user profile
        const user = await this.fetchUserProfileAsync(auth0User);
        
        if (user) {
          this.processUserProfile(user);
          resolve(user);
        } else {
          throw new Error('Failed to fetch user profile');
        }
      } catch (error) {
        this.handleAuthError('Authentication failed', error);
        resolve(null);
      } finally {
        this.authenticationInProgress = false;
        this.authLoadingSubject.next(false);
        this.authenticationPromise = null;
      }
    });

    await this.authenticationPromise;
  }
  
  /**
   * Restore user from session storage
   */
  private restoreUserFromSession(): void {
    try {
    const storedUser = sessionStorage.getItem('user_profile');
      if (!storedUser) return;
      
        const user = JSON.parse(storedUser);
      
      // Ensure user has roles
        if (!Array.isArray(user.roles) || user.roles.length === 0) {
          user.roles = ['COMPANY_USER'];
        }
      
        this.userSubject.next(user);
        
      // DON'T check status on session restore - let them stay where they are
      // Only do critical status checks, not convenience redirects
      this.checkCriticalStatusOnly(user);
    } catch (error) {
        sessionStorage.removeItem('user_profile');
    }
  }
  
  /**
   * Check only critical status issues without any convenience redirects
   */
  private async checkCriticalStatusOnly(user: User): Promise<void> {
    // Only check for truly critical issues that require immediate action
    try {
      // 1. Critical user status issues
      if (user.status === 'PENDING') {
        this.router.navigate(['/pending-account'], { replaceUrl: true });
        return;
      }
      
      if (user.status === 'DEACTIVATED') {
        this.router.navigate(['/account-deactivated'], { 
          queryParams: { status: user.status },
          replaceUrl: true 
        });
        return;
      }

      // 2. Critical company status issues
      if (user.email) {
        const companyStatus = await this.checkCompanyStatus(user);
        if (companyStatus) {
          this.router.navigate([companyStatus.path], {
            queryParams: companyStatus.queryParams,
            replaceUrl: companyStatus.replaceUrl ?? true
          });
          return;
        }
      }

      // No critical issues - let user stay where they are
    } catch (error) {
    }
  }
  
  /**
   * Fetch user profile from backend API (async version)
   */
  private async fetchUserProfileAsync(auth0User: any): Promise<User | null> {
    const auth0Id = encodeURIComponent(auth0User.sub);
    
    try {
      return await firstValueFrom(
        this.http.get<User>(`${this.environmentService.apiUrl}/users/${auth0Id}`)
          .pipe(
            // Don't retry on 404 as that's expected for new users
            retryWhen(errors => 
              errors.pipe(
                concatMap((error, i) => {
                  // Only retry network errors, not 404s
                  if (error.status === 404) {
                    return throwError(() => error);
                  }
                  if (i >= 3) {
                    return throwError(() => error);
                  }
                  return timer((i + 1) * 500);
                })
              )
            ),
            catchError(error => {
              // If user not found, register them
              if (error.status === 404) {
                return this.registerNewUser(auth0User).pipe(
                  tap(() => {}),
                  catchError(regError => {
                    if (regError.error) {
                      return throwError(() => new Error('Failed to register: ' + (regError.message || regError.statusText || 'Unknown error')));
                    }
                    return throwError(() => error);
                  })
                );
              }
              return throwError(() => error);
            }),
            timeout(10000)
          )
      );
    } catch (error) {
      throw error;
    }
  }
  
  /**
   * Register a new user in the backend
   */
  private registerNewUser(auth0User: any): Observable<User> {
    // Extract domain for potential company matching
    const email = auth0User.email;
    const domain = this.getEmailDomain(email);
    
    // Ensure auth0Id is properly set
    const auth0Id = auth0User.sub;
    if (!auth0Id) {
      return throwError(() => new Error('Missing auth0Id'));
    }
    
    const userData = {
      auth0Id: auth0Id,
      email: auth0User.email,
      name: auth0User.name || '',
      firstName: auth0User.given_name || '',
      lastName: auth0User.family_name || '',
      picture: auth0User.picture || '',
      provider: auth0Id.startsWith('google-oauth2|') ? 'Google' : 'Auth0',
      customerGoogleId: auth0Id.startsWith('google-oauth2|') ? 
        auth0Id.substring('google-oauth2|'.length) : undefined,
      primaryDomain: domain
    };
    
    return this.http.post<User>(
      `${this.environmentService.apiUrl}/users/register`, 
      userData
    ).pipe(
      tap(user => {}),
      catchError(error => {
        if (error.error) {
          return throwError(() => error);
        }
        return throwError(() => error);
      })
    );
  }
  
  /**
   * Process the user profile after fetching
   */
  private processUserProfile(user: User | null): void {
    if (!user) {
      this.handleAuthError('No user profile returned', null);
      return;
    }
    
    // Check if user has roles
    if (!Array.isArray(user.roles)) {
      user.roles = [];
    }
    
    // For newly registered users with no roles, implement retry mechanism
    if (user.roles.length === 0 && this.isGenuineNewLogin) {
      this.handleNewUserWithRetries(user);
      return;
    }
    
    // Do not automatically add roles - respect what comes from the backend
    // Only log the user status for debugging
    
    // Update state and storage
    this.userSubject.next(user);
    this.saveUserToSession(user);
    
    // Only log authentication if this is a genuine new login (not session restore/refresh)
    if (this.isGenuineNewLogin) {
    this.logAuthentication(user);
      this.isGenuineNewLogin = false; // Reset flag after use
    } else {
    }
    
    // Handle post-authentication navigation
    this.handlePostAuthNavigation(user);
  }
  
  /**
   * Handle new user with retry mechanism to wait for role assignment
   */
  private async handleNewUserWithRetries(user: User): Promise<void> {
    const maxRetries = 4;
    const retryDelays = [2000, 3000, 4000, 6000]; // 2s, 3s, 4s, 6s = total 15 seconds
    
    // Update state immediately so user doesn't see empty state
    this.userSubject.next(user);
    this.saveUserToSession(user);
    
    // Log authentication for new login
    this.logAuthentication(user);
    this.isGenuineNewLogin = false;
    
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      const delay = retryDelays[attempt];
      
      // Wait for the specified delay
      await new Promise(resolve => setTimeout(resolve, delay));
      
      try {
        // Refresh user profile
        const auth0User = await firstValueFrom(
          this.auth0.user$.pipe(
            filter(user => !!user),
            take(1),
            timeout(5000)
          )
        );
        
        if (auth0User?.sub) {
          const refreshedUser = await this.fetchUserProfileAsync(auth0User);
          
          if (refreshedUser && refreshedUser.roles && refreshedUser.roles.length > 0) {
            // Update state with refreshed user
            this.userSubject.next(refreshedUser);
            this.saveUserToSession(refreshedUser);
            
            // Handle navigation with the updated user
            this.handlePostAuthNavigation(refreshedUser);
            return;
          } else {
            // If user status is explicitly set to PENDING, stop retrying
            if (refreshedUser?.status === 'PENDING') {
              this.userSubject.next(refreshedUser);
              this.saveUserToSession(refreshedUser);
              this.router.navigate(['/pending-account'], { replaceUrl: true });
              return;
            }
          }
        }
      } catch (error) {
      }
    }
    
    // All retries exhausted
    this.router.navigate(['/pending-account'], { replaceUrl: true });
  }
  
  /**
   * Log successful authentication to backend
   */
  private logAuthentication(user: User): void {
    try {
      const authData = {
        email: user.email,
        auth0Id: user.auth0Id,
        name: user.name,
        firstName: user.firstName,
        lastName: user.lastName,
        picture: user.picture,
        roles: user.roles,
        status: user.status,
        companyId: user.companyId,
        companyStatus: user.companyStatus,
        companyName: user.companyName
      };
      
      this.http.post(`${this.environmentService.apiUrl}/auth0/log-authentication`, authData)
        .subscribe({
          next: () => {
          },
          error: (error) => {
          }
        });
    } catch (error) {
    }
  }
  
  /**
   * Handle navigation after successful authentication
   */
  private handlePostAuthNavigation(user: User): void {
    // **FIRST PRIORITY: Check for pending approval requests - ALWAYS**
    const pendingApproval = this.getPendingApprovalRequest();
    
    if (pendingApproval) {
      // Navigate to approval page with parameters
      const approvalUrl = `${pendingApproval.originalPath}?requestId=${pendingApproval.requestId}&email=${pendingApproval.email}`;
      
      // DON'T clear the pending request yet - let the approval page handle it
      // This ensures if there are redirect issues, we still have the data
      
      this.router.navigateByUrl(approvalUrl, { replaceUrl: true });
      return;
    } else {
    }
    
    // **Don't redirect if we're already on an approval URL**
    const currentUrl = this.router.url;
    const approvalUrls = ['/approve-license', '/confirm-purchase', '/purchase/accept', '/accept-purchase'];
    const isOnApprovalUrl = approvalUrls.some(url => currentUrl.startsWith(url));
    
    if (isOnApprovalUrl) {
      return;
    }
    
    // Check if there's a stored target URL from the auth guard
    const targetUrl = sessionStorage.getItem('auth_target_url');
    
    // **Only handle navigation if we're on the loading page OR root page**
    if (currentUrl !== '/auth/loading' && currentUrl !== '/' && currentUrl !== '') {
      return;
    }
    
    if (targetUrl) {
      sessionStorage.removeItem('auth_target_url');
      
      // Don't redirect to auth-related or status pages
      if (!this.isExcludedPath(targetUrl)) {
        this.router.navigateByUrl(targetUrl, { replaceUrl: true });
        return;
      }
    }
    
    // Check for critical status issues first
    this.checkCriticalStatusOnly(user);
  }
  
  /**
   * Handle authentication errors
   */
  private handleAuthError(message: string, error: any): void {
    this.authErrorSubject.next(message);
    this.authLoadingSubject.next(false);
    
    // Log failed authentication attempt
    this.logAuthenticationFailure(message, error);
    
    this.clearUserState();
  }

  /**
   * Log failed authentication to backend
   */
  private logAuthenticationFailure(reason: string, error: any): void {
    try {
      const failureData = {
        email: null, // We might not have email on failure
        reason: `${reason}: ${error?.message || 'Unknown error'}`
      };
      
      this.http.post(`${this.environmentService.apiUrl}/auth0/log-authentication-failure`, failureData)
        .subscribe({
          next: () => {
          },
          error: (logError) => {
          }
        });
    } catch (error) {
    }
  }
  
  /**
   * Clear user state (logout without redirect)
   */
  private clearUserState(): void {
          this.userSubject.next(null);
          sessionStorage.removeItem('user_profile');
    sessionStorage.removeItem('auth_target_url');
  }
  
  /**
   * Save user to session storage
   */
  private saveUserToSession(user: User): void {
    sessionStorage.setItem('user_profile', JSON.stringify(user));
  }
  
  /**
   * Check user status and handle redirects ONLY for critical issues
   */
  private checkUserStatus(user: User): void {
    // Skip checks for specific paths
    const currentPath = this.router.url;
    if (this.isExcludedPath(currentPath)) {
      return;
    }

    // Add debounce mechanism to prevent multiple redirects
    if (this.isRedirectInProgress) {
      return;
    }

    this.isRedirectInProgress = true;
    
    // Only check for CRITICAL redirects (status/company issues) - NO role-based redirects
    this.checkCriticalUserIssues(user).then(redirectResult => {
      this.isRedirectInProgress = false;
      
      if (!redirectResult) {
        return;
      }
      
      // If already on the correct page, don't redirect
      if (currentPath === redirectResult.path) {
        return;
      }
      
      this.router.navigate([redirectResult.path], {
        queryParams: redirectResult.queryParams,
        replaceUrl: redirectResult.replaceUrl ?? true
      });
    }).catch(error => {
    });
  }

  /**
   * Check ONLY for critical issues that require immediate redirection
   */
  private async checkCriticalUserIssues(user: User): Promise<RedirectResult | null> {
    if (!user) return null;
    
    try {
      // 1. Critical user status issues
      if (user.status === 'PENDING') {
        return { path: '/pending-account', replaceUrl: true };
      }
      
      if (user.status === 'DEACTIVATED') {
        return { 
          path: '/account-deactivated', 
          queryParams: { status: user.status },
          replaceUrl: true 
        };
      }

      // 2. Critical company status issues
      if (user.email) {
        const companyStatus = await this.checkCompanyStatus(user);
        if (companyStatus) {
          return companyStatus;
        }
      }

      // No critical issues found
      return null;
    } catch (error) {
      return null;
    }
  }

  /**
   * Check if path is excluded from status checks
   */
  private isExcludedPath(path: string): boolean {
    return path.includes('/auth/') ||
           path.includes('/login') ||
           path.includes('/sign-up') ||
           path.includes('/callback') ||
           path.includes('/company-inactive') ||
           path.includes('/company-not-registered') ||
           path.includes('/pending-account') ||
           path.includes('/account-deactivated') ||
           path.includes('/logout') ||
           path.includes('/error');
  }
  
  /**
   * Determine where user should be redirected based on status
   */
  private async determineUserRedirect(user: User): Promise<RedirectResult | null> {
    if (!user) return null;
    
    // **CHECK FOR APPROVAL URLS FIRST - Don't redirect away from these**
    const currentUrl = this.router.url;
    const approvalUrls = ['/approve-license', '/confirm-purchase', '/purchase/accept', '/accept-purchase'];
    const isOnApprovalUrl = approvalUrls.some(url => currentUrl.startsWith(url));
    
    if (isOnApprovalUrl) {
      return null; // Don't redirect, stay on current page
    }
    
    try {
      // 1. Check company status FIRST (most important check)
      if (user.email) {
        const companyStatus = await this.checkCompanyStatus(user);
        if (companyStatus) {
          return companyStatus;
        }
      }
      
      // 2. Check user status SECOND - Only if we don't have a company status redirect
      if (user.status === 'PENDING') {
        return { path: '/pending-account', replaceUrl: true };
      }
      
      if (user.status === 'DEACTIVATED') {
        return { 
          path: '/account-deactivated', 
          queryParams: { status: user.status },
          replaceUrl: true 
        };
      }
      
      // 3. Role-based default redirects
      return this.getRoleBasedRedirect(user.roles);
    } catch (error) {
      return null;
    }
  }
  
  /**
   * Check company status and return redirect if needed
   */
  private async checkCompanyStatus(user: User): Promise<RedirectResult | null> {
    if (!user.email) return null;
    
    const domain = this.getEmailDomain(user.email);
    if (!domain) return null;
    
    // Add a timestamp check to prevent frequent rechecks
    const lastCheckKey = `company_status_check_${domain}`;
    const lastCheckTime = parseInt(sessionStorage.getItem(lastCheckKey) || '0', 10);
    const currentTime = Date.now();
    const checkThreshold = 30000; // 30 seconds
    
    if (currentTime - lastCheckTime < checkThreshold) {
      // Return the cached redirect if available
      const cachedRedirectKey = `company_redirect_${domain}`;
      const cachedRedirect = sessionStorage.getItem(cachedRedirectKey);
      if (cachedRedirect) {
        try {
          return JSON.parse(cachedRedirect);
        } catch (e) {
          // If parsing fails, continue with the check
        }
      }
    }
    
    // Update the last check timestamp
    sessionStorage.setItem(lastCheckKey, currentTime.toString());
    
    try {
      // First check if user has already been identified as part of an unregistered company
      if (user.companyStatus === 'NOT_FOUND' || user.companyInfo?.status === 'NOT_FOUND') {
        const redirect = {
          path: '/company-not-registered',
          queryParams: { domain },
          replaceUrl: true
        };
        sessionStorage.setItem(`company_redirect_${domain}`, JSON.stringify(redirect));
        return redirect;
      }
      
      // Next check if user has an inactive company
      if (this.hasInactiveCompany(user)) {
        const companyStatus = user.company?.status || user.companyStatus || 'DEACTIVATED';
        
        // Route to different components based on status
        const redirectPath = companyStatus === 'SUSPENDED' ? '/company-suspended' : '/company-inactive';
        
        const redirect = {
          path: redirectPath,
          queryParams: { 
            status: companyStatus,
            company: user.company?.name || user.companyName || ''
          },
          replaceUrl: true
        };
        sessionStorage.setItem(`company_redirect_${domain}`, JSON.stringify(redirect));
        return redirect;
      }
      
      // If no status found on user object, fetch company by domain
      const companyInfo = await this.fetchCompanyByDomain(domain);
      
      if (!companyInfo) {
        return null;
      }
      
      if (companyInfo.status === 'NOT_FOUND') {
        const redirect = {
          path: '/company-not-registered',
          queryParams: { domain },
          replaceUrl: true
        };
        sessionStorage.setItem(`company_redirect_${domain}`, JSON.stringify(redirect));
        return redirect;
      }
      
      if (this.INACTIVE_STATUSES.includes(companyInfo.status)) {
        // Route to different components based on status
        const redirectPath = companyInfo.status === 'SUSPENDED' ? '/company-suspended' : '/company-inactive';
        
        const redirect = {
          path: redirectPath,
          queryParams: { 
            status: companyInfo.status,
            company: companyInfo.name
          },
          replaceUrl: true
        };
        sessionStorage.setItem(`company_redirect_${domain}`, JSON.stringify(redirect));
        return redirect;
      }
      
      // Company is active, clear any cached redirect
      sessionStorage.removeItem(`company_redirect_${domain}`);
    } catch (error) {
    }
    
    return null;
  }
  
  /**
   * Check if user has inactive company
   */
  private hasInactiveCompany(user: User): boolean {
    // Check direct status property
    if (user.companyStatus && this.INACTIVE_STATUSES.includes(user.companyStatus)) {
      return true;
    }
    
    // Check company object
    if (user.company?.status && this.INACTIVE_STATUSES.includes(user.company.status)) {
      return true;
    }
    
    // Check companyInfo object
    if (user.companyInfo?.status && this.INACTIVE_STATUSES.includes(user.companyInfo.status)) {
      return true;
    }
    
    return false;
  }
  
  /**
   * Public method to get role-based redirect
   */
  public getRoleBasedRedirectPath(roles: UserRole[] = []): string {
    const redirect = this.getRoleBasedRedirect(roles);
    return redirect.path;
  }
  
  /**
   * Get default redirect based on user role
   */
  private getRoleBasedRedirect(roles: UserRole[] = []): RedirectResult {
    // If user has no roles, redirect to pending account page
    if (!roles || roles.length === 0) {
      return { path: '/pending-account', replaceUrl: true };
    }
    
    // Provide role-based defaults
    if (roles.includes('SYSTEM_ADMIN')) {
      return { path: '/companies' };
    } 
    
    if (roles.includes('COMPANY_ADMIN')) {
      return { path: '/users' };
    }
    
    return { path: '/requests' };
  }
  
  /**
   * Extract domain from email
   */
  private getEmailDomain(email: string): string | null {
    if (!email || !email.includes('@')) return null;
    return email.split('@')[1];
  }

  /**
   * Fetch company information by domain
   */
  private async fetchCompanyByDomain(domain: string): Promise<CompanyStatusResult | null> {
    if (!domain) return null;
    
    // Check cache first
    const cachedResult = this.companyStatusCache.get(domain);
    if (cachedResult) {
      return cachedResult;
    }
    
    try {
      // Try primary endpoint
      const response = await firstValueFrom(
        this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies`).pipe(
          catchError(error => {
            // Fallback to alternative endpoint
            if (error.status === 404) {
              return this.http.get<any>(`${this.environmentService.apiUrl}/companies`);
            }
            return throwError(() => error);
          }),
          catchError(() => of(null))
        )
      );
      
      if (!response) {
        const result = { status: 'NOT_FOUND', name: '', domain };
        this.cacheCompanyStatus(domain, result);
        return result;
      }
      
      // Extract companies array
      const companies = Array.isArray(response) 
        ? response 
        : response?.companies || response?.content || [];
      
      if (!companies || companies.length === 0) {
        const result = { status: 'NOT_FOUND', name: '', domain };
        this.cacheCompanyStatus(domain, result);
        return result;
      }
      
      // Find matching company
      const company = this.findCompanyByDomain(companies, domain);
      
      if (company) {
        const status = company.status || 
                      (company.customFields && company.customFields.status) || 
                      'ACTIVE';
        
        const result = {
          status: status,
          name: company.name || ''
        };
        
        this.cacheCompanyStatus(domain, result);
        return result;
      }
      
      const notFoundResult = { status: 'NOT_FOUND', name: '', domain };
      this.cacheCompanyStatus(domain, notFoundResult);
      return notFoundResult;
    } catch (error) {
      // If there's an error, don't assume company is not found, just return null
      return null;
    }
  }

  /**
   * Find company by domain in company list
   */
  private findCompanyByDomain(companies: any[], domain: string): any {
    if (!domain) return null;
    
    // Special case for cloudmen.net domain
    if (domain.toLowerCase() === 'cloudmen.net') {
      const cloudmenCompany = companies.find(c => 
        c.name === 'CLOUDMEN' || 
        (c.contactInfo && c.contactInfo.some((contact: any) => 
          contact.value && contact.value.includes('@cloudmen.net')
        ))
      );
      
      if (cloudmenCompany) return cloudmenCompany;
      
      const activeCompany = companies.find(c => c.status === 'ACTIVE');
      if (activeCompany) return activeCompany;
    }
    
    // Try to match by primaryDomain
    let match = companies.find(c => 
      c.primaryDomain && c.primaryDomain.toLowerCase() === domain.toLowerCase()
    );
    
    if (match) return match;
    
    // Try to match by contactInfo emails
    match = companies.find(c => {
      // Check contactInfo array
      if (c.contactInfo && Array.isArray(c.contactInfo)) {
        return c.contactInfo.some((contact: any) => {
          if (!contact.value && !contact.email) return false;
          
          const email = contact.value || contact.email;
          const contactDomain = this.getEmailDomain(email);
          return domain.toLowerCase() === (contactDomain || '').toLowerCase();
        });
      }
      
      // Check email property
      if (c.email) {
        const companyDomain = this.getEmailDomain(c.email);
        return domain.toLowerCase() === (companyDomain || '').toLowerCase();
      }
      
      // Check contactEmails array
      if (c.contactEmails && Array.isArray(c.contactEmails)) {
        return c.contactEmails.some((email: string) => {
          const contactDomain = this.getEmailDomain(email);
          return domain.toLowerCase() === (contactDomain || '').toLowerCase();
        });
      }
      
      return false;
    });
    
    return match;
  }
  
  /**
   * Cache company status result for a domain
   */
  private cacheCompanyStatus(domain: string, result: CompanyStatusResult): void {
    this.companyStatusCache.set(domain, result);
    
    // Set expiration for cache entry
    setTimeout(() => {
      this.companyStatusCache.delete(domain);
    }, this.cacheTTL);
  }
  
  /* PUBLIC API METHODS */
  
  /**
   * Handle Auth0 callback
   */
  handleAuthCallback(): void {
    if (!this.auth0) return;
    
    this.authLoadingSubject.next(true);
    
    // Navigate to loading page immediately
    this.router.navigate(['/auth/loading'], { replaceUrl: true });
    
    // Clear any previous errors
    this.authErrorSubject.next(null);
    sessionStorage.removeItem('auth_error');
    
    // Process the callback
    this.auth0.handleRedirectCallback().subscribe({
      next: result => {
        if (result.appState?.target) {
          sessionStorage.setItem('auth_target_url', result.appState.target);
        }
        // Auth0 will trigger the isAuthenticated$ observable, which will handle profile loading
      },
      error: error => {
        // If there's a state error, this is often due to browser cache or multiple logins
        if (error.message?.includes('state')) {
          this.auth0.isAuthenticated$.pipe(
            take(1),
            timeout(5000)
          ).subscribe({
            next: isAuthenticated => {
              if (isAuthenticated) {
                this.auth0.user$.pipe(
                  filter(user => !!user),
                  take(1),
                  timeout(5000)
                ).subscribe({
                  next: auth0User => {
                    this.handleAuthentication();
                  },
                  error: profileError => {
                    this.authLoadingSubject.next(false);
                    this.handleInvalidStateError();
                  }
                });
              } else {
                this.authLoadingSubject.next(false);
                this.handleInvalidStateError();
              }
            },
            error: timeoutError => {
              this.authLoadingSubject.next(false);
              this.handleInvalidStateError();
            }
          });
        } else {
          // For non-state errors, handle differently
          this.authLoadingSubject.next(false);
          sessionStorage.setItem('auth_error', JSON.stringify({
            message: error.message || 'Unknown authentication error',
            timestamp: new Date().toISOString()
          }));
          
          this.router.navigate(['/auth/error'], { 
            queryParams: { error: 'authentication_failed' },
            replaceUrl: true 
          });
        }
      }
    });
  }
  
  /**
   * Handle invalid state error by clearing session and redirecting
   */
  private handleInvalidStateError(): void {
    // Get the target URL BEFORE clearing everything
    const targetUrl = sessionStorage.getItem('auth_target_url');
    
    // Clear any lingering state from both sessionStorage and localStorage
    sessionStorage.removeItem('auth_state');
    localStorage.removeItem('auth0.is.authenticated');
    
    // Clear Auth0 cache but preserve target URL
    try {
      // Clear Auth0's internal state
      if (this.auth0) {
        // Auth0 doesn't have a clear method, but we can clear localStorage keys
        Object.keys(localStorage).forEach(key => {
          if (key.startsWith('@@auth0spajs@@')) {
            localStorage.removeItem(key);
          }
        });
      }
    } catch (error) {
    }
    
    // Restore target URL if it existed
    if (targetUrl) {
      sessionStorage.setItem('auth_target_url', targetUrl);
    }
    
    // Store error but don't display yet
    sessionStorage.setItem('auth_error', JSON.stringify({
      message: 'Authentication session expired or invalid',
      timestamp: new Date().toISOString(),
      recoverable: true
    }));
    
    // The user is already authenticated despite the state error
  }
  
  /**
   * Start login flow
   */
  login(): void {
    if (!this.auth0) return;
    
    this.authLoadingSubject.next(true);
    
    // Mark this as a genuine new login attempt
    this.isGenuineNewLogin = true;
    
    // Store the complete current URL including query parameters
    const currentUrl = window.location.pathname + window.location.search;
    
    this.auth0.loginWithRedirect({
      appState: { target: currentUrl }
    });
  }

  /**
   * Logout user
   */
  logout(): void {
    if (!this.auth0) return;
    
    this.clearUserState();
    
    this.auth0.logout({
      logoutParams: {
        returnTo: window.location.origin
      }
    });
  }

  /**
   * Get access token for API calls
   */
  getAccessToken(): Observable<string> {
    if (!this.auth0) return of('');
    return this.auth0.getAccessTokenSilently();
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): Observable<boolean> {
    if (!this.auth0) return of(false);
    return this.auth0.isAuthenticated$;
  }

  /**
   * Get user roles
   */
  getUserRoles(): UserRole[] {
    const user = this.userSubject.value;
    if (!user || !user.roles || user.roles.length === 0) {
      return ['COMPANY_USER'];
    }
    return user.roles;
  }

  /**
   * Get primary user role
   */
  getUserRole(): UserRole {
    return this.getUserRoles()[0];
  }

  /**
   * Get current user
   */
  getCurrentUser(): User | null {
    return this.userSubject.value;
  }

  /**
   * Check if user has specific role
   */
  hasRole(role: UserRole): boolean {
    return this.getUserRoles().includes(role);
  }

  /**
   * Force refresh user profile
   */
  refreshUserProfile(): void {
    sessionStorage.removeItem('user_profile');
    
    // Use the new authentication flow instead of the old method
    this.handleAuthentication();
  }

  /**
   * Check if navigation to URL should be allowed
   * Used by guards to control route access
   */
  canNavigateToUrl(url: string): Observable<boolean> {
    return this.user$.pipe(
      // Only proceed when we have a user or know for sure we don't
      filter(user => user !== undefined),
      take(1),
      switchMap(user => {
        if (!user) {
          return of(false);
        }
        
        // For excluded paths, always allow
        if (this.isExcludedPath(url)) {
          return of(true);
        }
        
        // Only check for CRITICAL redirects (not convenience redirects)
        // This prevents unwanted redirects on page refresh
        return from(this.checkCriticalUserIssues(user)).pipe(
          map(redirectResult => {
            if (!redirectResult) {
              // No critical issues - allow navigation
              return true;
            }
            
            // If the URL matches the critical redirect target, allow it
            if (url === redirectResult.path) {
              return true;
            }
            
            // Critical issue found - redirect to appropriate status page
            this.router.navigate([redirectResult.path], {
              queryParams: redirectResult.queryParams,
              replaceUrl: redirectResult.replaceUrl ?? true
            });
      
            return false;
          })
        );
      })
    );
  }

  /**
   * Check for approval parameters and store them for later processing
   */
  private checkForApprovalParameters(): void {
    const currentUrl = window.location.href;
    const url = new URL(currentUrl);
    
    // Check if this is an approval URL
    const isApprovalPath = url.pathname.includes('/approve-license') || 
                          url.pathname.includes('/confirm-purchase') || 
                          url.pathname.includes('/purchase/accept') ||
                          url.pathname.includes('/accept-purchase');
    
    if (isApprovalPath) {
      const requestId = url.searchParams.get('requestId');
      const email = url.searchParams.get('email');
      
      if (requestId && email) {
        const approvalData = {
          requestId,
          email,
          originalPath: url.pathname,
          timestamp: Date.now()
        };
        
        localStorage.setItem(this.APPROVAL_STORAGE_KEY, JSON.stringify(approvalData));
        
        // Store in session storage as backup
        sessionStorage.setItem(this.APPROVAL_STORAGE_KEY, JSON.stringify(approvalData));
      }
    }
  }

  /**
   * Check if there's a pending approval request
   */
  private getPendingApprovalRequest(): any {
    try {
      // Check localStorage first, then sessionStorage
      let storedData = localStorage.getItem(this.APPROVAL_STORAGE_KEY);
      if (!storedData) {
        storedData = sessionStorage.getItem(this.APPROVAL_STORAGE_KEY);
      }
      
      if (storedData) {
        const approvalData = JSON.parse(storedData);
        
        // Check if the approval request is not too old (1 hour max)
        const maxAge = 60 * 60 * 1000; // 1 hour
        if (Date.now() - approvalData.timestamp > maxAge) {
          this.clearPendingApprovalRequest();
          return null;
        }
        
        return approvalData;
      }
    } catch (error) {
      this.clearPendingApprovalRequest();
    }
    
    return null;
  }

  /**
   * Clear pending approval request
   */
  private clearPendingApprovalRequest(): void {
    localStorage.removeItem(this.APPROVAL_STORAGE_KEY);
    sessionStorage.removeItem(this.APPROVAL_STORAGE_KEY);
  }

  /**
   * Clear pending approval request (should only be called by approval components after successful processing)
   */
  public clearProcessedApprovalRequest(): void {
    this.clearPendingApprovalRequest();
  }

  /**
   * Get pending approval request (public method for components)
   */
  public getPendingApprovalForProcessing(): any {
    return this.getPendingApprovalRequest();
  }

  /**
   * Clear company status cache and refresh user profile
   * Useful when company status changes and immediate update is needed
   */
  public clearCompanyStatusCache(): void {
    this.companyStatusCache.clear();
    
    // Also refresh the user profile to get latest data
    this.refreshUserProfile();
  }
  
  /**
   * Clear cache for a specific domain
   */
  public clearDomainCache(domain: string): void {
    this.companyStatusCache.delete(domain);
  }

  /**
   * Force check company status for current user, bypassing cache
   */
  public async forceCheckCompanyStatus(): Promise<void> {
    const currentUser = this.getCurrentUser();
    if (!currentUser || !currentUser.email) {
      return;
    }
    
    const domain = this.getEmailDomain(currentUser.email);
    if (!domain) {
      return;
    }
    
    // Clear cache for this domain first
    this.clearDomainCache(domain);
    
    // Check company status
    const companyStatus = await this.checkCompanyStatus(currentUser);
    if (companyStatus) {
      this.router.navigate([companyStatus.path], {
        queryParams: companyStatus.queryParams,
        replaceUrl: companyStatus.replaceUrl ?? true
      });
    }
  }

  /**
   * Debug method to manually test status changes (for development only)
   * Usage in browser console: window.authService.testStatusChange('SUSPENDED')
   */
  public testStatusChange(newStatus: 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED'): void {
    if (typeof window !== 'undefined') {
      // Clear all caches first
      this.clearCompanyStatusCache();
      
      // Simulate the status change by temporarily modifying the user object
      const currentUser = this.getCurrentUser();
      if (currentUser) {
        // Create a test company status
        if (currentUser.company) {
          currentUser.company.status = newStatus;
        } else {
          currentUser.company = { name: 'Test Company', status: newStatus };
        }
        
        // Update the user subject to trigger status checks
        this.userSubject.next(currentUser);
        
        // Force a status check
        this.forceCheckCompanyStatus();
      }
    }
  }
}