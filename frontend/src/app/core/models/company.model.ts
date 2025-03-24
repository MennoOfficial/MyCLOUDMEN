/**
 * Base interface for companies in list view
 */
export interface CompanyListItem {
  id: string;
  teamleaderId: string;
  name: string;
  email: string;
  phoneNumber: string;
  vatNumber: string;
  status: string;
  syncedAt: string;
}

/**
 * Interface for address information
 */
export interface CompanyAddress {
  line1: string;
  line2?: string;
  postalCode: string;
  city: string;
  country: string;
}

/**
 * Interface for contact information
 */
export interface ContactInfo {
  type: string;
  value: string;
}

/**
 * Interface for detailed company information
 */
export interface CompanyDetail extends CompanyListItem {
  website?: string;
  businessType?: string;
  primaryAddress?: CompanyAddress;
  primaryDomain?: string;
  contactInfo?: ContactInfo[];
  customFields?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

/**
 * Interface for paginated company list response
 */
export interface CompanyListResponse {
  companies: CompanyListItem[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
} 