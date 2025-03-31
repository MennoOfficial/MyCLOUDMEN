import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EnvironmentService } from './environment.service';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  constructor(
    private http: HttpClient,
    private environmentService: EnvironmentService
  ) {}

  // Normalize endpoint by removing leading slash if present
  private normalizeEndpoint(endpoint: string): string {
    return endpoint.startsWith('/') ? endpoint.substring(1) : endpoint;
  }

  // GET request
  get<T>(endpoint: string): Observable<T> {
    const normalizedEndpoint = this.normalizeEndpoint(endpoint);
    return this.http.get<T>(`${this.environmentService.apiUrl}/${normalizedEndpoint}`);
  }

  // POST request
  post<T>(endpoint: string, data: any): Observable<T> {
    const normalizedEndpoint = this.normalizeEndpoint(endpoint);
    return this.http.post<T>(`${this.environmentService.apiUrl}/${normalizedEndpoint}`, data);
  }

  // PUT request
  put<T>(endpoint: string, data: any): Observable<T> {
    const normalizedEndpoint = this.normalizeEndpoint(endpoint);
    return this.http.put<T>(`${this.environmentService.apiUrl}/${normalizedEndpoint}`, data);
  }

  // DELETE request
  delete<T>(endpoint: string): Observable<T> {
    const normalizedEndpoint = this.normalizeEndpoint(endpoint);
    return this.http.delete<T>(`${this.environmentService.apiUrl}/${normalizedEndpoint}`);
  }
}