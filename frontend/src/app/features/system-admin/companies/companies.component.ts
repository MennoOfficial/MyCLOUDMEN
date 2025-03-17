import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';

interface Company {
  id: string;
  name: string;
  website: string;
  vatNumber?: string;
  status?: 'Active' | 'Inactive';
  primaryAddress?: {
    city?: string;
    country?: string;
  };
  contactInfo?: Array<{
    type: string;
    value: string;
  }>;
}

interface PageEvent {
  pageIndex: number;
  pageSize: number;
  length: number;
}

interface CompaniesResponse {
  companies: Company[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
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
  companies: Company[] = [];
  filteredCompanies: Company[] = [];
  
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

  // Detail view
  selectedCompany: Company | null = null;
  showDetailView = false;
  isMobile = false;

  // Copy to clipboard
  showCopyToast = false;
  copyToastMessage = '';

  // Make Math available in the template
  Math = Math;

  constructor(private apiService: ApiService) {
    this.checkScreenSize();
  }

  @HostListener('window:resize')
  onResize() {
    this.checkScreenSize();
  }

  checkScreenSize() {
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
    this.apiService.get<CompaniesResponse>(`teamleader/companies?page=${this.pageIndex}&size=${this.pageSize}&sortBy=name&direction=asc`)
      .subscribe({
        next: (response) => {
          // Set default status to 'Active' for companies without a status
          this.companies = response.companies.map(company => ({
            ...company,
            status: company.status || 'Active' as 'Active'
          }));
          this.totalCompanies = response.totalItems;
          this.applyFilters();
          this.loading = false;
          
          // Remove auto-selection of first company
          // if (!this.isMobile && !this.selectedCompany && this.filteredCompanies.length > 0) {
          //   this.viewCompanyDetails(this.filteredCompanies[0]);
          // }
        },
        error: (err) => {
          console.error('Error fetching companies:', err);
          this.error = true;
          this.loading = false;
          
          // Check if the error is due to auth requirements
          if (err.error && err.error.authUrl) {
            this.authRequired = true;
            this.authUrl = err.error.authUrl;
          } else {
            // For demo purposes, load mock data if API fails
            console.log('Error fetching companies:', err);
            this.loadMockData();
          }
        }
      });
  }

  searchCompanies(): void {
    if (!this.searchQuery.trim()) {
      this.fetchCompanies();
      return;
    }

    this.loading = true;
    this.error = false;
    
    this.apiService.get<Company[]>(`teamleader/companies/search?query=${encodeURIComponent(this.searchQuery)}`)
      .subscribe({
        next: (response) => {
          this.companies = response;
          this.totalCompanies = response.length;
          this.filteredCompanies = this.getPaginatedData(response);
          this.loading = false;
          
          // Auto-select first company if none selected and we're not on mobile
          if (!this.isMobile && !this.selectedCompany && this.filteredCompanies.length > 0) {
            this.viewCompanyDetails(this.filteredCompanies[0]);
          } else if (this.selectedCompany) {
            // Check if selected company is still in filtered results
            const stillExists = this.filteredCompanies.some(c => c.id === this.selectedCompany?.id);
            if (!stillExists) {
              this.selectedCompany = this.filteredCompanies.length > 0 ? this.filteredCompanies[0] : null;
              this.showDetailView = this.selectedCompany !== null;
            }
          }
        },
        error: (err) => {
          console.error('Error searching companies:', err);
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
        company.website.toLowerCase().includes(query) ||
        (company.vatNumber && company.vatNumber.toLowerCase().includes(query))
      );
    }
    
    // Apply status filter
    if (this.statusFilter !== 'All') {
      filtered = filtered.filter(company => company.status === this.statusFilter);
    }
    
    this.totalCompanies = filtered.length;
    this.filteredCompanies = this.getPaginatedData(filtered);
    
    // Update selected company if it no longer exists in filtered results
    if (this.selectedCompany) {
      const stillExists = this.filteredCompanies.some(c => c.id === this.selectedCompany?.id);
      if (!stillExists) {
        this.selectedCompany = this.filteredCompanies.length > 0 ? this.filteredCompanies[0] : null;
        this.showDetailView = this.selectedCompany !== null;
      }
    }
  }

  getPaginatedData(data: Company[]): Company[] {
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

  getEmailAddress(company: Company): string | null {
    const emailContact = company.contactInfo?.find(contact => contact.type.includes('email'));
    return emailContact ? emailContact.value : null;
  }

  getPhoneNumber(company: Company): string | null {
    const phoneContact = company.contactInfo?.find(contact => contact.type.includes('phone'));
    return phoneContact ? phoneContact.value : null;
  }

  viewCompanyDetails(company: Company): void {
    this.selectedCompany = company;
    this.showDetailView = true;
  }

  closeDetailView(): void {
    this.showDetailView = false;
    this.selectedCompany = null;
  }

  loadMockData(): void {
    // Mock data for development/demo
    const mockCompanies = [
      {
        id: '1',
        name: 'TechCorp Solutions',
        website: 'techcorp.com',
        vatNumber: 'BE0987654321',
        status: 'Active' as 'Active',
        primaryAddress: {
          city: 'Brussels',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'info@techcorp.com' },
          { type: 'phone-primary', value: '+32 9 876 5432' }
        ]
      },
      {
        id: '2',
        name: 'DataFlow Analytics',
        website: 'dataflow.io',
        vatNumber: 'BE0123456789',
        status: 'Active' as 'Active',
        primaryAddress: {
          city: 'Antwerp',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'contact@dataflow.io' },
          { type: 'phone-primary', value: '+32 1 234 5678' }
        ]
      },
      {
        id: '3',
        name: 'HealthTech Innovations',
        website: 'healthtech.com',
        vatNumber: 'BE0567891234',
        status: 'Inactive' as 'Inactive',
        primaryAddress: {
          city: 'Ghent',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'info@healthtech.com' },
          { type: 'phone-primary', value: '+32 5 678 9123' }
        ]
      },
      {
        id: '4',
        name: 'EduSmart Systems',
        website: 'edusmart.edu',
        vatNumber: 'BE0345678912',
        status: 'Active' as 'Active',
        primaryAddress: {
          city: 'Leuven',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'contact@edusmart.edu' },
          { type: 'phone-primary', value: '+32 3 456 7891' }
        ]
      },
      {
        id: '5',
        name: 'GreenEnergy Solutions',
        website: 'greenenergy.be',
        vatNumber: 'BE0234567891',
        status: 'Active' as 'Active',
        primaryAddress: {
          city: 'Namur',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'info@greenenergy.be' },
          { type: 'phone-primary', value: '+32 2 345 6789' }
        ]
      },
      {
        id: '6',
        name: 'CloudNet Services',
        website: 'cloudnet.io',
        vatNumber: 'BE0456789123',
        status: 'Inactive' as 'Inactive',
        primaryAddress: {
          city: 'Charleroi',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'support@cloudnet.io' },
          { type: 'phone-primary', value: '+32 4 567 8912' }
        ]
      },
      {
        id: '7',
        name: 'BioMed Research',
        website: 'biomed-research.org',
        vatNumber: 'BE0678912345',
        status: 'Active' as 'Active',
        primaryAddress: {
          city: 'Liège',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'contact@biomed-research.org' },
          { type: 'phone-primary', value: '+32 6 789 1234' }
        ]
      },
      {
        id: '8',
        name: 'Innovate Digital',
        website: 'innovate-digital.com',
        vatNumber: 'BE0891234567',
        primaryAddress: {
          city: 'Bruges',
          country: 'Belgium'
        },
        contactInfo: [
          { type: 'email-primary', value: 'hello@innovate-digital.com' },
          { type: 'phone-primary', value: '+32 8 912 3456' }
        ]
      }
    ];
    
    // Set default status to 'Active' for companies without a status
    this.companies = mockCompanies.map(company => ({
      ...company,
      status: (company.status || 'Active') as 'Active' | 'Inactive'
    }));
    
    this.totalCompanies = this.companies.length;
    this.applyFilters();
    this.loading = false;
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
        this.showCopyToastMessage(`${type} copied to clipboard!`);
      }
    } catch (err) {
      console.error('Failed to copy text: ', err);
    }
    
    // Clean up
    document.body.removeChild(textarea);
  }

  showCopyToastMessage(message: string): void {
    this.copyToastMessage = message;
    this.showCopyToast = true;
    setTimeout(() => {
      this.showCopyToast = false;
    }, 3000);
  }
}
