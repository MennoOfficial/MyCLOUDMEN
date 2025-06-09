import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface PageAction {
  label: string;
  icon?: string;
  action: string;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline';
  disabled?: boolean;
}

export interface BreadcrumbItem {
  label: string;
  route?: string;
  active?: boolean;
}

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-header">
      <!-- Breadcrumbs -->
      <nav class="breadcrumbs" *ngIf="breadcrumbs.length > 0">
        <ol class="breadcrumb-list">
          <li 
            *ngFor="let item of breadcrumbs; let last = last" 
            class="breadcrumb-item"
            [class.active]="item.active || last"
          >
            <a 
              *ngIf="item.route && !item.active && !last" 
              [href]="item.route"
              class="breadcrumb-link"
              (click)="onBreadcrumbClick(item, $event)"
            >
              {{ item.label }}
            </a>
            <span *ngIf="!item.route || item.active || last" class="breadcrumb-text">
              {{ item.label }}
            </span>
            <span *ngIf="!last" class="breadcrumb-separator">
              <span class="material-icons">chevron_right</span>
            </span>
          </li>
        </ol>
      </nav>

      <!-- Header Content -->
      <div class="header-content">
        <!-- Left Section: Title and Description -->
        <div class="header-left">
          <!-- Back Button -->
          <button 
            *ngIf="showBackButton"
            class="back-button"
            (click)="onBackClick()"
            type="button"
            aria-label="Go back"
          >
            <span class="material-icons">arrow_back</span>
          </button>

          <!-- Title Section -->
          <div class="title-section">
            <div class="title-row">
              <span 
                *ngIf="icon" 
                class="material-icons page-icon"
              >
                {{ icon }}
              </span>
              <h1 class="page-title">{{ title }}</h1>
              <span 
                *ngIf="badge" 
                class="page-badge badge"
                [ngClass]="getBadgeClass()"
              >
                {{ badge }}
              </span>
            </div>
            <p *ngIf="description" class="page-description">{{ description }}</p>
            
            <!-- Custom subtitle content -->
            <div *ngIf="hasSubtitleContent" class="page-subtitle">
              <ng-content select="[slot=subtitle]"></ng-content>
            </div>
          </div>
        </div>

        <!-- Right Section: Actions -->
        <div class="header-right" *ngIf="actions.length > 0 || hasCustomActions">
          <!-- Custom actions slot -->
          <div *ngIf="hasCustomActions" class="custom-actions">
            <ng-content select="[slot=actions]"></ng-content>
          </div>

          <!-- Standard actions -->
          <div class="page-actions" *ngIf="actions.length > 0">
            <button
              *ngFor="let action of actions"
              class="btn"
              [ngClass]="'btn-' + (action.variant || 'primary')"
              [disabled]="action.disabled"
              (click)="onActionClick(action)"
              type="button"
            >
              <span *ngIf="action.icon" class="material-icons">{{ action.icon }}</span>
              {{ action.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- Stats or additional info -->
      <div class="header-stats" *ngIf="hasStatsContent">
        <ng-content select="[slot=stats]"></ng-content>
      </div>
    </div>
  `,
  styleUrls: ['./page-header.component.scss']
})
export class PageHeaderComponent {
  @Input() title = '';
  @Input() description?: string;
  @Input() icon?: string;
  @Input() badge?: string;
  @Input() badgeVariant: 'success' | 'warning' | 'danger' | 'info' | 'neutral' = 'neutral';
  @Input() showBackButton = false;
  @Input() breadcrumbs: BreadcrumbItem[] = [];
  @Input() actions: PageAction[] = [];
  @Input() hasCustomActions = false;
  @Input() hasSubtitleContent = false;
  @Input() hasStatsContent = false;

  @Output() actionClick = new EventEmitter<PageAction>();
  @Output() backClick = new EventEmitter<void>();
  @Output() breadcrumbClick = new EventEmitter<BreadcrumbItem>();

  onActionClick(action: PageAction) {
    if (!action.disabled) {
      this.actionClick.emit(action);
    }
  }

  onBackClick() {
    this.backClick.emit();
  }

  onBreadcrumbClick(item: BreadcrumbItem, event: Event) {
    event.preventDefault();
    this.breadcrumbClick.emit(item);
  }

  getBadgeClass(): string {
    return `badge-${this.badgeVariant}`;
  }
} 