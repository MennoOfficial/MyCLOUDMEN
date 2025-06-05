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
      [isOpen]="isOpen"
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
                      [(ngModel)]="user!.status"
                      [disabled]="!canModifyUser() || isUpdating"
                      (change)="onStatusChange()"
                      *ngIf="user">
                <option value="Active">Active</option>
                <option value="Inactive">Inactive</option>
              </select>
              <span class="form-control-icon material-icons">expand_more</span>
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
                      [(ngModel)]="user!.role"
                      [disabled]="!canModifyUser() || isUpdating"
                      (change)="onRoleChange()"
                      *ngIf="user">
                <option value="COMPANY_USER">User</option>
                <option value="COMPANY_ADMIN">Admin</option>
              </select>
              <span class="form-control-icon material-icons">expand_more</span>
            </div>
          </div>

          <!-- Permissions Info -->
          <div class="permissions-info" *ngIf="!canModifyUser()">
            <div class="info-card">
              <span class="material-icons">info</span>
              <span>You cannot modify other administrators or your own account.</span>
            </div>
          </div>

        </div>

        <!-- Loading Overlay -->
        <div class="loading-overlay" *ngIf="isUpdating">
          <div class="loading-spinner"></div>
          <span>Updating user...</span>
        </div>

      </div>
    </app-modal>
  `,
  styleUrls: ['./user-detail-modal.component.scss']
})
export class UserDetailModalComponent {
  @Input() isOpen = false;
  @Input() user: UserDetailData | null = null;
  @Input() currentUserRole = '';
  @Input() isUpdating = false;

  @Output() modalClose = new EventEmitter<void>();
  @Output() userUpdate = new EventEmitter<UserUpdateEvent>();
  @Output() copyEmail = new EventEmitter<string>();

  onClose() {
    this.modalClose.emit();
  }

  onStatusChange() {
    if (this.user) {
      this.userUpdate.emit({
        type: 'status',
        value: this.user.status,
        userId: this.user.id
      });
    }
  }

  onRoleChange() {
    if (this.user) {
      this.userUpdate.emit({
        type: 'role',
        value: this.user.role,
        userId: this.user.id
      });
    }
  }

  copyToClipboard(email: string) {
    this.copyEmail.emit(email);
  }

  canModifyUser(): boolean {
    if (!this.user) return false;
    
    // Company admins can only modify company users, not other admins
    return this.user.role.toUpperCase() !== 'COMPANY_ADMIN' && 
           this.user.role.toUpperCase() !== 'SYSTEM_ADMIN';
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