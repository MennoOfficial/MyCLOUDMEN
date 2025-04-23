import { CompanyStatusType } from './enums';

/**
 * Base interface for companies in list view
 */
export interface CompanyListItem {
  id: string;
  teamleaderId: string;
  name: string;
  status: string | CompanyStatusType;
  primaryAddress?: {
    city?: string;
    country?: string;
  };
  email?: string;
  website?: string;
  contactInfo?: ContactInfo[];
  primaryDomain?: string;
  syncedAt?: string | Date;
  hasMyCloudmenAccess?: boolean;
  phoneNumber?: string;
  vatNumber?: string;
}

/**
 * Interface for address information
 */
export interface CompanyAddress {
  line1?: string;
  line2?: string;
  postalCode?: string;
  city?: string;
  country?: string;
  type?: string;
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
  address?: CompanyAddress;
  vatNumber?: string;
  businessType?: string;
  createdAt?: string | Date;
  updatedAt?: string | Date;
  customFields?: Record<string, any>;
}

/**
 * Interface for paginated company list response
 */
export interface CompanyListResponse {
  companies: CompanyListItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  totalItems?: number;
}

/**
 * Simplified company information for display in UI components
 */
export interface Company {
  id: string;        // MongoDB ID
  teamleaderId?: string;  // Teamleader ID with dashes
  name: string;
  email?: string;
} 