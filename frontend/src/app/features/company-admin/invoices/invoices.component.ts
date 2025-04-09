import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { finalize } from 'rxjs/operators';
import { HttpParams } from '@angular/common/http';
import { catchError, of, Subscription } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { RouterModule } from '@angular/router';
import { UiStateService } from '../../../core/services/ui-state.service';

interface Invoice {
  id: string;
  invoiceNumber: string;
  date: string | Date;
  dueDate: string | Date;
  totalAmount: number;
  status: string;
  paymentTerm: string;
  isPaid: boolean;
  isOverdue: boolean;
  type: string;
  paymentDate?: string | Date;
  customer?: string;
  creditNoteCount?: number; // Count of related credit notes
}

interface CreditNote {
  id: string;
  creditNoteNumber: string;
  date: string | Date;
  totalAmount: number;
  status: string;
  relatedInvoiceId: string;
  type: string;
}

// Response format from the API
interface FinanceResponse {
  invoices: {
    id: string;
    dueOn: string;
    status: string;
    total: number;
    isPaid: boolean;
    isOverdue: boolean;
  }[];
  creditNotesByInvoice: {
    [invoiceId: string]: {
      id: string;
      status: string;
      total: number;
    }[];
  };
}

// Company with both MongoDB ID and Teamleader ID
interface Company {
  id: string;        // MongoDB ID
  teamleaderId?: string;  // Teamleader ID with dashes
  name: string;
  email?: string;
}

// Add InvoiceDetails interface that properly extends Invoice
interface InvoiceDetails {
  id: string;
  invoiceNumber: string;
  totalAmount: number;
  status: string;
  paymentTerm: string;
  isPaid: boolean;
  isOverdue: boolean;
  type: string;
  customer?: string;
  
  // Extended properties
  company?: { name: string, email: string };
  companyName?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  invoiceDate?: Date;
  issueDate?: Date;
  dueDate?: Date;
  paymentDate?: Date;
  amount?: {
    total: number;
    tax: number;
    subtotal: number;
  };
  description?: string;
  creditNotes?: Array<{
    id: string;
    number: string;
    creditNoteNumber?: string;
    amount: number;
    date: Date;
    downloadUrl: string;
  }>;
}

@Component({
  selector: 'app-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './invoices.component.html',
  styleUrl: './invoices.component.scss'
})
export class InvoicesComponent implements OnInit, OnDestroy {
  activeTab: 'outstanding' | 'paid' = 'outstanding';
  paidInvoices: Invoice[] = [];
  outstandingInvoices: Invoice[] = [];
  paidCreditNotes: CreditNote[] = [];
  outstandingCreditNotes: CreditNote[] = [];
  
  // Count properties for tab badges
  outstandingCount: number = 0;
  paidCount: number = 0;
  
  // Filter dropdown states
  statusFilterOpen: boolean = false;
  dateFilterOpen: boolean = false;
  amountFilterOpen: boolean = false;
  
  // Filter models
  statusFilter: string = 'all';
  dateFilter: any = null;
  amountFilter: any = null;
  
  // Collapsible section states
  outstandingInvoicesCollapsed: boolean = false;
  paidInvoicesCollapsed: boolean = true;
  outstandingCreditNotesCollapsed: boolean = false;
  paidCreditNotesCollapsed: boolean = true;
  
  // Search and filter state
  searchText = '';
  dateRangeStart: string = '';
  dateRangeEnd: string = '';
  selectedStatus: string = '';
  loading = false;
  selectedItem: Invoice | CreditNote | null = null;
  
  // Error state
  hasError: boolean = false;
  errorMessage: string = '';
  
  // For test data fallback
  testDataGenerated: boolean = false;
  
  // For internal data management - needed for both API and test data
  private allInvoices: Invoice[] = [];
  private allCreditNotes: CreditNote[] = [];
  filteredInvoices: Invoice[] = [];
  private filteredCreditNotes: CreditNote[] = [];
  
  // For tracking the current user's company
  private currentUserEmail: string = '';
  private currentUserDomain: string = '';
  private companyId: string = '';      // MongoDB ID
  private teamleaderId: string = '';   // Teamleader ID with dashes
  
  // Add loading and error state properties
  searchQuery = '';
  selectedInvoice: InvoiceDetails | null = null;
  isDetailViewVisible: boolean = false;
  
  // Add sorting properties
  sortColumn: string = 'date';
  sortDirection: string = 'desc';
  
  // Add these properties for the new filter functionality
  dateRangeFilter: string = 'all';
  amountRangeFilter: string = 'all';
  
  // Custom filter properties
  customDateFrom: Date | null = null;
  customDateTo: Date | null = null;
  customAmountMin: number | null = null;
  customAmountMax: number | null = null;

  tableView = true; // For toggling between table and card view

  // Display properties
  selectedTab: 'outstanding' | 'paid' = 'outstanding';
  isDetailOpen = false;
  
  // Filter properties
  searchTerm = '';
  showStatusFilter = false;
  showDateFilter = false;
  showAmountFilter = false;

  // Add subscription tracking
  private subscriptions: Subscription[] = [];
  
  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private uiStateService: UiStateService
  ) {}

  ngOnInit(): void {
    // Get the current user's email and domain
    const userSub = this.authService.user$.subscribe(user => {
      if (user && user.email) {
        this.currentUserEmail = user.email;
        const emailParts = user.email.split('@');
        if (emailParts.length === 2) {
          this.currentUserDomain = emailParts[1];
          this.getCompanyIdByDomain();
        } else {
          console.error('Invalid email format in user profile');
        }
      }
    });
    this.subscriptions.push(userSub);
    
    this.loading = true;
    
    // Initialize credit notes array
    this.allCreditNotes = [];
    
    this.initializeFilterModels();
    this.loadInvoices();
  }
  
  // Get company ID by the user's domain
  private getCompanyIdByDomain(): void {
    // Use the correct endpoint to get all companies
    this.apiService.get<any>(`teamleader/companies`)
      .pipe(
        catchError(error => {
          console.error('Error fetching companies:', error);
          // Fallback to test data if we can't get the companies
          this.generateTestData();
          return of(null);
        })
      )
      .subscribe(response => {
        if (response && response.companies && response.companies.length > 0) {
          console.log('Got companies:', response.companies);
          
          // Filter companies by email domain
          const userDomain = this.currentUserDomain.toLowerCase();
          const matchingCompany = response.companies.find((company: any) => {
            if (company.email) {
              const companyDomain = company.email.split('@')[1]?.toLowerCase();
              return companyDomain === userDomain;
            }
            return false;
          });

          // If no exact domain match, try matching by email directly
          if (!matchingCompany && this.currentUserEmail) {
            const userEmail = this.currentUserEmail.toLowerCase();
            const matchByEmail = response.companies.find((company: any) => 
              company.email && company.email.toLowerCase() === userEmail
            );
            
            if (matchByEmail) {
              this.companyId = matchByEmail.id;
              // Check if there's a teamleader_id property in the response
              this.teamleaderId = matchByEmail.teamleader_id || matchByEmail.teamleaderId || '';
              console.log('Found company by email match:', matchByEmail);
              console.log('Using MongoDB ID:', this.companyId, 'Teamleader ID:', this.teamleaderId);
              this.loadInvoices(); // Only load invoices, which will include credit notes
              return;
            }
          }

          if (matchingCompany) {
            this.companyId = matchingCompany.id;
            // Check if there's a teamleader_id property in the response
            this.teamleaderId = matchingCompany.teamleader_id || matchingCompany.teamleaderId || '';
            console.log('Found company by domain match:', matchingCompany);
            console.log('Using MongoDB ID:', this.companyId, 'Teamleader ID:', this.teamleaderId);
            this.loadInvoices(); // Only load invoices, which will include credit notes
          } else {
            console.error('No company found for domain:', this.currentUserDomain);
            
            // TEMPORARY: For testing, use the first company
            if (response.companies.length > 0) {
              this.companyId = response.companies[0].id;
              // Check if there's a teamleader_id property in the response
              this.teamleaderId = response.companies[0].teamleader_id || response.companies[0].teamleaderId || '';
              console.log('Using first company as fallback:', response.companies[0]);
              console.log('Using MongoDB ID:', this.companyId, 'Teamleader ID:', this.teamleaderId);
              this.loadInvoices(); // Only load invoices, which will include credit notes
            } else {
              // Fallback to test data if no company is found
              this.generateTestData();
            }
          }
        } else {
          console.error('No companies found in the response');
          // Fallback to test data if no companies
          this.generateTestData();
        }
      });
  }

  loadInvoices(): void {
    this.loading = true;
    this.errorMessage = '';
    
    // Reset credit notes array
    this.allCreditNotes = [];
    
    // Use the API ID that's most likely to work
    const apiCompanyId = this.getApiCompanyId();
    if (!apiCompanyId) {
      console.error('No company ID available for loading invoices');
      this.generateTestData();
      return;
    }
    
    console.log('Loading invoices and credit notes with company ID:', apiCompanyId);
    this.apiService.get<any>(`teamleader/finance/company/${apiCompanyId}/invoices`)
      .pipe(
        catchError(error => {
          console.error('Error loading invoices:', error);
          
          // Try the alternative ID format if available
          if ((error.status === 400 || error.status === 404) && this.canTryAlternativeId()) {
            console.log('Trying alternative ID format for invoices...');
            return this.tryAlternativeIdForInvoices();
          }
          
          // If we get a 404, the API doesn't exist yet, so generate test data
          if (error.status === 404) {
            this.generateTestData();
          } else {
            this.errorMessage = 'Failed to load invoices. Please try again later.';
          }
          
          this.loading = false;
          return of(null);
        })
      )
      .subscribe(data => {
        if (data) {
          // Debug: Log the raw response data
          console.log('Raw finance data from API:', JSON.stringify(data, null, 2));
          
          // Check if we have the expected structure with both invoices and creditNotesByInvoice
          if (Array.isArray(data) && data.length > 0 && 'creditNoteCount' in data[0]) {
            console.log('Found invoices with credit note counts');
            
            // Process invoices with credit note counts
          this.allInvoices = this.processInvoicesData(data);
          
          // Split invoices by status
          this.splitInvoicesByStatus();
            
            // For invoices that have credit notes, we'll load their details when showing invoice details
            console.log('Invoices loaded with credit note counts. Will load actual credit notes when needed.');
            
          } else if (data.invoices && data.creditNotesByInvoice) {
            console.log('Found combined invoices and credit notes response structure');
            
            // Process both invoices and credit notes from the same response
            this.allInvoices = this.processInvoicesData(data.invoices);
            this.allCreditNotes = this.processCreditNotesFromInvoiceResponse(data.creditNotesByInvoice);
          
            // Split invoices by status
            this.splitInvoicesByStatus();
            // Split credit notes by status
            this.splitCreditNotesByStatus();
          } else if (Array.isArray(data)) {
            console.log('Found array-only response structure, assuming these are invoices');
            // Fallback to old format if needed
            this.allInvoices = this.processInvoicesData(data);
            // Split invoices by status
            this.splitInvoicesByStatus();
            
            // Generate test credit notes for demo purposes if API doesn't provide them
            this.generateTestCreditNotes();
          } else {
            console.error('Unexpected data format from API:', typeof data);
            this.generateTestData();
          }
          
          this.loading = false;
          
          // Log summary of loaded data
          console.log(`Loaded ${this.allInvoices.length} invoices and ${this.allCreditNotes.length} credit notes`);
          console.log(`Outstanding invoices: ${this.outstandingInvoices.length}, Paid invoices: ${this.paidInvoices.length}`);
          
          // Report how many invoices have credit notes
          let invoicesWithCreditNotes = 0;
          this.allInvoices.forEach(invoice => {
            // Use the creditNoteCount if available from the API response
            if (invoice.creditNoteCount && invoice.creditNoteCount > 0) {
              invoicesWithCreditNotes++;
            } else {
              // Fall back to checking allCreditNotes if we loaded them
              const creditNotesCount = this.getRelatedCreditNotes(invoice.id).length;
              if (creditNotesCount > 0) {
                invoicesWithCreditNotes++;
              }
            }
          });
          console.log(`${invoicesWithCreditNotes} out of ${this.allInvoices.length} invoices have credit notes`);
        }
      });
  }

  // Helper to try the alternative ID format for invoices
  private tryAlternativeIdForInvoices() {
    const altId = this.getAlternativeApiCompanyId();
    if (!altId) return of(null);
    
    console.log('Trying alternative ID:', altId);
    return this.apiService.get<FinanceResponse>(`teamleader/finance/company/${altId}/invoices`)
      .pipe(
        catchError(error => {
          console.error('Error with alternative ID for invoices:', error);
        this.generateTestData();
          this.loading = false;
          return of(null);
        })
      );
  }

  // Process credit notes from the invoice response's creditNotesByInvoice property
  private processCreditNotesFromInvoiceResponse(creditNotesByInvoice: { [invoiceId: string]: any[] }): CreditNote[] {
    if (!creditNotesByInvoice || typeof creditNotesByInvoice !== 'object') {
      console.error('Invalid credit notes data format:', creditNotesByInvoice);
      return [];
    }

    const allCreditNotes: CreditNote[] = [];
    
    // Iterate through each invoice's credit notes
    Object.entries(creditNotesByInvoice).forEach(([invoiceId, creditNotes]) => {
      if (Array.isArray(creditNotes)) {
        creditNotes.forEach(creditNote => {
          // Debug: Log the structure of each credit note
          console.log(`Processing credit note ID: ${creditNote.id || 'unknown'} for invoice: ${invoiceId}`);
          
          // Extract the amount from the credit note
          let amount = 0;
          try {
            if (creditNote.total !== undefined) {
              // If total is a number
              if (typeof creditNote.total === 'number') {
                amount = creditNote.total;
              } 
              // Otherwise try to parse it as is
              else {
                amount = parseFloat(String(creditNote.total));
              }
            }
            console.log('Final extracted amount:', amount, 'from credit note:', creditNote.id);
          } catch (error) {
            console.error('Error parsing credit note amount:', error);
          }
          
          // Generate credit note number if not available
          const creditNoteNumber = creditNote.number || (creditNote.id ? `${creditNote.id}`.split('-')[0] : '') || '';
          
          allCreditNotes.push({
            id: creditNote.id || '',
            creditNoteNumber: creditNoteNumber,
            date: creditNote.date ? new Date(creditNote.date) : new Date(),
            totalAmount: amount,
            status: this.mapCreditNoteStatusToComponentStatus(creditNote.status),
            relatedInvoiceId: invoiceId,
            type: 'creditNote'
          });
        });
      }
    });
    
    console.log(`Processed ${allCreditNotes.length} credit notes from invoice response`);
    return allCreditNotes;
  }

  // Generate test credit notes based on existing invoices
  private generateTestCreditNotes(): void {
    // Only create test credit notes if we have invoices but no credit notes
    if (this.allInvoices.length > 0 && this.allCreditNotes.length === 0) {
      console.log('Generating test credit notes');
      
      // In a real-world scenario, not every invoice would have credit notes
      // Only create credit notes for about 30% of invoices
      const invoicesWithCreditNotes = this.allInvoices
        .filter((_, index) => index % 3 === 0) // Take every 3rd invoice
        .slice(0, Math.max(1, Math.floor(this.allInvoices.length * 0.3))); // Ensure we have at least one but not more than 30%
      
      console.log(`Creating credit notes for ${invoicesWithCreditNotes.length} out of ${this.allInvoices.length} invoices`);
      
      // Create 1-2 credit notes for the selected invoices
      invoicesWithCreditNotes.forEach((invoice, index) => {
        // First two invoices get 2 credit notes, others get 1 (for variety)
        const numNotesForThisInvoice = index < 2 ? 2 : 1;
        
        for (let i = 0; i < numNotesForThisInvoice; i++) {
          // Create a credit note for a portion of the invoice amount
          const creditNoteAmount = invoice.totalAmount * (0.15 + (i * 0.10)); // Different amounts for variety
          
          this.allCreditNotes.push({
            id: `-${invoice.id}-${i+1}`,
            creditNoteNumber: `${invoice.invoiceNumber}-${i+1}`,
            date: new Date(), // Current date
            totalAmount: creditNoteAmount,
            status: invoice.isPaid ? 'paid' : 'outstanding',
            relatedInvoiceId: invoice.id,
            type: 'creditNote'
          });
        }
      });
          
          // Split credit notes by status
          this.splitCreditNotesByStatus();
      console.log(`Generated ${this.allCreditNotes.length} test credit notes for ${invoicesWithCreditNotes.length} invoices`);
    }
  }

  // Helper to get the company ID to use for API calls
  private getApiCompanyId(): string {
    // Prefer the Teamleader ID with dashes if available
    if (this.teamleaderId && this.teamleaderId.includes('-')) {
      return this.teamleaderId;
    }
    return this.companyId;
  }

  // Helper to get the alternative company ID for API calls
  private getAlternativeApiCompanyId(): string {
    // If we're currently using the Teamleader ID, return the MongoDB ID
    if (this.getApiCompanyId() === this.teamleaderId) {
      return this.companyId;
    }
    // Otherwise return the Teamleader ID if it exists
    return this.teamleaderId;
  }

  // Helper to check if we can try an alternative ID
  private canTryAlternativeId(): boolean {
    // We can try the alternative ID if both IDs exist and are different
    return !!(this.companyId && this.teamleaderId && this.companyId !== this.teamleaderId);
  }

  // Helper to process invoices from API response
  private processInvoicesData(invoicesData: any[]): Invoice[] {
    if (!Array.isArray(invoicesData)) {
      console.error('Invalid invoices data format:', invoicesData);
      return [];
    }

    return invoicesData.map(invoice => {
      // Debug: Log the structure of each invoice
      console.log(`Processing invoice ID: ${invoice.id || 'unknown'}`);
      console.log('Invoice total property:', invoice.total);
      
      if (invoice.total) {
        console.log('Invoice total type:', typeof invoice.total);
        if (typeof invoice.total === 'object') {
          console.log('Invoice total keys:', Object.keys(invoice.total));
          if (invoice.total.tax_exclusive) {
            console.log('tax_exclusive:', invoice.total.tax_exclusive);
          }
          if (invoice.total.tax_inclusive) {
            console.log('tax_inclusive:', invoice.total.tax_inclusive);
          }
          if (invoice.total.payable) {
            console.log('payable:', invoice.total.payable);
          }
        }
      }
      
      // Extract the amount from the nested structure
      let amount = 0;
      try {
        // Based on Teamleader API structure, first check the specific paths
        if (invoice.total) {
          // Try tax_exclusive first as it's the typical business amount
          if (invoice.total.tax_exclusive && invoice.total.tax_exclusive.amount) {
            amount = parseFloat(invoice.total.tax_exclusive.amount);
            console.log('Found amount in total.tax_exclusive.amount:', amount);
          } 
          // Then try payable amount which is what's actually due
          else if (invoice.total.payable && invoice.total.payable.amount) {
            amount = parseFloat(invoice.total.payable.amount);
            console.log('Found amount in total.payable.amount:', amount);
          }
          // Then try tax_inclusive amount
          else if (invoice.total.tax_inclusive && invoice.total.tax_inclusive.amount) {
            amount = parseFloat(invoice.total.tax_inclusive.amount);
            console.log('Found amount in total.tax_inclusive.amount:', amount);
          }
          // If due amount is available, use that
          else if (invoice.total.due && invoice.total.due.amount) {
            amount = parseFloat(invoice.total.due.amount);
            console.log('Found amount in total.due.amount:', amount);
          }
          // If total is a direct number
          else if (typeof invoice.total === 'number') {
            amount = invoice.total;
            console.log('Found amount as direct number in total:', amount);
          }
          // Check if total has a direct amount property
          else if (typeof invoice.total === 'object' && invoice.total.amount) {
            amount = parseFloat(invoice.total.amount);
            console.log('Found amount in total.amount:', amount);
          }
        } 
        // Fallback to other possible formats
        else if (invoice.total_amount) {
          amount = parseFloat(invoice.total_amount);
          console.log('Found amount in total_amount:', amount);
        } else if (invoice.amount) {
          amount = parseFloat(invoice.amount);
          console.log('Found amount in amount:', amount);
        } else {
          // Last resort: log all properties at top level
          console.log('No amount found. All invoice properties:', Object.keys(invoice));
        }
        
        // Debug the amount extraction
        console.log('Final extracted amount:', amount, 'from invoice:', invoice.id);
      } catch (error) {
        console.error('Error parsing invoice amount from invoice', invoice.id, ':', error);
      }

      // Determine isPaid and isOverdue based on status and dates
      const isPaid = invoice.isPaid || invoice.paid || (invoice.status === 'paid' || invoice.status === 'matched');
      const dueDate = invoice.dueDate ? new Date(invoice.dueDate) : null;
      const isOverdue = !isPaid && dueDate && dueDate < new Date();

      // Use actual invoice number or appropriate fallback, but avoid using "INV-" prefix
      const invoiceNumber = invoice.invoiceNumber || invoice.number || invoice.id || '';

      return {
        id: invoice.id,
        invoiceNumber: invoiceNumber,
        date: invoice.date ? new Date(invoice.date) : new Date(),
        dueDate: dueDate || new Date(new Date().setDate(new Date().getDate() + 30)),
        totalAmount: amount,
        status: this.mapApiStatusToComponentStatus(invoice.status, isPaid, isOverdue || false),
        paymentTerm: invoice.paymentTerm || '30 days',
        isPaid: isPaid,
        isOverdue: isOverdue || false,
        type: 'invoice',
        customer: invoice.customer,
        creditNoteCount: invoice.creditNoteCount || 0
      };
    });
  }

  // Helper methods for status mapping
  private mapApiStatusToComponentStatus(apiStatus: string, isPaid: boolean, isOverdue: boolean): string {
    if (isPaid) {
      return 'paid';
    }
    if (isOverdue) {
      return 'overdue';
    }
    if (apiStatus === 'matched') {
      return 'paid';
    }
    if (apiStatus === 'draft') {
      return 'draft';
    }
    return 'outstanding';
  }

  private mapCreditNoteStatusToComponentStatus(apiStatus: string): string {
    if (apiStatus === 'booked' || apiStatus === 'matched') {
      return 'paid';
    }
    if (apiStatus === 'draft') {
      return 'draft';
    }
    return 'outstanding';
  }

  // Replace setActiveTab with switchTab to match HTML
  switchTab(tab: 'outstanding' | 'paid'): void {
    this.activeTab = tab;
    this.selectedItem = null;
    // Reset search and filter states
    this.searchText = '';
    this.statusFilter = 'all';
    this.dateRangeFilter = 'all';
    this.amountRangeFilter = 'all';
    // Update filtered invoices based on the newly selected tab
    this.filteredInvoices = [...this.getActiveInvoices()];
    // Apply filters without calling resetFilters to avoid trigger
    this.applyFilters();
  }

  // Add an alias for backward compatibility
  setActiveTab(tab: 'outstanding' | 'paid'): void {
    this.switchTab(tab);
  }

  // Update the clearSearch method to only clear the search term
  clearSearch(): void {
    this.searchText = '';
    this.applyFilters();
  }

  // Add a clearFilters method to reset all filters
  clearFilters(): void {
    this.searchText = '';
    this.statusFilter = 'all';
    this.dateRangeFilter = 'all';
    this.amountRangeFilter = 'all';
    this.dateFilter = null;
    this.amountFilter = null;
    this.applyFilters();
  }

  // Sort method for invoice lists
  sort(column: string): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
        } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.applyFiltersAndSort();
  }

  // Update the applyFilters method to handle the new filter options
  applyFilters(): void {
    // Close any open filter dropdowns
    this.statusFilterOpen = false;
    this.dateFilterOpen = false;
    this.amountFilterOpen = false;
    
    // Get invoices based on the active tab
    let filteredList = this.getActiveInvoices();
    
    // Apply search term filter
    if (this.searchText) {
      const term = this.searchText.toLowerCase();
      filteredList = filteredList.filter(invoice => 
        invoice.invoiceNumber.toLowerCase().includes(term) ||
        (invoice.totalAmount.toString().includes(term))
      );
    }
    
    // Apply status filter
    if (this.statusFilter !== 'all') {
      filteredList = filteredList.filter(invoice => {
        if (this.statusFilter === 'paid') return invoice.isPaid;
        if (this.statusFilter === 'outstanding') return !invoice.isPaid && !invoice.isOverdue;
        if (this.statusFilter === 'overdue') return invoice.isOverdue;
        return true;
      });
    }
    
    // Apply date range filter
    if (this.dateRangeFilter !== 'all') {
      const today = new Date();
      let fromDate: Date | null = null;
      
      if (this.dateRangeFilter === 'last7') {
        fromDate = new Date(today);
        fromDate.setDate(today.getDate() - 7);
      } else if (this.dateRangeFilter === 'last30') {
        fromDate = new Date(today);
        fromDate.setDate(today.getDate() - 30);
      } else if (this.dateRangeFilter === 'last90') {
        fromDate = new Date(today);
        fromDate.setDate(today.getDate() - 90);
      } else if (this.dateRangeFilter === 'custom' && this.dateFilter?.from) {
        fromDate = new Date(this.dateFilter.from);
      }
      
      if (fromDate) {
        filteredList = filteredList.filter(invoice => {
          const invoiceDate = new Date(invoice.date);
          return invoiceDate >= fromDate!;
        });
      }
      
      // If we have an end date in custom range
      if (this.dateRangeFilter === 'custom' && this.dateFilter?.to) {
        const toDate = new Date(this.dateFilter.to);
        // Set to end of day
        toDate.setHours(23, 59, 59, 999);
        
        filteredList = filteredList.filter(invoice => {
          const invoiceDate = new Date(invoice.date);
          return invoiceDate <= toDate;
        });
      }
    }
    
    // Apply amount range filter
    if (this.amountRangeFilter !== 'all') {
      let minAmount: number | null = null;
      let maxAmount: number | null = null;
      
      if (this.amountRangeFilter === 'under500') {
        maxAmount = 500;
      } else if (this.amountRangeFilter === '500to1000') {
        minAmount = 500;
        maxAmount = 1000;
      } else if (this.amountRangeFilter === '1000to5000') {
        minAmount = 1000;
        maxAmount = 5000;
      } else if (this.amountRangeFilter === 'over5000') {
        minAmount = 5000;
      } else if (this.amountRangeFilter === 'custom') {
        minAmount = this.amountFilter?.min;
        maxAmount = this.amountFilter?.max;
      }
      
      if (minAmount !== null) {
        filteredList = filteredList.filter(invoice => invoice.totalAmount >= minAmount!);
      }
      
      if (maxAmount !== null) {
        filteredList = filteredList.filter(invoice => invoice.totalAmount <= maxAmount!);
      }
    }
    
    this.filteredInvoices = filteredList;
    
    // If there are no results for a filter, let's keep the UI responsive
    if (this.filteredInvoices.length === 0) {
      console.log('No invoices match the current filters');
    }
    
    // Apply sorting
    this.sortInvoices();
  }

  // Add an alias for applyFiltersAndSort to match existing calls
  applyFiltersAndSort(): void {
    this.applyFilters();
  }

  // Reset filter methods
  resetStatusFilter(): void {
    this.statusFilter = 'all';
  }

  resetDateFilter(): void {
    this.dateFilter = null;
  }
   
  resetAmountFilter(): void {
    this.amountFilter = null;
  }

  setStatusFilter(status: string): void {
    this.statusFilter = status;
  }

  // Show invoice details using cleaner, more maintainable approach
  showInvoiceDetails(invoice: Invoice): void {
    if (!invoice || !invoice.id) {
      console.error('Attempting to show details for invalid invoice');
      return;
    }
    
    console.log('Showing details for invoice:', invoice);
    console.log(`Invoice credit note count from API: ${invoice.creditNoteCount}`);
    console.log(`Current credit notes for this invoice: ${this.getRelatedCreditNotes(invoice.id).length}`);
    
    // Check if we should load credit notes from API or use existing ones
    if (invoice.creditNoteCount && invoice.creditNoteCount > 0 && this.getRelatedCreditNotes(invoice.id).length === 0) {
      // We know there are credit notes but haven't loaded them yet, so fetch them from API
      console.log(`Invoice has ${invoice.creditNoteCount} credit notes according to API, loading them now...`);
      
      const apiCompanyId = this.getApiCompanyId();
      console.log(`Making API call to load credit notes: teamleader/finance/company/${apiCompanyId}/invoices/${invoice.id}/credit-notes`);
      
      this.apiService.get<any>(`teamleader/finance/company/${apiCompanyId}/invoices/${invoice.id}/credit-notes`)
        .pipe(
          catchError(error => {
            console.error('Error loading credit notes for invoice:', error);
            // Fallback to test credit notes if API fails
            if (this.allCreditNotes.length === 0) {
              this.generateTestCreditNotes();
            }
            return of([]);
          })
        )
        .subscribe(creditNotesData => {
          console.log('Raw credit notes API response:', JSON.stringify(creditNotesData, null, 2));
          
          // Inspect the raw API structure for troubleshooting
          if (creditNotesData) {
            if (Array.isArray(creditNotesData)) {
              console.log(`API returned an array of ${creditNotesData.length} items`);
              if (creditNotesData.length > 0) {
                console.log('First credit note structure:', Object.keys(creditNotesData[0]));
                // Check for for_invoice field specifically
                if (creditNotesData[0].for_invoice) {
                  console.log('for_invoice field:', creditNotesData[0].for_invoice);
                }
              }
        } else {
              console.log('API returned a non-array object with keys:', Object.keys(creditNotesData));
            }
          } else {
            console.log('API returned null or undefined creditNotesData');
          }
          
          if (creditNotesData && Array.isArray(creditNotesData) && creditNotesData.length > 0) {
            console.log(`Loaded ${creditNotesData.length} credit notes for invoice ${invoice.id} from API`);
            
            // Process and add to allCreditNotes
            const newCreditNotes = creditNotesData.map(cn => {
              console.log(`Processing credit note from API: id=${cn.id}, number=${cn.number || cn.credit_note_number}, invoiceId=${cn.invoiceId || cn.invoice_id}`);
              
              // The API response may use 'invoiceId' or store it in 'for_invoice.id'
              // We need to ensure we capture it correctly
              let creditNoteInvoiceId = invoice.id; // Default to current invoice
              
              // Try different possible locations for the invoice ID in the API response
              if (cn.invoiceId) {
                creditNoteInvoiceId = cn.invoiceId;
              } else if (cn.invoice_id) {
                creditNoteInvoiceId = cn.invoice_id;
              } else if (cn.for_invoice && cn.for_invoice.id) {
                creditNoteInvoiceId = cn.for_invoice.id;
              } else if (cn.invoice && cn.invoice.id) {
                creditNoteInvoiceId = cn.invoice.id;
              }
              
              console.log(`Using invoiceId=${creditNoteInvoiceId} for credit note ${cn.id}`);
              
              // Get the credit note number from various possible fields
              const creditNoteNumber = cn.number || cn.credit_note_number || '';
              
              // Calculate total amount from various possible formats in the API
              let totalAmount = 0;
              if (cn.total) {
                if (typeof cn.total === 'number') {
                  totalAmount = cn.total;
                } else if (typeof cn.total === 'object') {
                  // Try different paths in the total object based on API structure
                  if (cn.total.amount) {
                    totalAmount = parseFloat(String(cn.total.amount));
                  } else if (cn.total.tax_exclusive && cn.total.tax_exclusive.amount) {
                    totalAmount = parseFloat(String(cn.total.tax_exclusive.amount));
                  } else if (cn.total.payable && cn.total.payable.amount) {
                    totalAmount = parseFloat(String(cn.total.payable.amount));
                  }
                } else {
                  totalAmount = parseFloat(String(cn.total));
                }
              }
              
              // Get the date from various possible fields
              const dateStr = cn.date || cn.credit_note_date || null;
              const creditNoteDate = dateStr ? new Date(dateStr) : new Date();

      return {
                id: cn.id || '',
                creditNoteNumber: creditNoteNumber,
                date: creditNoteDate,
                totalAmount: totalAmount,
                status: this.mapCreditNoteStatusToComponentStatus(cn.status),
                relatedInvoiceId: creditNoteInvoiceId,
        type: 'creditNote'
      };
    });
            
            console.log('Processed credit notes:', newCreditNotes);
            
            // Add to our collection
            this.allCreditNotes.push(...newCreditNotes);
            
            // Split by status
            this.splitCreditNotesByStatus();
            
            // Now show the invoice details with the newly loaded credit notes
            this.displayInvoiceDetails(invoice);
          } else {
            console.log(`No credit notes found for invoice ${invoice.id} via API despite count of ${invoice.creditNoteCount}`);
            this.displayInvoiceDetails(invoice);
          }
        });
    } else {
      // Credit notes are already loaded or don't exist, show invoice details immediately
      this.displayInvoiceDetails(invoice);
    }
  }

  // Display invoice details - more consistent and maintainable approach
  private displayInvoiceDetails(invoice: Invoice): void {
    // Get credit notes for this invoice
    const creditNotes = this.getRelatedCreditNotes(invoice.id);
    console.log(`Preparing invoice details for ${invoice.invoiceNumber} with ${creditNotes.length} credit notes`);
    
    if (creditNotes.length > 0) {
      console.log('Credit notes to display:', creditNotes);
    }
    
    // Convert Invoice to InvoiceDetails
    const invoiceDetails: InvoiceDetails = {
      ...invoice,
      invoiceDate: invoice.date instanceof Date ? invoice.date : new Date(invoice.date),
      issueDate: invoice.date instanceof Date ? invoice.date : new Date(invoice.date),
      dueDate: invoice.dueDate instanceof Date ? invoice.dueDate : new Date(invoice.dueDate),
      paymentDate: invoice.paymentDate ? 
        (invoice.paymentDate instanceof Date ? invoice.paymentDate : new Date(invoice.paymentDate)) : 
        undefined,
      amount: {
        total: invoice.totalAmount,
        tax: invoice.totalAmount * 0.21, // Example calculation
        subtotal: invoice.totalAmount / 1.21 // Example calculation
      },
      companyName: invoice.customer || 'Unknown Company',
      contactName: 'Contact information unavailable',
      contactEmail: 'No email available',
      contactPhone: 'No phone available',
      description: `Invoice ${invoice.invoiceNumber}`,
      creditNotes: creditNotes.map(cn => {
        console.log(`Mapping credit note ${cn.id} to display format`);
        return {
          id: cn.id,
          number: cn.creditNoteNumber,
          creditNoteNumber: cn.creditNoteNumber,
          amount: cn.totalAmount,
          date: cn.date instanceof Date ? cn.date : new Date(cn.date),
          downloadUrl: `credit-notes/${cn.id}`
        };
      })
    };
    
    this.selectedInvoice = invoiceDetails;
    // Set the detail view to visible
    this.isDetailViewVisible = true;
    
    // Add the global dimming class to the body
    this.uiStateService.setDetailViewOpen(true);
    
    // Debug
    console.log('Final invoice details:', this.selectedInvoice);
    console.log('Credit notes in invoice details:', this.selectedInvoice.creditNotes?.length || 0);
    if (this.selectedInvoice.creditNotes && this.selectedInvoice.creditNotes.length > 0) {
      console.log('Credit notes content:', JSON.stringify(this.selectedInvoice.creditNotes, null, 2));
    }
  }

  // Helper methods for viewing invoice details
  selectInvoice(invoice: Invoice): void {
    this.showInvoiceDetails(invoice);
  }
  
  viewInvoiceDetails(invoice: any): void {
    this.showInvoiceDetails(invoice);
  }

  // Clean closure of invoice details
  closeInvoiceDetails(): void {
    this.selectedInvoice = null;
    this.isDetailViewVisible = false;
    
    // Remove the global dimming class from the body
    this.uiStateService.setDetailViewOpen(false);
  }

  // Download invoice
  downloadInvoice(invoice: Invoice | InvoiceDetails, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    console.log('Downloading invoice:', invoice.invoiceNumber);
    // Implement download functionality
  }

  // Download credit note
  downloadCreditNote(creditNote: any, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    console.log('Downloading credit note:', creditNote.number || creditNote.creditNoteNumber);
    // Implement download functionality
  }

  private splitInvoicesByStatus(): void {
    // First, ensure each paid invoice has a paymentDate
    this.allInvoices.forEach(invoice => {
      if (invoice.isPaid && !invoice.paymentDate) {
        // If an invoice is paid but has no payment date, set it to its date plus 7 days
        // This is just a placeholder for demo purposes
        const dateObj = invoice.date instanceof Date ? invoice.date : new Date(invoice.date);
        const paymentDateObj = new Date(dateObj);
        paymentDateObj.setDate(paymentDateObj.getDate() + 7);
        invoice.paymentDate = paymentDateObj;
      }
    });
    
    this.paidInvoices = this.allInvoices.filter(invoice => invoice.isPaid);
    this.outstandingInvoices = this.allInvoices.filter(invoice => !invoice.isPaid);
    this.filteredInvoices = [...this.getActiveInvoices()];
    
    // Update counts
    this.outstandingCount = this.outstandingInvoices.length;
    this.paidCount = this.paidInvoices.length;
    
    // Apply sorting if a sort field is set
    if (this.sortColumn) {
      this.sortInvoices();
    }
  }

  private splitCreditNotesByStatus(): void {
    this.paidCreditNotes = this.allCreditNotes.filter(note => note.status === 'paid');
    this.outstandingCreditNotes = this.allCreditNotes.filter(note => note.status !== 'paid');
    this.filteredCreditNotes = [...this.allCreditNotes];
  }

  // Complete replacement of loadCreditNotes method to prevent any API calls
  loadCreditNotes(): void {
    console.log('loadCreditNotes method called - no API call will be made');
    
    // Do not make any API calls at all
    // If credit notes are needed, they should be loaded from the invoice response
    
    // If we have no credit notes yet but have invoices, generate test ones
    if (this.allCreditNotes.length === 0 && this.allInvoices.length > 0) {
      console.log('Generating test credit notes since none were loaded from invoice response');
      this.generateTestCreditNotes();
    }
  }

  // Also make sure no HTTP calls are made in tryAlternativeIdForCreditNotes
  private tryAlternativeIdForCreditNotes() {
    console.log('tryAlternativeIdForCreditNotes called - no API call will be made');
    // Return empty observable - don't make any API calls
    return of(null);
  }

  // Helper to get active invoices based on current tab
  getActiveInvoices(): Invoice[] {
    return this.activeTab === 'outstanding' ? this.outstandingInvoices : this.paidInvoices;
  }

  // Helper to get credit notes related to an invoice
  getRelatedCreditNotes(invoiceId: string): CreditNote[] {
    if (!invoiceId) {
      console.error('Attempting to get credit notes for undefined invoice ID');
      return [];
    }
    
    // Make sure allCreditNotes is initialized
    if (!this.allCreditNotes || !Array.isArray(this.allCreditNotes)) {
      console.error('Credit notes array is not properly initialized');
      return [];
    }
    
    console.log(`Searching for credit notes related to invoice ${invoiceId} among ${this.allCreditNotes.length} available credit notes`);
    
    
    // Get related credit notes - log each one for debugging
    this.allCreditNotes.forEach(note => {
      console.log(`Credit note ${note.id}: relatedInvoiceId=${note.relatedInvoiceId}, matches invoice=${note.relatedInvoiceId === invoiceId}`);
    });
    
    // Get related credit notes
    const relatedNotes = this.allCreditNotes.filter(note => 
      note && note.relatedInvoiceId && note.relatedInvoiceId === invoiceId
    );
    
    console.log(`Found ${relatedNotes.length} credit notes for invoice ${invoiceId}`);
    
    return relatedNotes;
  }

  // Sort invoices based on current sort field and direction
  private sortInvoices(): void {
    const sortFn = (a: Invoice, b: Invoice) => {
      let valA: any = a[this.sortColumn as keyof Invoice];
      let valB: any = b[this.sortColumn as keyof Invoice];
      
      // Convert dates to timestamps for comparison
      if (valA instanceof Date) valA = valA.getTime();
      if (valB instanceof Date) valB = valB.getTime();
      if (typeof valA === 'string' && typeof valB === 'string') {
        // For string comparisons, use localeCompare
        return this.sortDirection === 'asc' ? 
          valA.localeCompare(valB) : 
          valB.localeCompare(valA);
    } else {
        // For number comparisons, use subtraction
        return this.sortDirection === 'asc' ? 
          (valA < valB ? -1 : valA > valB ? 1 : 0) : 
          (valB < valA ? -1 : valB > valA ? 1 : 0);
      }
    };
    
    // Sort both invoice lists
    this.outstandingInvoices.sort(sortFn);
    this.paidInvoices.sort(sortFn);
  }

  // Add export functionality
  exportInvoices(): void {
    this.loading = true;
    // This would typically download a CSV or Excel file with invoice data
    console.log('Exporting invoices...');
    alert('Invoice export functionality will be implemented in a future release.');
  }

  // Add functionality to mark an invoice as paid
  markAsPaid(invoice: Invoice): void {
    if (!invoice.isPaid) {
      // In a real implementation, this would call an API
      console.log('Marking invoice as paid:', invoice.invoiceNumber);
      
      // Update the invoice
      invoice.isPaid = true;
      invoice.status = 'paid';
      invoice.paymentDate = new Date(); // Set payment date to today
      
      // Move from outstanding to paid
      this.outstandingInvoices = this.outstandingInvoices.filter(inv => inv.id !== invoice.id);
      this.paidInvoices.push(invoice);
      
      // Update all invoices as well
      const index = this.allInvoices.findIndex(inv => inv.id === invoice.id);
      if (index !== -1) {
        this.allInvoices[index] = invoice;
      }
    }
  }

  viewDetails(item: Invoice | CreditNote): void {
    this.selectedItem = item;
  }

  downloadPdf(item: Invoice | CreditNote): void {
    // If we're using test data, show a message and return
    if (this.hasTestData()) {
      alert('PDF download is not available in demo mode.');
      return;
    }

    // Use the API ID that's most likely to work
    const apiCompanyId = this.getApiCompanyId();
    if (!apiCompanyId) {
      console.error('No company ID available for downloading PDF');
      alert('Unable to download PDF. Company information not available.');
      return;
    }

    const isInvoice = 'invoiceNumber' in item;
    
    const endpoint = isInvoice
      ? `teamleader/finance/company/${apiCompanyId}/invoice/${item.id}/pdf`
      : `teamleader/finance/company/${apiCompanyId}/credit-note/${item.id}/pdf`;
    
    window.open(`${this.apiService['environmentService'].apiUrl}/${endpoint}`, '_blank');
  }

  // Type guard functions to help with template
  isInvoice(item: any): item is Invoice {
    return item && 'invoiceNumber' in item;
  }

  isCreditNote(item: any): item is CreditNote {
    return item && 'creditNoteNumber' in item;
  }

  // Helper functions for template
  getStatusLabel(status: string): string {
    switch(status) {
      case 'paid': return 'Paid';
      case 'outstanding': return 'Outstanding';
      case 'overdue': return 'Overdue';
      default: return status;
    }
  }

  // Helper method to generate test data when API is not available
  private generateTestData(): void {
    this.testDataGenerated = true;
    
    // Generate test invoices
    const testInvoices: Invoice[] = [
      {
        id: '1',
        invoiceNumber: 'INV-001',
        date: '2023-01-15',
        dueDate: '2023-02-15',
        totalAmount: 1250.75,
        status: 'paid',
        paymentTerm: '30 days',
        isPaid: true,
        isOverdue: false,
        type: 'invoice',
        paymentDate: '2023-01-22'
      },
      {
        id: '2',
        invoiceNumber: 'INV-002',
        date: '2023-02-01',
        dueDate: '2023-03-01',
        totalAmount: 2420.50,
        status: 'paid',
        paymentTerm: '30 days',
        isPaid: true,
        isOverdue: false,
        type: 'invoice',
        paymentDate: '2023-02-15'
      },
      {
        id: '3',
        invoiceNumber: 'INV-003',
        date: '2023-03-10',
        dueDate: '2023-04-10',
        totalAmount: 3550.25,
        status: 'open',
        paymentTerm: '30 days',
        isPaid: false,
        isOverdue: false,
        type: 'invoice'
      },
      {
        id: '4',
        invoiceNumber: 'INV-004',
        date: '2023-03-01',
        dueDate: '2023-03-31',
        totalAmount: 1825.99,
        status: 'overdue',
        paymentTerm: '30 days',
        isPaid: false,
        isOverdue: true,
        type: 'invoice'
      },
      {
        id: '5',
        invoiceNumber: 'INV-005',
        date: '2023-04-05',
        dueDate: '2023-05-05',
        totalAmount: 3200.00,
        status: 'paid',
        paymentTerm: '30 days',
        isPaid: true,
        isOverdue: false,
        type: 'invoice',
        paymentDate: '2023-04-20'
      },
      {
        id: '6',
        invoiceNumber: 'INV-006',
        date: '2023-04-15',
        dueDate: '2023-05-15',
        totalAmount: 975.25,
        status: 'open',
        paymentTerm: '30 days',
        isPaid: false,
        isOverdue: false,
        type: 'invoice'
      }
    ];
    
    // Generate test credit notes - but only for some invoices (1, 2, and 5)
    const testCreditNotes: CreditNote[] = [
      {
        id: '101',
        creditNoteNumber: 'CN-001',
        date: '2023-01-20',
        totalAmount: 350.25,
        status: 'paid',
        relatedInvoiceId: '1',
        type: 'creditNote'
      },
      {
        id: '102',
        creditNoteNumber: 'CN-002',
        date: '2023-02-15',
        totalAmount: 675.50,
        status: 'paid',
        relatedInvoiceId: '2',
        type: 'creditNote'
      },
      {
        id: '103',
        creditNoteNumber: 'CN-003',
        date: '2023-01-25',
        totalAmount: 125.10,
        status: 'paid',
        relatedInvoiceId: '1',
        type: 'creditNote'
      },
      {
        id: '104',
        creditNoteNumber: 'CN-004',
        date: '2023-04-25',
        totalAmount: 800.00,
        status: 'paid',
        relatedInvoiceId: '5',
        type: 'creditNote'
      }
    ];
    
    this.allInvoices = testInvoices;
    this.allCreditNotes = testCreditNotes;
    
    // Split by status
    this.splitInvoicesByStatus();
    this.splitCreditNotesByStatus();
    
    // Log summary of generated data
    console.log(`Generated ${this.allInvoices.length} test invoices and ${this.allCreditNotes.length} test credit notes`);
    
    // Check how many invoices have credit notes
    let invoicesWithCreditNotes = 0;
    this.allInvoices.forEach(invoice => {
      const creditNotesCount = this.getRelatedCreditNotes(invoice.id).length;
      if (creditNotesCount > 0) {
        invoicesWithCreditNotes++;
        console.log(`Test invoice ${invoice.invoiceNumber} has ${creditNotesCount} credit notes`);
      }
    });
    console.log(`${invoicesWithCreditNotes} out of ${this.allInvoices.length} test invoices have credit notes`);
    
    this.loading = false;
  }

  // Helper to safely format amounts with 2 decimal places
  formatAmount(amount?: number): string {
    if (amount === null || amount === undefined) {
      return '0.00';
    }
    
    // Parse string values if needed
    if (typeof amount === 'string') {
      try {
        amount = parseFloat(amount);
      } catch (error) {
        console.error('Error parsing amount string:', error);
        return '0.00';
      }
    }
    
    // Handle NaN
    if (isNaN(amount)) {
      return '0.00';
    }
    
    return amount.toFixed(2);
  }

  // Toggle collapsible sections
  toggleSection(section: 'outstandingInvoices' | 'paidInvoices' | 'outstandingCreditNotes' | 'paidCreditNotes'): void {
    switch(section) {
      case 'outstandingInvoices':
        this.outstandingInvoicesCollapsed = !this.outstandingInvoicesCollapsed;
        break;
      case 'paidInvoices':
        this.paidInvoicesCollapsed = !this.paidInvoicesCollapsed;
        break;
      case 'outstandingCreditNotes':
        this.outstandingCreditNotesCollapsed = !this.outstandingCreditNotesCollapsed;
        break;
      case 'paidCreditNotes':
        this.paidCreditNotesCollapsed = !this.paidCreditNotesCollapsed;
        break;
    }
  }

  // Helper method to get icon for collapsed/expanded sections
  getSectionIcon(isCollapsed: boolean): string {
    return isCollapsed ? 'expand_more' : 'expand_less';
  }

  // Check if we're using test data (API not available)
  private hasTestData(): boolean {
    // If we have data but API failed with 404, we're using test data
    return (
      (this.paidInvoices.length > 0 || this.outstandingInvoices.length > 0) &&
      (this.paidInvoices[0]?.id === '1' || this.outstandingInvoices[0]?.id === '3')
    );
  }

  hasActiveFilters(): boolean {
    return !!(
      this.searchText || 
      (this.statusFilter && this.statusFilter !== 'all') || 
      (this.dateRangeFilter && this.dateRangeFilter !== 'all') ||
      (this.amountRangeFilter && this.amountRangeFilter !== 'all')
    );
  }
  
  resetFilters() {
    this.searchText = '';
    this.statusFilter = 'all';
    this.dateRangeFilter = 'all';
    this.amountRangeFilter = 'all';
    this.dateFilter = null;
    this.amountFilter = null;
    this.customDateFrom = null;
    this.customDateTo = null;
    this.customAmountMin = null;
    this.customAmountMax = null;
    this.applyFilters();
  }
  
  toggleDateRange() {
    if (this.dateFilter?.from !== 'custom') {
      this.customDateFrom = null;
      this.customDateTo = null;
    }
    this.applyFilters();
  }
  
  toggleAmountRange() {
    if (this.amountFilter !== 'custom') {
      this.customAmountMin = null;
      this.customAmountMax = null;
    }
    this.applyFilters();
  }
  
  // Status helper
  getStatusClass(invoice: any) {
    switch (invoice.status) {
      case 'paid':
        return 'active';
      case 'overdue':
        return 'pending';
      case 'outstanding':
        return 'inactive';
      default:
        return '';
    }
  }

  // Implement ngOnDestroy to clean up subscriptions
  ngOnDestroy(): void {
    // Clean up all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
    
    // Make sure to remove the detail view open class when component is destroyed
    this.uiStateService.setDetailViewOpen(false);
  }

  // Add getSortIcon method for table sorting
  getSortIcon(column: string): string {
    if (this.sortColumn !== column) {
      return 'fa-sort';
    }
    
    return this.sortDirection === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  initializeFilterModels(): void {
    // Initialize filter models
    this.dateFilter = { from: null, to: null };
    this.amountFilter = { min: null, max: null };
  }
}
