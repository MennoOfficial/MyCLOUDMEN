import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NavbarComponent } from '../../shared/components/navbar/navbar.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, NavbarComponent],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss']
})
export class MainLayoutComponent implements OnInit {
  userRole: 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER' = 'COMPANY_USER';
  sidebarExpanded = false;
  
  constructor(private authService: AuthService) {}
  
  ngOnInit(): void {
    // Get the user role from the auth service
    this.userRole = this.authService.getUserRole();
    
    // Subscribe to user changes to update role if it changes
    this.authService.user$.subscribe(user => {
      if (user && user.roles && user.roles.length > 0) {
        this.userRole = user.roles[0];
      }
    });
  }
  
  onMenuToggled(expanded: boolean): void {
    this.sidebarExpanded = expanded;
  }
}
