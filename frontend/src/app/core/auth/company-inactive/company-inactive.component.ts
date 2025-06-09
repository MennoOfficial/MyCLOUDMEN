import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-company-inactive',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './company-inactive.component.html',
  styleUrls: ['./company-inactive.component.scss']
})
export class CompanyInactiveComponent implements OnInit {
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
    // Open email client with subject about company deactivation
    window.location.href = `mailto:help@cloudmen.com?subject=Account reactivation request - ${this.companyName || 'Company Name'}`;
  }
  
  logout(): void {
    this.authService.logout();
  }
} 