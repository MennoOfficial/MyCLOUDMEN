import { RoleType, StatusType } from './enums';

/**
 * Company user model for displaying users in tables and lists
 */
export interface CompanyUser {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  lastLogin?: string;
  picture?: string;
}

/**
 * Pending user model for handling user approval workflows
 */
export interface PendingUser {
  id: string;
  name: string;
  email: string;
  requestedAt: string;
  status?: string; 
  primaryDomain: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
  picture?: string;
  auth0Id?: string;
  dateTimeAdded?: string;
  dateTimeChanged?: string;
}

/**
 * Selected user model for user detail views
 */
export interface SelectedUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role?: string;
  status?: string;
  picture?: string;
  requestedAt?: string;
  primaryDomain?: string;
  roles?: string[];
  lastLogin?: string;
}

/**
 * Authentication log entry model
 */
export interface AuthenticationLog {
  id: string;
  userId: string;
  email: string;
  primaryDomain: string;
  googleUniqueId: string;
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  failureReason: string;
  successful: boolean;
}

/**
 * Pagination response envelope for API results
 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
} 


/**
 * User model for authentication and authorization
 */
export interface User {
  id?: string;
  email?: string;
  status?: 'PENDING' | 'ACTIVE' | 'INACTIVE';
  roles?: string[];
}

