/**
 * Available user role types in the system
 */
export type UserRole = 'SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER';

/**
 * Available user status types in the system
 */
export type UserStatus = 'ACTIVATED' | 'DEACTIVATED' | 'PENDING' | 'REJECTED';

/**
 * User profile data model
 */
export interface User {
  id?: string;
  auth0Id?: string;
  email?: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  picture?: string;
  roles: UserRole[];
  status?: UserStatus;
  company?: any;
  companyInfo?: any;
}