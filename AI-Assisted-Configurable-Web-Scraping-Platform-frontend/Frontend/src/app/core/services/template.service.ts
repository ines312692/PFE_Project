import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {ApiResponse} from '../models/api-response.model';
import {environment} from '../../../environments/environment';
import {JobTemplate} from '../models/job-templates.model';

@Injectable({providedIn: 'root'})
export class TemplateService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/templates`;

  getAll(): Observable<JobTemplate[]> {
    return this.http
      .get<ApiResponse<JobTemplate[]>>(this.base)
      .pipe(map(r => r.data));
  }

  getById(id: string): Observable<JobTemplate> {
    return this.http
      .get<ApiResponse<JobTemplate>>(`${this.base}/${id}`)
      .pipe(map(r => r.data));
  }
}
