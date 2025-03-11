import { Component, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';

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
  
  constructor(private router: Router) {}
  
  ngOnInit(): void {
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
    // Clear any stored authentication data
    localStorage.clear();
    sessionStorage.clear();
    
    // Navigate to login page
    this.router.navigate(['/auth/login']);
    this.showLogoutModal = false;
  }
}