import { Component, OnInit, OnDestroy, ElementRef, Renderer2 } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, of, finalize, interval, Subscription } from 'rxjs';
import { EnvironmentService } from '../../../core/services/environment.service';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/models/auth.model';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

// Import standardized components
import { PageHeaderComponent, PageAction } from '../../../shared/components/page-header/page-header.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { DataTableComponent, TableColumn, TableAction, PaginationEvent, SortEvent } from '../../../shared/components/data-table/data-table.component';

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

// Add new interfaces for SKU data
interface GoogleWorkspaceSku {
  skuId: string;
  skuName: string;
  description?: string;
  plans?: string[];
  price?: {
    basePrice: number;
    currency: string;
    interval: string;
  };
}

interface SkuResponse {
  kind: string;
  skus: GoogleWorkspaceSku[];
}

// Add new interfaces for enhanced activity management
interface ActivityFilter {
  status: string;
  type: string;
  dateRange: string;
}

interface ActivitySearchState {
  searchTerm: string;
  filteredRequests: PurchaseRequest[];
  totalFilteredItems: number;
}

@Component({
  selector: 'app-purchase-requests',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    RouterModule,
    PageHeaderComponent,
    LoadingSpinnerComponent,
    DataTableComponent
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
  
  // SKU data for Google Workspace licenses
  availableSkus: GoogleWorkspaceSku[] = [];
  skuMap: Map<string, GoogleWorkspaceSku> = new Map(); // For quick lookups
  
  // Cached displayed data to prevent constant refreshing
  private _displayedRequests: any[] = [];
  private _lastRequestsHash: string = '';
  
  // Modal states
  showCreditsModal = false;
  showLicenseModal = false;
  purchaseQuantity = 10; // Changed from 100 to 10 for credits
  purchaseLicenseQuantity = 1; // For licenses
  selectedLicenseType = 'Business Standard';
  selectedSkuId = '1010020028'; // Default to Business Standard SKU
  
  // Notification states 
  showNotification = false;
  notificationMessage = '';
  notificationType = 'success';

  // New properties for enhanced UI
  showAllRequests = false;
  hasMoreRequests = false;
  private readonly RECENT_DAYS_LIMIT = 30;
  private readonly PAGE_SIZE = 10;
  private currentPage = 0;
  private lastFetchTime = 0;
  private readonly FETCH_COOLDOWN = 5000; // 5 seconds cooldown between fetches

  // Standardized component configurations - REMOVED REFRESH BUTTON
  headerActions: PageAction[] = [
    // Removed refresh button to prevent constant refreshing issues
  ];

  activityTableColumns: TableColumn[] = [
    {
      key: 'service',
      label: 'Service',
      type: 'avatar',
      sortable: false
    },
    {
      key: 'type',
      label: 'Request Type',
      type: 'text',
      sortable: true
    },
    {
      key: 'quantity',
      label: 'Quantity',
      type: 'text',
      sortable: false
    },
    {
      key: 'cost',
      label: 'Cost',
      type: 'currency',
      sortable: true
    },
    {
      key: 'status',
      label: 'Status',
      type: 'badge',
      badgeType: 'status',
      sortable: true
    },
    {
      key: 'requestDate',
      label: 'Date',
      type: 'date',
      sortable: true
    }
  ];

  // Enhanced activity management properties
  activitySearchTerm = '';
  activityFilter: ActivityFilter = {
    status: 'all',
    type: 'all',
    dateRange: 'all'
  };
  
  // Pagination for activity section
  activityCurrentPage = 1;
  activityPageSize = 10;
  activityTotalItems = 0;
  
  // Sorting properties
  activitySortColumn = '';
  activitySortDirection: 'asc' | 'desc' = 'desc';
  
  // Cache for filtered and paginated data
  private filteredActivityRequests: PurchaseRequest[] = [];
  private _displayedActivityRequests: any[] = [];
  private _lastActivityHash = '';

  // Available filter options
  readonly activityStatusOptions = [
    { value: 'all', label: 'All Statuses' },
    { value: 'PENDING', label: 'Pending' },
    { value: 'APPROVED', label: 'Approved' }
  ];

  readonly activityTypeOptions = [
    { value: 'all', label: 'All Types' },
    { value: 'license', label: 'License Requests' },
    { value: 'credit', label: 'Credit Purchases' }
  ];

  readonly activityDateOptions = [
    { value: 'all', label: 'All Time' },
    { value: '7', label: 'Last 7 days' },
    { value: '30', label: 'Last 30 days' },
    { value: '90', label: 'Last 90 days' }
  ];

  constructor(
    private renderer: Renderer2,
    private el: ElementRef,
    private http: HttpClient,
    private environmentService: EnvironmentService,
    private authService: AuthService,
    private route: ActivatedRoute,
    public router: Router
  ) {
    // Initialize route data and query parameters
    this.route.data.subscribe(data => {
      if (data['mode']) {
        this.mode = data['mode'];
      }
    });
    
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
        const type = params['type'] === 'purchase' ? 'Purchase' : 'License';
        this.showToast(`${type} request completed successfully!`, 'success');
        this.fetchPendingRequests();
      }
    });
  }

  ngOnInit(): void {
    this.handleModeActions();

    // Load data only for normal mode
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
        if (!this.requestId) {
          this.showError('Missing request ID for purchase acceptance');
          return;
        }

        if (this.customerId) {
          this.requestType = 'license';
          this.acceptGoogleWorkspaceLicense();
        } else {
          this.requestType = 'purchase';
          this.acceptPurchase();
        }
        break;

      case 'confirm':
        if (!this.requestId) {
          this.showError('Missing request ID for confirmation');
          return;
        }
        this.confirmPurchase(this.requestId);
        break;

      case 'approve-license':
        if (!this.requestId) {
          this.showError('Missing request ID for license approval');
          return;
        }
        this.approveLicense(this.requestId);
        break;

      case 'purchase-success':
      case 'license-success':
        this.showSuccess(this.mode);
        break;

      case 'purchase-error':
      case 'license-error':
        this.showError(this.errorMessage || 'An error occurred during processing');
        break;

      default:
        // Normal mode - continue with regular component initialization
        break;
    }
  }

  private showSuccess(mode: string) {
    this.success = true;
    this.loading = false;
    this.error = false;
    
    if (mode === 'license-success') {
      this.message = 'License request processed successfully!';
    } else {
      this.message = 'Purchase request processed successfully!';
    }
  }

  private showError(errorMessage: string) {
    this.success = false;
    this.loading = false;
    this.error = true;
    this.message = errorMessage;
  }
  
  // From PurchaseAcceptComponent - for handling email links
  acceptPurchase(): void {
    const user = this.authService.getCurrentUser();
    
    if (!user) {
      // Wait for authentication
      setTimeout(() => this.acceptPurchase(), 1000);
      return;
    }

    this.performPurchaseAcceptance();
  }
  
  private performPurchaseAcceptance(): void {
    const url = `${this.environmentService.apiUrl}/purchase/accept?requestId=${this.requestId}`;

    this.http.get(url, { responseType: 'text' }).subscribe({
      error: (error) => {
        // Handle successful response codes that Angular treats as errors
        if (error.status === 200 || error.status === 201 || error.status === 204) {
          this.success = true;
          this.loading = false;
          this.error = false;
          this.message = 'Purchase request approved successfully!';
          this.showToast('Purchase approved successfully!', 'success');
          this.fetchPendingRequests();
          
          setTimeout(() => {
            this.router.navigate(['/requests'], {
              queryParams: { status: 'success', type: 'purchase' }
            });
          }, 2000);
        } else {
          this.error = true;
          this.loading = false;
          this.success = false;
          this.message = 'Failed to approve purchase request. Please try again.';
        }
      },
      next: (response) => {
        this.success = true;
        this.loading = false;
        this.error = false;
        this.message = 'Purchase request approved successfully!';
        this.showToast('Purchase approved successfully!', 'success');
        this.fetchPendingRequests();
        
        setTimeout(() => {
          this.router.navigate(['/requests'], {
            queryParams: { status: 'success', type: 'purchase' }
          });
        }, 2000);
      }
    });
  }
  
  acceptGoogleWorkspaceLicense(): void {
    const user = this.authService.getCurrentUser();
    
    if (!user) {
      setTimeout(() => this.acceptGoogleWorkspaceLicense(), 1000);
      return;
    }

    this.performGoogleWorkspaceLicenseAcceptance();
  }
  
  private performGoogleWorkspaceLicenseAcceptance(): void {
    this.loading = true;
    const url = `${this.environmentService.apiUrl}/purchase/license/accept/signature-satori-credits?requestId=${this.requestId}&customerId=${this.customerId}`;

    this.http.get(url, { responseType: 'text' }).subscribe({
      error: (error) => {
        if (error.status === 200 || error.status === 201 || error.status === 204) {
          this.handleLicenseSuccess();
        } else {
          this.handleLicenseError(error.error?.message || 'License acceptance failed');
        }
      },
      next: (response) => {
        try {
          const jsonResponse = JSON.parse(response);
          if (jsonResponse.success === true || jsonResponse.status === 'success') {
            this.handleLicenseSuccess();
          } else {
            this.handleLicenseError(jsonResponse.message || 'License acceptance failed');
          }
        } catch (e) {
          // If response is not JSON, treat as success
          this.handleLicenseSuccess();
        }
      }
    });
  }
  
  private handleLicenseSuccess(): void {
    this.success = true;
    this.loading = false;
    this.error = false;
    this.message = 'License request approved successfully!';
    this.showToast('License approved successfully!', 'success');
    this.fetchPendingRequests();
    
    setTimeout(() => {
      this.router.navigate(['/requests'], {
        queryParams: { status: 'success', type: 'license' }
      });
    }, 2000);
  }
  
  private handleLicenseError(errorMessage: string): void {
    this.success = false;
    this.loading = false;
    this.error = true;
    this.message = errorMessage;
    this.showToast(errorMessage, 'error');
  }
  
  startPollingRequestStatus(): void {
    // Add a 2-second delay before starting polling to give backend time to process
    setTimeout(() => {
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
            
            // Fetch the requests anyway to see if the status was updated
            this.fetchPendingRequests();
            
            // Navigate back to the main requests page after a delay
            setTimeout(() => {
              this.router.navigate(['/requests']);
            }, 3000);
          }
        }
      });
    }, 2000);
  }
  
  checkRequestStatusForApproval(): void {
    const url = `${this.environmentService.apiUrl}/purchase/status`;
    
    this.http.get<any>(url, {
      params: { requestId: this.requestId },
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    }).pipe(
      catchError(error => {
        // For the last few attempts, try direct fetch as backup
        if (this.currentAttempt >= this.maxAttempts - 3) {
          this.fetchRequestDirectly();
        }
        return of(null);
      })
    ).subscribe(response => {
      if (response && response.status) {
        const currentStatus = response.status;
        
        // Store request details for success page
        if (response.userEmail) this.userEmail = response.userEmail;
        if (response.type === 'licenses') {
          this.licenseType = response.licenseType || '';
          this.count = response.quantity || 0;
          this.domain = response.domain || '';
          this.requestType = 'license';
        } else {
          this.count = response.quantity || 0;
          this.requestType = 'purchase';
        }
        
        if (currentStatus === 'APPROVED') {
          // Stop polling immediately
          if (this.statusCheckInterval) {
            this.statusCheckInterval.unsubscribe();
            this.statusCheckInterval = null;
          }
          
          // Show success state
          this.success = true;
          this.loading = false;
          this.error = false;
          
          if (this.requestType === 'license') {
            this.message = 'Your Google Workspace license has been successfully approved and is being processed.';
          } else {
            this.message = 'Your purchase has been successfully approved and is being processed.';
          }
          
          // Force refresh the purchase requests list
          this.fetchPendingRequests();
          
          // Navigate back to the main requests page after showing success message
          setTimeout(() => {
            this.router.navigate(['/requests'], {
              queryParams: {
                status: 'success',
                requestId: this.requestId,
                type: this.requestType
              }
            });
          }, 3000);
          
        } else if (currentStatus === 'REJECTED') {
          // Stop polling
          if (this.statusCheckInterval) {
            this.statusCheckInterval.unsubscribe();
            this.statusCheckInterval = null;
          }
          
          // Show error state
          this.error = true;
          this.loading = false;
          this.success = false;
          this.message = 'The request was rejected. Please contact support for assistance.';
          
          // Force refresh the purchase requests list
          this.fetchPendingRequests();
        }
        // For PENDING or AWAITING_CONFIRMATION - continue polling (no action needed)
        
      } else {
        // If we're near the end of attempts and still no valid response, try direct fetch
        if (this.currentAttempt >= this.maxAttempts - 3) {
          this.fetchRequestDirectly();
        }
      }
    });
  }
  
  /**
   * Fetch the request directly from the purchase-requests API as a fallback
   * if the status endpoints aren't working
   */
  private fetchRequestDirectly(): void {
    const directUrl = `${this.environmentService.apiUrl}/purchase-requests/${this.requestId}`;
    
    this.http.get<PurchaseRequest>(directUrl).pipe(
      catchError(error => {
        return of(null);
      })
    ).subscribe(request => {
      if (request) {
        // If request is approved, handle it like in the status check
        if (request.status === 'APPROVED') {
          // Stop polling
          if (this.statusCheckInterval) {
            this.statusCheckInterval.unsubscribe();
            this.statusCheckInterval = null;
          }
          
          // Show success state
          this.success = true;
          this.loading = false;
          
          if (request.type === 'licenses') {
            this.message = 'Your Google Workspace license has been successfully approved.';
          } else {
            this.message = 'Your purchase has been successfully approved.';
          }
          
          // Force refresh the purchase requests list
          this.fetchPendingRequests();
          
          // Navigate back to the main requests page after a delay
          setTimeout(() => {
            this.router.navigate(['/requests'], {
              queryParams: {
                status: 'success',
                requestId: this.requestId,
                type: request.type === 'licenses' ? 'license' : 'purchase'
              }
            });
          }, 2000);
        }
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
          // Navigate back to main requests page
          this.router.navigate(['/requests'], {
            queryParams: {
              status: 'success',
              requestId: requestId,
              type: 'purchase'
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
    // Check if user is authenticated before proceeding
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser) {
      // Wait for authentication with simple retry
      let attemptCount = 0;
      const maxAttempts = 10;
      
      const checkAuthInterval = setInterval(() => {
        attemptCount++;
        const user = this.authService.getCurrentUser();
        
        if (user) {
          clearInterval(checkAuthInterval);
          this.performLicenseApproval(requestId);
        } else if (attemptCount >= maxAttempts) {
          clearInterval(checkAuthInterval);
          
          // Show error state
          this.mode = 'license-error';
          this.error = true;
          this.loading = false;
          this.success = false;
          this.errorMessage = 'Authentication timeout. Please try again.';
          this.showToast('Authentication timeout. Please try again.', 'error');
        }
      }, 500);
      
      return;
    }
    
    // If already authenticated, proceed immediately
    this.performLicenseApproval(requestId);
  }
  
  private performLicenseApproval(requestId: string) {
    const url = `${this.environmentService.apiUrl}/purchase/accept?requestId=${requestId}`;
    
    // Show loading status while approval is in progress
    this.mode = 'approve-license';
    this.errorMessage = '';
    
    this.http.get(url, { responseType: 'text' as 'json' }).pipe(
      catchError(error => {
        if (error.status === 200) {
          // Switch to accept-purchase mode to show the success template
          this.mode = 'accept-purchase';
          this.requestType = 'license';
          
          // Show immediate success
          this.success = true;
          this.loading = false;
          this.error = false;
          this.message = 'Your license request has been successfully approved!';
          
          // Navigate after showing success
          setTimeout(() => {
            this.router.navigate(['/requests'], {
              queryParams: {
                status: 'success',
                requestId: this.requestId,
                type: 'license'
              }
            });
            this.showToast('License request successfully approved!', 'success');
          }, 2000);
          
        } else if (error.status === 400) {
          // Show error state
          this.mode = 'license-error';
          this.error = true;
          this.loading = false;
          this.success = false;
          this.errorMessage = error.error?.message || 'License approval failed';
          this.showToast('Error approving license: ' + this.errorMessage, 'error');
          
        } else {
          // Show error state for unknown responses
          this.mode = 'license-error';
          this.error = true;
          this.loading = false;
          this.success = false;
          this.errorMessage = 'Unexpected response from server';
          this.showToast('Unexpected response from server', 'error');
        }
        
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        try {
          // Parse the response as JSON
          const parsed = typeof response === 'string' ? JSON.parse(response) : response;
          
          // Check if the response indicates success
          if (parsed && (parsed.status === 'success' || parsed.status === 'SUCCESS')) {
            // Switch to accept-purchase mode to show the success template
            this.mode = 'accept-purchase';
            this.requestType = 'license';
            
            // Show immediate success
            this.success = true;
            this.loading = false;
            this.error = false;
            this.message = parsed.message || 'Your license request has been successfully approved!';
            
            // Navigate after showing success
            setTimeout(() => {
              this.router.navigate(['/requests'], {
                queryParams: {
                  status: 'success',
                  requestId: this.requestId,
                  type: 'license'
                }
              });
              this.showToast('License request successfully approved!', 'success');
            }, 2000);
            
          } else if (parsed && parsed.status === 'error') {
            // Show error state
            this.mode = 'license-error';
            this.error = true;
            this.loading = false;
            this.success = false;
            this.errorMessage = parsed.message || 'License approval failed';
            this.showToast('Error approving license: ' + this.errorMessage, 'error');
            
          } else {
            // Show error state for unknown responses
            this.mode = 'license-error';
            this.error = true;
            this.loading = false;
            this.success = false;
            this.errorMessage = 'Unexpected response from server';
            this.showToast('Unexpected response from server', 'error');
          }
          
        } catch (parseError) {
          // If parsing fails but we got a response, try to treat as success if response looks positive
          const responseText = String(response).toLowerCase();
          if (responseText.includes('success') || responseText.includes('approved') || responseText.includes('complete')) {
            this.mode = 'accept-purchase';
            this.requestType = 'license';
            this.success = true;
            this.loading = false;
            this.error = false;
            this.message = 'Your license request has been processed successfully!';
            
            setTimeout(() => {
              this.router.navigate(['/requests'], {
                queryParams: {
                  status: 'success',
                  requestId: this.requestId,
                  type: 'license'
                }
              });
              this.showToast('License request processed successfully!', 'success');
            }, 2000);
          } else {
            // Parse failed and response doesn't look positive
            this.mode = 'license-error';
            this.error = true;
            this.loading = false;
            this.success = false;
            this.errorMessage = 'Failed to parse server response';
            this.showToast('Failed to parse server response', 'error');
          }
        }
      } else {
        // No response - show error
        this.mode = 'license-error';
        this.error = true;
        this.loading = false;
        this.success = false;
        this.errorMessage = 'No response received from server';
        this.showToast('No response received from server', 'error');
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
      // Extract email domain
      const emailDomain = this.extractDomainFromEmail(currentUser.email || '');
      
      // First try to get the company information from TeamLeader
      if (emailDomain) {
        this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/domain/${emailDomain}`)
          .pipe(
            catchError(error => {
              // Add debugging: try to fetch all companies to see what's available
              this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies`)
                .subscribe({
                  next: (allCompanies) => {
                    // Debug info available but not logged
                  },
                  error: (debugError) => {
                    // Debug error handling without logging
                  }
                });
              
              return of(null);
            })
          )
          .subscribe(companyInfo => {
            // Check if the response indicates an error
            if (companyInfo && companyInfo.error) {
              this.setupUserInfoWithFallback(currentUser, emailDomain, null);
              return;
            }
            
            // If company info was found, use it
            if (companyInfo && companyInfo.name) {
              // Extract customer IDs from company data if available
              const customerIds = this.extractCustomerIds(companyInfo, emailDomain);
              
              // Map user info with company details
              this.userInfo = {
                ...currentUser,
                // Use the found company name
                company: companyInfo.name,
                companyData: companyInfo, // Store full company data for debugging
                // Add domain from email
                domain: emailDomain || 'example.com',
                // Use extracted or fallback customer IDs
                ...customerIds
              };
              
              // After getting user info, fetch licenses, credits, and SKUs
              this.fetchGoogleWorkspaceLicenses();
              this.fetchSignatureSatoriCredits();
              this.fetchAvailableSkus();
              this.fetchPendingRequests();
            } else {
              this.setupUserInfoWithFallback(currentUser, emailDomain, companyInfo);
            }
          });
      } else {
        this.setupUserInfoWithFallback(currentUser, null, null);
      }
    } else {
      this.showToast('Unable to retrieve user information. Please log in again.', 'error');
      this.isLoading = false;
      this.loadingError = true;
    }
  }
  
  /**
   * Extract customer IDs from company data or provide fallbacks based on domain
   */
  private extractCustomerIds(companyInfo: any, emailDomain: string | null): any {
    // Try to extract customer IDs from company custom fields or other properties
    let customerId = null;
    let googleWorkspaceCustomerId = null;
    
    // Check if company has custom fields with customer IDs
    if (companyInfo.customFields) {
      customerId = companyInfo.customFields.signatureSatoriCustomerId || 
                   companyInfo.customFields.satoriCustomerId ||
                   companyInfo.customFields.customerId;
      
      googleWorkspaceCustomerId = companyInfo.customFields.googleWorkspaceCustomerId ||
                                  companyInfo.customFields.googleCustomerId ||
                                  companyInfo.customFields.workspaceCustomerId;
    }
    
    // If no customer IDs found in company data, use domain-based fallbacks
    if (!customerId || !googleWorkspaceCustomerId) {
      // Domain-specific customer ID mapping (you may need to adjust these based on your actual data)
      const domainCustomerMapping = this.getDomainCustomerMapping(emailDomain);
      
      if (!customerId) {
        customerId = domainCustomerMapping.signatureSatori;
      }
      
      if (!googleWorkspaceCustomerId) {
        googleWorkspaceCustomerId = domainCustomerMapping.googleWorkspace;
      }
    }
    
    return {
      customerId,
      googleWorkspaceCustomerId
    };
  }

  /**
   * Get customer ID mapping based on email domain
   * This is a fallback when company data doesn't contain customer IDs
   */
  private getDomainCustomerMapping(emailDomain: string | null): any {
    // Default fallback customer IDs
    const defaultMapping = {
      signatureSatori: '535354',
      googleWorkspace: '363466'
    };
    
    // Add domain-specific mappings here if you have them
    const domainMappings: { [key: string]: any } = {
      'cloudmen.net': {
        signatureSatori: '535354',
        googleWorkspace: '363466'
      },
      // Add more domain mappings as needed
      // 'example.com': {
      //   signatureSatori: 'customer_id_for_example',
      //   googleWorkspace: 'workspace_id_for_example'
      // }
    };
    
    if (emailDomain && domainMappings[emailDomain.toLowerCase()]) {
      return domainMappings[emailDomain.toLowerCase()];
    }
    
    return defaultMapping;
  }

  /**
   * Setup user info with fallback data when company lookup fails
   */
  private setupUserInfoWithFallback(currentUser: any, emailDomain: string | null, companyInfo: any): void {
    // Use domain-based customer IDs as fallback
    const customerIds = this.getDomainCustomerMapping(emailDomain);
    
    // For gmail.com users, show a more helpful message
    let companyDisplayName = 'MyCLOUDMEN';
    let isGmailUser = emailDomain?.toLowerCase() === 'gmail.com';
    
    if (isGmailUser) {
      companyDisplayName = 'Your Organization';
    } else if (emailDomain) {
      // Try to create a reasonable company name from domain
      const derivedName = this.getCompanyNameFromDomain(emailDomain);
      if (derivedName && derivedName !== emailDomain) {
        companyDisplayName = derivedName;
      }
    }
    
    this.userInfo = {
      ...currentUser,
      // Use the appropriate fallback company name
      company: companyDisplayName,
      // Add domain from email
      domain: emailDomain || 'example.com',
      displayDomain: isGmailUser ? 'your-domain.com' : (emailDomain || 'your-domain.com'),
      isPublicEmail: isGmailUser,
      // Use fallback customer IDs
      customerId: customerIds.signatureSatori,
      googleWorkspaceCustomerId: customerIds.googleWorkspace,
      // Mark as fallback for debugging and UI hints
      _fallback: true,
      _reason: companyInfo ? 'invalid_company_data' : 'no_domain_or_lookup_failed',
      _isGmailUser: isGmailUser
    };
    
    // After getting user info, fetch licenses, credits, and SKUs
    this.fetchGoogleWorkspaceLicenses();
    this.fetchSignatureSatoriCredits();
    this.fetchAvailableSkus();
    this.fetchPendingRequests();
  }

  /**
   * Generate a reasonable company name from domain
   */
  private getCompanyNameFromDomain(domain: string | null): string | null {
    if (!domain) return null;
    
    // Convert domain to a reasonable company name
    // Remove common TLDs and convert to title case
    const baseName = domain.replace(/\.(com|net|org|io|co|uk|de|fr|nl|be)$/i, '');
    return baseName.charAt(0).toUpperCase() + baseName.slice(1);
  }

  /**
   * Helper function to extract domain from email
   */
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
    
    // Use the appropriate endpoint based on the user's role
    // Admins can see all requests, users can only see their own
    const endpoint = this.isAdmin() 
      ? `${this.environmentService.apiUrl}/purchase-requests` 
      : `${this.environmentService.apiUrl}/purchase-requests/user/${encodeURIComponent(userEmail || 'default@example.com')}`;
    
    this.http.get<PaginatedResponse<PurchaseRequestResponse>>(endpoint, {
      params: {
        page: '0',
        size: '50'
      }
    })
    .pipe(
      catchError(error => {
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
        type: this.formatRequestTypeFromResponse(item),
        quantity: item.quantity || 0,
        cost: item.cost || this.calculateCost(item),
        requestDate: new Date(item.requestDate).toISOString().split('T')[0],
        status: item.status
      }));
      
      // Clear cached data to force refresh
      this._displayedRequests = [];
      this._lastRequestsHash = '';
    });
  }
  
  // Helper to format the request type from API response
  private formatRequestTypeFromResponse(request: PurchaseRequestResponse): string {
    if (request.type === 'licenses' && request.licenseType) {
      return request.licenseType.replace('Google Workspace ', '');
    } else if (request.type === 'credits') {
      return 'Signature Credits';
    } else {
      return request.type || 'Unknown';
    }
  }
  
  // Helper to format the request type for display
  private formatRequestType(request: PurchaseRequest): string {
    // Convert internal type format to display format
    if (request.type.toLowerCase().includes('license')) {
      return 'Google Workspace License';
    } else if (request.type.toLowerCase().includes('credit')) {
      return 'Signature Satori Credits';
    }
    return request.type; // Fallback to original type
  }
  
  // Helper to calculate the cost based on request type
  private calculateCost(request: PurchaseRequestResponse): number {
    if (request.type === 'licenses' && request.licenseType) {
      const pricePerLicense = this.getLicensePriceValue(request.licenseType);
      return pricePerLicense * (request.quantity || 0);
    } else if (request.type === 'credits') {
      // €1.00 per credit (changed from $0.50)
      return (request.quantity || 0) * 1.0;
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
    
    // Find the corresponding SKU ID for this license type
    const sku = this.availableSkus.find(s => 
      s.skuName.toLowerCase().includes(licenseType.toLowerCase())
    );
    
    if (sku) {
      this.selectedSkuId = sku.skuId;
    } else {
      // Fallback to default mapping
      this.selectedSkuId = this.getSkuIdFromLicenseType(licenseType);
    }
    
    this.showLicenseModal = true;
  }
  
  /**
   * Get SKU ID from license type as fallback
   */
  private getSkuIdFromLicenseType(licenseType: string): string {
    const normalizedType = licenseType.toLowerCase();
    if (normalizedType.includes('starter')) {
      return '1010020020';
    } else if (normalizedType.includes('standard')) {
      return '1010020028';
    } else if (normalizedType.includes('plus')) {
      return '1010020025';
    } else if (normalizedType.includes('enterprise')) {
      return '1010060001';
    }
    return '1010020028'; // Default to Business Standard
  }
  
  openCreditsPurchaseModal(): void {
    this.purchaseQuantity = 10; // Changed from 100 to 10
    this.showCreditsModal = true;
  }

  closeModals(): void {
    this.showCreditsModal = false;
    this.showLicenseModal = false;
  }

  // Calculation functions
  calculateCreditsCost(): number {
    // €1.00 per credit (changed from $0.50)
    return this.purchaseQuantity * 1.0;
  }
  
  calculateLicenseCost(): number {
    // Use SKU price if available, otherwise fallback to hardcoded prices
    const sku = this.skuMap.get(this.selectedSkuId);
    if (sku && sku.price) {
      return sku.price.basePrice * this.purchaseLicenseQuantity;
    }
    
    // Fallback to hardcoded prices
    return this.getLicensePriceValue(this.selectedLicenseType) * this.purchaseLicenseQuantity;
  }

  // Purchase functions
  purchaseCredits(): void {
    // Validation - updated minimum to be more reasonable
    if (this.purchaseQuantity < 1) {
      this.showToast('Minimum purchase is 1 credit', 'error');
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
    
    // Send the purchase request to the correct purchase request endpoint (with email confirmation)
    this.http.post<any>(
      `${this.environmentService.apiUrl}/purchase/signature-satori/request?userEmail=${encodeURIComponent(currentUser.email)}&customerId=${customerId}`,
      creditsRequest
    ).subscribe({
      next: (response: any) => {
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
    
    // Create the license request DTO with SKU ID
    const licenseRequest = {
      count: this.purchaseLicenseQuantity,
      skuId: this.selectedSkuId, // Send the actual SKU ID
      licenseType: fullLicenseType, // Keep for backward compatibility
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
        // Refresh purchase requests list
        this.fetchPendingRequests();
      },
      error: (error) => {
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
   * Manually refresh purchase requests with cooldown to prevent spam
   */
  refreshPurchaseRequests(): void {
    const now = Date.now();
    if (now - this.lastFetchTime < this.FETCH_COOLDOWN) {
      this.showToast('Please wait before refreshing again', 'error');
      return;
    }
    
    this.lastFetchTime = now;
    this.showToast('Refreshing purchase requests...', 'success');
    this.fetchPendingRequests();
  }

  /**
   * Get displayedRequests with optimized caching
   * Optimized with caching to prevent unnecessary re-calculations
   */
  get displayedRequests(): any[] {
    // Create a hash of current requests to detect changes
    const currentHash = JSON.stringify(this.pendingRequests) + this.showAllRequests.toString();
    
    // Return cached data if nothing has changed
    if (this._lastRequestsHash === currentHash && this._displayedRequests.length > 0) {
      return this._displayedRequests;
    }
    
    // Update cache using optimized static methods
    this._lastRequestsHash = currentHash;
    this._displayedRequests = this.pendingRequests.slice(0, this.showAllRequests ? undefined : this.RECENT_DAYS_LIMIT).map(request => ({
      id: request.id,
      service: {
        picture: this.getServiceLogo(request.type),
        name: PurchaseRequestsComponent.getServiceNameStatic(request.type)
      },
      type: PurchaseRequestsComponent.formatRequestTypeStatic(request),
      quantity: `${request.quantity} ${PurchaseRequestsComponent.getQuantityUnitStatic(request.type)}`,
      cost: request.cost,
      status: request.status, // Use raw status for badge styling
      requestDate: request.requestDate,
      // Add original data for row click handling
      originalData: request
    }));
    
    return this._displayedRequests;
  }

  /**
   * Track by function for optimal rendering performance
   */
  trackByRequestId = (index: number, item: any): string => {
    return item.id || index.toString();
  };

  /**
   * Get CSS class based on request type
   */
  getRequestTypeClass(type: string): string {
    if (type.toLowerCase().includes('google') || type.toLowerCase().includes('workspace') || type.toLowerCase().includes('license')) {
      return 'google-request';
    }
    if (type.toLowerCase().includes('signature') || type.toLowerCase().includes('satori') || type.toLowerCase().includes('credit')) {
      return 'satori-request';
    }
    return 'general-request';
  }

  /**
   * Get icon for request type
   */
  getRequestIcon(type: string): string {
    if (type.toLowerCase().includes('google') || type.toLowerCase().includes('workspace') || type.toLowerCase().includes('license')) {
      return 'business';
    }
    if (type.toLowerCase().includes('signature') || type.toLowerCase().includes('satori') || type.toLowerCase().includes('credit')) {
      return 'verified';
    }
    return 'receipt';
  }

  /**
   * Format activity title
   */
  formatActivityTitle(request: PurchaseRequest): string {
    if (request.type.toLowerCase().includes('license')) {
      return `Workspace ${request.type} Request`;
    }
    if (request.type.toLowerCase().includes('credit')) {
      return 'Signature Credits Purchase';
    }
    return request.type;
  }

  /**
   * Format date for display
   */
  formatDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 1) {
      return 'Yesterday';
    } else if (diffDays < 7) {
      return `${diffDays} days ago`;
    } else {
      return date.toLocaleDateString('en-US', { 
        month: 'short', 
        day: 'numeric',
        year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined
      });
    }
  }

  /**
   * Get status icon
   */
  getStatusIcon(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PENDING':
        return 'schedule';
      case 'AWAITING_CONFIRMATION':
        return 'mail_outline';
      case 'APPROVED':
        return 'check_circle';
      case 'REJECTED':
        return 'cancel';
      default:
        return 'help_outline';
    }
  }

  /**
   * Format status for better readability - optimized as static method
   */
  private static formatStatusStatic(status: string): string {
    if (!status) return 'Unknown';
    
    switch (status.toUpperCase()) {
      case 'PENDING':
        return 'Pending';
      case 'AWAITING_CONFIRMATION':
        return 'Awaiting Confirmation';
      case 'APPROVED':
        return 'Approved';
      case 'REJECTED':
        return 'Rejected';
      default:
        // Convert to title case for any other status
        return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
    }
  }

  /**
   * Get quantity unit based on request type
   */
  getQuantityUnit(type: string): string {
    return PurchaseRequestsComponent.getQuantityUnitStatic(type);
  }

  /**
   * View request details - optimized to handle table row data properly
   */
  viewRequestDetails(rowData: any): void {
    // Handle the data table row click - rowData will be the transformed table row data
    const originalRequest = rowData.originalData || rowData;
    
    // You could implement a modal or navigation to details page here
    // For now, just show a toast with the request info
    const serviceName = this.getServiceName(originalRequest.type);
    this.showToast(`Viewing details for ${serviceName} request #${originalRequest.id}`, 'success');
  }

  /**
   * Load more requests
   */
  loadMoreRequests(): void {
    this.currentPage++;
    // Update hasMoreRequests based on whether there are more items
    const totalDisplayed = (this.currentPage + 1) * this.PAGE_SIZE;
    this.hasMoreRequests = totalDisplayed < this.pendingRequests.length;
  }

  /**
   * Get service logo URL based on request type
   */
  getServiceLogo(type: string): string {
    if (type.toLowerCase().includes('google') || type.toLowerCase().includes('workspace') || type.toLowerCase().includes('license')) {
      return 'https://crystalpng.com/wp-content/uploads/2025/05/google-logo.png';
    }
    if (type.toLowerCase().includes('signature') || type.toLowerCase().includes('satori') || type.toLowerCase().includes('credit')) {
      return 'https://www.cobry.co.uk/wp-content/uploads/2022/01/Asset-1satori-300x300-1.png';
    }
    return 'https://crystalpng.com/wp-content/uploads/2025/05/google-logo.png'; // Default to Google logo
  }

  /**
   * Get service name based on request type
   */
  getServiceName(type: string): string {
    return PurchaseRequestsComponent.getServiceNameStatic(type);
  }

  // Header action handler - simplified since refresh button is removed
  onHeaderAction(action: PageAction): void {
    // No actions since refresh button is removed
  }

  /**
   * Get service name based on request type - optimized as static method
   */
  private static getServiceNameStatic(type: string): string {
    if (type.toLowerCase().includes('google') || type.toLowerCase().includes('workspace') || type.toLowerCase().includes('license')) {
      return 'Workspace';
    }
    if (type.toLowerCase().includes('signature') || type.toLowerCase().includes('satori') || type.toLowerCase().includes('credit')) {
      return 'Signature';
    }
    return 'Workspace'; // Default
  }

  /**
   * Format request type - optimized as static method
   */
  private static formatRequestTypeStatic(request: PurchaseRequest): string {
    // Extract more specific information about the request type
    if (request.type.toLowerCase().includes('license')) {
      // For licenses, extract and format the license type if possible
      const licenseType = request.type.includes('-') ? 
        request.type.split('-')[1].trim() : 
        'Workspace License';
      return licenseType;
    } else if (request.type.toLowerCase().includes('credit')) {
      return 'Credits';
    }
    return request.type; // Fallback to original type
  }

  /**
   * Get quantity unit based on request type - optimized as static method
   */
  private static getQuantityUnitStatic(type: string): string {
    if (type.toLowerCase().includes('license')) {
      return 'licenses';
    }
    if (type.toLowerCase().includes('credit')) {
      return 'credits';
    }
    return 'items';
  }

  /**
   * Format status for display
   */
  formatStatus(status: string): string {
    return PurchaseRequestsComponent.formatStatusStatic(status);
  }

  /**
   * Enhanced displayedRequests with search, filtering, and pagination
   */
  get displayedActivityRequests(): any[] {
    const currentHash = JSON.stringify({
      requests: this.pendingRequests,
      searchTerm: this.activitySearchTerm,
      filter: this.activityFilter,
      currentPage: this.activityCurrentPage,
      pageSize: this.activityPageSize,
      sortColumn: this.activitySortColumn,
      sortDirection: this.activitySortDirection
    });

    // Return cached data if nothing has changed
    if (this._lastActivityHash === currentHash && this._displayedActivityRequests.length > 0) {
      return this._displayedActivityRequests;
    }

    this._lastActivityHash = currentHash;
    
    // Apply search and filters
    this.filteredActivityRequests = this.applyActivityFilters(this.pendingRequests);
    this.activityTotalItems = this.filteredActivityRequests.length;
    
    // Apply pagination
    const startIndex = (this.activityCurrentPage - 1) * this.activityPageSize;
    const endIndex = startIndex + this.activityPageSize;
    const paginatedRequests = this.filteredActivityRequests.slice(startIndex, endIndex);
    
    // Transform for display
    this._displayedActivityRequests = paginatedRequests.map(request => ({
      id: request.id,
      service: {
        picture: this.getServiceLogo(request.type),
        name: PurchaseRequestsComponent.getServiceNameStatic(request.type)
      },
      type: PurchaseRequestsComponent.formatRequestTypeStatic(request),
      quantity: `${request.quantity} ${PurchaseRequestsComponent.getQuantityUnitStatic(request.type)}`,
      cost: request.cost,
      status: request.status,
      requestDate: request.requestDate,
      // Don't include originalData to prevent clicking
      clickable: false
    }));

    return this._displayedActivityRequests;
  }

  /**
   * Apply search term and filters to the requests
   */
  private applyActivityFilters(requests: PurchaseRequest[]): PurchaseRequest[] {
    let filtered = [...requests];

    // Apply search term
    if (this.activitySearchTerm.trim()) {
      const searchLower = this.activitySearchTerm.toLowerCase();
      filtered = filtered.filter(request => 
        request.type.toLowerCase().includes(searchLower) ||
        request.status.toLowerCase().includes(searchLower) ||
        request.id.toLowerCase().includes(searchLower) ||
        PurchaseRequestsComponent.getServiceNameStatic(request.type).toLowerCase().includes(searchLower)
      );
    }

    // Apply status filter
    if (this.activityFilter.status !== 'all') {
      filtered = filtered.filter(request => request.status === this.activityFilter.status);
    }

    // Apply type filter - improved logic to properly identify license vs credit requests
    if (this.activityFilter.type !== 'all') {
      if (this.activityFilter.type === 'license') {
        filtered = filtered.filter(request => {
          // Check if this is a license request by looking for license-related keywords
          // or if the service name indicates it's a workspace service
          const type = request.type.toLowerCase();
          const serviceName = PurchaseRequestsComponent.getServiceNameStatic(request.type).toLowerCase();
          return type.includes('license') || 
                 type.includes('workspace') || 
                 type.includes('business') || 
                 type.includes('starter') || 
                 type.includes('standard') || 
                 type.includes('plus') ||
                 type.includes('enterprise') ||
                 serviceName === 'workspace';
        });
      } else if (this.activityFilter.type === 'credit') {
        filtered = filtered.filter(request => {
          // Check if this is a credit request
          const type = request.type.toLowerCase();
          const serviceName = PurchaseRequestsComponent.getServiceNameStatic(request.type).toLowerCase();
          return type.includes('credit') || 
                 type.includes('satori') || 
                 type.includes('signature') ||
                 serviceName === 'signature';
        });
      }
    }

    // Apply date range filter
    if (this.activityFilter.dateRange !== 'all') {
      const daysAgo = parseInt(this.activityFilter.dateRange);
      const cutoffDate = new Date();
      cutoffDate.setDate(cutoffDate.getDate() - daysAgo);
      
      filtered = filtered.filter(request => {
        const requestDate = new Date(request.requestDate);
        return requestDate >= cutoffDate;
      });
    }

    // Apply sorting if specified
    if (this.activitySortColumn) {
      filtered.sort((a, b) => {
        let comparison = 0;
        
        switch (this.activitySortColumn) {
          case 'type':
            comparison = PurchaseRequestsComponent.formatRequestTypeStatic(a).localeCompare(
              PurchaseRequestsComponent.formatRequestTypeStatic(b)
            );
            break;
          case 'cost':
            comparison = (a.cost || 0) - (b.cost || 0);
            break;
          case 'status':
            comparison = a.status.localeCompare(b.status);
            break;
          case 'requestDate':
            comparison = new Date(a.requestDate).getTime() - new Date(b.requestDate).getTime();
            break;
          default:
            // Default to date sorting if column not recognized
            comparison = new Date(a.requestDate).getTime() - new Date(b.requestDate).getTime();
        }
        
        return this.activitySortDirection === 'asc' ? comparison : -comparison;
      });
    } else {
      // Default: Sort by date (newest first) when no explicit sorting
      filtered.sort((a, b) => new Date(b.requestDate).getTime() - new Date(a.requestDate).getTime());
    }

    return filtered;
  }

  /**
   * Handle activity search input
   */
  onActivitySearch(searchTerm: string): void {
    this.activitySearchTerm = searchTerm;
    this.activityCurrentPage = 1; // Reset to first page
    this.clearActivityCache();
  }

  /**
   * Handle table sorting
   */
  onSort(event: SortEvent): void {
    this.activitySortColumn = event.column;
    this.activitySortDirection = event.direction;
    this.activityCurrentPage = 1; // Reset to first page
    this.clearActivityCache();
  }

  /**
   * Handle activity filter changes
   */
  onActivityFilterChange(): void {
    this.activityCurrentPage = 1; // Reset to first page
    this.clearActivityCache();
  }

  /**
   * Handle activity pagination
   */
  onActivityPageChange(event: PaginationEvent): void {
    this.activityCurrentPage = event.pageIndex + 1; // Convert 0-based to 1-based
    this.activityPageSize = event.pageSize;
    this.clearActivityCache();
  }

  /**
   * Clear activity filters and search
   */
  clearActivityFilters(): void {
    this.activitySearchTerm = '';
    this.activityFilter = {
      status: 'all',
      type: 'all',
      dateRange: 'all'
    };
    this.activityCurrentPage = 1;
    this.clearActivityCache();
  }

  /**
   * Clear activity cache to force refresh
   */
  private clearActivityCache(): void {
    this._lastActivityHash = '';
    this._displayedActivityRequests = [];
  }

  /**
   * Check if any filters are active
   */
  get hasActiveActivityFilters(): boolean {
    return this.activitySearchTerm.trim() !== '' ||
           this.activityFilter.status !== 'all' ||
           this.activityFilter.type !== 'all' ||
           this.activityFilter.dateRange !== 'all';
  }

  /**
   * Get activity filter summary for display
   */
  get activityFilterSummary(): string {
    const active = [];
    if (this.activitySearchTerm.trim()) active.push(`"${this.activitySearchTerm}"`);
    if (this.activityFilter.status !== 'all') {
      const statusLabel = this.activityStatusOptions.find(opt => opt.value === this.activityFilter.status)?.label;
      active.push(statusLabel);
    }
    if (this.activityFilter.type !== 'all') {
      const typeLabel = this.activityTypeOptions.find(opt => opt.value === this.activityFilter.type)?.label;
      active.push(typeLabel);
    }
    if (this.activityFilter.dateRange !== 'all') {
      const dateLabel = this.activityDateOptions.find(opt => opt.value === this.activityFilter.dateRange)?.label;
      active.push(dateLabel);
    }
    
    return active.length > 0 ? `Filtered by: ${active.join(', ')}` : '';
  }

  /**
   * Fetch available Google Workspace SKUs from the backend
   */
  fetchAvailableSkus(): void {
    this.http.get<SkuResponse>(`${this.environmentService.apiUrl}/google-workspace/skus`)
      .pipe(
        catchError(error => {
          this.showToast('Failed to load license types', 'error');
          // Return fallback SKU data
          return of({
            kind: 'reseller#skus',
            skus: [
              {
                skuId: '1010020020',
                skuName: 'Google Workspace Business Starter',
                description: 'Google Workspace Business Starter plan with 30GB storage',
                plans: ['ANNUAL', 'FLEXIBLE', 'TRIAL'],
                price: {
                  basePrice: 6.0,
                  currency: 'USD',
                  interval: 'MONTHLY'
                }
              },
              {
                skuId: '1010020028',
                skuName: 'Google Workspace Business Standard',
                description: 'Google Workspace Business Standard plan with 2TB storage',
                plans: ['ANNUAL', 'FLEXIBLE', 'TRIAL'],
                price: {
                  basePrice: 12.0,
                  currency: 'USD',
                  interval: 'MONTHLY'
                }
              },
              {
                skuId: '1010020025',
                skuName: 'Google Workspace Business Plus',
                description: 'Google Workspace Business Plus plan with 5TB storage',
                plans: ['ANNUAL', 'FLEXIBLE', 'TRIAL'],
                price: {
                  basePrice: 18.0,
                  currency: 'USD',
                  interval: 'MONTHLY'
                }
              }
            ]
          });
        })
      )
      .subscribe(response => {
        if (response && response.skus) {
          this.availableSkus = response.skus;
          
          // Build SKU map for quick lookups
          this.skuMap.clear();
          this.availableSkus.forEach(sku => {
            this.skuMap.set(sku.skuId, sku);
          });
        }
      });
  }
} 