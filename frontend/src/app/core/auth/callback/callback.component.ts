import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { Router } from '@angular/router';
import { filter, first, switchMap, take, tap, timeout } from 'rxjs/operators';
import { of, timer } from 'rxjs';
import { User } from '../../models/auth.model';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  template: '<div class="auth-callback"><app-loading-spinner text="Completing authentication..."></app-loading-spinner></div>',
  styles: ['.auth-callback { display: flex; justify-content: center; align-items: center; height: 100vh; }']
})
export class CallbackComponent implements OnInit {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    this.authService.handleAuthCallback();
  }
}
