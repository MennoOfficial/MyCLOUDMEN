import { Component, OnInit, HostListener, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CompanyListItem, CompanyDetail, CompanyListResponse, CompanyAddress, ContactInfo } from '../../../core/models/company.model';
import { CompanyStatusType } from '../../../core/models/enums';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { PageEvent } from '../../../core/models/common.model';

// Import new standardized components
import { PageHeaderComponent, PageAction } from '../../../shared/components/page-header/page-header.component';
import { SearchFilterComponent, FilterConfig, SearchFilterEvent } from '../../../shared/components/search-filter/search-filter.component';
import { DataTableComponent, TableColumn, TableAction, SortEvent, PaginationEvent } from '../../../shared/components/data-table/data-table.component';

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LoadingSpinnerComponent,
    PageHeaderComponent,
    SearchFilterComponent,
    DataTableComponent
  ],
  templateUrl: './companies.component.html',
  styleUrl: './companies.component.scss'
})
export class CompaniesComponent implements OnInit {
  companies: CompanyListItem[] = [];
  filteredCompanies: CompanyListItem[] = [];
  
  // Admin domain filtering
  adminDomain: string = '';
  
  // Pagination
  pageSize = 25;
  pageSizeOptions = [5, 10, 25, 50];
  pageIndex = 0;
  totalCompanies = 0;
  
  // Sorting
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';
  
  // Filters
  searchQuery = '';
  statusFilter = '';

  loading = true;
  error = false;
  authRequired = false;
  authUrl = '';
  
  // Responsive design
  isMobile = false;

  // Toast notification
  showToast = false;
  toastMessage = '';

  // Make Math available in the template
  Math = Math;

  // Configuration for standardized components
  headerActions: PageAction[] = [
    // Removed refresh and export actions for cleaner interface
  ];

  filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'DEACTIVATED', label: 'Deactivated' },
        { value: 'SUSPENDED', label: 'Suspended' }
      ]
    }
  ];

  tableColumns: TableColumn[] = [
    {
      key: 'name',
      label: 'Company',
      sortable: true,
      type: 'text'
    },
    {
      key: 'email',
      label: 'Email',
      sortable: true,
      type: 'text'
    },
    {
      key: 'phoneNumber',
      label: 'Phone',
      sortable: false,
      type: 'text',
      hideOnMobile: true
    },
    {
      key: 'vatNumber',
      label: 'VAT Number',
      sortable: false,
      type: 'text',
      hideOnMobile: true
    },
    {
      key: 'status',
      label: 'Status',
      sortable: false,
      type: 'badge'
    }
  ];

  tableActions: TableAction[] = [
    // Removed view details action - rows are clickable instead
  ];

  constructor(
    private apiService: ApiService,
    private router: Router,
    @Inject(AuthService) private authService: AuthService
  ) {
    this.checkScreenSize();
    this.initializeAdminDomain();
  }

  @HostListener('window:resize')
  onResize() {
    this.checkScreenSize();
  }

  checkScreenSize() {
    // Keeping this for potential responsive adjustments
    this.isMobile = window.innerWidth < 768;
  }

  ngOnInit(): void {
    this.fetchCompanies();
  }

  // Header action handler
  onHeaderAction(action: PageAction): void {
    switch (action.action) {
      case 'refresh':
        this.fetchCompanies();
        break;
      case 'export':
        this.exportCompanies();
        break;
    }
  }

  // Search and filter handler
  onSearchFilter(event: SearchFilterEvent): void {
    this.searchQuery = event.searchQuery;
    this.statusFilter = event.filters['status'] || '';
    this.pageIndex = 0; // Reset to first page
    this.applyFilters();
  }

  // Table sort handler
  onSort(event: SortEvent): void {
    this.sortColumn = event.column;
    this.sortDirection = event.direction;
    this.fetchCompanies();
  }

  // Table action handler
  onTableAction(event: { action: string, item: any }): void {
    switch (event.action) {
      case 'view':
        this.navigateToCompanyDetails(event.item);
        break;
    }
  }

  // Clear all filters
  clearAllFilters(): void {
    this.searchQuery = '';
    this.statusFilter = '';
    this.pageIndex = 0;
    this.fetchCompanies();
  }

  // Export companies
  exportCompanies(): void {
    // Implement export functionality
    this.showToastMessage('Export functionality coming soon!');
  }

  fetchCompanies(): void {
    this.loading = true;
    this.error = false;
    
    this.apiService.get<any>('teamleader/companies')
      .subscribe({
        next: (response) => {
          this.companies = response.companies || [];
          this.totalCompanies = this.companies.length;
          this.applyFilters(); // Apply filters to populate filteredCompanies
          this.loading = false;
        },
        error: (err) => {
          this.error = true;
          this.loading = false;
        }
      });
  }

  navigateToCompanyDetails(company: CompanyListItem): void {
    // Determine which ID to use for navigation
    const companyId = company.teamleaderId || company.id;
    
    if (companyId) {
      this.router.navigate(['/system-admin/companies', companyId])
        .then(success => {
          // Navigation completed
        })
        .catch(error => {
          // Navigation failed
        });
    } else {
      // No valid ID found
    }
  }

  searchCompanies(): void {
    if (!this.searchQuery.trim()) {
      this.fetchCompanies();
      return;
    }

    this.loading = true;
    this.error = false;
    
    this.apiService.get<CompanyListItem[]>(`teamleader/companies/search?query=${encodeURIComponent(this.searchQuery)}`)
      .subscribe({
        next: (response) => {
          this.companies = response;
          this.totalCompanies = response.length;
          this.filteredCompanies = this.getPaginatedData(response);
          this.loading = false;
        },
        error: (err) => {
          this.error = true;
          this.loading = false;
        }
      });
  }

  authorizeTeamleader(): void {
    if (this.authUrl) {
      window.location.href = this.authUrl;
    }
  }

  applyFilters(): void {
    let filtered = this.companies;
    
    // Filter out admin domain companies
    if (this.adminDomain) {
      filtered = filtered.filter(company => {
        if (company.email) {
          const companyDomain = company.email.split('@')[1];
          return companyDomain !== this.adminDomain;
        }
        return true;
      });
    }
    
    // Apply search filter
    if (this.searchQuery && this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(company => 
        company.name.toLowerCase().includes(query) || 
        (company.email && company.email.toLowerCase().includes(query)) ||
        (company.vatNumber && company.vatNumber.toLowerCase().includes(query)) ||
        (company.phoneNumber && company.phoneNumber.toLowerCase().includes(query))
      );
    }
    
    // Apply status filter
    if (this.statusFilter && this.statusFilter !== '') {
      filtered = filtered.filter(company => company.status === this.statusFilter);
    }
    
    this.totalCompanies = filtered.length;
    this.filteredCompanies = this.getPaginatedData(filtered);
  }

  getPaginatedData(data: CompanyListItem[]): CompanyListItem[] {
    const startIndex = this.pageIndex * this.pageSize;
    return data.slice(startIndex, startIndex + this.pageSize);
  }

  onPageChange(event: PaginationEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchCompanies();
  }

  onSearch(): void {
    this.pageIndex = 0; // Reset to first page on new search
    this.searchCompanies();
  }
  
  getPaginationArray(): number[] {
    const totalPages = Math.ceil(this.totalCompanies / this.pageSize);
    const pages: number[] = [];
    const maxVisible = 5;
    
    let start = Math.max(1, this.pageIndex + 1 - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    
    return pages;
  }

  copyToClipboard(text: string | null, type: string = 'Text'): void {
    if (!text) {
      this.showToastMessage(`No ${type.toLowerCase()} to copy`);
      return;
    }

    navigator.clipboard.writeText(text).then(() => {
      this.showToastMessage(`${type} copied to clipboard`);
    }).catch(err => {
      this.showToastMessage(`Failed to copy ${type.toLowerCase()}`);
    });
  }

  showToastMessage(message: string): void {
    this.toastMessage = message;
    this.showToast = true;
    setTimeout(() => {
      this.showToast = false;
    }, 3000);
  }

  initializeAdminDomain(): void {
    this.authService.user$.subscribe({
      next: (user) => {
        if (user && user.email) {
          // Extract domain from admin's email
          const emailParts = user.email.split('@');
          if (emailParts.length === 2) {
            this.adminDomain = emailParts[1];
            // Re-apply filters now that we have the admin domain
            if (this.companies.length > 0) {
              this.applyFilters();
            }
          }
        }
      },
      error: (err) => {
        // Error getting admin user profile
      }
    });
  }
}
