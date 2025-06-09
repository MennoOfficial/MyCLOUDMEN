/**
 * Base invoice model
 */
export interface Invoice {
  id: string;
  invoiceNumber: string;  // Mapped from id
  dueDate: string | Date; // Mapped from dueOn
  totalAmount: number;    // Mapped from total
  paymentReference: string;
  currency: string;
  isPaid: boolean;
  isOverdue: boolean;
  type: string;           // Always 'invoice'
  customer?: string;      // Added in processInvoicesData
  status?: string;        // Added for badge display
}

/**
 * Credit note model
 */
export interface CreditNote {
  id: string;
  creditNoteNumber: string;
  date: string | Date;
  totalAmount: number;
  status: string;
  relatedInvoiceId: string;
  type: string;
  
  // Additional fields from backend API
  number?: string;        // Alternative field name from API
  total?: number;         // Alternative field name for amount
  currency?: string;      // Currency code
  invoiceId?: string;     // Alternative field name for relatedInvoiceId
}

/**
 * Response format from the Teamleader API (simplified)
 */
export interface TeamleaderInvoiceResponse {
  id: string;
  paymentReference: string;
  dueOn: string;
  total: number;
  currency: string;
  isPaid: boolean;
  isOverdue: boolean;
}

/**
 * Detailed invoice information for display
 */
export interface InvoiceDetails {
  id: string;
  invoiceNumber: string;
  totalAmount: number;
  isPaid: boolean;
  isOverdue: boolean;
  type: string;
  customer?: string;
  paymentReference: string;
  currency: string;
  status?: string;        // Added for badge display
  
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
  customerId?: string; // Customer ID property 
  purchaseOrderNumber?: string; // Purchase order number
  sent?: boolean; // Whether the invoice has been sent
  creditNotes?: Array<{
    id: string;
    number: string;
    creditNoteNumber?: string;
    amount: number;
    date: Date;
    downloadUrl: string;
  }>;
} 