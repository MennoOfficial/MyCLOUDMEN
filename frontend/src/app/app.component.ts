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
    
    // Debug: Add a global function to check approval status
    (window as any).checkApprovalStatus = () => {
      const approvalData = localStorage.getItem('pending_approval_request') || 
                          sessionStorage.getItem('pending_approval_request');
      
      if (approvalData) {
        console.log('✅ Approval data found:', JSON.parse(approvalData));
      } else {
        console.log('❌ No approval data found');
      }
      
      console.log('Current URL:', window.location.href);
      console.log('Session storage auth_target_url:', sessionStorage.getItem('auth_target_url'));
    };
    
    console.log('🔍 Global debug function added: checkApprovalStatus()');
  }
  
  ngOnDestroy(): void {
    if (this.authSubscription) {
      this.authSubscription.unsubscribe();
    }
    this.statusCheckerService.stopPeriodicStatusCheck();
  }
}
