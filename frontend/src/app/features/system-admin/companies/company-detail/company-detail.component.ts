import { Component, OnInit, HostListener } from '@angular/core';
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

@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent, UserDetailModalComponent, DataTableComponent],
  templateUrl: './company-detail.component.html',
  styleUrl: './company-detail.component.scss'
})
export class CompanyDetailComponent implements OnInit {
  company: CompanyDetail | null = null;
  loading = true;
  error = false;
  errorMessage = '';
  
  // Expose enum to template
  CompanyStatusType = CompanyStatusType;
  
  // Status toggle popup
  showStatusPopup = false;
  newStatus: CompanyStatusType = CompanyStatusType.ACTIVE;
  updatingStatus = false;
  
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
  
  // Sorting properties
  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // User detail popup
  showUserDetailPopup = false;
  selectedUser: CompanyUser | null = null;
  selectedUserForModal: UserDetailData | null = null;
  availableRoles = ['COMPANY_USER', 'COMPANY_ADMIN']; 
  availableStatuses = ['Active', 'Inactive'];
  updatingUser = false;

  // Toast notification
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
          this.newStatus = this.company.status as CompanyStatusType;
          
          // After loading company, fetch users with the same domain
          this.fetchCompanyUsers();
          this.fetchPendingUsers();
        },
        error: (err) => {
          console.error(`Error fetching company details for ID ${companyId}:`, err);
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
          console.error(`Error fetching users for domain ${domain}:`, err);
          this.loadingUsers = false;
          this.companyUsers = []; // Set empty array on error
        }
      });
  }

  fetchPendingUsers(): void {
    if (!this.company || !this.company.email) return;
    
    
    // Extract primary domain from company email
    const companyEmailParts = this.company.email.split('@');
    const companyDomain = companyEmailParts[1];

    // Use the new direct endpoint to get pending users by domain
    this.apiService.get<any[]>(`users?domain=${companyDomain}&status=PENDING`)
      .subscribe({
        next: (pendingUsers) => {
          // Map backend fields to frontend PendingUser interface
          this.pendingUsers = pendingUsers.map(user => ({
            id: user.id,
            name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
            email: user.email,
            requestedAt: user.dateTimeAdded || user.requestedAt || new Date().toISOString(), // Map dateTimeAdded to requestedAt
            status: user.status,
            primaryDomain: user.primaryDomain,
            roles: user.roles || [],
            firstName: user.firstName,
            lastName: user.lastName,
            picture: user.picture,
            auth0Id: user.auth0Id,
            dateTimeAdded: user.dateTimeAdded,
            dateTimeChanged: user.dateTimeChanged
          }));
          
          this.pendingCount = this.pendingUsers.length;
          this.hasPendingUsers = this.pendingCount > 0;
        },
        error: (err) => {
          console.error(`Error fetching pending users:`, err);
          this.pendingUsers = [];
          this.pendingCount = 0;
          this.hasPendingUsers = false;
        }
      });
  }


  toggleNotificationPopup(event: MouseEvent): void {
    // Prevent this click from being captured by the document click handler
    event.stopPropagation();
    this.showNotificationPopup = !this.showNotificationPopup;
  }

  toggleStatus(): void {
    this.showStatusPopup = true;
    this.disableBodyScroll();
  }

  selectStatus(status: CompanyStatusType): void {
    this.newStatus = status;
  }

  showStatusConfirmationDialog(): void {
    this.showStatusConfirmation = true;
  }

  hideStatusConfirmation(): void {
    this.showStatusConfirmation = false;
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'check_circle';
      case 'DEACTIVATED':
        return 'pause_circle';
      case 'SUSPENDED':
        return 'block';
      default:
        return 'help_outline';
    }
  }

  getStatusButtonClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'btn-success';
      case 'DEACTIVATED':
        return 'btn-warning';
      case 'SUSPENDED':
        return 'btn-danger';
      default:
        return 'btn-primary';
    }
  }

  confirmStatusChange(): void {
    this.updatingStatus = true;
    
    this.apiService.put(`teamleader/companies/${this.company?.id}/status`, { status: this.newStatus })
      .subscribe({
        next: (response) => {
          if (this.company) {
            this.company.status = this.newStatus;
          }
          this.showToastNotification('Company status updated successfully', 'success');
          this.showStatusPopup = false;
          this.showStatusConfirmation = false;
          this.updatingStatus = false;
          this.enableBodyScroll();
        },
        error: (err) => {
          this.showToastNotification('Failed to update company status', 'error');
          this.updatingStatus = false;
        }
      });
  }

  cancelStatusChange(): void {
    this.showStatusPopup = false;
    this.showStatusConfirmation = false;
    this.newStatus = this.company?.status as CompanyStatusType || CompanyStatusType.ACTIVE;
    this.enableBodyScroll();
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

  showToastNotification(message: string, type: 'success' | 'error' = 'success'): void {
    this.toastMessage = message;
    this.toastType = type;
    this.showToast = true;
    
    // Auto-hide the toast after 3 seconds
    setTimeout(() => {
      this.showToast = false;
    }, 3000);
  }

  approveUser(userId: string): void {
    this.pendingUserId = userId;
    this.confirmAction = 'approve';
    this.showUserActionConfirmPopup = true;
    this.showNotificationPopup = false;
    
    // Find and set the selectedPendingUser for the popup
    const user = this.pendingUsers.find(u => u.id === userId);
    if (user) {
      this.selectedPendingUser = user;
    }
    
    // Prevent background scrolling
    this.disableBodyScroll();
  }

  rejectUser(userId: string): void {
    this.pendingUserId = userId;
    this.confirmAction = 'reject';
    this.showUserActionConfirmPopup = true;
    this.showNotificationPopup = false;
    
    // Find and set the selectedPendingUser for the popup
    const user = this.pendingUsers.find(u => u.id === userId);
    if (user) {
      this.selectedPendingUser = user;
    }
    
    // Prevent background scrolling
    this.disableBodyScroll();
  }

  cancelQuickAction(): void {
    this.showQuickConfirm = false;
    this.showNotificationPopup = true;
  }

  async confirmQuickAction(): Promise<void> {
    if (this.quickConfirmAction === 'approve') {
      await this.handleApproveUser(this.quickConfirmUserId);
    } else {
      await this.handleRejectUser(this.quickConfirmUserId);
    }
    this.showQuickConfirm = false;
    this.showNotificationPopup = true;
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
    if (!this.company || !this.company.id) return;
    
    const testUrl = `teamleader/companies/${this.company.id}/status/test`;
    
    this.apiService.get<any>(testUrl)
      .subscribe({
        next: (response) => {
        },
        error: (err) => {
          console.error('Status endpoint test failed:', err);
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

    this.selectedUser = user;
    
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
    // The API returns a LocalDateTime which is serialized as a string, or null if no login found
    this.apiService.get<string | null>(`auth-logs/user/${user.id}/last-login`).subscribe({
      next: (lastLoginTime) => {
        if (lastLoginTime && this.selectedUser) {
          this.selectedUser.lastLogin = lastLoginTime;
        } else if (this.selectedUser) {
          // If no login found by ID, try by email as fallback
          this.fetchLastLoginByEmail(user.email);
        }
      },
      error: (err) => {
        console.warn(`No last login found for user ID ${user.id}, trying email fallback:`, err);
        // On error, try by email as fallback
        if (this.selectedUser) {
          this.fetchLastLoginByEmail(user.email);
        }
      }
    });
  }
  
  /**
   * Fetch the last login time for a user by email
   * @param email The user's email
   */
  private fetchLastLoginByEmail(email: string): void {
    // Encode the email for URL safety
    const encodedEmail = encodeURIComponent(email);
    
    this.apiService.get<string | null>(`auth-logs/email/${encodedEmail}/last-login`).subscribe({
      next: (lastLoginTime) => {
        if (lastLoginTime && this.selectedUser) {
          this.selectedUser.lastLogin = lastLoginTime;
        } else if (this.selectedUser) {
          // No last login found, set to undefined to show "Never logged in"
          this.selectedUser.lastLogin = undefined;
        }
      },
      error: (err) => {
        console.warn(`No last login found for email ${email}:`, err);
        // Set to undefined to show "Never logged in"
        if (this.selectedUser) {
          this.selectedUser.lastLogin = undefined;
        }
      }
    });
  }

  // Method to hide user detail popup
  hideUserDetail(): void {
    this.showUserDetailPopup = false;
    this.enableBodyScroll();
    setTimeout(() => {
      this.selectedUser = null;
      this.selectedUserForModal = null;
    }, 200); // Small delay to allow animation to complete
  }

  // Method to update user role
  updateUserRole(newRole: string): void {
    if (!this.selectedUser) return;
    
    // System admins can change anyone's role - no restrictions
    this.updatingUser = true;
        
    // Call the API directly
    this.apiService.put(`users/${this.selectedUser.id}/role`, { role: newRole })
      .subscribe({
        next: (response) => {
          
          this.updateRoleLocally(newRole);
          this.updatingUser = false;
        },
        error: (err) => {
          console.error(`Error updating user role:`, err);
          console.error('Status code:', err.status);
          console.error('Error details:', err.error);
          
          // Provide more specific error message based on HTTP status
          let errorMessage = 'Failed to update user role. ';
          
          if (err.status === 403) {
            errorMessage += 'You do not have permission to change this user\'s role.';
          } else if (err.status === 400) {
            errorMessage += 'Invalid role value or request format.';
          } else if (err.status === 404) {
            errorMessage += 'User not found.';
          } else if (err.status === 0) {
            errorMessage += 'Cannot connect to the server. Please check your connection.';
          } else {
            errorMessage += `Server returned error: ${err.status}`;
          }
          
          alert(errorMessage);
          
          // Update UI optimistically anyway
          this.updateRoleLocally(newRole);
          this.updatingUser = false;
        }
      });
  }

  // Update role locally in the UI
  private updateRoleLocally(newRole: string): void {
    if (!this.selectedUser) return;
        
    // Update the selected user's role
    this.selectedUser.role = newRole;
    
    // Also update selectedUserForModal to sync the modal
    if (this.selectedUserForModal) {
      this.selectedUserForModal.role = newRole;
    }
    
    // Also update in the main users array for consistency
    const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
    if (userIndex >= 0) {
      this.companyUsers[userIndex].role = newRole;
    }
  }

  // Method to update user status
  updateUserStatus(newStatus: string): void {
    if (!this.selectedUser) return;
    
    this.updatingUser = true;
    
    // Convert friendly status name to backend format
    const backendStatus = newStatus === 'Active' ? 'ACTIVATED' : 'DEACTIVATED';
    
    
    // Call the API directly without checking if the user exists first
    this.apiService.put(`users/${this.selectedUser.id}/status`, { status: backendStatus })
      .subscribe({
        next: (response) => {
          
          this.updateStatusLocally(newStatus);
          this.updatingUser = false;
        },
        error: (err) => {
          console.error(`Error updating user status:`, err);
          console.error('Status code:', err.status);
          console.error('Error details:', err.error);
          
          // Provide more specific error message based on HTTP status
          let errorMessage = 'Failed to update user status. ';
          
          if (err.status === 403) {
            errorMessage += 'You do not have permission to change this user\'s status.';
          } else if (err.status === 400) {
            errorMessage += 'Invalid status value or request format.';
          } else if (err.status === 404) {
            errorMessage += 'User not found.';
          } else if (err.status === 0) {
            errorMessage += 'Cannot connect to the server. Please check your connection.';
          } else {
            errorMessage += `Server returned error: ${err.status}`;
          }
          
          alert(errorMessage);
          
          // Update UI optimistically anyway
          this.updateStatusLocally(newStatus);
          this.updatingUser = false;
        }
      });
  }

  // Update status locally in the UI
  private updateStatusLocally(newStatus: string): void {
    if (!this.selectedUser) return;
    
    // Update the selected user's status
    this.selectedUser.status = newStatus;
    
    // Also update selectedUserForModal to sync the modal
    if (this.selectedUserForModal) {
      this.selectedUserForModal.status = newStatus;
    }
    
    // Also update in the main users array for consistency
    const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
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
      .catch(err => {
        console.error('Failed to copy text: ', err);
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

  async handleApproveUser(userId: string): Promise<void> {
    if (!userId) return;
    
    try {
      await this.apiService.post(`users/pending/${userId}/approve`, {}).toPromise();
      this.showToastNotification('User approved successfully', 'success');
      this.fetchPendingUsers();
    } catch (err) {
      console.error('Error approving user:', err);
      console.error('API endpoint used:', `users/pending/${userId}/approve`);
      this.showToastNotification('Failed to approve user. Please try again.', 'error');
    }
  }

  async handleRejectUser(userId: string): Promise<void> {
    if (!userId) return;
    
    try {
      await this.apiService.post(`users/pending/${userId}/reject`, {}).toPromise();
      this.showToastNotification('User rejected successfully', 'success');
      this.fetchPendingUsers();
    } catch (err) {
      console.error('Error rejecting user:', err);
      console.error('API endpoint used:', `users/pending/${userId}/reject`);
      this.showToastNotification('Failed to reject user. Please try again.', 'error');
    }
  }

  confirmUserAction(): void {
    // Hide the confirmation popup but keep scroll locked until API completes
    this.showUserActionConfirmPopup = false;
    
    // Show loading indicator
    this.updatingUser = true;
    
    if (this.confirmAction === 'approve') {
      this.handleApproveUser(this.pendingUserId)
        .then(() => {
          this.updatingUser = false;
          this.enableBodyScroll();
        })
        .catch(() => {
          this.updatingUser = false;
          this.enableBodyScroll();
        });
    } else {
      this.handleRejectUser(this.pendingUserId)
        .then(() => {
          this.updatingUser = false;
          this.enableBodyScroll();
        })
        .catch(() => {
          this.updatingUser = false;
          this.enableBodyScroll();
        });
    }
  }
  
  cancelUserAction(): void {
    this.showUserActionConfirmPopup = false;
    this.selectedPendingUser = null;
    this.pendingUserId = '';
    
    // Enable scrolling
    this.enableBodyScroll();
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

  // Method to hide user action confirmation
  hideUserActionConfirm(): void {
    this.showUserActionConfirmPopup = false;
    this.selectedPendingUser = null;
    this.pendingUserId = '';
    
    // Enable scrolling
    this.enableBodyScroll();
  }

  // Handle table sorting
  onSort(event: SortEvent): void {
    this.sortUsers(event.column);
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
          console.error('Error accepting rejected user:', err);
          this.showToastNotification(`Failed to accept user: ${err.error?.message || err.message || 'Unknown error'}`, 'error');
          this.updatingUser = false;
        }
      });
  }
} 