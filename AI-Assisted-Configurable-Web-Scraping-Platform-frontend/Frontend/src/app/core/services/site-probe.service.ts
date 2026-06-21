import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, catchError, of} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {SiteProbeResponse} from '../models/site-probe.model';

@Injectable({providedIn: 'root'})
export class SiteProbeService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/jobs/probe`;

  probe(url: string): Observable<ApiResponse<SiteProbeResponse> | null> {
    return this.http
      .post<ApiResponse<SiteProbeResponse>>(this.base, {url})
      .pipe(catchError(() => of(null)));
  }
}
