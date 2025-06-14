import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NavigationStart, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, LoadingSpinnerComponent],
  templateUrl: './auth-layout.component.html',
  styleUrls: ['./auth-layout.component.scss']
})
export class AuthLayoutComponent implements OnInit {
  loading = false;
  private minLoadingTime = 2000; // Minimum time to show loading animation (ms)
  private loadingStartTime = 0;

  constructor(
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Only show loading animation for auth routes, not main layout routes
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart)
    ).subscribe((event: NavigationStart) => {
      // Only show loading for actual auth routes
      if (event.url.startsWith('/auth/')) {
        this.loading = true;
        this.loadingStartTime = Date.now();
        // Use setTimeout to avoid ExpressionChangedAfterItHasBeenCheckedError
        setTimeout(() => {
          this.cdr.detectChanges();
        });
      }
    });

    // Hide loading animation on navigation end, but ensure minimum display time
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      // Only handle loading for auth routes
      if (event.url.startsWith('/auth/')) {
        const elapsedTime = Date.now() - this.loadingStartTime;
        const remainingTime = Math.max(0, this.minLoadingTime - elapsedTime);
        
        // Ensure loading animation shows for at least minLoadingTime
        setTimeout(() => {
          this.loading = false;
          this.cdr.detectChanges();
        }, remainingTime);
      } else {
        // Immediately hide loading for non-auth routes
        setTimeout(() => {
          this.loading = false;
          this.cdr.detectChanges();
        });
      }
    });
  }
}
