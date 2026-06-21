import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  CreateProjectRequest, Project, ProjectMember, ProjectSearchParams, ProjectStats,
  UpdateMemberRoleRequest, UpdateProjectRequest,
} from '../models/project.model';
import {Job} from '../models/job.model';

@Injectable({providedIn: 'root'})
export class ProjectService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/projects`;

  projects = signal<Project[]>([]);
  selectedProject = signal<Project | null>(null);
  members = signal<ProjectMember[]>([]);
  projectJobs = signal<Job[]>([]);
  loading = signal(false);
  totalElements = signal(0);
  totalPages = signal(1);
  currentPage = signal(0);
  stats = signal<ProjectStats | null>(null);

  search(params: ProjectSearchParams = {}) {
    this.loading.set(true);

    let httpParams = new HttpParams()
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 20))
      .set('sortBy', params.sortBy ?? 'createdAt')
      .set('sortDirection', params.sortDirection ?? 'desc');

    if (params.search?.trim()) httpParams = httpParams.set('search', params.search.trim());
    if (params.status && params.status !== 'ALL') httpParams = httpParams.set('status', params.status);
    if (params.createdFrom) httpParams = httpParams.set('createdFrom', params.createdFrom);
    if (params.createdTo) httpParams = httpParams.set('createdTo', params.createdTo);

    return this.http.get<ApiResponse<Project[]>>(this.base, {params: httpParams}).pipe(
      tap({
        next: res => {
          this.projects.set(res.data ?? []);
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
    return this.http.get<ApiResponse<ProjectStats>>(`${this.base}/stats`).pipe(
      tap(res => this.stats.set(res.data))
    );
  }

  getById(id: string) {
    return this.http.get<ApiResponse<Project>>(`${this.base}/${id}`).pipe(
      tap(res => this.selectedProject.set(res.data))
    );
  }

  create(request: CreateProjectRequest) {
    return this.http.post<ApiResponse<Project>>(this.base, request).pipe(
      tap(res => this.projects.update(list => [res.data, ...list]))
    );
  }

  update(id: string, request: UpdateProjectRequest) {
    return this.http.patch<ApiResponse<Project>>(`${this.base}/${id}`, request).pipe(
      tap(res => {
        this.projects.update(list => list.map(p => p.id === id ? res.data : p));
        if (this.selectedProject()?.id === id) this.selectedProject.set(res.data);
      })
    );
  }

  archive(id: string) {
    return this.http.post<ApiResponse<unknown>>(`${this.base}/${id}/archive`, {}).pipe(
      tap(() => this.projects.update(list =>
        list.map(p => p.id === id ? {...p, status: 'ARCHIVED' as const} : p)
      ))
    );
  }

  delete(id: string) {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(
      tap(() => {
        this.projects.update(list => list.filter(p => p.id !== id));
        if (this.selectedProject()?.id === id) this.selectedProject.set(null);
      })
    );
  }

  getMembers(projectId: string) {
    return this.http.get<ApiResponse<ProjectMember[]>>(`${this.base}/${projectId}/members`).pipe(
      tap(res => this.members.set(res.data ?? []))
    );
  }

  updateMemberRole(projectId: string, userId: string, request: UpdateMemberRoleRequest) {
    return this.http.patch<ApiResponse<ProjectMember>>(
      `${this.base}/${projectId}/members/${userId}`, request
    ).pipe(
      tap(res => this.members.update(list => list.map(m => m.userId === userId ? res.data : m)))
    );
  }

  removeMember(projectId: string, userId: string) {
    return this.http.delete<void>(`${this.base}/${projectId}/members/${userId}`).pipe(
      tap(() => this.members.update(list => list.filter(m => m.userId !== userId)))
    );
  }

  getProjectJobs(projectId: string) {
    return this.http.get<ApiResponse<Job[]>>(`${this.base}/${projectId}/jobs`).pipe(
      tap(res => this.projectJobs.set(res.data ?? []))
    );
  }
}
