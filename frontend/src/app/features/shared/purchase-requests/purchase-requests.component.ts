import { Component, OnInit, OnDestroy, ElementRef, Renderer2 } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, of, finalize, interval, Subscription } from 'rxjs';
import { EnvironmentService } from '../../../core/services/environment.service';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/models/auth.model';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

interface PurchaseRequest {
  id: string;
  type: string;
  quantity: number;
  cost: number;
  requestDate: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
}

interface GoogleWorkspaceLicense {
  skuId: string;
  skuName: string;
  totalLicenses: number;
  planType: string;
  status: string;
}

interface SignatureSatoriCredits {
  customerId: string;
  creditBalance: number;
  domains: string[];
  ownerEmail: string;
  creditDiscountPercent: number;
}

interface GoogleWorkspaceSubscriptionListResponseDTO {
  subscriptions: GoogleWorkspaceLicense[];
}

// Add interface for API purchase request response
interface PurchaseRequestResponse {
  id: string;
  type: string;
  licenseType?: string;
  quantity?: number;
  domain?: string;
  cost?: number;
  userEmail: string;
  requestDate: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
}

// Add interface for API paginated response
interface PaginatedResponse<T> {
  items: T[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

// Add missing interface for request status checking
interface RequestStatusResponse {
  id: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
  type: 'credits' | 'licenses';
  licenseType?: string;
  quantity?: number;
  domain?: string;
  userEmail?: string;
}

// Add this in the component class after the existing properties
@Component({
  selector: 'app-purchase-requests',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    RouterModule
  ],
  templateUrl: './purchase-requests.component.html',
  styleUrls: ['./purchase-requests.component.scss']
})
export class PurchaseRequestsComponent implements OnInit, OnDestroy {
  userInfo: any = {};
  
  // Mode from route data
  mode: string = 'normal'; // normal, confirm, approve-license, purchase-success, purchase-error, license-success, license-error, accept-purchase
  
  // URL parameters
  requestId: string = '';
  userEmail: string = '';
  licenseType: string = '';
  count: number = 0;
  domain: string = '';
  errorMessage: string = '';
  customerId: string = '';
  
  // Properties for accept-purchase mode (from PurchaseAcceptComponent)
  loadingText: string = 'Processing your approval request...';
  success: boolean = false;
  loading: boolean = true;
  error: boolean = false;
  message: string = '';
  requestType: 'purchase' | 'license' = 'purchase';
  
  // For status polling
  private statusCheckInterval: Subscription | null = null;
  private maxAttempts = 20; // Maximum polling attempts
  private currentAttempt = 0;
  
  // Original component state
  isLoading = true;
  loadingError = false;
  licenses: GoogleWorkspaceLicense[] = [];
  creditBalance = 0;
  pendingRequests: PurchaseRequest[] = [];
  
  // Modal states
  showCreditsModal = false;
  showLicenseModal = false;
  purchaseQuantity = 100; // For credits
  purchaseLicenseQuantity = 1; // For licenses
  selectedLicenseType = 'Business Standard';
  
  // Notification states 
  showNotification = false;
  notificationMessage = '';
  notificationType = 'success';

  constructor(
    private renderer: Renderer2,
    private el: ElementRef,
    private http: HttpClient,
    private environmentService: EnvironmentService,
    private authService: AuthService,
    private route: ActivatedRoute,
    public router: Router
  ) {}

  ngOnInit(): void {
    // Check if we're in a special mode from route data
    this.route.data.subscribe(data => {
      if (data['mode']) {
        this.mode = data['mode'];
        console.log(`PurchaseRequestsComponent operating in ${this.mode} mode`);
      }
    });
    
    // Get URL parameters
    this.route.queryParams.subscribe(params => {
      this.requestId = params['requestId'] || '';
      this.userEmail = params['email'] || '';
      this.licenseType = params['licenseType'] || '';
      this.count = params['count'] ? parseInt(params['count']) : 0;
      this.domain = params['domain'] || '';
      this.errorMessage = params['message'] || '';
      this.customerId = params['customerId'] || '';
      
      // Check for success status
      if (params['status'] === 'success') {
        // Show success notification
        const type = params['type'] === 'purchase' ? 'Purchase' : 'License';
        this.showToast(`${type} request completed successfully!`, 'success');
        
        // Refresh the purchase requests list
        this.fetchPendingRequests();
      }
      
      // Handle different modes using the parameters
      this.handleModeActions();
    });
    
    // Only fetch normal mode data if we're in normal mode
    if (this.mode === 'normal') {
      this.fetchUserInfo();
    }
  }
  
  ngOnDestroy(): void {
    // Clean up subscription when component is destroyed
    if (this.statusCheckInterval) {
      this.statusCheckInterval.unsubscribe();
      this.statusCheckInterval = null;
    }
  }
  
  private handleModeActions() {
    switch (this.mode) {
      case 'accept-purchase':
        // This is the email link handler (formerly PurchaseAcceptComponent)
        if (!this.requestId) {
          this.error = true;
          this.loading = false;
          this.message = 'Missing request ID. Unable to process purchase.';
          return;
        }
        
        // Determine which API to call based on whether customerId is present
        if (this.customerId) {
          this.requestType = 'license';
          this.loadingText = 'Processing your Google Workspace license approval...';
          this.acceptGoogleWorkspaceLicense();
        } else {
          this.requestType = 'purchase';
          this.loadingText = 'Processing your purchase approval...';
          this.acceptPurchase();
        }
        break;
        
      case 'confirm':
        // Call API to confirm purchase with requestId from URL
        if (this.requestId) {
          // We're already showing the confirmation page in the UI
          // confirmPurchase will handle the transition to success or error
          this.confirmPurchase(this.requestId);
        } else {
          // If no requestId, show error
          this.errorMessage = 'Missing request ID';
          this.mode = 'purchase-error';
        }
        break;
        
      case 'approve-license':
        // Call API to approve license purchase with requestId from URL
        if (this.requestId) {
          // We're already showing the approval page in the UI
          // approveLicense will handle the transition to success or error
          this.approveLicense(this.requestId);
        } else {
          // If no requestId, show error
          this.errorMessage = 'Missing request ID';
          this.mode = 'license-error';
        }
        break;
        
      case 'purchase-success':
      case 'license-success':
        // Just display success message, handled in template
        // No toast needed since we're showing a full success page
        // Add a timeout to automatically navigate back to purchase requests after 3 seconds
        setTimeout(() => {
          this.mode = 'normal';
          this.router.navigate(['/purchase-requests']);
        }, 3000);
        break;
        
      case 'purchase-error':
      case 'license-error':
        // Just display error message, handled in template
        // No toast needed since we're showing a full error page
        break;
        
      default:
        // Normal mode, handled by normal flow
        break;
    }
  }
  
  // From PurchaseAcceptComponent - for handling email links
  acceptPurchase(): void {
    // Continue to show loading state while we make the API call
    this.loading = true;
    
    // Create the API URL
    const url = `${this.environmentService.apiUrl}/purchase/accept`;
    
    // Send HTTP request to the backend API
    this.http.get(url, { 
      params: { requestId: this.requestId },
      responseType: 'text'
    }).pipe(
      catchError(error => {
        this.error = true;
        this.message = error.error || 'An error occurred while processing your purchase. Please try again later.';
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        // After initiating acceptance, start polling for status changes
        this.startPollingRequestStatus();
      }
    });
  }
  
  acceptGoogleWorkspaceLicense(): void {
    // Continue to show loading state while we make the API call
    this.loading = true;
    
    // Create the API URL
    const url = `${this.environmentService.apiUrl}/purchase/google-workspace/accept`;
    
    // Send HTTP request to the backend API
    this.http.get(url, { 
      params: { 
        requestId: this.requestId,
        customerId: this.customerId 
      },
      responseType: 'text'
    }).pipe(
      catchError(error => {
        this.error = true;
        this.message = error.error || 'An error occurred while processing your license. Please try again later.';
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        // After initiating acceptance, start polling for status changes
        this.startPollingRequestStatus();
      }
    });
  }
  
  startPollingRequestStatus(): void {
    // Poll every 3 seconds to check if request has been approved
    this.statusCheckInterval = interval(3000).subscribe(() => {
      this.checkRequestStatusForApproval();
      
      // Increment attempt counter
      this.currentAttempt++;
      
      // Stop polling after maximum attempts
      if (this.currentAttempt >= this.maxAttempts) {
        if (this.statusCheckInterval) {
          this.statusCheckInterval.unsubscribe();
          this.statusCheckInterval = null;
        }
        
        // Show error if we've reached max attempts without success
        if (!this.success && !this.error) {
          this.error = true;
          this.loading = false;
          this.message = 'The approval process is taking longer than expected. Please check your purchase requests page for status updates.';
        }
      }
    });
  }
  
  checkRequestStatusForApproval(): void {
    // Default endpoint for regular purchases
    let url = `${this.environmentService.apiUrl}/purchase/status`;
    
    // Use different endpoint for license requests
    if (this.requestType === 'license') {
      url = `${this.environmentService.apiUrl}/license/status`;
    }
    
    this.http.get<RequestStatusResponse>(url, {
      params: { requestId: this.requestId }
    }).pipe(
      catchError(error => {
        // Don't show error yet, we'll keep trying
        console.error('Error checking request status:', error);
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        // Store request details for success page
        this.userEmail = response.userEmail || '';
        
        if (response.type === 'licenses') {
          this.licenseType = response.licenseType || '';
          this.count = response.quantity || 0;
          this.domain = response.domain || '';
        } else {
          this.count = response.quantity || 0;
        }
        
        // Check if request has been approved
        if (response.status === 'APPROVED') {
          // Stop polling
          if (this.statusCheckInterval) {
            this.statusCheckInterval.unsubscribe();
            this.statusCheckInterval = null;
          }
          
          // Show success state
          this.success = true;
          this.loading = false;
          
          if (this.requestType === 'license') {
            this.message = 'Your Google Workspace license has been successfully approved.';
          } else {
            this.message = 'Your purchase has been successfully approved.';
          }
          
          // Change mode after a delay
          setTimeout(() => {
            this.mode = this.requestType === 'license' ? 'license-success' : 'purchase-success';
            this.router.navigate(
              [this.requestType === 'license' ? '/license-success' : '/purchase-success'],
              {
                queryParams: {
                  requestId: this.requestId,
                  email: this.userEmail,
                  licenseType: this.licenseType,
                  count: this.count,
                  domain: this.domain
                }
              }
            );
          }, 1000);
        }
        // Check if request has been rejected
        else if (response.status === 'REJECTED') {
          // Stop polling
          if (this.statusCheckInterval) {
            this.statusCheckInterval.unsubscribe();
            this.statusCheckInterval = null;
          }
          
          // Show error state
          this.error = true;
          this.loading = false;
          this.message = 'The request was rejected. Please contact support for assistance.';
        }
        // Otherwise (PENDING or AWAITING_CONFIRMATION), continue polling
      }
    });
  }

  private confirmPurchase(requestId: string) {
    const url = `${this.environmentService.apiUrl}/purchase/accept?requestId=${requestId}`;
    
    // Show loading status while confirmation is in progress
    this.mode = 'confirm';
    this.errorMessage = '';
    
    this.http.get(url).subscribe({
      next: (response: any) => {
        if (response.status === 'success') {
          // Navigate back to purchase-requests with success parameters
          this.router.navigate(['/purchase-requests'], {
            queryParams: {
              requestId: response.requestId,
              email: response.email,
              type: response.type,
              status: 'success'
            }
          });
          // Show toast notification
          this.showToast('Purchase request successfully approved!', 'success');
        } else {
          // Show error page
          this.errorMessage = response.message || 'Unknown error';
          this.mode = 'purchase-error';
          // Show toast notification
          this.showToast('Error approving purchase: ' + this.errorMessage, 'error');
        }
      },
      error: (error) => {
        // Show error page instead of toast
        this.errorMessage = error.message || 'Unknown error';
        this.mode = 'purchase-error';
        // Show toast notification
        this.showToast('Error approving purchase: ' + this.errorMessage, 'error');
      }
    });
  }
  
  private approveLicense(requestId: string) {
    const url = `${this.environmentService.apiUrl}/purchase/google-workspace/accept?requestId=${requestId}`;
    
    // Show loading status while approval is in progress
    this.mode = 'approve-license';
    this.errorMessage = '';
    
    // Don't navigate away immediately, wait for response
    this.http.get(url, { responseType: 'text' }).subscribe({
      next: (response) => {
        try {
          // Try to parse the response as JSON
          const jsonResponse = JSON.parse(response);
          if (jsonResponse.status === 'success') {
            // Navigate back to purchase-requests with success parameters
            this.router.navigate(['/purchase-requests'], {
              queryParams: {
                requestId: this.requestId,
                email: this.userEmail,
                licenseType: this.licenseType,
                count: this.count,
                domain: this.domain,
                status: 'success'
              }
            });
            // Show toast notification
            this.showToast('License request successfully approved!', 'success');
            return;
          }
        } catch (e) {
          // Non-JSON response, continue with default handling
        }

        // Navigate back to purchase-requests with success parameters
        this.router.navigate(['/purchase-requests'], {
          queryParams: {
            requestId: this.requestId,
            email: this.userEmail,
            licenseType: this.licenseType,
            count: this.count,
            domain: this.domain,
            status: 'success'
          }
        });
        // Show toast notification
        this.showToast('License request successfully approved!', 'success');
      },
      error: (error) => {
        if (error.status === 200) {
          // If somehow we got a 200 status as an error, treat it as success
          this.router.navigate(['/purchase-requests'], {
            queryParams: {
              requestId: this.requestId || `req-${Math.floor(Math.random() * 10000)}`,
              email: this.userEmail,
              licenseType: this.licenseType,
              count: this.count,
              domain: this.domain,
              status: 'success'
            }
          });
          // Show toast notification
          this.showToast('License request successfully approved!', 'success');
          return;
        }
        // Show error page instead of toast
        this.errorMessage = error.message || 'Unknown error';
        this.mode = 'license-error';
        // Show toast notification
        this.showToast('Error approving license: ' + this.errorMessage, 'error');
      }
    });
  }

  // Custom notification method
  showToast(message: string, type: 'success' | 'error' = 'success'): void {
    this.notificationMessage = message;
    this.notificationType = type;
    this.showNotification = true;
    
    // Auto hide after 5 seconds (increased from 3 seconds)
    setTimeout(() => {
      this.showNotification = false;
    }, 5000);
  }

  fetchUserInfo(): void {
    // Get the current user from AuthService
    const currentUser = this.authService.getCurrentUser();
    
    if (currentUser) {
      console.log('Current user from AuthService:', currentUser);
      
      // Extract email domain
      const emailDomain = this.extractDomainFromEmail(currentUser.email || '');
      
      // First try to get the company information from TeamLeader
      if (emailDomain) {
        this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/domain/${emailDomain}`)
          .pipe(
            catchError(error => {
              console.error(`Error fetching company by domain ${emailDomain}:`, error);
              return of(null);
            })
          )
          .subscribe(companyInfo => {
            // If company info was found, use it
            if (companyInfo && companyInfo.name) {
              // Map user info with company details
              this.userInfo = {
                ...currentUser,
                // Use the found company name
                company: companyInfo.name,
                // Add domain from email
                domain: emailDomain || 'example.com',
                // Add customer IDs
                customerId: '535354', // For Signature Satori
                googleWorkspaceCustomerId: '363466' // For Google Workspace
              };
              
              console.log('User info prepared with company data:', this.userInfo);
              
              // After getting user info, fetch licenses and credits
              this.fetchGoogleWorkspaceLicenses();
              this.fetchSignatureSatoriCredits();
              this.fetchPendingRequests();
            } else {
              // Fallback to using the domain if no company was found
              this.userInfo = {
                ...currentUser,
                // Use default company name
                company: 'MyCLOUDMEN',
                // Add domain from email
                domain: emailDomain || 'example.com',
                // Add customer IDs
                customerId: '535354', // For Signature Satori
                googleWorkspaceCustomerId: '363466' // For Google Workspace
              };
              
              console.log('User info prepared with default company:', this.userInfo);
              
              // After getting user info, fetch licenses and credits
              this.fetchGoogleWorkspaceLicenses();
              this.fetchSignatureSatoriCredits();
              this.fetchPendingRequests();
            }
          });
      } else {
        // If no domain could be extracted, use defaults
        this.userInfo = {
          ...currentUser,
          // Use default company name
          company: 'MyCLOUDMEN',
          // Add default domain
          domain: 'example.com',
          // Add customer IDs
          customerId: '535354', // For Signature Satori
          googleWorkspaceCustomerId: '363466' // For Google Workspace
        };
        
        console.log('User info prepared with defaults (no domain):', this.userInfo);
        
        // After getting user info, fetch licenses and credits
        this.fetchGoogleWorkspaceLicenses();
        this.fetchSignatureSatoriCredits();
        this.fetchPendingRequests();
      }
    } else {
      console.error('No user logged in or user info not available');
      this.showToast('Unable to retrieve user information. Please log in again.', 'error');
      this.isLoading = false;
      this.loadingError = true;
    }
  }
  
  // Helper function to extract domain from email
  private extractDomainFromEmail(email: string): string | null {
    if (!email || !email.includes('@')) return null;
    return email.split('@')[1];
  }

  fetchGoogleWorkspaceLicenses(): void {
    // Use the customer ID from the user info
    const customerId = this.userInfo.googleWorkspaceCustomerId;
    this.customerId = customerId;
    
    // Fetch Google Workspace licenses using the correct endpoint
    this.http.get<GoogleWorkspaceSubscriptionListResponseDTO>(
      `${this.environmentService.apiUrl}/google-workspace/customers/${customerId}/licenses`
    )
      .pipe(
        catchError(error => {
          console.error('Error fetching Google Workspace licenses:', error);
          this.showToast('Failed to load license information', 'error');
          // Fallback to mock data
          return of({ 
            subscriptions: [
              {
                skuId: '1010020020',
                skuName: 'Google Workspace Business Starter',
                totalLicenses: 5,
                planType: 'ANNUAL',
                status: 'ACTIVE'
              },
              {
                skuId: '1010020028',
                skuName: 'Google Workspace Business Standard',
                totalLicenses: 10,
                planType: 'ANNUAL',
                status: 'ACTIVE'
              },
              {
                skuId: '1010020025',
                skuName: 'Google Workspace Business Plus',
                totalLicenses: 3,
                planType: 'FLEXIBLE',
                status: 'ACTIVE'
              }
            ] 
          });
        }),
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe(response => {
        if (response && response.subscriptions) {
          this.licenses = response.subscriptions;
        }
      });
  }

  fetchSignatureSatoriCredits(): void {
    const satoriCustomerId = this.userInfo.customerId;
    
    this.http.get<SignatureSatoriCredits>(
      `${this.environmentService.apiUrl}/signaturesatori/customers/${satoriCustomerId}/credits`
    )
      .pipe(
        catchError(error => {
          console.error('Error fetching Signature Satori credits:', error);
          this.showToast('Failed to load credit information', 'error');
          return of({
            customerId: satoriCustomerId,
            creditBalance: 150,
            domains: [this.userInfo.domain],
            ownerEmail: this.userInfo.email,
            creditDiscountPercent: 0.1
          });
        })
      )
      .subscribe(response => {
        if (response) {
          this.creditBalance = response.creditBalance;
        }
      });
  }

  fetchPendingRequests(): void {
    // Using the new API endpoint to fetch real purchase requests
    this.isLoading = true;
    
    // Get the current user email
    const userEmail = this.userInfo?.email;
    
    if (!userEmail) {
      this.showToast('User email not available', 'error');
      this.isLoading = false;
      return;
    }
    
    // Use the appropriate endpoint based on the user's role
    // Admins can see all requests, users can only see their own
    const endpoint = this.isAdmin() 
      ? `${this.environmentService.apiUrl}/purchase-requests` 
      : `${this.environmentService.apiUrl}/purchase-requests/user/${encodeURIComponent(userEmail)}`;
    
    this.http.get<PaginatedResponse<PurchaseRequestResponse>>(endpoint, {
      params: {
        page: '0',
        size: '50'
      }
    })
    .pipe(
      catchError(error => {
        console.error('Error fetching purchase requests:', error);
        this.showToast('Failed to load purchase requests', 'error');
        
        // Fallback to mock data on error
        return of({
          items: [
            {
              id: 'req-123',
              type: 'licenses',
              licenseType: 'Business Standard',
              quantity: 5,
              domain: this.userInfo?.domain || 'example.com',
              userEmail: userEmail,
              requestDate: new Date().toISOString(),
              status: 'AWAITING_CONFIRMATION'
            },
            {
              id: 'req-124',
              type: 'credits',
              quantity: 500,
              userEmail: userEmail,
              requestDate: new Date(Date.now() - 86400000).toISOString(), // Yesterday
              status: 'APPROVED'
            }
          ],
          currentPage: 0,
          totalItems: 2,
          totalPages: 1
        } as PaginatedResponse<PurchaseRequestResponse>);
      }),
      finalize(() => {
        this.isLoading = false;
      })
    )
    .subscribe(response => {
      // Extract items from the paginated response
      this.pendingRequests = response.items.map((item: PurchaseRequestResponse) => ({
        id: item.id,
        type: this.formatRequestType(item),
        quantity: item.quantity || 0,
        cost: item.cost || this.calculateCost(item),
        requestDate: new Date(item.requestDate).toISOString().split('T')[0],
        status: item.status
      }));
      
      console.log('Fetched purchase requests:', this.pendingRequests);
    });
  }
  
  // Helper to format the request type for display
  private formatRequestType(request: PurchaseRequestResponse): string {
    if (request.type === 'licenses' && request.licenseType) {
      return `Google Workspace - ${request.licenseType}`;
    } else if (request.type === 'credits') {
      return 'Signature Satori Credits';
    } else {
      return request.type || 'Unknown';
    }
  }
  
  // Helper to calculate the cost based on request type
  private calculateCost(request: PurchaseRequestResponse): number {
    if (request.type === 'licenses' && request.licenseType) {
      const pricePerLicense = this.getLicensePriceValue(request.licenseType);
      return pricePerLicense * (request.quantity || 0);
    } else if (request.type === 'credits') {
      // $0.50 per credit
      return (request.quantity || 0) * 0.5;
    } else {
      return 0;
    }
  }
  
  // Helper to determine if user is an admin
  private isAdmin(): boolean {
    // Check if user has admin role
    const roles = this.userInfo?.roles || [];
    return roles.some((role: string) => ['SYSTEM_ADMIN', 'COMPANY_ADMIN'].includes(role));
  }

  // Helper functions to display license information
  hasLicenseType(licenseType: string): boolean {
    // Check if user has any licenses of this type
    return this.licenses.some(license => license.skuName.includes(licenseType));
  }
  
  getLicenseCount(licenseType: string): number {
    // Find total licenses of this type
    const license = this.licenses.find(license => license.skuName.includes(licenseType));
    return license ? license.totalLicenses : 0;
  }
  
  getLicensePrice(licenseType: string): string {
    // Return the price based on license type
    switch (licenseType) {
      case 'Business Starter':
        return '$7 USD';
      case 'Business Standard':
        return '$14 USD';
      case 'Business Plus':
        return '$22 USD';
      default:
        return '$0 USD';
    }
  }
  
  getLicensePriceValue(licenseType: string): number {
    // Return the numeric price for calculations
    switch (licenseType) {
      case 'Business Starter':
        return 7;
      case 'Business Standard':
        return 14;
      case 'Business Plus':
        return 22;
      default:
        return 0;
    }
  }

  // Modal functions
  openLicensePurchaseModal(licenseType: string): void {
    this.selectedLicenseType = licenseType;
    this.purchaseLicenseQuantity = 1;
    this.showLicenseModal = true;
  }
  
  openCreditsPurchaseModal(): void {
    this.purchaseQuantity = 100;
    this.showCreditsModal = true;
  }

  closeModals(): void {
    this.showCreditsModal = false;
    this.showLicenseModal = false;
  }

  // Calculation functions
  calculateCreditsCost(): number {
    // $0.50 per credit
    return this.purchaseQuantity * 0.5;
  }
  
  calculateLicenseCost(): number {
    // Cost = price per license * quantity
    return this.getLicensePriceValue(this.selectedLicenseType) * this.purchaseLicenseQuantity;
  }

  // Purchase functions
  purchaseCredits(): void {
    // Validation
    if (this.purchaseQuantity < 100) {
      this.showToast('Minimum purchase is 100 credits', 'error');
      return;
    }
    
    if (this.purchaseQuantity > 10000) {
      this.showToast('Maximum purchase is 10,000 credits', 'error');
      return;
    }
    
    // Close the modal
    this.closeModals();
    
    // Get user information
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser || !currentUser.email) {
      this.showToast('User information not available', 'error');
      return;
    }
    
    // Show toast notification
    this.showToast('Purchase request sent. Check your email to confirm the purchase.', 'success');
    
    // Get customer ID from user info
    const customerId = this.userInfo?.customerId || '535354';
    
    // Create the credits request - format exactly as the backend expects
    const creditsRequest = {
      quantity: this.purchaseQuantity,
      cost: this.calculateCreditsCost()
    };
    
    console.log('Sending credits request:', creditsRequest);
    
    // Send the purchase request to the correct purchase request endpoint (with email confirmation)
    this.http.post<any>(
      `${this.environmentService.apiUrl}/purchase/signature-satori/request?userEmail=${encodeURIComponent(currentUser.email)}&customerId=${customerId}`,
      creditsRequest
    ).subscribe({
      next: (response: any) => {
        console.log('Credits request sent successfully:', response);
        
        // Show success message
        if (response.status === 'PENDING') {
          this.showToast('Purchase request submitted! Check your email to confirm the purchase.', 'success');
        } else {
          this.showToast('Purchase request submitted successfully!', 'success');
        }
        
        // Refresh purchase requests list (don't refresh credits as purchase is not completed yet)
        this.fetchPendingRequests();
      },
      error: (error) => {
        console.error('Error sending credits request:', error);
        
        // Handle different error scenarios
        if (error.status === 400 && error.error?.message) {
          this.showToast(error.error.message, 'error');
        } else if (error.status === 500) {
          this.showToast('Server error processing request. Please try again later.', 'error');
        } else {
          this.showToast('Error sending purchase request. Please try again.', 'error');
        }
      }
    });
  }
  
  purchaseLicenses(): void {
    // Validation
    if (this.purchaseLicenseQuantity < 1) {
      this.showToast('Minimum purchase is 1 license', 'error');
      return;
    }
    
    if (this.purchaseLicenseQuantity > 100) {
      this.showToast('Maximum purchase is 100 licenses', 'error');
      return;
    }
    
    // Close the modal
    this.closeModals();
    
    // Get user information
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser || !currentUser.email) {
      this.showToast('User information not available', 'error');
      return;
    }
    
    const userDomain = this.extractDomainFromEmail(currentUser.email) || 'example.com';
    
    // Show toast notification
    this.showToast('License request sent. Check your email to confirm the purchase.', 'success');
    
    // Ensure consistent license type naming
    const fullLicenseType = this.getFullLicenseTypeName(this.selectedLicenseType);
    console.log(`Requesting license type: ${fullLicenseType}`);
    
    // Create the license request DTO
    const licenseRequest = {
      count: this.purchaseLicenseQuantity,
      licenseType: fullLicenseType,
      domain: userDomain,
      cost: this.calculateLicenseCost()
    };
    
    // Get customer ID from user info
    const customerId = this.userInfo?.googleWorkspaceCustomerId || '363466';
    
    // Send the purchase request
    this.http.post(
      `${this.environmentService.apiUrl}/purchase/google-workspace/request?userEmail=${currentUser.email}&customerId=${customerId}`,
      licenseRequest
    ).subscribe({
      next: (response: any) => {
        console.log('License request sent successfully:', response);
        // Refresh purchase requests list
        this.fetchPendingRequests();
      },
      error: (error) => {
        console.error('Error sending license request:', error);
        if (error.status === 200) {
          // If somehow we got a 200 status as an error, treat it as success
          this.fetchPendingRequests();
        } else {
          // Handle validation errors (400 Bad Request)
          if (error.status === 400 && error.error?.message) {
            this.showToast(error.error.message, 'error');
          } else {
            this.showToast('Error sending license request. Please try again.', 'error');
          }
        }
      }
    });
  }
  
  /**
   * Gets the full license type name with proper Google Workspace prefix
   * to ensure consistent naming between frontend and backend
   */
  private getFullLicenseTypeName(licenseType: string): string {
    // If it already has the Google Workspace prefix, return as is
    if (licenseType.toLowerCase().startsWith('google workspace')) {
      return licenseType;
    }
    
    // Otherwise, add the prefix
    return `Google Workspace ${licenseType}`;
  }

  // Status badge helper
  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'PENDING':
        return 'badge-warning';
      case 'AWAITING_CONFIRMATION':
        return 'badge-info';
      case 'APPROVED':
        return 'badge-success';
      case 'REJECTED':
        return 'badge-danger';
      default:
        return 'badge-info';
    }
  }

  /**
   * Manually refresh purchase requests
   */
  refreshPurchaseRequests(): void {
    console.log('Manually refreshing purchase requests');
    this.showToast('Refreshing purchase requests...', 'success');
    this.fetchPendingRequests();
  }
} 