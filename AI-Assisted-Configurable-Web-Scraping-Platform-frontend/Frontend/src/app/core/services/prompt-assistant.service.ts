import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  PromptAssistRequest,
  PromptAssistResponse,
} from '../models/prompt-assistant.model';


@Injectable({ providedIn: 'root' })
export class PromptAssistantService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiUrl}/v1/schema/assist`;

  assist(request: PromptAssistRequest): Observable<PromptAssistResponse> {
    return this.http
      .post<ApiResponse<PromptAssistResponse>>(this.endpoint, request)
      .pipe(map((res) => res.data));
  }
}
