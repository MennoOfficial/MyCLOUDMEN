import { Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { BehaviorSubject, timer } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class SessionTimeoutService {
  private showTimeoutModalSubject = new BehaviorSubject<boolean>(false);
  showTimeoutModal$ = this.showTimeoutModalSubject.asObservable();
  
  private remainingTimeSubject = new BehaviorSubject<number>(0);
  remainingTime$ = this.remainingTimeSubject.asObservable();
  
  private countdownInterval: any;
  private sessionTimeoutId: any = null;
  private warningTimeoutId: any = null;
  
  // Session constants
  private readonly SESSION_DURATION = 2 * 60 * 60 * 1000; // 2 hours
  private readonly WARNING_BEFORE_TIMEOUT = 5 * 60 * 1000; // 5 minutes

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.startSessionTimer();
    this.setupActivityListeners();
  }

  private setupActivityListeners(): void {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart'];
    
    events.forEach(event => {
      window.addEventListener(event, () => {
        if (!this.showTimeoutModalSubject.value) {
          this.resetSessionTimer();
        }
      }, { passive: true });
    });
  }
  
  // Start the session timer
  startSessionTimer(): void {
    // Clear any existing timeout
    this.clearSessionTimeout();
    
    // Set timeout for warning
    this.warningTimeoutId = setTimeout(() => {
      console.log('Session timeout warning');
      this.showTimeoutModalSubject.next(true);
      this.startCountdown(this.WARNING_BEFORE_TIMEOUT);
    }, this.SESSION_DURATION - this.WARNING_BEFORE_TIMEOUT);
    
    // Set timeout for logout
    this.sessionTimeoutId = setTimeout(() => {
      this.logout();
    }, this.SESSION_DURATION);
  }

  // Reset the session timer
  resetSessionTimer(): void {
    this.clearSessionTimeout();
    this.startSessionTimer();
  }

  // Clear the session timeout
  clearSessionTimeout(): void {
    if (this.sessionTimeoutId) {
      clearTimeout(this.sessionTimeoutId);
      this.sessionTimeoutId = null;
    }
    
    if (this.warningTimeoutId) {
      clearTimeout(this.warningTimeoutId);
      this.warningTimeoutId = null;
    }
  }

  private startCountdown(duration: number): void {
    let timeLeft = Math.floor(duration / 1000);
    this.remainingTimeSubject.next(timeLeft);
    
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
    
    this.countdownInterval = setInterval(() => {
      timeLeft -= 1;
      this.remainingTimeSubject.next(timeLeft);
      
      if (timeLeft <= 0) {
        clearInterval(this.countdownInterval);
        this.logout();
      }
    }, 1000);
  }

  extendSession(): void {
    this.closeTimeoutModal();
    this.resetSessionTimer();
  }

  closeTimeoutModal(): void {
    this.showTimeoutModalSubject.next(false);
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
  }

  logout(): void {
    this.closeTimeoutModal();
    this.authService.logout();
  }
} 