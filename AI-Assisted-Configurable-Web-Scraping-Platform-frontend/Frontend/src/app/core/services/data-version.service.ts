import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {Observable, map} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {DataVersion, DataVersionSummary} from '../models/data-version.model';

@Injectable({providedIn: 'root'})
export class DataVersionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/scrape-results`;

  list(scrapeResultId: string): Observable<DataVersionSummary[]> {
    return this.http
      .get<ApiResponse<DataVersionSummary[]>>(`${this.base}/${scrapeResultId}/versions`)
      .pipe(map(r => r.data));
  }

  get(scrapeResultId: string, versionNumber: number): Observable<DataVersion> {
    return this.http
      .get<ApiResponse<DataVersion>>(`${this.base}/${scrapeResultId}/versions/${versionNumber}`)
      .pipe(map(r => r.data));
  }

  rollback(scrapeResultId: string, versionNumber: number): Observable<DataVersion> {
    return this.http
      .post<ApiResponse<DataVersion>>(
        `${this.base}/${scrapeResultId}/versions/${versionNumber}/rollback`, {})
      .pipe(map(r => r.data));
  }
}
