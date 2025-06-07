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
      return;
    }
    
    // Try to find user by email first
    this.http.get<UserDTO>(`${this.environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`)
      .pipe(
        catchError(error => {
          console.error('Error fetching user by email:', error);
          return of(null);
        })
      )
      .subscribe({
        next: (user) => {
          if (!user) {
            return; // Skip if no user is found
          }
          
          // Check user status and route appropriately
          if (user.status === 'PENDING') {
            this.router.navigate(['/pending-account'], { 
              replaceUrl: true 
            });
            return;
          }
          
          if (user.status === 'DEACTIVATED') {
            this.router.navigate(['/account-deactivated'], { 
              queryParams: { status: user.status },
              replaceUrl: true 
            });
            return;
          }
          
          // For ACTIVATED users, check company status
          if (user.status === 'ACTIVATED') {
            this.checkCompanyStatus(user);
          }
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
      return;
    }
    
    // Try fetching companies from domain-specific endpoint first
    this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/domain/${encodeURIComponent(emailDomain)}`)
      .pipe(
        catchError(error => {
          console.error(`Error fetching company by domain ${emailDomain}:`, error);
          
          // Fallback to fetching all companies
          return this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies`)
            .pipe(
              catchError(allError => {
                console.error('Error fetching all companies:', allError);
                return of(null);
              })
            );
        })
      )
      .subscribe(response => {
        // If domain-specific endpoint returned a single company
        if (response && !Array.isArray(response)) {
          this.handleCompanyResponse(response);
          return;
        }
        
        // Handle array response
        const companies = Array.isArray(response) ? response : response?.companies;
        
        if (!companies || companies.length === 0) {
          return;
        }
                
        // Look for a company with matching domain
        const matchingCompany = this.findCompanyByDomain(companies, emailDomain);
        
        if (matchingCompany) {
          this.handleCompanyResponse(matchingCompany);
        }
      });
  }
  
  private handleCompanyResponse(company: any): void {
    // Get company status - either direct or from customFields
    const status = company.status || 
      (company.customFields && company.customFields.status);
    
    // Redirect if company is not active
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
      this.handleCompanyStatus(status, company.name || 'Your company');
    }
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
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
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