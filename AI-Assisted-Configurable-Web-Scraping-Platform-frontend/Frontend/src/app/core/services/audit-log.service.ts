import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable, signal} from '@angular/core';
import {forkJoin, tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  AuditLog,
  AuditLogSearchParams,
  AuditLogStats,
} from '../models/audit-log.model';

@Injectable({providedIn: 'root'})
export class AuditLogService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/audit-logs`;

  logs = signal<AuditLog[]>([]);
  selectedLog = signal<AuditLog | null>(null);
  loading = signal(false);
  totalElements = signal(0);
  totalPages = signal(1);
  currentPage = signal(0);
  stats = signal<AuditLogStats | null>(null);

  search(params: AuditLogSearchParams = {}) {
    this.loading.set(true);

    let httpParams = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20))
      .set('sortBy', params.sortBy ?? 'occurredAt')
      .set('sortDirection', params.sortDirection ?? 'desc');

    if (params.search?.trim()) httpParams = httpParams.set('search', params.search.trim());
    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.action && params.action !== 'ALL') httpParams = httpParams.set('action', params.action);
    if (params.resourceType && params.resourceType !== 'ALL') httpParams = httpParams.set('resourceType', params.resourceType);
    if (params.resourceId) httpParams = httpParams.set('resourceId', params.resourceId);
    if (params.outcome && params.outcome !== 'ALL') httpParams = httpParams.set('outcome', params.outcome);
    if (params.occurredFrom) httpParams = httpParams.set('occurredFrom', params.occurredFrom);
    if (params.occurredTo) httpParams = httpParams.set('occurredTo', params.occurredTo);

    return this.http.get<ApiResponse<AuditLog[]>>(this.base, {params: httpParams}).pipe(
      tap({
        next: res => {
          this.logs.set(res.data ?? []);
          this.totalElements.set(res.pagination?.totalElements ?? 0);
          this.totalPages.set(res.pagination?.totalPages ?? 1);
          this.currentPage.set(res.pagination?.page ?? 0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      })
    );
  }

  getStats() {
    return this.http.get<ApiResponse<AuditLogStats>>(`${this.base}/stats`).pipe(
      tap(res => this.stats.set(res.data))
    );
  }

  getById(id: string) {
    return this.http.get<ApiResponse<AuditLog>>(`${this.base}/${id}`).pipe(
      tap(res => this.selectedLog.set(res.data))
    );
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  deleteBulk(ids: string[]) {
    return forkJoin(ids.map(id => this.http.delete<void>(`${this.base}/${id}`)));
  }
}
