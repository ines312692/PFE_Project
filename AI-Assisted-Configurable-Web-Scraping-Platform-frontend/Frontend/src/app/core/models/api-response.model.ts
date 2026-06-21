export interface PageMeta {
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ApiResponse<T> {
  data: T;
  message: string | null;
  statusCode: number;
  timestamp: string;
  pagination: PageMeta | null;
}

