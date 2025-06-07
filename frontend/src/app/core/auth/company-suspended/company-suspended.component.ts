import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-company-suspended',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './company-suspended.component.html',
  styleUrls: ['./company-suspended.component.scss']
})
export class CompanySuspendedComponent implements OnInit {
  companyName: string = '';
  
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}
  
  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['company']) {
        this.companyName = params['company'];
      }
    });
  }
  
  contactSupport(): void {
    // Open email client with subject about company suspension
    window.location.href = `mailto:help@cloudmen.com?subject=Account suspended  - ${this.companyName || 'Company Name'}`;
  }
  
  logout(): void {
    this.authService.logout();
  }
} 