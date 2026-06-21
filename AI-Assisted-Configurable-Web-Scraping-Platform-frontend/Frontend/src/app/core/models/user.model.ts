export type UserRole = 'ADMIN' | 'OPERATOR' | 'READER';

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  avatarUrl?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  avatarUrl?: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  role: UserRole;
}

export interface UpdateUserRequest {
  username?: string;
  email?: string;
  role?: UserRole;
  enabled?: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}
