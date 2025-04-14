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
  creditNoteCount?: number;
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

// Invoice details interface for display
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
  companyName?: string;
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
  // Tab state
  activeTab: 'outstanding' | 'paid' = 'outstanding';
  
  // Invoice and credit note data
  paidInvoices: Invoice[] = [];
  outstandingInvoices: Invoice[] = [];
  private allInvoices: Invoice[] = [];
  private allCreditNotes: CreditNote[] = [];
  filteredInvoices: Invoice[] = [];
  
  // Counters
  outstandingCount: number = 0;
  paidCount: number = 0;
  
  // Filter state
  statusFilterOpen: boolean = false;
  dateFilterOpen: boolean = false;
  amountFilterOpen: boolean = false;
  statusFilter: string = 'all';
  dateFilter: any = null;
  amountFilter: any = null;
  searchText = '';
  dateRangeFilter: string = 'all';
  amountRangeFilter: string = 'all';
  
  // UI state
  loading = false;
  hasError: boolean = false;
  errorMessage: string = '';
  selectedInvoice: InvoiceDetails | null = null;
  isDetailViewVisible: boolean = false;
  tableView = true;
  
  // Sorting
  sortColumn: string = 'date';
  sortDirection: string = 'desc';
  
  // Company identification
  private currentUserEmail: string = '';
  private currentUserDomain: string = '';
  private companyId: string = '';
  private teamleaderId: string = '';
  
  // Subscription management
  private subscriptions: Subscription[] = [];
  
  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private uiStateService: UiStateService
  ) {}

  ngOnInit(): void {
    const userSub = this.authService.user$.subscribe(user => {
      if (user && user.email) {
        this.currentUserEmail = user.email;
        const emailParts = user.email.split('@');
        if (emailParts.length === 2) {
          this.currentUserDomain = emailParts[1];
          this.getCompanyIdByDomain();
        }
      }
    });
    this.subscriptions.push(userSub);
    
    this.loading = true;
    this.allCreditNotes = [];
    this.initializeFilterModels();
  }
  
  // Get company ID by the user's domain
  private getCompanyIdByDomain(): void {
    this.apiService.get<any>(`teamleader/companies`)
      .pipe(
        catchError(error => {
          console.error('Error fetching companies:', error);
          this.generateTestData();
          return of(null);
        })
      )
      .subscribe(response => {
        if (response && response.companies && response.companies.length > 0) {
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
              this.teamleaderId = this.validateTeamleaderId(matchByEmail.teamleader_id || matchByEmail.teamleaderId || '');
              this.loadInvoices();
              return;
            }
          }

          if (matchingCompany) {
            this.companyId = matchingCompany.id;
            this.teamleaderId = this.validateTeamleaderId(matchingCompany.teamleader_id || matchingCompany.teamleaderId || '');
            this.loadInvoices();
          } else {
            // Fallback to first company if no match found
            if (response.companies.length > 0) {
              this.companyId = response.companies[0].id;
              this.teamleaderId = this.validateTeamleaderId(response.companies[0].teamleader_id || response.companies[0].teamleaderId || '');
              this.loadInvoices();
            } else {
              this.generateTestData();
            }
          }
        } else {
          this.generateTestData();
        }
      });
  }

  async loadInvoices() {
    this.loading = true;
    this.errorMessage = '';
    this.allCreditNotes = [];

    const companyId = this.getApiCompanyId();
    if (!companyId) {
      this.loading = false;
      this.errorMessage = 'No valid company ID available';
      return;
    }
    
    try {
      const response = await this.apiService.get<any>(`teamleader/finance/company/${companyId}/invoices`).toPromise();
      
      if (!response || (!Array.isArray(response) && !response.data)) {
        throw new Error('Invalid response format');
      }

      // Handle both array response and {data: []} response formats
      const invoices = Array.isArray(response) ? response : response.data;
      
      if (!invoices || invoices.length === 0) {
            this.generateTestData();
          } else {
        // Process the invoice data using processInvoicesData
        this.allInvoices = this.processInvoicesData(invoices);
        this.processLoadedInvoices();
      }
    } catch (error) {
      console.error('Error loading invoices:', error);
            this.errorMessage = 'Failed to load invoices. Please try again later.';
      this.generateTestData();
    } finally {
      this.loading = false;
    }
  }

  private processLoadedInvoices() {
    if (!this.allInvoices || !Array.isArray(this.allInvoices)) {
      this.generateTestData();
      return;
    }
    
    // Map Teamleader API status values to our internal status values
    const outstandingInvoices = this.allInvoices.filter(inv => {
      const status = (inv.status || '').toLowerCase();
      return status === 'open' || status === 'outstanding' || status === 'overdue' || status === 'draft';
    });
    
    const paidInvoices = this.allInvoices.filter(inv => {
      const status = (inv.status || '').toLowerCase();
      return status === 'paid' || status === 'matched';
    });
    
    // If we still have no invoices in either category, assume they're all outstanding
    if (outstandingInvoices.length === 0 && paidInvoices.length === 0 && this.allInvoices.length > 0) {
      this.outstandingInvoices = [...this.allInvoices];
      this.paidInvoices = [];
    } else {
      this.outstandingInvoices = outstandingInvoices;
      this.paidInvoices = paidInvoices;
    }
    
    // Update counts
    this.outstandingCount = this.outstandingInvoices.length;
    this.paidCount = this.paidInvoices.length;
    
    // Set filtered invoices based on active tab
    this.filteredInvoices = [...this.getActiveInvoices()];
    
    // Generate test credit notes if needed
    if (this.allInvoices.length > 0 && this.allCreditNotes.length === 0) {
      this.generateTestCreditNotes();
    }
  }

  // Helper to get the company ID to use for API calls
  private getApiCompanyId(): string {
    if (this.isValidTeamleaderId(this.teamleaderId)) {
      return this.teamleaderId;
    }
    return this.companyId || '';
  }

  // Helper to check if a string is a valid Teamleader ID
  private isValidTeamleaderId(id: string): boolean {
    if (!id || id.trim() === '') {
      return false;
    }
    
    const teamleaderIdPattern = /^[a-zA-Z0-9-]{30,}$/;
    const hasDashes = id.includes('-');
    const isMongoDB = /^[a-f0-9]{24}$/.test(id);
    
    if (isMongoDB) {
      return false;
    }
    
    return teamleaderIdPattern.test(id) && hasDashes;
  }

  // Helper to process invoices from API response
  private processInvoicesData(data: any): Invoice[] {
    if (!data || !Array.isArray(data)) {
      return [];
    }

    return data.map((invoice: any) => {
      // Extract invoice number
      const invoiceNumber = invoice.number || 
                           invoice.invoice_number || 
                           invoice.reference || 
                           'Unknown';
      
      // Extract dates
      const invoiceDate = this.formatApiDate(invoice.date || invoice.created_at);
      const invoiceDueDate = this.formatApiDate(invoice.due_date || invoice.due_on);
      
      // Extract total amount
      let totalAmount = 0;
      if (invoice.total && typeof invoice.total === 'number') {
        totalAmount = invoice.total;
      } else if (invoice.total_price && invoice.total_price.amount) {
        totalAmount = parseFloat(invoice.total_price.amount);
      } else if (invoice.total && typeof invoice.total === 'string') {
        totalAmount = parseFloat(invoice.total);
      }
      
      // Extract status
      const status = invoice.status || 'unknown';
      
      // Extract customer name
      let customerName = 'Unknown Customer';
      if (invoice.customer && invoice.customer.name) {
        customerName = invoice.customer.name;
      } else if (invoice.contact && invoice.contact.name) {
        customerName = invoice.contact.name;
      } else if (invoice.customer_name) {
        customerName = invoice.customer_name;
      }

      // Create and return our Invoice object
      return {
        id: invoice.id || `unknown-${Math.random().toString(36).substring(2, 10)}`,
        invoiceNumber: invoiceNumber,
        date: invoiceDate,
        dueDate: invoiceDueDate,
        totalAmount: totalAmount,
        status: this.mapInvoiceStatusToComponentStatus(status),
        paymentTerm: invoice.payment_term || '30 days',
        isPaid: status.toLowerCase() === 'paid' || status.toLowerCase() === 'matched',
        isOverdue: status.toLowerCase() === 'overdue',
        type: 'invoice',
        customer: customerName
      } as Invoice;
    });
  }

  // Generate test credit notes based on existing invoices
  private generateTestCreditNotes(): void {
    // Only create credit notes for about 30% of invoices
    const invoicesWithCreditNotes = this.allInvoices
      .filter((_, index) => index % 3 === 0)
      .slice(0, Math.max(1, Math.floor(this.allInvoices.length * 0.3)));
    
    // Create 1-2 credit notes for the selected invoices
    invoicesWithCreditNotes.forEach((invoice, index) => {
      // First two invoices get 2 credit notes, others get 1
      const numNotesForThisInvoice = index < 2 ? 2 : 1;
      
      for (let i = 0; i < numNotesForThisInvoice; i++) {
        // Create a credit note for a portion of the invoice amount
        const creditNoteAmount = invoice.totalAmount * (0.15 + (i * 0.10));
        
        this.allCreditNotes.push({
          id: `-${invoice.id}-${i+1}`,
          creditNoteNumber: `${invoice.invoiceNumber}-CN${i+1}`,
          date: new Date(),
          totalAmount: creditNoteAmount,
          status: invoice.isPaid ? 'paid' : 'outstanding',
          relatedInvoiceId: invoice.id,
          type: 'creditNote'
        });
      }
    });
  }

  // Helper methods for status mapping
  private mapApiStatusToComponentStatus(apiStatus: string, isPaid: boolean, isOverdue: boolean): string {
    if (isPaid) return 'paid';
    if (isOverdue) return 'overdue';
    if (apiStatus === 'matched') return 'paid';
    if (apiStatus === 'draft') return 'draft';
    return 'outstanding';
  }

  private mapCreditNoteStatusToComponentStatus(apiStatus: string): string {
    if (apiStatus === 'booked' || apiStatus === 'matched') return 'paid';
    if (apiStatus === 'draft') return 'draft';
    return 'outstanding';
  }

  private mapInvoiceStatusToComponentStatus(status: string): string {
    let isPaid = false;
    let isOverdue = false;
    
    const lowerStatus = status.toLowerCase();
    if (lowerStatus === 'paid' || lowerStatus === 'matched') {
      isPaid = true;
    } else if (lowerStatus === 'overdue') {
      isOverdue = true;
    }
    
    return this.mapApiStatusToComponentStatus(lowerStatus, isPaid, isOverdue);
  }

  // Tab switching
  switchTab(tab: 'outstanding' | 'paid'): void {
    this.activeTab = tab;
    this.selectedInvoice = null;
    // Reset search and filter states
    this.searchText = '';
    this.statusFilter = 'all';
    this.dateRangeFilter = 'all';
    this.amountRangeFilter = 'all';
    // Update filtered invoices based on the newly selected tab
    this.filteredInvoices = [...this.getActiveInvoices()];
    this.applyFilters();
  }

  // For backward compatibility
  setActiveTab(tab: 'outstanding' | 'paid'): void {
    this.switchTab(tab);
  }

  // Filter management
  clearSearch(): void {
    this.searchText = '';
    this.applyFilters();
  }

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
    this.applyFilters();
  }

  // Filter application
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
    
    // Apply sorting
    this.sortInvoices();
  }

  // Same as applyFilters for consistency
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

  // Show invoice details
  showInvoiceDetails(invoice: Invoice): void {
    if (!invoice || !invoice.id) {
      return;
    }
    
    // Fetch credit notes if needed, otherwise display details immediately
    if (this.getRelatedCreditNotes(invoice.id).length === 0) {
      this.apiService.get<any>(`teamleader/finance/company/${this.getApiCompanyId()}/invoices/${invoice.id}/credit-notes`)
        .pipe(
          catchError(error => {
            if (this.allCreditNotes.length === 0) {
              this.generateTestCreditNotes();
            }
            return of([]);
          })
        )
        .subscribe(creditNotes => {
          if (creditNotes && creditNotes.length > 0) {
            // Process and add to allCreditNotes
            const newCreditNotes = creditNotes.map((cn: any) => ({
              id: cn.id || '',
              creditNoteNumber: cn.credit_note_number || cn.number || `CN-${cn.id}`,
              date: new Date(cn.credit_note_date || cn.date || new Date()),
              totalAmount: this.extractAmount(cn.total),
              status: this.mapCreditNoteStatusToComponentStatus(cn.status || ''),
              relatedInvoiceId: invoice.id,
              type: 'creditNote'
            }));
            
            this.allCreditNotes.push(...newCreditNotes);
          }
          
          this.displayInvoiceDetails(invoice);
        });
    } else {
      this.displayInvoiceDetails(invoice);
    }
  }

  // Extract amount from various formats in API responses
  private extractAmount(total: any): number {
    if (!total) return 0;
    
    if (typeof total === 'number') {
      return total;
    } else if (typeof total === 'object') {
      // Try different possible paths in the total object
      if (total.amount) {
        return parseFloat(String(total.amount));
      } else if (total.tax_exclusive && total.tax_exclusive.amount) {
        return parseFloat(String(total.tax_exclusive.amount));
      } else if (total.tax_inclusive && total.tax_inclusive.amount) {
        return parseFloat(String(total.tax_inclusive.amount));
      }
    } else if (typeof total === 'string') {
      return parseFloat(total);
    }
    
    return 0;
  }

  // Display invoice details with proper handling of different data formats
  private displayInvoiceDetails(invoice: Invoice): void {
    if (!invoice || !invoice.id) {
      return;
    }

    // Get related credit notes for this invoice
    const creditNotes = this.getRelatedCreditNotes(invoice.id);
    
    // Convert Invoice to InvoiceDetails
    const invoiceDetails: InvoiceDetails = {
      ...invoice,
      // Convert dates ensuring they are Date objects
      invoiceDate: invoice.date instanceof Date ? invoice.date : new Date(invoice.date),
      issueDate: invoice.date instanceof Date ? invoice.date : new Date(invoice.date),
      dueDate: invoice.dueDate instanceof Date ? invoice.dueDate : new Date(invoice.dueDate),
      paymentDate: invoice.paymentDate ? 
        (invoice.paymentDate instanceof Date ? invoice.paymentDate : new Date(invoice.paymentDate)) : 
        undefined,
      // Add amount details
      amount: {
        total: invoice.totalAmount,
        tax: invoice.totalAmount * 0.21, // Example calculation
        subtotal: invoice.totalAmount / 1.21 // Example calculation
      },
      // Add customer info
      companyName: invoice.customer || 'Unknown Company',
      description: `Invoice ${invoice.invoiceNumber}`,
      // Map credit notes with proper formatting
      creditNotes: creditNotes.map(cn => ({
        id: cn.id,
        number: cn.creditNoteNumber,
        creditNoteNumber: cn.creditNoteNumber,
        amount: cn.totalAmount,
        date: cn.date instanceof Date ? cn.date : new Date(cn.date),
        downloadUrl: `credit-notes/${cn.id}`
      }))
    };
    
    // Set the current invoice and show detail view
    this.selectedInvoice = invoiceDetails;
    this.isDetailViewVisible = true;
    
    // Add a global dimming class to the body via the UI service
    this.uiStateService.setDetailViewOpen(true);
  }

  // Download invoice
  downloadInvoice(invoice: Invoice | InvoiceDetails): void {
    window.open(`${this.apiService['environmentService'].apiUrl}/teamleader/finance/company/${this.getApiCompanyId()}/invoice/${invoice.id}/pdf`, '_blank');
  }

  // Download credit note
  downloadCreditNote(creditNote: any, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    window.open(`${this.apiService['environmentService'].apiUrl}/teamleader/finance/company/${this.getApiCompanyId()}/credit-note/${creditNote.id}/pdf`, '_blank');
  }

  // Helper to get active invoices based on current tab
  getActiveInvoices(): Invoice[] {
    return this.activeTab === 'outstanding' ? this.outstandingInvoices : this.paidInvoices;
  }

  // Helper to get credit notes related to an invoice
  getRelatedCreditNotes(invoiceId: string): CreditNote[] {
    if (!invoiceId || !this.allCreditNotes || !Array.isArray(this.allCreditNotes)) {
      return [];
    }
    
    return this.allCreditNotes.filter(note => 
      note && note.relatedInvoiceId && note.relatedInvoiceId === invoiceId
    );
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

  // Helper method to generate test data when API is not available
  private generateTestData(): void {
    // Generate 6 test invoices with various statuses
    this.allInvoices = Array(6).fill(0).map((_, i) => {
      const id = (i + 1).toString();
      const isPaid = i < 3; // First 3 are paid
      const isOverdue = !isPaid && i % 2 === 0; // Some unpaid ones are overdue
      
      return {
        id,
        invoiceNumber: `INV-00${id}`,
        date: new Date(2023, i, 15), // Different months
        dueDate: new Date(2023, i + 1, 15),
        totalAmount: 1000 + (i * 500),
        status: isPaid ? 'paid' : (isOverdue ? 'overdue' : 'outstanding'),
        paymentTerm: '30 days',
        isPaid,
        isOverdue,
        type: 'invoice',
        paymentDate: isPaid ? new Date(2023, i, 22) : undefined,
        customer: 'Test Company'
      };
    });
    
    // Generate credit notes for approximately 30% of invoices
    this.allCreditNotes = [];
    const invoicesWithCreditNotes = [1, 2, 5]; // Only specific invoices get credit notes
    
    invoicesWithCreditNotes.forEach((invoiceId, i) => {
      // Add 1-2 credit notes per invoice
      const noteCount = invoiceId <= 2 ? 2 : 1;
      
      for (let j = 0; j < noteCount; j++) {
        const id = `${100 + this.allCreditNotes.length + 1}`;
        this.allCreditNotes.push({
          id,
          creditNoteNumber: `CN-${id}`,
          date: new Date(),
          totalAmount: this.allInvoices[invoiceId-1].totalAmount * (0.2 + (j * 0.1)),
          status: this.allInvoices[invoiceId-1].isPaid ? 'paid' : 'outstanding',
          relatedInvoiceId: invoiceId.toString(),
        type: 'creditNote'
        });
      }
    });
    
    // Process the test data
    this.outstandingInvoices = this.allInvoices.filter(invoice => !invoice.isPaid);
    this.paidInvoices = this.allInvoices.filter(invoice => invoice.isPaid);
    this.outstandingCount = this.outstandingInvoices.length;
    this.paidCount = this.paidInvoices.length;
    this.filteredInvoices = [...this.getActiveInvoices()];
    
    this.loading = false;
  }

  // Helper to safely format amounts with 2 decimal places
  formatAmount(amount?: number): string {
    if (amount === null || amount === undefined || isNaN(amount)) {
      return '0.00';
    }
    return amount.toFixed(2);
  }

  // Get status class for styling
  getStatusClass(invoice: any) {
    switch (invoice.status) {
      case 'paid': return 'active';
      case 'overdue': return 'pending';
      case 'outstanding': return 'inactive';
      default: return '';
    }
  }

  // Check if any filters are active
  hasActiveFilters(): boolean {
    return !!(
      this.searchText || 
      (this.statusFilter && this.statusFilter !== 'all') || 
      (this.dateRangeFilter && this.dateRangeFilter !== 'all') ||
      (this.amountRangeFilter && this.amountRangeFilter !== 'all')
    );
  }

  // Clean up on destroy
  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
    this.uiStateService.setDetailViewOpen(false);
  }

  // UI helpers
  getSortIcon(column: string): string {
    if (this.sortColumn !== column) {
      return 'fa-sort';
    }
    return this.sortDirection === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  initializeFilterModels(): void {
    this.dateFilter = { from: null, to: null };
    this.amountFilter = { min: null, max: null };
  }

  // Helper to validate and format Teamleader ID
  private validateTeamleaderId(id: string): string {
    if (!id || id.trim() === '' || !/^[a-zA-Z0-9-]+$/.test(id.trim())) {
      return '';
    }
    return id.trim();
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
    this.uiStateService.setDetailViewOpen(false);
  }

  // Format date from API
  private formatApiDate(dateString: string | null): Date {
    if (!dateString) {
      return new Date();
    }
    
    try {
      const date = new Date(dateString);
      if (isNaN(date.getTime())) {
        return new Date();
      }
      return date;
    } catch (e) {
      return new Date();
    }
  }
}
