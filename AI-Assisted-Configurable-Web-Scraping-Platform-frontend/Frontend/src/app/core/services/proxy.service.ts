import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {map, Observable} from 'rxjs';

import {ApiResponse} from '../models/api-response.model';
import {
  AntiBotDetectionResult,
  AntiBotProvider,
  CreateProxyRequest, PageResponse,
  Proxy, ProxyCountryStat, ProxyDashboard,
  ProxyHealthCheckResult, ProxySearchParams,
  ProxyStats,
} from '../models/proxy.model';
import {environment} from '../../../environments/environment';

@Injectable({providedIn: 'root'})
export class ProxyService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/proxies`;

  findAll(params: ProxySearchParams = {}): Observable<PageResponse<Proxy>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '' && v !== 'ALL' && v !== 'ANY') {
        httpParams = httpParams.set(k, String(v));
      }
    });
    return this.http
      .get<ApiResponse<PageResponse<Proxy>>>(this.base, {params: httpParams})
      .pipe(map(r => r.data));
  }

  getStats(): Observable<ProxyStats> {
    return this.http
      .get<ApiResponse<ProxyStats>>(`${this.base}/stats`)
      .pipe(map(r => r.data));
  }

  refresh(): Observable<{ added: number; timestampMs: number }> {
    return this.http
      .post<ApiResponse<{ added: number; timestampMs: number }>>(
        `${this.base}/refresh`, {}
      )
      .pipe(map(r => r.data));
  }

  create(payload: CreateProxyRequest): Observable<Proxy> {
    return this.http
      .post<ApiResponse<Proxy>>(this.base, payload)
      .pipe(map(r => r.data));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  blacklist(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/blacklist`, {});
  }

  reactivate(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/reactivate`, {});
  }

  triggerHealthCheck(id: string): Observable<ProxyHealthCheckResult> {
    return this.http
      .post<ApiResponse<ProxyHealthCheckResult>>(
        `${this.base}/${id}/health-check`, {}
      )
      .pipe(map(r => r.data));
  }

  triggerAntiBotCheck(id: string, provider: AntiBotProvider): Observable<AntiBotDetectionResult> {
    const params = new HttpParams().set('provider', provider);
    return this.http
      .post<ApiResponse<AntiBotDetectionResult>>(
        `${this.base}/${id}/antibot-check`, {}, {params}
      )
      .pipe(map(r => r.data));
  }

  listAntiBotProviders(): Observable<AntiBotProvider[]> {
    return this.http
      .get<ApiResponse<AntiBotProvider[]>>(`${this.base}/antibot-providers`)
      .pipe(map(r => r.data));
  }

  triggerFullAntiBotCheck(id: string): Observable<ProxyHealthCheckResult> {
    return this.http
      .post<ApiResponse<ProxyHealthCheckResult>>(`${this.base}/${id}/antibot-check`, {})
      .pipe(map(r => r.data));
  }

  getDashboard(): Observable<ProxyDashboard> {
    return this.http
      .get<ApiResponse<ProxyDashboard>>(`${this.base}/dashboard`)
      .pipe(map(r => r.data));
  }

  getAvailableCountries(): Observable<ProxyCountryStat[]> {
    return this.http
      .get<ApiResponse<ProxyCountryStat[]>>(`${this.base}/countries`)
      .pipe(map(r => r.data));
  }
}
