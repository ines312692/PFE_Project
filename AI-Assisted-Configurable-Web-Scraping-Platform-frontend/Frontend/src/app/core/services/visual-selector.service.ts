import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {map} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';

export interface SnapshotRequest {
  url: string;
  stealthMode: boolean;
  waitForSelector?: string;
}

export interface SnapshotResponse {
  finalUrl: string;
  html: string;
  title: string;
  htmlLength: number;
}

export interface HardenSelectorRequest {
  html: string;
  candidateSelector: string;
  url?: string;
  expectList: boolean;
}

export interface HardenedSelectorResponse {
  cssSelector: string;
  xpath: string;
  matchCount: number;
  unique: boolean;
  previewValues: string[];
  suggestedName: string;
  warning?: string;
}

@Injectable({providedIn: 'root'})
export class VisualSelectorService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/visual-selector`;

  renderSnapshot(request: SnapshotRequest) {
    return this.http
      .post<ApiResponse<SnapshotResponse>>(`${this.base}/snapshot`, request)
      .pipe(map(res => res.data));
  }

  hardenSelector(request: HardenSelectorRequest) {
    return this.http
      .post<ApiResponse<HardenedSelectorResponse>>(`${this.base}/harden`, request)
      .pipe(map(res => res.data));
  }
}
