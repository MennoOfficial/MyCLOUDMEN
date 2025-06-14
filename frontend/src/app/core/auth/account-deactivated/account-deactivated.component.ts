import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-account-deactivated',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './account-deactivated.component.html',
  styleUrls: ['./account-deactivated.component.scss']
})
export class AccountDeactivatedComponent implements OnInit {
  userStatus: string = 'INACTIVE';
  
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}
  
  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['status']) {
        this.userStatus = params['status'];
      }
    });
  }
  
  contactSupport(): void {
    // Open email client without subject or body
    window.location.href = 'mailto:help@cloudmen.com';
  }
  
  logout(): void {
    this.authService.logout();
  }
} 