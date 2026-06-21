import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {EtlRequest, EtlResponse, ExportRequest, ExportResponse} from '../models/etl.model';

@Injectable({providedIn: 'root'})
export class EtlService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/scrape-results`;

  applyEtl(scrapeResultId: string, request: EtlRequest): Observable<EtlResponse> {
    return this.http
      .post<ApiResponse<EtlResponse>>(`${this.base}/${scrapeResultId}/etl`, request)
      .pipe(map(r => r.data));
  }

  applyAndSave(scrapeResultId: string, request: EtlRequest): Observable<EtlResponse> {
    return this.http
      .post<ApiResponse<EtlResponse>>(`${this.base}/${scrapeResultId}/etl/apply-and-save`, request)
      .pipe(map(r => r.data));
  }

  exportRecords(scrapeResultId: string, request: ExportRequest): Observable<ExportResponse> {
    return this.http
      .post<ApiResponse<ExportResponse>>(`${this.base}/${scrapeResultId}/export`, request)
      .pipe(map(r => r.data));
  }
}
