import {inject, Injectable, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../models/api-response.model';
import {
  ChangePasswordRequest,
  CreateUserRequest,
  UpdateUserRequest,
  UserProfile,
  UserResponse
} from '../models/user.model';

@Injectable({providedIn: 'root'})
export class UserService {

  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/v1`;

  profile = signal<UserProfile | null>(null);
  loading = signal(false);

  getMyProfile() {
    this.loading.set(true);
    return this.http.get<ApiResponse<UserProfile>>(`${this.baseUrl}/account`).pipe(
      tap({
        next: res => {
          this.profile.set(res.data);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      })
    );
  }

  changePassword(request: ChangePasswordRequest) {
    return this.http.put<ApiResponse<string>>(
      `${this.baseUrl}/account/security/password`,
      request
    );
  }
  getAllUsers() {
    return this.http.get<ApiResponse<UserResponse[]>>(`${this.baseUrl}/users`);
  }

  getUserById(id: string) {
    return this.http.get<ApiResponse<UserResponse>>(`${this.baseUrl}/users/${id}`);
  }

  createUser(request: CreateUserRequest) {
    return this.http.post<ApiResponse<UserResponse>>(`${this.baseUrl}/users`, request);
  }

  updateUser(id: string, request: UpdateUserRequest) {
    return this.http.patch<ApiResponse<UserResponse>>(`${this.baseUrl}/users/${id}`, request);
  }

  deleteUser(id: string) {
    return this.http.delete<void>(`${this.baseUrl}/users/${id}`);
  }

  updateAvatar(avatarUrl: string) {
    return this.http.patch<ApiResponse<UserProfile>>(
      `${this.baseUrl}/account/avatar`,
      {avatarUrl}
    ).pipe(
      tap(res => this.profile.set(res.data))
    );
  }

}
