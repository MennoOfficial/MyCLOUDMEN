import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { EnvironmentService } from './environment.service';

// Types
interface PurchaseRequest {
  id: string;
  type: string;
  quantity: number;
  cost: number;
  requestDate: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
}

interface PurchaseRequestResponse {
  id: string;
  type: string;
  licenseType?: string;
  quantity?: number;
  domain?: string;
  cost?: number;
  userEmail: string;
  requestDate: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
}

interface PaginatedResponse<T> {
  items: T[];
  currentPage: number;
  totalItems: number;
  totalPages: number;
}

interface RequestStatusResponse {
  id: string;
  status: 'PENDING' | 'AWAITING_CONFIRMATION' | 'APPROVED' | 'REJECTED';
  type: 'credits' | 'licenses';
  licenseType?: string;
  quantity?: number;
  domain?: string;
  userEmail?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PurchaseRequestService {
  private readonly apiUrl: string;

  constructor(
    private http: HttpClient,
    private environmentService: EnvironmentService
  ) {
    this.apiUrl = this.environmentService.apiUrl;
  }

  /**
   * Fetch pending purchase requests
   */
  fetchPendingRequests(): Observable<PurchaseRequest[]> {
    return this.http.get<PaginatedResponse<PurchaseRequestResponse>>(`${this.apiUrl}/purchase/requests`)
      .pipe(
        map(response => response.items.map(item => this.transformPurchaseRequest(item))),
        catchError(error => {
          return of([]);
        })
      );
  }

  /**
   * Accept a purchase request
   */
  acceptPurchase(requestId: string): Observable<any> {
    const url = `${this.apiUrl}/purchase/accept?requestId=${requestId}`;
    return this.http.get(url, { responseType: 'text' });
  }

  /**
   * Accept a Google Workspace license request
   */
  acceptGoogleWorkspaceLicense(requestId: string, customerId: string): Observable<any> {
    const url = `${this.apiUrl}/purchase/license/accept/signature-satori-credits?requestId=${requestId}&customerId=${customerId}`;
    return this.http.get(url, { responseType: 'text' });
  }

  /**
   * Check the status of a request
   */
  checkRequestStatus(requestId: string): Observable<RequestStatusResponse> {
    const url = `${this.apiUrl}/purchase/status?requestId=${requestId}`;
    return this.http.get<RequestStatusResponse>(url);
  }

  /**
   * Purchase signature credits
   */
  purchaseCredits(quantity: number, userEmail: string): Observable<any> {
    const requestBody = {
      quantity: quantity,
      userEmail: userEmail
    };

    return this.http.post(`${this.apiUrl}/purchase/signature-satori-credits`, requestBody);
  }

  /**
   * Purchase Google Workspace licenses
   */
  purchaseLicenses(licenseType: string, quantity: number, domain: string, userEmail: string, skuId?: string): Observable<any> {
    const requestBody = {
      licenseType: licenseType,
      quantity: quantity,
      domain: domain,
      userEmail: userEmail,
      ...(skuId && { skuId })
    };

    return this.http.post(`${this.apiUrl}/purchase/google-workspace-license`, requestBody);
  }

  /**
   * Fetch Google Workspace SKUs
   */
  fetchAvailableSkus(): Observable<any[]> {
    return this.http.get<any>(`${this.apiUrl}/google-workspace/skus`)
      .pipe(
        map(response => response.skus || []),
        catchError(error => {
          return of([]);
        })
      );
  }

  /**
   * Transform purchase request response to internal format
   */
  private transformPurchaseRequest(item: PurchaseRequestResponse): PurchaseRequest {
    return {
      id: item.id,
      type: this.formatRequestType(item),
      quantity: item.quantity || 0,
      cost: item.cost || this.calculateCost(item),
      requestDate: item.requestDate,
      status: item.status
    };
  }

  /**
   * Format request type for display
   */
  private formatRequestType(request: PurchaseRequestResponse): string {
    if (request.type === 'licenses' && request.licenseType) {
      return request.licenseType.replace('Google Workspace ', '');
    } else if (request.type === 'credits') {
      return 'Signature Credits';
    } else {
      return request.type || 'Unknown';
    }
  }

  /**
   * Calculate cost for a request
   */
  private calculateCost(request: PurchaseRequestResponse): number {
    if (request.cost && request.cost > 0) {
      return request.cost;
    }

    if (request.type === 'licenses' && request.licenseType && request.quantity) {
      return this.getLicensePriceValue(request.licenseType) * request.quantity;
    }

    if (request.type === 'credits' && request.quantity) {
      const basePrice = 0.90;
      const discountPercent = 10;
      const discountedPrice = basePrice * (1 - discountPercent / 100);
      return request.quantity * discountedPrice;
    }

    return 0;
  }

  /**
   * Get license price value
   */
  private getLicensePriceValue(licenseType: string): number {
    const prices: { [key: string]: number } = {
      'Google Workspace Business Starter': 6,
      'Google Workspace Business Standard': 12,
      'Google Workspace Business Plus': 18,
      'Google Workspace Enterprise Standard': 20,
      'Google Workspace Enterprise Plus': 24
    };

    return prices[licenseType] || 0;
  }

  /**
   * Get service name for display
   */
  static getServiceName(type: string): string {
    if (type?.toLowerCase().includes('license') || type?.toLowerCase().includes('workspace')) {
      return 'Workspace';
    } else if (type?.toLowerCase().includes('credit') || type?.toLowerCase().includes('signature')) {
      return 'Signature';
    }
    return 'Unknown Service';
  }

  /**
   * Get service logo URL
   */
  static getServiceLogo(type: string): string {
    if (type?.toLowerCase().includes('license') || type?.toLowerCase().includes('workspace')) {
      return 'https://crystalpng.com/wp-content/uploads/2025/05/google-logo.png';
    } else if (type?.toLowerCase().includes('credit') || type?.toLowerCase().includes('signature')) {
      return 'https://www.cobry.co.uk/wp-content/uploads/2022/01/Asset-1satori-300x300-1.png';
    }
    return '';
  }
} 