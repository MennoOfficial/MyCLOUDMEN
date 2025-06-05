import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TableColumn {
  key: string;
  label: string;
  sortable?: boolean;
  hideOnMobile?: boolean;
  type?: 'text' | 'badge' | 'actions' | 'avatar' | 'date' | 'currency';
  width?: string;
  badgeType?: 'status' | 'auth' | 'role';
}

export interface TableAction {
  label: string;
  icon: string;
  action: string;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
}

export interface SortEvent {
  column: string;
  direction: 'asc' | 'desc';
}

export interface PaginationEvent {
  pageIndex: number;
  pageSize: number;
  length: number;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="table-container">
      <!-- Desktop Table -->
      <div class="table-responsive hide-mobile">
        <table class="table">
          <thead>
            <tr>
              <th 
                *ngFor="let column of columns" 
                [class.sortable-header]="column.sortable"
                [class.hide-mobile]="column.hideOnMobile"
                [style.width]="column.width"
                (click)="column.sortable && onSort(column.key)"
              >
                {{ column.label }}
                <span 
                  *ngIf="column.sortable" 
                  class="material-icons sort-icon"
                >
                  {{ getSortIcon(column.key) }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr 
              *ngFor="let item of data; let i = index" 
              class="table-row"
              [class.clickable]="rowClickable"
              (click)="rowClickable && onRowClick(item)"
            >
              <td 
                *ngFor="let column of columns" 
                [class.hide-mobile]="column.hideOnMobile"
                [attr.data-label]="column.label"
              >
                <ng-container [ngSwitch]="column.type">
                  <!-- Avatar -->
                  <div *ngSwitchCase="'avatar'" class="avatar-cell">
                    <div class="user-avatar" *ngIf="!getNestedValue(item, column.key + '.picture')">
                      {{ getNestedValue(item, column.key + '.name')?.charAt(0)?.toUpperCase() }}
                    </div>
                    <img 
                      *ngIf="getNestedValue(item, column.key + '.picture')"
                      [src]="getNestedValue(item, column.key + '.picture')"
                      [alt]="getNestedValue(item, column.key + '.name')"
                      class="user-avatar-img"
                    />
                    <div class="avatar-info">
                      <div class="avatar-name">{{ getNestedValue(item, column.key + '.name') }}</div>
                    </div>
                  </div>
                  
                  <!-- Badge -->
                  <span 
                    *ngSwitchCase="'badge'" 
                    class="badge"
                    [ngClass]="getBadgeClass(getNestedValue(item, column.key), column)"
                  >
                    <span *ngIf="shouldShowBadgeIcon(getNestedValue(item, column.key), column)" class="material-icons">
                      {{ getBadgeIcon(getNestedValue(item, column.key)) }}
                    </span>
                    {{ formatBadgeValue(getNestedValue(item, column.key), column) }}
                  </span>
                  
                  <!-- Actions -->
                  <div *ngSwitchCase="'actions'" class="table-actions">
                    <button 
                      *ngFor="let action of tableActions"
                      class="btn btn-sm"
                      [ngClass]="'btn-' + (action.variant || 'ghost')"
                      (click)="onAction(action.action, item); $event.stopPropagation()"
                    >
                      <span class="material-icons">{{ action.icon }}</span>
                      <span class="hide-mobile">{{ action.label }}</span>
                    </button>
                  </div>
                  
                  <!-- Date -->
                  <span *ngSwitchCase="'date'">
                    {{ formatDate(getNestedValue(item, column.key)) }}
                  </span>
                  
                  <!-- Currency -->
                  <span *ngSwitchCase="'currency'" class="currency-value">
                    {{ formatCurrency(getNestedValue(item, column.key)) }}
                  </span>
                  
                  <!-- Default Text -->
                  <span *ngSwitchDefault>
                    {{ getNestedValue(item, column.key) || '-' }}
                  </span>
                </ng-container>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Mobile Cards -->
      <div class="mobile-cards hide-desktop">
        <div 
          *ngFor="let item of data" 
          class="mobile-card"
          [class.clickable]="rowClickable"
          (click)="rowClickable && onRowClick(item)"
        >
          <div *ngFor="let column of columns" class="mobile-field">
            <div class="field-label">{{ column.label }}</div>
            <div class="field-value">
              <ng-container [ngSwitch]="column.type">
                <!-- Avatar -->
                <div *ngSwitchCase="'avatar'" class="avatar-cell">
                  <div class="user-avatar" *ngIf="!getNestedValue(item, column.key + '.picture')">
                    {{ getNestedValue(item, column.key + '.name')?.charAt(0)?.toUpperCase() }}
                  </div>
                  <img 
                    *ngIf="getNestedValue(item, column.key + '.picture')"
                    [src]="getNestedValue(item, column.key + '.picture')"
                    [alt]="getNestedValue(item, column.key + '.name')"
                    class="user-avatar-img"
                  />
                  <div class="avatar-info">
                    <div class="avatar-name">{{ getNestedValue(item, column.key + '.name') }}</div>
                  </div>
                </div>
                
                <!-- Badge -->
                <span 
                  *ngSwitchCase="'badge'" 
                  class="badge"
                  [ngClass]="getBadgeClass(getNestedValue(item, column.key), column)"
                >
                  <span *ngIf="shouldShowBadgeIcon(getNestedValue(item, column.key), column)" class="material-icons">
                    {{ getBadgeIcon(getNestedValue(item, column.key)) }}
                  </span>
                  {{ formatBadgeValue(getNestedValue(item, column.key), column) }}
                </span>
                
                <!-- Actions -->
                <div *ngSwitchCase="'actions'" class="table-actions">
                  <button 
                    *ngFor="let action of tableActions"
                    class="btn btn-sm"
                    [ngClass]="'btn-' + (action.variant || 'ghost')"
                    (click)="onAction(action.action, item); $event.stopPropagation()"
                  >
                    <span class="material-icons">{{ action.icon }}</span>
                    {{ action.label }}
                  </button>
                </div>
                
                <!-- Date -->
                <span *ngSwitchCase="'date'">
                  {{ formatDate(getNestedValue(item, column.key)) }}
                </span>
                
                <!-- Currency -->
                <span *ngSwitchCase="'currency'" class="currency-value">
                  {{ formatCurrency(getNestedValue(item, column.key)) }}
                </span>
                
                <!-- Default Text -->
                <span *ngSwitchDefault>
                  {{ getNestedValue(item, column.key) || '-' }}
                </span>
              </ng-container>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div *ngIf="data.length === 0" class="empty-state">
        <span class="material-icons">{{ emptyIcon || 'inbox' }}</span>
        <h3>{{ emptyTitle || 'No data found' }}</h3>
        <p>{{ emptyMessage || 'There are no items to display.' }}</p>
        <button 
          *ngIf="emptyAction" 
          class="btn btn-primary"
          (click)="onEmptyAction()"
        >
          {{ emptyAction }}
        </button>
      </div>
    </div>

    <!-- Pagination -->
    <div *ngIf="showPagination && totalItems > 0" class="pagination-container">
      <div class="pagination-info">
        Showing {{ (currentPage - 1) * pageSize + 1 }} - 
        {{ Math.min(currentPage * pageSize, totalItems) }} of 
        {{ totalItems }} items
      </div>

      <div class="pagination-controls">
        <button
          class="btn btn-ghost btn-sm"
          [disabled]="currentPage === 1"
          (click)="onPageChange(currentPage - 1)"
        >
          <span class="material-icons">chevron_left</span>
        </button>

        <div class="page-numbers">
          <button
            *ngFor="let page of getPageNumbers()"
            class="btn btn-sm"
            [ngClass]="page === currentPage ? 'btn-primary' : 'btn-ghost'"
            (click)="onPageChange(page)"
          >
            {{ page }}
          </button>
        </div>

        <button
          class="btn btn-ghost btn-sm"
          [disabled]="currentPage >= Math.ceil(totalItems / pageSize)"
          (click)="onPageChange(currentPage + 1)"
        >
          <span class="material-icons">chevron_right</span>
        </button>
      </div>

      <div class="page-size-selector">
        <label for="pageSize">Items per page:</label>
        <div class="custom-select">
          <select
            id="pageSize"
            [value]="pageSize"
            (change)="onPageSizeChange($event)"
          >
            <option value="10">10</option>
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./data-table.component.scss']
})
export class DataTableComponent implements OnInit {
  @Input() data: any[] = [];
  @Input() columns: TableColumn[] = [];
  @Input() tableActions: TableAction[] = [];
  @Input() loading = false;
  @Input() rowClickable = false;
  @Input() showPagination = false;
  @Input() totalItems = 0;
  @Input() currentPage = 1;
  @Input() pageSize = 25;
  @Input() sortColumn = '';
  @Input() sortDirection: 'asc' | 'desc' = 'asc';
  
  // Empty state
  @Input() emptyIcon?: string;
  @Input() emptyTitle?: string;
  @Input() emptyMessage?: string;
  @Input() emptyAction?: string;

  @Output() sort = new EventEmitter<SortEvent>();
  @Output() rowClick = new EventEmitter<any>();
  @Output() action = new EventEmitter<{action: string, item: any}>();
  @Output() pageChange = new EventEmitter<PaginationEvent>();
  @Output() emptyActionClick = new EventEmitter<void>();

  Math = Math;

  ngOnInit() {
    // Component initialization
  }

  onSort(column: string) {
    const direction = this.sortColumn === column && this.sortDirection === 'asc' ? 'desc' : 'asc';
    this.sort.emit({ column, direction });
  }

  getSortIcon(column: string): string {
    if (this.sortColumn !== column) return 'unfold_more';
    return this.sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  onRowClick(item: any) {
    this.rowClick.emit(item);
  }

  onAction(action: string, item: any) {
    this.action.emit({ action, item });
  }

  onPageChange(page: number) {
    this.pageChange.emit({
      pageIndex: page - 1,
      pageSize: this.pageSize,
      length: this.totalItems
    });
  }

  onPageSizeChange(event: any) {
    const newPageSize = parseInt(event.target.value);
    this.pageChange.emit({
      pageIndex: 0,
      pageSize: newPageSize,
      length: this.totalItems
    });
  }

  onEmptyAction() {
    this.emptyActionClick.emit();
  }

  getPageNumbers(): number[] {
    const totalPages = Math.ceil(this.totalItems / this.pageSize);
    const pages: number[] = [];
    const maxVisible = 5;
    
    let start = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    
    return pages;
  }

  getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((current, key) => current?.[key], obj);
  }

  getBadgeClass(value: any, column?: TableColumn): string {
    if (value === null || value === undefined) return 'badge-neutral';
    
    // Handle boolean values only for authentication status
    if (typeof value === 'boolean' && column?.badgeType === 'auth') {
      return value ? 'badge-success' : 'badge-danger';
    }
    
    // Convert to string for further processing
    const stringValue = String(value);
    const lowerValue = stringValue.toLowerCase();
    
    // For purchase request statuses, apply direct status-specific class
    if (['PENDING', 'AWAITING_CONFIRMATION', 'APPROVED', 'REJECTED'].includes(stringValue)) {
      return `badge-${stringValue}`;
    }
    
    // Handle invoice statuses
    if (lowerValue.includes('overdue')) {
      return 'badge-danger';
    }
    if (lowerValue.includes('paid') || lowerValue.includes('success') || lowerValue.includes('approved')) {
      return 'badge-success';
    }
    
    // Handle specific company status values
    if (stringValue === 'ACTIVE' || lowerValue.includes('active')) {
      return 'badge-success';
    }
    if (stringValue === 'DEACTIVATED' || lowerValue.includes('deactivated') || lowerValue.includes('inactive') || lowerValue.includes('error') || lowerValue.includes('failed') || lowerValue.includes('rejected')) {
      return 'badge-danger';
    }
    if (stringValue === 'SUSPENDED' || lowerValue.includes('suspended')) {
      return 'badge-warning';
    }
    
    // Handle user roles - Admin should be blue, User should be gray
    if (stringValue === 'COMPANY_ADMIN' || lowerValue === 'admin') {
      return 'badge-info';
    }
    if (stringValue === 'COMPANY_USER' || lowerValue === 'user') {
      return 'badge-secondary';
    }
    
    // Handle other statuses
    if (lowerValue.includes('pending') || lowerValue.includes('warning')) {
      return 'badge-warning';
    }
    if (lowerValue.includes('info') || lowerValue.includes('processing')) {
      return 'badge-info';
    }
    
    return 'badge-neutral';
  }

  formatBadgeValue(value: any, column?: TableColumn): string {
    if (value === null || value === undefined) return '';
    
    // Handle boolean values only for authentication status
    if (typeof value === 'boolean' && column?.badgeType === 'auth') {
      return value ? 'Login Success' : 'Login Failed';
    }
    
    // Convert to string for further processing
    const stringValue = String(value);
    
    // Format purchase request statuses
    switch (stringValue) {
      case 'PENDING':
        return 'Pending';
      case 'AWAITING_CONFIRMATION':
        return 'Awaiting Confirmation';
      case 'APPROVED':
        return 'Approved';
      case 'REJECTED':
        return 'Rejected';
      // Convert role values to display format
      case 'COMPANY_ADMIN':
        return 'Admin';
      case 'COMPANY_USER':
        return 'User';
      case 'SYSTEM_ADMIN':
        return 'System Admin';
      default:
        // Convert any other uppercase value to title case
        if (stringValue === stringValue.toUpperCase()) {
          return stringValue.charAt(0) + stringValue.slice(1).toLowerCase();
        }
        return stringValue;
    }
  }

  formatDate(date: string | Date): string {
    if (!date) return '-';
    const d = new Date(date);
    return d.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  formatCurrency(amount: number): string {
    if (amount === null || amount === undefined) return '-';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'EUR'
    }).format(amount);
  }

  getBadgeIcon(value: any): string {
    if (value === null || value === undefined) return 'help_outline';

    // Convert to string for processing
    const stringValue = String(value);
    
    // Return appropriate icon based on status
    switch (stringValue) {
      case 'PENDING':
        return 'schedule';
      case 'AWAITING_CONFIRMATION':
        return 'mail_outline';
      case 'APPROVED':
        return 'check_circle';
      case 'REJECTED':
        return 'cancel';
      case 'ACTIVE':
        return 'check_circle';
      case 'INACTIVE':
      case 'DEACTIVATED':
        return 'block';
      case 'SUSPENDED':
        return 'pause_circle_filled';
      default:
        if (stringValue.toLowerCase().includes('success')) return 'check_circle';
        if (stringValue.toLowerCase().includes('error') || stringValue.toLowerCase().includes('fail')) return 'error';
        if (stringValue.toLowerCase().includes('warning')) return 'warning';
        if (stringValue.toLowerCase().includes('processing')) return 'autorenew';
        return 'fiber_manual_record';
    }
  }
  
  shouldShowBadgeIcon(value: any, column?: TableColumn): boolean {
    // Always show icons for status badges
    if (column?.badgeType === 'status') return true;
    
    // For other types, only show if value has a corresponding icon
    if (value === null || value === undefined) return false;
    
    const stringValue = String(value);
    return ['PENDING', 'AWAITING_CONFIRMATION', 'APPROVED', 'REJECTED', 'ACTIVE', 'INACTIVE', 'DEACTIVATED', 'SUSPENDED'].includes(stringValue) ||
      stringValue.toLowerCase().includes('success') ||
      stringValue.toLowerCase().includes('error') ||
      stringValue.toLowerCase().includes('fail') ||
      stringValue.toLowerCase().includes('warning') ||
      stringValue.toLowerCase().includes('processing');
  }
} 