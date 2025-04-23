import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from '../../../core/services/environment.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { AuthenticationLog, PageResponse } from '../../../core/models/user.model';

@Component({
  selector: 'app-auth-logs',
  templateUrl: './auth-logs.component.html',
  styleUrls: ['./auth-logs.component.scss'],
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent],
  standalone: true
})
export class AuthLogsComponent implements OnInit {
  logs: AuthenticationLog[] = [];
  loading = true;
  error = '';
  
  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 25, 50];
  
  // Filters
  emailFilter = '';
  domainFilter = '';
  successFilter: boolean | null = null;
  startDate: string | null = null;
  endDate: string | null = null;
  
  // Display
  displayedColumns = ['timestamp', 'email', 'domain', 'ipAddress', 'status', 'actions'];
  selectedLog: AuthenticationLog | null = null;
  showDetailModal = false;
  filtersOpen = false; // Default to closed on all screen sizes
  
  // Toast notification
  showToast = false;
  toastMessage = '';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private environmentService: EnvironmentService
  ) { }

  ngOnInit(): void {
    this.loadLogs();
    this.setStartDateOneWeekAgo();
  }

  loadLogs(): void {
    this.loading = true;
    this.error = '';
    
    let url = `${this.environmentService.apiUrl}/auth-logs?page=${this.currentPage}&size=${this.pageSize}`;
    
    // Apply filters if set
    const params: string[] = [];
    
    if (this.emailFilter) {
      params.push(`email=${encodeURIComponent(this.emailFilter)}`);
    }
    
    if (this.domainFilter) {
      params.push(`domain=${encodeURIComponent(this.domainFilter)}`);
    }
    
    if (this.successFilter !== null) {
      params.push(`successful=${this.successFilter}`);
    }
    
    if (this.startDate) {
      params.push(`startDate=${encodeURIComponent(this.startDate)}`);
    }
    
    if (this.endDate) {
      params.push(`endDate=${encodeURIComponent(this.endDate)}`);
    }
    
    if (params.length > 0) {
      url += `&${params.join('&')}`;
    }
  
    
    this.authService.getAccessToken().subscribe({
      next: (token) => {
        
        this.http.get<PageResponse<AuthenticationLog>>(url, {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }).subscribe({
          next: (response) => {
            this.logs = response.content;
            this.totalItems = response.totalElements;
            this.totalPages = response.totalPages;
            this.loading = false;
          },
          error: (err) => {
            console.error('Error loading logs:', err);
            this.error = `Failed to load authentication logs: ${err.status} ${err.statusText}`;
            if (err.error && err.error.message) {
              this.error += ` - ${err.error.message}`;
            }
            this.loading = false;
          }
        });
      },
      error: (err) => {
        console.error('Error getting token:', err);
        this.error = 'Failed to get authentication token. Please try logging out and back in.';
        this.loading = false;
      }
    });
  }

  onPageChange(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadLogs();
    }
  }
  
  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 0; // Reset to first page
    this.loadLogs();
  }

  applyFilters(): void {
    this.currentPage = 0; // Reset to first page
    this.loadLogs();
    
    // Close filters on mobile after applying
    if (window.innerWidth <= 768) {
      this.filtersOpen = false;
    }
  }

  /**
   * Toggle the visibility of filters on all screen sizes
   */
  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
  }

  /**
   * Clear all filters
   * @param event Optional event to stop propagation when clicked inside the header
   */
  clearFilters(event?: Event): void {
    if (event) {
      event.stopPropagation(); // Prevent toggling filters when clicking the clear button
    }
    
    this.emailFilter = '';
    this.domainFilter = '';
    this.successFilter = null;
    this.startDate = null;
    this.endDate = null;
    
    this.applyFilters();
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  getStatusClass(successful: boolean): string {
    return successful ? 'status-success' : 'status-failed';
  }

  getStatusText(successful: boolean): string {
    return successful ? 'Success' : 'Failed';
  }
  
  showDetails(log: AuthenticationLog): void {
    this.selectedLog = log;
    this.showDetailModal = true;
  }
  
  closeDetails(): void {
    this.showDetailModal = false;
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
  
  // Generate pagination array for template
  getPaginationArray(): number[] {
    const paginationArray: number[] = [];
    const maxPagesToShow = 5;
    
    if (this.totalPages <= maxPagesToShow) {
      // Show all pages if there are few
      for (let i = 0; i < this.totalPages; i++) {
        paginationArray.push(i);
      }
    } else {
      // Show a window of pages around current page
      let startPage = Math.max(0, this.currentPage - Math.floor(maxPagesToShow / 2));
      let endPage = Math.min(this.totalPages - 1, startPage + maxPagesToShow - 1);
      
      // Adjust if we're near the end
      if (endPage - startPage < maxPagesToShow - 1) {
        startPage = Math.max(0, endPage - maxPagesToShow + 1);
      }
      
      for (let i = startPage; i <= endPage; i++) {
        paginationArray.push(i);
      }
    }
    
    return paginationArray;
  }
  setStartDateOneWeekAgo() {
    const currentDate = new Date();
    currentDate.setDate(currentDate.getDate() - 7); // Subtract 7 days for 1 week ago

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
    navigator.clipboard.writeText(text).then(
      () => {
        this.showToastNotification('Copied to clipboard');
      },
      (err) => {
        console.error('Could not copy text: ', err);
        this.showToastNotification('Failed to copy text');
      }
    );
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
