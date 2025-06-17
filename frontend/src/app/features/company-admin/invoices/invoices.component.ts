import { Component, OnInit, OnDestroy, HostListener, ElementRef, Renderer2 } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { finalize } from 'rxjs/operators';
import { HttpParams } from '@angular/common/http';
import { catchError, of, Subscription } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { RouterModule } from '@angular/router';
import { UiStateService } from '../../../core/services/ui-state.service';
import { Invoice, CreditNote, TeamleaderInvoiceResponse, InvoiceDetails } from '../../../core/models/invoice.model';
import { Company } from '../../../core/models/company.model';

// Import standardized components
import { PageHeaderComponent, PageAction } from '../../../shared/components/page-header/page-header.component';
import { SearchFilterComponent, FilterConfig, SearchFilterEvent } from '../../../shared/components/search-filter/search-filter.component';
import { DataTableComponent, TableColumn, TableAction, SortEvent, PaginationEvent } from '../../../shared/components/data-table/data-table.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

// For production build, define a simple environment object
const environment = {
  production: false
};

@Component({
  selector: 'app-invoices',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    ReactiveFormsModule, 
    RouterModule,
    PageHeaderComponent,
    DataTableComponent,
    LoadingSpinnerComponent,
    SearchFilterComponent
  ],
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
  statusFilter: string = 'all';
  searchText = '';
  
  // UI state
  loading = false;
  hasError: boolean = false;
  errorMessage: string = '';
  selectedInvoice: InvoiceDetails | null = null;
  isDetailViewVisible: boolean = false;
  tableView = true;
  
  // Sorting
  sortColumn: string = 'paymentReference';
  sortDirection: 'asc' | 'desc' = 'asc';
  
  // Company identification
  private currentUserEmail: string = '';
  private currentUserDomain: string = '';
  private companyId: string = '';
  private teamleaderId: string = '';
  
  // Subscription management
  private subscriptions: Subscription[] = [];

  // Add missing properties used in the displayInvoiceDetails method
  invoiceDetails: any;
  selectedInvoiceId: string | null = null;
  isShowingInvoiceDetails = false;
  
  // New property for download options
  showDownloadOptions: boolean = false;
  
  // Configuration for standardized components
  headerActions: PageAction[] = [
    // Removed Download All button as requested
  ];

  filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { value: 'Outstanding', label: 'Outstanding' },
        { value: 'Overdue', label: 'Overdue' }
      ]
    }
  ];

  tableColumns: TableColumn[] = [
    {
      key: 'paymentReference',
      label: 'Payment Reference',
      sortable: false,
      type: 'text'
    },
    {
      key: 'dueDate',
      label: 'Due Date',
      sortable: true,
      type: 'date'
    },
    {
      key: 'totalAmount',
      label: 'Amount',
      sortable: true,
      type: 'currency'
    },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      type: 'badge'
    }
  ];

  tableActions: TableAction[] = [
    {
      label: 'Download',
      icon: 'download',
      action: 'download',
      variant: 'ghost'
    }
  ];

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private uiStateService: UiStateService,
    private elementRef: ElementRef,
    private renderer: Renderer2
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
              this.errorMessage = 'No company found for this user';
            }
          }
        } else {
          this.errorMessage = 'No company found for this user';
        }
      });
  }

  async loadInvoices() {
    this.loading = true;
    this.errorMessage = '';
    
    const companyId = this.getApiCompanyId();
    if (!companyId) {
      this.loading = false;
      this.errorMessage = 'No valid company ID available';
      return;
    }
    
    try {
      // Load all invoices at once instead of filtering by tab
      const baseEndpoint = `teamleader/finance/company/${companyId}/invoices`;
      
      // Get both paid and unpaid invoices
      const [paidResponse, unpaidResponse] = await Promise.all([
        this.apiService.get<TeamleaderInvoiceResponse[]>(`${baseEndpoint}?status=paid`).toPromise(),
        this.apiService.get<TeamleaderInvoiceResponse[]>(`${baseEndpoint}?status=unpaid`).toPromise()
      ]);
      
      // Process both responses
      const paidInvoices = this.processInvoicesData(paidResponse || []);
      const unpaidInvoices = this.processInvoicesData(unpaidResponse || []);
      
      // Store all invoices
      this.allInvoices = [...paidInvoices, ...unpaidInvoices];
      this.paidInvoices = paidInvoices;
      this.outstandingInvoices = unpaidInvoices;
      
      // Update counts
      this.outstandingCount = this.outstandingInvoices.length;
      this.paidCount = this.paidInvoices.length;
      
      // Set filtered invoices based on active tab
      this.filteredInvoices = [...this.getActiveInvoices()];
      
      // Credit notes are now loaded on-demand when viewing invoice details
  
    } catch (error) {
      this.errorMessage = 'Failed to load invoices. Please try again later.';
    } finally {
      this.loading = false;
    }
  }

  /**
   * Process the loaded invoices, separating them into paid and unpaid collections
   * @param invoices The array of invoices to process
   */
  private processLoadedInvoices(invoices: Invoice[]): void {
    this.allInvoices = invoices;
    
    // Separate paid and unpaid invoices
    this.paidInvoices = invoices.filter(invoice => invoice.isPaid);
    this.outstandingInvoices = invoices.filter(invoice => !invoice.isPaid);
    
    // Update counts
    this.paidCount = this.paidInvoices.length;
    this.outstandingCount = this.outstandingInvoices.length;
    
    // Set initial filtered invoices based on active tab
    this.filteredInvoices = this.activeTab === 'outstanding' ? this.outstandingInvoices : this.paidInvoices;
    
    // Apply any existing filters and sorting
    this.applyFiltersAndSort();
    
    if (this.paidInvoices.length === 0) {
      // No paid invoices
    }
    if (this.outstandingInvoices.length === 0) {
      // No unpaid invoices
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
  private processInvoicesData(data: TeamleaderInvoiceResponse[]): Invoice[] {
    if (!data || !Array.isArray(data)) {
      return [];
    }

    return data.map((invoice: TeamleaderInvoiceResponse) => {
      // Extract reference id as invoice number
      const invoiceNumber = invoice.id || 'Unknown';
      
      // Extract due date
      let dueDate: Date;
      if (typeof invoice.dueOn === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(invoice.dueOn)) {
        dueDate = this.parseDateWithoutTimezone(invoice.dueOn);
      } else {
        dueDate = this.formatApiDate(invoice.dueOn);
      }
      
      // Extract invoice date from API if available
      let invoiceDate: Date | undefined = undefined;
      if ((invoice as any).invoiceDate) {
        // Check if it's a date-only string and parse without timezone conversion
        if (typeof (invoice as any).invoiceDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test((invoice as any).invoiceDate)) {
          invoiceDate = this.parseDateWithoutTimezone((invoice as any).invoiceDate);
        } else {
          invoiceDate = this.formatApiDate((invoice as any).invoiceDate);
        }
      } else if ((invoice as any).date) {
        // Check if it's a date-only string and parse without timezone conversion
        if (typeof (invoice as any).date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test((invoice as any).date)) {
          invoiceDate = this.parseDateWithoutTimezone((invoice as any).date);
        } else {
          invoiceDate = this.formatApiDate((invoice as any).date);
        }
      } else if ((invoice as any).createdAt) {
        invoiceDate = this.formatApiDate((invoice as any).createdAt);
      } else {
        // The simplified API doesn't provide invoice creation date
        // We'll need to fetch it from the detailed invoice API
        // For now, set to undefined and it will be fetched when viewing details
        invoiceDate = undefined;
      }
      
      // Extract payment date for paid invoices
      let paymentDate: Date | undefined = undefined;
      if (invoice.isPaid && (invoice as any).paidAt) {
        paymentDate = this.formatApiDate((invoice as any).paidAt);
      }
      
      // Determine status for badge display
      let status = 'Outstanding';
      if (invoice.isPaid) {
        status = 'Paid';
      } else if (invoice.isOverdue) {
        status = 'Overdue';
      }
      
      // Create and return our Invoice object
      return {
        id: invoice.id,
        invoiceNumber: invoiceNumber,
        dueDate: dueDate,
        invoiceDate: invoiceDate, // Add calculated invoice date
        paymentDate: paymentDate, // Add payment date for paid invoices
        totalAmount: invoice.total || 0,
        paymentReference: invoice.paymentReference || '',
        currency: invoice.currency || 'EUR',
        isPaid: invoice.isPaid || false,
        isOverdue: invoice.isOverdue || false,
        type: 'invoice',
        customer: 'Customer', // We don't have customer info in simplified DTO
        status: status // Add status for badge display
      } as Invoice;
    });
  }

  // Note: Credit notes are now loaded from API when viewing invoice details

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
    this.isDetailViewVisible = false;
    
    // Reset search state
    this.searchText = '';
    
    // Update filtered invoices based on the new active tab
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

  // Search and filter handler for standardized component
  onSearchFilter(event: SearchFilterEvent): void {
    this.searchText = event.searchQuery;
    this.statusFilter = event.filters['status'] || 'all';
    this.applyFilters();
  }

  clearFilters(): void {
    this.searchText = '';
    this.statusFilter = 'all';
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
    // Get invoices based on the active tab
    let filteredList = this.getActiveInvoices();
    
    // Apply search term filter
    if (this.searchText) {
      const term = this.searchText.toLowerCase();
      filteredList = filteredList.filter(invoice => 
        invoice.paymentReference?.toLowerCase().includes(term) ||
        (invoice.totalAmount.toString().includes(term))
      );
    }
    
    // Apply status filter
    if (this.statusFilter !== 'all') {
      filteredList = filteredList.filter(invoice => {
        const status = invoice.status?.toLowerCase() || '';
        if (this.statusFilter === 'Paid') return invoice.isPaid || status.includes('paid');
        if (this.statusFilter === 'Outstanding') return (!invoice.isPaid && !invoice.isOverdue) || status.includes('outstanding');
        if (this.statusFilter === 'Overdue') return invoice.isOverdue || status.includes('overdue');
        return true;
      });
    }
    
    this.filteredInvoices = filteredList;
    this.sortInvoices();
  }

  // Same as applyFilters for consistency
  applyFiltersAndSort(): void {
    this.applyFilters();
  }

  // Reset filter methods
  resetStatusFilter(): void {
    this.statusFilter = 'all';
    this.applyFilters();
  }

  setStatusFilter(status: string): void {
    this.statusFilter = status;
    this.applyFilters();
  }

  // Enhanced showInvoiceDetails with mobile optimization
  showInvoiceDetails(invoice: Invoice): void {
    // Calculate amount breakdown as fallback
    const total = invoice.totalAmount;
    const subtotal = Math.round((total / 1.21) * 100) / 100; // Assuming 21% VAT
    const tax = Math.round((total - subtotal) * 100) / 100;
    

    
    // Set initial details with fallback data
    this.selectedInvoice = {
      ...invoice,
      invoiceDate: invoice.invoiceDate ? new Date(invoice.invoiceDate) : undefined, // Copy from invoice if available
      dueDate: new Date(invoice.dueDate),
      paymentDate: invoice.paymentDate ? new Date(invoice.paymentDate) : undefined, // Copy payment date if available
      amount: {
        total: total,
        subtotal: subtotal,
        tax: tax
      }
    } as InvoiceDetails;
    

    
          this.isDetailViewVisible = true;
      
      // Try to fetch detailed invoice information to get the actual invoice date
      const companyId = this.getApiCompanyId();
      if (companyId) {
        this.apiService.get(`teamleader/finance/company/${companyId}/invoices/${invoice.id}`)
          .pipe(
            catchError(error => {
              console.warn('Could not fetch detailed invoice information:', error);
              return of(null);
            })
          )
          .subscribe(detailedResponse => {
            if (detailedResponse && this.selectedInvoice) {
              // Update the selected invoice with detailed information
              this.selectedInvoice = this.mapApiResponseToInvoiceDetails(detailedResponse, invoice);
            }
          });
      }
      
      // Load real credit notes for this invoice
    this.loadCreditNotesForInvoice(invoice.id);
    
    // Prevent body scroll on mobile when detail panel is open
    if (this.isMobileView()) {
      this.renderer.addClass(document.body, 'modal-open');
    }
  }

  // Enhanced closeInvoiceDetails with mobile optimization
  closeInvoiceDetails(): void {
    this.isDetailViewVisible = false;
    this.selectedInvoice = null;
    
    // Re-enable body scroll on mobile
    if (this.isMobileView()) {
      this.renderer.removeClass(document.body, 'modal-open');
    }
  }

  // Check if current view is mobile
  private isMobileView(): boolean {
    return window.innerWidth <= 768;
  }

  // Handle window resize to adjust mobile behavior
  @HostListener('window:resize', ['$event'])
  onResize(event: any): void {
    // Close detail panel if switching from mobile to desktop with panel open
    if (this.isDetailViewVisible && !this.isMobileView()) {
      // Remove mobile-specific body class if switching to desktop
      this.renderer.removeClass(document.body, 'modal-open');
    } else if (this.isDetailViewVisible && this.isMobileView()) {
      // Add mobile-specific body class if switching to mobile
      this.renderer.addClass(document.body, 'modal-open');
    }
  }

  // Handle escape key to close detail panel
  @HostListener('document:keydown.escape', ['$event'])
  onEscapeKey(event: KeyboardEvent): void {
    if (this.isDetailViewVisible) {
      this.closeInvoiceDetails();
    }
  }

  // Handle click outside - no longer needed since we use SearchFilterComponent
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    // Filter dropdown handling is now managed by SearchFilterComponent
  }

  // Map API response to our internal format
  private mapApiResponseToInvoiceDetails(response: any, originalInvoice: Invoice): InvoiceDetails {
    
    // Start with original invoice data as fallback
    const details: InvoiceDetails = {
      id: originalInvoice.id,
      invoiceNumber: originalInvoice.invoiceNumber || '',
      totalAmount: originalInvoice.totalAmount || 0,
      isPaid: originalInvoice.isPaid,
      isOverdue: originalInvoice.isOverdue,
      type: originalInvoice.type || 'invoice',
      paymentReference: originalInvoice.paymentReference || '',
      currency: originalInvoice.currency || 'EUR',
      customer: originalInvoice.customer || 'Unknown Customer',
      status: originalInvoice.status, // Preserve original status
      dueDate: originalInvoice.dueDate instanceof Date ? 
        originalInvoice.dueDate : new Date(originalInvoice.dueDate || new Date()),
      invoiceDate: originalInvoice.invoiceDate instanceof Date ? 
        originalInvoice.invoiceDate : 
        (originalInvoice.invoiceDate ? 
          (typeof originalInvoice.invoiceDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(originalInvoice.invoiceDate) ?
            this.parseDateWithoutTimezone(originalInvoice.invoiceDate) : 
            new Date(originalInvoice.invoiceDate)) : 
          undefined), // Use original date if available, will be overridden by API response
      
      // Initialize amount object properly to avoid type errors
      amount: {
        total: originalInvoice.totalAmount || 0,
        tax: Math.round((originalInvoice.totalAmount || 0) * 0.21 * 100) / 100,
        subtotal: Math.round((originalInvoice.totalAmount || 0) / 1.21 * 100) / 100
      },
      companyName: originalInvoice.customer || 'Unknown Customer',
      description: `Invoice ${originalInvoice.paymentReference || originalInvoice.id}`,
      customerId: originalInvoice.customer,
      purchaseOrderNumber: originalInvoice.paymentReference,
      sent: originalInvoice.isPaid
    };
    
    // Override with API data if available
    if (response) {
      if (response.number) details.invoiceNumber = response.number;
      if (response.dueOn) {
        // Check if it's a date-only string and parse without timezone conversion
        if (typeof response.dueOn === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(response.dueOn)) {
          details.dueDate = this.parseDateWithoutTimezone(response.dueOn);
        } else {
          details.dueDate = new Date(response.dueOn);
        }
      }
      if (response.date) {
        // Check if it's a date-only string and parse without timezone conversion
        if (typeof response.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(response.date)) {
          details.invoiceDate = this.parseDateWithoutTimezone(response.date);
        } else {
          details.invoiceDate = new Date(response.date);
        }
      }
      if (response.paid_at) details.paymentDate = new Date(response.paid_at);
      if (response.status) {
        details.isPaid = response.status === 'paid' || response.status === 'matched';
        // Update status text if we have API data, but preserve original status if it's more accurate
        if (response.status === 'paid' || response.status === 'matched') {
          details.status = 'Paid';
        } else if (response.status === 'overdue' || originalInvoice.isOverdue) {
          details.status = 'Overdue';
        } else {
          details.status = 'Outstanding';
        }
      } else {
        // If no API status, use original invoice status logic
        if (originalInvoice.isPaid) {
          details.status = 'Paid';
        } else if (originalInvoice.isOverdue) {
          details.status = 'Overdue';
        } else {
          details.status = 'Outstanding';
        }
      }
      
      // Handle total amounts with proper initialization and type safety
      if (response.total && details.amount) {
        // Ensure amount object exists and is initialized
        const amount = details.amount;
        
        if (response.total.tax_inclusive && response.total.tax_inclusive.amount) {
          amount.total = parseFloat(response.total.tax_inclusive.amount);
        }
        
        if (response.total.tax_exclusive && response.total.tax_exclusive.amount) {
          amount.subtotal = parseFloat(response.total.tax_exclusive.amount);
        }
        
        // Calculate tax amount with null checks
        if (amount && amount.total && amount.subtotal) {
          amount.tax = amount.total - amount.subtotal;
        }
        
        // Update total amount from the amount object
        if (amount && amount.total) {
          details.totalAmount = amount.total;
        }
      }
    }
    
    // If no invoice date was set from API response, try to get it from the detailed API
    if (details && !details.invoiceDate) {
      // The invoice date should have been set from response.date above
      // If it's still not set, it means the API doesn't provide the actual invoice date
      // In this case, we'll show a placeholder or try to fetch from detailed endpoint
      console.warn(`No invoice date available for invoice ${details.id} from API`);
      
      // As a last resort, use current date to avoid showing wrong calculated dates
      details.invoiceDate = new Date();
    }
    
    return details;
  }

  // Download invoice
  downloadInvoice(invoice: Invoice | InvoiceDetails, format: string = 'pdf'): void {

    
    const companyId = this.getApiCompanyId();
    const invoiceId = invoice.id;
    
    if (!companyId || !invoiceId) {
      alert('Cannot download invoice: Missing required information');
      return;
    }
    
    // Try direct download for all invoices
    const downloadUrl = `${this.apiService['environmentService'].apiUrl}/teamleader/finance/company/${companyId}/invoice/${invoiceId}/download?format=${format}&redirect=true`;
    

          // First, check if we can find this invoice in the system
    this.apiService.get(`teamleader/finance/company/${companyId}/invoices/${invoiceId}`)
      .pipe(
        catchError(error => {
          alert(`Invoice ${invoiceId} not found in the system. This might be why download fails.`);
          return of(null);
        })
      )
      .subscribe(invoiceDetails => {
        if (invoiceDetails) {
          
          // Now try the download
          this.apiService.get(`teamleader/finance/company/${companyId}/invoice/${invoiceId}/download?format=${format}`)
            .pipe(
              catchError(error => {
                if (error.status === 404) {
                  alert(`This invoice (${invoiceId}) exists in the system but is not available for download. It may not have been processed yet or may not be available in TeamLeader.`);
                } else {
                  alert('Error downloading invoice. Please try again later.');
                }
                return of(null);
              })
            )
            .subscribe(response => {
              if (response && (response as any).location) {
                // If we get a response with a location, redirect to it
                window.open((response as any).location, '_blank');
              } else if (response) {
                // If we get a response but no location, try the direct URL
                window.open(downloadUrl, '_blank');
              }
              // If response is null, the error was already handled in catchError
                         });
         }
       });
  }

  // Download credit note in the specified format
  downloadCreditNote(creditNote: CreditNote, format: string = 'pdf'): void {
    const companyId = this.getApiCompanyId();
    if (!companyId) {
      return;
    }

    // Try to use credit note ID first, fallback to number if needed
    const creditNoteIdentifier = creditNote.id || creditNote.creditNoteNumber || creditNote.number;
    
    if (!creditNoteIdentifier) {
      alert('Cannot download credit note: No valid identifier found');
      return;
    }

    // Construct URL for credit note download with company context
    const downloadUrl = `${this.apiService['environmentService'].apiUrl}/teamleader/finance/company/${companyId}/credit-note/${creditNoteIdentifier}/download?format=${format}&redirect=true`;
    
    // Try to open the URL and log any issues
    try {
      window.open(downloadUrl, '_blank');
    } catch (error) {
      // Handle error silently or show user-friendly message
    }
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
      note && 
      note.relatedInvoiceId && 
      note.relatedInvoiceId === invoiceId &&
      note.totalAmount && 
      note.totalAmount > 0
    );
  }

  // Sort invoices based on current sort field and direction
  private sortInvoices(): void {
    const sortFn = (a: Invoice, b: Invoice) => {
      let valA: any, valB: any;
      
      // Handle different column types
      switch (this.sortColumn) {
        case 'totalAmount':
          valA = a.totalAmount;
          valB = b.totalAmount;
          break;
        case 'dueDate':
          valA = a.dueDate instanceof Date ? a.dueDate : new Date(a.dueDate || 0);
          valB = b.dueDate instanceof Date ? b.dueDate : new Date(b.dueDate || 0);
          break;
        case 'status':
          valA = a.status || (a.isPaid ? 'Paid' : (a.isOverdue ? 'Overdue' : 'Outstanding'));
          valB = b.status || (b.isPaid ? 'Paid' : (b.isOverdue ? 'Overdue' : 'Outstanding'));
          break;
        case 'paymentReference':
          valA = a.paymentReference || '';
          valB = b.paymentReference || '';
          break;
        default:
          valA = a[this.sortColumn as keyof Invoice];
          valB = b[this.sortColumn as keyof Invoice];
      }
      
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
    
    // Sort the filtered invoices list directly
    this.filteredInvoices.sort(sortFn);
    
    // Also update the source arrays to maintain consistency
    this.outstandingInvoices.sort(sortFn);
    this.paidInvoices.sort(sortFn);
  }

  // Helper to safely format amounts with 2 decimal places
  formatAmount(amount?: number): string {
    if (amount === null || amount === undefined || isNaN(amount)) {
      return '0.00';
    }
    return amount.toFixed(2);
  }

  // Get status class for styling
  getStatusClass(invoice: any): string {
    if (!invoice || !invoice.status) return '';
    
    const status = invoice.status.toLowerCase();
    
    if (status.includes('overdue')) {
      return 'overdue';
    }
    if (status.includes('paid')) {
      return 'paid';
    }
    if (status.includes('outstanding')) {
      return 'outstanding';
    }
    return 'outstanding'; // Default for unpaid invoices
  }

  // Check if any filters are active
  hasActiveFilters(): boolean {
    return !!(
      this.searchText || 
      (this.statusFilter && this.statusFilter !== 'all')
    );
  }

  // Clean up on destroy
  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
    this.uiStateService.setDetailViewOpen(false);
    
    // Clean up mobile body class if detail panel was open
    if (this.isDetailViewVisible) {
      this.renderer.removeClass(document.body, 'modal-open');
    }
  }

  // UI helpers
  getSortIcon(column: string): string {
    if (this.sortColumn !== column) {
      return 'fa-sort';
    }
    return this.sortDirection === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  initializeFilterModels(): void {
    // Filter models are now handled by SearchFilterComponent
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

  // Event handlers for standardized components
  onHeaderAction(action: PageAction): void {
    // No header actions currently available
  }

  onSort(event: SortEvent): void {
    this.sortColumn = event.column;
    this.sortDirection = event.direction;
    this.sortInvoices();
  }

  onTableAction(event: { action: string, item: any }): void {
    switch (event.action) {
      case 'download':
        this.downloadInvoice(event.item);
        break;
    }
  }

  onRowClick(invoice: Invoice): void {
    this.showInvoiceDetails(invoice);
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

  // Parse date without timezone conversion for date-only strings (YYYY-MM-DD)
  private parseDateWithoutTimezone(dateString: string): Date {
    if (!dateString) {
      return new Date();
    }
    
    try {
      // For date-only strings like "2025-04-09", parse as local date to avoid timezone conversion
      const parts = dateString.split('-');
      if (parts.length === 3) {
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10) - 1; // Month is 0-indexed
        const day = parseInt(parts[2], 10);
        return new Date(year, month, day);
      }
      
      // Fallback to regular parsing
      return new Date(dateString);
    } catch (e) {
      return new Date();
    }
  }

  // Toggle the download options dropdown
  toggleDownloadOptions(event: Event): void {
    event.stopPropagation(); // Prevent the click from closing the detail panel
    this.showDownloadOptions = !this.showDownloadOptions;
    
    // Close the dropdown when clicking outside
    if (this.showDownloadOptions) {
      setTimeout(() => {
        const closeDropdown = () => {
          this.showDownloadOptions = false;
          document.removeEventListener('click', closeDropdown);
        };
        document.addEventListener('click', closeDropdown);
      }, 0);
    }
  }

  // Load credit notes for an invoice
  private loadCreditNotesForInvoice(invoiceId: string): void {
    const companyId = this.getApiCompanyId();
    if (!companyId) {
      return;
    }

    this.apiService.get<CreditNote[]>(`teamleader/finance/company/${companyId}/invoices/${invoiceId}/credit-notes`)
      .pipe(
        catchError(error => {
          return of([]);
        })
      )
      .subscribe(creditNotes => {
        
        // If no credit notes are returned, it's normal
        if (creditNotes.length === 0) {
          // No credit notes found
        }
        
        // Filter credit notes for this specific invoice and process dates
        const invoiceCreditNotes = creditNotes.filter(note => note.relatedInvoiceId === invoiceId).map(note => {
          
          // Process date from API fields - use credit_note_date first, then created_at as fallback
          let creditNoteDate: Date | undefined = undefined;
          
          if ((note as any).credit_note_date) {
            creditNoteDate = this.parseDateWithoutTimezone((note as any).credit_note_date);
          } else if ((note as any).created_at) {
            creditNoteDate = new Date((note as any).created_at);
          } else if (note.date) {
            // Parse date without timezone conversion for date-only strings
            if (typeof note.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(note.date)) {
              creditNoteDate = this.parseDateWithoutTimezone(note.date);
            } else {
              creditNoteDate = typeof note.date === 'string' ? new Date(note.date) : note.date;
            }
          }
          
          // Set the processed date
          if (creditNoteDate && !isNaN(creditNoteDate.getTime())) {
            note.date = creditNoteDate;
          } else {
            console.warn('No valid date found for credit note:', note.id);
            note.date = new Date(); // Fallback to current date
          }
          
          // Ensure totalAmount is set from alternative fields if needed
          if ((!note.totalAmount || note.totalAmount === 0) && note.total) {
            note.totalAmount = note.total;
          }
          
          // Ensure creditNoteNumber is set from alternative fields if needed  
          if (!note.creditNoteNumber && note.number) {
            note.creditNoteNumber = note.number;
          }
          
          return note;
        });
        
        // Clear any existing credit notes for this invoice and add new ones
        this.allCreditNotes = this.allCreditNotes.filter(note => note.relatedInvoiceId !== invoiceId);
        this.allCreditNotes.push(...invoiceCreditNotes);
      });
  }
}
