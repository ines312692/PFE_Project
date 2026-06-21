import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  ActionPlanRequest, ActionPlanResponse,
  CreateJobRequest, Job, JobSearchParams, JobStats, UpdateJobRequest
} from '../models/job.model';
import {ScrapeResultResponse} from '../models/scrape-result.model';

@Injectable({providedIn: 'root'})
export class JobService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/jobs`;

  jobs = signal<Job[]>([]);
  selectedJob = signal<Job | null>(null);
  loading = signal(false);
  totalElements = signal(0);
  totalPages = signal(1);
  currentPage = signal(0);
  stats = signal<JobStats | null>(null);

  search(params: JobSearchParams = {}) {
    this.loading.set(true);

    let httpParams = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20))
      .set('sortBy', params.sortBy ?? 'createdAt')
      .set('sortDirection', params.sortDirection ?? 'desc');

    if (params.search?.trim()) httpParams = httpParams.set('search', params.search.trim());
    if (params.status && params.status !== 'ALL') httpParams = httpParams.set('status', params.status);
    if (params.priority && params.priority !== 'ALL') httpParams = httpParams.set('priority', params.priority);
    if (params.triggerType && params.triggerType !== 'ALL') httpParams = httpParams.set('triggerType', params.triggerType);
    if (params.jobType && params.jobType !== 'ALL') httpParams = httpParams.set('jobType', params.jobType);
    if (params.createdFrom) httpParams = httpParams.set('createdFrom', params.createdFrom);
    if (params.createdTo) httpParams = httpParams.set('createdTo', params.createdTo);

    return this.http.get<ApiResponse<Job[]>>(this.base, {params: httpParams}).pipe(
      tap({
        next: res => {
          this.jobs.set(res.data ?? []);
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
    return this.http.get<ApiResponse<JobStats>>(`${this.base}/stats`).pipe(
      tap(res => this.stats.set(res.data))
    );
  }

  getAll() {
    return this.search();
  }

  getById(id: string) {
    return this.http.get<ApiResponse<Job>>(`${this.base}/${id}`).pipe(
      tap(res => this.selectedJob.set(res.data))
    );
  }

  create(request: CreateJobRequest) {
    return this.http.post  <ApiResponse<Job>>(this.base, request);
  }

  update(id: string, r: UpdateJobRequest) {
    return this.http.patch <ApiResponse<Job>>(`${this.base}/${id}`, r);
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  launch(id: string) {
    return this.http.post  <ApiResponse<Job>>(`${this.base}/${id}/launch`, {});
  }

  pause(id: string) {
    return this.http.post  <ApiResponse<Job>>(`${this.base}/${id}/pause`, {});
  }

  duplicate(id: string) {
    return this.http.post  <ApiResponse<Job>>(`${this.base}/${id}/duplicate`, {});
  }

  getResults(id: string) {
    return this.http.get   <ApiResponse<ScrapeResultResponse>>(`${this.base}/${id}/results`);
  }

  getHistory(id: string) {
    return this.http.get   <ApiResponse<ScrapeResultResponse[]>>(`${this.base}/${id}/history`);
  }

  planActions(req: ActionPlanRequest): Observable<ApiResponse<ActionPlanResponse>> {
    return this.http.post<ApiResponse<ActionPlanResponse>>(
      `${this.base}/action-planner`, req);
  }
}
