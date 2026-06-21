import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, map} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  SchemaValidationRequest,
  SchemaValidationResponse,
} from '../models/schema-validation.model';

@Injectable({providedIn: 'root'})
export class SchemaValidatorService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/v1/schema/assist/validate`;

  validate(request: SchemaValidationRequest): Observable<SchemaValidationResponse> {
    return this.http
      .post<ApiResponse<SchemaValidationResponse>>(this.url, request)
      .pipe(map((res) => res.data));
  }
}
