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
import { DataTableComponent, TableColumn, TableAction } from '../../../shared/components/data-table/data-table.component';

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
  
  // Cached displayed data to prevent constant refreshing
  private _displayedRequests: any[] = [];
  private _lastRequestsHash: string = '';
  
  // Modal states
  showCreditsModal = false;
  showLicenseModal = false;
  purchaseQuantity = 10; // Changed from 100 to 10 for credits
  purchaseLicenseQuantity = 1; // For licenses
  selectedLicenseType = 'Business Standard';
  
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
      sortable: false
    },
    {
      key: 'requestDate',
      label: 'Date',
      type: 'date',
      sortable: true
    }
  ];

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
    console.log(`[DEBUG] ===== HANDLE MODE ACTIONS =====`);
    console.log(`[DEBUG] Current mode: '${this.mode}'`);
    console.log(`[DEBUG] Request ID: '${this.requestId}'`);
    
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
        console.log(`[DEBUG] ✅ APPROVE-LICENSE MODE DETECTED!`);
        console.log(`[DEBUG] About to call approveLicense with requestId: '${this.requestId}'`);
        // Call API to approve license purchase with requestId from URL
        if (this.requestId) {
          // We're already showing the approval page in the UI
          // approveLicense will handle the transition to success or error
          this.approveLicense(this.requestId);
        } else {
          // If no requestId, show error
          console.log(`[DEBUG] ❌ NO REQUEST ID - showing error`);
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
          this.router.navigate(['/requests']);
        }, 3000);
        break;
        
      case 'purchase-error':
      case 'license-error':
        // Just display error message, handled in template
        // No toast needed since we're showing a full error page
        break;
        
      default:
        console.log(`[DEBUG] ⚠️ DEFAULT MODE: '${this.mode}'`);
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
    
    console.log(`[DEBUG] Accepting purchase request with ID: ${this.requestId}`);
    console.log(`[DEBUG] API URL: ${url}`);
    
    // Send HTTP request to the backend API - ensure proper response handling
    this.http.get(url, { 
      params: { requestId: this.requestId }
    }).pipe(
      catchError(error => {
        console.error('[DEBUG] Error in acceptPurchase:', error);
        console.error('[DEBUG] Error status:', error.status);
        console.error('[DEBUG] Error message:', error.error);
        
        // Specific handling for 200 OK responses that might be treated as errors
        if (error.status === 200) {
          console.log('[DEBUG] Got 200 status but was treated as error, continuing with processing');
          try {
            // Try to parse the response body if available
            const responseBody = error.error?.text || error.error || null;
            if (responseBody) {
              return of(responseBody);
            }
          } catch (parseError) {
            console.error('[DEBUG] Failed to parse error response:', parseError);
          }
        }
        
        this.error = true;
        this.message = error.error || 'An error occurred while processing your purchase. Please try again later.';
        this.loading = false; // Stop loading on error
        return of(null);
      })
    ).subscribe(response => {
      console.log('[DEBUG] acceptPurchase raw response:', response);
      
      if (response) {
        try {
          // Try to parse the response as JSON if it's a string
          const jsonResponse = typeof response === 'string' ? JSON.parse(response) : response;
          console.log('[DEBUG] acceptPurchase parsed response:', jsonResponse);
          
          // Check if the response indicates success
          if (jsonResponse && jsonResponse.status === 'success') {
            console.log('[DEBUG] Purchase accepted successfully, starting status polling');
            // After initiating acceptance, start polling for status changes
            this.startPollingRequestStatus();
          } else {
            console.error('[DEBUG] Purchase acceptance failed:', jsonResponse);
            this.error = true;
            this.loading = false;
            this.message = (jsonResponse && jsonResponse.message) || 'An error occurred while processing your purchase.';
          }
        } catch (e) {
          console.error('[DEBUG] Error parsing response:', e);
          console.log('[DEBUG] Continuing with status polling despite parsing error');
          // If response is not valid JSON, still try to continue
          this.startPollingRequestStatus();
        }
      } else {
        // If response is null (due to error), don't start polling
        console.log('[DEBUG] No response received, not starting status polling');
      }
    });
  }
  
  acceptGoogleWorkspaceLicense(): void {
    // Continue to show loading state while we make the API call
    this.loading = true;
    
    // **SIMPLIFIED - Use the same endpoint as purchase acceptance**
    const url = `${this.environmentService.apiUrl}/purchase/accept`;
    
    console.log(`[DEBUG] Accepting license request with ID: ${this.requestId}, Customer ID: ${this.customerId}`);
    console.log(`[DEBUG] Using simplified API URL: ${url}`);
    
    // Send HTTP request to the backend API - ensure proper response handling
    this.http.get(url, { 
      params: { 
        requestId: this.requestId
      }
    }).pipe(
      catchError(error => {
        console.error('[DEBUG] Error in acceptGoogleWorkspaceLicense:', error);
        console.error('[DEBUG] Error status:', error.status);
        console.error('[DEBUG] Error message:', error.error);
        
        // Specific handling for 200 OK responses that might be treated as errors
        if (error.status === 200) {
          console.log('[DEBUG] Got 200 status but was treated as error, continuing with processing');
          try {
            // Try to parse the response body if available
            const responseBody = error.error?.text || error.error || null;
            if (responseBody) {
              return of(responseBody);
            }
          } catch (parseError) {
            console.error('[DEBUG] Failed to parse error response:', parseError);
          }
        }
        
        this.error = true;
        this.message = error.error || 'An error occurred while processing your license. Please try again later.';
        this.loading = false; // Stop loading on error
        return of(null);
      })
    ).subscribe(response => {
      console.log('[DEBUG] acceptGoogleWorkspaceLicense raw response:', response);
      
      if (response) {
        try {
          // Try to parse the response as JSON if it's a string
          const jsonResponse = typeof response === 'string' ? JSON.parse(response) : response;
          console.log('[DEBUG] acceptGoogleWorkspaceLicense parsed response:', jsonResponse);
          
          // Check if the response indicates success
          if (jsonResponse && jsonResponse.status === 'success') {
            console.log('[DEBUG] License accepted successfully, starting status polling');
            // After initiating acceptance, start polling for status changes
            this.startPollingRequestStatus();
          } else {
            console.error('[DEBUG] License acceptance failed:', jsonResponse);
            this.error = true;
            this.loading = false;
            this.message = (jsonResponse && jsonResponse.message) || 'An error occurred while processing your license.';
          }
        } catch (e) {
          console.error('[DEBUG] Error parsing response:', e);
          console.log('[DEBUG] Continuing with status polling despite parsing error');
          // If response is not valid JSON, still try to continue
          this.startPollingRequestStatus();
        }
      } else {
        // If response is null (due to error), don't start polling
        console.log('[DEBUG] No response received, not starting status polling');
      }
    });
  }
  
  startPollingRequestStatus(): void {
    console.log(`[DEBUG] Starting to poll for request status. RequestID: ${this.requestId}, Type: ${this.requestType}`);
    console.log(`[DEBUG] Will check status every 3 seconds, max ${this.maxAttempts} attempts`);
    
    // Poll every 3 seconds to check if request has been approved
    this.statusCheckInterval = interval(3000).subscribe(() => {
      // Log start of this polling attempt
      console.log(`[DEBUG] Poll attempt ${this.currentAttempt + 1}/${this.maxAttempts} for requestId: ${this.requestId}`);
      
      this.checkRequestStatusForApproval();
      
      // Increment attempt counter
      this.currentAttempt++;
      
      // Stop polling after maximum attempts
      if (this.currentAttempt >= this.maxAttempts) {
        if (this.statusCheckInterval) {
          this.statusCheckInterval.unsubscribe();
          this.statusCheckInterval = null;
          console.log(`[DEBUG] Reached max attempts (${this.maxAttempts}). Stopping polling.`);
        }
        
        // Show error if we've reached max attempts without success
        if (!this.success && !this.error) {
          this.error = true;
          this.loading = false;
          this.message = 'The approval process is taking longer than expected. Please check your purchase requests page for status updates.';
          console.log(`[DEBUG] Max attempts reached without success or error. Showing timeout message.`);
          
          // Fetch the requests anyway to see if the status was updated
          this.fetchPendingRequests();
          
          // Navigate back to the main requests page after a delay
          setTimeout(() => {
            console.log('[DEBUG] Redirecting to /requests after timeout');
            this.router.navigate(['/requests']);
          }, 3000);
        }
      }
    });
  }
  
  checkRequestStatusForApproval(): void {
    // **SIMPLIFIED - Use only purchase/status endpoint for all types**
    const url = `${this.environmentService.apiUrl}/purchase/status`;
    
    console.log(`[DEBUG] === STATUS CHECK ATTEMPT ${this.currentAttempt + 1}/${this.maxAttempts} ===`);
    console.log(`[DEBUG] Checking request status - URL: ${url}, RequestID: ${this.requestId}`);
    
    this.http.get<any>(url, {
      params: { requestId: this.requestId },
      // Add headers to ensure we get JSON response
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    }).pipe(
      catchError(error => {
        console.error('[DEBUG] === STATUS CHECK ERROR ===');
        console.error('[DEBUG] Error checking request status:', error);
        console.error('[DEBUG] Error details:', {
          status: error.status,
          statusText: error.statusText,
          url: error.url,
          message: error.message,
          error: error.error
        });
        console.error('[DEBUG] Attempt:', this.currentAttempt + 1, 'of', this.maxAttempts);
        
        // For the last few attempts, try direct fetch as backup
        if (this.currentAttempt >= this.maxAttempts - 3) {
          console.log('[DEBUG] Near max attempts - trying direct fetch as backup');
          this.fetchRequestDirectly();
        }
        
        return of(null);
      })
    ).subscribe(response => {
      console.log('[DEBUG] === STATUS CHECK RESPONSE ===');
      console.log('[DEBUG] Raw response:', response);
      console.log('[DEBUG] Response type:', typeof response);
      console.log('[DEBUG] Attempt:', this.currentAttempt + 1, 'of', this.maxAttempts);
      
      if (response && response.status) {
        const currentStatus = response.status;
        console.log(`[DEBUG] Current request status: "${currentStatus}"`);
        
        // Store request details for success page
        if (response.userEmail) this.userEmail = response.userEmail;
        if (response.type === 'licenses') {
          this.licenseType = response.licenseType || '';
          this.count = response.quantity || 0;
          this.domain = response.domain || '';
          this.requestType = 'license';
          console.log(`[DEBUG] License details - Type: ${this.licenseType}, Count: ${this.count}, Domain: ${this.domain}`);
        } else {
          this.count = response.quantity || 0;
          this.requestType = 'purchase';
          console.log(`[DEBUG] Purchase details - Count: ${this.count}, Type: ${response.type}`);
        }
        
        // **SIMPLIFIED STATUS CHECKING**
        console.log(`[DEBUG] Checking if "${currentStatus}" === "APPROVED"`);
        
        if (currentStatus === 'APPROVED') {
          console.log('[DEBUG] ✅ REQUEST APPROVED! Stopping polling and showing success.');
          
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
          
          console.log('[DEBUG] Success message set:', this.message);
          
          // Force refresh the purchase requests list to show the updated status
          console.log('[DEBUG] Refreshing purchase requests list');
          this.fetchPendingRequests();
          
          // Navigate back to the main requests page after showing success message
          setTimeout(() => {
            console.log('[DEBUG] Navigating to /requests with success parameters');
            this.router.navigate(['/requests'], {
              queryParams: {
                status: 'success',
                requestId: this.requestId,
                type: this.requestType
              }
            });
          }, 3000); // Longer delay to show success message
          
        } else if (currentStatus === 'REJECTED') {
          console.log('[DEBUG] ❌ REQUEST REJECTED! Stopping polling and showing error.');
          
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
          
        } else {
          // PENDING or AWAITING_CONFIRMATION - continue polling
          console.log(`[DEBUG] ⏳ Request still "${currentStatus}", continuing to poll...`);
          
          if (currentStatus !== 'PENDING' && currentStatus !== 'AWAITING_CONFIRMATION') {
            console.warn(`[DEBUG] ⚠️  Unrecognized status: "${currentStatus}". Valid statuses: PENDING, AWAITING_CONFIRMATION, APPROVED, REJECTED`);
          }
        }
        
      } else {
        console.log('[DEBUG] ❌ No valid response received from status check');
        console.log('[DEBUG] Response object:', response);
        
        // If we're near the end of attempts and still no valid response, try direct fetch
        if (this.currentAttempt >= this.maxAttempts - 3) {
          console.log('[DEBUG] No valid response near max attempts - trying direct fetch');
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
    console.log(`[DEBUG] Fetching request directly from: ${directUrl}`);
    
    this.http.get<PurchaseRequest>(directUrl).pipe(
      catchError(error => {
        console.error('[DEBUG] Direct fetch failed:', error);
        return of(null);
      })
    ).subscribe(request => {
      if (request) {
        console.log('[DEBUG] Direct fetch successful:', request);
        console.log(`[DEBUG] Direct fetch status: "${request.status}"`);
        
        // If request is approved, handle it like in the status check
        if (request.status === 'APPROVED') {
          console.log('[DEBUG] Request is APPROVED according to direct fetch!');
          
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
    console.log(`[DEBUG] ===== APPROVE LICENSE START =====`);
    console.log(`[DEBUG] Request ID: ${requestId}`);
    console.log(`[DEBUG] this.requestId: ${this.requestId}`);
    console.log(`[DEBUG] Current mode: ${this.mode}`);
    
    // **SIMPLIFIED - Use the same endpoint as purchase acceptance**
    const url = `${this.environmentService.apiUrl}/purchase/accept?requestId=${requestId}`;
    
    console.log(`[DEBUG] Full URL: ${url}`);
    console.log(`[DEBUG] Environment API URL: ${this.environmentService.apiUrl}`);
    
    // Show loading status while approval is in progress
    this.mode = 'approve-license';
    this.errorMessage = '';
    
    console.log(`[DEBUG] About to make HTTP request...`);
    
    // Don't navigate away immediately, wait for response
    this.http.get(url).subscribe({
      next: (response: any) => {
        console.log(`[DEBUG] ===== HTTP RESPONSE RECEIVED =====`);
        console.log('[DEBUG] Raw response:', response);
        console.log('[DEBUG] Response type:', typeof response);
        console.log('[DEBUG] Response.status:', response?.status);
        console.log('[DEBUG] Response.message:', response?.message);
        console.log('[DEBUG] Full response object:', JSON.stringify(response, null, 2));
        
        try {
          // Check if the response indicates success
          if (response && response.status === 'success') {
            console.log('[DEBUG] ✅ SUCCESS! Response indicates success');
            console.log('[DEBUG] License approved successfully!');
            
            console.log('[DEBUG] About to navigate to /requests...');
            // Navigate back to main requests page
            this.router.navigate(['/requests'], {
              queryParams: {
                status: 'success',
                requestId: this.requestId,
                type: 'license'
              }
            });
            // Show toast notification
            this.showToast('License request successfully approved!', 'success');
            console.log('[DEBUG] Navigation and toast completed');
            return;
          } else {
            console.log('[DEBUG] ❌ FAILURE! Response does not indicate success');
            console.error('[DEBUG] License approval failed:', response);
            this.errorMessage = response?.message || 'License approval failed';
            this.mode = 'license-error';
            this.showToast('Error approving license: ' + this.errorMessage, 'error');
          }
        } catch (e) {
          console.error('[DEBUG] ⚠️ EXCEPTION in response processing:', e);
          console.error('[DEBUG] Error processing license approval response:', e);
          console.log('[DEBUG] Treating as success anyway due to parsing issue...');
          // If we get here, treat as success anyway (might be parsing issue)
          this.router.navigate(['/requests'], {
            queryParams: {
              status: 'success',
              requestId: this.requestId,
              type: 'license'
            }
          });
          this.showToast('License request processed!', 'success');
        }
      },
      error: (error) => {
        console.log(`[DEBUG] ===== HTTP ERROR RECEIVED =====`);
        console.error('[DEBUG] License approval HTTP error:', error);
        console.error('[DEBUG] Error status:', error.status);
        console.error('[DEBUG] Error statusText:', error.statusText);
        console.error('[DEBUG] Error message:', error.message);
        console.error('[DEBUG] Error error object:', error.error);
        console.error('[DEBUG] Full error object:', JSON.stringify(error, null, 2));
        
        if (error.status === 200) {
          // If somehow we got a 200 status as an error, treat it as success
          console.log('[DEBUG] ✅ Got 200 status as error - treating as success');
          this.router.navigate(['/requests'], {
            queryParams: {
              status: 'success',
              requestId: this.requestId || `req-${Math.floor(Math.random() * 10000)}`,
              type: 'license'
            }
          });
          this.showToast('License request successfully approved!', 'success');
          return;
        }
        
        // Show error page
        console.error('[DEBUG] ❌ Showing error page for license approval');
        this.errorMessage = error.message || error.error?.message || 'Unknown error occurred';
        this.mode = 'license-error';
        this.showToast('Error approving license: ' + this.errorMessage, 'error');
      }
    });
    
    console.log(`[DEBUG] ===== HTTP REQUEST SENT =====`);
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
      console.log('Extracted email domain:', emailDomain);
      
      // First try to get the company information from TeamLeader
      if (emailDomain) {
        console.log(`[DEBUG] Making API call to: ${this.environmentService.apiUrl}/teamleader/companies/domain/${emailDomain}`);
        
        this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies/domain/${emailDomain}`)
          .pipe(
            catchError(error => {
              console.error(`Error fetching company by domain ${emailDomain}:`, error);
              console.error('API response:', error.error);
              console.error('Full error object:', error);
              
              // Add debugging: try to fetch all companies to see what's available
              console.log('[DEBUG] Domain lookup failed, fetching all companies for debugging...');
              this.http.get<any>(`${this.environmentService.apiUrl}/teamleader/companies`)
                .subscribe({
                  next: (allCompanies) => {
                    console.log('[DEBUG] All companies in database:', allCompanies);
                    if (allCompanies.companies && Array.isArray(allCompanies.companies)) {
                      console.log(`[DEBUG] Found ${allCompanies.companies.length} companies total`);
                      allCompanies.companies.forEach((company: any, index: number) => {
                        console.log(`[DEBUG] Company ${index + 1}:`, {
                          name: company.name,
                          teamleaderId: company.teamleaderId,
                          contactInfo: company.contactInfo || 'No contact info',
                          website: company.website || 'No website'
                        });
                      });
                    }
                  },
                  error: (debugError) => {
                    console.error('[DEBUG] Failed to fetch all companies:', debugError);
                  }
                });
              
              return of(null);
            })
          )
          .subscribe(companyInfo => {
            console.log('Company domain lookup response:', companyInfo);
            
            // Check if the response indicates an error
            if (companyInfo && companyInfo.error) {
              console.log('Company not found for domain:', emailDomain, 'Error:', companyInfo.message);
              this.setupUserInfoWithFallback(currentUser, emailDomain, null);
              return;
            }
            
            // If company info was found, use it
            if (companyInfo && companyInfo.name) {
              console.log('Found company:', companyInfo.name, 'for domain:', emailDomain);
              
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
              
              console.log('User info prepared with company data:', this.userInfo);
              
              // After getting user info, fetch licenses and credits
              this.fetchGoogleWorkspaceLicenses();
              this.fetchSignatureSatoriCredits();
              this.fetchPendingRequests();
            } else {
              console.log('No valid company data found in response, using fallback');
              this.setupUserInfoWithFallback(currentUser, emailDomain, companyInfo);
            }
          });
      } else {
        console.log('No email domain could be extracted, using fallback');
        this.setupUserInfoWithFallback(currentUser, null, null);
      }
    } else {
      console.error('No user logged in or user info not available');
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
      console.log('No customer IDs found in company data, using domain-based fallbacks');
      
      // Domain-specific customer ID mapping (you may need to adjust these based on your actual data)
      const domainCustomerMapping = this.getDomainCustomerMapping(emailDomain);
      
      if (!customerId) {
        customerId = domainCustomerMapping.signatureSatori;
      }
      
      if (!googleWorkspaceCustomerId) {
        googleWorkspaceCustomerId = domainCustomerMapping.googleWorkspace;
      }
    }
    
    console.log('Extracted customer IDs:', {
      customerId,
      googleWorkspaceCustomerId,
      source: companyInfo.customFields ? 'company_data' : 'domain_mapping'
    });
    
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
    console.log('Setting up user info with fallback data');
    
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
    
    console.log('User info prepared with fallback:', this.userInfo);
    
    // After getting user info, fetch licenses and credits
    this.fetchGoogleWorkspaceLicenses();
    this.fetchSignatureSatoriCredits();
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
        type: this.formatRequestTypeFromResponse(item),
        quantity: item.quantity || 0,
        cost: item.cost || this.calculateCost(item),
        requestDate: new Date(item.requestDate).toISOString().split('T')[0],
        status: item.status
      }));
      
      // Clear cached data to force refresh
      this._displayedRequests = [];
      this._lastRequestsHash = '';
      
      console.log('Fetched purchase requests:', this.pendingRequests);
    });
  }
  
  // Helper to format the request type from API response
  private formatRequestTypeFromResponse(request: PurchaseRequestResponse): string {
    if (request.type === 'licenses' && request.licenseType) {
      return `Google Workspace - ${request.licenseType}`;
    } else if (request.type === 'credits') {
      return 'Signature Satori Credits';
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
    this.showLicenseModal = true;
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
    // Cost = price per license * quantity
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
   * Manually refresh purchase requests with cooldown to prevent spam
   */
  refreshPurchaseRequests(): void {
    const now = Date.now();
    if (now - this.lastFetchTime < this.FETCH_COOLDOWN) {
      this.showToast('Please wait before refreshing again', 'error');
      return;
    }
    
    this.lastFetchTime = now;
    console.log('Manually refreshing purchase requests');
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
      return `Google Workspace ${request.type} Request`;
    }
    if (request.type.toLowerCase().includes('credit')) {
      return 'Signature Satori Credits Purchase';
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
    console.log('Viewing request details:', originalRequest);
    
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
    console.log('Header action triggered:', action);
  }

  /**
   * Get service name based on request type - optimized as static method
   */
  private static getServiceNameStatic(type: string): string {
    if (type.toLowerCase().includes('google') || type.toLowerCase().includes('workspace') || type.toLowerCase().includes('license')) {
      return 'Google Workspace';
    }
    if (type.toLowerCase().includes('signature') || type.toLowerCase().includes('satori') || type.toLowerCase().includes('credit')) {
      return 'Signature Satori';
    }
    return 'Google Workspace'; // Default
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
        'License';
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
} 