import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  template: '<div class="auth-callback"><app-loading-spinner text="Completing authentication..."></app-loading-spinner></div>',
  styles: ['.auth-callback { display: flex; justify-content: center; align-items: center; height: 100vh; }']
})
export class CallbackComponent implements OnInit {
  constructor(private authService: AuthService) {}
  
  ngOnInit(): void {
    // Process the authentication callback
    this.authService.handleAuthCallback();
  }
}
