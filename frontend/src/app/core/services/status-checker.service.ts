import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { EnvironmentService } from './environment.service';
import { interval, Subscription } from 'rxjs';
import { UserDTO } from '../models/user.dto';
import { catchError, of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class StatusCheckerService implements OnDestroy {
  private statusCheckInterval = 60000; // Check every 60 seconds
  private checkSubscription: Subscription | null = null;

  constructor(
    private http: HttpClient,
    private router: Router,
    private environmentService: EnvironmentService
  ) {}

  startPeriodicStatusCheck(): void {
    // Cancel any existing subscription
    this.stopPeriodicStatusCheck();
    
    console.log('Starting periodic status checks');
    
    // Create a new subscription
    this.checkSubscription = interval(this.statusCheckInterval).subscribe(() => {
      this.checkUserStatus();
    });
    
    // Do an initial check immediately
    this.checkUserStatus();
  }

  stopPeriodicStatusCheck(): void {
    if (this.checkSubscription) {
      this.checkSubscription.unsubscribe();
      this.checkSubscription = null;
    }
  }

  private checkUserStatus(): void {
    const currentUser = JSON.parse(sessionStorage.getItem('user_profile') || '{}');
    
    if (!currentUser || !currentUser.email) {
      console.log('No user profile in session storage, skipping status check');
      return;
    }
    
    console.log('Performing periodic status check for:', currentUser.email);
    
    // Try to find user by email first
    this.http.get<UserDTO>(`${this.environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`)
      .subscribe({
        next: (user) => {
          console.log('Periodic status check result:', user.status);
          
          // Check user status
          if (user.status !== 'ACTIVATED') {
            console.log(`User status is ${user.status}, redirecting to deactivated page`);
            this.router.navigate(['/account-deactivated'], { 
              queryParams: { status: user.status } 
            });
            return;
          }
          
          // Also check company status
          this.checkCompanyStatus(user);
        },
        error: (error) => {
          console.error('Error in periodic status check:', error);
        }
      });
  }
  
  private checkCompanyStatus(user: UserDTO): void {
    // Extract domain from email for company lookup
    const emailDomain = this.getEmailDomain(user.email);
    
    // Skip if no domain found
    if (!emailDomain) {
      console.log('Unable to extract domain from email, skipping company status check');
      return;
    }
    
    console.log('Checking company status for domain:', emailDomain);
    
    // Fetch all companies and check for domain match
    this.http.get<any>(`${this.environmentService.apiUrl}/companies`)
      .pipe(
        catchError(error => {
          console.error('Error fetching companies:', error);
          return of(null);
        })
      )
      .subscribe(response => {
        // Check if response is an array or has a companies property
        const companies = Array.isArray(response) ? response : response?.companies;
        
        if (!companies || companies.length === 0) {
          console.log('No companies found to check for domain match');
          return;
        }
        
        console.log(`Retrieved ${companies.length} companies, searching for domain: ${emailDomain}`);
        
        // Look for a company with matching domain
        const matchingCompany = this.findCompanyByDomain(companies, emailDomain);
        
        if (matchingCompany) {
          console.log('Found company with matching domain:', matchingCompany.name);
          
          // Get company status - either direct or from customFields
          const status = matchingCompany.status || 
            (matchingCompany.customFields && matchingCompany.customFields.status);
          
          // Redirect if company is not active
          if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
            console.log(`Company ${matchingCompany.name} has status ${status}, redirecting to company-inactive`);
            this.handleCompanyStatus(status, matchingCompany.name || 'Your company');
          } else {
            console.log(`Company ${matchingCompany.name} is active with status: ${status || 'unknown'}`);
          }
        } else {
          console.log(`No company found with domain: ${emailDomain}`);
        }
      });
  }
  
  // Helper method to find a company by domain
  private findCompanyByDomain(companies: any[], domain: string): any {
    // First try to match by primaryDomain
    let match = companies.find(company => 
      company.primaryDomain && 
      company.primaryDomain.toLowerCase() === domain.toLowerCase()
    );
    
    if (match) return match;
    
    // Then try to match by contact email domains
    return companies.find(company => {
      if (!company.contactInfo || !Array.isArray(company.contactInfo)) {
        return false;
      }
      
      return company.contactInfo.some((contact: any) => {
        const email = contact.email || contact.value;
        if (!email) return false;
        
        const contactDomain = this.getEmailDomain(email);
        return contactDomain && contactDomain.toLowerCase() === domain.toLowerCase();
      });
    });
  }
  
  private getEmailDomain(email: string): string | null {
    if (!email || !email.includes('@')) {
      return null;
    }
    
    const parts = email.split('@');
    if (parts.length === 2 && parts[1]) {
      return parts[1];
    }
    
    return null;
  }
  
  private handleCompanyStatus(status: string, companyName: string): void {
    console.log(`Company status check result: ${status} for ${companyName}`);
    
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
      console.log(`Company ${companyName} is ${status}, redirecting to company-inactive page`);
      this.router.navigate(['/company-inactive'], {
        queryParams: {
          status: status,
          company: companyName
        },
        replaceUrl: true
      });
    }
  }

  ngOnDestroy(): void {
    this.stopPeriodicStatusCheck();
  }
} 