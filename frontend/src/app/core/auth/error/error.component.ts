import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-error',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.scss']
})
export class ErrorComponent implements OnInit {
  errorMessage: string = 'An error occurred during authentication.';
  
  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}
  
  ngOnInit(): void {
    // Get error details from route parameters
    this.route.queryParams.subscribe(params => {
      if (params['message']) {
        this.errorMessage = params['message'];
      }
    });
  }
  
  tryAgain(): void {
    // Redirect to home page to trigger authentication again
    this.router.navigate(['/']);
  }
}
