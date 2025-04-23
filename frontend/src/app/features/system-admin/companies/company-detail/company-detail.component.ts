import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../../core/services/api.service';
import { CompanyDetail } from '../../../../core/models/company.model';
import { EnvironmentService } from '../../../../core/services/environment.service';
import { CompanyStatusType } from '../../../../core/models/enums';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { CompanyUser, PendingUser } from '../../../../core/models/user.model';

@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent],
  templateUrl: './company-detail.component.html',
  styleUrl: './company-detail.component.scss'
})
export class CompanyDetailComponent implements OnInit {
  company: CompanyDetail | null = null;
  loading = true;
  error = false;
  errorMessage = '';
  
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
  confirmAction: 'approve' | 'reject' = 'approve';
  pendingUserId: string = '';
  
  // Sorting properties
  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // User detail popup
  showUserDetailPopup = false;
  selectedUser: CompanyUser | null = null;
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private environmentService: EnvironmentService
  ) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    // Check if click was outside the notification popup
    const notificationBell = document.querySelector('.notification-bell');
    const notificationPopup = document.querySelector('.notification-popup');
    
    if (this.showNotificationPopup && notificationBell && notificationPopup) {
      const clickedInsideBell = notificationBell.contains(event.target as Node);
      const clickedInsidePopup = notificationPopup.contains(event.target as Node);
      
      if (!clickedInsideBell && !clickedInsidePopup) {
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
    
    // Call the enhanced API to get non-pending users with the company's domain
    this.apiService.get<any[]>(`users?domain=${domain}&excludeStatus=PENDING`)
      .subscribe({
        next: (users) => {
          // Map API response to User interface format
          this.companyUsers = users.map(user => ({
            id: user.id,
            name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
            email: user.email,
            // Handle roles from the API response
            role: user.roles && user.roles.length > 0 ? user.roles[0] : 'COMPANY_USER',
            // Convert status to friendly display format
            status: user.status === 'ACTIVATED' ? 'Active' : 
                   user.status === 'DEACTIVATED' ? 'Inactive' : 
                   user.status,
            lastLogin: user.lastLogin || null
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
    this.apiService.get<PendingUser[]>(`users?domain=${companyDomain}&status=PENDING`)
      .subscribe({
        next: (pendingUsers) => {
          this.pendingUsers = pendingUsers;
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
    // Don't preselect a status, let the user choose from dropdown
    this.showStatusPopup = true;
  }

  confirmStatusChange(): void {
    
    if (!this.company || !this.company.id) {
      console.error('Cannot update status: Company or company ID is missing');
      return;
    }
    
    // First test if the path is accessible
    this.testStatusEndpoint();
    
    const previousStatus = this.company.status;
    // Store the new status temporarily
    const newStatus = this.newStatus;
    
    
    // Set loading indicator
    this.updatingStatus = true;
    
    // Try using teamleaderId if it exists
    const idToUse = this.company.teamleaderId || this.company.id;
    
    // Log the API URL for debugging
    const apiUrl = `teamleader/companies/${idToUse}/status`;
    const fullUrl = `${this.environmentService.apiUrl}/${apiUrl}`;    
    // Call the API to update the status
    this.apiService.put(apiUrl, { status: newStatus })
      .subscribe({
        next: (response: any) => {  // Type as 'any' to handle the flexible response structure
          
          // Update the company status with the returned value if possible
          if (this.company) { // Check for null
            if (response && typeof response === 'object') {
              if ('status' in response && typeof response.status === 'string') {
                this.company.status = response.status;
              } else {
                // If response doesn't contain status field directly, default to the new status
                this.company.status = newStatus;
              }
            } else {
              // If response is not as expected, use new status
              this.company.status = newStatus;
            }
          }
          
          this.showStatusPopup = false;
          this.updatingStatus = false;
          this.showToastNotification(`Company status updated to ${this.getStatusDisplayName(newStatus)}`, 'success');
        },
        error: (err) => {
          let errorMessage = '';
          
          // Try to extract error details from the response if available
          if (err.error && typeof err.error === 'object') {
            if (err.error.message) {
              errorMessage = `Server error: ${err.error.message}`;
              console.error('Server error message:', err.error.message);
            } else {
              errorMessage = `Server error: ${JSON.stringify(err.error)}`;
            }
          } else {
            errorMessage = err.message || 'Unknown error';
          }
          
          if (this.company) {
            this.company.status = previousStatus;
          }
          
          // Show an error notification with more details
          this.showToastNotification(`Failed to update company status: ${errorMessage}`, 'error');
          console.error(`Status update failed (${err.status || 'unknown status'})`, err);
          this.updatingStatus = false;
        }
      });
  }

  cancelStatusChange(): void {
    this.showStatusPopup = false;
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
    this.selectedUser = { ...user }; // Create a copy to avoid direct modification
    this.showUserDetailPopup = true;
    
    // More aggressive approach to prevent scrolling
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
    
    // Fetch the last login time for the user if it's not already set
    if (!this.selectedUser.lastLogin) {
      this.fetchLastLoginTime(user);
    }
  }

  /**
   * Fetch the last login time for a user, trying by ID first and then falling back to email if needed
   * @param user The user to fetch last login for
   */
  private fetchLastLoginTime(user: CompanyUser): void {
    // The API returns a LocalDateTime which is serialized as a string
    this.apiService.get<string>(`auth-logs/user/${user.id}/last-login`).subscribe({
      next: (lastLoginTime) => {
        if (lastLoginTime && this.selectedUser) {
          this.selectedUser.lastLogin = lastLoginTime;
        } else if (this.selectedUser) {
          // If no login found by ID, try by email as fallback
          this.fetchLastLoginByEmail(user.email);
        }
      },
      error: (err) => {
        console.error(`Error fetching last login time for user ${user.id}:`, err);
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
    
    this.apiService.get<string>(`auth-logs/email/${encodedEmail}/last-login`).subscribe({
      next: (lastLoginTime) => {
        if (lastLoginTime && this.selectedUser) {
          this.selectedUser.lastLogin = lastLoginTime;
        }
      },
      error: (err) => {
        console.error(`Error fetching last login time for email ${email}:`, err);
      }
    });
  }

  // Method to hide user detail popup
  hideUserDetail(): void {
    this.showUserDetailPopup = false;
    this.selectedUser = null;
    
    // Restore scrolling position
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

  // Method to update user role
  updateUserRole(newRole: string): void {
    if (!this.selectedUser) return;
    
    // Only prevent changing role for system admins, allow company admins to be changed
    if (this.isSystemAdmin(this.selectedUser.role)) {
      alert('System administrator roles cannot be changed.');
      return;
    }
    
    this.updatingUser = true;
        
    // Call the API directly without checking if the user exists first
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
    if (!this.pendingUserId) return;

    const action = this.confirmAction;
    
    // Show a loading indicator
    this.updatingUser = true;
    
    // Use handleApproveUser or handleRejectUser which have proper error handling
    if (action === 'approve') {
      this.handleApproveUser(this.pendingUserId)
        .then(() => {
          // Close all popups
          this.showUserActionConfirmPopup = false;
          this.showPendingPopup = false;
          this.showNotificationPopup = false;
          this.updatingUser = false;
          this.enableBodyScroll();
        })
        .catch(() => {
          this.updatingUser = false;
          this.enableBodyScroll();
        });
    } else { // reject
      this.handleRejectUser(this.pendingUserId)
        .then(() => {
          // Close all popups
          this.showUserActionConfirmPopup = false;
          this.showPendingPopup = false;
          this.showNotificationPopup = false;
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
    this.showPendingPopup = false;
    
    // Enable scrolling
    this.enableBodyScroll();
  }

  // Add these utility methods for scroll management
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
        return 'Active companies have full access to all features and appear in regular searches.';
      case 'DEACTIVATED': 
        return 'Deactivated companies have no access to the platform and won\'t appear in searches.';
      case 'SUSPENDED': 
        return 'Suspended companies have temporary limited access to the platform and reduced visibility.';
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
} 