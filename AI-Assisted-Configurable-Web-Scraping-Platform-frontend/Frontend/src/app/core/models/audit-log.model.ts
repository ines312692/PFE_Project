export type AuditAction =
  | 'CREATE' | 'UPDATE' | 'DELETE'
  | 'LAUNCH' | 'PAUSE' | 'DUPLICATE'
  | 'LOGIN' | 'LOGOUT' | 'LOGIN_FAILED'
  | 'ROLE_CHANGE' | 'STATUS_CHANGE' | 'SECRET_ROTATION'
  | 'EXPORT' | 'OTHER';

export type AuditResource =
  | 'JOB' | 'USER' | 'PROJECT' | 'PROJECT_INVITATION'
  | 'PROXY' | 'PROXY_CONFIG' | 'SCHEMA' | 'TEMPLATE'
  | 'WEBHOOK' | 'ETL' | 'EXPORT' | 'AUTH' | 'OTHER';

export type AuditOutcome = 'SUCCESS' | 'FAILURE';

export type AuditSortField = 'occurredAt' | 'action' | 'resourceType' | 'outcome';

export interface AuditLog {
  id: string;
  occurredAt: string;
  userId: string | null;
  username: string | null;
  userRole: string | null;
  action: AuditAction;
  resourceType: AuditResource;
  resourceId: string | null;
  httpMethod: string | null;
  endpoint: string | null;
  httpStatus: number | null;
  outcome: AuditOutcome;
  sourceIp: string | null;
  userAgent: string | null;
  correlationId: string | null;
  requestSummary: string | null;
  errorMessage: string | null;
}

export interface AuditLogStats {
  total: number;
  today: number;
  failuresLast24h: number;
  uniqueUsersLast24h: number;
}

export interface AuditLogSearchParams {
  page?: number;
  size?: number;
  search?: string;
  userId?: string;
  action?: AuditAction | 'ALL';
  resourceType?: AuditResource | 'ALL';
  resourceId?: string;
  outcome?: AuditOutcome | 'ALL';
  occurredFrom?: string;
  occurredTo?: string;
  sortBy?: AuditSortField;
  sortDirection?: 'asc' | 'desc';
}

export const ALL_ACTIONS: (AuditAction | 'ALL')[] = [
  'ALL',
  'CREATE', 'UPDATE', 'DELETE',
  'LAUNCH', 'PAUSE', 'DUPLICATE',
  'LOGIN', 'LOGOUT', 'LOGIN_FAILED',
  'ROLE_CHANGE', 'STATUS_CHANGE', 'SECRET_ROTATION',
  'EXPORT', 'OTHER',
];

export const ALL_RESOURCES: (AuditResource | 'ALL')[] = [
  'ALL',
  'JOB', 'USER', 'PROJECT', 'PROJECT_INVITATION',
  'PROXY', 'PROXY_CONFIG', 'SCHEMA', 'TEMPLATE',
  'WEBHOOK', 'ETL', 'EXPORT', 'AUTH', 'OTHER',
];

export const ALL_OUTCOMES: (AuditOutcome | 'ALL')[] = ['ALL', 'SUCCESS', 'FAILURE'];
