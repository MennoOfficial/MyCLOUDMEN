import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SessionTimeoutService } from '../../../core/services/session-timeout.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-session-timeout-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './session-timeout-modal.component.html',
  styleUrls: ['./session-timeout-modal.component.scss']
})
export class SessionTimeoutModalComponent implements OnInit, OnDestroy {
  showModal = false;
  remainingTime = 0;
  private subscriptions: Subscription[] = [];
  
  constructor(private sessionTimeoutService: SessionTimeoutService) {}
  
  ngOnInit(): void {
    // Subscribe to modal visibility changes
    this.subscriptions.push(
      this.sessionTimeoutService.showTimeoutModal$.subscribe(show => {
        console.log('Modal visibility changed:', show);
        this.showModal = show;
      })
    );
    
    // Subscribe to countdown timer
    this.subscriptions.push(
      this.sessionTimeoutService.remainingTime$.subscribe(time => {
        this.remainingTime = time;
      })
    );
  }
  
  ngOnDestroy(): void {
    // Clean up subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
  
  formatTime(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds < 10 ? '0' : ''}${remainingSeconds}`;
  }
  
  extendSession(): void {
    this.sessionTimeoutService.extendSession();
  }
  
  logout(): void {
    this.sessionTimeoutService.logout();
  }
} 