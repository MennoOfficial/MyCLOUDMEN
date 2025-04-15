import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { EnvironmentService } from './environment.service';
import { interval, Subscription } from 'rxjs';
import { UserDTO } from '../models/user.dto';

@Injectable({
  providedIn: 'root'
})
export class StatusCheckerService implements OnDestroy {
  private statusCheckInterval = 60000; // Check every 60 seconds
  private checkSubscription: Subscription | null = null;

  constructor(
    private http: HttpClient,
    private router: Router,
    private environmentService: EnvironmentService
  ) {}

  startPeriodicStatusCheck(): void {
    // Cancel any existing subscription
    this.stopPeriodicStatusCheck();
    
    console.log('Starting periodic status checks');
    
    // Create a new subscription
    this.checkSubscription = interval(this.statusCheckInterval).subscribe(() => {
      this.checkUserStatus();
    });
    
    // Do an initial check immediately
    this.checkUserStatus();
  }

  stopPeriodicStatusCheck(): void {
    if (this.checkSubscription) {
      this.checkSubscription.unsubscribe();
      this.checkSubscription = null;
    }
  }

  private checkUserStatus(): void {
    const currentUser = JSON.parse(sessionStorage.getItem('user_profile') || '{}');
    
    if (!currentUser || !currentUser.email) {
      console.log('No user profile in session storage, skipping status check');
      return;
    }
    
    console.log('Performing periodic status check for:', currentUser.email);
    
    // Try to find user by email first
    this.http.get<UserDTO>(`${this.environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`)
      .subscribe({
        next: (user) => {
          console.log('Periodic status check result:', user.status);
          if (user.status !== 'ACTIVATED') {
            console.log(`User status is ${user.status}, redirecting to deactivated page`);
            this.router.navigate(['/account-deactivated'], { 
              queryParams: { status: user.status } 
            });
          }
        },
        error: (error) => {
          console.error('Error in periodic status check:', error);
        }
      });
  }

  ngOnDestroy(): void {
    this.stopPeriodicStatusCheck();
  }
} 