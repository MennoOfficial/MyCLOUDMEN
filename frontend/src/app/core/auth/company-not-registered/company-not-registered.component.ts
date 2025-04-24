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
    // Set the flag immediately in constructor to prevent any race conditions
    localStorage.setItem('company_not_registered_viewed', 'true');
    console.log('[CompanyNotRegistered] Flag set in constructor');
  }
  
  ngOnInit(): void {
    // Get domain from query params
    this.subscriptions.add(
      this.route.queryParams.subscribe(params => {
        if (params['domain']) {
          this.domain = params['domain'];
        }
      })
    );
    
    // Also set flag in ngOnInit to ensure it's set
    localStorage.setItem('company_not_registered_viewed', 'true');
    console.log('[CompanyNotRegistered] Flag set in ngOnInit');
    
    // Set up a periodic check to ensure flag stays set
    this.checkFlagInterval = setInterval(() => {
      if (localStorage.getItem('company_not_registered_viewed') !== 'true') {
        console.log('[CompanyNotRegistered] Flag was removed, setting it again');
        localStorage.setItem('company_not_registered_viewed', 'true');
      }
    }, 5000); // Check every 5 seconds
    
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
    localStorage.removeItem('company_not_registered_viewed');
    this.subscriptions.unsubscribe();
    if (this.checkFlagInterval) {
      clearInterval(this.checkFlagInterval);
    }
    console.log('[CompanyNotRegistered] Component destroyed, flag remains set');
  }
  
  contactSupport(): void {
    // Open email client with subject about company registration
    window.location.href = `mailto:info@cloudmen.com?subject=MyCLOUDMEN Registration Request - ${this.domain || 'New Company'}`;
  }
  
  logout(): void {
    this.authService.logout();
  }
} 