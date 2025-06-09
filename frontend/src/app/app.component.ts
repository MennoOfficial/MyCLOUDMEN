import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionTimeoutModalComponent } from './shared/components/session-timeout-modal/session-timeout-modal.component';
import { StatusCheckerService } from './core/services/status-checker.service';
import { AuthService } from './core/auth/auth.service';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SessionTimeoutModalComponent, CommonModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'MyCLOUDMEN';
  private authSubscription: Subscription | null = null;
  
  constructor(
    private statusCheckerService: StatusCheckerService,
    private authService: AuthService
  ) {}
  
  ngOnInit(): void {
    // Start the periodic status checker when user is authenticated
    this.authSubscription = this.authService.isAuthenticated().subscribe(isAuthenticated => {
      if (isAuthenticated) {
        this.statusCheckerService.startPeriodicStatusCheck();
      } else {
        this.statusCheckerService.stopPeriodicStatusCheck();
      }
    });
    
    // Check for approval data in session storage
    const approvalData = sessionStorage.getItem('pendingApproval');
      if (approvalData) {
      // Approval data found
      } else {
      // No approval data found
    }

    // Add global debug function for approval status checking
    (window as any).checkApprovalStatus = () => {
      const data = sessionStorage.getItem('pendingApproval');
      return data ? JSON.parse(data) : null;
    };
  }
  
  ngOnDestroy(): void {
    if (this.authSubscription) {
      this.authSubscription.unsubscribe();
    }
    this.statusCheckerService.stopPeriodicStatusCheck();
  }
}
