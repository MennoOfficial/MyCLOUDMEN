import { Component, OnInit, OnDestroy, ElementRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from '../../../core/services/environment.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthenticationLog, PageResponse } from '../../../core/models/user.model';
import { Subscription } from 'rxjs';

// Import standardized components
import { PageHeaderComponent, PageAction } from '../../../shared/components/page-header/page-header.component';
import { SearchFilterComponent, FilterConfig, SearchFilterEvent } from '../../../shared/components/search-filter/search-filter.component';
import { DataTableComponent, TableColumn, TableAction, SortEvent, PaginationEvent } from '../../../shared/components/data-table/data-table.component';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-auth-logs',
  templateUrl: './auth-logs.component.html',
  styleUrls: ['./auth-logs.component.scss'],
  imports: [
    CommonModule, 
    FormsModule, 
    PageHeaderComponent,
    SearchFilterComponent,
    DataTableComponent,
    ModalComponent,
    LoadingSpinnerComponent
  ],
  standalone: true
})
export class AuthLogsComponent implements OnInit, OnDestroy {
  logs: AuthenticationLog[] = [];
  loading = true;
  error = '';
  
  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  
  // Filters
  searchText = '';
  startDate: string | null = null;
  endDate: string | null = null;
  
  // Sorting
  sortColumn = 'timestamp';
  sortDirection: 'asc' | 'desc' = 'desc';
  
  // Modal
  selectedLog: AuthenticationLog | null = null;
  showDetailModal = false;
  
  // Toast notification
  showToast = false;
  toastMessage = '';
  
  // Subscriptions
  private subscriptions: Subscription[] = [];
  
  // Configuration for standardized components
  headerActions: PageAction[] = [
    // Removed refresh and export buttons as requested
  ];

  filterConfigs: FilterConfig[] = [
    {
      key: 'startDate',
      label: 'Start Date',
      type: 'date'
    },
    {
      key: 'endDate',
      label: 'End Date',
      type: 'date'
    }
  ];

  tableColumns: TableColumn[] = [
    {
      key: 'timestamp',
      label: 'Time',
      sortable: true,
      type: 'date'
    },
    {
      key: 'email',
      label: 'Email',
      sortable: true,
      type: 'text'
    },
    {
      key: 'primaryDomain',
      label: 'Domain',
      sortable: true,
      type: 'text'
    },
    {
      key: 'ipAddress',
      label: 'IP Address',
      sortable: false,
      type: 'text'
    },
    {
      key: 'successful',
      label: 'Result',
      sortable: true,
      type: 'badge',
      badgeType: 'auth'
    }
  ];

  tableActions: TableAction[] = [
    {
      label: 'View Details',
      icon: 'visibility',
      action: 'view',
      variant: 'ghost'
    }
  ];

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private environmentService: EnvironmentService,
    private elementRef: ElementRef
  ) { }

  ngOnInit(): void {
    this.setStartDateToYesterday();
    this.loadLogs();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  loadLogs(): void {
    // Cancel any previous requests to prevent race conditions
    this.subscriptions.forEach(sub => {
      if (!sub.closed) {
        sub.unsubscribe();
      }
    });
    this.subscriptions = [];
    
    this.loading = true;
    this.error = '';
    
    let url = `${this.environmentService.apiUrl}/auth-logs?page=${this.currentPage}&size=${this.pageSize}`;
    
    // Apply filters to server
    const params: string[] = [];
    
    // Handle search text by determining if it's an email, domain, or IP
    if (this.searchText && this.searchText.trim().length > 0) {
      const searchValue = this.searchText.trim();
      
      // Check if it looks like an email
      if (searchValue.includes('@')) {
        params.push(`email=${encodeURIComponent(searchValue)}`);
      }
      // Check if it looks like an IP address
      else if (/^\d+\.\d+\.\d+\.\d+$/.test(searchValue)) {
        params.push(`ipAddress=${encodeURIComponent(searchValue)}`);
      }
      // Otherwise treat it as a domain search (backend expects 'domain' not 'primaryDomain')
      else {
        params.push(`domain=${encodeURIComponent(searchValue)}`);
      }
    }
    
    if (this.startDate) {
      // Convert date to LocalDateTime format (add time)
      const startDateTime = this.startDate.includes('T') ? this.startDate : `${this.startDate}T00:00:00`;
      params.push(`startDate=${encodeURIComponent(startDateTime)}`);
    }
    
    if (this.endDate) {
      // Convert date to LocalDateTime format (add end of day time)
      const endDateTime = this.endDate.includes('T') ? this.endDate : `${this.endDate}T23:59:59`;
      params.push(`endDate=${encodeURIComponent(endDateTime)}`);
    }
    
    // Add sorting parameters
    if (this.sortColumn) {
      params.push(`sort=${this.sortColumn},${this.sortDirection}`);
    }
    
    if (params.length > 0) {
      url += `&${params.join('&')}`;
    }
    
    const tokenSub = this.authService.getAccessToken().subscribe({
      next: (token) => {
        const httpSub = this.http.get<PageResponse<AuthenticationLog>>(url, {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }).subscribe({
          next: (response) => {
            this.logs = response.content || [];
            this.totalItems = response.totalElements || 0;
            this.totalPages = response.totalPages || 0;
            this.loading = false;
          },
          error: (err) => {
            this.loading = false;
            this.error = 'Failed to load authentication logs. Please try again.';
          }
        });
        this.subscriptions.push(httpSub);
      },
      error: (err) => {
        this.loading = false;
        this.error = 'Failed to get authentication token. Please try logging out and back in.';
      }
    });
    this.subscriptions.push(tokenSub);
  }

  // Event handlers for standardized components
  onHeaderAction(action: PageAction): void {
    // No actions available now
  }

  onSearchFilter(event: SearchFilterEvent): void {
    // Handle search query
    const newSearchQuery = event.searchQuery ? event.searchQuery.trim() : '';
    const searchChanged = newSearchQuery !== this.searchText.trim();
    
    if (searchChanged) {
      this.searchText = newSearchQuery;
    }
    
    // Handle filter values
    let filtersChanged = false;
    if (event.filters) {
      const newStartDate = event.filters['startDate'] || null;
      const newEndDate = event.filters['endDate'] || null;
      
      if (newStartDate !== this.startDate || newEndDate !== this.endDate) {
        this.startDate = newStartDate;
        this.endDate = newEndDate;
        filtersChanged = true;
      }
    }
    
    // Reload if anything changed
    if (searchChanged || filtersChanged) {
      this.currentPage = 0; // Reset to first page
      this.loadLogs();
    }
  }

  onSort(event: SortEvent): void {
    // Map frontend column names to backend field names if needed
    let backendColumn = event.column;
    switch (event.column) {
      case 'successful':
        backendColumn = 'successful'; // Ensure this matches backend field
        break;
      case 'timestamp':
        backendColumn = 'timestamp'; // Ensure this matches backend field
        break;
      case 'email':
        backendColumn = 'email'; // Ensure this matches backend field
        break;
      case 'primaryDomain':
        backendColumn = 'primaryDomain'; // Ensure this matches backend field
        break;
      default:
        backendColumn = event.column;
    }
    
    this.sortColumn = backendColumn;
    this.sortDirection = event.direction;
    this.currentPage = 0; // Reset to first page when sorting
    
    this.loadLogs();
  }

  onPagination(event: PaginationEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLogs();
  }

  onTableAction(event: { action: string, item: any }): void {
    switch (event.action) {
      case 'view':
        this.showDetails(event.item);
        break;
    }
  }

  onRowClick(log: AuthenticationLog): void {
    this.showDetails(log);
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  getStatusClass(successful: boolean): string {
    return successful ? 'status-success' : 'status-failed';
  }

  getStatusText(successful: boolean): string {
    return successful ? 'Login Success' : 'Login Failed';
  }
  
  showDetails(log: AuthenticationLog): void {
    // Ensure clean state before opening new modal
    this.closeDetails();
    
    // Small delay to ensure DOM updates before opening new modal
    setTimeout(() => {
      this.selectedLog = log;
      this.showDetailModal = true;
    }, 10);
  }
  
  closeDetails(): void {
    this.showDetailModal = false;
    // Reset selected log immediately to prevent state issues
    this.selectedLog = null;
  }
  
  getBrowserInfo(userAgent: string): string {
    // Simple browser detection
    if (userAgent.includes('Chrome')) return 'Chrome';
    if (userAgent.includes('Firefox')) return 'Firefox';
    if (userAgent.includes('Safari') && !userAgent.includes('Chrome')) return 'Safari';
    if (userAgent.includes('Edge')) return 'Edge';
    if (userAgent.includes('MSIE') || userAgent.includes('Trident/')) return 'Internet Explorer';
    return 'Unknown';
  }
  
  getOSInfo(userAgent: string): string {
    // Simple OS detection
    if (userAgent.includes('Windows')) return 'Windows';
    if (userAgent.includes('Mac OS')) return 'macOS';
    if (userAgent.includes('Linux')) return 'Linux';
    if (userAgent.includes('Android')) return 'Android';
    if (userAgent.includes('iOS') || userAgent.includes('iPhone') || userAgent.includes('iPad')) return 'iOS';
    return 'Unknown';
  }
  
  getDeviceInfo(userAgent: string): string {
    // Simple device detection
    if (userAgent.includes('Mobile')) return 'Mobile';
    if (userAgent.includes('Tablet')) return 'Tablet';
    return 'Desktop';
  }

  setStartDateToYesterday() {
    const currentDate = new Date();
    currentDate.setDate(currentDate.getDate() - 1); // Show last 24 hours instead of 7 days

    // Format the date to match the datetime-local input format (YYYY-MM-DDTHH:MM)
    const year = currentDate.getFullYear();
    const month = (currentDate.getMonth() + 1).toString().padStart(2, '0'); // Ensure two digits
    const day = currentDate.getDate().toString().padStart(2, '0');
    const hours = currentDate.getHours().toString().padStart(2, '0');
    const minutes = currentDate.getMinutes().toString().padStart(2, '0');

    // Construct the datetime-local string
    this.startDate = `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  /**
   * Copy text to clipboard and show toast notification
   * @param text The text to copy
   */
  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(() => {
      // Successfully copied
    }).catch(err => {
      // Copy failed
    });
  }
  
  /**
   * Show a toast notification that automatically disappears after a delay
   * @param message The message to display in the toast
   */
  showToastNotification(message: string): void {
    this.toastMessage = message;
    this.showToast = true;
    
    // Auto-hide the toast after 3 seconds
    setTimeout(() => {
      this.showToast = false;
    }, 3000);
  }
}
