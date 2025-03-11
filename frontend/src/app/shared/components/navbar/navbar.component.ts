import { Component, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

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
  
  // Navigation items based on user role
  systemAdminNavItems = [
    { label: 'Companies', route: '/system-admin/companies', icon: 'business' },
    { label: 'User logs', route: '/system-admin/auth-logs', icon: 'security' }
  ];
  
  companyAdminNavItems = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'User management', route: '/company-admin/users', icon: 'people' },
    { label: 'Invoice management', route: '/company-admin/invoices', icon: 'receipt' },
    { label: 'Requests', route: '/company-admin/requests', icon: 'assignment' }
  ];
  
  companyUserNavItems = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Requests', route: '/company-user/requests', icon: 'assignment' }
  ];
  
  navItems: any[] = [];
  
  constructor(private authService: AuthService) {}
  
  ngOnInit(): void {
    // Set navigation items based on user role
    this.setNavItems();
    
    // Subscribe to user changes
    this.authService.user$.subscribe(user => {
      if (user && user.role) {
        this.userRole = user.role;
        this.setNavItems();
      } else {
        this.userRole = 'COMPANY_USER';
        this.setNavItems();
      }
    });
  }
  
  setNavItems(): void {
    // Set navigation items based on user role
    switch(this.userRole) {
      case 'SYSTEM_ADMIN':
        this.navItems = this.systemAdminNavItems;
        break;
      case 'COMPANY_ADMIN':
        this.navItems = this.companyAdminNavItems;
        break;
      case 'COMPANY_USER':
        this.navItems = this.companyUserNavItems;
        break;
    }
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
}