import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../../core/services/api.service';
import { CompanyDetail } from '../../../../core/models/company.model';
import { EnvironmentService } from '../../../../core/services/environment.service';
import { CompanyStatusType } from '../../../../core/models/enums';
import { CompanyUser, PendingUser } from '../../../../core/models/user.model';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { UserDetailModalComponent, UserDetailData, UserUpdateEvent } from '../../../../shared/components/user-detail-modal/user-detail-modal.component';
import { DataTableComponent, TableColumn, SortEvent } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent, PageAction, BreadcrumbItem } from '../../../../shared/components/page-header/page-header.component';

// Toast interface
interface Toast {
  id: number;
  title: string;
  message: string;
  type: 'success' | 'error';
}

@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent, UserDetailModalComponent, DataTableComponent, PageHeaderComponent],
  templateUrl: './company-detail.component.html',
  styleUrl: './company-detail.component.scss'
})
export class CompanyDetailComponent implements OnInit, OnDestroy {
  company: CompanyDetail | null = null;
  loading = true;
  error = false;
  errorMessage = '';
  
  // Modern page header properties
  breadcrumbs: BreadcrumbItem[] = [];
  headerActions: PageAction[] = [];
  
  // Status modal properties
  showStatusModal = false;
  selectedStatus: string | null = null;
  newStatus: string = '';
  updatingStatus = false;
  
  // Toast notifications
  toasts: Toast[] = [];
  private toastIdCounter = 0;
  
  // Expose enum to template
  CompanyStatusType = CompanyStatusType;
  
  // Status toggle popup (legacy - keeping for backward compatibility)
  showStatusPopup = false;
  updatingStatusLegacy = false;
  
  // Available status types for dropdown
  statusTypes: CompanyStatusType[] = [
    CompanyStatusType.ACTIVE, 
    CompanyStatusType.DEACTIVATED, 
    CompanyStatusType.SUSPENDED
  ];
  
  // Company users
  companyUsers: CompanyUser[] = [];
  loadingUsers = false;
  
  // Pending user requests
  pendingUsers: PendingUser[] = [];
  hasPendingUsers = false;
  pendingCount = 0;
  showPendingPopup = false;
  selectedPendingUser: PendingUser | null = null;
  
  // Notification popup
  showNotificationPopup = false;
  
  // Confirmation popup for user actions
  showUserActionConfirmPopup = false;
  confirmAction: 'approve' | 'reject' | undefined = undefined;
  pendingUserId: string = '';
  selectedUser: PendingUser | null = null; // For pending user actions
  
  // Sorting properties
  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // User detail popup
  showUserDetailPopup = false;
  selectedUserDetail: CompanyUser | null = null; // For user detail modal
  selectedUserForModal: UserDetailData | null = null;
  availableRoles = ['COMPANY_USER', 'COMPANY_ADMIN']; 
  availableStatuses = ['Active', 'Inactive'];
  updatingUser = false;

  // Toast notification (legacy - keeping for backward compatibility)
  showToast = false;
  toastMessage = '';
  toastType: 'success' | 'error' = 'success';

  showQuickConfirm = false;
  quickConfirmAction: 'approve' | 'reject' = 'approve';
  quickConfirmUserId: string = '';

  // Rejected user popup - copied from users component
  showRejectedUserPopup = false;
  selectedRejectedUser: any | null = null;
  showAcceptConfirmation = false;

  // Status change confirmation step
  showStatusConfirmation = false;

  // Table configuration for users
  userTableColumns: TableColumn[] = [
    { key: 'name', label: 'User', sortable: true, width: '40%' },
    { key: 'role', label: 'Role', type: 'badge', sortable: true, badgeType: 'role', width: '20%' },
    { key: 'status', label: 'Status', type: 'badge', sortable: true, badgeType: 'status', width: '20%' },
    { key: 'lastLogin', label: 'Last Login', type: 'date', sortable: true, hideOnMobile: true, width: '20%' }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private environmentService: EnvironmentService
  ) {
    // Initialize breadcrumbs
    this.breadcrumbs = [
      { label: 'Admin', route: '/admin' },
      { label: 'Companies', route: '/admin/companies' },
      { label: 'Company Details', active: true }
    ];
  }

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
    
    // Auto-close modals when clicking outside and re-enable scroll
    // BUT exclude clicks on the status button itself
    if (this.showStatusModal) {
      const modal = target.closest('.confirmation-modal');
      const statusButton = target.closest('.status-action-btn');
      if (!modal && !statusButton) {
        this.showStatusModal = false;
        this.selectedStatus = null;
        this.newStatus = '';
        this.enableBodyScroll();
      }
    }
    
    if (this.showUserActionConfirmPopup) {
      const modal = target.closest('.confirmation-modal');
      if (!modal) {
        this.hideUserActionConfirmPopup();
      }
    }
    
    if (this.showRejectedUserPopup) {
      const modal = target.closest('.confirmation-modal');
      if (!modal) {
        this.closeRejectedUserPopup();
      }
    }
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const companyId = params['id'];
      if (companyId) {
        this.fetchCompanyDetails(companyId);
      } else {
        this.error = true;
        this.errorMessage = 'No company ID provided';
        this.loading = false;
      }
    });
  }

  fetchCompanyDetails(companyId: string): void {
    this.loading = true;
    this.error = false;
    
    this.apiService.get<CompanyDetail>(`teamleader/companies/${companyId}`)
      .subscribe({
        next: (response) => {
          this.company = response;
          
          // Convert legacy status values if needed
          if (this.company.status === 'Active') {
            this.company.status = 'ACTIVE';
          } else if (this.company.status === 'Inactive') {
            this.company.status = 'DEACTIVATED';
          }
          
          // Ensure status has a default value if it's empty
          if (!this.company.status) {
            this.company.status = 'ACTIVE';
          }
          
          this.loading = false;
          
          // Set initial value for status toggle
          this.newStatus = this.company.status as string;
          
          // After loading company, fetch users with the same domain
          this.fetchCompanyUsers();
          this.fetchPendingUsers();
        },
        error: (err) => {
          this.error = true;
          this.errorMessage = 'Failed to load company details. Please try again.';
          this.loading = false;
        }
      });
  }

  fetchCompanyUsers(): void {
    if (!this.company || !this.company.email) return;
    
    this.loadingUsers = true;
    
    // Extract domain from company email for the API query
    const domain = this.company.email.split('@')[1];
    
    // Call the enhanced API with last login data
    this.apiService.get<any[]>(`users/with-last-login?domain=${domain}`)
      .subscribe({
        next: (users) => {
          // Filter out PENDING users on the frontend and map API response to User interface format
          this.companyUsers = users
            .filter(user => user.status !== 'PENDING') // Filter out pending users
            .map(user => ({
            id: user.id,
            name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
            email: user.email,
            // Handle roles from the API response
            role: user.roles && user.roles.length > 0 ? user.roles[0] : 'COMPANY_USER',
              // Convert status to friendly display format - EXACTLY like users component
            status: user.status === 'ACTIVATED' ? 'Active' : 
                   user.status === 'DEACTIVATED' ? 'Inactive' : 
                     user.status === 'REJECTED' ? 'Rejected' :
                   user.status,
              lastLogin: user.lastLogin || undefined, // Use undefined instead of null for proper display
              picture: user.picture || '' // Add picture field for avatars in modals
          }));
          this.loadingUsers = false;
        },
        error: (err) => {
          this.loadingUsers = false;
        }
      });
  }

  fetchPendingUsers(): void {
    if (!this.company || !this.company.email) {
      console.log('No company or email for fetching pending users');
      return;
    }
    
    // Extract domain from company email for the API query
    const domain = this.company.email.split('@')[1];
    console.log('Fetching pending users for domain:', domain);

    // Try multiple endpoints to find pending users
    const endpoints = [
      `users/pending?domain=${domain}`,
      `users?domain=${domain}&status=PENDING`,
      `users?status=PENDING&domain=${domain}`,
      `teamleader/users/pending?domain=${domain}`,
      `teamleader/companies/${this.company.id}/pending-users`
    ];

    // Try the first endpoint
    this.apiService.get<any[]>(endpoints[0])
      .subscribe({
        next: (users) => {
          console.log('Pending users API response (endpoint 1):', users);
          this.processPendingUsers(users);
        },
        error: (err) => {
          console.log('Error with endpoint 1, trying endpoint 2:', err);
          // Try second endpoint
          this.apiService.get<any[]>(endpoints[1])
            .subscribe({
              next: (users) => {
                console.log('Pending users API response (endpoint 2):', users);
                const pendingUsers = users.filter(user => user.status === 'PENDING');
                this.processPendingUsers(pendingUsers);
              },
              error: (err2) => {
                console.log('Error with endpoint 2, trying endpoint 3:', err2);
                // Try third endpoint
                this.apiService.get<any[]>(endpoints[2])
                  .subscribe({
                    next: (users) => {
                      console.log('Pending users API response (endpoint 3):', users);
                      this.processPendingUsers(users);
                    },
                    error: (err3) => {
                      console.log('All endpoints failed:', err3);
                      this.processPendingUsers([]);
                    }
                  });
              }
            });
        }
      });
  }

  private processPendingUsers(users: any[]): void {
    this.pendingUsers = users.map(user => ({
      id: user.id,
      name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
      email: user.email,
      picture: user.picture || '',
      requestedAt: user.dateTimeAdded || user.createdAt || new Date().toISOString(),
      primaryDomain: user.primaryDomain || this.company?.email?.split('@')[1] || 'unknown',
      roles: user.roles || []
    }));
    this.pendingCount = this.pendingUsers.length;
    this.hasPendingUsers = this.pendingUsers.length > 0;
    console.log('Processed pending users:', {
      count: this.pendingCount,
      hasPending: this.hasPendingUsers,
      users: this.pendingUsers
    });
  }

  toggleNotificationPopup(event: MouseEvent): void {
    // Prevent this click from being captured by the document click handler
    event.stopPropagation();
    console.log('Notification popup toggled. Current state:', this.showNotificationPopup);
    console.log('Pending users data:', {
      count: this.pendingCount,
      hasPending: this.hasPendingUsers,
      users: this.pendingUsers
    });
    this.showNotificationPopup = !this.showNotificationPopup;
  }

  toggleStatus(): void {
    console.log('toggleStatus called - before:', { showStatusModal: this.showStatusModal });
    this.showStatusModal = true;
    this.selectedStatus = null;
    this.newStatus = '';
    this.disableBodyScroll();
    console.log('toggleStatus called - after:', { showStatusModal: this.showStatusModal });
  }

  selectStatus(status: string): void {
    this.newStatus = status;
    this.selectedStatus = status;
  }

  showStatusConfirmationDialog(): void {
    this.showStatusConfirmation = true;
  }

  hideStatusConfirmation(): void {
    this.showStatusConfirmation = false;
  }

  getStatusIcon(status: string): string {
    switch (status?.toLowerCase()) {
      case 'active': return 'check_circle';
      case 'inactive': return 'pause_circle';
      case 'suspended': return 'block';
      default: return 'help';
    }
  }

  getStatusButtonClass(status: string): string {
    switch (status?.toLowerCase()) {
      case 'active': return 'btn-success';
      case 'inactive': return 'btn-warning';
      case 'suspended': return 'btn-danger';
      default: return 'btn-primary';
    }
  }

  confirmStatusChange(): void {
    if (!this.selectedStatus || !this.company) {
      return;
    }
    
    this.updatingStatus = true;
    
    const updateData = {
      status: this.selectedStatus
    };
    
    this.apiService.put(`teamleader/companies/${this.company.id}/status`, updateData)
      .subscribe({
        next: (response) => {
          this.company!.status = this.selectedStatus!;
          this.showStatusModal = false;
          this.selectedStatus = null;
          this.newStatus = '';
          this.updatingStatus = false;
          this.enableBodyScroll();
          this.showToastNotification(
            'Success',
            `Company status updated to ${this.getStatusDisplayName(this.selectedStatus!)}`,
            'success'
          );
        },
        error: (err) => {
          this.updatingStatus = false;
          this.showToastNotification(
            'Error',
            'Failed to update company status. Please try again.',
            'error'
          );
        }
      });
  }

  cancelStatusChange(): void {
    if (this.selectedStatus) {
      this.selectedStatus = null;
      this.newStatus = '';
    } else {
      this.showStatusModal = false;
      this.enableBodyScroll();
    }
  }

  showPendingUserActions(user: PendingUser): void {
    this.selectedPendingUser = user;
    this.pendingUserId = user.id;
    this.showPendingPopup = true;
    this.disableBodyScroll();
    
    // Close the notification popup when showing the details popup
    this.showNotificationPopup = false;
    this.showUserActionConfirmPopup = false;
  }

  hidePendingPopup(): void {
    this.showPendingPopup = false;
    this.selectedPendingUser = null;
    this.enableBodyScroll();
  }

  showToastNotification(title: string, message: string, type: 'success' | 'error' = 'success'): void {
    const toast: Toast = {
      id: ++this.toastIdCounter,
      title,
      message,
      type
    };
    this.toasts.push(toast);
    
    // Auto remove after 5 seconds
    setTimeout(() => {
      this.removeToast(toast.id);
    }, 5000);
  }

  removeToast(id: number): void {
    this.toasts = this.toasts.filter(toast => toast.id !== id);
  }

  approveUser(userId: string): void {
    this.confirmAction = 'approve';
    this.selectedUser = this.pendingUsers.find(u => u.id === userId) || null;
    this.showUserActionConfirmPopup = true;
    this.disableBodyScroll();
  }

  rejectUser(userId: string): void {
    this.confirmAction = 'reject';
    this.selectedUser = this.pendingUsers.find(u => u.id === userId) || null;
    this.showUserActionConfirmPopup = true;
    this.disableBodyScroll();
  }

  hideUserActionConfirmPopup(): void {
    this.showUserActionConfirmPopup = false;
    this.confirmAction = undefined;
    this.selectedUser = null;
    this.enableBodyScroll();
  }

  confirmUserAction(): void {
    if (!this.selectedUser || !this.confirmAction) return;
    
    this.updatingUser = true;
    const action = this.confirmAction;
    const userId = this.selectedUser.id;
    
    const apiCall = action === 'approve' 
      ? this.apiService.put(`users/${userId}/approve`, {})
      : this.apiService.put(`users/${userId}/reject`, {});
    
    apiCall.subscribe({
      next: () => {
        // Remove from pending users list
        this.pendingUsers = this.pendingUsers.filter(u => u.id !== userId);
        this.pendingCount = this.pendingUsers.length;
        this.hasPendingUsers = this.pendingCount > 0;
        
        this.updatingUser = false;
        this.hideUserActionConfirmPopup();
        
        this.showToastNotification(
          'Success',
          `User ${action === 'approve' ? 'approved' : 'rejected'} successfully`,
          'success'
        );
        
        // Refresh company users to show the newly approved user
        if (action === 'approve') {
          this.fetchCompanyUsers();
        }
      },
      error: () => {
        this.updatingUser = false;
        this.showToastNotification(
          'Error',
          `Failed to ${action} user. Please try again.`,
          'error'
        );
      }
    });
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

  goBack(): void {
    this.router.navigate(['/system-admin/companies']);
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
    } else if (normalizedRole.includes('SYSTEM')) {
      return 'role-pill role-system-admin';
    } else {
      // Default for regular users or unknown roles
      return 'role-pill role-company-user';
    }
  }

  /**
   * Sorts the company users based on the specified column
   * @param column The column to sort by
   */
  sortUsers(column: string): void {
    // If clicking on the same column, toggle sort direction
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      // New column, set it and default to ascending
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    
    // Sort the users array based on the selected column and direction
    this.companyUsers.sort((a, b) => {
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
          comparison = a.status.localeCompare(b.status);
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

  // Add this new method to test the status endpoint
  testStatusEndpoint(): void {
    this.apiService.get('purchase/status')
      .subscribe({
        next: (response) => {
          this.showToastNotification('Status endpoint is working', 'success');
        },
        error: (err) => {
          this.showToastNotification('Status endpoint failed', 'error');
        }
      });
  }

  // Method to show user detail popup
  showUserDetail(user: CompanyUser): void {
    // Check if user is rejected - show rejected user popup instead
    if (user.status === 'Rejected') {
      this.showRejectedUserDetail(user);
      return;
    }

    this.selectedUserDetail = user;
    
    // Convert to UserDetailData format
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
    this.disableBodyScroll();
  }

  // Method to handle user updates from the modal
  onUserUpdate(event: UserUpdateEvent): void {
    if (event.type === 'status') {
      this.updateUserStatus(event.value);
    } else if (event.type === 'role') {
      this.updateUserRole(event.value);
    }
  }

  /**
   * Fetch the last login time for a user, trying by ID first and then falling back to email if needed
   * @param user The user to fetch last login for
   */
  private fetchLastLoginTime(user: CompanyUser): void {
    this.apiService.get<any>(`users/${user.id}/last-login`)
      .subscribe({
        next: (response) => {
          if (response && response.lastLogin) {
            // Update the user object with the fetched last login time
            const userIndex = this.companyUsers.findIndex(u => u.id === user.id);
            if (userIndex >= 0) {
              this.companyUsers[userIndex].lastLogin = response.lastLogin;
            }
          } else {
            // If no last login found by ID, try using email as fallback
          this.fetchLastLoginByEmail(user.email);
        }
      },
      error: (err) => {
          // If getting last login by ID fails, try email as fallback
          this.fetchLastLoginByEmail(user.email);
      }
    });
  }
  
  /**
   * Fetch the last login time for a user by email
   * @param email The user's email
   */
  private fetchLastLoginByEmail(email: string): void {
    this.apiService.get<any>(`users/email/${encodeURIComponent(email)}/last-login`)
      .subscribe({
        next: (response) => {
          if (response && response.lastLogin) {
            // Update the user object with the fetched last login time
            const userIndex = this.companyUsers.findIndex(u => u.email === email);
            if (userIndex >= 0) {
              this.companyUsers[userIndex].lastLogin = response.lastLogin;
            }
        }
      },
      error: (err) => {
          // Silently handle the error - some users might not have login records
      }
    });
  }

  // Method to hide user detail popup
  hideUserDetail(): void {
    this.showUserDetailPopup = false;
    this.enableBodyScroll();
    setTimeout(() => {
      this.selectedUserDetail = null;
      this.selectedUserForModal = null;
    }, 200); // Small delay to allow animation to complete
  }

  // Method to update user role
  updateUserRole(newRole: string): void {
    if (!this.selectedUserDetail) return;
    
    this.updatingUser = true;
        
    // Call the API to update the user role
    this.apiService.put(`users/${this.selectedUserDetail.id}/role`, { role: newRole })
      .subscribe({
        next: (response) => {
          this.updateRoleLocally(newRole);
          this.showToastNotification('Success', 'User role updated successfully', 'success');
          this.updatingUser = false;
        },
        error: (err) => {
          this.showToastNotification('Error', 'Failed to update user role', 'error');
          this.updatingUser = false;
        }
      });
  }

  // Update role locally in the UI
  private updateRoleLocally(newRole: string): void {
    if (!this.selectedUserDetail) return;
        
    // Update the selected user's role
    this.selectedUserDetail.role = newRole;
    
    // Also update selectedUserForModal to sync the modal
    if (this.selectedUserForModal) {
      this.selectedUserForModal.role = newRole;
    }
    
    // Also update in the main users array for consistency
    const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUserDetail?.id);
    if (userIndex >= 0) {
      this.companyUsers[userIndex].role = newRole;
    }
  }

  // Method to update user status
  updateUserStatus(newStatus: string): void {
    if (!this.selectedUserDetail) return;
    
    this.updatingUser = true;
    
    // Convert friendly status name to backend format
    const backendStatus = newStatus === 'Active' ? 'ACTIVATED' : 'DEACTIVATED';
    
    // Call the API to update the user status
    this.apiService.put(`users/${this.selectedUserDetail.id}/status`, { status: backendStatus })
      .subscribe({
        next: (response) => {
          this.updateStatusLocally(newStatus);
          this.showToastNotification('Success', 'User status updated successfully', 'success');
          this.updatingUser = false;
        },
        error: (err) => {
          this.showToastNotification('Error', 'Failed to update user status', 'error');
          this.updatingUser = false;
        }
      });
  }

  // Update status locally in the UI
  private updateStatusLocally(newStatus: string): void {
    if (!this.selectedUserDetail) return;
    
    // Update the selected user's status
    this.selectedUserDetail.status = newStatus;
    
    // Also update selectedUserForModal to sync the modal
    if (this.selectedUserForModal) {
      this.selectedUserForModal.status = newStatus;
    }
    
    // Also update in the main users array for consistency
    const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUserDetail?.id);
    if (userIndex >= 0) {
      this.companyUsers[userIndex].status = newStatus;
    }
  }

  // Format the timestamp for better display
  formatTimestamp(timestamp: string): string {
    if (!timestamp) return 'Never';
    
    const date = new Date(timestamp);
    return date.toLocaleString();
  }
  
  /**
   * Copy text to clipboard
   * @param text Text to copy to clipboard
   */
  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text)
      .then(() => {
        // Clipboard write successful - could add a toast notification here
      })
      .catch(err => {
        // Silent failure for copy operations
      });
  }

  // Helper method to check if a user is an admin (either system or company)
  isUserAdmin(role: string): boolean {
    const normalizedRole = role.toUpperCase();
    return normalizedRole.includes('ADMIN');
  }

  // Helper method to check specifically for system admin role
  isSystemAdmin(role: string): boolean {
    const normalizedRole = role.toUpperCase();
    return normalizedRole === 'SYSTEM_ADMIN';
  }

  // Utility methods
  formatDate(date: string | Date | undefined): string {
    if (!date) return 'Unknown';
    try {
      return new Date(date).toLocaleDateString();
    } catch {
      return 'Invalid date';
    }
  }

  // Add scroll lock methods
  private disableBodyScroll(): void {
    if (typeof window === 'undefined') return;
    
    const scrollY = window.scrollY;
    const body = document.body;
    const html = document.documentElement;
    
    // Add no-scroll class
    body.classList.add('body-no-scroll');
    
    // Set scroll position and prevent scrolling
    body.style.position = 'fixed';
    body.style.top = `-${scrollY}px`;
    body.style.left = '0';
    body.style.right = '0';
    body.style.width = '100%';
    body.style.overflow = 'hidden';
    
    // Also prevent scrolling on html
    html.style.overflow = 'hidden';
    
    // Set scrollbar width as CSS variable to prevent layout shift
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    html.style.setProperty('--scrollbar-width', `${scrollbarWidth}px`);
    html.style.setProperty('--scroll-position', `${scrollY}px`);
  }
  
  private enableBodyScroll(): void {
    if (typeof window === 'undefined') return;
    
    const body = document.body;
    const html = document.documentElement;
    const scrollY = html.style.getPropertyValue('--scroll-position') || '0';
    
    // Remove no-scroll class
    body.classList.remove('body-no-scroll');
    
    // Reset body styles
    body.style.position = '';
    body.style.top = '';
    body.style.left = '';
    body.style.right = '';
    body.style.width = '';
    body.style.overflow = '';
    
    // Reset html styles
    html.style.overflow = '';
    
    // Restore scroll position
    window.scrollTo(0, parseInt(scrollY || '0'));
    
    // Remove the CSS custom properties
    html.style.removeProperty('--scrollbar-width');
    html.style.removeProperty('--scroll-position');
  }

  // Helper method to get readable status names
  getStatusDisplayName(status: string): string {
    switch(status) {
      case 'ACTIVE': return 'Active';
      case 'DEACTIVATED': return 'Deactivated';
      case 'SUSPENDED': return 'Suspended';
      default: return status;
    }
  }

  // Helper method to get status description
  getStatusDescription(status: string): string {
    switch(status) {
      case 'ACTIVE': 
        return 'Company has full access to all platform features and services.';
      case 'DEACTIVATED': 
        return 'Company has ended partnership. All access is disabled.';
      case 'SUSPENDED': 
        return 'Company is temporarily blocked. All access is disabled.';
      default: 
        return '';
    }
  }

  // Helper to get status CSS class
  getStatusClass(status: string): string {
    switch(status) {
      case 'ACTIVE': return 'status-active';
      case 'DEACTIVATED': return 'status-inactive';
      case 'SUSPENDED': return 'status-suspended';
      // Handle legacy status values
      case 'Active': return 'status-active';
      case 'Inactive': return 'status-inactive';
      default: return '';
    }
  }

  /**
   * Format website URL to ensure it has a proper protocol
   */
  formatWebsiteUrl(website: string | undefined): string {
    if (!website) return '';
    
    // If the URL already has a protocol, return as is
    if (website.startsWith('http://') || website.startsWith('https://')) {
      return website;
    }
    
    // Default to https for secure connections
    return `https://${website}`;
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
      primaryDomain: this.company?.email?.split('@')?.[1] || 'Unknown Domain',
      requestedAt: user.lastLogin || new Date().toISOString() // Use lastLogin as fallback for requestedAt
    };
    
    this.showRejectedUserPopup = true;
    this.showAcceptConfirmation = false; // Reset confirmation state
    this.disableBodyScroll();
  }

  // Method to close rejected user popup
  closeRejectedUserPopup(): void {
    this.showRejectedUserPopup = false;
    this.showAcceptConfirmation = false;
    this.enableBodyScroll();
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
          this.enableBodyScroll();
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

  // Modern page header methods
  onHeaderAction(action: PageAction): void {
    switch (action.action) {
      case 'refresh':
        this.fetchCompanyDetails(this.route.snapshot.params['id']);
        break;
      // Add more actions as needed
    }
  }

  // Handle table sorting
  onSort(event: SortEvent): void {
    this.sortUsers(event.column);
  }

  ngOnDestroy(): void {
    this.enableBodyScroll();
  }
} 