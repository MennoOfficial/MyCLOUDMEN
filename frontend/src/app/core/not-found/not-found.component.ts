import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './not-found.component.html',
  styleUrl: './not-found.component.scss'
})
export class NotFoundComponent {
  constructor(
    private router: Router,
    private authService: AuthService
  ) {}

  goHome(): void {
    const userRoles = this.authService.getUserRoles();
    
    if (!this.authService.getCurrentUser()) {
      // If not authenticated, redirect to login
      this.authService.login();
      return;
    }
    
    // Use role-based navigation
    if (userRoles.includes('SYSTEM_ADMIN')) {
      this.router.navigate(['/system-admin/companies']);
    } else if (userRoles.includes('COMPANY_ADMIN')) {
      this.router.navigate(['/company-admin/users']);
    } else {
      this.router.navigate(['/company-user/requests']);
    }
  }
} 