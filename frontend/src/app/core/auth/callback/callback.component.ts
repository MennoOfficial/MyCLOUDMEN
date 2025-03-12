import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule],
  template: '<div class="loading-spinner">Completing authentication...</div>',
  styles: ['.loading-spinner { display: flex; justify-content: center; align-items: center; height: 100vh; }']
})
export class CallbackComponent implements OnInit {
  constructor(private authService: AuthService) {}
  
  ngOnInit(): void {
    // Process the authentication callback
    this.authService.handleAuthCallback();
  }
}
