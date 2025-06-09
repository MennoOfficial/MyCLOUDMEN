import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';
import { Subscription, interval } from 'rxjs';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-pending-account',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pending-account.component.html',
  styleUrls: ['./pending-account.component.scss']
})
export class PendingAccountComponent implements OnInit, OnDestroy {
  
  private userSubscription?: Subscription;
  private refreshSubscription?: Subscription;
  private refreshInterval?: any;
  
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}
  
  ngOnInit(): void {
    // Listen for user changes to check if roles have been assigned
    this.userSubscription = this.authService.user$.pipe(
      filter(user => !!user) // Only proceed when we have a user
    ).subscribe(user => {
      if (user && user.roles && user.roles.length > 0) {
        // User now has roles, redirect to appropriate page
        const redirectPath = this.authService.getRoleBasedRedirectPath(user.roles);
        this.router.navigate([redirectPath], { replaceUrl: true });
      }
    });
    
    // Auto-refresh every 30 seconds to check for role updates
    this.refreshInterval = setInterval(() => {
      this.refreshUserProfile();
    }, 30000);
  }
  
  private refreshUserProfile(): void {
    this.authService.refreshUserProfile();
  }
  
  ngOnDestroy(): void {
    if (this.userSubscription) {
      this.userSubscription.unsubscribe();
    }
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }
  
  contactAdmin(): void {
    // Open email client without subject or body
    window.location.href = 'mailto:help@cloudmen.com';
  }
  
  logout(): void {
    this.authService.logout();
  }
  
  onManualRefresh(): void {
    this.refreshUserProfile();
  }
} 