import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';

export interface UpcomingRun {
  jobId: string;
  jobName: string;
  fireAt: string;
}

export interface NextRunsPreview {
  cron: string;
  next: string[];
}

@Injectable({providedIn: 'root'})
export class ScheduleService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/jobs/schedule`;

  upcoming(from: Date, to: Date) {
    const params = new HttpParams()
      .set('from', from.toISOString())
      .set('to', to.toISOString());
    return this.http.get<ApiResponse<UpcomingRun[]>>(`${this.base}/upcoming`, {params});
  }

  preview(cron: string, count = 5) {
    const params = new HttpParams().set('cron', cron).set('count', String(count));
    return this.http.get<ApiResponse<NextRunsPreview>>(`${this.base}/preview`, {params});
  }
}
