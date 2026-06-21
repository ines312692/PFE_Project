import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiResponse} from '../models/api-response.model';
import {ScrapeResultResponse} from '../models/scrape-result.model';
import {environment} from '../../../environments/environment';

@Injectable({providedIn: 'root'})
export class ScrapeResultService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/scrape-results`;

  getLatestByJobId(jobId: string): Observable<ApiResponse<ScrapeResultResponse>> {
    return this.http.get<ApiResponse<ScrapeResultResponse>>(
      `${this.base}/job/${jobId}/latest`
    );
  }

  getAllByJobId(jobId: string): Observable<ApiResponse<ScrapeResultResponse[]>> {
    return this.http.get<ApiResponse<ScrapeResultResponse[]>>(
      `${this.base}/job/${jobId}`
    );
  }
}
