import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-pending-account',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pending-account.component.html',
  styleUrls: ['./pending-account.component.scss']
})
export class PendingAccountComponent implements OnInit {
  
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}
  
  ngOnInit(): void {
    // Nothing specific needed on init for pending accounts
  }
  
  contactAdmin(): void {
    // Open email client without subject or body
    window.location.href = 'mailto:help@cloudmen.com';
  }
  
  logout(): void {
    this.authService.logout();
  }
} 