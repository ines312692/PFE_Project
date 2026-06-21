export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  role?: UserRole;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  username: string;
  role: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export type UserRole = 'ADMIN' | 'OPERATOR' | 'READER';
