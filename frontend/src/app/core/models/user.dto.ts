export interface UserDTO {
  auth0Id: string;
  email: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  picture?: string;
  provider: string;
  customerGoogleId?: string;
  primaryDomain?: string;
  status: ('ACTIVATED' | 'PENDING' | 'DEACTIVATED' | 'REJECTED');
  roles?: ('SYSTEM_ADMIN' | 'COMPANY_ADMIN' | 'COMPANY_USER')[];
}

export interface UserResponseDTO {
  id: string;
  email: string;
  roles: string[];
  status: string;
  auth0Id: string;
} 