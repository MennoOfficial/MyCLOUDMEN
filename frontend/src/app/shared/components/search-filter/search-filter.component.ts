import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface FilterOption {
  value: string;
  label: string;
}

export interface FilterConfig {
  key: string;
  label: string;
  type: 'select' | 'date' | 'number';
  options?: FilterOption[];
  placeholder?: string;
}

export interface SearchFilterEvent {
  searchQuery: string;
  filters: { [key: string]: any };
}

@Component({
  selector: 'app-search-filter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="filters-container">
      <!-- Modern Inline Search and Filters -->
      <div class="search-section">
        <!-- Search Box -->
        <div class="search-box">
          <span class="material-icons search-icon">search</span>
          <input
            type="text"
            class="search-input"
            [placeholder]="searchPlaceholder"
            [(ngModel)]="searchQuery"
            (input)="onSearchChange()"
            (keyup.enter)="onSearch()"
          />
          <button
            *ngIf="searchQuery"
            class="clear-search"
            (click)="clearSearch()"
            type="button"
          >
            <span class="material-icons">close</span>
          </button>
        </div>

        <!-- Inline Filters -->
        <div class="filters-section" *ngIf="filterConfigs.length > 0">
          <div class="filters-row">
            <div class="filter-group" *ngFor="let config of filterConfigs">
              <label [for]="config.key">{{ config.label }}</label>
              
              <!-- Select Filter with Custom Wrapper -->
              <div *ngIf="config.type === 'select'" class="filter-select">
                <select
                  [id]="config.key"
                  [(ngModel)]="filters[config.key]"
                  (change)="onFilterChange()"
                >
                  <option value="">All {{ config.label === 'Status' ? 'Statuses' : config.label }}</option>
                  <option 
                    *ngFor="let option of config.options" 
                    [value]="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>

              <!-- Date Filter -->
              <input
                *ngIf="config.type === 'date'"
                type="date"
                [id]="config.key"
                [(ngModel)]="filters[config.key]"
                (change)="onFilterChange()"
              />

              <!-- Number Filter -->
              <input
                *ngIf="config.type === 'number'"
                type="number"
                [id]="config.key"
                [placeholder]="config.placeholder || ''"
                [(ngModel)]="filters[config.key]"
                (input)="onFilterChange()"
              />
            </div>
          </div>

          <!-- Clear Filters Button -->
          <div class="filter-actions" *ngIf="hasActiveFilters()">
            <button
              class="btn btn-ghost btn-sm"
              (click)="clearFilters()"
              type="button"
            >
              <span class="material-icons">filter_alt_off</span>
              Clear filters
            </button>
          </div>
        </div>
      </div>

      <!-- Custom Date Range (if needed) -->
      <div class="custom-range-section" *ngIf="showCustomDateRange">
        <div class="custom-range-inputs">
          <div class="date-range-group">
            <label for="dateFrom">From:</label>
            <input
              type="date"
              id="dateFrom"
              [(ngModel)]="customDateRange.from"
              (change)="onCustomDateChange()"
            />
          </div>
          <div class="date-range-group">
            <label for="dateTo">To:</label>
            <input
              type="date"
              id="dateTo"
              [(ngModel)]="customDateRange.to"
              (change)="onCustomDateChange()"
            />
          </div>
        </div>
      </div>

      <!-- Custom Amount Range (if needed) -->
      <div class="custom-range-section" *ngIf="showCustomAmountRange">
        <div class="custom-range-inputs">
          <div class="amount-range-group">
            <label for="amountMin">Min Amount:</label>
            <input
              type="number"
              id="amountMin"
              placeholder="0"
              [(ngModel)]="customAmountRange.min"
              (input)="onCustomAmountChange()"
            />
          </div>
          <div class="amount-range-group">
            <label for="amountMax">Max Amount:</label>
            <input
              type="number"
              id="amountMax"
              placeholder="No limit"
              [(ngModel)]="customAmountRange.max"
              (input)="onCustomAmountChange()"
            />
          </div>
        </div>
      </div>

      <!-- Results Summary -->
      <div class="results-summary" *ngIf="showResultsSummary && totalResults !== undefined">
        <span class="results-count">
          {{ totalResults }} {{ totalResults === 1 ? 'result' : 'results' }} found
        </span>
        <span class="active-filters" *ngIf="getActiveFiltersCount() > 0">
          {{ getActiveFiltersCount() }} {{ getActiveFiltersCount() === 1 ? 'filter' : 'filters' }} applied
        </span>
      </div>
    </div>
  `,
  styleUrls: ['./search-filter.component.scss']
})
export class SearchFilterComponent implements OnInit {
  @Input() searchPlaceholder = 'Search...';
  @Input() filterConfigs: FilterConfig[] = [];
  @Input() showResultsSummary = false;
  @Input() totalResults?: number;
  @Input() debounceTime = 300;

  // Custom range controls
  @Input() showCustomDateRange = false;
  @Input() showCustomAmountRange = false;

  @Output() searchFilter = new EventEmitter<SearchFilterEvent>();
  @Output() searchChange = new EventEmitter<string>();
  @Output() filterChange = new EventEmitter<{ [key: string]: any }>();

  searchQuery = '';
  filters: { [key: string]: any } = {};
  customDateRange = { from: '', to: '' };
  customAmountRange = { min: null, max: null };

  private searchTimeout: any;

  ngOnInit() {
    // Initialize filters
    this.filterConfigs.forEach(config => {
      this.filters[config.key] = '';
    });
  }

  onSearchChange() {
    this.searchChange.emit(this.searchQuery);
    
    // Debounce search
    if (this.searchTimeout) {
      clearTimeout(this.searchTimeout);
    }
    
    this.searchTimeout = setTimeout(() => {
      this.emitSearchFilter();
    }, this.debounceTime);
  }

  onSearch() {
    if (this.searchTimeout) {
      clearTimeout(this.searchTimeout);
    }
    this.emitSearchFilter();
  }

  onFilterChange() {
    this.filterChange.emit(this.filters);
    this.emitSearchFilter();
  }

  onCustomDateChange() {
    this.emitSearchFilter();
  }

  onCustomAmountChange() {
    this.emitSearchFilter();
  }

  clearSearch() {
    this.searchQuery = '';
    this.onSearchChange();
  }

  clearFilters() {
    // Reset all filters
    Object.keys(this.filters).forEach(key => {
      this.filters[key] = '';
    });
    
    // Reset custom ranges
    this.customDateRange = { from: '', to: '' };
    this.customAmountRange = { min: null, max: null };
    
    this.onFilterChange();
  }

  hasActiveFilters(): boolean {
    return this.getActiveFiltersCount() > 0 || !!this.searchQuery;
  }

  getActiveFiltersCount(): number {
    let count = 0;
    
    // Count regular filters
    Object.values(this.filters).forEach(value => {
      if (value && value !== '') {
        count++;
      }
    });
    
    // Count custom date range
    if (this.customDateRange.from || this.customDateRange.to) {
      count++;
    }
    
    // Count custom amount range
    if (this.customAmountRange.min !== null || this.customAmountRange.max !== null) {
      count++;
    }
    
    return count;
  }

  private emitSearchFilter() {
    const allFilters = {
      ...this.filters,
      ...(this.showCustomDateRange && (this.customDateRange.from || this.customDateRange.to) 
        ? { customDateRange: this.customDateRange } 
        : {}),
      ...(this.showCustomAmountRange && (this.customAmountRange.min !== null || this.customAmountRange.max !== null)
        ? { customAmountRange: this.customAmountRange }
        : {})
    };

    this.searchFilter.emit({
      searchQuery: this.searchQuery,
      filters: allFilters
    });
  }
} 