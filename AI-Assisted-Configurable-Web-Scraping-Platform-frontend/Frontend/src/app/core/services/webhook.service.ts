import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  WebhookCredentialsResponse,
  WebhookDeliveryResponse,
  WebhookInfoResponse
} from '../models/webhook.model';

@Injectable({providedIn: 'root'})
export class WebhookService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/jobs`;

  getInfo(jobId: string) {
    return this.http.get<ApiResponse<WebhookInfoResponse>>(
      `${this.base}/${jobId}/webhook`);
  }

  rotateSecret(jobId: string) {
    return this.http.post<ApiResponse<WebhookCredentialsResponse>>(
      `${this.base}/${jobId}/webhook/rotate`, {});
  }

  getDeliveries(jobId: string, page = 0, size = 20) {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<ApiResponse<WebhookDeliveryResponse[]>>(
      `${this.base}/${jobId}/webhook/deliveries`, {params});
  }
}
