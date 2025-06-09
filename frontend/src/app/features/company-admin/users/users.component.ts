import { Component, OnInit, HostListener, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { User as AuthUser } from '../../../core/models/auth.model';
import { EnvironmentService } from '../../../core/services/environment.service';
import { CompanyUser, PendingUser, SelectedUser } from '../../../core/models/user.model';

// Import standardized components
import { PageHeaderComponent, PageAction } from '../../../shared/components/page-header/page-header.component';
import { SearchFilterComponent, FilterConfig, SearchFilterEvent } from '../../../shared/components/search-filter/search-filter.component';
import { DataTableComponent, TableColumn, TableAction, SortEvent, PaginationEvent } from '../../../shared/components/data-table/data-table.component';
import { UserDetailModalComponent, UserDetailData, UserUpdateEvent } from '../../../shared/components/user-detail-modal/user-detail-modal.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    PageHeaderComponent,
    SearchFilterComponent,
    DataTableComponent,
    UserDetailModalComponent,
    LoadingSpinnerComponent
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  companyUsers: CompanyUser[] = [];
  filteredUsers: CompanyUser[] = []; // Filtered list of users
  loadingUsers = true;
  error = false;
  errorMessage = '';
  companyDomain = '';
  
  // Search and filter properties
  searchQuery = '';
  statusFilter = 'all';
  roleFilter = 'all';
  
  // Pending user requests
  pendingUsers: PendingUser[] = [];
  hasPendingUsers = false;
  pendingCount = 0;
  showNotificationPopup = false;
  showPendingPopup = false;
  selectedPendingUser: PendingUser | null = null;

  // User action confirmation
  showUserActionConfirmPopup = false;
  confirmAction: 'approve' | 'reject' = 'approve';
  pendingUserId = '';
  
  // Sorting properties
  sortColumn: string = 'name';
  sortDirection: 'asc' | 'desc' = 'asc';

  // User detail popup
  showUserDetailPopup = false;
  selectedUser: SelectedUser | null = null;
  selectedUserForModal: UserDetailData | null = null;
  
  // Rejected user popup
  showRejectedUserPopup = false;
  selectedRejectedUser: SelectedUser | null = null;
  showAcceptConfirmation = false;
  
  availableStatuses = ['Active', 'Inactive', 'Rejected'];
  availableRoles = ['COMPANY_USER', 'COMPANY_ADMIN'];
  updatingUser = false;

  // State management
  showToast = false;
  toastMessage = '';
  toastType: 'success' | 'error' = 'success';

  // Configuration for standardized components
  headerActions: PageAction[] = [
    // Can add actions like "Invite User" later if needed
  ];

  filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { value: 'Active', label: 'Active' },
        { value: 'Inactive', label: 'Inactive' }
      ]
    },
    {
      key: 'role',
      label: 'Role',
      type: 'select',
      options: [
        { value: 'COMPANY_USER', label: 'User' },
        { value: 'COMPANY_ADMIN', label: 'Admin' }
      ]
    }
  ];

  tableColumns: TableColumn[] = [
    {
      key: 'user',
      label: 'User',
      sortable: true,
      type: 'avatar'
    },
    {
      key: 'email',
      label: 'Email',
      sortable: true,
      type: 'text'
    },
    {
      key: 'role',
      label: 'Role',
      sortable: true,
      type: 'badge'
    },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      type: 'badge'
    },
    {
      key: 'lastLogin',
      label: 'Last Login',
      sortable: false,
      type: 'date',
      hideOnMobile: true
    }
  ];

  tableActions: TableAction[] = [
    // Actions will be handled through row clicks for user details
  ];

  constructor(
    private apiService: ApiService,
    @Inject(AuthService) private authService: AuthService,
    private environmentService: EnvironmentService
  ) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as Element;
    
    // Check if the click is outside the notification system
    if (this.showNotificationPopup) {
      const notificationSection = target.closest('.notification-section');
      if (!notificationSection) {
        this.showNotificationPopup = false;
      }
    }

    // Check if click was outside the user detail popup
    if (this.showUserDetailPopup && this.selectedUser) {
      const popupElement = document.querySelector('.user-detail-popup');
      if (popupElement && !popupElement.contains(event.target as Node) && 
          !event.defaultPrevented) {
        // Only close if the click was outside the popup and not on a button that opens it
        const userRows = document.querySelectorAll('.user-row, .user-card');
        let clickedOnTrigger = false;
        userRows.forEach(row => {
          if (row.contains(event.target as Node)) {
            clickedOnTrigger = true;
          }
        });
        
        if (!clickedOnTrigger) {
          this.hideUserDetail();
        }
      }
    }
    
    // Check if click was outside the pending user popup
    if (this.showPendingPopup) {
      const pendingPopup = document.querySelector('.pending-requests-popup');
      if (pendingPopup && !pendingPopup.contains(event.target as Node) && 
          !event.defaultPrevented) {
        this.hidePendingPopup();
      }
    }
    
    // Check if click was outside the confirmation popup
    if (this.showUserActionConfirmPopup) {
      const confirmationPopup = document.querySelector('.confirmation-popup');
      if (confirmationPopup && !confirmationPopup.contains(event.target as Node) && 
          !event.defaultPrevented) {
        this.cancelUserAction();
      }
    }
    
    // Check if click was outside the rejected user popup
    if (this.showRejectedUserPopup) {
      const rejectedUserPopup = document.querySelector('.rejected-user-modal');
      if (rejectedUserPopup && !rejectedUserPopup.contains(event.target as Node) && 
          !event.defaultPrevented) {
        this.closeRejectedUserPopup();
      }
    }
  }

  ngOnInit(): void {
    this.initializeCompanyDomain();
  }

  // Event handlers for standardized components
  onHeaderAction(action: PageAction): void {
    switch (action.action) {
      case 'invite':
        // Handle invite user action
        break;
    }
  }

  onSearchFilter(event: SearchFilterEvent): void {
    this.searchQuery = event.searchQuery;
    this.statusFilter = event.filters['status'] || 'all';
    this.roleFilter = event.filters['role'] || 'all';
    this.applyFilters();
  }

  onSort(event: SortEvent): void {
    // Map avatar column to name for sorting
    const sortColumn = event.column === 'user' ? 'name' : event.column;
    this.sortUsers(sortColumn, false);
    this.sortDirection = event.direction;
  }

  onTableAction(event: { action: string, item: any }): void {
    switch (event.action) {
      case 'view':
        this.showUserDetail(event.item);
        break;
    }
  }

  onRowClick(user: CompanyUser): void {
    // Check if this is a rejected user
    if (user.status === 'Rejected' || user.status === 'REJECTED') {
      this.showRejectedUserDetail(user);
    } else {
      this.showUserDetail(user);
    }
  }

  private initializeCompanyDomain(): void {
    this.authService.user$.subscribe({
      next: (user: AuthUser | null) => {
        if (user && user.email) {
          // Extract domain from email
          const emailParts = user.email.split('@');
          if (emailParts.length === 2) {
            this.companyDomain = emailParts[1];
            this.fetchCompanyUsers();
            this.fetchPendingUsers();
          } else {
            this.handleError('Invalid email format in user profile');
          }
        } else {
          this.handleError('No email found in user profile');
        }
      },
      error: (err: Error) => {
        this.handleError('Failed to get user profile');
      }
    });
  }

  fetchCompanyUsers(): void {
    if (!this.companyDomain) {
      this.handleError('No company domain available');
      return;
    }
    
    this.loadingUsers = true;
    this.error = false; // Reset error flag
    
    // Call the new API endpoint that includes last login times
    this.apiService.get<any[]>(`users/with-last-login?domain=${this.companyDomain}&excludeStatus=PENDING`)
      .subscribe({
        next: (users) => {
          try {
            // Map API response to User interface format
            this.companyUsers = users.map(user => {
              const processedUser = {
                id: user.id,
                name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
                email: user.email,
                role: user.roles && user.roles.length > 0 ? user.roles[0] : 'COMPANY_USER',
                status: user.status === 'ACTIVATED' ? 'Active' : 
                       user.status === 'DEACTIVATED' ? 'Inactive' : 
                       user.status === 'REJECTED' ? 'Rejected' :
                       user.status,
                lastLogin: user.lastLogin || undefined,
                picture: user.picture || '',
                // Add user object for avatar column
                user: {
                  name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
                  email: user.email,
                  picture: user.picture ? this.getProxyImageUrl(user.picture) : ''
                }
              };
              
              // Process profile image URL if it exists
              if (processedUser.picture) {
                processedUser.picture = this.getProxyImageUrl(processedUser.picture);
              }
              
              return processedUser;
            });
            
            // Initialize filtered users with all users
            this.filteredUsers = [...this.companyUsers];
            
            this.loadingUsers = false;
            this.error = false; // Ensure error flag is reset
            this.sortUsers('name');
          } catch (error) {
            this.handleError('Error processing user data');
          }
        },
        error: (err) => {
          this.handleError(`Error fetching users for domain ${this.companyDomain}`);
        }
      });
  }

  fetchPendingUsers(): void {
    if (!this.companyDomain) {
      this.pendingUsers = [];
      this.pendingCount = 0;
      this.hasPendingUsers = false;
      return;
    }
    
    // Call the API to get pending users for the domain
    this.apiService.get<any[]>(`users?domain=${encodeURIComponent(this.companyDomain)}&status=PENDING`)
      .subscribe({
        next: (pendingUsers) => {
          // Process the results
          if (Array.isArray(pendingUsers)) {
            this.pendingUsers = pendingUsers
              .filter(user => user != null) // Filter nulls just in case
              .map(user => {
                // Map backend fields to frontend PendingUser interface
                const mappedUser: PendingUser = {
                  id: user.id,
                  name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
                  email: user.email,
                  requestedAt: user.dateTimeAdded || user.requestedAt || new Date().toISOString(), // Map dateTimeAdded to requestedAt
                  status: user.status,
                  primaryDomain: user.primaryDomain,
                  roles: user.roles || [],
                  firstName: user.firstName,
                  lastName: user.lastName,
                  picture: user.picture ? this.getProxyImageUrl(user.picture) : '',
                  auth0Id: user.auth0Id,
                  dateTimeAdded: user.dateTimeAdded,
                  dateTimeChanged: user.dateTimeChanged
                };
                
                return mappedUser;
              });
            
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = this.pendingCount > 0;
          } else {
            this.pendingUsers = [];
            this.pendingCount = 0;
            this.hasPendingUsers = false;
          }
        },
        error: (err) => {
          this.pendingUsers = [];
          this.pendingCount = 0;
          this.hasPendingUsers = false;
        }
      });
  }
  
  /**
   * Handles profile image URLs by using a proxy if needed
   * @param url The original image URL
   * @returns A proxied URL for external images
   */
  getProxyImageUrl(url: string): string {
    if (!url) return '';
    
    // Only proxy external URLs, not data URLs or relative paths
    if (url.startsWith('http') && !url.includes(window.location.hostname)) {
      const encodedUrl = encodeURIComponent(url);
      return `${this.environmentService.apiUrl}/proxy/image?url=${encodedUrl}`;
    }
    
    return url;
  }

  toggleNotificationPopup(event: MouseEvent): void {
    // Prevent this click from being captured by the document click handler
    event.stopPropagation();
    this.showNotificationPopup = !this.showNotificationPopup;
  }

  showPendingUserActions(user: PendingUser): void {
    this.selectedPendingUser = user;
    this.pendingUserId = user.id;
    this.showPendingPopup = true;
    
    // Close the notification popup when showing the details popup
    this.showNotificationPopup = false;
    this.showUserActionConfirmPopup = false;
  }

  hidePendingPopup(): void {
    this.showPendingPopup = false;
    this.selectedPendingUser = null;
  }

  approveUser(userId: string): void {
    if (!userId) return;
    
    // Find the user being approved
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      return;
    }
    
    // Create a SelectedUser from the pendingUser
    this.selectedUser = {
      id: user.id,
      firstName: user.name.split(' ')[0] || '',
      lastName: user.name.split(' ').slice(1).join(' ') || '',
      email: user.email,
      role: user.roles?.[0] || 'COMPANY_USER',
      status: 'ACTIVE',
      picture: user.picture,
      requestedAt: user.requestedAt,
      primaryDomain: user.primaryDomain,
      roles: user.roles
    };
    
    // Set up confirmation popup
    this.confirmAction = 'approve';
    this.showUserActionConfirmPopup = true;
    this.showNotificationPopup = false; // Close notification dropdown
  }

  rejectUser(userId: string): void {
    if (!userId) return;
    
    // Find the user being rejected
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      return;
    }
    
    // Create a SelectedUser from the pendingUser
    this.selectedUser = {
      id: user.id,
      firstName: user.name.split(' ')[0] || '',
      lastName: user.name.split(' ').slice(1).join(' ') || '',
      email: user.email,
      role: user.roles?.[0] || 'COMPANY_USER',
      status: 'INACTIVE',
      picture: user.picture,
      requestedAt: user.requestedAt,
      primaryDomain: user.primaryDomain,
      roles: user.roles
    };
    
    // Set up confirmation popup
    this.confirmAction = 'reject';
    this.showUserActionConfirmPopup = true;
    this.showNotificationPopup = false; // Close notification dropdown
  }
  
  confirmUserAction(): void {
    // Hide the confirmation popup
    this.showUserActionConfirmPopup = false;
    
    // Show loading indicator
    this.updatingUser = true;
    
    if (this.confirmAction === 'approve') {
      this.processApproval();
    } else {
      this.processRejection();
    }
    
    // Enable scrolling
    this.enableBodyScroll();
  }
  
  cancelUserAction(): void {
    this.showUserActionConfirmPopup = false;
    this.selectedUser = null;
    this.pendingUserId = '';
    
    // Enable scrolling
    this.enableBodyScroll();
  }
  
  processApproval(): void {
    if (!this.selectedUser) return;
    
    const userId = this.selectedUser.id;
    
    // Create a copy of the user for adding to the company users list
    const approvedUser: any = {
      id: this.selectedUser.id,
      name: `${this.selectedUser.firstName} ${this.selectedUser.lastName}`,
      email: this.selectedUser.email,
      role: this.selectedUser.role || 'COMPANY_USER',
      status: 'ACTIVE',
      picture: this.selectedUser.picture
    };
    
    // Create a pendingUser reference in case we need to revert
    const pendingUser: PendingUser = {
      id: this.selectedUser.id,
      name: `${this.selectedUser.firstName} ${this.selectedUser.lastName}`,
      email: this.selectedUser.email,
      requestedAt: this.selectedUser.requestedAt || new Date().toISOString(),
      primaryDomain: this.selectedUser.primaryDomain || '',
      roles: this.selectedUser.roles || [this.selectedUser.role || 'COMPANY_USER'],
      picture: this.selectedUser.picture
    };
    
    // Optimistically update UI first for better UX
    // 1. Remove from pending users if in the pending list
    if (this.pendingUsers) {
      this.pendingUsers = this.pendingUsers.filter(u => u.id !== userId);
      this.pendingCount = this.pendingUsers.length;
      this.hasPendingUsers = this.pendingCount > 0;
    }
    
    // 2. Add to company users if not already there
    if (!this.companyUsers.some(u => u.id === userId)) {
      this.companyUsers = [...this.companyUsers, approvedUser];
      this.filteredUsers = [...this.companyUsers];
    }
    
    // Call the API to approve the pending user
    this.apiService.post(`users/pending/${userId}/approve`, {}).subscribe({
      next: (response) => {
        this.showToastNotification('User approved successfully', 'success');
        this.fetchPendingUsers();
      },
      error: (error) => {
        this.showToastNotification('Error approving user: ' + (error.error?.message || error.message), 'error');
      }
    });
  }
  
  processRejection(): void {
    if (!this.selectedUser) return;
    
    const userId = this.selectedUser.id;
    
    // Create a pendingUser reference in case we need to revert
    const pendingUser: PendingUser = {
      id: this.selectedUser.id,
      name: `${this.selectedUser.firstName} ${this.selectedUser.lastName}`,
      email: this.selectedUser.email,
      requestedAt: this.selectedUser.requestedAt || new Date().toISOString(),
      primaryDomain: this.selectedUser.primaryDomain || '',
      roles: this.selectedUser.roles || [this.selectedUser.role || 'COMPANY_USER'],
      picture: this.selectedUser.picture
    };
    
    // Optimistically update UI first for better UX
    if (this.pendingUsers) {
      this.pendingUsers = this.pendingUsers.filter(u => u.id !== userId);
      this.pendingCount = this.pendingUsers.length;
      this.hasPendingUsers = this.pendingCount > 0;
    }
    
    // Call the API to reject the pending user
    this.apiService.post(`users/pending/${userId}/reject`, {}).subscribe({
      next: (response: any) => {
        this.showToastNotification('User rejected successfully', 'success');
        this.fetchPendingUsers();
      },
      error: (error) => {
        this.showToastNotification('Error rejecting user: ' + (error.error?.message || error.message), 'error');
      }
    });
  }

  /**
   * Formats a timestamp into a human-readable time difference (e.g., "2 hours ago")
   */
  formatTimeAgo(timestamp: string | Date | undefined): string {
    if (!timestamp) {
      return 'Unknown';
    }
    
    try {
      // Convert string to Date object
      const date = typeof timestamp === 'string' ? new Date(timestamp) : timestamp;
      
      // Check if date is valid
      if (isNaN(date.getTime())) {
        return 'Unknown';
      }
      
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      
      // Handle future dates
      if (diffMs < 0) {
        return 'Just now';
      }
      
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHour = Math.floor(diffMin / 60);
      const diffDay = Math.floor(diffHour / 24);
      
      if (diffSec < 60) {
        return 'Just now';
      } else if (diffMin < 60) {
        return `${diffMin} ${diffMin === 1 ? 'minute' : 'minutes'} ago`;
      } else if (diffHour < 24) {
        return `${diffHour} ${diffHour === 1 ? 'hour' : 'hours'} ago`;
      } else if (diffDay < 30) {
        return `${diffDay} ${diffDay === 1 ? 'day' : 'days'} ago`;
      } else {
        // For older dates, show the actual date
        return date.toLocaleDateString();
      }
    } catch (error) {
      return 'Unknown';
    }
  }

  handleError(message: string): void {
    this.error = true;
    this.errorMessage = message;
    this.loadingUsers = false;
  }

  formatRole(role: string): string {
    if (!role) return 'User';
    
    // Handle uppercase role types (from backend)
    switch(role.toUpperCase()) {
      case 'COMPANY_ADMIN':
        return 'Admin';
      case 'COMPANY_USER':
        return 'User';
      case 'SYSTEM_ADMIN':
        return 'System Admin';
      default:
        // Handle already formatted roles
        if (role.includes(' ')) {
          return role;
        }
        return role.charAt(0).toUpperCase() + role.slice(1).toLowerCase();
    }
  }

  formatStatus(status: string): string {
    if (!status) return 'Unknown';
    
    switch(status.toUpperCase()) {
      case 'ACTIVATED':
        return 'Active';
      case 'DEACTIVATED':
        return 'Inactive';
      case 'REJECTED':
        return 'Rejected';
      case 'PENDING':
        return 'Pending';
      default:
        return status;
    }
  }

  /**
   * Determines the CSS class for a role badge based on the role value
   */
  getRoleClass(role: string): string {
    if (!role) return 'role-pill role-company-user';
    
    const normalizedRole = role.toUpperCase();
    
    if (normalizedRole.includes('ADMIN')) {
      // Check if it's a system admin
      if (normalizedRole.includes('SYSTEM')) {
        return 'role-pill role-system-admin';
      }
      // Otherwise it's a company admin
      return 'role-pill role-company-admin';
    } else {
      // Default for regular users or unknown roles
      return 'role-pill role-company-user';
    }
  }

  /**
   * Apply filters based on search query, status, and role
   */
  applyFilters(): void {
    // Start with all users
    let filtered = [...this.companyUsers];
    
    // Apply search filter if there's a query
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase().trim();
      filtered = filtered.filter(user => 
        user.name.toLowerCase().includes(query) || 
        user.email.toLowerCase().includes(query)
      );
    }
    
    // Apply status filter if not 'all'
    if (this.statusFilter !== 'all') {
      filtered = filtered.filter(user => 
        this.formatStatus(user.status) === this.statusFilter
      );
    }
    
    // Apply role filter if not 'all'
    if (this.roleFilter !== 'all') {
      filtered = filtered.filter(user => 
        user.role === this.roleFilter
      );
    }
    
    // Update filtered users
    this.filteredUsers = filtered;
    
    // Resort based on current sort settings
    this.sortUsers(this.sortColumn, false);
  }
  
  /**
   * Clear the search query and reapply filters
   */
  clearSearch(): void {
    this.searchQuery = '';
    this.applyFilters();
  }
  
  /**
   * Clear all filters and reset to show all users
   */
  clearFilters(): void {
    this.searchQuery = '';
    this.statusFilter = 'all';
    this.roleFilter = 'all';
    this.filteredUsers = [...this.companyUsers];
    this.sortUsers(this.sortColumn, false);
  }

  /**
   * Sorts the company users based on the specified column
   * @param column The column to sort by
   */
  sortUsers(column: string, toggleDirection: boolean = true): void {
    // If clicking on the same column and toggle is enabled, toggle sort direction
    if (this.sortColumn === column && toggleDirection) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else if (this.sortColumn !== column) {
      // New column, set it and default to ascending
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    
    // Sort the users array based on the selected column and direction
    this.filteredUsers.sort((a, b) => {
      let comparison = 0;
      
      // Determine which property to use for comparison
      switch (column) {
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'email':
          comparison = a.email.localeCompare(b.email);
          break;
        case 'role':
          comparison = this.formatRole(a.role).localeCompare(this.formatRole(b.role));
          break;
        case 'status':
          comparison = this.formatStatus(a.status).localeCompare(this.formatStatus(b.status));
          break;
        default:
          comparison = 0;
      }
      
      // Adjust based on sort direction
      return this.sortDirection === 'asc' ? comparison : -comparison;
    });
  }
  
  /**
   * Gets the current sort icon based on column and sort state
   * @param column The column to check
   * @returns The appropriate sort icon or empty string
   */
  getSortIcon(column: string): string {
    if (this.sortColumn !== column) {
      return 'unfold_more';
    }
    
    return this.sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  // Method to show user detail popup
  showUserDetail(user: any): void {
    // Convert the CompanyUser to SelectedUser format (for backward compatibility)
    this.selectedUser = {
      id: user.id,
      firstName: user.name.split(' ')[0] || '',
      lastName: user.name.split(' ').slice(1).join(' ') || '',
      email: user.email,
      role: user.role,
      status: user.status,
      picture: user.picture,
      lastLogin: user.lastLogin
    };

    // Convert to UserDetailData format for the new modal
    this.selectedUserForModal = {
      id: user.id,
      firstName: user.name.split(' ')[0] || '',
      lastName: user.name.split(' ').slice(1).join(' ') || '',
      email: user.email,
      role: user.role,
      status: user.status,
      picture: user.picture,
      lastLogin: user.lastLogin
    };
    
    this.showUserDetailPopup = true;
  }

  // Method to hide user detail popup
  hideUserDetail(): void {
    this.showUserDetailPopup = false;
    setTimeout(() => {
      this.selectedUser = null;
      // Restore body scrolling
      document.body.style.overflow = '';
    }, 200); // Small delay to allow animation to complete
  }

  // Method to show rejected user detail popup
  showRejectedUserDetail(user: CompanyUser): void {
    this.selectedRejectedUser = {
      id: user.id,
      firstName: user.name.split(' ')[0] || '',
      lastName: user.name.split(' ').slice(1).join(' ') || '',
      email: user.email,
      role: user.role,
      status: user.status,
      picture: user.picture,
      lastLogin: user.lastLogin,
      primaryDomain: this.companyDomain,
      requestedAt: user.lastLogin || new Date().toISOString() // Use lastLogin as fallback for requestedAt
    };
    
    this.showRejectedUserPopup = true;
    this.showAcceptConfirmation = false; // Reset confirmation state
  }

  // Method to close rejected user popup
  closeRejectedUserPopup(): void {
    this.showRejectedUserPopup = false;
    this.showAcceptConfirmation = false;
    setTimeout(() => {
      this.selectedRejectedUser = null;
    }, 200);
  }

  // Method to show accept confirmation dialog
  showAcceptConfirmationDialog(): void {
    this.showAcceptConfirmation = true;
  }

  // Method to hide accept confirmation and go back to main dialog
  hideAcceptConfirmation(): void {
    this.showAcceptConfirmation = false;
  }

  // Method to accept a rejected user (change status from REJECTED to ACTIVATED)
  acceptRejectedUser(): void {
    if (!this.selectedRejectedUser) return;
    
    this.updatingUser = true;
    
    // Call the API to update user status from REJECTED to ACTIVATED
    this.apiService.put(`users/${this.selectedRejectedUser.id}/status`, { status: 'ACTIVATED' })
      .subscribe({
        next: (response) => {
          this.showToastNotification('User has been accepted successfully', 'success');
          
          // Close the popup and reset all states
          this.showRejectedUserPopup = false;
          this.showAcceptConfirmation = false;
          setTimeout(() => {
            this.selectedRejectedUser = null;
          }, 200);
          
          // Refresh the users list to reflect the status change
          this.fetchCompanyUsers();
          
          this.updatingUser = false;
        },
        error: (err) => {
          this.showToastNotification(`Failed to accept user: ${err.error?.message || err.message || 'Unknown error'}`, 'error');
          this.updatingUser = false;
        }
      });
  }

  // Method to check if the current user can modify another user
  canModifyUser(user: CompanyUser | SelectedUser): boolean {
    if (!user) return false;
    
    // Get the role and ensure it's a string
    const role = user.role || '';
    
    // Company admins can only modify company users, not other admins
    return role.toUpperCase() !== 'COMPANY_ADMIN' && 
           role.toUpperCase() !== 'SYSTEM_ADMIN';
  }

  // Method to update user status
  updateUserStatus(newStatus: string): void {
    if (!this.selectedUser) return;
    
    this.updatingUser = true;
    
    // Convert friendly status name to backend format
    const backendStatus = newStatus === 'Active' ? 'ACTIVATED' : 'DEACTIVATED';
    
    // Call the API to update the user status
    this.apiService.put(`users/${this.selectedUser.id}/status`, { status: backendStatus })
      .subscribe({
        next: (response) => {
          // Update the UI
          if (this.selectedUser) {
            this.selectedUser.status = newStatus;
          }
          
          // Also update selectedUserForModal to sync the modal
          if (this.selectedUserForModal) {
            this.selectedUserForModal.status = newStatus;
          }
          
          // Also update in the main users array for consistency
          const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
          if (userIndex >= 0) {
            this.companyUsers[userIndex].status = newStatus;
          }
          
          this.updatingUser = false;
        },
        error: (err) => {
          alert('Failed to update user status.');
          this.updatingUser = false;
        }
      });
  }

  // Method to update user role
  updateUserRole(newRole: string): void {
    if (!this.selectedUser) return;
    
    this.updatingUser = true;
    
    // Call the API to update the user role
    this.apiService.put(`users/${this.selectedUser.id}/role`, { role: newRole })
      .subscribe({
        next: (response) => {
          // Update the UI
          if (this.selectedUser) {
            this.selectedUser.role = newRole;
          }
          
          // Also update selectedUserForModal to sync the modal
          if (this.selectedUserForModal) {
            this.selectedUserForModal.role = newRole;
          }
          
          // Also update in the main users array for consistency
          const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
          if (userIndex >= 0) {
            this.companyUsers[userIndex].role = newRole;
          }
          
          this.updatingUser = false;
        },
        error: (err) => {
          alert('Failed to update user role.');
          this.updatingUser = false;
        }
      });
  }

  // Copy text to clipboard
  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text)
      .then(() => {
        // Clipboard write successful
      })
      .catch(err => {
        // Silent failure
      });
  }

  // Add scroll lock methods
  private disableBodyScroll(): void {
    const scrollY = window.scrollY;
    document.body.classList.add('body-no-scroll');
    document.body.style.position = 'fixed';
    document.body.style.top = `-${scrollY}px`;
    document.body.style.width = '100%';
    document.body.style.overflow = 'hidden';
    
    // Set scrollbar width as CSS variable to prevent layout shift
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    document.documentElement.style.setProperty('--scrollbar-width', `${scrollbarWidth}px`);
    document.documentElement.style.setProperty('--scroll-position', `${scrollY}px`);
  }
  
  private enableBodyScroll(): void {
    const scrollY = document.documentElement.style.getPropertyValue('--scroll-position') || '0';
    document.body.classList.remove('body-no-scroll');
    document.body.style.position = '';
    document.body.style.top = '';
    document.body.style.width = '';
    document.body.style.overflow = '';
    window.scrollTo(0, parseInt(scrollY || '0'));
    
    // Remove the CSS custom properties
    document.documentElement.style.removeProperty('--scrollbar-width');
    document.documentElement.style.removeProperty('--scroll-position');
  }

  showToastNotification(message: string, type: 'success' | 'error' = 'success'): void {
    this.toastMessage = message;
    this.toastType = type;
    this.showToast = true;
    
    // Auto-hide the toast after 3 seconds
    setTimeout(() => {
      this.showToast = false;
    }, 3000);
  }

  // Handle user updates from the modal
  onUserUpdate(event: UserUpdateEvent): void {
    if (event.type === 'status') {
      this.updateUserStatus(event.value);
    } else if (event.type === 'role') {
      this.updateUserRole(event.value);
    }
  }
}
