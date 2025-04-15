import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionTimeoutModalComponent } from './shared/components/session-timeout-modal/session-timeout-modal.component';
import { StatusCheckerService } from './core/services/status-checker.service';
import { AuthService } from './core/auth/auth.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SessionTimeoutModalComponent],
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
        console.log('User is authenticated, starting status checker');
        this.statusCheckerService.startPeriodicStatusCheck();
      } else {
        console.log('User is not authenticated, stopping status checker');
        this.statusCheckerService.stopPeriodicStatusCheck();
      }
    });
  }
  
  ngOnDestroy(): void {
    if (this.authSubscription) {
      this.authSubscription.unsubscribe();
    }
    this.statusCheckerService.stopPeriodicStatusCheck();
  }
}
