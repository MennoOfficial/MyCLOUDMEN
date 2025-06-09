import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';
import { UserRole } from '../../models/auth.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-company-not-registered',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './company-not-registered.component.html',
  styleUrls: ['./company-not-registered.component.scss']
})
export class CompanyNotRegisteredComponent implements OnInit, OnDestroy {
  domain: string = '';
  private subscriptions: Subscription = new Subscription();
  private checkFlagInterval: any;
  
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {
    // Set the flag that user has seen the company not registered page
    sessionStorage.setItem('has_seen_company_not_registered', 'true');
  }
  
  ngOnInit(): void {
    // Ensure the flag is set when component initializes
    sessionStorage.setItem('has_seen_company_not_registered', 'true');
    
    // If the flag disappears for some reason, set it again
    const intervalId = setInterval(() => {
      const flag = sessionStorage.getItem('has_seen_company_not_registered');
      if (!flag) {
        sessionStorage.setItem('has_seen_company_not_registered', 'true');
      }
    }, 1000);

    // Clear interval after 30 seconds to avoid running indefinitely
    setTimeout(() => {
      clearInterval(intervalId);
    }, 30000);
    
    // Get domain from query params
    this.subscriptions.add(
      this.route.queryParams.subscribe(params => {
        if (params['domain']) {
          this.domain = params['domain'];
        }
      })
    );
    
    // Get the current user and ensure their company status is set correctly
    this.authService.user$.subscribe(user => {
      if (user && user.email) {
        // Update user object with NOT_FOUND company status to ensure proper redirects
        const updatedUser = {
          ...user,
          companyStatus: 'NOT_FOUND'
        };
        this.authService['userSubject'].next(updatedUser);
        this.authService['saveUserToSession'](updatedUser);
      }
    });
  }
  
  ngOnDestroy(): void {
    // Keep the flag set even when component is destroyed
    sessionStorage.setItem('has_seen_company_not_registered', 'true');
    this.subscriptions.unsubscribe();
    if (this.checkFlagInterval) {
      clearInterval(this.checkFlagInterval);
    }
  }
  
  contactSupport(): void {
    // Open email client with subject about company registration
    window.location.href = `mailto:info@cloudmen.com?subject=MyCLOUDMEN Registration Request - ${this.domain || 'New Company'}`;
  }
  
  logout(): void {
    this.authService.logout();
  }
} 