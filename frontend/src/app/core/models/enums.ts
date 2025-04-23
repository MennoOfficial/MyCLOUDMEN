/**
 * User role types
 */
export enum RoleType {
  SYSTEM_ADMIN = 'SYSTEM_ADMIN',
  COMPANY_ADMIN = 'COMPANY_ADMIN',
  COMPANY_USER = 'COMPANY_USER'
}

/**
 * User status types
 */
export enum StatusType {
  ACTIVATED = 'ACTIVATED',
  DEACTIVATED = 'DEACTIVATED',
  PENDING = 'PENDING'
}

/**
 * Company status types
 */
export enum CompanyStatusType {
  ACTIVE = 'ACTIVE',
  DEACTIVATED = 'DEACTIVATED',
  SUSPENDED = 'SUSPENDED'
}

/**
 * Invoice status types
 */
export enum InvoiceStatusType {
  PAID = 'PAID',
  UNPAID = 'UNPAID',
  OVERDUE = 'OVERDUE',
  DRAFT = 'DRAFT',
  SENT = 'SENT'
} 