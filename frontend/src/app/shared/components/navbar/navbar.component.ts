import { Component, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService, User } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit {
  @Input() userRole: 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' = 'COMPANY_USER';
  @Output() menuToggled = new EventEmitter<boolean>();
  
  isMenuOpen = false;
  showLogoutModal = false;
  user: User | null = null;
  
  // Navigation items based on user role
  systemAdminNavItems = [
    { label: 'Companies', route: '/system-admin/companies', icon: 'business' },
    { label: 'User logs', route: '/system-admin/auth-logs', icon: 'security' }
  ];
  
  companyAdminNavItems = [
    { label: 'User management', route: '/company-admin/users', icon: 'people' },
    { label: 'Invoice management', route: '/company-admin/invoices', icon: 'receipt' },
    { label: 'Requests', route: '/company-admin/requests', icon: 'assignment' }
  ];
  
  companyUserNavItems = [
    { label: 'Requests', route: '/company-user/requests', icon: 'assignment' }
  ];
  
  navItems: any[] = [];
  
  constructor(private authService: AuthService) {}
  
  ngOnInit(): void {
    // Set navigation items based on user role
    this.setNavItems();
    
    console.log('Initial user role:', this.userRole);
    
    // Get the current user
    this.authService.user$.subscribe(user => {
      console.log('Navbar received user:', user);
      this.user = user;
      
      if (user && user.roles && user.roles.length > 0) {
        console.log('Setting user role to:', user.roles[0]);
        this.userRole = user.roles[0];
        this.setNavItems();
      } else {
        console.log('User or user.roles is undefined:', user);
      }
    });
  }
  
  setNavItems(): void {
    this.navItems = [];
    
    // Get all user roles
    const userRoles = this.user?.roles || [];
    
    // Add System Admin items if user has SYSTEM_ADMIN role
    if (userRoles.includes('SYSTEM_ADMIN')) {
      this.navItems.push(...this.systemAdminNavItems);
    }
    
    // Add Company Admin items if user has COMPANY_ADMIN role
    if (userRoles.includes('COMPANY_ADMIN')) {
      this.navItems.push(...this.companyAdminNavItems);
    }
    
    // Add Company User items if user has COMPANY_USER role
    if (userRoles.includes('COMPANY_USER') || userRoles.length === 0) {
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
  
  getPrimaryRole(): string {
    return this.userRole || (this.user?.roles && this.user.roles.length > 0 ? this.user.roles[0] : 'COMPANY_USER');
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
    this.userRole = role as 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER';
    this.setNavItems();
    // If you need to update the role in the auth service:
    this.authService.updateUserRole(role as any);
  }
}