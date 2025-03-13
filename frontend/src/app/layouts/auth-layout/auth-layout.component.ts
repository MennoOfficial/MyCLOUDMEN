import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NavigationStart, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './auth-layout.component.html',
  styleUrls: ['./auth-layout.component.scss']
})
export class AuthLayoutComponent implements OnInit {
  loading = false;
  private minLoadingTime = 2000; // Minimum time to show loading animation (ms)
  private loadingStartTime = 0;

  constructor(private router: Router) {}

  ngOnInit(): void {
    // Show loading animation on navigation start
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart)
    ).subscribe(() => {
      this.loading = true;
      this.loadingStartTime = Date.now();
    });

    // Hide loading animation on navigation end, but ensure minimum display time
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      const elapsedTime = Date.now() - this.loadingStartTime;
      const remainingTime = Math.max(0, this.minLoadingTime - elapsedTime);
      
      // Ensure loading animation shows for at least minLoadingTime
      setTimeout(() => {
        this.loading = false;
      }, remainingTime);
    });
  }
}
