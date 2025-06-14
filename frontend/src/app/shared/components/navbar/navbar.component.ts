import { Component, Input, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/models/auth.model';
import { BehaviorSubject, Subject, takeUntil, Observable, map } from 'rxjs';
import { EnvironmentService } from '../../../core/services/environment.service';
import { LoadingSpinnerComponent } from '../loading-spinner/loading-spinner.component';
import { ApiService } from '../../../core/services/api.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, LoadingSpinnerComponent],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit, OnDestroy {
  @Input() set userRole(role: 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER') {
    this._userRole$.next(role);
  }
  get userRole(): 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' {
    return this._userRole$.value;
  }
  
  private _userRole$ = new BehaviorSubject<'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER'>('COMPANY_USER');
  private destroy$ = new Subject<void>();
  
  @Output() menuToggled = new EventEmitter<boolean>();
  
  isMenuOpen = false;
  showLogoutModal = false;
  profileImageLoading = true;
  isUserLoading = true;

  // Company information
  userCompany: string | null = null;
  isLoadingCompany = false;

  // Create observable streams
  user$: Observable<User | null>;
  
  // Navigation items based on user role
  systemAdminNavItems = [
    { label: 'Companies', route: '/companies', icon: 'business' },
    { label: 'User logs', route: '/auth-logs', icon: 'security' }
  ];
  
  companyAdminNavItems = [
    { label: 'User management', route: '/users', icon: 'people' },
    { label: 'Invoice management', route: '/invoices', icon: 'receipt' },
    { label: 'Purchase Requests', route: '/requests', icon: 'shopping_cart' }
  ];
  
  companyUserNavItems = [
    { label: 'Purchase Requests', route: '/requests', icon: 'shopping_cart' }
  ];
  
  navItems: any[] = [];
  
  constructor(
    private authService: AuthService,
    private environmentService: EnvironmentService,
    private apiService: ApiService
  ) {
    // Initialize user$ stream
    this.user$ = this.authService.user$.pipe(
      takeUntil(this.destroy$),
      map(user => {
        this.isUserLoading = false;
        if (user) {
          // Update role if available
          const userRole = user.roles?.[0];
          if (userRole) {
            this._userRole$.next(userRole as 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER');
          }
          // Preload profile image if available
          if (user.picture) {
            this.preloadProfileImage(user.picture);
          } else {
            this.profileImageLoading = false;
          }
          
          // Get company information for non-system admins
          if (user.email && userRole !== 'SYSTEM_ADMIN') {
            this.fetchUserCompany(user.email);
          } else {
            this.userCompany = null;
          }
        } else {
          this.profileImageLoading = false;
          this.userCompany = null;
        }
        return user;
      })
    );

    // Subscribe to role changes to update nav items
    this._userRole$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.setNavItems());
  }
  
  ngOnInit(): void {
    // Set initial navigation items
    this.setNavItems();
    
    // Subscribe to user$ to ensure the stream is active
    this.user$.subscribe();
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
  
  setNavItems(): void {
    const currentRole = this._userRole$.value;
    this.navItems = [];
    
    if (currentRole === 'SYSTEM_ADMIN') {
      this.navItems.push(...this.systemAdminNavItems);
    }
    
    if (currentRole === 'COMPANY_ADMIN') {
      this.navItems.push(...this.companyAdminNavItems);
    }
    
    if (currentRole === 'COMPANY_USER' || !currentRole) {
      this.navItems.push(...this.companyUserNavItems);
    }
    
    // Remove duplicates
    this.navItems = this.navItems.filter((item, index, self) => 
      index === self.findIndex((t) => t.route === item.route)
    );
  }
  
  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
    this.menuToggled.emit(this.isMenuOpen);
  }
  
  handleLogout(): void {
    this.showLogoutModal = true;
  }
  
  closeLogoutModal(): void {
    this.showLogoutModal = false;
  }
  
  confirmLogout(): void {
    // Use Auth0 logout
    this.authService.logout();
    this.showLogoutModal = false;
  }
  
  // Format role for display
  formatRole(role: string): string {
    switch(role) {
      case 'SYSTEM_ADMIN':
        return 'System Admin';
      case 'COMPANY_ADMIN':
        return 'Company Admin';
      case 'COMPANY_USER':
        return 'Company User';
      default:
        return role.replace('_', ' ');
    }
  }
  
  formatRoleClass(role: string): string {
    return role.toLowerCase().replace('_', '-');
  }
  
  getPrimaryRole(): string {
    return this._userRole$.value || 'COMPANY_USER';
  }
  
  getPrimaryRoleClass(): string {
    const role = this.getPrimaryRole();
    return role.toLowerCase().replace('_', '-');
  }
  
  getUserStatusClass(): string {
    const role = this.getPrimaryRole();
    if (role === 'SYSTEM_ADMIN') return 'admin';
    if (role === 'COMPANY_ADMIN') return 'admin';
    return 'user';
  }
  
  getRoleIcon(role: string): string {
    switch(role) {
      case 'SYSTEM_ADMIN':
        return 'admin_panel_settings';
      case 'COMPANY_ADMIN':
        return 'business';
      case 'COMPANY_USER':
        return 'person';
      default:
        return 'person';
    }
  }
  
  switchRole(role: string): void {
    this._userRole$.next(role as 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER');
    // Store the selected role in session storage for persistence
    const currentUser = this.authService.getCurrentUser();
    if (currentUser) {
      const updatedUser = { 
        ...currentUser, 
        roles: [role as 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER'] 
      };
      sessionStorage.setItem('user_profile', JSON.stringify(updatedUser));
      
      // Force refresh to apply the new role
      window.location.reload();
    }
  }
  
  handleImageError(event: any): void {
    this.profileImageLoading = false;
    // Fallback to default image
    event.target.src = 'https://isobarscience.com/wp-content/uploads/2020/09/default-profile-picture1.jpg';
  }
  
  preloadProfileImage(imageUrl: string): void {
    if (!imageUrl) {
      this.profileImageLoading = false;
      return;
    }
    
    // Check if it's a Google profile image that might have CORS issues
    if (imageUrl.startsWith('https://lh3.googleusercontent.com/') || 
        imageUrl.startsWith('https://s.gravatar.com/')) {
      // Use our proxy endpoint instead of direct access
      // URL encode the image URL
      const encodedUrl = encodeURIComponent(imageUrl);
      const baseUrl = this.environmentService.apiUrl;
      
      imageUrl = `${baseUrl}/proxy/image?url=${encodedUrl}`;
    }
    
    const img = new Image();
    img.onload = () => {
      this.profileImageLoading = false;
    };
    img.onerror = () => {
      this.profileImageLoading = false;
    };
    img.src = imageUrl; // Set src after setting up event handlers
  }

  // Add the encodeURIComponent method to fix the linter error
  encodeURIComponent(url: string): string {
    return encodeURIComponent(url);
  }

  // Get proxy URL for images with CORS issues
  getProxyImageUrl(originalUrl: string): string {
    const encodedUrl = encodeURIComponent(originalUrl);
    return `${this.environmentService.apiUrl}/proxy/image?url=${encodedUrl}`;
  }

  fetchUserCompany(email: string): void {
    this.isLoadingCompany = true;
    const domain = email.split('@')[1];
    
    console.log('Fetching company for domain:', domain);
    
    // Use the correct endpoint from TeamleaderCompanyController to get full company details
    this.apiService.get<any>(`teamleader/companies/domain/${domain}`).subscribe({
      next: (response: any) => {
        console.log('Company API response:', response);
        this.isLoadingCompany = false;
        // Check if we got a valid company response
        if (response && !response.error && response.name) {
          console.log('Found company name:', response.name);
          this.userCompany = response.name;
        } else {
          console.log('No company found, trying search endpoint...');
          // Try alternative: search by domain
          this.searchCompanyByDomain(domain);
        }
      },
      error: (error: any) => {
        console.error('Error fetching company by domain:', error);
        console.log('Trying search endpoint as fallback...');
        // Try alternative endpoint
        this.searchCompanyByDomain(domain);
      }
    });
  }

  private searchCompanyByDomain(domain: string): void {
    // Try searching companies and filter by domain
    this.apiService.get<any>('teamleader/companies').subscribe({
      next: (response: any) => {
        console.log('Companies search response:', response);
        this.isLoadingCompany = false;
        
        if (response && response.companies && Array.isArray(response.companies)) {
          // Look for company with matching domain
          const company = response.companies.find((comp: any) => 
            comp.email && comp.email.includes(domain)
          );
          
          if (company && company.name) {
            console.log('Found company via search:', company.name);
            this.userCompany = company.name;
          } else {
            console.log('No matching company found, using formatted domain');
            this.setFallbackCompanyName(domain);
          }
        } else {
          this.setFallbackCompanyName(domain);
        }
      },
      error: (error: any) => {
        console.error('Error searching companies:', error);
        this.isLoadingCompany = false;
        this.setFallbackCompanyName(domain);
      }
    });
  }

  private setFallbackCompanyName(domain: string): void {
    // Fallback: format domain nicely
    this.userCompany = domain.split('.')[0].charAt(0).toUpperCase() + domain.split('.')[0].slice(1);
    console.log('Using fallback company name:', this.userCompany);
  }
}