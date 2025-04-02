import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../../core/services/api.service';
import { CompanyDetail } from '../../../../core/models/company.model';
import { EnvironmentService } from '../../../../core/services/environment.service';

interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  lastLogin?: string;
}

interface PendingUser {
  id: string;
  name: string;
  email: string;
  requestedAt: string;
  status: string;
  primaryDomain: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
  picture?: string;
  auth0Id?: string;
  dateTimeAdded?: string;
  dateTimeChanged?: string;
}

@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  newStatus: 'Active' | 'Inactive' = 'Active';
  updatingStatus = false; // Add loading state tracking
  
  // Company users
  companyUsers: User[] = [];
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
  selectedUser: User | null = null;
  availableRoles = ['COMPANY_USER', 'COMPANY_ADMIN'];  // Only allow setting to regular user
  availableStatuses = ['Active', 'Inactive'];
  updatingUser = false;

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
          
          // Ensure status has a default value if it's empty
          if (!this.company.status) {
            this.company.status = 'Active';
          }
          
          this.loading = false;
          
          // Set initial value for status toggle
          this.newStatus = this.company.status as 'Active' | 'Inactive';
          
          // After loading company, fetch users with the same domain
          this.fetchCompanyUsers();
          this.fetchPendingUsers();
          
          console.log('COMPANY DATA:', JSON.stringify(response, null, 2));
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
    
    // Safely extract domain from company email for the API query
    const emailParts = this.company.email.trim().split('@');
    if (emailParts.length !== 2 || !emailParts[1]) {
      console.error('Invalid company email format:', this.company.email);
      this.loadingUsers = false;
      this.companyUsers = [];
      return;
    }
    
    const domain = emailParts[1];
    
    console.log('Fetching users with domain:', domain);
    
    // Call the enhanced API to get non-pending users with the company's domain
    this.apiService.get<any[]>(`users?domain=${domain}&excludeStatus=PENDING`)
      .subscribe({
        next: (users) => {
          try {
            console.log('Full raw response:', JSON.stringify(users));
            console.log('Raw API response for users:', users);
            console.log('Number of users returned from API:', users.length);
            
            // Map API response to User interface format
            this.companyUsers = users.map(user => {
              console.log('Processing user:', user);
              return {
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
              };
            });
            
            console.log('Mapped company users:', this.companyUsers);
            this.loadingUsers = false;
          } catch (error) {
            console.error('Error processing user data:', error);
            this.loadingUsers = false;
            this.companyUsers = []; // Set empty array on error
          }
        },
        error: (err) => {
          console.error(`Error fetching users for domain ${domain}:`, err);
          console.error('Error details:', err.status, err.message, err.error);
          this.loadingUsers = false;
          this.companyUsers = []; // Set empty array on error
        }
      });
  }

  fetchPendingUsers(): void {
    if (!this.company || !this.company.email) return;
    
    console.log('Company email:', this.company.email);
    
    // Safely extract domain from company email
    const companyEmailParts = this.company.email.trim().split('@');
    if (companyEmailParts.length !== 2 || !companyEmailParts[1]) {
      console.error('Invalid company email format:', this.company.email);
      this.pendingUsers = [];
      this.pendingCount = 0;
      this.hasPendingUsers = false;
      return;
    }
    
    const companyDomain = companyEmailParts[1];

    console.log('Searching for pending users with domain:', companyDomain);
    // Use the direct endpoint to get pending users by domain
    this.apiService.get<PendingUser[]>(`users?domain=${encodeURIComponent(companyDomain)}&status=PENDING`)
      .subscribe({
        next: (pendingUsers) => {
          console.log('Pending users from API:', pendingUsers);
          // Process the results properly
          if (Array.isArray(pendingUsers)) {
            this.pendingUsers = pendingUsers.filter(user => user != null); // Filter nulls just in case
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = this.pendingCount > 0;
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


  toggleNotificationPopup(event: MouseEvent): void {
    // Prevent this click from being captured by the document click handler
    event.stopPropagation();
    this.showNotificationPopup = !this.showNotificationPopup;
  }

  toggleStatus(): void {
    this.newStatus = this.company?.status === 'Active' ? 'Inactive' : 'Active';
    this.showStatusPopup = true;
  }

  confirmStatusChange(): void {
    console.log('⚠️ Please check the Network tab in your browser console (F12) to monitor the API request');
    
    if (!this.company || !this.company.id) {
      console.error('Cannot update status: Company or company ID is missing');
      return;
    }
    
    // First test if the path is accessible
    this.testStatusEndpoint();
    
    const previousStatus = this.company.status;
    // Store the new status temporarily
    const newStatus = this.newStatus;
    
    console.log(`Updating company ${this.company.id} status from ${previousStatus} to ${newStatus}`);
    console.log('Company object:', this.company);
    
    // Set loading indicator
    this.updatingStatus = true;
    
    // Try using teamleaderId if it exists
    const idToUse = this.company.teamleaderId || this.company.id;
    console.log(`Using ID for status update: ${idToUse}, Type: ${this.company.teamleaderId ? 'teamleaderId' : 'MongoDB id'}`);
    
    // Log the API URL for debugging
    const apiUrl = `teamleader/companies/${idToUse}/status`;
    const fullUrl = `${this.environmentService.apiUrl}/${apiUrl}`;
    console.log('Endpoint path:', apiUrl);
    console.log('Full API URL being called:', fullUrl);
    
    // Call the API to update the status
    this.apiService.put(apiUrl, { status: newStatus })
      .subscribe({
        next: (response: any) => {  // Type as 'any' to handle the flexible response structure
          console.log('Status update successful. API response:', response);
          
          // Update the company status with the returned value if possible
          if (this.company) { // Check for null
            if (response && typeof response === 'object') {
              if ('status' in response && typeof response.status === 'string') {
                this.company.status = response.status;
                console.log(`Updated company status to ${response.status} based on API response`);
              } else {
                // If response doesn't contain status field directly, default to the new status
                this.company.status = newStatus;
                console.log(`Updated company status to ${newStatus} (no status field in API response)`);
              }
            } else {
              // If response is not as expected, use new status
              this.company.status = newStatus;
              console.log(`Updated company status to ${newStatus} (unexpected API response format)`);
            }
          }
          
          this.showStatusPopup = false;
          this.updatingStatus = false;
        },
        error: (err) => {
          // Revert on error
          console.error('Error updating company status:', err.message);
          console.error('Status code:', err.status);
          console.error('Full error object:', err);
          console.error('Endpoint used:', apiUrl);
          console.error('Full URL called:', fullUrl);
          console.error('Payload:', { status: newStatus });
          
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
            console.log(`Reverted company status to ${previousStatus} due to API error`);
          }
          
          // Show an error notification with more details
          alert(`Failed to update company status (${err.status || 'unknown status'}).\n
Please check the browser console (F12) for more details.\n
Error: ${errorMessage}`);
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

  approveUser(userId: string): void {
    if (!userId) return;
    
    // Find the user being approved
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      console.error(`User with ID ${userId} not found in pending users list`);
      return;
    }
    
    // Show the confirmation popup
    this.selectedPendingUser = user;
    this.pendingUserId = userId;
    this.confirmAction = 'approve';
    this.showUserActionConfirmPopup = true;
    
    // Close the notification popup to avoid UI clutter
    this.showNotificationPopup = false;
  }

  rejectUser(userId: string): void {
    if (!userId) return;
    
    // Find the user being rejected
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      console.error(`User with ID ${userId} not found in pending users list`);
      return;
    }
    
    // Show the confirmation popup
    this.selectedPendingUser = user;
    this.pendingUserId = userId;
    this.confirmAction = 'reject';
    this.showUserActionConfirmPopup = true;
    
    // Close the notification popup to avoid UI clutter
    this.showNotificationPopup = false;
  }
  
  confirmUserAction(): void {
    // Hide the confirmation popup
    this.showUserActionConfirmPopup = false;
    
    if (this.confirmAction === 'approve') {
      this.processApproval(this.pendingUserId);
    } else {
      this.processRejection(this.pendingUserId);
    }
  }
  
  cancelUserAction(): void {
    this.showUserActionConfirmPopup = false;
    this.selectedPendingUser = null;
    this.pendingUserId = '';
  }
  
  processApproval(userId: string): void {
    if (!userId) return;
    
    // Find the user being approved
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      console.error(`User with ID ${userId} not found in pending users list`);
      return;
    }
    
    console.log(`Approving user: ${user.name} (${user.email})`);
    
    // Create a copy of the user for adding to the company users list
    const approvedUser: User = {
      id: user.id,
      name: user.name,
      email: user.email,
      role: user.roles[0] || 'COMPANY_USER',
      status: 'Active'
    };
    
    // Optimistically update UI first for better UX
    // 1. Remove from pending users
    this.pendingUsers = this.pendingUsers.filter(u => u.id !== userId);
    this.pendingCount = this.pendingUsers.length;
    this.hasPendingUsers = this.pendingCount > 0;
    
    // 2. Add to company users
    this.companyUsers = [...this.companyUsers, approvedUser];
    
    // Call the new dedicated API endpoint to approve the pending user
    this.apiService.post(`users/pending/${userId}/approve`, {})
      .subscribe({
        next: (response) => {
          console.log(`User ${userId} approved successfully`);
          
          // Refresh the company users list to ensure data consistency
          this.fetchCompanyUsers();
        },
        error: (err) => {
          console.error(`Error approving user ${userId}:`, err);
          
          // Revert the optimistic update if the API call fails
          if (user) {
            // Add back to pending users
            this.pendingUsers.push(user);
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = true;
            
            // Remove from company users
            this.companyUsers = this.companyUsers.filter(u => u.id !== userId);
          }
        }
      });
  }
  
  processRejection(userId: string): void {
    if (!userId) return;
    
    // Find the user being rejected
    const user = this.pendingUsers.find(u => u.id === userId);
    if (!user) {
      console.error(`User with ID ${userId} not found in pending users list`);
      return;
    }
    
    console.log(`Rejecting user: ${user.name} (${user.email})`);
    
    // Optimistically update UI first for better UX
    this.pendingUsers = this.pendingUsers.filter(u => u.id !== userId);
    this.pendingCount = this.pendingUsers.length;
    this.hasPendingUsers = this.pendingCount > 0;
    
    // Call the new dedicated API endpoint to reject the pending user
    this.apiService.post(`users/pending/${userId}/reject`, {})
      .subscribe({
        next: (response) => {
          console.log(`User ${userId} rejected successfully`);
        },
        error: (err) => {
          console.error(`Error rejecting user ${userId}:`, err);
          
          // Revert the optimistic update if the API call fails
          if (user) {
            this.pendingUsers.push(user);
            this.pendingCount = this.pendingUsers.length;
            this.hasPendingUsers = true;
          }
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
    if (!this.company || !this.company.id) return;
    
    const testUrl = `teamleader/companies/${this.company.id}/status/test`;
    console.log('Testing status endpoint accessibility at:', testUrl);
    
    this.apiService.get<any>(testUrl)
      .subscribe({
        next: (response) => {
          console.log('Status endpoint test successful:', response);
        },
        error: (err) => {
          console.error('Status endpoint test failed:', err);
        }
      });
  }

  // Method to show user detail popup
  showUserDetail(user: User): void {
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
  private fetchLastLoginTime(user: User): void {
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
    
    console.log(`Updating role for user ${this.selectedUser.id} to ${newRole}`);
    
    // Call the API directly without checking if the user exists first
    this.apiService.put(`users/${this.selectedUser.id}/role`, { role: newRole })
      .subscribe({
        next: (response) => {
          console.log(`Successfully updated role for user ${this.selectedUser?.id} to ${newRole}`);
          console.log('API response:', response);
          
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
    
    console.log(`Updating role locally to ${newRole} for UI consistency`);
    
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
    
    console.log(`Updating status for user ${this.selectedUser.id} to ${newStatus} (${backendStatus})`);
    
    // Call the API directly without checking if the user exists first
    this.apiService.put(`users/${this.selectedUser.id}/status`, { status: backendStatus })
      .subscribe({
        next: (response) => {
          console.log(`Successfully updated status for user ${this.selectedUser?.id} to ${newStatus}`);
          console.log('API response:', response);
          
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
    
    console.log(`Updating status locally to ${newStatus} for UI consistency`);
    
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
      .then(() => {
        // You could show a toast notification here, but we'll keep it simple
        console.log('Copied to clipboard:', text);
      })
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
} 