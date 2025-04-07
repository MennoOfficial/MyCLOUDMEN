import { Component, OnInit, HostListener, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { AuthService, User } from '../../../core/auth/auth.service';
import { EnvironmentService } from '../../../core/services/environment.service';

interface CompanyUser {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  lastLogin?: string;
  picture?: string;
}

interface PendingUser {
  id: string;
  name: string;
  email: string;
  requestedAt: string;
  primaryDomain: string;
  roles: string[];
  picture?: string;
}

interface SelectedUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role?: string;
  status?: string;
  picture?: string;
  requestedAt?: string;
  primaryDomain?: string;
  roles?: string[];
  lastLogin?: string;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  availableStatuses = ['Active', 'Inactive'];
  availableRoles = ['COMPANY_USER', 'COMPANY_ADMIN'];
  updatingUser = false;

  // Add these properties at the class level
  showToast = false;
  toastMessage = '';
  toastType: 'success' | 'error' = 'success';

  // Toast notifications
  showApprovePopup = false;
  showRejectPopup = false;

  constructor(
    private apiService: ApiService,
    @Inject(AuthService) private authService: AuthService,
    private environmentService: EnvironmentService
  ) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
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

    // Check if click was outside the notification popup
    if (this.showNotificationPopup) {
      const notificationBell = document.querySelector('.notification-bell');
      const notificationPopup = document.querySelector('.notification-popup');
      
      if (notificationBell && notificationPopup) {
        const clickedInsideBell = notificationBell.contains(event.target as Node);
        const clickedInsidePopup = notificationPopup.contains(event.target as Node);
        
        if (!clickedInsideBell && !clickedInsidePopup && !event.defaultPrevented) {
          this.showNotificationPopup = false;
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
  }

  ngOnInit(): void {
    this.initializeCompanyDomain();
  }

  private initializeCompanyDomain(): void {
    this.authService.user$.subscribe({
      next: (user: User | null) => {
        if (user && user.email) {
          // Extract domain from email
          const emailParts = user.email.split('@');
          if (emailParts.length === 2) {
            this.companyDomain = emailParts[1];
            console.log('Company domain set to:', this.companyDomain);
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
        console.error('Error getting user profile:', err);
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
    console.log('Fetching users for domain:', this.companyDomain);
    
    // Call the API to get users with the company's domain
    this.apiService.get<any[]>(`users?domain=${this.companyDomain}&excludeStatus=PENDING`)
      .subscribe({
        next: (users) => {
          try {
            console.log('Raw API response for users:', users);
            console.log('Number of users returned from API:', users.length);
            
            // Map API response to User interface format
            this.companyUsers = users.map(user => {
              console.log('Processing user:', user);
              const processedUser = {
                id: user.id,
                name: user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim(),
                email: user.email,
                role: user.roles && user.roles.length > 0 ? user.roles[0] : 'COMPANY_USER',
                status: user.status === 'ACTIVATED' ? 'Active' : 
                       user.status === 'DEACTIVATED' ? 'Inactive' : 
                       user.status,
                lastLogin: user.lastLogin || null,
                picture: user.picture || ''
              };
              
              // Process profile image URL if it exists
              if (processedUser.picture) {
                processedUser.picture = this.getProxyImageUrl(processedUser.picture);
              }
              
              return processedUser;
            });
            
            // Initialize filtered users with all users
            this.filteredUsers = [...this.companyUsers];
            
            console.log('Mapped company users:', this.companyUsers);
            this.loadingUsers = false;
            this.error = false; // Ensure error flag is reset
            this.sortUsers('name');
          } catch (error) {
            console.error('Error processing user data:', error);
            this.handleError('Error processing user data');
          }
        },
        error: (err) => {
          console.error(`Error fetching users for domain ${this.companyDomain}:`, err);
          this.handleError(`Error fetching users for domain ${this.companyDomain}`);
        }
      });
  }

  fetchPendingUsers(): void {
    if (!this.companyDomain) {
      console.error('No company domain available for fetching pending users');
      this.pendingUsers = [];
      this.pendingCount = 0;
      this.hasPendingUsers = false;
      return;
    }
    
    console.log('Fetching pending users for domain:', this.companyDomain);
    
    // Call the API to get pending users for the domain
    this.apiService.get<PendingUser[]>(`users?domain=${encodeURIComponent(this.companyDomain)}&status=PENDING`)
      .subscribe({
        next: (pendingUsers) => {
          console.log('Pending users from API:', pendingUsers);
          // Process the results
          if (Array.isArray(pendingUsers)) {
            this.pendingUsers = pendingUsers
              .filter(user => user != null) // Filter nulls just in case
              .map(user => {
                // Process profile image URL if it exists
                if (user.picture) {
                  user.picture = this.getProxyImageUrl(user.picture);
                }
                return user;
              });
            
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = this.pendingCount > 0;
            console.log(`Found ${this.pendingCount} pending users for domain ${this.companyDomain}`);
          } else {
            console.error('API did not return a valid array for pending users:', pendingUsers);
            this.pendingUsers = [];
            this.pendingCount = 0;
            this.hasPendingUsers = false;
          }
        },
        error: (err) => {
          console.error(`Error fetching pending users:`, err);
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
      console.log(`Using proxy for profile image: ${this.environmentService.apiUrl}/proxy/image?url=${encodedUrl}`);
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
      console.error(`User with ID ${userId} not found in pending users list`);
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
    
    this.showApprovePopup = true;
  }

  rejectUser(userId: string): void {
    if (!userId) return;
    
    // Find the user being rejected
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      console.error(`User with ID ${userId} not found in pending users list`);
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
    
    this.showRejectPopup = true;
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
    console.log(`Approving user: ${this.selectedUser.firstName} ${this.selectedUser.lastName} (${this.selectedUser.email})`);
    
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
    
    // Close the approval popup
    this.showApprovePopup = false;
    
    // Call the API to approve the pending user
    console.log(`Making API call to approve user ${userId}`);
    
    // Use the correct API endpoint format - users/pending/{id}/approve
    this.apiService.post(`users/pending/${userId}/approve`, {})
      .subscribe({
        next: (response) => {
          console.log(`User ${userId} approved successfully, API response:`, response);
          
          // Display success message using toast
          this.showToastNotification('User has been approved successfully', 'success');
          
          // Refresh the company users list to ensure data consistency
          this.fetchCompanyUsers();
        },
        error: (err) => {
          console.error(`Error approving user ${userId}:`, err);
          console.error('API endpoint used:', `users/pending/${userId}/approve`);
          
          // Show error toast
          this.showToastNotification(`Failed to approve user: ${err.message || 'Unknown error'}`, 'error');
          
          // Revert the optimistic update if the API call fails
          if (this.selectedUser) {
            // Add back to pending users
            if (this.pendingUsers) {
              this.pendingUsers.push(pendingUser);
              this.pendingCount = this.pendingUsers.length;
              this.hasPendingUsers = true;
            }
            
            // Remove from company users
            this.companyUsers = this.companyUsers.filter(u => u.id !== userId);
            this.filteredUsers = [...this.companyUsers];
          }
        }
      });
  }
  
  processRejection(): void {
    if (!this.selectedUser) return;
    
    const userId = this.selectedUser.id;
    console.log(`Rejecting user: ${this.selectedUser.firstName} ${this.selectedUser.lastName} (${this.selectedUser.email})`);
    
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
    
    // Close the rejection popup
    this.showRejectPopup = false;
    
    // Call the API to reject the pending user
    this.apiService.post(`users/pending/${userId}/reject`, {})
      .subscribe({
        next: (response) => {
          console.log(`User ${userId} rejected successfully`);
          this.showToastNotification('User has been rejected successfully', 'success');
        },
        error: (err) => {
          console.error(`Error rejecting user ${userId}:`, err);
          console.error('API endpoint used:', `users/pending/${userId}/reject`);
          
          // Show error toast
          this.showToastNotification(`Failed to reject user: ${err.message || 'Unknown error'}`, 'error');
          
          // Revert the optimistic update if the API call fails
          if (this.selectedUser && this.pendingUsers) {
            this.pendingUsers.push(pendingUser);
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = true;
          }
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
    console.error('Error:', message);
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
    // Convert the CompanyUser to SelectedUser format
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
    
    this.showUserDetailPopup = true;
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
          console.log(`Retrieved last login for user ${user.id}:`, lastLoginTime);
        } else if (this.selectedUser) {
          console.log(`No login history found for user ${user.id} by ID, trying email...`);
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
          console.log(`Retrieved last login for email ${email}:`, lastLoginTime);
        } else {
          console.log(`No login history found for email ${email}`);
        }
      },
      error: (err) => {
        console.error(`Error fetching last login time for email ${email}:`, err);
      }
    });
  }

  // Method to hide user detail popup
  hideUserDetail(): void {
    console.log('Hiding user detail popup');
    this.showUserDetailPopup = false;
    setTimeout(() => {
      this.selectedUser = null;
      // Restore body scrolling
      document.body.style.overflow = '';
    }, 200); // Small delay to allow animation to complete
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
          console.log(`Successfully updated status for user ${this.selectedUser?.id} to ${newStatus}`);
          console.log('API response:', response);
          
          // Update the UI
          if (this.selectedUser) {
            this.selectedUser.status = newStatus;
          }
          
          // Also update in the main users array for consistency
          const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
          if (userIndex >= 0) {
            this.companyUsers[userIndex].status = newStatus;
          }
          
          this.updatingUser = false;
        },
        error: (err) => {
          console.error(`Error updating user status:`, err);
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
          console.log(`Successfully updated role for user ${this.selectedUser?.id} to ${newRole}`);
          console.log('API response:', response);
          
          // Update the UI
          if (this.selectedUser) {
            this.selectedUser.role = newRole;
          }
          
          // Also update in the main users array for consistency
          const userIndex = this.companyUsers.findIndex(u => u.id === this.selectedUser?.id);
          if (userIndex >= 0) {
            this.companyUsers[userIndex].role = newRole;
          }
          
          this.updatingUser = false;
        },
        error: (err) => {
          console.error(`Error updating user role:`, err);
          alert('Failed to update user role.');
          this.updatingUser = false;
        }
      });
  }

  // Copy text to clipboard
  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text)
      .then(() => {
        console.log('Copied to clipboard');
      })
      .catch(err => {
        console.error('Failed to copy text: ', err);
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
}
