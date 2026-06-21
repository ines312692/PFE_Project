import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, map} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  CaptchaIntervention,
  SubmitTokenRequest,
} from '../models/captcha-intervention.model';

@Injectable({providedIn: 'root'})
export class CaptchaInterventionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/captcha-interventions`;

  getPending(jobId: string): Observable<CaptchaIntervention> {
    return this.http
      .get<ApiResponse<CaptchaIntervention>>(`${this.base}/${jobId}`, {
        withCredentials: true,
      })
      .pipe(map(r => r.data));
  }

  submitToken(jobId: string, token: string): Observable<string> {
    const body: SubmitTokenRequest = {token};
    return this.http
      .post<ApiResponse<string>>(`${this.base}/${jobId}/submit`, body, {
        withCredentials: true,
      })
      .pipe(map(r => r.data));
  }

  cancel(jobId: string): Observable<string> {
    return this.http
      .post<ApiResponse<string>>(
        `${this.base}/${jobId}/cancel`,
        {},
        {withCredentials: true}
      )
      .pipe(map(r => r.data));
  }

  confirm(jobId: string) {
    return this.http.post(
      `${this.base}/${jobId}/confirm`,
      {},
      { withCredentials: true }
    );
  }
}
