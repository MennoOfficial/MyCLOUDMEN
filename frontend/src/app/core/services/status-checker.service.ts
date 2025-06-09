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
    this.stopPeriodicStatusCheck();
    
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
    
    this.http.get<UserDTO>(`${this.environmentService.apiUrl}/users/email/${encodeURIComponent(currentUser.email)}`)
      .pipe(
        catchError(() => of(null))
      )
      .subscribe({
        next: (user) => {
          if (!user) {
            return;
          }
          
          if (user.status === 'PENDING') {
            this.router.navigate(['/pending-account'], { replaceUrl: true });
            return;
          }
          
          if (user.status === 'DEACTIVATED') {
            this.router.navigate(['/account-deactivated'], { 
              queryParams: { status: user.status },
              replaceUrl: true 
            });
            return;
          }
          
          if (user.status === 'ACTIVATED') {
            this.checkCompanyStatus(user);
          }
        },
        error: () => {
          // Silent error handling
        }
      });
  }
  
  private checkCompanyStatus(user: UserDTO): void {
    const emailDomain = this.getEmailDomain(user.email);
    
    if (!emailDomain) {
      return;
    }
    
    this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/domain/${encodeURIComponent(emailDomain)}`)
      .pipe(
        catchError(() => {
          return this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies`)
            .pipe(
              catchError(() => of({ data: [] }))
            );
        })
      )
      .subscribe({
        next: (response) => {
          const companies = response.data || [];
          if (companies.length > 0) {
            const matchingCompany = this.findCompanyByDomain(companies, emailDomain);
            if (matchingCompany) {
              this.handleCompanyResponse(matchingCompany);
            }
          }
        },
        error: () => {
          // Silent error handling
        }
      });
  }
  
  private handleCompanyResponse(company: any): void {
    const status = company.status || 
      (company.customFields && company.customFields.status);
    
    if (status === 'DEACTIVATED' || status === 'SUSPENDED') {
      this.handleCompanyStatus(status, company.name || 'Your company');
    }
  }
  
  private findCompanyByDomain(companies: any[], domain: string): any {
    let match = companies.find(company => 
      company.primaryDomain && 
      company.primaryDomain.toLowerCase() === domain.toLowerCase()
    );
    
    if (match) return match;
    
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