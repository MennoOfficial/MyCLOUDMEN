import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../modal/modal.component';

export interface UserDetailData {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  status: string;
  picture?: string;
  lastLogin?: string;
}

export interface UserUpdateEvent {
  type: 'status' | 'role';
  value: string;
  userId: string;
}

@Component({
  selector: 'app-user-detail-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  template: `
    <app-modal
      [isOpen]="isOpen && !showConfirmation"
      title="User Details"
      icon="person"
      size="md"
      variant="default"
      [showFooter]="false"
      (modalClose)="onClose()"
    >
      <div class="user-detail-content">
        
        <!-- User Profile Section -->
        <div class="user-profile-section">
          <div class="user-avatar-container">
            <img *ngIf="user?.picture" 
                 [src]="user?.picture" 
                 [alt]="getFullName()"
                 class="user-avatar-img" />
            <div *ngIf="!user?.picture" class="user-avatar-placeholder">
              {{getInitials()}}
            </div>
          </div>
          
          <div class="user-info-container">
            <h4 class="user-name" *ngIf="user">{{getFullName()}}</h4>
            <p class="user-email" *ngIf="user">
              {{user.email}}
              <button class="copy-email-btn" 
                      (click)="copyToClipboard(user.email)"
                      title="Copy email">
                <span class="material-icons">content_copy</span>
              </button>
            </p>
            <p class="last-login-info" *ngIf="user?.lastLogin">
              <span class="material-icons">schedule</span>
              Last login: {{formatTimeAgo(user?.lastLogin)}}
            </p>
            <p class="last-login-info never-logged-in" *ngIf="user && !user.lastLogin">
              <span class="material-icons">schedule</span>
              Never logged in
            </p>
          </div>
        </div>

        <!-- User Details Form -->
        <div class="user-details-form">
          
          <!-- Status Section -->
          <div class="form-section">
            <label class="form-label">
              <span class="material-icons">toggle_on</span>
              Status
            </label>
            <div class="form-control-wrapper">
              <select class="form-control"
                      [(ngModel)]="pendingStatus"
                      [disabled]="!canModifyUser() || isUpdating"
                      (change)="onPendingStatusChange()"
                      *ngIf="user">
                <option value="Active">Active</option>
                <option value="Inactive">Inactive</option>
              </select>
              <span class="form-control-icon material-icons">expand_more</span>
            </div>
            <div class="change-indicator" *ngIf="hasStatusChanged()">
              <span class="material-icons">info</span>
              <span>Changes pending - click Save to apply</span>
            </div>
          </div>

          <!-- Role Section -->
          <div class="form-section">
            <label class="form-label">
              <span class="material-icons">admin_panel_settings</span>
              Role
            </label>
            <div class="form-control-wrapper">
              <select class="form-control"
                      [(ngModel)]="pendingRole"
                      [disabled]="!canModifyUser() || isUpdating"
                      (change)="onPendingRoleChange()"
                      *ngIf="user">
                <option value="COMPANY_USER">User</option>
                <option value="COMPANY_ADMIN">Admin</option>
              </select>
              <span class="form-control-icon material-icons">expand_more</span>
            </div>
            <div class="change-indicator" *ngIf="hasRoleChanged()">
              <span class="material-icons">info</span>
              <span>Changes pending - click Save to apply</span>
            </div>
          </div>

          <!-- Permissions Info -->
          <div class="permissions-info" *ngIf="!canModifyUser()">
            <div class="info-card">
              <span class="material-icons">info</span>
              <span>You cannot modify other administrators or your own account.</span>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="action-buttons" *ngIf="canModifyUser() && hasChanges()">
            <button class="btn btn-secondary" 
                    (click)="resetChanges()"
                    [disabled]="isUpdating">
              <span class="material-icons">undo</span>
              Reset
            </button>
            <button class="btn btn-primary" 
                    (click)="showSaveConfirmation()"
                    [disabled]="isUpdating">
              <span class="material-icons">save</span>
              Save Changes
            </button>
          </div>

        </div>

        <!-- Loading Overlay -->
        <div class="loading-overlay" *ngIf="isUpdating">
          <div class="loading-spinner"></div>
          <span>Updating user...</span>
        </div>

      </div>
    </app-modal>

    <!-- Save Confirmation Modal -->
    <div *ngIf="showConfirmation" class="modal-overlay">
      <div class="confirmation-modal">
        <div class="modal-header">
          <div class="header-icon save-icon">
            <span class="material-icons">save</span>
          </div>
          <h2>Save Changes</h2>
        </div>

        <div class="modal-body">
          <div class="user-summary" *ngIf="user">
            <div class="user-avatar-large">
              <img *ngIf="user.picture"
                   [src]="user.picture"
                   [alt]="getFullName()" />
              <span *ngIf="!user.picture">
                {{ getInitials() }}
              </span>
            </div>
            <div class="user-details">
              <h3>{{ getFullName() }}</h3>
              <p class="user-email">{{ user.email }}</p>
            </div>
          </div>

          <div class="changes-summary">
            <h4>The following changes will be applied:</h4>
            <ul class="changes-list">
              <li *ngIf="hasStatusChanged()" class="change-item">
                <span class="material-icons">toggle_on</span>
                <span>Status: <strong>{{ user?.status }}</strong> → <strong>{{ pendingStatus }}</strong></span>
              </li>
              <li *ngIf="hasRoleChanged()" class="change-item">
                <span class="material-icons">admin_panel_settings</span>
                <span>Role: <strong>{{ formatRole(user?.role || '') }}</strong> → <strong>{{ formatRole(pendingRole) }}</strong></span>
              </li>
            </ul>
          </div>

          <div class="confirmation-message">
            <p>Are you sure you want to apply these changes? This action cannot be undone.</p>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" (click)="hideConfirmation()">
            Cancel
          </button>
          <button class="btn btn-success" 
                  (click)="confirmSaveChanges()"
                  [disabled]="isUpdating">
            <span *ngIf="isUpdating" class="loading-indicator">
              <div class="loading-spinner-small"></div>
            </span>
            <span class="material-icons" *ngIf="!isUpdating">check</span>
            {{ isUpdating ? "Saving..." : "Yes, Save Changes" }}
          </button>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./user-detail-modal.component.scss']
})
export class UserDetailModalComponent {
  @Input() isOpen = false;
  @Input() user: UserDetailData | null = null;
  @Input() currentUserRole = '';
  @Input() isUpdating = false;
  /**
   * Allow editing of admin users (set to true for system admins who can modify anyone)
   * When false (default), prevents editing of COMPANY_ADMIN and SYSTEM_ADMIN users
   */
  @Input() allowAdminEdit = false;

  @Output() modalClose = new EventEmitter<void>();
  @Output() userUpdate = new EventEmitter<UserUpdateEvent>();
  @Output() copyEmail = new EventEmitter<string>();

  // Pending changes tracking
  pendingStatus = '';
  pendingRole = '';
  showConfirmation = false;

  ngOnChanges() {
    // Initialize pending values when user changes
    if (this.user) {
      this.pendingStatus = this.user.status;
      this.pendingRole = this.user.role;
    }
  }

  onClose() {
    this.resetChanges();
    this.hideConfirmation();
    this.modalClose.emit();
  }

  onPendingStatusChange() {
    // Just update the pending value, don't emit changes yet
  }

  onPendingRoleChange() {
    // Just update the pending value, don't emit changes yet
  }

  copyToClipboard(email: string) {
    this.copyEmail.emit(email);
  }

  hasChanges(): boolean {
    return this.hasStatusChanged() || this.hasRoleChanged();
  }

  hasStatusChanged(): boolean {
    return this.user ? this.pendingStatus !== this.user.status : false;
  }

  hasRoleChanged(): boolean {
    return this.user ? this.pendingRole !== this.user.role : false;
  }

  resetChanges() {
    if (this.user) {
      this.pendingStatus = this.user.status;
      this.pendingRole = this.user.role;
    }
  }

  showSaveConfirmation() {
    this.showConfirmation = true;
  }

  hideConfirmation() {
    this.showConfirmation = false;
  }

  confirmSaveChanges() {
    if (!this.user) return;

    // Apply status changes
    if (this.hasStatusChanged()) {
      this.userUpdate.emit({
        type: 'status',
        value: this.pendingStatus,
        userId: this.user.id
      });
    }

    // Apply role changes
    if (this.hasRoleChanged()) {
      this.userUpdate.emit({
        type: 'role',
        value: this.pendingRole,
        userId: this.user.id
      });
    }

    this.hideConfirmation();
  }

  canModifyUser(): boolean {
    if (!this.user) return false;
    
    // If allowAdminEdit is true (system admin context), allow editing of anyone
    if (this.allowAdminEdit) return true;
    
    // Company admins can only modify company users, not other admins
    return this.user.role.toUpperCase() !== 'COMPANY_ADMIN' && 
           this.user.role.toUpperCase() !== 'SYSTEM_ADMIN';
  }

  formatRole(role: string): string {
    switch(role) {
      case 'COMPANY_ADMIN': return 'Admin';
      case 'COMPANY_USER': return 'User';
      default: return role;
    }
  }

  getInitials(): string {
    if (!this.user) return '';
    const first = this.user.firstName?.charAt(0) || '';
    const last = this.user.lastName?.charAt(0) || '';
    return (first + last).toUpperCase();
  }

  getFullName(): string {
    if (!this.user) return '';
    return `${this.user.firstName || ''} ${this.user.lastName || ''}`.trim();
  }

  formatTimeAgo(timestamp: string | undefined): string {
    if (!timestamp) return 'Unknown';
    
    try {
      const date = new Date(timestamp);
      if (isNaN(date.getTime())) return 'Unknown';
      
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      
      if (diffMs < 0) return 'Just now';
      
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHour = Math.floor(diffMin / 60);
      const diffDay = Math.floor(diffHour / 24);
      
      if (diffSec < 60) return 'Just now';
      if (diffMin < 60) return `${diffMin} ${diffMin === 1 ? 'minute' : 'minutes'} ago`;
      if (diffHour < 24) return `${diffHour} ${diffHour === 1 ? 'hour' : 'hours'} ago`;
      if (diffDay < 30) return `${diffDay} ${diffDay === 1 ? 'day' : 'days'} ago`;
      
      return date.toLocaleDateString();
    } catch (error) {
      return 'Unknown';
    }
  }
} 