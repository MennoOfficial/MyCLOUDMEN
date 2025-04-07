import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { CompanyListItem, CompanyDetail, CompanyListResponse, CompanyAddress, ContactInfo } from '../../../core/models/company.model';

interface PageEvent {
  pageIndex: number;
  pageSize: number;
  length: number;
}

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './companies.component.html',
  styleUrl: './companies.component.scss'
})
export class CompaniesComponent implements OnInit {
  companies: CompanyListItem[] = [];
  filteredCompanies: CompanyListItem[] = [];
  
  // Pagination
  pageSize = 5;
  pageSizeOptions = [5, 10, 25, 50];
  pageIndex = 0;
  totalCompanies = 0;
  
  // Filters
  searchQuery = '';
  statusFilter = 'All';

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

  constructor(
    private apiService: ApiService,
    private router: Router
  ) {
    this.checkScreenSize();
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

  fetchCompanies(): void {
    this.loading = true;
    this.error = false;
    this.authRequired = false;
    
    // Use the API service to fetch companies from the backend
    this.apiService.get<CompanyListResponse>(`teamleader/companies?page=${this.pageIndex}&size=${this.pageSize}&sortBy=name&direction=asc`)
      .subscribe({
        next: (response) => {
          this.companies = response.companies;
          this.totalCompanies = response.totalItems;
          this.applyFilters();
          this.loading = false;
        },
        error: (err) => {
          this.error = true;
          this.loading = false;
          
          // Check if the error is due to auth requirements
          if (err.error && err.error.authUrl) {
            this.authRequired = true;
            this.authUrl = err.error.authUrl;
          } else {
            console.error('Error fetching companies:', err);
          }
        }
      });
  }

  navigateToCompanyDetails(company: CompanyListItem): void {
    // Navigate to the company details page with the company ID
    this.router.navigate(['/system-admin/companies', company.teamleaderId]);
    this.showToastMessage(`Navigating to ${company.name} details`);
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
    if (this.statusFilter !== 'All') {
      filtered = filtered.filter(company => company.status === this.statusFilter);
    }
    
    this.totalCompanies = filtered.length;
    this.filteredCompanies = this.getPaginatedData(filtered);
  }

  getPaginatedData(data: CompanyListItem[]): CompanyListItem[] {
    const startIndex = this.pageIndex * this.pageSize;
    return data.slice(startIndex, startIndex + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchCompanies();
  }

  onSearch(): void {
    this.pageIndex = 0; // Reset to first page on new search
    this.searchCompanies();
  }

  onStatusFilterChange(): void {
    this.pageIndex = 0; // Reset to first page on filter change
    this.applyFilters();
  }

  
  getPaginationArray(): number[] {
    const totalPages = Math.ceil(this.totalCompanies / this.pageSize);
    
    // If 5 or fewer pages, show all
    if (totalPages <= 5) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }
    
    // Otherwise, show current page with 2 pages before and after when possible
    let startPage = Math.max(1, this.pageIndex - 1);
    let endPage = Math.min(totalPages, startPage + 4);
    
    // Adjust if we're near the end
    if (endPage === totalPages) {
      startPage = Math.max(1, endPage - 4);
    }
    
    return Array.from({ length: endPage - startPage + 1 }, (_, i) => startPage + i);
  }

  copyToClipboard(text: string | null, type: string = 'Text'): void {
    if (!text) return;
    
    // Create a temporary textarea element to copy from
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed'; // Prevent scrolling to bottom
    document.body.appendChild(textarea);
    textarea.select();
    
    try {
      // Execute copy command
      const successful = document.execCommand('copy');
      if (successful) {
        this.showToastMessage(`${type} copied to clipboard!`);
      }
    } catch (err) {
      console.error('Failed to copy text: ', err);
    }
    
    // Clean up
    document.body.removeChild(textarea);
  }

  showToastMessage(message: string): void {
    this.toastMessage = message;
    this.showToast = true;
    setTimeout(() => {
      this.showToast = false;
    }, 3000);
  }
}
