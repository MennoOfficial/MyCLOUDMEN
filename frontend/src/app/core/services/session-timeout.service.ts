import { Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SessionTimeoutService {
  private showTimeoutModalSubject = new BehaviorSubject<boolean>(false);
  showTimeoutModal$ = this.showTimeoutModalSubject.asObservable();
  
  private remainingTimeSubject = new BehaviorSubject<number>(0);
  remainingTime$ = this.remainingTimeSubject.asObservable();
  
  private countdownInterval: any;

  constructor(private authService: AuthService) {
    this.authService.sessionTimeoutWarning$.subscribe(warningTime => {
      console.log('Session timeout warning received:', warningTime);
      this.showTimeoutModalSubject.next(true);
      this.startCountdown(warningTime);
    });
    
    this.setupActivityListeners();
  }

  private setupActivityListeners(): void {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart'];
    
    events.forEach(event => {
      window.addEventListener(event, () => {
        if (!this.showTimeoutModalSubject.value) {
          this.authService.resetSessionTimer();
        }
      }, { passive: true });
    });
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
    this.authService.extendSession();
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